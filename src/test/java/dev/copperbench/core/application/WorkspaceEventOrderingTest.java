package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class WorkspaceEventOrderingTest {
	private static final UUID ID = UUID.randomUUID();
	private final RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
	private final Events gateway = new Events();

	private WorkspaceApplicationService service(Supplier<UUID> ids) {
		store.register(new WorkspaceState(ID, "Events", "mod", 0, false, new JsonObject(), new JsonObject(), List.of()));
		return new WorkspaceApplicationService(store, gateway, Clock.systemUTC(), ids);
	}

	@Test void allocationAndRetentionStayOrderedWhenAnEarlierPublisherPauses() throws Exception {
		CountDownLatch firstAllocated = new CountDownLatch(1), releaseFirst = new CountDownLatch(1), secondFinished = new CountDownLatch(1);
		var service = service(() -> {
			if (Thread.currentThread().getName().equals("first-event")) {
				firstAllocated.countDown(); await(releaseFirst);
			}
			return UUID.randomUUID();
		});
		List<Long> received = new CopyOnWriteArrayList<>(); CountDownLatch delivered = new CountDownLatch(2);
		try (var subscription = service.subscribeEvents(ID, 0, event -> { received.add(event.sequence()); delivered.countDown(); })) {
			Thread first = Thread.ofPlatform().name("first-event").start(gateway::emit);
			assertTrue(firstAllocated.await(5, TimeUnit.SECONDS));
			Thread second = Thread.startVirtualThread(() -> { gateway.emit(); secondFinished.countDown(); });
			try { secondFinished.await(250, TimeUnit.MILLISECONDS); }
			finally { releaseFirst.countDown(); }
			first.join(5000); second.join(5000);
			assertTrue(delivered.await(5, TimeUnit.SECONDS));
			assertEquals(List.of(1L, 2L), received);
		}
		List<Long> replayed = new CopyOnWriteArrayList<>(); CountDownLatch replay = new CountDownLatch(2);
		try (var subscription = service.subscribeEvents(ID, 0, event -> { replayed.add(event.sequence()); replay.countDown(); })) {
			assertTrue(replay.await(5, TimeUnit.SECONDS));
			assertEquals(List.of(1L, 2L), replayed);
		}
	}

	@Test void slowListenerDoesNotBlockCommandsAndReconnectCannotOvertakeReplay() throws Exception {
		var service = service(UUID::randomUUID);
		CountDownLatch listenerEntered = new CountDownLatch(1), release = new CountDownLatch(1), delivered = new CountDownLatch(3);
		List<Long> received = new CopyOnWriteArrayList<>(), replayed = new CopyOnWriteArrayList<>();
		try (var subscription = service.subscribeEvents(ID, 0, event -> {
			if (event.sequence() == 1) { listenerEntered.countDown(); await(release); }
			received.add(event.sequence()); delivered.countDown();
		})) {
			gateway.emit(); assertTrue(listenerEntered.await(5, TimeUnit.SECONDS));
			try {
				CompletableFuture<Void> writers = CompletableFuture.runAsync(() -> {
					gateway.emit();
					var payload = JsonParser.parseString("{\"registry\":\"variables\",\"entry\":{\"name\":\"counter\"}}").getAsJsonObject();
					var outcome = service.executeAndPublish(Command.of(UUID.randomUUID(), ID, 0, Operation.CREATE_REGISTRY_ENTRY, payload),
							new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
					assertEquals("committed", outcome.result().status());
				});
				writers.get(5, TimeUnit.SECONDS);
				assertEquals(1, store.read(ID).orElseThrow().revision());
				CountDownLatch replay = new CountDownLatch(4);
				try (var reconnect = service.subscribeEvents(ID, 0, event -> { replayed.add(event.sequence()); replay.countDown(); })) {
					gateway.emit(); // Queued after replay, even while the first live listener is still blocked.
					release.countDown();
					assertTrue(delivered.await(5, TimeUnit.SECONDS));
					assertTrue(replay.await(5, TimeUnit.SECONDS));
					assertEquals(List.of(1L, 2L, 3L, 4L), replayed);
					assertEquals(List.of(1L, 2L, 3L), received.subList(0, 3));
				}
			} finally { release.countDown(); }
		}
	}

	private static void await(CountDownLatch latch) {
		try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
		catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
	}

	private static final class Events implements WorkspaceTaskGateway {
		private Consumer<TaskEvent> listener;
		public AutoCloseable subscribeTaskEvents(Consumer<TaskEvent> consumer) { listener = consumer; return () -> {}; }
		public JsonObject start(UUID id, Operation operation, JsonObject payload) { throw new UnsupportedOperationException(); }
		public Optional<JsonObject> find(UUID id, UUID taskId) { return Optional.empty(); }
		public List<JsonObject> active(UUID id) { return List.of(); }
		public Optional<JsonObject> cancel(UUID id, UUID taskId) { return Optional.empty(); }
		void emit() { listener.accept(new TaskEvent(ID, UUID.randomUUID(), "task_progressed", new JsonObject(), List.of(), List.of())); }
	}
}
