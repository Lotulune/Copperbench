package dev.copperbench.references;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspaceReferenceIndexTest {

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
		node.add("fields", new JsonObject());
		node.add("inputs", new JsonObject());
		JsonObject dependency = new JsonObject();
		dependency.addProperty("id", UUID.randomUUID().toString());
		dependency.addProperty("kind", "procedure");
		dependency.addProperty("name", "missing_procedure");
		dependency.addProperty("dataType", "unknown");
		dependency.addProperty("target", missingProcedureId.toString());
		JsonObject ir = new JsonObject();
		ir.addProperty("schemaVersion", "1.0");
		ir.addProperty("trigger", "no_ext_trigger");
		JsonArray nodes = new JsonArray();
		nodes.add(node);
		ir.add("nodes", nodes);
		JsonArray dependencies = new JsonArray();
		dependencies.add(dependency);
		ir.add("dependencies", dependencies);
		JsonObject values = new JsonObject();
		values.addProperty("id", UUID.randomUUID().toString());
		values.add("procedureIr", ir);
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		Element procedure = new Element(elementId, "procedure", "caller", "Caller", "valid", "owned",
				Instant.parse("2026-08-24T00:00:00Z"), values);
		WorkspaceState state = new WorkspaceState(workspaceId, "References", "mod", 3, false, generator,
				new JsonObject(), List.of(procedure));

		JsonObject projection = new WorkspaceReferenceIndex().projection(state, "");

		assertEquals(1, projection.getAsJsonArray("edges").size());
		assertEquals(missingProcedureId.toString(), projection.getAsJsonArray("edges").get(0).getAsJsonObject()
				.get("target").getAsString());
		assertEquals(1, projection.getAsJsonArray("diagnostics").size());
	}
}
