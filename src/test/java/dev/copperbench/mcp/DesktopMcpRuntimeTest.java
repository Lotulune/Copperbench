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
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopMcpRuntimeTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-4000-8000-000000000091");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-02T03:00:00Z"), ZoneOffset.UTC);

	@TempDir Path workspace;

	@Test void desktopRuntimePublishesNonSecretConnectionMetadataAndServesTheOpenedWorkspace() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Desktop MCP Workspace\"}");
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID, adapter(history), CLOCK);
			String token = null;
			try {
				var state = runtime.state();
				assertEquals("listening", state.status(), state.failure());
				assertTrue(state.url().startsWith("http://127.0.0.1:"));
				assertEquals(WORKSPACE_ID, state.workspaceId());
				assertEquals(PermissionProfile.WORKSPACE, state.permissionProfile());
				assertTrue(state.tokenAvailable());

				Path connectionFile = workspace.resolve(".copperbench/mcp-connection.json");
				assertTrue(Files.isRegularFile(connectionFile));
				JsonObject connection = JsonParser.parseString(Files.readString(connectionFile)).getAsJsonObject();
				assertEquals(state.url(), connection.get("url").getAsString());
				assertEquals(WORKSPACE_ID.toString(), connection.get("workspaceId").getAsString());
				assertEquals("workspace", connection.get("permissionProfile").getAsString());
				assertEquals("ui-once", connection.get("tokenDelivery").getAsString());
				assertFalse(connection.has("token"));

				token = runtime.revealTokenOnce().orElseThrow();
				assertTrue(runtime.revealTokenOnce().isEmpty());
				assertFalse(runtime.state().tokenAvailable());
				assertFalse(Files.readString(connectionFile).contains(token));

				URI endpoint = URI.create(state.url());
				HttpResponse<String> initialized = post(endpoint, initializeBody(), token, null);
				assertEquals(200, initialized.statusCode());
				String sessionId = initialized.headers().firstValue("mcp-session-id").orElseThrow();
				post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token,
						sessionId);
				HttpResponse<String> workspaceResult = post(endpoint,
						"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"get_workspace\",\"arguments\":{}}}",
						token, sessionId);
				assertEquals(200, workspaceResult.statusCode());
				assertTrue(workspaceResult.body().contains("Desktop MCP Workspace"));
				assertTrue(workspaceResult.body().contains(WORKSPACE_ID.toString()));
			} finally {
				runtime.close();
			}

			assertEquals("not_started", runtime.state().status());
			assertFalse(Files.exists(workspace.resolve(".copperbench/mcp-connection.json")));
			assertTrue(runtime.revealTokenOnce().isEmpty());
		}
	}

	private static HttpResponse<String> post(URI endpoint, String body, String token, String sessionId)
			throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/event-stream")
				.header("X-Copperbench-Workspace", WORKSPACE_ID.toString())
				.POST(HttpRequest.BodyPublishers.ofString(body));
		request.header("Authorization", "Bearer " + token);
		if (sessionId != null) request.header("mcp-session-id", sessionId);
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String initializeBody() {
		return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
				"\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," +
				"\"clientInfo\":{\"name\":\"desktop-runtime-test\",\"version\":\"1.0\"}}}";
	}

	private static McpWorkspaceEntryAdapter adapter(LocalHistoryService history) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-26.1.2");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "26.1.2");
		generator.addProperty("displayName", "Fabric 26.1.2");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Desktop MCP Workspace", "mod", 7, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(900);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, ids),
				dev.copperbench.core.application.WorkspaceMutationGateway.noOp(), history,
				ignored -> store.read(WORKSPACE_ID).orElseThrow().copy(), CLOCK, ids);
		return new McpWorkspaceEntryAdapter(service, PermissionProfile.WORKSPACE);
	}
}
