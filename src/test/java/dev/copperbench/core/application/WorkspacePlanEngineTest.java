package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RestoreResult;
import dev.copperbench.history.WorkspaceChange;
import dev.copperbench.procedure.ProcedureIr;
import dev.copperbench.procedure.ProcedureIrCodec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePlanEngineTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-27T05:00:00Z"), ZoneOffset.UTC);
	private static final RequestContext MCP = new RequestContext(Actor.MCP, PermissionProfile.WORKSPACE);

	@Test void orderedPlanAppliesAsOneRevisionAndExactReplayIsIdempotent() {
		Fixture fixture = fixture(false);
		JsonObject plan = plan(fixture.service(), MCP, 7, "plan-two-elements",
				createElement("item", "planned_item"), createElement("block", "planned_block"));

		assertEquals(2, plan.get("operationCount").getAsInt());
		assertEquals(2, plan.getAsJsonArray("semanticDiff").size());
		assertEquals(2, plan.getAsJsonArray("changedPaths").size());
		assertEquals(2, plan.getAsJsonObject("review").getAsJsonObject("summary")
				.get("affectedObjectCount").getAsInt());
		assertEquals("multi_object", plan.getAsJsonObject("review").getAsJsonObject("summary")
				.get("scope").getAsString());
		assertEquals(1, plan.getAsJsonObject("review").getAsJsonArray("operationGroups").size());
		assertNotEquals(plan.getAsJsonArray("operations").get(0).getAsJsonObject().get("plannedId").getAsString(),
				plan.getAsJsonArray("operations").get(1).getAsJsonObject().get("plannedId").getAsString());

		JsonObject previewPayload = new JsonObject();
		previewPayload.add("plan", plan.deepCopy());
		var preview = fixture.service().query(Query.of(uuid(10), WORKSPACE_ID, Operation.PREVIEW_WORKSPACE_PLAN,
				previewPayload), MCP);
		assertEquals("succeeded", preview.status());
		assertTrue(preview.data().getAsJsonObject().get("wouldApply").getAsBoolean());

		var applied = fixture.service().execute(applyCommand(11, 7, plan), MCP);
		assertEquals("committed", applied.result().status());
		assertEquals(8, applied.result().newRevision());
		assertFalse(applied.result().data().getAsJsonObject().get("idempotentReplay").getAsBoolean());
		assertEquals(1, fixture.history().created.size());
		assertEquals(1, fixture.gateway().planCalls);
		WorkspaceState committed = fixture.store().read(WORKSPACE_ID).orElseThrow();
		assertEquals(8, committed.revision());
		assertEquals(2, committed.elements().size());

		var replay = fixture.service().execute(applyCommand(12, 7, plan), MCP);
		assertEquals("committed", replay.result().status());
		assertEquals(8, replay.result().newRevision());
		assertTrue(replay.result().data().getAsJsonObject().get("idempotentReplay").getAsBoolean());
		assertEquals(1, fixture.history().created.size());
		assertEquals(1, fixture.gateway().planCalls);
		assertEquals(8, fixture.store().read(WORKSPACE_ID).orElseThrow().revision());
	}

	@Test void differentArtifactOnlyPlanAtTheSameBaseRevisionIsStaleInsteadOfAFalseReplay() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		store.register(new WorkspaceState(WORKSPACE_ID, "Artifact Replay", "mod", 7, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(500);
		RecordingHistory history = new RecordingHistory();
		RecordingGateway gateway = new RecordingGateway(false);
		WorkspacePlanEngine engine = new WorkspacePlanEngine(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())), gateway,
				history, CLOCK, () -> uuid(sequence.getAndIncrement()));

		JsonObject request = new JsonObject();
		request.addProperty("expectedRevision", 7);
		request.addProperty("idempotencyKey", "artifact-a");
		WorkspacePlanArtifact artifactA = WorkspacePlanArtifact.of("assets/example/a.txt", "a".getBytes());
		JsonObject planA = engine.planPrepared(Query.of(uuid(501), WORKSPACE_ID,
				Operation.PREVIEW_LOCAL_TEMPLATE_INSTANTIATION, request), MCP, new JsonArray(),
				List.of(artifactA), new JsonObject()).data().getAsJsonObject();

		JsonObject secondRequest = request.deepCopy();
		secondRequest.addProperty("idempotencyKey", "artifact-b");
		WorkspacePlanArtifact artifactB = WorkspacePlanArtifact.of("assets/example/b.txt", "b".getBytes());
		JsonObject planB = engine.planPrepared(Query.of(uuid(502), WORKSPACE_ID,
				Operation.PREVIEW_LOCAL_TEMPLATE_INSTANTIATION, secondRequest), MCP, new JsonArray(),
				List.of(artifactB), new JsonObject()).data().getAsJsonObject();

		var first = engine.apply(applyCommand(503, 7, planA), MCP);
		assertEquals("committed", first.result().status(), first.result().diagnostics().toString());
		assertTrue(gateway.appliedArtifact(artifactA));

		var second = engine.apply(applyCommand(504, 7, planB), MCP);
		assertEquals("rejected", second.result().status());
		assertTrue(second.result().diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_STALE".equals(diagnostic.code())));
		assertFalse(gateway.appliedArtifact(artifactB));
		assertEquals(1, gateway.planCalls);
	}

	@Test void highImpactReviewDoesNotInventAiOnlyApprovalForWorkspaceAuthorizedPlan() {
		Fixture fixture = fixture(false);
		JsonObject plan = plan(fixture.service(), MCP, 7, "high-impact-local-creates",
				createElement("item", "planned_item_1"), createElement("item", "planned_item_2"),
				createElement("item", "planned_item_3"), createElement("item", "planned_item_4"),
				createElement("item", "planned_item_5"));

		JsonObject summary = plan.getAsJsonObject("review").getAsJsonObject("summary");
		assertTrue(summary.get("highImpact").getAsBoolean());
		assertEquals(5, summary.get("operationCount").getAsInt());
		assertTrue(plan.getAsJsonObject("permission").get("allowed").getAsBoolean());
		assertFalse(plan.has("userApproved"));

		JsonObject previewPayload = new JsonObject();
		previewPayload.add("plan", plan.deepCopy());
		var preview = fixture.service().query(Query.of(uuid(13), WORKSPACE_ID,
				Operation.PREVIEW_WORKSPACE_PLAN, previewPayload), MCP);
		assertEquals("succeeded", preview.status());
		assertTrue(preview.data().getAsJsonObject().get("wouldApply").getAsBoolean());

		var applied = fixture.service().execute(applyCommand(14, 7, plan), MCP);
		assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
		assertEquals(8, applied.result().newRevision());
		assertEquals(5, fixture.store().read(WORKSPACE_ID).orElseThrow().elements().size());
	}

	@Test void batchProcedureResourceReplacementUpdatesMultipleCallersAsOneProtectedRevision() {
		Fixture fixture = fixture(false);
		RequestContext ui = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		String resourceXml = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
				+ "<block type=\"event_trigger\"><field name=\"trigger\">no_ext_trigger</field></block>"
				+ "<block type=\"mcitem_all\"><field name=\"value\">minecraft:stone</field></block></xml>";
		String firstCallerId = createProcedure(fixture, ui, 7, 90, "first_resource_user", resourceXml);
		String secondCallerId = createProcedure(fixture, ui, 8, 91, "second_resource_user", resourceXml);

		JsonObject request = new JsonObject();
		request.addProperty("kind", "replace_resource_target");
		request.addProperty("expectedRevision", 9);
		request.addProperty("idempotencyKey", "replace-stone-resource");
		request.addProperty("sourceResource", "minecraft:stone");
		request.addProperty("targetResource", "minecraft:diamond");
		var planned = fixture.service().query(Query.of(uuid(92), WORKSPACE_ID,
				Operation.PLAN_PROCEDURE_REFACTOR, request), ui);
		assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
		JsonObject plan = planned.data().getAsJsonObject();
		assertEquals(2, plan.get("operationCount").getAsInt());
		assertTrue(plan.get("requireRecoveryPoint").getAsBoolean());
		assertEquals(2, plan.getAsJsonObject("review").getAsJsonObject("summary")
				.get("affectedElementCount").getAsInt());
		assertTrue(plan.getAsJsonObject("review").getAsJsonArray("affectedObjects").asList().stream()
				.allMatch(raw -> raw.getAsJsonObject().getAsJsonArray("changedProperties").asList().stream()
						.anyMatch(path -> path.getAsString().equals("/values/procedureIr"))));

		var applied = fixture.service().execute(applyCommand(93, 9, plan), ui);
		assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
		assertEquals(10, applied.result().newRevision());
		assertEquals(1, fixture.history().created.size());
		assertEquals(1, fixture.gateway().planCalls);
		assertEquals(plan.getAsJsonObject("review"), applied.result().data().getAsJsonObject().getAsJsonObject("review"));

		ProcedureIrCodec codec = new ProcedureIrCodec();
		WorkspaceState after = fixture.store().read(WORKSPACE_ID).orElseThrow();
		for (String callerId : List.of(firstCallerId, secondCallerId)) {
			Element caller = after.element(UUID.fromString(callerId));
			ProcedureIr.Node resource = codec.read(caller.values(), caller.id()).nodes().stream()
					.filter(node -> node.type().equals("mcitem_all")).findFirst().orElseThrow();
			assertEquals("minecraft:diamond", resource.fields().get("value").getAsString());
		}
	}

	@Test void planSimulationHonorsOperationOrderAndRejectsInvalidSecondStep() {
		Fixture fixture = fixture(false);
		JsonObject payload = planPayload(7, "duplicate-name",
				createElement("item", "same_name"), createElement("block", "same_name"));
		var result = fixture.service().query(Query.of(uuid(20), WORKSPACE_ID, Operation.PLAN_WORKSPACE_CHANGES,
				payload), MCP);
		assertEquals("failed", result.status());
		assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
				"MOD_ELEMENT_NAME_CONFLICT".equals(diagnostic.code())));
		assertEquals(7, fixture.store().read(WORKSPACE_ID).orElseThrow().revision());
		assertTrue(fixture.store().read(WORKSPACE_ID).orElseThrow().elements().isEmpty());
	}

	@Test void stalePlanAndReadOnlyPermissionAreReportedBeforeMutation() {
		Fixture fixture = fixture(false);
		JsonObject plan = plan(fixture.service(), MCP, 7, "stale-plan", createElement("item", "planned_item"));

		JsonObject readOnlyPayload = new JsonObject();
		readOnlyPayload.add("plan", plan.deepCopy());
		var readOnlyPreview = fixture.service().query(Query.of(uuid(30), WORKSPACE_ID,
				Operation.PREVIEW_WORKSPACE_PLAN, readOnlyPayload),
				new RequestContext(Actor.MCP, PermissionProfile.READ_ONLY));
		assertEquals("succeeded", readOnlyPreview.status());
		assertFalse(readOnlyPreview.data().getAsJsonObject().getAsJsonObject("permission").get("allowed").getAsBoolean());

		JsonObject direct = new JsonObject();
		direct.addProperty("elementType", "item");
		direct.addProperty("name", "other_item");
		direct.add("initialValues", new JsonObject());
		var changed = fixture.service().execute(Command.of(uuid(31), WORKSPACE_ID, 7,
				Operation.CREATE_MOD_ELEMENT, direct), new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE));
		assertEquals("committed", changed.result().status());

		var stale = fixture.service().execute(applyCommand(32, 7, plan), MCP);
		assertEquals("rejected", stale.result().status());
		assertTrue(stale.result().diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_STALE".equals(diagnostic.code())));
		assertEquals(8, fixture.store().read(WORKSPACE_ID).orElseThrow().revision());
	}

	@Test void durableFailureLeavesStoreUnchangedAndCreatesOnlyOneRecoveryPoint() {
		Fixture fixture = fixture(true);
		JsonObject plan = plan(fixture.service(), MCP, 7, "rollback-plan",
				createElement("item", "planned_item"), createElement("block", "planned_block"));

		var result = fixture.service().execute(applyCommand(40, 7, plan), MCP);
		assertEquals("rejected", result.result().status());
		assertTrue(result.result().diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_PERSISTENCE_FAILED".equals(diagnostic.code())));
		WorkspaceState after = fixture.store().read(WORKSPACE_ID).orElseThrow();
		assertEquals(7, after.revision());
		assertTrue(after.elements().isEmpty());
		assertEquals(1, fixture.history().created.size());
		assertEquals(1, fixture.gateway().planCalls);
	}

	@Test void protectedPlanIsBlockedBeforeMutationWhenRecoveryPointsAreUnavailable() {
		Fixture fixture = fixture(false, false);
		JsonObject payload = planPayload(7, "protected-refactor", createElement("item", "planned_item"));
		payload.addProperty("requireRecoveryPoint", true);
		var planned = fixture.service().query(Query.of(uuid(45), WORKSPACE_ID,
				Operation.PLAN_WORKSPACE_CHANGES, payload), MCP);
		assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
		JsonObject plan = planned.data().getAsJsonObject();
		assertTrue(plan.get("requireRecoveryPoint").getAsBoolean());
		assertFalse(plan.getAsJsonObject("safety").get("recoveryPointAvailable").getAsBoolean());
		assertFalse(plan.getAsJsonObject("safety").get("ready").getAsBoolean());

		JsonObject previewPayload = new JsonObject();
		previewPayload.add("plan", plan.deepCopy());
		var preview = fixture.service().query(Query.of(uuid(46), WORKSPACE_ID,
				Operation.PREVIEW_WORKSPACE_PLAN, previewPayload), MCP);
		assertEquals("succeeded", preview.status(), preview.diagnostics().toString());
		assertFalse(preview.data().getAsJsonObject().get("wouldApply").getAsBoolean());

		var applied = fixture.service().execute(applyCommand(47, 7, plan), MCP);
		assertEquals("rejected", applied.result().status());
		assertTrue(applied.result().diagnostics().stream().anyMatch(diagnostic ->
				"RECOVERY_POINT_REQUIRED_UNAVAILABLE".equals(diagnostic.code())));
		assertEquals(7, fixture.store().read(WORKSPACE_ID).orElseThrow().revision());
		assertTrue(fixture.store().read(WORKSPACE_ID).orElseThrow().elements().isEmpty());
		assertEquals(0, fixture.gateway().planCalls);
	}

	@Test void protectedVariableRenameUpdatesProcedureDependenciesAndKeepsReferenceIndexStable() {
		Fixture fixture = fixture(false);
		RequestContext ui = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

		JsonObject registryPayload = new JsonObject();
		registryPayload.addProperty("clientMutationId", uuid(60).toString());
		registryPayload.addProperty("registry", "variables");
		JsonObject variable = new JsonObject();
		variable.addProperty("name", "player_energy");
		variable.addProperty("dataType", "number");
		variable.addProperty("scope", "player_persistent");
		registryPayload.add("entry", variable);
		var registryCreated = fixture.service().execute(Command.of(uuid(61), WORKSPACE_ID, 7,
				Operation.CREATE_REGISTRY_ENTRY, registryPayload), ui);
		assertEquals("committed", registryCreated.result().status(), registryCreated.result().diagnostics().toString());
		String registryEntryId = registryCreated.result().data().getAsJsonObject().getAsJsonObject("entry")
				.get("id").getAsString();

		JsonObject createPayload = new JsonObject();
		createPayload.addProperty("clientMutationId", uuid(62).toString());
		createPayload.addProperty("elementType", "procedure");
		createPayload.addProperty("name", "energy_tick");
		JsonObject initialValues = new JsonObject();
		initialValues.addProperty("procedurexml",
				"<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\">"
						+ "<field name=\"trigger\">no_ext_trigger</field></block></xml>");
		createPayload.add("initialValues", initialValues);
		var procedureCreated = fixture.service().execute(Command.of(uuid(63), WORKSPACE_ID, 8,
				Operation.CREATE_MOD_ELEMENT, createPayload), ui);
		assertEquals("committed", procedureCreated.result().status(), procedureCreated.result().diagnostics().toString());
		String procedureId = procedureCreated.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString();

		JsonObject node = new JsonObject();
		node.addProperty("id", uuid(64).toString());
		node.addProperty("type", "variables_get_number");
		node.addProperty("kind", "value");
		node.addProperty("x", 120);
		node.addProperty("y", 80);
		JsonObject fields = new JsonObject();
		fields.addProperty("VAR", "player_energy");
		node.add("fields", fields);
		node.add("inputs", new JsonObject());
		node.add("next", com.google.gson.JsonNull.INSTANCE);
		JsonObject edit = new JsonObject();
		edit.addProperty("operation", "add_node");
		edit.add("node", node);
		JsonArray edits = new JsonArray();
		edits.add(edit);
		JsonObject updatePayload = new JsonObject();
		updatePayload.addProperty("clientMutationId", uuid(65).toString());
		updatePayload.addProperty("elementId", procedureId);
		updatePayload.add("edits", edits);
		var procedureUpdated = fixture.service().execute(Command.of(uuid(66), WORKSPACE_ID, 9,
				Operation.UPDATE_PROCEDURE, updatePayload), ui);
		assertEquals("committed", procedureUpdated.result().status(), procedureUpdated.result().diagnostics().toString());

		JsonObject renamePayload = new JsonObject();
		renamePayload.addProperty("entryId", registryEntryId);
		renamePayload.addProperty("newName", "player_stamina");
		JsonObject renameStep = new JsonObject();
		renameStep.addProperty("operation", "rename_registry_entry");
		renameStep.add("payload", renamePayload);
		JsonObject planPayload = planPayload(10, "stage13-variable-refactor", renameStep);
		planPayload.addProperty("requireRecoveryPoint", true);
		var planned = fixture.service().query(Query.of(uuid(67), WORKSPACE_ID,
				Operation.PLAN_WORKSPACE_CHANGES, planPayload), MCP);
		assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
		JsonObject plan = planned.data().getAsJsonObject();
		assertTrue(plan.getAsJsonObject("safety").get("ready").getAsBoolean());
		assertTrue(plan.getAsJsonArray("semanticDiff").asList().stream().anyMatch(raw ->
				"registry_updated".equals(raw.getAsJsonObject().get("kind").getAsString())));
		assertTrue(plan.getAsJsonArray("semanticDiff").asList().stream().anyMatch(raw ->
				"element_updated".equals(raw.getAsJsonObject().get("kind").getAsString())));

		var applied = fixture.service().execute(applyCommand(68, 10, plan), MCP);
		assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
		assertEquals(11, applied.result().newRevision());
		assertTrue(applied.result().recoveryPointId() != null && !applied.result().recoveryPointId().isBlank());
		assertEquals(1, fixture.history().created.size());

		WorkspaceState after = fixture.store().read(WORKSPACE_ID).orElseThrow();
		Element procedure = after.element(UUID.fromString(procedureId));
		JsonObject procedureIr = procedure.values().getAsJsonObject("procedureIr");
		assertTrue(procedureIr.getAsJsonArray("nodes").asList().stream().anyMatch(raw -> {
			JsonObject candidate = raw.getAsJsonObject();
			return "variables_get_number".equals(candidate.get("type").getAsString())
					&& "player_stamina".equals(candidate.getAsJsonObject("fields").get("VAR").getAsString());
		}));
		assertTrue(procedureIr.getAsJsonArray("dependencies").asList().stream().anyMatch(raw ->
				"player_stamina".equals(raw.getAsJsonObject().get("target").getAsString())));

		JsonObject referencesPayload = new JsonObject();
		referencesPayload.addProperty("target", registryEntryId);
		var references = fixture.service().query(Query.of(uuid(69), WORKSPACE_ID,
				Operation.GET_WORKSPACE_REFERENCES, referencesPayload), ui);
		assertEquals("succeeded", references.status(), references.diagnostics().toString());
		assertEquals(1, references.data().getAsJsonObject().getAsJsonArray("edges").size());
	}

	@Test void procedureExtractionCreatesReusableProcedureAndReplacesSourceAsOneProtectedRevision() {
		Fixture fixture = fixture(false);
		RequestContext ui = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		UUID triggerId = uuid(71);
		UUID rootId = uuid(72);
		UUID valueId = uuid(73);
		UUID afterId = uuid(74);
		UUID afterTextId = uuid(75);
		String xml = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
				+ "<block type=\"event_trigger\" id=\"" + triggerId + "\"><field name=\"trigger\">no_ext_trigger</field><next>"
				+ "<block type=\"variables_set_number\" id=\"" + rootId + "\"><field name=\"VAR\">quest_score</field>"
				+ "<value name=\"VALUE\"><block type=\"math_number\" id=\"" + valueId + "\"><field name=\"NUM\">5</field></block></value>"
				+ "<next><block type=\"text_print\" id=\"" + afterId + "\"><value name=\"TEXT\">"
				+ "<block type=\"text\" id=\"" + afterTextId + "\"><field name=\"TEXT\">after</field></block>"
				+ "</value></block></next></block></next></block></xml>";
		String sourceId = createProcedure(fixture, ui, 7, 76, "quest_tick", xml);

		JsonObject request = new JsonObject();
		request.addProperty("kind", "extract_node");
		request.addProperty("expectedRevision", 8);
		request.addProperty("idempotencyKey", "extract-quest-score");
		request.addProperty("elementId", sourceId);
		request.addProperty("nodeId", rootId.toString());
		request.addProperty("newProcedureName", "award_quest_score");
		var planned = fixture.service().query(Query.of(uuid(77), WORKSPACE_ID,
				Operation.PLAN_PROCEDURE_REFACTOR, request), ui);
		assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
		JsonObject plan = planned.data().getAsJsonObject();
		assertTrue(plan.get("requireRecoveryPoint").getAsBoolean());
		assertTrue(plan.getAsJsonObject("safety").get("ready").getAsBoolean());
		assertEquals(2, plan.get("operationCount").getAsInt());
		String extractedId = plan.getAsJsonArray("operations").get(0).getAsJsonObject()
				.get("plannedId").getAsString();

		var applied = fixture.service().execute(applyCommand(78, 8, plan), ui);
		assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
		assertEquals(9, applied.result().newRevision());
		assertTrue(applied.result().recoveryPointId() != null && !applied.result().recoveryPointId().isBlank());
		assertEquals(1, fixture.history().created.size());
		assertEquals(1, fixture.gateway().planCalls);

		WorkspaceState after = fixture.store().read(WORKSPACE_ID).orElseThrow();
		assertEquals(2, after.elements().size());
		ProcedureIrCodec codec = new ProcedureIrCodec();
		ProcedureIr sourceIr = codec.read(after.element(UUID.fromString(sourceId)).values(), UUID.fromString(sourceId));
		ProcedureIr.Node call = sourceIr.nodeIndex().get(rootId);
		assertEquals("call_procedure", call.type());
		assertEquals("award_quest_score", call.fields().get("procedureId").getAsString());
		assertEquals(afterId, call.next());
		assertFalse(sourceIr.nodeIndex().containsKey(valueId));
		assertTrue(sourceIr.nodeIndex().containsKey(afterId));

		Element extracted = after.element(UUID.fromString(extractedId));
		assertEquals("procedure", extracted.type());
		assertEquals("award_quest_score", extracted.name());
		ProcedureIr extractedIr = codec.read(extracted.values(), extracted.id());
		assertEquals(3, extractedIr.nodes().size());
		assertTrue(extractedIr.nodes().stream().anyMatch(node -> node.type().equals("variables_set_number")));
		assertTrue(extractedIr.nodes().stream().anyMatch(node -> node.type().equals("math_number")));
		assertFalse(extractedIr.nodes().stream().anyMatch(node -> node.type().equals("text_print")));

		JsonObject referencesPayload = new JsonObject();
		referencesPayload.addProperty("target", extractedId);
		var references = fixture.service().query(Query.of(uuid(79), WORKSPACE_ID,
				Operation.GET_WORKSPACE_REFERENCES, referencesPayload), ui);
		assertEquals("succeeded", references.status(), references.diagnostics().toString());
		assertEquals(1, references.data().getAsJsonObject().getAsJsonArray("edges").size());
	}

	@Test void batchProcedureCallReplacementUpdatesMultipleCallersAsOneProtectedRevision() {
		Fixture fixture = fixture(false);
		RequestContext ui = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		String empty = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\">"
				+ "<field name=\"trigger\">no_ext_trigger</field></block></xml>";
		String sourceId = createProcedure(fixture, ui, 7, 80, "old_reward", empty);
		String targetId = createProcedure(fixture, ui, 8, 81, "new_reward", empty);
		String callerXml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\">"
				+ "<field name=\"trigger\">no_ext_trigger</field><next><block type=\"call_procedure\">"
				+ "<field name=\"procedureId\">old_reward</field><field name=\"procedure\">old_reward</field>"
				+ "</block></next></block></xml>";
		String firstCallerId = createProcedure(fixture, ui, 9, 82, "first_caller", callerXml);
		String secondCallerId = createProcedure(fixture, ui, 10, 83, "second_caller", callerXml);

		JsonObject request = new JsonObject();
		request.addProperty("kind", "replace_call_target");
		request.addProperty("expectedRevision", 11);
		request.addProperty("idempotencyKey", "replace-old-reward-calls");
		request.addProperty("sourceProcedureId", sourceId);
		request.addProperty("targetProcedureId", targetId);
		var planned = fixture.service().query(Query.of(uuid(84), WORKSPACE_ID,
				Operation.PLAN_PROCEDURE_REFACTOR, request), ui);
		assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
		JsonObject plan = planned.data().getAsJsonObject();
		assertEquals(2, plan.get("operationCount").getAsInt());
		assertTrue(plan.get("requireRecoveryPoint").getAsBoolean());

		var applied = fixture.service().execute(applyCommand(85, 11, plan), ui);
		assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
		assertEquals(12, applied.result().newRevision());
		assertEquals(1, fixture.history().created.size());
		assertEquals(1, fixture.gateway().planCalls);

		ProcedureIrCodec codec = new ProcedureIrCodec();
		WorkspaceState after = fixture.store().read(WORKSPACE_ID).orElseThrow();
		for (String callerId : List.of(firstCallerId, secondCallerId)) {
			Element caller = after.element(UUID.fromString(callerId));
			ProcedureIr.Node call = codec.read(caller.values(), caller.id()).nodes().stream()
					.filter(node -> node.type().equals("call_procedure")).findFirst().orElseThrow();
			assertEquals(targetId, call.fields().get("procedureId").getAsString());
			assertEquals("new_reward", call.fields().get("procedure").getAsString());
		}

		JsonObject referencesPayload = new JsonObject();
		referencesPayload.addProperty("target", targetId);
		var references = fixture.service().query(Query.of(uuid(86), WORKSPACE_ID,
				Operation.GET_WORKSPACE_REFERENCES, referencesPayload), ui);
		assertEquals(2, references.data().getAsJsonObject().getAsJsonArray("edges").size());
	}

	@Test void batchProcedureCallReplacementRejectsIntroducedCallCycleBeforePlanning() {
		Fixture fixture = fixture(false);
		RequestContext ui = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		String empty = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\">"
				+ "<field name=\"trigger\">no_ext_trigger</field></block></xml>";
		String sourceId = createProcedure(fixture, ui, 7, 87, "old_reward", empty);
		String targetXml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\">"
				+ "<field name=\"trigger\">no_ext_trigger</field><next><block type=\"call_procedure\">"
				+ "<field name=\"procedureId\">old_reward</field><field name=\"procedure\">old_reward</field>"
				+ "</block></next></block></xml>";
		String targetId = createProcedure(fixture, ui, 8, 88, "new_reward", targetXml);

		JsonObject request = new JsonObject();
		request.addProperty("kind", "replace_call_target");
		request.addProperty("expectedRevision", 9);
		request.addProperty("idempotencyKey", "reject-call-cycle");
		request.addProperty("sourceProcedureId", sourceId);
		request.addProperty("targetProcedureId", targetId);
		var planned = fixture.service().query(Query.of(uuid(89), WORKSPACE_ID,
				Operation.PLAN_PROCEDURE_REFACTOR, request), ui);
		assertEquals("rejected", planned.status());
		assertTrue(planned.diagnostics().stream().anyMatch(diagnostic ->
				"PROCEDURE_REFACTOR_CALL_CYCLE".equals(diagnostic.code())));
		assertEquals(9, fixture.store().read(WORKSPACE_ID).orElseThrow().revision());
		assertEquals(0, fixture.gateway().planCalls);
		assertEquals(0, fixture.history().created.size());
	}

	@Test void tamperedDerivedPlanMetadataIsRejectedBeforeMutation() {
		Fixture fixture = fixture(false);
		JsonObject plan = plan(fixture.service(), MCP, 7, "tamper-plan", createElement("item", "planned_item"));

		JsonObject forgedToken = plan.deepCopy();
		forgedToken.addProperty("planToken", "0".repeat(64));
		JsonObject forgedPreviewPayload = new JsonObject();
		forgedPreviewPayload.add("plan", forgedToken);
		var forgedPreview = fixture.service().query(Query.of(uuid(49), WORKSPACE_ID,
				Operation.PREVIEW_WORKSPACE_PLAN, forgedPreviewPayload), MCP);
		assertEquals("failed", forgedPreview.status());
		assertTrue(forgedPreview.diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_INTEGRITY_FAILED".equals(diagnostic.code())));

		JsonObject tampered = plan.deepCopy();
		tampered.addProperty("operationCount", 2);
		JsonObject previewPayload = new JsonObject();
		previewPayload.add("plan", tampered);
		var preview = fixture.service().query(Query.of(uuid(50), WORKSPACE_ID,
				Operation.PREVIEW_WORKSPACE_PLAN, previewPayload), MCP);
		assertEquals("failed", preview.status());
		assertTrue(preview.diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_INTEGRITY_FAILED".equals(diagnostic.code())));

		JsonObject changedPathsTamper = plan.deepCopy();
		JsonArray fakePaths = new JsonArray();
		fakePaths.add("/elements/00000000-0000-4000-8000-999999999999");
		changedPathsTamper.add("changedPaths", fakePaths);
		var applied = fixture.service().execute(applyCommand(51, 7, changedPathsTamper), MCP);
		assertEquals("rejected", applied.result().status());
		assertTrue(applied.result().diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_INTEGRITY_FAILED".equals(diagnostic.code())));
		assertEquals(7, fixture.store().read(WORKSPACE_ID).orElseThrow().revision());
		assertEquals(0, fixture.gateway().planCalls);
		assertEquals(0, fixture.history().created.size());

		JsonObject reviewTamper = plan.deepCopy();
		reviewTamper.getAsJsonObject("review").getAsJsonObject("summary").addProperty("affectedObjectCount", 99);
		JsonObject reviewPreviewPayload = new JsonObject();
		reviewPreviewPayload.add("plan", reviewTamper);
		var reviewPreview = fixture.service().query(Query.of(uuid(54), WORKSPACE_ID,
				Operation.PREVIEW_WORKSPACE_PLAN, reviewPreviewPayload), MCP);
		assertEquals("failed", reviewPreview.status());
		assertTrue(reviewPreview.diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_INTEGRITY_FAILED".equals(diagnostic.code())));

		JsonObject protectedPayload = planPayload(7, "protected-tamper-plan", createElement("item", "protected_item"));
		protectedPayload.addProperty("requireRecoveryPoint", true);
		var protectedResult = fixture.service().query(Query.of(uuid(52), WORKSPACE_ID,
				Operation.PLAN_WORKSPACE_CHANGES, protectedPayload), MCP);
		assertEquals("succeeded", protectedResult.status(), protectedResult.diagnostics().toString());
		JsonObject strippedProtection = protectedResult.data().getAsJsonObject().deepCopy();
		strippedProtection.addProperty("requireRecoveryPoint", false);
		var strippedApply = fixture.service().execute(applyCommand(53, 7, strippedProtection), MCP);
		assertEquals("rejected", strippedApply.result().status());
		assertTrue(strippedApply.result().diagnostics().stream().anyMatch(diagnostic ->
				"WORKSPACE_PLAN_INTEGRITY_FAILED".equals(diagnostic.code())));
		assertEquals(7, fixture.store().read(WORKSPACE_ID).orElseThrow().revision());
		assertEquals(0, fixture.history().created.size());
	}

	private static Fixture fixture(boolean failPlanPersistence) {
		return fixture(failPlanPersistence, true);
	}

	private static Fixture fixture(boolean failPlanPersistence, boolean withHistory) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		store.register(new WorkspaceState(WORKSPACE_ID, "Workspace Plan", "mod", 7, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(100);
		RecordingHistory history = withHistory ? new RecordingHistory() : null;
		RecordingGateway gateway = new RecordingGateway(failPlanPersistence);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())), gateway,
				history, null, CLOCK, () -> uuid(sequence.getAndIncrement()));
		return new Fixture(store, service, history, gateway);
	}

	private static JsonObject plan(WorkspaceApplicationService service, RequestContext context, long revision,
			String idempotencyKey, JsonObject... steps) {
		var result = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.PLAN_WORKSPACE_CHANGES,
				planPayload(revision, idempotencyKey, steps)), context);
		assertEquals("succeeded", result.status(), () -> result.diagnostics().toString());
		return result.data().getAsJsonObject();
	}

	private static JsonObject planPayload(long revision, String idempotencyKey, JsonObject... steps) {
		JsonObject payload = new JsonObject();
		payload.addProperty("expectedRevision", revision);
		payload.addProperty("idempotencyKey", idempotencyKey);
		JsonArray operations = new JsonArray();
		for (JsonObject step : steps) operations.add(step);
		payload.add("operations", operations);
		return payload;
	}

	private static JsonObject createElement(String type, String name) {
		JsonObject payload = new JsonObject();
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", new JsonObject());
		JsonObject step = new JsonObject();
		step.addProperty("operation", "create_mod_element");
		step.add("payload", payload);
		return step;
	}

	private static String createProcedure(Fixture fixture, RequestContext context, long revision, long requestId,
			String name, String xml) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(requestId + 1000).toString());
		payload.addProperty("elementType", "procedure");
		payload.addProperty("name", name);
		JsonObject values = new JsonObject();
		values.addProperty("procedurexml", xml);
		payload.add("initialValues", values);
		var result = fixture.service().execute(Command.of(uuid(requestId), WORKSPACE_ID, revision,
				Operation.CREATE_MOD_ELEMENT, payload), context);
		assertEquals("committed", result.result().status(), result.result().diagnostics().toString());
		return result.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
	}

	private static Command applyCommand(long request, long revision, JsonObject plan) {
		JsonObject payload = new JsonObject();
		payload.add("plan", plan.deepCopy());
		return Command.of(uuid(request), WORKSPACE_ID, revision, Operation.APPLY_WORKSPACE_PLAN, payload);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}

	private record Fixture(RevisionedWorkspaceStore store, WorkspaceApplicationService service,
			RecordingHistory history, RecordingGateway gateway) {
	}

	private static final class RecordingGateway implements WorkspaceMutationGateway {
		private final boolean failPlanPersistence;
		private final List<String> appliedArtifacts = new ArrayList<>();
		private int planCalls;

		private RecordingGateway(boolean failPlanPersistence) {
			this.failPlanPersistence = failPlanPersistence;
		}

		@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation,
				Element affectedElement) {
		}

		@Override public void persistWorkspacePlan(WorkspaceState before, WorkspaceState after,
				List<Operation> operations) throws Exception {
			persistWorkspacePlan(before, after, operations, List.of());
		}

		@Override public void persistWorkspacePlan(WorkspaceState before, WorkspaceState after,
				List<Operation> operations, List<WorkspacePlanArtifact> artifacts) throws Exception {
			planCalls++;
			if (failPlanPersistence) throw new Exception("synthetic plan persistence failure");
			if (artifacts != null) for (WorkspacePlanArtifact artifact : artifacts)
				appliedArtifacts.add(artifactKey(artifact));
		}

		@Override public void validateWorkspacePlan(WorkspaceState before, WorkspaceState after,
				List<WorkspacePlanArtifact> artifacts) {
		}

		@Override public boolean workspacePlanArtifactsAlreadyApplied(List<WorkspacePlanArtifact> artifacts) {
			return artifacts == null || artifacts.stream().allMatch(this::appliedArtifact);
		}

		private boolean appliedArtifact(WorkspacePlanArtifact artifact) {
			return appliedArtifacts.contains(artifactKey(artifact));
		}

		private static String artifactKey(WorkspacePlanArtifact artifact) {
			return artifact.relativePath() + "=" + artifact.sha256();
		}
	}

	private static final class RecordingHistory implements LocalHistoryService {
		private final List<RecoveryPointRequest> created = new ArrayList<>();

		@Override public RecoveryPoint createRecoveryPoint(RecoveryPointRequest request) {
			created.add(request);
			return new RecoveryPoint("recovery-" + created.size(), request.label(), request.actor(), request.taskId(),
					CLOCK.instant());
		}

		@Override public List<RecoveryPoint> listRecoveryPoints() { return List.of(); }
		@Override public String currentRecoveryPointId() { return null; }
		@Override public List<WorkspaceChange> compare(String fromRecoveryPointId, String toRecoveryPointId)
				throws LocalHistoryException { return List.of(); }
		@Override public List<WorkspaceChange> previewRestore(String recoveryPointId) { return List.of(); }
		@Override public RestoreResult restore(String recoveryPointId) throws LocalHistoryException {
			throw new LocalHistoryException("not used");
		}
		@Override public void close() { }
	}
}
