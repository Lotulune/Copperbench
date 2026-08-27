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
	}

	private static Fixture fixture(boolean failPlanPersistence) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		store.register(new WorkspaceState(WORKSPACE_ID, "Workspace Plan", "mod", 7, false, generator,
				new JsonObject(), List.of()));
		AtomicLong sequence = new AtomicLong(100);
		RecordingHistory history = new RecordingHistory();
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
		private int planCalls;

		private RecordingGateway(boolean failPlanPersistence) {
			this.failPlanPersistence = failPlanPersistence;
		}

		@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation,
				Element affectedElement) {
		}

		@Override public void persistWorkspacePlan(WorkspaceState before, WorkspaceState after,
				List<Operation> operations) throws Exception {
			planCalls++;
			if (failPlanPersistence) throw new Exception("synthetic plan persistence failure");
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
		@Override public List<WorkspaceChange> compare(String fromRecoveryPointId, String toRecoveryPointId)
				throws LocalHistoryException { return List.of(); }
		@Override public RestoreResult restore(String recoveryPointId) throws LocalHistoryException {
			throw new LocalHistoryException("not used");
		}
		@Override public void close() { }
	}
}
