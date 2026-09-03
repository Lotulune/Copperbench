/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopMcpAgentLoopTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-4000-8000-000000000092");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-02T03:15:00Z"), ZoneOffset.UTC);

	@TempDir Path workspace;

	@Test void directCodeCreationSurfacesCompileVerificationDiagnosticsThroughGetTask() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Code Diagnostic Workspace\"}");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-26.1.2");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "26.1.2");
		generator.addProperty("displayName", "Fabric 26.1.2");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Code Diagnostic Workspace", "testmod2", 0, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(7000);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		FailedCompileBuildGateway tasks = new FailedCompileBuildGateway(ids);

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
					WorkspaceMutationGateway.noOp(), history,
					ignored -> store.read(WORKSPACE_ID).orElseThrow().copy(), CLOCK, ids);
			DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID,
					new McpWorkspaceEntryAdapter(service, PermissionProfile.WORKSPACE), CLOCK);
			try {
				String token = runtime.revealTokenOnce().orElseThrow();
				URI endpoint = URI.create(runtime.state().url());
				String sessionId = initialize(endpoint, token);

				JsonObject code = new JsonObject();
				code.addProperty("elementType", "code");
				code.addProperty("name", "broken_agent_behavior");
				JsonObject values = new JsonObject();
				values.addProperty("code", "package net.example; public final class Broken { missingSymbol(); }");
				code.add("initialValues", values);
				code.addProperty("expectedRevision", 0);
				JsonObject created = call(endpoint, token, sessionId, 20, "create_mod_element", code);
				assertEquals("committed", created.get("status").getAsString(), created.toString());
				JsonObject verification = created.getAsJsonObject("data").getAsJsonObject("compileVerification");
				assertEquals("build_workspace", verification.get("operation").getAsString());
				assertEquals("accepted", verification.get("status").getAsString());
				String taskId = verification.getAsJsonObject("task").get("id").getAsString();

				JsonObject taskArguments = new JsonObject();
				taskArguments.addProperty("taskId", taskId);
				taskArguments.addProperty("afterLogSequence", 0);
				JsonObject task = call(endpoint, token, sessionId, 21, "get_task", taskArguments)
						.getAsJsonObject("data");
				assertEquals("failed", task.getAsJsonObject("task").get("state").getAsString());
				String diagnostics = task.getAsJsonArray("diagnostics").toString();
				assertTrue(diagnostics.contains("JAVA_COMPILE_ERROR"), diagnostics);
				assertTrue(diagnostics.contains("/src/main/java/net/example/Broken.java"), diagnostics);
				assertTrue(diagnostics.contains("Line 1: cannot find symbol"), diagnostics);
			} finally {
				runtime.close();
			}
		}
	}

	@Test void desktopHttpLoopSupportsReadWritePlanBuildIncrementalLogsAndRevisionRecovery() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Agent Loop Workspace\"}");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-26.1.2");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "26.1.2");
		generator.addProperty("displayName", "Fabric 26.1.2");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Agent Loop Workspace", "testmod2", 0, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(1000);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		CompletedBuildGateway tasks = new CompletedBuildGateway(ids);

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
					WorkspaceMutationGateway.noOp(), history,
					ignored -> store.read(WORKSPACE_ID).orElseThrow().copy(), CLOCK, ids);
			DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID,
					new McpWorkspaceEntryAdapter(service, PermissionProfile.WORKSPACE), CLOCK);
			try {
				assertEquals("listening", runtime.state().status(), runtime.state().failure());
				String token = runtime.revealTokenOnce().orElseThrow();
				URI endpoint = URI.create(runtime.state().url());
				String sessionId = initialize(endpoint, token);

				JsonObject initial = call(endpoint, token, sessionId, 2, "get_workspace", new JsonObject());
				assertEquals(0, initial.get("revision").getAsLong());
				assertEquals(WORKSPACE_ID.toString(), initial.get("workspaceId").getAsString());

				JsonObject emptyList = call(endpoint, token, sessionId, 3, "list_mod_elements", new JsonObject());
				assertEquals(0, emptyList.getAsJsonObject("data").getAsJsonArray("items").size());

				JsonObject item = new JsonObject();
				item.addProperty("elementType", "item");
				item.addProperty("name", "agent_blade");
				JsonObject itemValues = new JsonObject();
				itemValues.addProperty("displayName", "Agent Blade");
				item.add("initialValues", itemValues);
				item.addProperty("expectedRevision", 0);
				JsonObject created = call(endpoint, token, sessionId, 4, "create_mod_element", item);
				assertEquals("committed", created.get("status").getAsString());
				assertEquals(1, created.get("newRevision").getAsLong());

				JsonObject planArguments = new JsonObject();
				planArguments.addProperty("expectedRevision", 1);
				planArguments.addProperty("idempotencyKey", "desktop-agent-code-plan");
				JsonArray operations = new JsonArray();
				JsonObject operation = new JsonObject();
				operation.addProperty("operation", "create_mod_element");
				JsonObject codePayload = new JsonObject();
				codePayload.addProperty("elementType", "code");
				codePayload.addProperty("name", "agent_behavior");
				codePayload.add("initialValues", new JsonObject());
				operation.add("payload", codePayload);
				operations.add(operation);
				planArguments.add("operations", operations);
				JsonObject plannedResult = call(endpoint, token, sessionId, 5, "plan_workspace_changes", planArguments);
				assertEquals("succeeded", plannedResult.get("status").getAsString(), plannedResult.toString());
				JsonObject plan = plannedResult.getAsJsonObject("data");

				JsonObject previewArguments = new JsonObject();
				previewArguments.add("plan", plan.deepCopy());
				JsonObject preview = call(endpoint, token, sessionId, 6, "preview_workspace_plan", previewArguments);
				assertTrue(preview.getAsJsonObject("data").get("wouldApply").getAsBoolean());

				JsonObject applyArguments = new JsonObject();
				applyArguments.add("plan", plan.deepCopy());
				applyArguments.addProperty("expectedRevision", 1);
				JsonObject applied = call(endpoint, token, sessionId, 7, "apply_workspace_plan", applyArguments);
				assertEquals("committed", applied.get("status").getAsString(), applied.toString());
				assertEquals(2, applied.get("newRevision").getAsLong());

				JsonObject firstPageArguments = new JsonObject();
				firstPageArguments.addProperty("limit", 1);
				JsonObject firstPage = call(endpoint, token, sessionId, 8, "list_mod_elements", firstPageArguments)
						.getAsJsonObject("data");
				assertEquals(1, firstPage.getAsJsonArray("items").size());
				String cursor = firstPage.get("nextCursor").getAsString();
				JsonObject secondPageArguments = new JsonObject();
				secondPageArguments.addProperty("limit", 1);
				secondPageArguments.addProperty("cursor", cursor);
				JsonObject secondPage = call(endpoint, token, sessionId, 9, "list_mod_elements", secondPageArguments)
						.getAsJsonObject("data");
				assertEquals(1, secondPage.getAsJsonArray("items").size());
				assertTrue(secondPage.has("nextCursor"), secondPage.toString());
				assertTrue(secondPage.get("nextCursor").isJsonNull());

				JsonObject buildArguments = new JsonObject();
				buildArguments.addProperty("expectedRevision", 2);
				JsonObject build = call(endpoint, token, sessionId, 10, "build_workspace", buildArguments);
				assertEquals("accepted", build.get("status").getAsString());
				String taskId = build.getAsJsonObject("task").get("id").getAsString();
				JsonObject taskArguments = new JsonObject();
				taskArguments.addProperty("taskId", taskId);
				taskArguments.addProperty("afterLogSequence", 0);
				JsonObject task = call(endpoint, token, sessionId, 11, "get_task", taskArguments).getAsJsonObject("data");
				assertEquals("succeeded", task.getAsJsonObject("task").get("state").getAsString());
				assertEquals(2, task.getAsJsonArray("logs").size());
				assertEquals(2, task.getAsJsonArray("logs").get(1).getAsJsonObject().get("sequence").getAsLong());

				JsonObject stale = new JsonObject();
				stale.addProperty("elementType", "projectile");
				stale.addProperty("name", "stale_projectile");
				stale.add("initialValues", new JsonObject());
				stale.addProperty("expectedRevision", 1);
				JsonObject conflict = call(endpoint, token, sessionId, 12, "create_mod_element", stale);
				assertEquals("rejected", conflict.get("status").getAsString());
				assertTrue(conflict.getAsJsonArray("diagnostics").toString().contains("WORKSPACE_REVISION_CONFLICT"));

				JsonObject refreshed = call(endpoint, token, sessionId, 13, "get_workspace", new JsonObject());
				assertEquals(2, refreshed.get("revision").getAsLong());
				JsonObject retry = stale.deepCopy();
				retry.addProperty("name", "agent_projectile");
				retry.addProperty("expectedRevision", 2);
				JsonObject retried = call(endpoint, token, sessionId, 14, "create_mod_element", retry);
				assertEquals("committed", retried.get("status").getAsString(), retried.toString());
				assertEquals(3, retried.get("newRevision").getAsLong());

				JsonObject code = new JsonObject();
				code.addProperty("elementType", "code");
				code.addProperty("name", "agent_compiled_behavior");
				JsonObject codeValues = new JsonObject();
				codeValues.addProperty("code", "package net.example; public final class AgentCompiledBehavior {}");
				code.add("initialValues", codeValues);
				code.addProperty("expectedRevision", 3);
				JsonObject codeCreated = call(endpoint, token, sessionId, 15, "create_mod_element", code);
				assertEquals("committed", codeCreated.get("status").getAsString(), codeCreated.toString());
				assertEquals(4, codeCreated.get("newRevision").getAsLong());
				JsonObject verification = codeCreated.getAsJsonObject("data").getAsJsonObject("compileVerification");
				assertEquals("build_workspace", verification.get("operation").getAsString());
				assertEquals("accepted", verification.get("status").getAsString());
				assertEquals("succeeded", verification.getAsJsonObject("task").get("state").getAsString());

				String audit = Files.readString(workspace.resolve(".copperbench/automation-audit.jsonl"));
				assertTrue(audit.contains("plan_workspace_changes"));
				assertTrue(audit.contains("apply_workspace_plan"));
				assertTrue(audit.contains("build_workspace"));
				assertFalse(audit.contains(token));
			} finally {
				runtime.close();
			}
		}
	}

	private static String initialize(URI endpoint, String token) throws Exception {
		HttpResponse<String> initialized = post(endpoint,
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
						"\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," +
						"\"clientInfo\":{\"name\":\"external-agent-loop-test\",\"version\":\"1.0\"}}}", token, null);
		assertEquals(200, initialized.statusCode());
		String sessionId = initialized.headers().firstValue("mcp-session-id").orElseThrow();
		post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token, sessionId);
		return sessionId;
	}

	private static JsonObject call(URI endpoint, String token, String sessionId, int id, String tool,
			JsonObject arguments) throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("name", tool);
		params.add("arguments", arguments);
		JsonObject body = new JsonObject();
		body.addProperty("jsonrpc", "2.0");
		body.addProperty("id", id);
		body.addProperty("method", "tools/call");
		body.add("params", params);
		HttpResponse<String> response = post(endpoint, body.toString(), token, sessionId);
		assertEquals(200, response.statusCode(), response.body());
		return toolResult(response);
	}

	private static HttpResponse<String> post(URI endpoint, String body, String token, String sessionId)
			throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/event-stream")
				.header("X-Copperbench-Workspace", WORKSPACE_ID.toString())
				.header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (sessionId != null) request.header("mcp-session-id", sessionId);
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static JsonObject toolResult(HttpResponse<String> response) {
		String data = response.body().lines().filter(line -> line.startsWith("data: ")).findFirst()
				.orElseThrow(() -> new AssertionError("Missing SSE data: " + response.body())).substring(6);
		JsonObject envelope = JsonParser.parseString(data).getAsJsonObject();
		String text = envelope.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject()
				.get("text").getAsString();
		return JsonParser.parseString(text).getAsJsonObject();
	}

	private static final class CompletedBuildGateway implements WorkspaceTaskGateway {
		private final Supplier<UUID> ids;
		private final Map<UUID, JsonObject> tasks = new LinkedHashMap<>();
		private final Map<UUID, List<JsonObject>> logs = new LinkedHashMap<>();

		private CompletedBuildGateway(Supplier<UUID> ids) {
			this.ids = ids;
		}

		@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
			assertEquals(Operation.BUILD_WORKSPACE, operation);
			UUID taskId = ids.get();
			JsonObject task = new JsonObject();
			task.addProperty("id", taskId.toString());
			task.addProperty("kind", "build");
			task.addProperty("state", "succeeded");
			task.addProperty("cancellable", false);
			task.addProperty("progress", 1.0);
			JsonObject stage = new JsonObject();
			stage.addProperty("key", "task.completed");
			stage.addProperty("fallback", "Build completed");
			stage.add("args", new JsonObject());
			task.add("stage", stage);
			task.addProperty("startedAt", CLOCK.instant().toString());
			task.addProperty("completedAt", CLOCK.instant().toString());
			JsonObject counts = new JsonObject();
			counts.addProperty("error", 0);
			counts.addProperty("warning", 0);
			counts.addProperty("info", 0);
			task.add("diagnostics", counts);
			tasks.put(taskId, task);

			JsonObject first = log(1, "Gradle build started");
			JsonObject second = log(2, "BUILD SUCCESSFUL");
			logs.put(taskId, List.of(first, second));
			return task.deepCopy();
		}

		@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
			JsonObject task = tasks.get(taskId);
			return Optional.ofNullable(task == null ? null : task.deepCopy());
		}

		@Override public List<JsonObject> active(UUID workspaceId) {
			return List.of();
		}

		@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
			return find(workspaceId, taskId);
		}

		@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
			return logs.getOrDefault(taskId, List.of()).stream().map(JsonObject::deepCopy).toList();
		}

		private static JsonObject log(long sequence, String text) {
			JsonObject log = new JsonObject();
			log.addProperty("sequence", sequence);
			log.addProperty("timestamp", CLOCK.instant().toString());
			log.addProperty("level", "info");
			log.addProperty("text", text);
			return log;
		}
	}

	private static final class FailedCompileBuildGateway implements WorkspaceTaskGateway {
		private final Supplier<UUID> ids;
		private final Map<UUID, JsonObject> tasks = new LinkedHashMap<>();
		private final Map<UUID, List<JsonObject>> logs = new LinkedHashMap<>();
		private final Map<UUID, List<JsonObject>> diagnostics = new LinkedHashMap<>();

		private FailedCompileBuildGateway(Supplier<UUID> ids) {
			this.ids = ids;
		}

		@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
			assertEquals(Operation.BUILD_WORKSPACE, operation);
			UUID taskId = ids.get();
			JsonObject task = new JsonObject();
			task.addProperty("id", taskId.toString());
			task.addProperty("kind", "build");
			task.addProperty("state", "failed");
			task.addProperty("cancellable", false);
			task.addProperty("progress", 1.0);
			JsonObject stage = new JsonObject();
			stage.addProperty("key", "task.failed");
			stage.addProperty("fallback", "Build failed");
			stage.add("args", new JsonObject());
			task.add("stage", stage);
			task.addProperty("startedAt", CLOCK.instant().toString());
			task.addProperty("completedAt", CLOCK.instant().toString());
			JsonObject counts = new JsonObject();
			counts.addProperty("error", 1);
			counts.addProperty("warning", 0);
			counts.addProperty("info", 0);
			task.add("diagnostics", counts);
			tasks.put(taskId, task);

			logs.put(taskId, List.of(CompletedBuildGateway.log(1,
					"src/main/java/net/example/Broken.java:1: error: cannot find symbol")));
			JsonObject diagnostic = new JsonObject();
			diagnostic.addProperty("code", "JAVA_COMPILE_ERROR");
			diagnostic.addProperty("severity", "error");
			diagnostic.addProperty("path", "/src/main/java/net/example/Broken.java");
			JsonObject message = new JsonObject();
			message.addProperty("key", "diagnostic.java_compile_error");
			message.addProperty("fallback", "Line 1: cannot find symbol");
			message.add("args", new JsonObject());
			diagnostic.add("message", message);
			diagnostics.put(taskId, List.of(diagnostic));
			return task.deepCopy();
		}

		@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
			JsonObject task = tasks.get(taskId);
			return Optional.ofNullable(task == null ? null : task.deepCopy());
		}

		@Override public List<JsonObject> active(UUID workspaceId) {
			return List.of();
		}

		@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
			return find(workspaceId, taskId);
		}

		@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
			return logs.getOrDefault(taskId, List.of()).stream().map(JsonObject::deepCopy).toList();
		}

		@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
			return diagnostics.getOrDefault(taskId, List.of()).stream().map(JsonObject::deepCopy).toList();
		}
	}
}
