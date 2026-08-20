package dev.copperbench.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.LegacyWorkspaceEntryAdapter;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceApplicationServiceTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T06:50:00Z"), ZoneOffset.UTC);
	private static final Gson GSON = UiCore.wireGson();

	@Test void twoEntryAdaptersUseTheSameQueryRules() {
		Fixture fixture = fixture();
		Query query = Query.of(uuid(90), WORKSPACE_ID, Operation.GET_WORKBENCH, new JsonObject());
		LegacyWorkspaceEntryAdapter legacy = new LegacyWorkspaceEntryAdapter(fixture.service);
		HeadlessWorkspaceEntryAdapter headless = new HeadlessWorkspaceEntryAdapter(fixture.service,
				PermissionProfile.WORKSPACE);

		assertEquals(GSON.toJsonTree(legacy.query(query)), GSON.toJsonTree(headless.query(query)));
	}

	@Test void legacyMcpAndHeadlessEntriesProduceTheSameElementResultAndEvent() {
		Fixture legacyFixture = fixture();
		Fixture mcpFixture = fixture();
		Fixture headlessFixture = fixture();
		Command command = createCommand(uuid(91), "signal_lantern");
		LegacyWorkspaceEntryAdapter legacy = new LegacyWorkspaceEntryAdapter(legacyFixture.service);
		McpWorkspaceEntryAdapter mcp = new McpWorkspaceEntryAdapter(mcpFixture.service,
				PermissionProfile.WORKSPACE);
		HeadlessWorkspaceEntryAdapter headless = new HeadlessWorkspaceEntryAdapter(headlessFixture.service,
				PermissionProfile.WORKSPACE);

		var legacyOutcome = GSON.toJsonTree(legacy.execute(command));
		assertEquals(legacyOutcome, GSON.toJsonTree(mcp.execute(command)));
		assertEquals(legacyOutcome, GSON.toJsonTree(headless.execute(command)));
	}

	@Test void concurrentCommandsBasedOnOneRevisionCommitExactlyOnce() throws Exception {
		Fixture fixture = fixture();
		Command first = createCommand(uuid(1), "signal_lantern");
		Command second = createCommand(uuid(2), "trail_marker");
		RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<CommandOutcome>> calls = List.of(() -> fixture.service.execute(first, context),
					() -> fixture.service.execute(second, context));
			List<Future<CommandOutcome>> futures = executor.invokeAll(calls);
			List<String> statuses = futures.stream().map(future -> {
				try {
					return future.get().result().status();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
			}).toList();
			assertEquals(1, statuses.stream().filter("committed"::equals).count());
			assertEquals(1, statuses.stream().filter("rejected"::equals).count());
			assertEquals(1, fixture.store.read(WORKSPACE_ID).orElseThrow().revision());
			assertEquals(1, fixture.store.read(WORKSPACE_ID).orElseThrow().elements().size());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test void persistenceFailureRejectsTheCandidateWithoutAdvancingRevision() {
		RevisionedWorkspaceStore store = registeredStore();
		SequentialIds ids = new SequentialIds();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, ids), (_, _, _, _) -> {
					throw new IllegalStateException("simulated persistence failure");
				}, CLOCK, ids);

		CommandOutcome outcome = service.execute(createCommand(uuid(15), "failed_element"),
				new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE));

		assertEquals("rejected", outcome.result().status());
		assertEquals("WORKSPACE_PERSISTENCE_FAILED", outcome.result().diagnostics().getFirst().code());
		assertEquals(0, store.read(WORKSPACE_ID).orElseThrow().revision());
		assertTrue(store.read(WORKSPACE_ID).orElseThrow().elements().isEmpty());
		assertTrue(outcome.events().isEmpty());
	}

	@Test void rejectedFieldUpdateLeavesRevisionAndValuesUntouched() {
		Fixture fixture = fixture();
		CommandOutcome created = fixture.service.execute(createCommand(uuid(3), "copper_lamp"),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		UUID elementId = UUID.fromString(created.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString());
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(4).toString());
		payload.addProperty("elementId", elementId.toString());
		JsonObject change = new JsonObject();
		change.addProperty("path", "/fields/hardness");
		change.addProperty("value", 101);
		JsonArray changes = new JsonArray();
		changes.add(change);
		payload.add("changes", changes);

		CommandOutcome outcome = fixture.service.execute(
				Command.of(uuid(5), WORKSPACE_ID, 1, Operation.UPDATE_MOD_ELEMENT, payload),
				new RequestContext(Actor.MCP, PermissionProfile.WORKSPACE));

		assertEquals("rejected", outcome.result().status());
		assertEquals("FIELD_VALUE_OUT_OF_RANGE", outcome.result().diagnostics().getFirst().code());
		WorkspaceState state = fixture.store.read(WORKSPACE_ID).orElseThrow();
		assertEquals(1, state.revision());
		assertTrue(!state.element(elementId).values().has("fields"));
	}

	@Test void taskAcceptanceDoesNotAdvanceContentRevisionAndEventsAreMonotonic() {
		Fixture fixture = fixture();
		RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		CommandOutcome created = fixture.service.execute(createCommand(uuid(6), "signal_lantern"), context);
		JsonObject taskPayload = new JsonObject();
		taskPayload.addProperty("clientMutationId", uuid(7).toString());
		taskPayload.addProperty("scope", "workspace");
		CommandOutcome build = fixture.service.execute(
				Command.of(uuid(8), WORKSPACE_ID, 1, Operation.BUILD_WORKSPACE, taskPayload), context);

		assertEquals("accepted", build.result().status());
		assertEquals(1, build.result().newRevision());
		assertEquals(1, fixture.store.read(WORKSPACE_ID).orElseThrow().revision());
		assertNotEquals(created.events().getFirst().sequence(), build.events().getFirst().sequence());
		assertTrue(created.events().getFirst().sequence() < build.events().getFirst().sequence());
	}

	@Test void readOnlyEditorMarksFieldsAndCapabilitiesUnavailable() {
		Fixture fixture = fixture();
		CommandOutcome created = fixture.service.execute(createCommand(uuid(10), "signal_lantern"),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString();
		JsonObject payload = new JsonObject();
		payload.addProperty("elementId", elementId);

		var result = fixture.service.query(Query.of(uuid(11), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, payload),
				new RequestContext(Actor.HEADLESS, PermissionProfile.READ_ONLY));
		JsonObject editor = result.data().getAsJsonObject();
		JsonArray fields = editor.getAsJsonArray("sections").get(0).getAsJsonObject().getAsJsonArray("fields");

		assertTrue(fields.get(0).getAsJsonObject().get("readOnly").getAsBoolean());
		for (var capability : editor.getAsJsonArray("capabilities"))
			assertEquals("unavailable", capability.getAsJsonObject().get("availability").getAsString());
	}

	@Test void taskStartAndContentMutationAreOrderedByTheWorkspaceLock() throws Exception {
		RevisionedWorkspaceStore store = registeredStore();
		BlockingTaskGateway gateway = new BlockingTaskGateway();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, gateway, CLOCK,
				new SequentialIds());
		JsonObject taskPayload = new JsonObject();
		taskPayload.addProperty("clientMutationId", uuid(12).toString());
		taskPayload.addProperty("scope", "workspace");
		Command build = Command.of(uuid(13), WORKSPACE_ID, 0, Operation.BUILD_WORKSPACE, taskPayload);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<CommandOutcome> buildFuture = executor.submit(() -> service.execute(build,
					new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)));
			assertTrue(gateway.entered.await(2, TimeUnit.SECONDS));
			Future<CommandOutcome> createFuture = executor.submit(() -> service.execute(
					createCommand(uuid(14), "trail_marker"),
					new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE)));

			assertFalse(createFuture.isDone());
			gateway.release.countDown();

			CommandOutcome buildOutcome = buildFuture.get(2, TimeUnit.SECONDS);
			CommandOutcome createOutcome = createFuture.get(2, TimeUnit.SECONDS);
			assertEquals("accepted", buildOutcome.result().status());
			assertEquals("committed", createOutcome.result().status());
			assertTrue(buildOutcome.events().getFirst().sequence() < createOutcome.events().getFirst().sequence());
		} finally {
			gateway.release.countDown();
			executor.shutdownNow();
		}
	}

	@Test void commandEnvelopeSerializesToUiCoreWireNames() {
		Command command = createCommand(uuid(9), "signal_lantern");
		JsonObject json = GSON.toJsonTree(command).getAsJsonObject();
		assertEquals("command", json.get("messageType").getAsString());
		assertEquals(UiCore.SCHEMA_VERSION, json.get("schemaVersion").getAsString());
		assertEquals("create_mod_element", json.get("operation").getAsString());
	}

	@Test void wireSerializerKeepsRequiredNullResultProperties() {
		Fixture fixture = fixture();
		CommandOutcome outcome = fixture.service.execute(createCommand(uuid(92), "signal_lantern"),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		JsonObject json = GSON.toJsonTree(outcome.result()).getAsJsonObject();

		assertTrue(json.has("recoveryPointId") && json.get("recoveryPointId").isJsonNull());
		assertTrue(json.has("task") && json.get("task").isJsonNull());
		assertTrue(json.has("conflict") && json.get("conflict").isJsonNull());
		assertTrue(json.has("denial") && json.get("denial").isJsonNull());
	}

	private static Command createCommand(UUID requestId, String name) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(requestId.variant() + 20).toString());
		payload.addProperty("elementType", "block");
		payload.addProperty("name", name);
		payload.add("initialValues", new JsonObject());
		return Command.of(requestId, WORKSPACE_ID, 0, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static Fixture fixture() {
		RevisionedWorkspaceStore store = registeredStore();
		SequentialIds supplier = new SequentialIds();
		InMemoryWorkspaceTaskGateway tasks = new InMemoryWorkspaceTaskGateway(CLOCK, supplier);
		return new Fixture(store, new WorkspaceApplicationService(store, tasks, CLOCK, supplier));
	}

	private static RevisionedWorkspaceStore registeredStore() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		return store;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}

	private record Fixture(RevisionedWorkspaceStore store, WorkspaceApplicationService service) {
	}

	private static final class SequentialIds implements Supplier<UUID> {
		private final Queue<UUID> ids = new ArrayDeque<>();

		private SequentialIds() {
			for (int index = 100; index < 180; index++)
				ids.add(uuid(index));
		}

		@Override public synchronized UUID get() {
			return ids.remove();
		}
	}

	private static final class BlockingTaskGateway implements WorkspaceTaskGateway {
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final InMemoryWorkspaceTaskGateway delegate = new InMemoryWorkspaceTaskGateway(CLOCK,
				new SequentialIds());

		@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
			entered.countDown();
			try {
				if (!release.await(2, TimeUnit.SECONDS))
					throw new IllegalStateException("Timed out waiting to release task start");
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			return delegate.start(workspaceId, operation, payload);
		}

		@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
			return delegate.find(workspaceId, taskId);
		}

		@Override public List<JsonObject> active(UUID workspaceId) {
			return delegate.active(workspaceId);
		}

		@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
			return delegate.cancel(workspaceId, taskId);
		}
	}
}
