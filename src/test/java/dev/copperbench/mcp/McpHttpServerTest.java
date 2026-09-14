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

	@Test void externalAgentCanPreviewAndApplyReferenceSafeAssetMove() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Copper Trails\"}");
		Path model = workspace.resolve("assets/coppertrails/models/block/copper_lamp.json");
		Path texture = workspace.resolve("assets/coppertrails/textures/block/copper_lamp.png");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"coppertrails:textures/block/copper_lamp\","
				+ "\"missing\":\"coppertrails:textures/block/missing_lamp\"}}");
		Files.write(texture, new byte[] { 0, 1, 2 });

		WorkspaceTokenService tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		WorkspaceToken token = tokens.issue(WORKSPACE_ID, PermissionProfile.WORKSPACE);
		Path auditPath = workspace.resolve(".copperbench/automation-audit.jsonl");

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				CopperbenchMcpServer server = CopperbenchMcpServer.start(
						new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE,
								Set.of("http://localhost:5173"), CLOCK),
						tokens, adapter(history, workspace), new JsonLineAuditLog(auditPath), new AssetWorkspaceService(workspace))) {
			URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");
			HttpResponse<String> initialized = post(endpoint, initializeBody(), token.value(), null,
					"http://localhost:5173");
			String sessionId = initialized.headers().firstValue("mcp-session-id").orElseThrow();
			post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token.value(),
					sessionId, "http://localhost:5173");

			JsonObject textureList = toolResult(post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":51,\"method\":\"tools/call\",\"params\":{\"name\":\"list_assets\",\"arguments\":{\"category\":\"TEXTURE\"}}}",
					token.value(), sessionId, "http://localhost:5173"));
			String sourceAssetId = textureList.getAsJsonArray("assets").get(0).getAsJsonObject().get("id").getAsString();

			String target = "assets/coppertrails/textures/block/copper_lamp_renamed.png";
			JsonObject preview = toolResult(post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":52,\"method\":\"tools/call\",\"params\":{\"name\":\"preview_asset_move\",\"arguments\":{\"sourceAssetId\":\""
							+ sourceAssetId + "\",\"targetRelativePath\":\"" + target + "\"}}}",
					token.value(), sessionId, "http://localhost:5173"));
			assertEquals("succeeded", preview.get("status").getAsString(), preview::toString);
			JsonObject plan = preview.getAsJsonObject("data");
			assertTrue(plan.get("canApply").getAsBoolean());
			assertEquals(1, plan.get("referenceCount").getAsInt());
			assertEquals("/textures/all", plan.getAsJsonArray("rewrites").get(0).getAsJsonObject()
					.get("sourcePointer").getAsString());
			String planToken = plan.get("planToken").getAsString();

			JsonObject moved = toolResult(post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":53,\"method\":\"tools/call\",\"params\":{\"name\":\"move_asset\",\"arguments\":{\"planToken\":\""
							+ planToken + "\",\"expectedRevision\":0}}}",
					token.value(), sessionId, "http://localhost:5173"));
			assertEquals("committed", moved.get("status").getAsString());
			assertEquals(1, moved.get("newRevision").getAsLong());
			assertTrue(moved.has("recoveryPointId") && !moved.get("recoveryPointId").isJsonNull());
			assertEquals(1, moved.getAsJsonObject("data").get("rewrittenReferences").getAsInt());
			assertFalse(Files.exists(texture));
			assertTrue(Files.isRegularFile(workspace.resolve(target)));
			assertTrue(Files.readString(model).contains("copper_lamp_renamed"));
			assertTrue(Files.readString(auditPath).contains("preview_asset_move"));
			assertTrue(Files.readString(auditPath).contains("move_asset"));
		}
	}

	@TempDir Path workspace;

	@Test void modelingImportAndElementBindingUseRealMcpRevisionsAndIdempotentReceipts() throws Exception {
		var tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		var token = tokens.issue(WORKSPACE_ID, PermissionProfile.WORKSPACE);
		try (var history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			var tasks = new dev.copperbench.assets.BlockbenchModelingService(workspace, WORKSPACE_ID, history, CLOCK);
			UUID taskId = UUID.randomUUID();
			var task = tasks.begin(taskId, null, "models/import_test.bbmodel", 0, dev.copperbench.core.contract.UiCore.Actor.MCP);
			Path edit = Path.of(task.get("editPath").getAsString());
			Files.writeString(edit, "{\"meta\":{\"model_format\":\"java_block\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16]}],\"textures\":[]}");
			tasks.finish(taskId, tasks.get(taskId).get("editSha256").getAsString(), dev.copperbench.core.contract.UiCore.Actor.MCP);
			Files.writeString(edit.resolveSibling("export.json"), "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
			try (var server = CopperbenchMcpServer.start(new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE,
					Set.of("http://localhost:5173"), CLOCK), tokens, adapter(history, workspace), new JsonLineAuditLog(workspace.resolve("audit.jsonl")))) {
				URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");
				String session = post(endpoint, initializeBody(), token.value(), null, "http://localhost:5173").headers().firstValue("mcp-session-id").orElseThrow();
				post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token.value(), session, "http://localhost:5173");
				JsonObject preview = JsonParser.parseString("{\"taskId\":\"" + taskId + "\",\"outputs\":[{\"sourceRelativePath\":\"export.json\",\"targetRelativePath\":\"src/main/resources/assets/copper_trails/models/custom/import_test.json\"}]}").getAsJsonObject();
				var plan = modelingCall(endpoint, session, token.value(), "preview_blockbench_import", preview);
				assertEquals("succeeded", plan.get("status").getAsString(), plan::toString);
				JsonObject apply = new JsonObject(); apply.addProperty("taskId", taskId.toString()); apply.add("planToken", plan.getAsJsonObject("data").get("planToken")); apply.addProperty("expectedRevision", 0);
				var imported = modelingCall(endpoint, session, token.value(), "import_blockbench_task", apply);
				assertEquals("committed", imported.get("status").getAsString(), imported::toString);
				assertEquals(1, imported.get("newRevision").getAsLong());
				assertTrue(imported.getAsJsonObject("data").get("imported").getAsBoolean());
				JsonObject create = JsonParser.parseString("{\"elementType\":\"block\",\"name\":\"bound_lamp\",\"initialValues\":{},\"expectedRevision\":1}").getAsJsonObject();
				var created = modelingCall(endpoint, session, token.value(), "create_mod_element", create);
				assertEquals("committed", created.get("status").getAsString(), created::toString);
				String elementId = created.getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
				JsonObject bind = JsonParser.parseString("{\"taskId\":\"" + taskId + "\",\"elementId\":\"" + elementId + "\",\"modelResource\":\"copper_trails:custom/import_test\",\"expectedRevision\":2}").getAsJsonObject();
				var bound = modelingCall(endpoint, session, token.value(), "bind_blockbench_model", bind);
				assertEquals("committed", bound.get("status").getAsString(), bound::toString);
				assertEquals(3, bound.get("newRevision").getAsLong());
				assertEquals("completed", modelingCall(endpoint, session, token.value(), "bind_blockbench_model", bind).get("status").getAsString());
				var replay = modelingCall(endpoint, session, token.value(), "import_blockbench_task", apply);
				assertEquals("completed", replay.get("status").getAsString());
				assertEquals(3, replay.get("newRevision").getAsLong());
				assertTrue(replay.getAsJsonObject("data").get("idempotentReplay").getAsBoolean());
			}
		}
	}

	@Test void agentCanCreateInspectFinishAndCancelAModelingCandidateThroughMcp() throws Exception {
		var tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		var token = tokens.issue(WORKSPACE_ID, PermissionProfile.WORKSPACE);
		Path auditPath = workspace.resolve(".copperbench/automation-audit.jsonl");
		try (var history = JGitLocalHistoryService.open(workspace, CLOCK);
				var server = CopperbenchMcpServer.start(new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE,
						Set.of("http://localhost:5173"), CLOCK), tokens, adapter(history, workspace), new JsonLineAuditLog(auditPath))) {
			URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");
			String session = post(endpoint, initializeBody(), token.value(), null, "http://localhost:5173")
					.headers().firstValue("mcp-session-id").orElseThrow();
			post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token.value(), session, "http://localhost:5173");
			JsonObject arguments = new JsonObject();
			String id = UUID.randomUUID().toString();
			arguments.addProperty("taskId", id);
			arguments.addProperty("targetRelativePath", "models/agent_model.bbmodel");
			arguments.addProperty("expectedRevision", 1);
			var conflict = modelingCall(endpoint, session, token.value(), "begin_blockbench_task", arguments);
			assertEquals("rejected", conflict.get("status").getAsString());
			assertFalse(Files.exists(workspace.resolve(".copperbench/modeling-tasks/" + id)));
			arguments.addProperty("expectedRevision", 0);
			var started = modelingCall(endpoint, session, token.value(), "begin_blockbench_task", arguments);
			assertEquals("completed", started.get("status").getAsString(), started::toString);
			Path edit = Path.of(started.getAsJsonObject("data").get("editPath").getAsString());
			Files.writeString(edit, "{\"meta\":{\"format_version\":\"5.0\",\"model_format\":\"java_block\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16]}],\"textures\":[]}");
			JsonObject query = new JsonObject(); query.addProperty("taskId", id);
			var inspected = modelingCall(endpoint, session, token.value(), "get_blockbench_task", query);
			JsonObject finish = query.deepCopy();
			finish.addProperty("expectedRevision", 0);
			finish.add("savedSha256", inspected.getAsJsonObject("data").get("editSha256"));
			var finished = modelingCall(endpoint, session, token.value(), "finish_blockbench_task", finish);
			assertEquals("completed", finished.get("status").getAsString(), finished::toString);
			assertEquals("ready_to_import", finished.getAsJsonObject("data").get("state").getAsString());
			assertEquals(0, finished.get("newRevision").getAsLong());
			assertFalse(Files.exists(workspace.resolve("models/agent_model.bbmodel")));
			assertEquals(finished.get("data"), modelingCall(endpoint, session, token.value(), "finish_blockbench_task", finish).get("data"));
			finish.remove("savedSha256");
			var cancelled = modelingCall(endpoint, session, token.value(), "cancel_blockbench_task", finish);
			assertEquals("cancelled", cancelled.getAsJsonObject("data").get("state").getAsString());
			assertTrue(Files.exists(edit));
			assertTrue(Files.readString(auditPath).contains("finish_blockbench_task"));
			assertEquals(1, history.listRecoveryPoints().size());
		}
	}

	private static JsonObject modelingCall(URI endpoint, String session, String token, String name, JsonObject arguments) throws Exception {
		JsonObject request = new JsonObject(); request.addProperty("jsonrpc", "2.0"); request.addProperty("id", 81);
		request.addProperty("method", "tools/call");
		JsonObject params = new JsonObject(); params.addProperty("name", name); params.add("arguments", arguments); request.add("params", params);
		return toolResult(post(endpoint, request.toString(), token, session, "http://localhost:5173"));
	}

	@Test void blockbenchEnvironmentIsDiscoverableThroughReadOnlyMcpWithoutChangingRevision() throws Exception {
		WorkspaceTokenService tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		WorkspaceToken token = tokens.issue(WORKSPACE_ID, PermissionProfile.READ_ONLY);
		Path auditPath = workspace.resolve(".copperbench/automation-audit.jsonl");
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				CopperbenchMcpServer server = CopperbenchMcpServer.start(
						new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.READ_ONLY,
								Set.of("http://localhost:5173"), CLOCK), tokens,
						adapter(history, workspace, dev.copperbench.automation.security.TaskAuthorizationStore.productDefault(CLOCK), PermissionProfile.READ_ONLY),
						new JsonLineAuditLog(auditPath))) {
			URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");
			var initialized = post(endpoint, initializeBody(), token.value(), null, "http://localhost:5173");
			String session = initialized.headers().firstValue("mcp-session-id").orElseThrow();
			post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token.value(), session, "http://localhost:5173");
			var listing = post(endpoint, "{\"jsonrpc\":\"2.0\",\"id\":70,\"method\":\"tools/list\"}", token.value(), session, "http://localhost:5173");
			assertTrue(listing.body().contains("get_blockbench_environment"));
			JsonObject result = toolResult(post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":71,\"method\":\"tools/call\",\"params\":{\"name\":\"get_blockbench_environment\",\"arguments\":{}}}",
					token.value(), session, "http://localhost:5173"));
			assertEquals("succeeded", result.get("status").getAsString(), result::toString);
			assertEquals(0, result.get("revision").getAsLong());
			assertEquals("not_checked", result.getAsJsonObject("data").getAsJsonObject("mcp").get("state").getAsString());
			assertTrue(result.getAsJsonObject("data").get("managedModelingTasksAvailable").getAsBoolean());
			assertTrue(result.getAsJsonObject("data").get("automaticModelImportAvailable").getAsBoolean());
			assertTrue(history.listRecoveryPoints().isEmpty());
			JsonObject begin = new JsonObject(); begin.addProperty("taskId", UUID.randomUUID().toString());
			begin.addProperty("targetRelativePath", "models/denied.bbmodel"); begin.addProperty("expectedRevision", 0);
			assertEquals("rejected", modelingCall(endpoint, session, token.value(), "begin_blockbench_task", begin).get("status").getAsString());
			assertFalse(Files.exists(workspace.resolve(".copperbench/modeling-tasks")));
			assertTrue(Files.readString(auditPath).contains("get_blockbench_environment"));
			assertFalse(Files.readString(auditPath).contains(token.value()));
		}
	}

	@Test void authenticatedMcpUsesAndRevokesUserIssuedTaskAuthority() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{}");
		var authority = new dev.copperbench.automation.security.TaskAuthorizationStore(workspace.resolve("private-fixture"), CLOCK);
		String id = authority.issue(dev.copperbench.core.contract.UiCore.Actor.UI, true, "Server test", workspace,
				List.of("run_server", "test", "edit"), 3600, true).get("id").getAsString();
		WorkspaceTokenService tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		WorkspaceToken token = tokens.issue(WORKSPACE_ID, PermissionProfile.WORKSPACE);
		Path auditPath = workspace.resolve(".copperbench/automation-audit.jsonl");
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				CopperbenchMcpServer server = CopperbenchMcpServer.start(
						new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE, Set.of("http://localhost:5173"), CLOCK),
						tokens, adapter(history, workspace, authority), new JsonLineAuditLog(auditPath), new AssetWorkspaceService(workspace))) {
			URI endpoint = URI.create("http://127.0.0.1:" + server.address().getPort() + "/mcp");
			var initialized = post(endpoint, initializeBody(), token.value(), null, "http://localhost:5173");
			String session = initialized.headers().firstValue("mcp-session-id").orElseThrow();
			post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token.value(), session, "http://localhost:5173");
			String run = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"run_server\",\"arguments\":{\"expectedRevision\":0,\"taskAuthorizationId\":\"" + id + "\"}}}";
			assertEquals("accepted", toolResult(post(endpoint, run, token.value(), session, "http://localhost:5173")).get("status").getAsString());
			String revoke = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"revoke_task_authorization\",\"arguments\":{\"expectedRevision\":0,\"authorizationId\":\"" + id + "\"}}}";
			assertEquals("completed", toolResult(post(endpoint, revoke, token.value(), session, "http://localhost:5173")).get("status").getAsString());
			var rejected = toolResult(post(endpoint, run, token.value(), session, "http://localhost:5173"));
			assertEquals("rejected", rejected.get("status").getAsString());
			assertTrue(rejected.toString().contains("TASK_AUTHORIZATION_REVOKED"));
			assertTrue(Files.readString(auditPath).contains(id));
		}
	}

	@Test void tomcatBaseDoesNotDependOnInstallWorkingDirectory() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Copper Trails\"}");
		Path invalidInstallRoot = workspace.resolve("installed-product-root");
		Files.writeString(invalidInstallRoot, "not a directory");
		String previousUserDir = System.getProperty("user.dir");
		String previousCatalinaBase = System.getProperty("catalina.base");
		System.setProperty("user.dir", invalidInstallRoot.toString());
		System.setProperty("catalina.base", invalidInstallRoot.toString());

		WorkspaceTokenService tokens = new WorkspaceTokenService(CLOCK, Duration.ofMinutes(5));
		Path auditPath = workspace.resolve(".copperbench/automation-audit.jsonl");
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				CopperbenchMcpServer server = CopperbenchMcpServer.start(
						new McpServerConfiguration(0, WORKSPACE_ID, PermissionProfile.WORKSPACE,
								Set.of("http://localhost:5173"), CLOCK),
						tokens, adapter(history), new JsonLineAuditLog(auditPath), new AssetWorkspaceService(workspace))) {
			assertTrue(server.address().getAddress().isLoopbackAddress());
			assertTrue(server.address().getPort() > 0);
		} finally {
			if (previousUserDir == null)
				System.clearProperty("user.dir");
			else
				System.setProperty("user.dir", previousUserDir);
			if (previousCatalinaBase == null)
				System.clearProperty("catalina.base");
			else
				System.setProperty("catalina.base", previousCatalinaBase);
		}
	}

	@Test void authenticatedLoopbackServerExposesSdkToolsAndRejectsUntrustedRequests() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Copper Trails\"}");
		Path model = workspace.resolve("assets/coppertrails/models/block/copper_lamp.json");
		Files.createDirectories(model.getParent());
		Files.createDirectories(workspace.resolve("assets/coppertrails/textures/block"));
		Files.writeString(model, "{\"textures\":{\"all\":\"coppertrails:textures/block/copper_lamp\","
				+ "\"missing\":\"coppertrails:block/missing_lamp\"}}");
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
			assertTrue(tools.body().contains("get_workspace_environment"));
			assertTrue(tools.body().contains("list_new_workspace_generators"));
			assertTrue(tools.body().contains("create_workspace"));
			assertTrue(tools.body().contains("preview_mod_element_change"));
			assertTrue(tools.body().contains("get_procedure"));
			assertTrue(tools.body().contains("preview_procedure_change"));
			assertTrue(tools.body().contains("update_procedure"));
			assertTrue(tools.body().contains("get_workspace_health"));
			assertTrue(tools.body().contains("get_workspace_references"));
			assertTrue(tools.body().contains("list_workspace_registries"));
			assertTrue(tools.body().contains("preview_registry_rename"));
			assertTrue(tools.body().contains("plan_procedure_refactor"));
			assertTrue(tools.body().contains("replace_call_target"));
			assertTrue(tools.body().contains("replace_resource_target"));
			assertTrue(tools.body().contains("sourceResource"));
			assertTrue(tools.body().contains("targetResource"));
			assertTrue(tools.body().contains("plan_workspace_changes"));
			assertTrue(tools.body().contains("requireRecoveryPoint"));
			assertTrue(tools.body().contains("create_local_template"));
			assertTrue(tools.body().contains("list_local_templates"));
			assertTrue(tools.body().contains("preview_local_template_instantiation"));
			assertTrue(tools.body().contains("create_registry_entry"));
			assertTrue(tools.body().contains("rename_registry_entry"));
			assertTrue(tools.body().contains("create_mod_element"));
			assertTrue(tools.body().contains("livingentity"));
			assertTrue(tools.body().contains("update_mod_element"));
			assertTrue(tools.body().contains("set_mod_element_source_management"));
			assertTrue(tools.body().contains("delete_mod_element"));
			assertTrue(tools.body().contains("generate_workspace"));
			assertTrue(tools.body().contains("build_workspace"));
			assertTrue(tools.body().contains("run_client"));
			assertTrue(tools.body().contains("run_datagen"));
			assertTrue(tools.body().contains("preview_datagen_output"));
			assertTrue(tools.body().contains("publish_datagen_output"));
			assertTrue(tools.body().contains("get_task"));
			assertTrue(tools.body().contains("cancel_task"));
			assertTrue(tools.body().contains("restore_recovery_point"));
			assertTrue(tools.body().contains("preview_recovery_restore"));
			assertTrue(tools.body().contains("list_assets"));
			assertTrue(tools.body().contains("inspect_asset_references"));
			assertTrue(tools.body().contains("preview_asset_move"));
			assertTrue(tools.body().contains("move_asset"));

			HttpResponse<String> coverageResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":35,\"method\":\"tools/call\",\"params\":{\"name\":\"get_element_coverage\",\"arguments\":{}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject coverage = toolResult(coverageResult);
			assertEquals("succeeded", coverage.get("status").getAsString());
			assertEquals(37, coverage.getAsJsonObject("data").getAsJsonArray("firstPartySlice").size());
			assertEquals(0, coverage.getAsJsonObject("data").getAsJsonArray("unsupportedInNewUi").size());

			HttpResponse<String> workspaceResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_workspace\",\"arguments\":{}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals(200, workspaceResult.statusCode());
			assertTrue(workspaceResult.body().contains("Copper Trails"));

			HttpResponse<String> environmentResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":36,\"method\":\"tools/call\",\"params\":{\"name\":\"get_workspace_environment\",\"arguments\":{}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject environment = toolResult(environmentResult);
			assertEquals("succeeded", environment.get("status").getAsString());
			assertTrue(environment.getAsJsonObject("data").has("execution"));
			assertTrue(environment.getAsJsonObject("data").getAsJsonObject("agentWorkflow")
					.get("nativeFilesAuthoritative").getAsBoolean());
			assertTrue(environment.getAsJsonObject("data").getAsJsonObject("agentWorkflow")
					.get("structuredElementsOptional").getAsBoolean());

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
			JsonObject assets = toolResult(assetsResult);
			assertEquals("succeeded", assets.get("status").getAsString());
			assertTrue(assets.getAsJsonArray("assets").toString().contains("copper_lamp.json"));
			assertTrue(assets.has("health"));
			assertEquals(1, assets.getAsJsonArray("assetHealth").size());
			assertTrue(assets.getAsJsonArray("diagnostics").toString().contains("MISSING_ASSET_REFERENCE"));
			assertTrue(assets.getAsJsonArray("diagnostics").toString().contains("open_asset"));
			HttpResponse<String> referencesResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"tools/call\",\"params\":{\"name\":\"inspect_asset_references\",\"arguments\":{\"sourcePath\":\"assets/coppertrails/models/block/copper_lamp.json\"}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject references = toolResult(referencesResult);
			assertEquals("succeeded", references.get("status").getAsString());
			assertEquals(1, references.getAsJsonArray("references").size());
			assertTrue(references.has("incomingReferences"));
			assertTrue(references.has("health"));
			assertTrue(references.getAsJsonArray("diagnostics").toString().contains("MISSING_ASSET_REFERENCE"));
			assertTrue(references.getAsJsonArray("diagnostics").toString().contains("open_asset"));

			HttpResponse<String> recoveryPointResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"create_recovery_point\",\"arguments\":{\"label\":\"Before MCP edit\",\"expectedRevision\":0}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals(200, recoveryPointResult.statusCode());
			JsonObject recoveryPoint = toolResult(recoveryPointResult);
			assertEquals("committed", recoveryPoint.get("status").getAsString());
			assertEquals("create_recovery_point", recoveryPoint.get("operation").getAsString());
			assertTrue(recoveryPoint.has("recoveryPointId"));
			String recoveryPointId = recoveryPoint.get("recoveryPointId").getAsString();

			HttpResponse<String> restorePreviewResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":40,\"method\":\"tools/call\",\"params\":{\"name\":\"preview_recovery_restore\",\"arguments\":{\"recoveryPointId\":\"" + recoveryPointId + "\"}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject restorePreview = toolResult(restorePreviewResult);
			assertEquals("succeeded", restorePreview.get("status").getAsString());
			assertEquals(recoveryPointId, restorePreview.getAsJsonObject("data").get("recoveryPointId").getAsString());

			HttpResponse<String> protectedRestoreResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":41,\"method\":\"tools/call\",\"params\":{\"name\":\"restore_recovery_point\",\"arguments\":{\"recoveryPointId\":\"" + recoveryPointId + "\",\"expectedRevision\":0}}}",
					token.value(), sessionId, "http://localhost:5173");
			JsonObject protectedRestore = toolResult(protectedRestoreResult);
			assertEquals("rejected", protectedRestore.get("status").getAsString());
			assertTrue(protectedRestore.getAsJsonArray("diagnostics").toString().contains("USER_APPROVAL_REQUIRED"));

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
					"{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"get_task\",\"arguments\":{\"taskId\":\"" + taskId + "\",\"afterLogSequence\":0}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals("running", toolResult(taskResult).getAsJsonObject("data")
					.getAsJsonObject("task").get("state").getAsString());

			HttpResponse<String> cancelledResult = post(endpoint,
					"{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"cancel_task\",\"arguments\":{\"taskId\":\"" + taskId + "\",\"expectedRevision\":1}}}",
					token.value(), sessionId, "http://localhost:5173");
			assertEquals("cancelled", toolResult(cancelledResult).get("status").getAsString());
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
		return adapter(history, null);
	}

	private static McpWorkspaceEntryAdapter adapter(LocalHistoryService history, Path workspaceRoot) {
		return adapter(history, workspaceRoot, dev.copperbench.automation.security.TaskAuthorizationStore.productDefault(CLOCK));
	}

	private static McpWorkspaceEntryAdapter adapter(LocalHistoryService history, Path workspaceRoot,
			dev.copperbench.automation.security.TaskAuthorizationStore authority) {
		return adapter(history, workspaceRoot, authority, PermissionProfile.WORKSPACE);
	}

	private static McpWorkspaceEntryAdapter adapter(LocalHistoryService history, Path workspaceRoot,
			dev.copperbench.automation.security.TaskAuthorizationStore authority, PermissionProfile permission) {
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
		WorkspaceApplicationService service = workspaceRoot == null
				? new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(CLOCK, ids),
						dev.copperbench.core.application.WorkspaceMutationGateway.noOp(), history,
						ignored -> store.read(WORKSPACE_ID).orElseThrow().copy(), CLOCK, ids)
				: new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(CLOCK, ids),
						dev.copperbench.core.application.WorkspaceMutationGateway.noOp(), history,
						ignored -> store.read(WORKSPACE_ID).orElseThrow().copy(), ignored -> workspaceRoot, CLOCK, ids, authority);
		return new McpWorkspaceEntryAdapter(service, permission);
	}
}
