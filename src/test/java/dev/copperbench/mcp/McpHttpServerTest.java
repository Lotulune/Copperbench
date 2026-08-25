/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.automation.audit.JsonLineAuditLog;
import dev.copperbench.automation.security.WorkspaceToken;
import dev.copperbench.automation.security.WorkspaceTokenService;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryService;
import org.junit.jupiter.api.BeforeAll;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpHttpServerTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-4000-8000-000000000030");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);

	@BeforeAll static void configureLogDirectory() {
		System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
	}

	@TempDir Path workspace;

	@Test void authenticatedLoopbackServerExposesSdkToolsAndRejectsUntrustedRequests() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Copper Trails\"}");
		Path model = workspace.resolve("assets/coppertrails/models/block/copper_lamp.json");
		Files.createDirectories(model.getParent());
		Files.createDirectories(workspace.resolve("assets/coppertrails/textures/block"));
		Files.writeString(model, "{\"textures\":{\"all\":\"coppertrails:textures/block/copper_lamp\"}}");
		Files.write(workspace.resolve("assets/coppertrails/textures/block/copper_lamp.png"), new byte[] { 0, 1, 2 });
		WorkspaceTokenService tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		WorkspaceToken token = tokens.issue(WORKSPACE_ID, PermissionProfile.WORKSPACE);
		Path auditPath = workspace.resolve(".copperbench/automation-audit.jsonl");

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				CopperbenchMcpServer server = CopperbenchMcpServer.start(
						new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE,
						Set.of("http://localhost:5173"), CLOCK),
					tokens, adapter(history), new JsonLineAuditLog(auditPath), new AssetWorkspaceService(workspace))) {
			assertTrue(server.address().getAddress().isLoopbackAddress());
			URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");

			HttpResponse<String> missingToken = post(endpoint, initializeBody(), null, null,
					"http://localhost:5173");
			assertEquals(401, missingToken.statusCode());

			HttpResponse<String> foreignOrigin = post(endpoint, initializeBody(), token.value(), null,
					"http://malicious.invalid");
			assertEquals(403, foreignOrigin.statusCode());

			HttpResponse<String> initialized = post(endpoint, initializeBody(), token.value(), null,
					"http://localhost:5173");
			assertEquals(200, initialized.statusCode());
			assertTrue(initialized.body().contains("copperbench"));
			String sessionId = initialized.headers().firstValue("mcp-session-id").orElseThrow();

			post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token.value(),
					sessionId, "http://localhost:5173");
			HttpResponse<String> tools = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals(200, tools.statusCode());
			assertTrue(tools.body().contains("get_workspace"));
			assertTrue(tools.body().contains("list_new_workspace_generators"));
			assertTrue(tools.body().contains("create_workspace"));
			assertTrue(tools.body().contains("preview_mod_element_change"));
			assertTrue(tools.body().contains("get_procedure"));
			assertTrue(tools.body().contains("preview_procedure_change"));
			assertTrue(tools.body().contains("update_procedure"));
			assertTrue(tools.body().contains("get_workspace_references"));
			assertTrue(tools.body().contains("list_workspace_registries"));
			assertTrue(tools.body().contains("preview_registry_rename"));
			assertTrue(tools.body().contains("create_registry_entry"));
			assertTrue(tools.body().contains("rename_registry_entry"));
			assertTrue(tools.body().contains("create_mod_element"));
			assertTrue(tools.body().contains("update_mod_element"));
			assertTrue(tools.body().contains("delete_mod_element"));
			assertTrue(tools.body().contains("generate_workspace"));
			assertTrue(tools.body().contains("build_workspace"));
			assertTrue(tools.body().contains("run_client"));
			assertTrue(tools.body().contains("run_datagen"));
			assertTrue(tools.body().contains("preview_datagen_output"));
			assertTrue(tools.body().contains("publish_datagen_output"));
			assertTrue(tools.body().contains("get_task"));
			assertTrue(tools.body().contains("list_assets"));
			assertTrue(tools.body().contains("inspect_asset_references"));

			HttpResponse<String> workspaceResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_workspace\",\"arguments\":{}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals(200, workspaceResult.statusCode());
			assertTrue(workspaceResult.body().contains("Copper Trails"));

			HttpResponse<String> generatorsResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":33,\"method\":\"tools/call\",\"params\":{\"name\":\"list_new_workspace_generators\",\"arguments\":{}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject generators = toolResult(generatorsResult);
			assertEquals("succeeded", generators.get("status").getAsString());
			assertEquals(9, generators.getAsJsonObject("data").getAsJsonArray("generators").size());
			assertTrue(generators.getAsJsonObject("data").getAsJsonArray("generators").toString()
					.contains("resourcepack-1.21.1"));

			HttpResponse<String> unapprovedCreate = post(endpoint,
					"""
					{"jsonrpc":"2.0","id":34,"method":"tools/call","params":{"name":"create_workspace","arguments":{"generatorId":"fabric-1.21.1","modName":"Copper Trails","modId":"copper_trails","workspaceFolderPath":"workspace/copper_trails","userApproved":false,"expectedRevision":0}}}
					""",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject unapproved = toolResult(unapprovedCreate);
			assertEquals("rejected", unapproved.get("status").getAsString());
			assertTrue(unapproved.getAsJsonArray("diagnostics").toString().contains("USER_APPROVAL_REQUIRED"));

			HttpResponse<String> assetsResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\",\"params\":{\"name\":\"list_assets\",\"arguments\":{\"category\":\"MODEL\"}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals("succeeded", toolResult(assetsResult).get("status").getAsString());
			assertTrue(toolResult(assetsResult).getAsJsonArray("assets").toString().contains("copper_lamp.json"));
			HttpResponse<String> referencesResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"tools/call\",\"params\":{\"name\":\"inspect_asset_references\",\"arguments\":{\"sourcePath\":\"assets/coppertrails/models/block/copper_lamp.json\"}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals("succeeded", toolResult(referencesResult).get("status").getAsString());
			assertEquals(1, toolResult(referencesResult).getAsJsonArray("references").size());

			HttpResponse<String> recoveryPointResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"create_recovery_point\",\"arguments\":{\"label\":\"Before MCP edit\",\"expectedRevision\":0}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals(200, recoveryPointResult.statusCode());
			JsonObject recoveryPoint = toolResult(recoveryPointResult);
			assertEquals("committed", recoveryPoint.get("status").getAsString());
			assertEquals("create_recovery_point", recoveryPoint.get("operation").getAsString());
			assertTrue(recoveryPoint.has("recoveryPointId"));

			HttpResponse<String> createdResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"create_mod_element\",\"arguments\":{\"elementType\":\"item\",\"name\":\"trail_marker\",\"initialValues\":{\"displayName\":\"Trail Marker\",\"fields\":{\"maxStackSize\":16}},\"expectedRevision\":0}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject created = toolResult(createdResult);
			assertEquals("committed", created.get("status").getAsString());
			assertEquals(1, created.get("newRevision").getAsLong());
			assertTrue(created.has("recoveryPointId") && !created.get("recoveryPointId").isJsonNull());

			HttpResponse<String> generatedResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"generate_workspace\",\"arguments\":{\"expectedRevision\":1}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject generated = toolResult(generatedResult);
			assertEquals("accepted", generated.get("status").getAsString());
			String taskId = generated.getAsJsonObject("task").get("id").getAsString();

			HttpResponse<String> taskResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"get_task\",\"arguments\":{\"taskId\":\"" + taskId + "\"}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals("running", toolResult(taskResult).getAsJsonObject("data")
					.getAsJsonObject("task").get("state").getAsString());
			assertTrue(Files.readString(auditPath).contains("get_workspace"));
			assertTrue(Files.readString(auditPath).contains("create_recovery_point"));
			assertTrue(Files.readString(auditPath).contains("create_mod_element"));
			assertFalse(Files.readString(auditPath).contains(token.value()));
		}
	}

	@Test void rejectsTokenWhosePermissionProfileDoesNotMatchTheServerSession() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Copper Trails\"}");
		WorkspaceTokenService tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		WorkspaceToken readOnlyToken = tokens.issue(WORKSPACE_ID, PermissionProfile.READ_ONLY);

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				CopperbenchMcpServer server = CopperbenchMcpServer.start(
						new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE,
								Set.of("http://localhost:5173"), CLOCK),
						tokens, adapter(history),
						new JsonLineAuditLog(workspace.resolve(".copperbench/automation-audit.jsonl")))) {
			URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");
			HttpResponse<String> response = post(endpoint, initializeBody(), readOnlyToken.value(), null,
					"http://localhost:5173");

			assertEquals(403, response.statusCode());
			assertTrue(response.body().contains("TOKEN_PROFILE_MISMATCH"));
		}
	}

	@Test void rejectsToolCallWhenTheMandatoryAuditLogCannotBeWritten() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Copper Trails\"}");
		Path blockedParent = workspace.resolve("blocked-parent");
		Files.writeString(blockedParent, "not a directory");
		WorkspaceTokenService tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		WorkspaceToken token = tokens.issue(WORKSPACE_ID, PermissionProfile.WORKSPACE);

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				CopperbenchMcpServer server = CopperbenchMcpServer.start(
						new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE,
								Set.of("http://localhost:5173"), CLOCK),
						tokens, adapter(history), new JsonLineAuditLog(blockedParent.resolve("audit.jsonl")))) {
			URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");
			HttpResponse<String> initialized = post(endpoint, initializeBody(), token.value(), null,
					"http://localhost:5173");
			String sessionId = initialized.headers().firstValue("mcp-session-id").orElseThrow();

			HttpResponse<String> response = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_workspace\",\"arguments\":{}}}",
					token.value(), sessionId, "http://localhost:5173");

			assertEquals(200, response.statusCode());
			assertFalse(response.body().contains("Copper Trails"));
			assertTrue(response.body().contains("AUDIT_LOG_UNAVAILABLE"));
		}
	}

	private static HttpResponse<String> post(URI endpoint, String body, String token, String sessionId,
			String origin) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/event-stream")
				.header("X-Copperbench-Workspace", WORKSPACE_ID.toString())
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (token != null)
			request.header("Authorization", "Bearer " + token);
		if (sessionId != null)
			request.header("mcp-session-id", sessionId);
		if (origin != null)
			request.header("Origin", origin);
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String initializeBody() {
		return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
				"\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," +
				"\"clientInfo\":{\"name\":\"stage-2-test\",\"version\":\"1.0\"}}}";
	}

	private static JsonObject toolResult(HttpResponse<String> response) {
		String data = response.body().lines().filter(line -> line.startsWith("data: ")).findFirst()
				.orElseThrow(() -> new AssertionError("Missing SSE data: " + response.body())).substring(6);
		JsonObject envelope = JsonParser.parseString(data).getAsJsonObject();
		String text = envelope.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject()
				.get("text").getAsString();
		return JsonParser.parseString(text).getAsJsonObject();
	}

	private static McpWorkspaceEntryAdapter adapter(LocalHistoryService history) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(300);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, ids),
				dev.copperbench.core.application.WorkspaceMutationGateway.noOp(), history, null, CLOCK, ids);
		return new McpWorkspaceEntryAdapter(service, PermissionProfile.WORKSPACE);
	}
}
