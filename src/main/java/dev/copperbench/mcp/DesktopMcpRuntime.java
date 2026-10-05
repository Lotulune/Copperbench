/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.automation.audit.AuditRecord;
import dev.copperbench.automation.audit.JsonLineAuditLog;
import dev.copperbench.automation.security.WorkspaceToken;
import dev.copperbench.automation.security.WorkspaceTokenService;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.platform.PrivatePathPermissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the loopback MCP server and its workspace-scoped desktop credentials. */
public final class DesktopMcpRuntime implements AutoCloseable {

	private static final Logger LOG = LogManager.getLogger(DesktopMcpRuntime.class);
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Duration TOKEN_TTL = Duration.ofHours(12);
	private static final Duration TOKEN_RENEWAL_LEAD = Duration.ofMinutes(5);

	private final UUID workspaceId;
	private PermissionProfile permissionProfile = PermissionProfile.WORKSPACE;
	private final Path connectionFile;
	private final Path settingsFile;
	private final Path workspaceRoot;
	private final McpWorkspaceEntryAdapter adapter;
	private final JsonLineAuditLog audit;
	private final WorkspaceTokenService tokens;
	private final Clock clock;
	private final AtomicReference<WorkspaceToken> activeToken = new AtomicReference<>();
	private CopperbenchMcpServer server;
	private String endpoint;
	private String failure;
	private final AtomicReference<String> oneTimeToken = new AtomicReference<>();
	private final ScheduledExecutorService tokenRenewal;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private DesktopMcpRuntime(Path workspaceRoot, UUID workspaceId, McpWorkspaceEntryAdapter adapter, Clock clock) {
		this.workspaceId = workspaceId;
		this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
		this.connectionFile = this.workspaceRoot.resolve(".copperbench/mcp-connection.json");
		this.settingsFile = this.workspaceRoot.resolve(".copperbench/mcp-settings.json");
		this.adapter = adapter;
		this.audit = new JsonLineAuditLog(this.workspaceRoot.resolve(".copperbench/automation-audit.jsonl"));
		this.tokens = new WorkspaceTokenService(clock, TOKEN_TTL);
		this.clock = clock;
		this.tokenRenewal = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "Copperbench-MCP-token-renewal-" + workspaceId);
			thread.setDaemon(true);
			return thread;
		});
	}

	public static DesktopMcpRuntime start(Path workspaceRoot, UUID workspaceId, McpWorkspaceEntryAdapter adapter,
			Clock clock) {
		DesktopMcpRuntime runtime = new DesktopMcpRuntime(workspaceRoot, workspaceId, adapter, clock);
		try {
			if (Files.exists(runtime.settingsFile)) {
				JsonObject settings = JsonParser.parseString(Files.readString(runtime.settingsFile,
						StandardCharsets.UTF_8)).getAsJsonObject();
				if (!settings.has("schemaVersion") || !settings.get("schemaVersion").isJsonPrimitive()
						|| !settings.getAsJsonPrimitive("schemaVersion").isString()
						|| !settings.get("schemaVersion").getAsString().equals("1.0"))
					throw new IllegalArgumentException("Unsupported MCP settings version");
				if (!settings.has("permissionProfile") || !settings.get("permissionProfile").isJsonPrimitive()
						|| !settings.getAsJsonPrimitive("permissionProfile").isString())
					throw new IllegalArgumentException("Invalid stored MCP permission profile");
				runtime.permissionProfile = parsePermissionProfile(settings.get("permissionProfile").getAsString());
			}
			runtime.startServer(0);
		} catch (Exception exception) {
			runtime.failClosed(exception);
		}
		runtime.tokenRenewal.scheduleWithFixedDelay(runtime::renewTokenSafely, 1, 1, TimeUnit.MINUTES);
		return runtime;
	}

	public synchronized RuntimeState state() {
		renewTokenIfNeeded();
		WorkspaceToken token = activeToken.get();
		return new RuntimeState(server != null && !closed.get() ? "listening" : "not_started", endpoint, workspaceId,
				permissionProfile, token == null ? null : token.expiresAt(), oneTimeToken.get() != null, failure);
	}

	public synchronized Optional<String> revealTokenOnce() {
		if (closed.get()) return Optional.empty();
		renewTokenIfNeeded();
		return Optional.ofNullable(oneTimeToken.getAndSet(null));
	}

	public Path connectionFile() {
		return connectionFile;
	}

	/** Desktop-only: retires the old authenticated sessions before changing the Core permission boundary. */
	public synchronized RuntimeState setPermissionProfile(PermissionProfile profile) {
		Objects.requireNonNull(profile, "Permission profile is required");
		if (closed.get()) throw new IllegalStateException("Desktop MCP runtime is closed");
		if (server != null && permissionProfile == profile) return state();
		int port = server == null ? 0 : server.address().getPort();
		PermissionProfile previous = permissionProfile;
		try {
			stopServer();
			permissionProfile = profile;
			startServer(port);
			JsonObject settings = new JsonObject();
			settings.addProperty("schemaVersion", "1.0");
			settings.addProperty("permissionProfile", wire(profile));
			audit.append(new AuditRecord(clock.instant(), "desktop-ui", "set_mcp_permission_profile",
					wire(previous) + " -> " + wire(profile), "selected", -1, null));
			writePrivateJson(settingsFile, settings);
			failure = null;
		} catch (Exception exception) {
			failClosed(exception);
		}
		return state();
	}

	public static PermissionProfile parsePermissionProfile(String value) {
		return switch (value) {
			case "read_only" -> PermissionProfile.READ_ONLY;
			case "workspace" -> PermissionProfile.WORKSPACE;
			case "full_access" -> PermissionProfile.FULL_ACCESS;
			default -> throw new IllegalArgumentException("Unknown MCP permission profile");
		};
	}

	private void startServer(int port) throws Exception {
		WorkspaceToken token = tokens.issue(workspaceId, permissionProfile);
		server = CopperbenchMcpServer.start(new McpServerConfiguration(port, workspaceId, permissionProfile,
				Set.of("http://mcreator", "http://localhost:5173", "http://127.0.0.1:5173"), clock),
				tokens, adapter.withPermissionProfile(permissionProfile), audit, new AssetWorkspaceService(workspaceRoot));
		endpoint = "http://127.0.0.1:" + server.address().getPort() + "/mcp";
		writeConnectionFile(connectionFile, endpoint, workspaceId, permissionProfile, token.expiresAt());
		activeToken.set(token);
		oneTimeToken.set(token.value());
	}

	private void failClosed(Exception exception) {
		try {
			stopServer();
		} catch (RuntimeException cleanupFailure) {
			exception.addSuppressed(cleanupFailure);
		}
		failure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
		LOG.error("Could not configure desktop MCP for workspace {}", workspaceId, exception);
	}

	@Override public synchronized void close() {
		if (!closed.compareAndSet(false, true)) return;
		tokenRenewal.shutdownNow();
		stopServer();
	}

	private void stopServer() {
		oneTimeToken.set(null);
		activeToken.set(null);
		tokens.revokeWorkspace(workspaceId);
		RuntimeException failure = null;
		CopperbenchMcpServer previous = server;
		server = null;
		endpoint = null;
		if (previous != null) {
			try {
				previous.close();
			} catch (RuntimeException exception) {
				failure = exception;
			}
		}
		try {
			Files.deleteIfExists(connectionFile);
		} catch (Exception exception) {
			if (failure == null) failure = new IllegalStateException("Could not remove MCP connection file", exception);
			else failure.addSuppressed(exception);
		}
		if (failure != null) throw failure;
	}

	private void renewTokenSafely() {
		try {
			renewTokenIfNeeded();
		} catch (RuntimeException exception) {
			LOG.warn("Could not renew desktop MCP token for workspace {}", workspaceId, exception);
		}
	}

	private synchronized void renewTokenIfNeeded() {
		if (closed.get() || server == null) return;
		WorkspaceToken current = activeToken.get();
		if (current == null || clock.instant().isBefore(current.expiresAt().minus(TOKEN_RENEWAL_LEAD))) return;

		WorkspaceToken replacement = tokens.issue(workspaceId, permissionProfile);
		try {
			writeConnectionFile(connectionFile, endpoint, workspaceId, permissionProfile, replacement.expiresAt());
		} catch (Exception exception) {
			tokens.revoke(replacement.value());
			throw new IllegalStateException("Could not publish renewed MCP connection metadata", exception);
		}
		activeToken.set(replacement);
		oneTimeToken.set(replacement.value());
	}

	private static void writeConnectionFile(Path connectionFile, String endpoint, UUID workspaceId,
			PermissionProfile permission, Instant expiresAt) throws Exception {
		JsonObject connection = new JsonObject();
		connection.addProperty("schemaVersion", "1.0");
		connection.addProperty("status", "listening");
		connection.addProperty("url", endpoint);
		connection.addProperty("workspaceId", workspaceId.toString());
		connection.addProperty("permissionProfile", wire(permission));
		connection.addProperty("expiresAt", expiresAt.toString());
		connection.addProperty("tokenDelivery", "ui-once");
		writePrivateJson(connectionFile, connection);
	}

	private static void writePrivateJson(Path target, JsonObject value) throws Exception {
		PrivatePathPermissions.createPrivateDirectory(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			PrivatePathPermissions.makePrivateFile(temporary);
			Files.writeString(temporary, JSON.toJson(value) + System.lineSeparator(), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static String wire(PermissionProfile permission) {
		return switch (permission) {
			case READ_ONLY -> "read_only";
			case WORKSPACE -> "workspace";
			case FULL_ACCESS -> "full_access";
		};
	}

	public record RuntimeState(String status, String url, UUID workspaceId, PermissionProfile permissionProfile,
			Instant expiresAt, boolean tokenAvailable, String failure) {

		public JsonObject toJson() {
			JsonObject result = new JsonObject();
			result.addProperty("status", status);
			if (url == null) result.add("url", com.google.gson.JsonNull.INSTANCE);
			else result.addProperty("url", url);
			result.addProperty("workspaceId", workspaceId.toString());
			result.addProperty("permissionProfile", wire(permissionProfile));
			if (expiresAt == null) result.add("expiresAt", com.google.gson.JsonNull.INSTANCE);
			else result.addProperty("expiresAt", expiresAt.toString());
			result.addProperty("tokenAvailable", tokenAvailable);
			if (failure == null) result.add("failure", com.google.gson.JsonNull.INSTANCE);
			else result.addProperty("failure", failure);
			return result;
		}
	}
}
