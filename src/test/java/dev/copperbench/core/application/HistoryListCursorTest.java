/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryListCursorTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T12:00:00Z"), ZoneOffset.UTC);
	private static final RequestContext MCP = new RequestContext(Actor.MCP, PermissionProfile.WORKSPACE);

	@TempDir Path workspace;

	@Test void historyCursorPagesProjectsAndRejectsDatasetChanges() throws Exception {
		WorkspaceApplicationService service = service();
		for (int index = 0; index < 5; index++) createPoint(service, "Cursor point " + index);

		var first = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.GET_HISTORY,
				historyCursorPayload(null)), MCP);
		assertEquals("succeeded", first.status());
		JsonObject firstData = first.data().getAsJsonObject();
		assertEquals(5, firstData.get("total").getAsInt());
		assertEquals(2, firstData.getAsJsonArray("recoveryPoints").size());
		firstData.getAsJsonArray("recoveryPoints").forEach(raw ->
				assertEquals(Set.of("id", "label"), raw.getAsJsonObject().keySet()));
		String cursor = firstData.get("nextCursor").getAsString();

		var second = service.query(Query.of(uuid(2), WORKSPACE_ID, Operation.GET_HISTORY,
				historyCursorPayload(cursor)), MCP);
		assertEquals("succeeded", second.status());
		assertEquals(2, second.data().getAsJsonObject().getAsJsonArray("recoveryPoints").size());

		// Recovery-point creation does not advance the workspace revision, so
		// dataset identity must also participate in cursor validity.
		createPoint(service, "Cursor point added later");
		var changedDataset = service.query(Query.of(uuid(3), WORKSPACE_ID, Operation.GET_HISTORY,
				historyCursorPayload(cursor)), MCP);
		assertEquals("rejected", changedDataset.status());
		assertTrue(changedDataset.diagnostics().stream().anyMatch(diagnostic ->
				"LIST_CURSOR_INVALID".equals(diagnostic.code())));
	}

	private WorkspaceApplicationService service() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		store.register(new WorkspaceState(WORKSPACE_ID, "History Cursor", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(100);
		JGitLocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
		return new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())),
				WorkspaceMutationGateway.noOp(), history, workspaceId -> store.read(workspaceId).orElseThrow().copy(),
				CLOCK, () -> uuid(sequence.getAndIncrement()));
	}

	private static void createPoint(WorkspaceApplicationService service, String label) {
		JsonObject payload = new JsonObject();
		payload.addProperty("label", label);
		var result = service.execute(Command.of(UUID.randomUUID(), WORKSPACE_ID, 0,
				Operation.CREATE_RECOVERY_POINT, payload), MCP);
		assertEquals("committed", result.result().status());
	}

	private static JsonObject historyCursorPayload(String cursor) {
		JsonObject payload = new JsonObject();
		payload.addProperty("limit", 2);
		payload.addProperty("sort", "label");
		if (cursor != null) payload.addProperty("cursor", cursor);
		payload.add("filter", new JsonObject());
		JsonArray fields = new JsonArray();
		fields.add("id");
		fields.add("label");
		payload.add("fields", fields);
		return payload;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
