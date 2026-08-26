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
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondaryListCursorTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
	private static final RequestContext MCP = new RequestContext(Actor.MCP, PermissionProfile.WORKSPACE);

	@TempDir Path workspace;

	@Test void publishBatchCursorPagesSortsProjectsAndRejectsDatasetChanges() throws Exception {
		writeBatch("alpha", 1, "2026-08-26T10:00:00Z");
		writeBatch("bravo", 5, "2026-08-26T11:00:00Z");
		writeBatch("charlie", 3, "2026-08-26T12:00:00Z");

		WorkspaceApplicationService service = service();
		JsonObject firstPayload = publishCursorPayload(null);
		var first = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.LIST_PUBLISH_BATCHES, firstPayload), MCP);
		assertEquals("succeeded", first.status());
		JsonObject firstData = first.data().getAsJsonObject();
		assertEquals(3, firstData.get("total").getAsInt());
		assertEquals(2, firstData.getAsJsonArray("items").size());
		assertEquals("bravo", firstData.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());
		firstData.getAsJsonArray("items").forEach(raw ->
				assertEquals(Set.of("assetCount", "id", "name"), raw.getAsJsonObject().keySet()));
		String cursor = firstData.get("nextCursor").getAsString();

		var second = service.query(Query.of(uuid(2), WORKSPACE_ID, Operation.LIST_PUBLISH_BATCHES,
				publishCursorPayload(cursor)), MCP);
		assertEquals("succeeded", second.status());
		assertEquals(1, second.data().getAsJsonObject().getAsJsonArray("items").size());
		assertTrue(second.data().getAsJsonObject().get("nextCursor").isJsonNull());

		writeBatch("delta", 9, "2026-08-26T13:00:00Z");
		var staleDataset = service.query(Query.of(uuid(3), WORKSPACE_ID, Operation.LIST_PUBLISH_BATCHES,
				publishCursorPayload(cursor)), MCP);
		assertEquals("rejected", staleDataset.status());
		assertTrue(staleDataset.diagnostics().stream().anyMatch(diagnostic ->
				"LIST_CURSOR_INVALID".equals(diagnostic.code())));
	}

	private WorkspaceApplicationService registryService() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		WorkspaceState state = new WorkspaceState(WORKSPACE_ID, "Registry Lists", "mod", 4, false, generator,
				new JsonObject(), List.of());
		JsonObject registries = new JsonObject();
		JsonArray variables = new JsonArray();
		for (int index = 0; index < 5; index++) {
			JsonObject variable = new JsonObject();
			variable.addProperty("id", UUID.nameUUIDFromBytes(("variable-" + index).getBytes(StandardCharsets.UTF_8)).toString());
			variable.addProperty("kind", "variable");
			variable.addProperty("name", "variable_" + index);
			variable.addProperty("dataType", "number");
			variable.addProperty("scope", "workspace");
			variables.add(variable);
		}
		registries.add("variables", variables);
		registries.add("tags", new JsonArray());
		registries.add("languageKeys", new JsonArray());
		state.replaceRegistries(registries);
		store.register(state);
		Clock clock = Clock.fixed(Instant.parse("2026-08-26T14:00:00Z"), ZoneOffset.UTC);
		return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, () -> uuid(110)),
				WorkspaceMutationGateway.noOp(), null, null, ignored -> workspace, clock, () -> uuid(111));
	}

	@Test void selectedRegistryCursorPagesSortsAndProjectsWithoutChangingLegacyShape() {
		WorkspaceApplicationService service = registryService();

		var legacy = service.query(Query.of(uuid(10), WORKSPACE_ID, Operation.LIST_WORKSPACE_REGISTRIES,
				new JsonObject()), MCP);
		assertEquals("succeeded", legacy.status());
		assertTrue(legacy.data().getAsJsonObject().has("registries"));
		assertEquals(5, legacy.data().getAsJsonObject().getAsJsonObject("registries")
				.getAsJsonArray("variables").size());

		JsonObject payload = registryCursorPayload(null);
		var first = service.query(Query.of(uuid(11), WORKSPACE_ID, Operation.LIST_WORKSPACE_REGISTRIES, payload), MCP);
		assertEquals("succeeded", first.status());
		JsonObject firstData = first.data().getAsJsonObject();
		assertEquals("variables", firstData.get("registry").getAsString());
		assertEquals(5, firstData.get("total").getAsInt());
		assertEquals("variable_4", firstData.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());
		firstData.getAsJsonArray("items").forEach(raw ->
				assertEquals(Set.of("id", "name"), raw.getAsJsonObject().keySet()));
		String cursor = firstData.get("nextCursor").getAsString();

		var second = service.query(Query.of(uuid(12), WORKSPACE_ID, Operation.LIST_WORKSPACE_REGISTRIES,
				registryCursorPayload(cursor)), MCP);
		assertEquals("succeeded", second.status());
		assertEquals(2, second.data().getAsJsonObject().getAsJsonArray("items").size());
	}

	private WorkspaceApplicationService service() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		store.register(new WorkspaceState(WORKSPACE_ID, "Secondary Lists", "mod", 4, false, generator,
				new JsonObject(), List.of()));
		Clock clock = Clock.fixed(Instant.parse("2026-08-26T14:00:00Z"), ZoneOffset.UTC);
		return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, () -> uuid(100)),
				WorkspaceMutationGateway.noOp(), null, null, ignored -> workspace, clock, () -> uuid(101));
	}

	private void writeBatch(String name, int assetCount, String createdAt) throws Exception {
		Path directory = workspace.resolve(".copperbench/publish-batches");
		Files.createDirectories(directory);
		JsonObject batch = new JsonObject();
		batch.addProperty("id", UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString());
		batch.addProperty("name", name);
		batch.addProperty("sourceDirectory", "assets/" + name);
		batch.addProperty("outputPath", "exports/" + name + ".zip");
		batch.addProperty("sha256", "a".repeat(64));
		batch.addProperty("assetCount", assetCount);
		batch.addProperty("createdAt", createdAt);
		JsonArray assets = new JsonArray();
		for (int index = 0; index < assetCount; index++) assets.add("assets/" + name + "/" + index + ".json");
		batch.add("assets", assets);
		Files.writeString(directory.resolve(name + ".json"), batch.toString(), StandardCharsets.UTF_8);
	}

	private static JsonObject publishCursorPayload(String cursor) {
		JsonObject payload = new JsonObject();
		payload.addProperty("limit", 2);
		payload.addProperty("sort", "-assetCount");
		if (cursor != null) payload.addProperty("cursor", cursor);
		payload.add("filter", new JsonObject());
		JsonArray fields = new JsonArray();
		fields.add("id");
		fields.add("name");
		fields.add("assetCount");
		payload.add("fields", fields);
		return payload;
	}

	private static JsonObject registryCursorPayload(String cursor) {
		JsonObject payload = new JsonObject();
		payload.addProperty("registry", "variables");
		payload.addProperty("limit", 2);
		payload.addProperty("sort", "-name");
		if (cursor != null) payload.addProperty("cursor", cursor);
		payload.add("filter", new JsonObject());
		JsonArray fields = new JsonArray();
		fields.add("id");
		fields.add("name");
		payload.add("fields", fields);
		return payload;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
