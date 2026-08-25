/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.copperbench.references;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "copperbench.stage9.scale", matches = "true")
class WorkspaceReferenceIndexScaleTest {

	private static final int ELEMENT_COUNT = 2_000;
	private static final int REFERENCES_PER_ELEMENT = 5;

	@Test void indexesTwoThousandElementsAndTenThousandReferencesWithoutLoss() throws Exception {
		List<UUID> ids = new ArrayList<>(ELEMENT_COUNT);
		for (int index = 0; index < ELEMENT_COUNT; index++)
			ids.add(UUID.nameUUIDFromBytes(("scale-element-" + index).getBytes(StandardCharsets.UTF_8)));

		List<Element> elements = new ArrayList<>(ELEMENT_COUNT);
		for (int index = 0; index < ELEMENT_COUNT; index++) {
			JsonArray references = new JsonArray();
			for (int offset = 1; offset <= REFERENCES_PER_ELEMENT; offset++) {
				JsonObject reference = new JsonObject();
				reference.addProperty("target", ids.get((index + offset) % ELEMENT_COUNT).toString());
				references.add(reference);
			}
			JsonObject values = new JsonObject();
			values.add("references", references);
			elements.add(new Element(ids.get(index), "function", "function_" + index, "Function " + index,
					"valid", "owned", Instant.EPOCH, values));
		}
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		WorkspaceState state = new WorkspaceState(UUID.nameUUIDFromBytes("scale-workspace".getBytes(StandardCharsets.UTF_8)),
				"Scale Gate", "mod", 1, false, generator, new JsonObject(), elements);

		WorkspaceReferenceIndex index = new WorkspaceReferenceIndex();
		long initialStart = System.nanoTime();
		JsonObject initial = index.projection(state, "");
		long initialMillis = elapsedMillis(initialStart);
		long repeatStart = System.nanoTime();
		JsonObject repeated = index.projection(state, "");
		long repeatMillis = elapsedMillis(repeatStart);

		assertEquals(ELEMENT_COUNT, initial.getAsJsonArray("nodes").size());
		assertEquals(ELEMENT_COUNT * REFERENCES_PER_ELEMENT, initial.getAsJsonArray("edges").size());
		assertEquals(0, initial.getAsJsonArray("diagnostics").size());
		assertEquals(initial.getAsJsonArray("edges").size(), repeated.getAsJsonArray("edges").size());
		assertTrue(initialMillis < 10_000, "Initial reference projection exceeded the nightly smoke threshold");
		assertTrue(repeatMillis < 5_000, "Repeat reference projection exceeded the nightly smoke threshold");

		JsonObject result = new JsonObject();
		result.addProperty("elements", ELEMENT_COUNT);
		result.addProperty("references", ELEMENT_COUNT * REFERENCES_PER_ELEMENT);
		result.addProperty("initialMillis", initialMillis);
		result.addProperty("repeatMillis", repeatMillis);
		result.addProperty("scope", "reference-index smoke; does not close the fixed-hardware UI P95 gate");
		Path output = Path.of("build", "nightly-results", "stage9-reference-scale.json");
		Files.createDirectories(output.getParent());
		Files.writeString(output, result.toString(), StandardCharsets.UTF_8);
	}

	private static long elapsedMillis(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}
}
