/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.copperbench.procedure;

import com.google.gson.JsonObject;
import dev.copperbench.procedure.ProcedureIr.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic Core-side baseline for the Stage 9 500-node Procedure payload. */
@EnabledIfSystemProperty(named = "copperbench.stage9.scale", matches = "true")
class ProcedureIrScaleGateTest {

	private static final int NODE_COUNT = 500;
	private static final UUID ELEMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

	@Test void validatesAndRoundTripsFiveHundredNodeProcedure() throws Exception {
		ProcedureIrCodec codec = new ProcedureIrCodec();
		List<Node> nodes = new ArrayList<>(NODE_COUNT);
		UUID previous = null;
		for (int index = 0; index < NODE_COUNT; index++) {
			UUID id = UUID.nameUUIDFromBytes(("stage9-procedure-node-" + index).getBytes(StandardCharsets.UTF_8));
			JsonObject fields = new JsonObject();
			fields.addProperty("value", index);
			nodes.add(new Node(id, index == 0 ? "event_trigger" : "math_number", "value", index * 12, 40,
				fields, Map.of(), previous, false, ""));
			previous = id;
		}
		ProcedureIr original = new ProcedureIr(ProcedureIr.SCHEMA_VERSION, "no_ext_trigger", nodes, List.of(),
				new JsonObject());

		long validationStart = System.nanoTime();
		assertTrue(codec.validate(original).isEmpty());
		long validationMillis = elapsedMillis(validationStart);

		long jsonStart = System.nanoTime();
		JsonObject json = codec.toJson(original);
		ProcedureIr jsonRoundTrip = codec.fromJson(json);
		long jsonRoundTripMillis = elapsedMillis(jsonStart);

		long xmlStart = System.nanoTime();
		String xml = codec.toBlocklyXml(original);
		ProcedureIr xmlRoundTrip = codec.fromBlocklyXml(xml, ELEMENT_ID);
		long xmlRoundTripMillis = elapsedMillis(xmlStart);

		assertEquals(NODE_COUNT, jsonRoundTrip.nodes().size());
		assertEquals(NODE_COUNT, xmlRoundTrip.nodes().size());
		assertTrue(codec.validate(jsonRoundTrip).isEmpty());
		assertTrue(codec.validate(xmlRoundTrip).isEmpty());
		assertTrue(jsonRoundTrip.nodes().get(NODE_COUNT - 1).fields().get("value").getAsInt() == NODE_COUNT - 1);
		assertTrue(validationMillis < 5_000, "Procedure validation exceeded the nightly smoke threshold");
		assertTrue(jsonRoundTripMillis < 5_000, "Procedure JSON round-trip exceeded the nightly smoke threshold");
		assertTrue(xmlRoundTripMillis < 10_000, "Procedure Blockly XML round-trip exceeded the nightly smoke threshold");

		JsonObject evidence = new JsonObject();
		evidence.addProperty("nodes", NODE_COUNT);
		evidence.addProperty("validationMillis", validationMillis);
		evidence.addProperty("jsonRoundTripMillis", jsonRoundTripMillis);
		evidence.addProperty("xmlRoundTripMillis", xmlRoundTripMillis);
		evidence.addProperty("scope", "Core IR/data-integrity baseline; does not close fixed-hardware UI P95 or JCEF gate");
		evidence.addProperty("generatedAt", Instant.now().toString());
		Path output = Path.of("build", "nightly-results", "stage9-procedure-scale.json");
		Files.createDirectories(output.getParent());
		Files.writeString(output, evidence.toString(), StandardCharsets.UTF_8);
	}

	private static long elapsedMillis(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}
}
