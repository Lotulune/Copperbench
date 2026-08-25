package dev.copperbench.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceRegistryContractTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID VARIABLE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID PROCEDURE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

	@Test void renameKeepsStableIdentityUpdatesStructuredProcedureReferencesAndBlocksUnsafeDelete() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(state());
		AtomicLong sequence = new AtomicLong(500);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.incrementAndGet())), CLOCK,
				() -> uuid(sequence.incrementAndGet()));
		RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		JsonObject previewPayload = new JsonObject();
		previewPayload.addProperty("entryId", VARIABLE_ID.toString());
		previewPayload.addProperty("newName", "trail_score");

		var preview = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.PREVIEW_REGISTRY_RENAME,
				previewPayload), context);
		assertEquals("succeeded", preview.status());
		assertEquals(1, preview.data().getAsJsonObject().get("impactedElementCount").getAsInt());

		JsonObject renamePayload = previewPayload.deepCopy();
		renamePayload.addProperty("clientMutationId", uuid(2).toString());
		var renamed = service.execute(Command.of(uuid(3), WORKSPACE_ID, 0, Operation.RENAME_REGISTRY_ENTRY,
				renamePayload), context);
		assertEquals("committed", renamed.result().status());
		assertEquals(1, renamed.result().newRevision());
		WorkspaceState current = store.read(WORKSPACE_ID).orElseThrow();
		JsonObject variable = current.registries().getAsJsonArray("variables").get(0).getAsJsonObject();
		assertEquals(VARIABLE_ID.toString(), variable.get("id").getAsString());
		assertEquals("trail_score", variable.get("name").getAsString());
		JsonObject ir = current.element(PROCEDURE_ID).values().getAsJsonObject("procedureIr");
		assertEquals("trail_score", ir.getAsJsonArray("dependencies").get(0).getAsJsonObject()
				.get("name").getAsString());
		assertEquals("trail_score", ir.getAsJsonArray("nodes").get(1).getAsJsonObject()
				.getAsJsonObject("fields").get("VAR").getAsString());
		assertTrue(current.element(PROCEDURE_ID).values().get("procedurexml").getAsString().contains("trail_score"));

		JsonObject deletePayload = new JsonObject();
		deletePayload.addProperty("clientMutationId", uuid(4).toString());
		deletePayload.addProperty("entryId", VARIABLE_ID.toString());
		var deleted = service.execute(Command.of(uuid(5), WORKSPACE_ID, 1, Operation.DELETE_REGISTRY_ENTRY,
				deletePayload), context);
		assertEquals("rejected", deleted.result().status());
		assertTrue(deleted.result().diagnostics().stream()
				.anyMatch(diagnostic -> diagnostic.code().equals("REGISTRY_ENTRY_IN_USE")));
		assertEquals(1, store.read(WORKSPACE_ID).orElseThrow().revision());
	}

	private static WorkspaceState state() {
		JsonObject variable = new JsonObject();
		variable.addProperty("id", VARIABLE_ID.toString());
		variable.addProperty("kind", "variable");
		variable.addProperty("name", "score");
		variable.addProperty("dataType", "number");
		variable.addProperty("scope", "global");
		JsonArray variables = new JsonArray();
		variables.add(variable);
		JsonObject registries = new JsonObject();
		registries.add("variables", variables);
		registries.add("tags", new JsonArray());
		registries.add("languageKeys", new JsonArray());
		JsonObject product = new JsonObject();
		product.add("registries", registries);
		JsonObject document = new JsonObject();
		document.add("dev.copperbench", product);

		UUID triggerId = UUID.fromString("44444444-4444-4444-8444-444444444444");
		UUID variableNodeId = UUID.fromString("55555555-5555-4555-8555-555555555555");
		JsonObject trigger = node(triggerId, "event_trigger");
		JsonObject variableNode = node(variableNodeId, "variables_get_number");
		variableNode.getAsJsonObject("fields").addProperty("VAR", "score");
		variableNode.getAsJsonObject("fields").addProperty("variableId", VARIABLE_ID.toString());
		JsonArray nodes = new JsonArray();
		nodes.add(trigger);
		nodes.add(variableNode);
		JsonObject dependency = new JsonObject();
		dependency.addProperty("id", UUID.fromString("66666666-6666-4666-8666-666666666666").toString());
		dependency.addProperty("kind", "variable");
		dependency.addProperty("name", "score");
		dependency.addProperty("dataType", "number");
		dependency.addProperty("target", VARIABLE_ID.toString());
		JsonArray dependencies = new JsonArray();
		dependencies.add(dependency);
		JsonObject ir = new JsonObject();
		ir.addProperty("schemaVersion", "1.0");
		ir.addProperty("trigger", "no_ext_trigger");
		ir.add("nodes", nodes);
		ir.add("dependencies", dependencies);
		JsonObject values = new JsonObject();
		values.add("procedureIr", ir);
		WorkspaceState.Element procedure = new WorkspaceState.Element(PROCEDURE_ID, "procedure", "score_reader",
				"Score Reader", "valid", "owned", CLOCK.instant(), values);
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		return new WorkspaceState(WORKSPACE_ID, "Registries", "mod", 0, false, generator, document,
				List.of(procedure));
	}

	private static JsonObject node(UUID id, String type) {
		JsonObject node = new JsonObject();
		node.addProperty("id", id.toString());
		node.addProperty("type", type);
		node.addProperty("kind", type.equals("event_trigger") ? "statement" : "value");
		node.addProperty("x", 40);
		node.addProperty("y", 40);
		node.add("fields", new JsonObject());
		node.add("inputs", new JsonObject());
		return node;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
