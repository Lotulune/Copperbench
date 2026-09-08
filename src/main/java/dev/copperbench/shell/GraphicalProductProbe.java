/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.copperbench.mcp.DesktopMcpRuntime;
import dev.copperbench.platform.DesktopSessionCapabilities;
import dev.copperbench.platform.PrivatePathPermissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Opt-in installed-product probe used by graphical candidate gates.
 *
 * <p>No file is written during normal product use. The probe is enabled only
 * when {@value #RESULT_PROPERTY} or {@value #RESULT_ENVIRONMENT} points at a
 * result file. It deliberately records MCP metadata but never credentials.</p>
 */
public final class GraphicalProductProbe {

	static final String RESULT_PROPERTY = "copperbench.graphicalProbeResult";
	static final String RESULT_ENVIRONMENT = "COPPERBENCH_GRAPHICAL_PROBE_RESULT";

	private static final Logger LOG = LogManager.getLogger(GraphicalProductProbe.class);
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Object WRITE_LOCK = new Object();

	private GraphicalProductProbe() {
	}

	public static void browserReady(UUID workspaceId, Path workspaceRoot, DesktopMcpRuntime mcpRuntime) {
		writeConfigured("ready", true, null, workspaceId, workspaceRoot, mcpRuntime,
				DesktopSessionCapabilities.current(), System.getenv(), System.getProperties(),
				Path.of(System.getProperty("user.dir", ".")));
	}

	public static void rendererTerminated(UUID workspaceId, Path workspaceRoot,
			DesktopMcpRuntime mcpRuntime, String reason) {
		writeConfigured("renderer_terminated", false, reason, workspaceId, workspaceRoot, mcpRuntime,
				DesktopSessionCapabilities.current(), System.getenv(), System.getProperties(),
				Path.of(System.getProperty("user.dir", ".")));
	}

	private static void writeConfigured(String status, boolean jcefReady, String reason, UUID workspaceId,
			Path workspaceRoot, DesktopMcpRuntime mcpRuntime, DesktopSessionCapabilities capabilities,
			Map<String, String> environment, Properties properties, Path userDirectory) {
		Path result = configuredResultPath(environment, properties, userDirectory);
		if (result == null) return;
		try {
			writeResult(result, status, jcefReady, reason, workspaceId, workspaceRoot, mcpRuntime.state(), capabilities);
		} catch (Exception exception) {
			LOG.warn("Could not write graphical product probe result to {}", result, exception);
		}
	}

	static Path configuredResultPath(Map<String, String> environment, Properties properties, Path userDirectory) {
		String configured = properties.getProperty(RESULT_PROPERTY);
		if (configured == null || configured.isBlank()) configured = environment.get(RESULT_ENVIRONMENT);
		if (configured == null || configured.isBlank()) return null;
		Path path = Path.of(configured.trim());
		if (!path.isAbsolute()) path = userDirectory.toAbsolutePath().normalize().resolve(path);
		return path.toAbsolutePath().normalize();
	}

	static void writeResult(Path result, String status, boolean jcefReady, String reason, UUID workspaceId,
			Path workspaceRoot, DesktopMcpRuntime.RuntimeState mcpState, DesktopSessionCapabilities capabilities)
			throws IOException {
		JsonObject payload = new JsonObject();
		payload.addProperty("schemaVersion", "1.0");
		payload.addProperty("probe", "graphical-product-shell");
		payload.addProperty("status", status);
		payload.addProperty("observedAt", Instant.now().toString());
		payload.addProperty("formalSupportClaim", false);
		payload.addProperty("jcefMainFrameLoaded", jcefReady);
		if (reason == null) payload.add("rendererTerminationReason", com.google.gson.JsonNull.INSTANCE);
		else payload.addProperty("rendererTerminationReason", reason);
		payload.addProperty("workspaceId", workspaceId.toString());
		payload.addProperty("workspaceRoot", workspaceRoot.toAbsolutePath().normalize().toString());
		payload.add("platform", capabilities.toJson());
		payload.add("mcp", mcpState.toJson());

		Path target = result.toAbsolutePath().normalize();
		Path parent = target.getParent();
		if (parent != null) Files.createDirectories(parent);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		synchronized (WRITE_LOCK) {
			Files.writeString(temporary, JSON.toJson(payload) + System.lineSeparator(), StandardCharsets.UTF_8);
			PrivatePathPermissions.makePrivateFile(temporary);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			PrivatePathPermissions.makePrivateFile(target);
		}
	}
}
