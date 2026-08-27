package dev.copperbench.procedure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcedureIrCodecTest {

	private static final UUID ELEMENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID TRIGGER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID UNKNOWN_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

	@Test void keepsUnknownBlocklyBlocksAtomicAndByteExactAcrossStructuredMoves() {
		String unknown = "<block type=\"plugin_future_block\" id=\"" + UNKNOWN_ID
				+ "\" x=\"240\" y=\"80\"><field name=\"opaque\">A &amp; B</field>"
				+ "<value name=\"nested\"><block type=\"math_number\"><field name=\"NUM\">7</field>"
				+ "</block></value></block>";
		String xml = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
				+ "<block type=\"event_trigger\" id=\"" + TRIGGER_ID + "\" x=\"40\" y=\"40\">"
				+ "<field name=\"trigger\">no_ext_trigger</field></block>" + unknown + "</xml>";
		ProcedureIrCodec codec = new ProcedureIrCodec();

		ProcedureIr original = codec.fromBlocklyXml(xml, ELEMENT_ID);
		ProcedureIr.Node opaque = original.nodes().stream().filter(ProcedureIr.Node::unknown).findFirst().orElseThrow();
		assertEquals(unknown, opaque.rawPayload());
		assertEquals(2, original.nodes().size(), "children of an unknown block must remain inside its opaque payload");

		JsonObject move = new JsonObject();
		move.addProperty("operation", "move_node");
		move.addProperty("nodeId", UNKNOWN_ID.toString());
		move.addProperty("x", 420);
		move.addProperty("y", 160);
		JsonArray edits = new JsonArray();
		edits.add(move);
		ProcedureIr moved = codec.applyEdits(original, edits);

		ProcedureIr.Node movedOpaque = moved.nodeIndex().get(UNKNOWN_ID);
		assertEquals(420, movedOpaque.x());
		assertEquals(unknown, movedOpaque.rawPayload());
		assertTrue(codec.toBlocklyXml(moved).contains(unknown));
	}

	@Test void keepsNestedUnknownBlocklyBlocksByteExactInsideKnownParents() {
		String unknown = "<block type=\"plugin_future_statement\" id=\"" + UNKNOWN_ID
				+ "\"><field name=\"opaque\">nested &amp; exact</field></block>";
		String xml = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
				+ "<block type=\"event_trigger\" id=\"" + TRIGGER_ID + "\">"
				+ "<field name=\"trigger\">no_ext_trigger</field><next>" + unknown + "</next></block></xml>";
		ProcedureIrCodec codec = new ProcedureIrCodec();

		ProcedureIr parsed = codec.fromBlocklyXml(xml, ELEMENT_ID);
		ProcedureIr.Node trigger = parsed.nodeIndex().get(TRIGGER_ID);
		ProcedureIr.Node opaque = parsed.nodeIndex().get(UNKNOWN_ID);

		assertEquals(UNKNOWN_ID, trigger.next());
		assertEquals(unknown, opaque.rawPayload());
		assertTrue(codec.toBlocklyXml(parsed).contains("<next>" + unknown + "</next>"));
	}

	@Test void preservesOuterUnknownPayloadWhenNestedBlocksShareAnIdentity() {
		String inner = "<block type=\"plugin_duplicate\"><field name=\"opaque\">inner</field></block>";
		String outer = "<block type=\"plugin_duplicate\"><field name=\"opaque\">outer</field>"
				+ "<value name=\"nested\">" + inner + "</value></block>";
		String xml = "<xml xmlns=\"https://developers.google.com/blockly/xml\">" + outer + "</xml>";
		ProcedureIrCodec codec = new ProcedureIrCodec();

		ProcedureIr parsed = codec.fromBlocklyXml(xml, ELEMENT_ID);
		ProcedureIr.Node opaque = parsed.nodes().stream().filter(ProcedureIr.Node::unknown).findFirst().orElseThrow();

		assertEquals(1, parsed.nodes().size(), "children of an unknown block must remain opaque");
		assertEquals(outer, opaque.rawPayload());
		assertTrue(codec.toBlocklyXml(parsed).contains(outer));
	}

	@Test void reportsCyclesAndDanglingPortsAfterStructuredEdits() {
		ProcedureIrCodec codec = new ProcedureIrCodec();
		JsonObject values = new JsonObject();
		ProcedureIr base = codec.read(values, ELEMENT_ID);
		UUID first = UUID.fromString("44444444-4444-4444-8444-444444444444");
		UUID second = UUID.fromString("55555555-5555-4555-8555-555555555555");
		JsonArray edits = new JsonArray();
		edits.add(addNode(first, "text_print"));
		edits.add(addNode(second, "text_print"));
		edits.add(connect(first, second));
		edits.add(connect(second, first));

		ProcedureIr cycle = codec.applyEdits(base, edits);
		assertTrue(codec.validate(cycle).stream().anyMatch(issue -> issue.code().equals("PROCEDURE_GRAPH_CYCLE")));

		JsonObject json = codec.toJson(cycle);
		json.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("next",
				"66666666-6666-4666-8666-666666666666");
		ProcedureIr dangling = codec.fromJson(json);
		assertTrue(codec.validate(dangling).stream()
				.anyMatch(issue -> issue.code().equals("PROCEDURE_DANGLING_CONTROL_FLOW")));
		assertFalse(codec.sourcePreview(base).isBlank());
	}

	@Test void parsesDeepButBoundedBlocklyControlFlow() {
		StringBuilder xml = new StringBuilder("<xml xmlns=\"https://developers.google.com/blockly/xml\">"
				+ "<block type=\"event_trigger\" id=\"" + TRIGGER_ID + "\">");
		for (int index = 0; index < 128; index++) {
			UUID id = UUID.nameUUIDFromBytes(("deep-procedure-" + index).getBytes(StandardCharsets.UTF_8));
			xml.append("<next><block type=\"text_print\" id=\"").append(id).append("\">");
		}
		xml.append("</block></next>".repeat(128)).append("</block></xml>");

		ProcedureIr parsed = new ProcedureIrCodec().fromBlocklyXml(xml.toString(), ELEMENT_ID);
		ProcedureIr.Node trigger = parsed.nodes().stream().filter(node -> node.type().equals("event_trigger"))
				.findFirst().orElseThrow();

		assertEquals(129, parsed.nodes().size());
		assertTrue(parsed.nodeIndex().containsKey(trigger.next()));
		assertTrue(new ProcedureIrCodec().validate(parsed).isEmpty());
	}

	private static JsonObject addNode(UUID id, String type) {
		JsonObject node = new JsonObject();
		node.addProperty("id", id.toString());
		node.addProperty("type", type);
		node.addProperty("kind", "statement");
		node.addProperty("x", 100);
		node.addProperty("y", 100);
		node.add("fields", new JsonObject());
		node.add("inputs", new JsonObject());
		JsonObject edit = new JsonObject();
		edit.addProperty("operation", "add_node");
		edit.add("node", node);
		return edit;
	}

	private static JsonObject connect(UUID source, UUID target) {
		JsonObject edit = new JsonObject();
		edit.addProperty("operation", "connect");
		edit.addProperty("sourceNodeId", source.toString());
		edit.addProperty("targetNodeId", target.toString());
		edit.addProperty("port", "next");
		return edit;
	}
}
