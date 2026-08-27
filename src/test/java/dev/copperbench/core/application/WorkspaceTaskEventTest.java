package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceTaskEventTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-27T06:50:00Z"), ZoneOffset.UTC);

	@Test void taskEventsArePublishedAndReplayedAfterReconnect() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		SequentialIds ids = new SequentialIds();
		InMemoryWorkspaceTaskGateway tasks = new InMemoryWorkspaceTaskGateway(CLOCK, ids);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
		List<UiCore.Event> received = new ArrayList<>();
		AutoCloseable subscription = service.subscribeEvents(WORKSPACE_ID, 0, received::add);
		try {
			JsonObject startPayload = new JsonObject();
			startPayload.addProperty("clientMutationId", uuid(1).toString());
			startPayload.addProperty("scope", "workspace");
			var started = service.execute(Command.of(uuid(2), WORKSPACE_ID, 0, Operation.BUILD_WORKSPACE,
					startPayload), new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
			UUID taskId = UUID.fromString(started.result().task().getAsJsonObject().get("id").getAsString());

			JsonObject cancelPayload = new JsonObject();
			cancelPayload.addProperty("clientMutationId", uuid(3).toString());
			cancelPayload.addProperty("taskId", taskId.toString());
			var cancelled = service.execute(Command.of(uuid(4), WORKSPACE_ID, 0, Operation.CANCEL_TASK,
					cancelPayload), new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));

			assertEquals("cancelled", cancelled.result().status());
			assertEquals(1, received.size());
			UiCore.Event event = received.getFirst();
			assertEquals("task_completed", event.event());
			assertEquals("cancelled", event.payload().getAsJsonObject("task").get("state").getAsString());
			assertTrue(event.sequence() > 0);

			List<UiCore.Event> replayed = new ArrayList<>();
			try (AutoCloseable reconnect = service.subscribeEvents(WORKSPACE_ID, event.sequence() - 1,
					replayed::add)) {
				assertEquals(1, replayed.size());
				assertEquals(event.eventId(), replayed.getFirst().eventId());
			}
		} finally {
			subscription.close();
		}
		assertFalse(received.isEmpty());
	}

	@Test void taskPollingReturnsOnlyLogsAfterRequestedSequence() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		SequentialIds ids = new SequentialIds();
		InMemoryWorkspaceTaskGateway tasks = new InMemoryWorkspaceTaskGateway(CLOCK, ids);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
		JsonObject startPayload = new JsonObject();
		startPayload.addProperty("clientMutationId", uuid(5).toString());
		startPayload.addProperty("scope", "workspace");
		var started = service.execute(Command.of(uuid(6), WORKSPACE_ID, 0, Operation.BUILD_WORKSPACE, startPayload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		UUID taskId = UUID.fromString(started.result().task().getAsJsonObject().get("id").getAsString());
		tasks.appendLog(WORKSPACE_ID, taskId, "info", "first");
		tasks.appendLog(WORKSPACE_ID, taskId, "info", "second");

		JsonObject queryPayload = new JsonObject();
		queryPayload.addProperty("taskId", taskId.toString());
		queryPayload.addProperty("afterLogSequence", 1);
		var result = service.query(UiCore.Query.of(uuid(7), WORKSPACE_ID, Operation.GET_TASK, queryPayload),
				new RequestContext(Actor.MCP, PermissionProfile.WORKSPACE));
		assertEquals("succeeded", result.status());
		assertEquals(1, result.data().getAsJsonObject().getAsJsonArray("logs").size());
		assertEquals("second", result.data().getAsJsonObject().getAsJsonArray("logs").get(0)
				.getAsJsonObject().get("text").getAsString());
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}

	private static final class SequentialIds implements Supplier<UUID> {
		private final Queue<UUID> ids = new ArrayDeque<>();

		private SequentialIds() {
			for (int index = 100; index < 140; index++) ids.add(uuid(index));
		}

		@Override public synchronized UUID get() {
			return ids.remove();
		}
	}
}
