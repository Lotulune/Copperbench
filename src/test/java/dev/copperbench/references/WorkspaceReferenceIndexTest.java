package dev.copperbench.references;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceReferenceIndexTest {

	@org.junit.jupiter.api.BeforeAll static void initialize() throws Exception {
		dev.copperbench.testing.McreatorTestRuntime.ensureInitialized();
	}

	@Test void nestedCollectionsAndProcedureMapsAreIndexedWithoutTreatingKeysAsReferences() {
		JsonObject values = com.google.gson.JsonParser.parseString("""
				{"customProperties":{"mode/ref~id":"missing_property","empty":null},
				 "animations":[{"condition":{"name":"missing_animation"}}],
				 "specialInformation":["fixed text","minecraft:stone"]}
				""").getAsJsonObject();
		values.add("fields", values.deepCopy());
		var source = element("item", "nested_item", values);
		var state = state(List.of(source));
		JsonObject before = source.values().deepCopy();
		var index = new WorkspaceReferenceIndex();
		var graph = index.projection(state, "");
		assertEquals(2, graph.getAsJsonArray("edges").size(), graph.toString());
		assertEquals(java.util.Set.of("/customProperties/mode~1ref~0id", "/animations/0/condition"),
				graph.getAsJsonArray("edges").asList().stream().map(e -> e.getAsJsonObject().get("sourcePath").getAsString()).collect(java.util.stream.Collectors.toSet()));
		assertEquals(2, graph.getAsJsonArray("diagnostics").size());
		assertEquals(before, source.values(), "Indexing must not mutate the source or its aliases");
		state.addElement(element("procedure", "missing_property", new JsonObject()));
		state.addElement(element("procedure", "missing_animation", new JsonObject()));
		assertEquals(0, index.projection(state, "").getAsJsonArray("diagnostics").size());
	}

	@Test void guiProcedureFieldsUseComponentTypesAndIgnoreFixedTextAndUnknownAdapters() {
		JsonObject values = com.google.gson.JsonParser.parseString("""
				{"components":[
				 {"type":"button","data":{"name":"run","onClick":"missing_click","displayCondition":{"name":"wrong_kind"}}},
				 {"type":"label","data":{"text":{"name":null,"fixedValue":"minecraft:stone"}}},
				 {"type":"inputslot","data":{"disablePickup":false,"disablePlacement":{"name":"missing_condition","fixedValue":false},"onSlotChanged":"missing_slot"}},
				 {"type":"unknown_plugin","data":{"onClick":"opaque_hook"}}]}
				""").getAsJsonObject();
		var source = element("gui", "nested_gui", values);
		var graph = new WorkspaceReferenceIndex().projection(state(List.of(source, element("item", "wrong_kind", new JsonObject()))), "");
		assertEquals(4, graph.getAsJsonArray("edges").size(), graph.toString());
		assertEquals(4, graph.getAsJsonArray("diagnostics").size());
		var paths = graph.getAsJsonArray("edges").asList().stream().map(e -> e.getAsJsonObject().get("sourcePath").getAsString()).toList();
		assertTrue(paths.containsAll(List.of("/components/0/data/onClick", "/components/0/data/displayCondition",
				"/components/2/data/disablePlacement", "/components/2/data/onSlotChanged")));
		assertTrue(graph.getAsJsonArray("diagnostics").asList().stream().anyMatch(d -> d.getAsJsonObject().get("code").getAsString().equals("WORKSPACE_REFERENCE_TYPE_MISMATCH")));
		for (var raw : graph.getAsJsonArray("diagnostics")) {
			var diagnostic = raw.getAsJsonObject();
			assertEquals(diagnostic.get("path"), diagnostic.getAsJsonArray("actions").get(0).getAsJsonObject().get("target"));
		}
	}

	@Test void actualCallNodesRemainVisibleWhenDependencyMetadataIsEmptyAndAliasesDoNotDuplicateEdges() {
		JsonObject values = call("missing_target");
		var plain = new WorkspaceReferenceIndex().projection(state(List.of(element("procedure", "plain", values))), "");
		assertEquals(1, plain.getAsJsonArray("edges").size());
		JsonObject aliases = values.deepCopy(); values.add("fields", aliases);
		var graph = new WorkspaceReferenceIndex().projection(state(List.of(element("procedure", "caller", values))), "");
		assertEquals(1, graph.getAsJsonArray("edges").size());
		assertEquals("missing_target", graph.getAsJsonArray("edges").get(0).getAsJsonObject().get("target").getAsString());
		assertEquals(1, graph.getAsJsonArray("diagnostics").size());
	}

	@Test void fabricatedSummariesAndDisplayLabelsDoNotCreateValidProcedureBindings() {
		JsonObject values = call("label_only");
		JsonObject fabricated = new JsonObject(); fabricated.addProperty("id", UUID.randomUUID().toString());
		fabricated.addProperty("kind", "procedure"); fabricated.addProperty("target", "forged_missing");
		values.getAsJsonObject("procedureIr").getAsJsonArray("dependencies").add(fabricated);
		var real = new Element(UUID.randomUUID(), "procedure", "actual_name", "label_only", "valid", "generated", Instant.EPOCH, new JsonObject());
		var graph = new WorkspaceReferenceIndex().projection(state(List.of(element("procedure", "caller", values), real)), "");
		assertEquals(1, graph.getAsJsonArray("edges").size());
		assertEquals("label_only", graph.getAsJsonArray("edges").get(0).getAsJsonObject().get("target").getAsString());
		assertEquals("WORKSPACE_REFERENCE_DANGLING", graph.getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString());
	}

	@Test void ambiguousImportedNamesAreNotResolvedByIterationOrder() {
		var graph = new WorkspaceReferenceIndex().projection(state(List.of(element("procedure", "caller", call("duplicate")),
				element("procedure", "duplicate", new JsonObject()), element("procedure", "duplicate", new JsonObject()))), "");
		assertEquals("WORKSPACE_REFERENCE_AMBIGUOUS", graph.getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString());
		assertTrue(graph.getAsJsonArray("edges").get(0).getAsJsonObject().get("targetId").isJsonNull());
	}

	@Test void procedureTargetsMustHaveTheRightTypeAndRegistryNamesMustNotShadowThem() {
		var caller = element("procedure", "caller", call("target"));
		var wrong = element("item", "target", new JsonObject());
		var index = new WorkspaceReferenceIndex(); var state = state(List.of(caller, wrong));
		var graph = index.projection(state, "");
		assertEquals("WORKSPACE_REFERENCE_TYPE_MISMATCH", graph.getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString());
		state.removeElement(wrong.id()); var right = element("procedure", "target", new JsonObject()); state.addElement(right);
		JsonObject variable = new JsonObject(); variable.addProperty("id", UUID.randomUUID().toString()); variable.addProperty("name", "target");
		JsonObject registries = state.registries(); registries.getAsJsonArray("variables").add(variable); state.replaceRegistries(registries);
		graph = index.projection(state, "");
		assertEquals(0, graph.getAsJsonArray("diagnostics").size());
		assertEquals(right.id().toString(), graph.getAsJsonArray("edges").get(0).getAsJsonObject().get("targetId").getAsString());
	}

	@Test void typedProcedureFieldsDistinguishNamesFromFixedValues() {
		JsonObject values = com.google.gson.JsonParser.parseString("{\"glowCondition\":{\"name\":\"missing_condition\",\"fixedValue\":false},\"onRightClickedInAir\":\"missing_hook\",\"specialInformation\":[\"fixed text\"]}").getAsJsonObject();
		var graph = new WorkspaceReferenceIndex().projection(state(List.of(element("item", "test_item", values))), "");
		assertEquals(2, graph.getAsJsonArray("edges").size()); assertEquals(2, graph.getAsJsonArray("diagnostics").size());
		values.addProperty("glowCondition", false); values.add("onRightClickedInAir", com.google.gson.JsonNull.INSTANCE);
		graph = new WorkspaceReferenceIndex().projection(state(List.of(element("item", "fixed_item", values))), "");
		assertEquals(0, graph.getAsJsonArray("edges").size());
	}

	private static JsonObject call(String target) {
		JsonObject values = new JsonObject();
		var codec = new dev.copperbench.procedure.ProcedureIrCodec();
		JsonObject ir = codec.toJson(codec.fromBlocklyXml("<xml><block type=\"call_procedure\"><field name=\"procedureId\">" + target + "</field></block></xml>", UUID.randomUUID()));
		ir.add("dependencies", new JsonArray()); values.add("procedureIr", ir); return values;
	}
	private static Element element(String type, String name, JsonObject values) { return new Element(UUID.randomUUID(), type, name, name, "valid", "generated", Instant.EPOCH, values); }
	private static WorkspaceState state(List<Element> elements) { return new WorkspaceState(UUID.randomUUID(), "References", "mod", 0, false, new JsonObject(), new JsonObject(), elements); }

	@Test void indexesProcedureDependenciesWithoutTreatingGraphNodeIdsAsWorkspaceReferences() {
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		UUID elementId = UUID.fromString("22222222-2222-4222-8222-222222222222");
		UUID nodeId = UUID.fromString("33333333-3333-4333-8333-333333333333");
		UUID missingProcedureId = UUID.fromString("44444444-4444-4444-8444-444444444444");
		JsonObject node = new JsonObject();
		node.addProperty("id", nodeId.toString());
		node.addProperty("type", "call_procedure");
		node.addProperty("kind", "statement");
		node.addProperty("x", 40);
		node.addProperty("y", 40);
		JsonObject callFields = new JsonObject(); callFields.addProperty("procedureId", missingProcedureId.toString());
		node.add("fields", callFields);
		node.add("inputs", new JsonObject());
		JsonObject dependency = new JsonObject();
		dependency.addProperty("id", UUID.randomUUID().toString());
		dependency.addProperty("kind", "procedure");
		dependency.addProperty("name", "missing_procedure");
		dependency.addProperty("dataType", "unknown");
		dependency.addProperty("target", missingProcedureId.toString());
		JsonObject resourceDependency = new JsonObject();
		resourceDependency.addProperty("id", UUID.randomUUID().toString());
		resourceDependency.addProperty("kind", "resource");
		resourceDependency.addProperty("name", "minecraft:stone");
		resourceDependency.addProperty("dataType", "itemstack");
		resourceDependency.addProperty("target", "minecraft:stone");
		JsonObject ir = new JsonObject();
		ir.addProperty("schemaVersion", "1.0");
		ir.addProperty("trigger", "no_ext_trigger");
		JsonArray nodes = new JsonArray();
		nodes.add(node);
		JsonObject resourceNode = new JsonObject(); resourceNode.addProperty("id", UUID.randomUUID().toString()); resourceNode.addProperty("type", "mcitem_all");
		JsonObject resourceFields = new JsonObject(); resourceFields.addProperty("value", "minecraft:stone"); resourceNode.add("fields", resourceFields); nodes.add(resourceNode);
		ir.add("nodes", nodes);
		JsonArray dependencies = new JsonArray();
		dependencies.add(dependency);
		dependencies.add(resourceDependency);
		ir.add("dependencies", dependencies);
		JsonObject values = new JsonObject();
		values.addProperty("id", UUID.randomUUID().toString());
		values.add("procedureIr", ir);
		values.addProperty("procedurexml",
				"<xml><block type=\"mcitem_all\"><field name=\"value\">minecraft:stone</field></block></xml>");
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		Element procedure = new Element(elementId, "procedure", "caller", "Caller", "valid", "owned",
				Instant.parse("2026-08-24T00:00:00Z"), values);
		WorkspaceState state = new WorkspaceState(workspaceId, "References", "mod", 3, false, generator,
				new JsonObject(), List.of(procedure));

		JsonObject projection = new WorkspaceReferenceIndex().projection(state, "");

		assertEquals(2, projection.getAsJsonArray("edges").size());
		assertEquals(missingProcedureId.toString(), projection.getAsJsonArray("edges").get(0).getAsJsonObject()
				.get("target").getAsString());
		assertEquals("caller", projection.getAsJsonArray("edges").get(0).getAsJsonObject()
				.get("sourceName").getAsString());
		assertEquals("procedure", projection.getAsJsonArray("edges").get(0).getAsJsonObject()
				.get("sourceType").getAsString());
		assertEquals("minecraft:stone", projection.getAsJsonArray("edges").get(1).getAsJsonObject()
				.get("targetName").getAsString());
		assertEquals("resource", projection.getAsJsonArray("edges").get(1).getAsJsonObject()
				.get("kind").getAsString());
		assertEquals(1, projection.getAsJsonArray("diagnostics").size());
	}
}
