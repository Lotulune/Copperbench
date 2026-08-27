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
import dev.copperbench.automation.audit.JsonLineAuditLog;
import dev.copperbench.automation.security.WorkspaceTokenService;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Test-source launcher used only by the official MCP conformance CLI. */
public final class McpConformanceServerMain {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private McpConformanceServerMain() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1 || args.length > 2)
			throw new IllegalArgumentException("Expected a run directory and optional permission profile");
		String runDirectoryArgument = args[0];
		String inlineProfile = "";
		int profileSeparator = runDirectoryArgument.lastIndexOf(',');
		if (args.length == 1 && profileSeparator > 1) {
			inlineProfile = runDirectoryArgument.substring(profileSeparator + 1);
			runDirectoryArgument = runDirectoryArgument.substring(0, profileSeparator);
		}
		Path runDirectory = Path.of(runDirectoryArgument).toAbsolutePath();
		String requestedProfile = args.length == 2 ? args[1] : inlineProfile;
		PermissionProfile permission = requestedProfile.equals("read_only")
				? PermissionProfile.READ_ONLY : PermissionProfile.WORKSPACE;
		Path workspace = runDirectory.resolve("workspace");
		Path connectionFile = runDirectory.resolve("connection.json");
		Files.createDirectories(workspace);
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"name\":\"Conformance Workspace\"}",
				StandardCharsets.UTF_8);

		Clock clock = Clock.systemUTC();
		WorkspaceTokenService tokens = new WorkspaceTokenService(clock, Duration.ofMinutes(15));
		var token = tokens.issue(WORKSPACE_ID, permission);
		var history = JGitLocalHistoryService.open(workspace, clock);
		var server = CopperbenchMcpServer.start(
				new McpServerConfiguration(0, WORKSPACE_ID, permission,
						Set.of("http://localhost:61999", "http://127.0.0.1:61999"), clock),
				tokens, adapter(clock, history, permission),
				new JsonLineAuditLog(workspace.resolve(".copperbench/automation-audit.jsonl")));

		JsonObject connection = new JsonObject();
		connection.addProperty("port", server.address().getPort());
		connection.addProperty("token", token.value());
		connection.addProperty("workspaceId", WORKSPACE_ID.toString());
		connection.addProperty("permissionProfile", permission == PermissionProfile.READ_ONLY ? "read_only" : "workspace");
		Files.createDirectories(connectionFile.getParent());
		Files.writeString(connectionFile, connection.toString(), StandardCharsets.UTF_8);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			server.close();
			history.close();
		}));
		new CountDownLatch(1).await();
	}

	private static McpWorkspaceEntryAdapter adapter(Clock clock, LocalHistoryService history,
			PermissionProfile permission) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Conformance Workspace", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(800);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, ids),
				WorkspaceMutationGateway.noOp(), history,
				ignored -> store.read(WORKSPACE_ID).orElseThrow().copy(), clock, ids);
		return new McpWorkspaceEntryAdapter(service, permission);
	}
}
