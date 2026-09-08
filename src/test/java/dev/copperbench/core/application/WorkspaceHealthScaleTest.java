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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "copperbench.stage9.scale", matches = "true")
class WorkspaceHealthScaleTest {

	private static final int ELEMENT_COUNT = 2_000;
	private static final int REFERENCES_PER_ELEMENT = 5;
	private static final UUID WORKSPACE_ID = UUID.nameUUIDFromBytes(
			"workspace-health-scale".getBytes(StandardCharsets.UTF_8));
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);

	@Test void aggregatesTwoThousandElementsAndTenThousandReferencesWithinNightlyCeiling() throws Exception {
		List<UUID> ids = new ArrayList<>(ELEMENT_COUNT);
		for (int index = 0; index < ELEMENT_COUNT; index++)
			ids.add(UUID.nameUUIDFromBytes(("health-element-" + index).getBytes(StandardCharsets.UTF_8)));

		List<Element> elements = new ArrayList<>(ELEMENT_COUNT);
		for (int index = 0; index < ELEMENT_COUNT; index++) {
			JsonArray targets = new JsonArray();
			for (int offset = 1; offset <= REFERENCES_PER_ELEMENT; offset++) {
				JsonObject reference = new JsonObject();
				reference.addProperty("target", ids.get((index + offset) % ELEMENT_COUNT).toString());
				targets.add(reference);
			}
			JsonObject values = new JsonObject();
			values.add("references", targets);
			elements.add(new Element(ids.get(index), "function", "health_function_" + index,
					"Health Function " + index, "valid", "generated", Instant.EPOCH, values));
		}

		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		store.register(new WorkspaceState(WORKSPACE_ID, "Workspace Health Scale", "mod", 1, false,
				generator, new JsonObject(), elements));
		AtomicLong sequence = new AtomicLong(50_000);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())), CLOCK,
				() -> uuid(sequence.getAndIncrement()));
		RequestContext context = new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE);

		long initialStart = System.nanoTime();
		var initial = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.GET_WORKSPACE_HEALTH,
				new JsonObject()), context);
		long initialMillis = elapsedMillis(initialStart);
		long repeatStart = System.nanoTime();
		var repeated = service.query(Query.of(uuid(2), WORKSPACE_ID, Operation.GET_WORKSPACE_HEALTH,
				new JsonObject()), context);
		long repeatMillis = elapsedMillis(repeatStart);

		assertEquals("succeeded", initial.status());
		assertEquals(ELEMENT_COUNT, initial.data().getAsJsonObject().getAsJsonObject("elements").get("total").getAsInt());
		assertEquals(ELEMENT_COUNT * REFERENCES_PER_ELEMENT,
				initial.data().getAsJsonObject().getAsJsonObject("references").get("edgeCount").getAsInt());
		assertEquals(0, initial.data().getAsJsonObject().getAsJsonObject("references").get("danglingCount").getAsInt());
		assertEquals(0, initial.data().getAsJsonObject().getAsJsonObject("diagnostics").get("total").getAsInt());
		assertEquals(ELEMENT_COUNT * REFERENCES_PER_ELEMENT,
				repeated.data().getAsJsonObject().getAsJsonObject("references").get("edgeCount").getAsInt());
		assertTrue(initialMillis < 10_000, "Initial Workspace Health aggregation exceeded the nightly smoke threshold");
		assertTrue(repeatMillis < 5_000, "Repeat Workspace Health aggregation exceeded the nightly smoke threshold");

		JsonObject result = new JsonObject();
		result.addProperty("elements", ELEMENT_COUNT);
		result.addProperty("references", ELEMENT_COUNT * REFERENCES_PER_ELEMENT);
		result.addProperty("initialMillis", initialMillis);
		result.addProperty("repeatMillis", repeatMillis);
		result.addProperty("scope", "Workspace Health Core aggregation smoke");
		Path output = Path.of("build", "nightly-results", "stage13-workspace-health-scale.json");
		Files.createDirectories(output.getParent());
		Files.writeString(output, result.toString(), StandardCharsets.UTF_8);
	}

	private static long elapsedMillis(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
