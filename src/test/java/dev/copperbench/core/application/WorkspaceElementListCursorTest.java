/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
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
import dev.copperbench.core.workspace.WorkspaceState.Element;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceElementListCursorTest {

	private static final int ELEMENT_COUNT = 2_000;
	private static final int LIMIT = 137;
	private static final UUID WORKSPACE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T12:00:00Z"), ZoneOffset.UTC);

	@Test void cursorTraversesTwoThousandElementsWithoutOmissionOrDuplication() throws Exception {
		WorkspaceApplicationService service = service();
		RequestContext context = new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE);
		Set<String> seen = new HashSet<>();
		String cursor = null;
		int calls = 0;
		long started = System.nanoTime();

		do {
			JsonObject payload = cursorPayload(cursor, "", "name", List.of("id", "name"));
			var result = service.query(Query.of(uuid(10_000 + calls), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS, payload),
					context);
			assertEquals("succeeded", result.status());
			JsonObject data = result.data().getAsJsonObject();
			assertEquals(ELEMENT_COUNT, data.get("total").getAsInt());
			JsonArray items = data.getAsJsonArray("items");
			assertTrue(items.size() <= LIMIT);
			items.forEach(item -> assertEquals(Set.of("id", "name"), item.getAsJsonObject().keySet()));
			items.forEach(item -> assertTrue(seen.add(item.getAsJsonObject().get("id").getAsString()),
					"cursor traversal returned a duplicate element"));
			cursor = data.get("nextCursor").isJsonNull() ? null : data.get("nextCursor").getAsString();
			calls++;
		} while (cursor != null);

		assertEquals(ELEMENT_COUNT, seen.size());
		assertEquals((ELEMENT_COUNT + LIMIT - 1) / LIMIT, calls);
		long traversalMillis = (System.nanoTime() - started) / 1_000_000;
		JsonObject evidence = new JsonObject();
		evidence.addProperty("elements", ELEMENT_COUNT);
		evidence.addProperty("limit", LIMIT);
		evidence.addProperty("calls", calls);
		evidence.addProperty("uniqueElements", seen.size());
		evidence.addProperty("traversalMillis", traversalMillis);
		evidence.addProperty("sort", "name");
		evidence.add("fields", array("id", "name"));
		evidence.addProperty("scope", "cursor correctness smoke; does not close fixed-hardware workspace P95 gate");
		Path output = Path.of("build", "nightly-results", "stage10-element-cursor.json");
		Files.createDirectories(output.getParent());
		Files.writeString(output, evidence.toString(), StandardCharsets.UTF_8);
	}

	@Test void cursorRejectsFilterChanges() {
		WorkspaceApplicationService service = service();
		RequestContext context = new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE);
		var first = service.query(Query.of(uuid(20_000), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS,
				cursorPayload(null, "", "name", List.of("id", "name"))), context);
		assertEquals("succeeded", first.status());
		String cursor = first.data().getAsJsonObject().get("nextCursor").getAsString();
		assertNotNull(cursor);

		var mismatched = service.query(Query.of(uuid(20_001), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS,
				cursorPayload(cursor, "element_01", "name", List.of("id", "name"))), context);
		assertEquals("rejected", mismatched.status());
		assertTrue(mismatched.diagnostics().stream().anyMatch(diagnostic ->
				"LIST_CURSOR_INVALID".equals(diagnostic.code())));
	}

	@Test void cursorRejectsSortChangesAndStaleRevisionsWithStableCodes() {
		RequestContext context = new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE);
		WorkspaceApplicationService revisionSeven = service(7);
		var first = revisionSeven.query(Query.of(uuid(21_000), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS,
				cursorPayload(null, "", "-name", List.of("id", "name"))), context);
		assertEquals("succeeded", first.status());
		JsonObject firstData = first.data().getAsJsonObject();
		assertEquals("element_1999", firstData.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());
		String cursor = firstData.get("nextCursor").getAsString();

		var changedSort = revisionSeven.query(Query.of(uuid(21_001), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS,
				cursorPayload(cursor, "", "name", List.of("id", "name"))), context);
		assertEquals("rejected", changedSort.status());
		assertTrue(changedSort.diagnostics().stream().anyMatch(diagnostic ->
				"LIST_CURSOR_INVALID".equals(diagnostic.code())));

		WorkspaceApplicationService revisionEight = service(8);
		var stale = revisionEight.query(Query.of(uuid(21_002), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS,
				cursorPayload(cursor, "", "-name", List.of("id", "name"))), context);
		assertEquals("rejected", stale.status());
		assertTrue(stale.diagnostics().stream().anyMatch(diagnostic ->
				"LIST_CURSOR_STALE".equals(diagnostic.code())));
	}

	@Test void cursorFilterSupportsTypeSelection() {
		WorkspaceApplicationService service = service();
		RequestContext context = new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE);
		JsonObject payload = cursorPayload(null, "", "name", List.of("id", "type"));
		payload.getAsJsonObject("filter").add("types", array("block"));
		var result = service.query(Query.of(uuid(22_000), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS, payload), context);
		assertEquals("succeeded", result.status());
		JsonObject data = result.data().getAsJsonObject();
		assertEquals(ELEMENT_COUNT / 2, data.get("total").getAsInt());
		data.getAsJsonArray("items").forEach(item ->
				assertEquals("block", item.getAsJsonObject().get("type").getAsString()));
	}

	private static WorkspaceApplicationService service() {
		return service(7);
	}

	private static WorkspaceApplicationService service(long revision) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		List<Element> elements = new ArrayList<>(ELEMENT_COUNT);
		for (int index = 0; index < ELEMENT_COUNT; index++) {
			UUID id = UUID.nameUUIDFromBytes(("cursor-element-" + index).getBytes(StandardCharsets.UTF_8));
			String name = String.format("element_%04d", index);
			elements.add(new Element(id, index % 2 == 0 ? "block" : "item", name, "Element " + index,
					"valid", "generated", Instant.EPOCH, new JsonObject()));
		}
		store.register(new WorkspaceState(WORKSPACE_ID, "Cursor Scale", "mod", revision, false, generator,
				new JsonObject(), elements));
		AtomicLong sequence = new AtomicLong(30_000);
		return new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())), CLOCK,
				() -> uuid(sequence.getAndIncrement()));
	}

	private static JsonObject cursorPayload(String cursor, String search, String sort, List<String> fields) {
		JsonObject payload = new JsonObject();
		payload.addProperty("limit", LIMIT);
		if (cursor != null) payload.addProperty("cursor", cursor);
		payload.addProperty("sort", sort);
		JsonObject filter = new JsonObject();
		filter.addProperty("search", search);
		filter.add("types", new JsonArray());
		filter.add("states", new JsonArray());
		payload.add("filter", filter);
		JsonArray requestedFields = new JsonArray();
		fields.forEach(requestedFields::add);
		payload.add("fields", requestedFields);
		return payload;
	}

	private static JsonArray array(String... values) {
		JsonArray result = new JsonArray();
		for (String value : values) result.add(value);
		return result;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
