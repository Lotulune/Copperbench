/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HeadlessCliTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-4000-8000-000000000020");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);

	@Test void buildValidateAndExportUseTheSharedApplicationServiceAndStableJsonExitCodes() {
		SequentialIds ids = new SequentialIds();
		WorkspaceApplicationService service = service(ids);
		HeadlessCli cli = new HeadlessCli(
				new HeadlessWorkspaceEntryAdapter(service, PermissionProfile.WORKSPACE), WORKSPACE_ID, ids);

		assertSuccessful(cli, "validate", "validate_workspace");
		assertSuccessful(cli, "build", "build_workspace");
		assertSuccessful(cli, "export", "export_workspace", "--output", "exports/copper-trails.zip");
		RunResult release = run(cli, "release");
		assertEquals(HeadlessExitCode.SUCCESS.code(), release.exitCode());
		assertEquals("get_release_notes", release.json().get("operation").getAsString());
		assertEquals("succeeded", release.json().get("status").getAsString());
		RunResult plugins = run(cli, "plugins");
		assertEquals(HeadlessExitCode.SUCCESS.code(), plugins.exitCode());
		assertEquals("list_installed_plugins", plugins.json().get("operation").getAsString());
		assertEquals("succeeded", plugins.json().get("status").getAsString());
		assertFalse(plugins.json().getAsJsonObject("data").get("loadsJava").getAsBoolean());

		RunResult invalid = run(cli, "unknown");
		assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), invalid.exitCode());
		assertEquals("HEADLESS_INVALID_ARGUMENTS", invalid.json().get("code").getAsString());
	}

	@Test void readOnlyHeadlessAllowsValidationButRejectsBuild() {
		SequentialIds ids = new SequentialIds();
		HeadlessCli cli = new HeadlessCli(new HeadlessWorkspaceEntryAdapter(service(ids),
				PermissionProfile.READ_ONLY), WORKSPACE_ID, ids);

		assertSuccessful(cli, "validate", "validate_workspace");
		RunResult denied = run(cli, "build");
		assertEquals(HeadlessExitCode.PERMISSION_DENIED.code(), denied.exitCode());
		assertEquals("rejected", denied.json().get("status").getAsString());
	}

	private static void assertSuccessful(HeadlessCli cli, String command, String operation, String... options) {
		String[] arguments = new String[options.length + 1];
		arguments[0] = command;
		System.arraycopy(options, 0, arguments, 1, options.length);
		RunResult result = run(cli, arguments);
		assertEquals(HeadlessExitCode.SUCCESS.code(), result.exitCode());
		assertEquals("1.0", result.json().get("schemaVersion").getAsString());
		assertEquals(operation, result.json().get("operation").getAsString());
		assertEquals("accepted", result.json().get("status").getAsString());
	}

	private static RunResult run(HeadlessCli cli, String... arguments) {
		StringWriter output = new StringWriter();
		int exitCode = cli.run(arguments, new PrintWriter(output, true));
		return new RunResult(exitCode, JsonParser.parseString(output.toString().trim()).getAsJsonObject());
	}

	private static WorkspaceApplicationService service(Supplier<UUID> ids) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(CLOCK, ids), CLOCK, ids);
	}

	private record RunResult(int exitCode, JsonObject json) {
	}

	private static final class SequentialIds implements Supplier<UUID> {
		private final Queue<UUID> ids = new ArrayDeque<>();

		private SequentialIds() {
			for (int index = 200; index < 260; index++)
				ids.add(UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", index)));
		}

		@Override public UUID get() {
			return ids.remove();
		}
	}
}
