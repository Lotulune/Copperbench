package dev.copperbench.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.workspace.project.GradleProjectOperations;
import dev.copperbench.core.workspace.project.GradleProjectWorkspaceSession;
import dev.copperbench.generator.GradleProcessRunner.ProcessResult;
import dev.copperbench.generator.project.GradleProjectTaskGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The desktop MCP server over an adopted project's session (ADR-0020); only the Gradle process is replaced. */
class GradleProjectMcpTest {

	@TempDir Path root;
	private UUID workspaceId;
	private URI endpoint;
	private String token;
	private String session;

	@BeforeEach void project() throws Exception {
		write("gradlew", "#!/bin/sh\n");
		write("gradlew.bat", "@echo off\r\n");
		write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.8-bin.zip\n");
		write("gradle/wrapper/gradle-wrapper.jar", "jar");
		write("settings.gradle", "rootProject.name = 'emberkit'\n");
		write("build.gradle", "plugins { id 'fabric-loom' version '1.7.4' }\n");
		write("gradle.properties", "minecraft_version=1.21.1\n");
		write("src/main/resources/fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"emberkit\",\"name\":\"Ember Kit\"}\n");
	}

	@Test void anAgentBuildsAnAdoptedProjectAndSeesOnlyWhatItOffers() throws Exception {
		try (var opened = GradleProjectWorkspaceSession.open(root, (store, folder) -> new GradleProjectTaskGateway(store, ignored -> folder,
				Path.of("").toAbsolutePath(), Clock.systemUTC(), UUID::randomUUID,
				profile -> (directory, arguments, timeout, output) -> {
					Files.createDirectories(directory.resolve("build/libs"));
					return new ProcessResult(0, false);
				}), Clock.systemUTC(), UUID::randomUUID);
				DesktopMcpRuntime runtime = DesktopMcpRuntime.start(root, opened.workspaceId(),
						opened.mcpEntry(PermissionProfile.WORKSPACE), Clock.systemUTC())) {
			workspaceId = opened.workspaceId();
			assertEquals("listening", runtime.state().status(), runtime.state().failure());
			endpoint = URI.create(runtime.state().url());
			token = runtime.revealTokenOnce().orElseThrow();
			HttpResponse<String> initialized = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
					+ "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"adopted\",\"version\":\"1.0\"}}}");
			session = initialized.headers().firstValue("mcp-session-id").orElseThrow();
			String instructions = result(initialized).get("instructions").getAsString();
			assertTrue(instructions.contains("did not create") && instructions.contains("run_gametest"), instructions);
			post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

			JsonArray listed = result(post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}")).getAsJsonArray("tools");
			Set<String> names = listed.asList().stream().map(tool -> tool.getAsJsonObject().get("name").getAsString()).collect(Collectors.toSet());
			for (String offered : List.of("get_workspace", "get_workspace_environment", "get_workspace_doctor", "validate_workspace", "build_workspace",
					"export_workspace", "export_verified_artifact", "run_client", "run_server", "prepare_game_tests", "run_gametest", "acceptance", "get_task",
					"cancel_task", "create_recovery_point", "restore_recovery_point", "list_assets", "move_asset", "delete_asset"))
				assertTrue(names.contains(offered), offered);
			for (String missing : List.of("create_mod_element", "update_mod_element", "update_procedure", "create_registry_entry", "generate_workspace",
					"plan_workspace_changes", "apply_workspace_plan", "run_datagen", "publish_datagen_output", "create_publish_batch",
					"execute_loader_migration", "version_copy_upgrade", "preview", "get_mod_element_field_contract", "create_local_template"))
				assertFalse(names.contains(missing), missing);

			JsonObject workspace = call("get_workspace", "{}").getAsJsonObject("data").getAsJsonObject("workspace");
			assertEquals("gradle_project", workspace.get("kind").getAsString());
			JsonObject environment = call("get_workspace_environment", "{}").getAsJsonObject("data");
			assertTrue(environment.getAsJsonObject("fieldContracts").isEmpty(), "no element contracts are sent for a project without elements");
			assertFalse(environment.getAsJsonObject("agentWorkflow").has("planOperation"));

			JsonObject built = call("build_workspace", "{\"expectedRevision\":0}");
			assertEquals("accepted", built.get("status").getAsString(), built.toString());
			assertEquals("succeeded", await(built.getAsJsonObject("task").get("id").getAsString()).get("state").getAsString());

			write("gradle.properties", "minecraft_version=1.21.4\n");
			JsonObject rejected = call("build_workspace", "{\"expectedRevision\":0}");
			JsonObject failed = await(rejected.getAsJsonObject("task").get("id").getAsString());
			assertEquals("failed", failed.get("state").getAsString(), "the project on disk is read again for every task");
		}
	}

	@Test void anUnsupportedProjectExplainsWhyItCannotBeBuilt() throws Exception {
		write("gradle.properties", "minecraft_version=1.21.4\n");
		try (var opened = GradleProjectWorkspaceSession.open(root, (store, folder) -> new GradleProjectTaskGateway(store, ignored -> folder,
				Path.of("").toAbsolutePath(), Clock.systemUTC(), UUID::randomUUID, profile -> (directory, arguments, timeout, output) -> {
					throw new AssertionError("no process starts for an unsupported project");
				}), Clock.systemUTC(), UUID::randomUUID);
				DesktopMcpRuntime runtime = DesktopMcpRuntime.start(root, opened.workspaceId(),
						opened.mcpEntry(PermissionProfile.WORKSPACE), Clock.systemUTC())) {
			workspaceId = opened.workspaceId();
			endpoint = URI.create(runtime.state().url());
			token = runtime.revealTokenOnce().orElseThrow();
			session = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\","
					+ "\"capabilities\":{},\"clientInfo\":{\"name\":\"adopted\",\"version\":\"1.0\"}}}").headers().firstValue("mcp-session-id").orElseThrow();
			post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
			JsonObject diagnostic = call("build_workspace", "{\"expectedRevision\":0}").getAsJsonArray("diagnostics").get(0).getAsJsonObject();
			assertEquals(GradleProjectOperations.UNSUPPORTED, diagnostic.get("code").getAsString());
			assertTrue(diagnostic.getAsJsonObject("message").toString().contains("1.21.4"), diagnostic.toString());
			JsonObject doctor = call("get_workspace_doctor", "{}").getAsJsonObject("data");
			assertTrue(doctor.toString().contains("GRADLE_PROJECT_VERSION_UNSUPPORTED"), doctor.toString());
		}
	}

	private JsonObject await(String taskId) throws Exception {
		Instant deadline = Instant.now().plusSeconds(20);
		while (Instant.now().isBefore(deadline)) {
			JsonObject task = call("get_task", "{\"taskId\":\"" + taskId + "\",\"afterLogSequence\":0}").getAsJsonObject("data").getAsJsonObject("task");
			if (!List.of("running", "queued").contains(task.get("state").getAsString())) return task;
			Thread.sleep(50);
		}
		throw new AssertionError("Task timed out");
	}

	private JsonObject call(String name, String arguments) throws Exception {
		JsonObject result = result(post("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name
				+ "\",\"arguments\":" + arguments + "}}"));
		return JsonParser.parseString(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()).getAsJsonObject();
	}

	private static JsonObject result(HttpResponse<String> response) {
		assertEquals(200, response.statusCode(), response.body());
		String body = response.body().lines().filter(line -> line.startsWith("data: ")).findFirst().map(line -> line.substring(6)).orElse(response.body());
		return JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("result");
	}

	private HttpResponse<String> post(String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
				.header("X-Copperbench-Workspace", workspaceId.toString()).header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (session != null) request.header("mcp-session-id", session);
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void write(String relative, String text) throws Exception {
		Path target = root.resolve(relative);
		Files.createDirectories(target.getParent());
		Files.writeString(target, text);
	}
}
