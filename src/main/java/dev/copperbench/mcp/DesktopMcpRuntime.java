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
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.automation.audit.JsonLineAuditLog;
import dev.copperbench.automation.security.WorkspaceToken;
import dev.copperbench.automation.security.WorkspaceTokenService;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
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
	private final PermissionProfile permissionProfile;
	private final Path connectionFile;
	private final WorkspaceTokenService tokens;
	private final Clock clock;
	private final AtomicReference<WorkspaceToken> activeToken;
	private final CopperbenchMcpServer server;
	private final String endpoint;
	private final String failure;
	private final AtomicReference<String> oneTimeToken;
	private final ScheduledExecutorService tokenRenewal;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private DesktopMcpRuntime(UUID workspaceId, PermissionProfile permissionProfile, Path connectionFile,
			WorkspaceTokenService tokens, WorkspaceToken token, CopperbenchMcpServer server, String endpoint,
			Clock clock, String failure) {
		this.workspaceId = workspaceId;
		this.permissionProfile = permissionProfile;
		this.connectionFile = connectionFile;
		this.tokens = tokens;
		this.clock = clock;
		this.activeToken = new AtomicReference<>(token);
		this.server = server;
		this.endpoint = endpoint;
		this.failure = failure;
		this.oneTimeToken = new AtomicReference<>(token == null ? null : token.value());
		if (token == null) {
			this.tokenRenewal = null;
		} else {
			this.tokenRenewal = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "Copperbench-MCP-token-renewal-" + workspaceId);
				thread.setDaemon(true);
				return thread;
			});
			this.tokenRenewal.scheduleWithFixedDelay(this::renewTokenSafely, 1, 1, TimeUnit.MINUTES);
		}
	}

	public static DesktopMcpRuntime start(Path workspaceRoot, UUID workspaceId, McpWorkspaceEntryAdapter adapter,
			Clock clock) {
		Path root = workspaceRoot.toAbsolutePath().normalize();
		Path connectionFile = root.resolve(".copperbench/mcp-connection.json").normalize();
		PermissionProfile permission = PermissionProfile.WORKSPACE;
		WorkspaceTokenService tokens = new WorkspaceTokenService(clock, TOKEN_TTL);
		WorkspaceToken token = tokens.issue(workspaceId, permission);
		CopperbenchMcpServer server = null;
		try {
			server = CopperbenchMcpServer.start(new McpServerConfiguration(0, workspaceId, permission,
					Set.of("http://mcreator", "http://localhost:5173", "http://127.0.0.1:5173"), clock),
					tokens, adapter, new JsonLineAuditLog(root.resolve(".copperbench/automation-audit.jsonl")),
					new AssetWorkspaceService(root));
			String endpoint = "http://127.0.0.1:" + server.address().getPort() + "/mcp";
			writeConnectionFile(connectionFile, endpoint, workspaceId, permission, token.expiresAt());
			return new DesktopMcpRuntime(workspaceId, permission, connectionFile, tokens, token, server, endpoint, clock,
					null);
		} catch (Exception exception) {
			if (server != null) {
				try {
					server.close();
				} catch (RuntimeException closeFailure) {
					exception.addSuppressed(closeFailure);
				}
			}
			tokens.revoke(token.value());
			try {
				Files.deleteIfExists(connectionFile);
			} catch (Exception cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			LOG.error("Could not start desktop MCP for workspace {}", workspaceId, exception);
			return new DesktopMcpRuntime(workspaceId, permission, connectionFile, tokens, null, null, null, clock,
					exception.getClass().getSimpleName() + ": " + exception.getMessage());
		}
	}

	public RuntimeState state() {
		renewTokenIfNeeded();
		WorkspaceToken token = activeToken.get();
		return new RuntimeState(server != null && !closed.get() ? "listening" : "not_started", endpoint, workspaceId,
				permissionProfile, token == null ? null : token.expiresAt(), oneTimeToken.get() != null, failure);
	}

	public Optional<String> revealTokenOnce() {
		if (closed.get()) return Optional.empty();
		renewTokenIfNeeded();
		return Optional.ofNullable(oneTimeToken.getAndSet(null));
	}

	public Path connectionFile() {
		return connectionFile;
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		if (tokenRenewal != null) tokenRenewal.shutdownNow();
		oneTimeToken.set(null);
		activeToken.set(null);
		tokens.revokeWorkspace(workspaceId);
		RuntimeException failure = null;
		if (server != null) {
			try {
				server.close();
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
		Files.createDirectories(connectionFile.getParent());
		JsonObject connection = new JsonObject();
		connection.addProperty("schemaVersion", "1.0");
		connection.addProperty("status", "listening");
		connection.addProperty("url", endpoint);
		connection.addProperty("workspaceId", workspaceId.toString());
		connection.addProperty("permissionProfile", wire(permission));
		connection.addProperty("expiresAt", expiresAt.toString());
		connection.addProperty("tokenDelivery", "ui-once");
		Path temporary = connectionFile.resolveSibling(connectionFile.getFileName() + ".tmp");
		Files.writeString(temporary, JSON.toJson(connection) + System.lineSeparator(), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, connectionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, connectionFile, StandardCopyOption.REPLACE_EXISTING);
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
