/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.LegacyWorkspaceEntryAdapter;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceStateReloader;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryContractTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);
	private static final Gson GSON = UiCore.wireGson();
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

	@TempDir Path workspaceDirectory;

	@Test void historyQueriesListRecoveryPointsAndDiffIdenticallyAcrossEntryAdapters() throws Exception {
		Fixture fixture = fixture(stateAtRestoredPoint());
		createPoint(fixture.service, "Before AI edit");
		Files.writeString(workspaceDirectory.resolve("workspace.mcreator"), "{\"revision\":1}");
		createPoint(fixture.service, "After AI edit");

		Query history = Query.of(uuid(10), WORKSPACE_ID, Operation.GET_HISTORY, new JsonObject());
		var legacy = GSON.toJsonTree(new LegacyWorkspaceEntryAdapter(fixture.service).query(history));
		var mcp = GSON.toJsonTree(new McpWorkspaceEntryAdapter(fixture.service, PermissionProfile.WORKSPACE)
				.query(history));
		var headless = GSON.toJsonTree(new HeadlessWorkspaceEntryAdapter(fixture.service, PermissionProfile.WORKSPACE)
				.query(history));
		assertEquals(legacy, mcp);
		assertEquals(legacy, headless);

		JsonObject projection = legacy.getAsJsonObject().getAsJsonObject("data");
		assertEquals(2, projection.getAsJsonArray("recoveryPoints").size());
		assertEquals("After AI edit", projection.getAsJsonArray("recoveryPoints").get(0).getAsJsonObject()
				.get("label").getAsString());

		JsonObject diffPayload = new JsonObject();
		diffPayload.addProperty("fromRecoveryPointId", pointId(projection, 1));
		diffPayload.addProperty("toRecoveryPointId", pointId(projection, 0));
		var diff = fixture.service.query(Query.of(uuid(11), WORKSPACE_ID, Operation.GET_DIFF, diffPayload), UI);
		assertEquals("succeeded", diff.status());
		assertEquals(1, diff.data().getAsJsonObject().getAsJsonArray("changes").size());
		assertEquals("workspace.mcreator", diff.data().getAsJsonObject().getAsJsonArray("changes").get(0)
				.getAsJsonObject().get("path").getAsString());
	}

	@Test void restoreIsAProtectedOperationAndCannotRunWithoutExplicitUserApproval() throws Exception {
		Fixture fixture = fixture(stateAtRestoredPoint());
		String pointId = createPoint(fixture.service, "Checkpoint");

		JsonObject payload = new JsonObject();
		payload.addProperty("recoveryPointId", pointId);
		Command restore = Command.of(uuid(20), WORKSPACE_ID, 0, Operation.RESTORE_RECOVERY_POINT, payload);

		CommandOutcome outcome = fixture.service.execute(restore, UI);
		assertEquals("rejected", outcome.result().status());
		assertEquals("USER_APPROVAL_REQUIRED", outcome.result().diagnostics().get(0).code());
		assertTrue(outcome.result().denial().getAsJsonObject().get("protectedOperation").getAsBoolean());
		assertTrue(outcome.result().denial().getAsJsonObject().get("approvalRequired").getAsBoolean());

		// MCP actors cannot silently restore either (AI never bypasses protected operations).
		CommandOutcome mcpOutcome = new McpWorkspaceEntryAdapter(fixture.service, PermissionProfile.FULL_ACCESS)
				.execute(restore);
		assertEquals("rejected", mcpOutcome.result().status());
		assertTrue(mcpOutcome.result().denial().getAsJsonObject().get("protectedOperation").getAsBoolean());
	}

	@Test void approvedRestoreReplacesStateAdvancesRevisionAndEmitsRestoredEvent() throws Exception {
		AtomicReference<WorkspaceState> restoredState = new AtomicReference<>(stateAtRestoredPoint());
		Fixture fixture = fixture(restoredState.get());
		String pointId = createPoint(fixture.service, "Checkpoint");

		// The mutation gateway is a no-op in this fixture, so persist the
		// change on disk the way the real session would before restoring.
		Files.writeString(workspaceDirectory.resolve("workspace.mcreator"), "{\"revision\":1,\"signal\":true}");
		JsonObject createPayload = new JsonObject();
		createPayload.addProperty("elementType", "block");
		createPayload.addProperty("name", "signal_lantern");
		createPayload.add("initialValues", new JsonObject());
		fixture.service.execute(Command.of(uuid(30), WORKSPACE_ID, 0, Operation.CREATE_MOD_ELEMENT, createPayload),
				UI);
		assertEquals(1, fixture.store.read(WORKSPACE_ID).orElseThrow().revision());

		JsonObject payload = new JsonObject();
		payload.addProperty("recoveryPointId", pointId);
		payload.addProperty("userApproved", true);
		CommandOutcome outcome = fixture.service.execute(
				Command.of(uuid(31), WORKSPACE_ID, 1, Operation.RESTORE_RECOVERY_POINT, payload), UI);

		assertEquals("committed", outcome.result().status());
		assertEquals(2, outcome.result().newRevision());
		assertEquals(pointId, outcome.result().recoveryPointId());
		assertFalse(outcome.result().data().getAsJsonObject().getAsJsonArray("changedPaths").isEmpty());
		assertEquals("workspace_restored", outcome.events().get(0).event());

		WorkspaceState after = fixture.store.read(WORKSPACE_ID).orElseThrow();
		assertEquals(2, after.revision());
		assertTrue(after.elements().isEmpty());

		// Restoring again with the stale revision produces a structured conflict.
		Files.writeString(workspaceDirectory.resolve("workspace.mcreator"),
				"{\"revision\":2,\"afterRestoreLocalChange\":true}");
		JsonObject stale = new JsonObject();
		stale.addProperty("recoveryPointId", pointId);
		stale.addProperty("userApproved", true);
		CommandOutcome conflict = fixture.service.execute(
				Command.of(uuid(32), WORKSPACE_ID, 1, Operation.RESTORE_RECOVERY_POINT, stale), UI);
		assertEquals("rejected", conflict.result().status());
		assertTrue(conflict.result().conflict().isJsonObject());
		assertEquals("{\"revision\":2,\"afterRestoreLocalChange\":true}",
				Files.readString(workspaceDirectory.resolve("workspace.mcreator")));

		JsonObject pointPayload = new JsonObject();
		pointPayload.addProperty("label", "After restore");
		CommandOutcome nextEvent = fixture.service.execute(
				Command.of(uuid(33), WORKSPACE_ID, 2, Operation.CREATE_RECOVERY_POINT, pointPayload), UI);
		assertEquals("committed", nextEvent.result().status());
		assertTrue(nextEvent.events().get(0).sequence() > outcome.events().get(0).sequence());
	}

	@Test void readOnlySessionsCannotCreateRecoveryPoints() throws Exception {
		Fixture fixture = fixture(stateAtRestoredPoint());
		JsonObject payload = new JsonObject();
		payload.addProperty("label", "Read only attempt");
		CommandOutcome outcome = fixture.service.execute(
				Command.of(uuid(40), WORKSPACE_ID, 0, Operation.CREATE_RECOVERY_POINT, payload),
				new RequestContext(Actor.MCP, PermissionProfile.READ_ONLY));
		assertEquals("rejected", outcome.result().status());
		assertTrue(outcome.result().denial().isJsonObject());
	}

	private String createPoint(WorkspaceApplicationService service, String label) {
		JsonObject payload = new JsonObject();
		payload.addProperty("label", label);
		long revision = fixtureRevision(service);
		CommandOutcome outcome = service.execute(
				Command.of(UUID.randomUUID(), WORKSPACE_ID, revision, Operation.CREATE_RECOVERY_POINT, payload), UI);
		assertEquals("committed", outcome.result().status());
		return outcome.result().recoveryPointId();
	}

	private long fixtureRevision(WorkspaceApplicationService service) {
		return service.query(Query.of(UUID.randomUUID(), WORKSPACE_ID, Operation.GET_WORKBENCH, new JsonObject()),
				UI).revision();
	}

	private static String pointId(com.google.gson.JsonElement historyProjection, int index) {
		return historyProjection.getAsJsonObject().getAsJsonArray("recoveryPoints").get(index).getAsJsonObject()
				.get("id").getAsString();
	}

	private Fixture fixture(WorkspaceState restoredSnapshot) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		WorkspaceState initial = stateAtRestoredPoint();
		store.register(initial);
		SequentialIds ids = new SequentialIds();
		InMemoryWorkspaceTaskGateway tasks = new InMemoryWorkspaceTaskGateway(CLOCK, ids);
		JGitLocalHistoryService history;
		try {
			history = JGitLocalHistoryService.open(workspaceDirectory, CLOCK);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		WorkspaceStateReloader reloader = workspaceId -> restoredSnapshot.copy();
		return new Fixture(store, new WorkspaceApplicationService(store, tasks,
				dev.copperbench.core.application.WorkspaceMutationGateway.noOp(), history, reloader, CLOCK, ids));
	}

	private static WorkspaceState stateAtRestoredPoint() {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator, new JsonObject(),
				List.of());
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}

	private record Fixture(RevisionedWorkspaceStore store, WorkspaceApplicationService service) {
	}

	private static final class SequentialIds implements Supplier<UUID> {
		private long next = 100;

		@Override public UUID get() {
			return uuid(next++);
		}
	}
}
