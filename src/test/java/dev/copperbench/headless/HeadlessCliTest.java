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
import org.junit.jupiter.api.BeforeAll;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessCliTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-4000-8000-000000000020");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);
	private static final UUID PROCEDURE_ID = UUID.fromString("00000000-0000-4000-8000-000000000021");

	@BeforeAll static void configureLogDirectory() {
		System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
	}

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

	@Test void newWorkspaceQueryAndCreateCommandShareCoreApprovalRules() {
		SequentialIds ids = new SequentialIds();
		HeadlessCli cli = new HeadlessCli(
				new HeadlessWorkspaceEntryAdapter(service(ids), PermissionProfile.WORKSPACE), WORKSPACE_ID, ids);

		RunResult help = run(cli, "help");
		assertTrue(help.json().getAsJsonArray("commands").toString().contains("list-new-workspace-generators"));
		assertTrue(help.json().getAsJsonArray("commands").toString().contains("create-workspace"));
		assertTrue(help.json().getAsJsonArray("commands").toString().contains("preview-datagen"));
		assertTrue(help.json().getAsJsonArray("commands").toString().contains("publish-datagen"));

		RunResult generators = run(cli, "list-new-workspace-generators");
		assertEquals(HeadlessExitCode.SUCCESS.code(), generators.exitCode());
		assertEquals("list_new_workspace_generators", generators.json().get("operation").getAsString());
		assertEquals("succeeded", generators.json().get("status").getAsString());
		assertEquals(9, generators.json().getAsJsonObject("data").getAsJsonArray("generators").size());
		assertTrue(generators.json().getAsJsonObject("data").getAsJsonArray("generators").toString()
				.contains("resourcepack-1.21.1"));

		RunResult unapproved = run(cli, "create-workspace", "--generator-id", "fabric-1.21.1", "--mod-name",
				"Copper Trails", "--mod-id", "copper_trails", "--workspace-folder",
				"C:\\Users\\example\\MCreatorWorkspaces\\copper_trails", "--approve", "false");
		assertEquals(HeadlessExitCode.PERMISSION_DENIED.code(), unapproved.exitCode());
		assertEquals("create_workspace", unapproved.json().get("operation").getAsString());
		assertEquals("rejected", unapproved.json().get("status").getAsString());
		assertTrue(unapproved.json().getAsJsonArray("diagnostics").toString().contains("USER_APPROVAL_REQUIRED"));

		RunResult selfApproved = run(cli, "create-workspace", "--generator-id", "fabric-1.21.1", "--mod-name",
				"Copper Trails", "--mod-id", "Invalid ID!", "--workspace-folder",
				"C:\\Users\\example\\MCreatorWorkspaces\\invalid", "--approve", "true");
		assertEquals(HeadlessExitCode.PERMISSION_DENIED.code(), selfApproved.exitCode());
		assertTrue(selfApproved.json().getAsJsonArray("diagnostics").toString()
				.contains("USER_APPROVAL_REQUIRED"));
	}

	@Test void datagenPublicationCommandsRequireStagingIdentityAndManifestHash() {
		SequentialIds ids = new SequentialIds();
		HeadlessCli cli = new HeadlessCli(
				new HeadlessWorkspaceEntryAdapter(service(ids), PermissionProfile.WORKSPACE), WORKSPACE_ID, ids);

		RunResult missingTask = run(cli, "preview-datagen");
		assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), missingTask.exitCode());
		RunResult preview = run(cli, "preview-datagen", "--task-id", PROCEDURE_ID.toString());
		assertEquals("preview_datagen_output", preview.json().get("operation").getAsString());
		RunResult missingHash = run(cli, "publish-datagen", "--task-id", PROCEDURE_ID.toString());
		assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), missingHash.exitCode());
	}

	@Test void procedureQueriesAndStructuredUpdatesUseTheSharedCoreContract() {
		SequentialIds ids = new SequentialIds();
		HeadlessCli cli = new HeadlessCli(
				new HeadlessWorkspaceEntryAdapter(service(ids), PermissionProfile.WORKSPACE), WORKSPACE_ID, ids);

		RunResult editor = run(cli, "procedure", "--element-id", PROCEDURE_ID.toString());
		assertEquals(HeadlessExitCode.SUCCESS.code(), editor.exitCode());
		assertEquals("get_procedure_editor", editor.json().get("operation").getAsString());
		assertTrue(editor.json().getAsJsonObject("data").has("ir"));

		String edits = "[{\"operation\":\"set_trigger\",\"trigger\":\"on_block_right_clicked\"}]";
		RunResult preview = run(cli, "preview-procedure", "--element-id", PROCEDURE_ID.toString(),
				"--edits-json", edits);
		assertEquals(HeadlessExitCode.SUCCESS.code(), preview.exitCode());
		assertEquals("on_block_right_clicked", preview.json().getAsJsonObject("data")
				.getAsJsonObject("candidateIr").get("trigger").getAsString());

		RunResult updated = run(cli, "update-procedure", "--element-id", PROCEDURE_ID.toString(),
				"--edits-json", edits);
		assertEquals(HeadlessExitCode.SUCCESS.code(), updated.exitCode());
		assertEquals("update_procedure", updated.json().get("operation").getAsString());
		assertEquals(1, updated.json().get("newRevision").getAsLong());
	}

	@Test void registryCrudIsAvailableToHeadlessWithoutBypassingCoreRevisions() {
		SequentialIds ids = new SequentialIds();
		HeadlessCli cli = new HeadlessCli(
				new HeadlessWorkspaceEntryAdapter(service(ids), PermissionProfile.WORKSPACE), WORKSPACE_ID, ids);

		RunResult created = run(cli, "create-registry-entry", "--registry", "variables", "--entry-json",
				"{\"name\":\"score\",\"dataType\":\"number\",\"scope\":\"global\"}");
		assertEquals(HeadlessExitCode.SUCCESS.code(), created.exitCode());
		assertEquals(1, created.json().get("newRevision").getAsLong());
		String entryId = created.json().getAsJsonObject("data").getAsJsonObject("entry").get("id").getAsString();

		RunResult renamed = run(cli, "rename-registry-entry", "--revision", "1", "--entry-id", entryId,
				"--new-name", "trail_score");
		assertEquals(HeadlessExitCode.SUCCESS.code(), renamed.exitCode());
		assertEquals(2, renamed.json().get("newRevision").getAsLong());

		RunResult listed = run(cli, "registries", "--registry", "variables");
		assertEquals(HeadlessExitCode.SUCCESS.code(), listed.exitCode());
		assertTrue(listed.json().getAsJsonObject("data").getAsJsonArray("variables").toString()
				.contains("trail_score"));
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
		WorkspaceState.Element procedure = new WorkspaceState.Element(PROCEDURE_ID, "procedure", "announce_trail",
				"Announce Trail", "valid", "owned", CLOCK.instant(), new JsonObject());
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of(procedure)));
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
