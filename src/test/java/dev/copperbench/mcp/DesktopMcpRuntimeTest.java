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
import dev.copperbench.platform.PrivatePathPermissions;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

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
				if (PrivatePathPermissions.posixSupported(connectionFile)) {
					assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
							Files.getPosixFilePermissions(connectionFile));
					assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
							PosixFilePermission.OWNER_EXECUTE),
							Files.getPosixFilePermissions(connectionFile.getParent()));
				}

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

	@Test void desktopRuntimeRenewsCredentialsBeforeExpiryWithoutRestartingTheWorkspace() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Desktop MCP Workspace\"}");
		MutableClock clock = new MutableClock(Instant.parse("2026-09-02T03:00:00Z"));
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, clock);
				DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID, adapter(history), clock)) {
			var initialState = runtime.state();
			String initialToken = runtime.revealTokenOnce().orElseThrow();
			Instant initialExpiry = initialState.expiresAt();

			clock.advance(Duration.ofHours(11).plusMinutes(56));
			var renewedState = runtime.state();
			assertEquals("listening", renewedState.status());
			assertTrue(renewedState.expiresAt().isAfter(initialExpiry));
			assertTrue(renewedState.tokenAvailable());
			String renewedToken = runtime.revealTokenOnce().orElseThrow();
			assertFalse(initialToken.equals(renewedToken));

			JsonObject connection = JsonParser.parseString(Files.readString(
					workspace.resolve(".copperbench/mcp-connection.json"))).getAsJsonObject();
			assertEquals(renewedState.expiresAt().toString(), connection.get("expiresAt").getAsString());
			URI endpoint = URI.create(renewedState.url());
			assertEquals(200, post(endpoint, initializeBody(), initialToken, null).statusCode(),
					"the previous credential must remain valid during the renewal overlap");

			clock.advance(Duration.ofMinutes(5));
			assertEquals(401, post(endpoint, initializeBody(), initialToken, null).statusCode());
			assertEquals(200, post(endpoint, initializeBody(), renewedToken, null).statusCode());
		}
	}

	@Test void permissionChangesRevokeAllOldCredentialsAndEnforceTheSelectedCoreProfile() throws Exception {
		MutableClock clock = new MutableClock(CLOCK.instant());
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, clock);
				DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID, adapter(history), clock)) {
			URI endpoint = URI.create(runtime.state().url());
			String originalToken = runtime.revealTokenOnce().orElseThrow();
			String originalSession = initialize(endpoint, originalToken);
			clock.advance(Duration.ofHours(11).plusMinutes(56));
			String renewedToken = runtime.revealTokenOnce().orElseThrow();
			assertEquals(200, post(endpoint, initializeBody(), originalToken, null).statusCode());

			var readOnly = runtime.setPermissionProfile(PermissionProfile.READ_ONLY);
			assertEquals("listening", readOnly.status(), readOnly.failure());
			assertEquals(endpoint.toString(), readOnly.url(), "changing profile preserves the configured URL");
			assertEquals(401, post(endpoint, initializeBody(), originalToken, null).statusCode());
			assertEquals(401, post(endpoint, initializeBody(), renewedToken, originalSession).statusCode());
			String readToken = runtime.revealTokenOnce().orElseThrow();
			String readSession = initialize(endpoint, readToken);
			assertEquals(404, post(endpoint, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}",
					readToken, originalSession).statusCode(), "a replacement token cannot revive an old session");
			JsonObject read = call(endpoint, readToken, readSession, "get_workspace", "{}");
			assertEquals("read_only", read.getAsJsonObject("data").getAsJsonObject("permission").get("profile").getAsString());
			String create = "{\"elementType\":\"item\",\"name\":\"permission_item\",\"initialValues\":{},\"expectedRevision\":7}";
			JsonObject denied = call(endpoint, readToken, readSession, "create_mod_element", create);
			assertEquals("rejected", denied.get("status").getAsString(), denied.toString());
			assertEquals("PERMISSION_DENIED", denied.getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString());
			assertEquals("read_only", denied.getAsJsonObject("denial").get("currentProfile").getAsString());
			assertEquals(7, call(endpoint, readToken, readSession, "get_workspace", "{}").get("revision").getAsLong());

			var writable = runtime.setPermissionProfile(PermissionProfile.WORKSPACE);
			assertEquals("listening", writable.status(), writable.failure());
			assertEquals(401, post(endpoint, initializeBody(), readToken, null).statusCode());
			String writeToken = runtime.revealTokenOnce().orElseThrow();
			String writeSession = initialize(endpoint, writeToken);
			JsonObject created = call(endpoint, writeToken, writeSession, "create_mod_element", create);
			assertEquals("committed", created.get("status").getAsString(), created.toString());
			assertEquals(8, created.get("newRevision").getAsLong());
			assertFalse(runtime.setPermissionProfile(PermissionProfile.WORKSPACE).tokenAvailable(),
					"selecting the current profile must not rotate credentials again");
			assertEquals(200, post(endpoint, initializeBody(), writeToken, null).statusCode());

			var full = runtime.setPermissionProfile(PermissionProfile.FULL_ACCESS);
			assertEquals("listening", full.status(), full.failure());
			assertEquals(401, post(endpoint, initializeBody(), writeToken, null).statusCode());
			String fullToken = runtime.revealTokenOnce().orElseThrow();
			String fullSession = initialize(endpoint, fullToken);
			JsonObject fullState = call(endpoint, fullToken, fullSession, "get_workspace", "{}");
			assertEquals("full_access", fullState.getAsJsonObject("data").getAsJsonObject("permission").get("profile").getAsString());
			assertTrue(fullState.getAsJsonObject("data").getAsJsonObject("permission").get("protectedOperationsAlwaysConfirm").getAsBoolean());
			JsonObject protectedImport = call(endpoint, fullToken, fullSession, "import_upstream_workspace",
					"{\"sourceWorkspacePath\":\"unused\",\"outputName\":\"copy\",\"userApproved\":false,\"expectedRevision\":8}");
			assertEquals("rejected", protectedImport.get("status").getAsString());
			assertTrue(protectedImport.getAsJsonObject("denial").get("protectedOperation").getAsBoolean());
			var listing = post(endpoint, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}", fullToken, fullSession);
			assertFalse(listing.body().contains("set_permission_profile"), "agents cannot change their own profile");
			assertTrue(Files.readString(runtime.connectionFile()).contains("full_access"));
			String audit = Files.readString(workspace.resolve(".copperbench/automation-audit.jsonl"));
			assertTrue(audit.contains("set_mcp_permission_profile"));
			assertFalse(audit.contains(fullToken));
			assertFalse(Files.readString(runtime.connectionFile()).contains(fullToken));
		}
	}

	@Test void selectedPermissionSurvivesReopeningOnlyThisWorkspace() throws Exception {
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			String oldToken;
			try (DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID, adapter(history), CLOCK)) {
				assertEquals("listening", runtime.setPermissionProfile(PermissionProfile.READ_ONLY).status());
				oldToken = runtime.revealTokenOnce().orElseThrow();
			}
			try (DesktopMcpRuntime reopened = DesktopMcpRuntime.start(workspace, WORKSPACE_ID, adapter(history), CLOCK)) {
				assertEquals("listening", reopened.state().status(), reopened.state().failure());
				assertEquals(PermissionProfile.READ_ONLY, reopened.state().permissionProfile());
				URI endpoint = URI.create(reopened.state().url());
				assertEquals(401, post(endpoint, initializeBody(), oldToken, null).statusCode());
				String token = reopened.revealTokenOnce().orElseThrow();
				String session = initialize(endpoint, token);
				assertEquals("read_only", call(endpoint, token, session, "get_workspace", "{}")
						.getAsJsonObject("data").getAsJsonObject("permission").get("profile").getAsString());
			}
			Path other = Files.createDirectory(workspace.resolve("other-workspace"));
			try (DesktopMcpRuntime unrelated = DesktopMcpRuntime.start(other, WORKSPACE_ID, adapter(history), CLOCK)) {
				assertEquals(PermissionProfile.WORKSPACE, unrelated.state().permissionProfile());
			}
		}
	}

	@Test void failedSettingsPublicationStopsTheServerAndAllowsExplicitRetry() throws Exception {
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID, adapter(history), CLOCK)) {
			Path settings = Files.createDirectory(workspace.resolve(".copperbench/mcp-settings.json"));
			Path blocker = Files.writeString(settings.resolve("blocker"), "owned test fixture");
			var failed = runtime.setPermissionProfile(PermissionProfile.READ_ONLY);
			assertEquals("not_started", failed.status());
			assertNull(failed.url());
			assertFalse(failed.tokenAvailable());
			assertFalse(Files.exists(runtime.connectionFile()));
			assertFalse(failed.failure().isBlank());
			Files.delete(blocker);
			Files.delete(settings);
			var retried = runtime.setPermissionProfile(PermissionProfile.READ_ONLY);
			assertEquals("listening", retried.status(), retried.failure());
			assertNull(retried.failure());
		}
	}

	@Test void invalidStoredPermissionsFailClosedUntilTheUserSelectsAValidProfile() throws Exception {
		Files.createDirectories(workspace.resolve(".copperbench"));
		Files.writeString(workspace.resolve(".copperbench/mcp-settings.json"),
				"{\"schemaVersion\":\"1.0\",\"permissionProfile\":\"unrestricted\"}");
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				DesktopMcpRuntime runtime = DesktopMcpRuntime.start(workspace, WORKSPACE_ID, adapter(history), CLOCK)) {
			assertEquals("not_started", runtime.state().status());
			assertTrue(runtime.revealTokenOnce().isEmpty());
			assertEquals("listening", runtime.setPermissionProfile(PermissionProfile.READ_ONLY).status());
			runtime.close();
			assertThrows(IllegalStateException.class, () -> runtime.setPermissionProfile(PermissionProfile.FULL_ACCESS));
		}
	}

	private static String initialize(URI endpoint, String token) throws Exception {
		var response = post(endpoint, initializeBody(), token, null);
		assertEquals(200, response.statusCode(), response.body());
		String session = response.headers().firstValue("mcp-session-id").orElseThrow();
		post(endpoint, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token, session);
		return session;
	}

	private static JsonObject call(URI endpoint, String token, String session, String name, String arguments)
			throws Exception {
		String body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"" +
				name + "\",\"arguments\":" + arguments + "}}";
		var response = post(endpoint, body, token, session);
		assertEquals(200, response.statusCode(), response.body());
		String data = response.body().lines().filter(line -> line.startsWith("data: ")).findFirst().orElseThrow().substring(6);
		String text = JsonParser.parseString(data).getAsJsonObject().getAsJsonObject("result")
				.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
		return JsonParser.parseString(text).getAsJsonObject();
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

	private static final class MutableClock extends Clock {
		private volatile Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override public Instant instant() {
			return instant;
		}
	}
}
