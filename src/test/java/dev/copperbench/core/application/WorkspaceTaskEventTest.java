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
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.GradleWorkspaceBackend;
import dev.copperbench.generator.GradleWorkspaceTaskGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

	@Test void asynchronousGradleTaskPublishesFailureAndReplaysCancellationAfterReconnect(@TempDir Path tempDir)
			throws Exception {
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
		CountDownLatch cancellableRunStarted = new CountDownLatch(1);
		AtomicInteger runs = new AtomicInteger();
		GradleProcessRunner runner = (root, arguments, timeout, output) -> {
			int run = runs.incrementAndGet();
			output.accept(run == 1 ? "synthetic compile failure" : "synthetic long-running build");
			if (run == 1) return new GradleProcessRunner.ProcessResult(1, false);
			cancellableRunStarted.countDown();
			try {
				new CountDownLatch(1).await();
				return new GradleProcessRunner.ProcessResult(0, false);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return new GradleProcessRunner.ProcessResult(130, false);
			}
		};
		GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
			@Override public String displayName() { return "Synthetic Fabric"; }
			@Override public String diagnosticPrefix() { return "SYNTHETIC"; }
			@Override public List<ValidationIssue> validate(WorkspaceState workspace) { return List.of(); }
			@Override public GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws Exception {
				Files.createDirectories(targetRoot);
				return new GenerationResult("fabric-1.21.1", "copper_trails", List.of("src/generated.txt"));
			}
			@Override public boolean buildOutputAvailable(Path targetRoot) { return true; }
		};

		try (GradleWorkspaceTaskGateway tasks = new GradleWorkspaceTaskGateway(store, ignored -> tempDir,
				backend, CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
			List<UiCore.Event> received = new CopyOnWriteArrayList<>();
			AutoCloseable subscription = service.subscribeEvents(WORKSPACE_ID, 0, received::add);

			UUID failedTask = startBuild(service, 20);
			awaitTaskState(tasks, failedTask, "failed");
			assertTrue(received.stream().anyMatch(event -> event.event().equals("task_progressed")));
			assertTrue(received.stream().anyMatch(event -> event.event().equals("task_log_appended")));
			assertTrue(received.stream().anyMatch(event -> event.event().equals("diagnostics_changed")));
			assertTrue(received.stream().anyMatch(event -> event.event().equals("task_completed")
					&& event.payload().getAsJsonObject("task").get("state").getAsString().equals("failed")));

			UUID cancelledTask = startBuild(service, 30);
			assertTrue(cancellableRunStarted.await(5, TimeUnit.SECONDS));
			awaitEvent(received, "task_log_appended", cancelledTask);
			long disconnectSequence = received.getLast().sequence();
			subscription.close();

			JsonObject cancelPayload = new JsonObject();
			cancelPayload.addProperty("clientMutationId", uuid(31).toString());
			cancelPayload.addProperty("taskId", cancelledTask.toString());
			var cancelled = service.execute(Command.of(uuid(32), WORKSPACE_ID, 0, Operation.CANCEL_TASK,
					cancelPayload), new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
			assertEquals("cancelled", cancelled.result().status());

			List<UiCore.Event> replayed = new ArrayList<>();
			try (AutoCloseable reconnect = service.subscribeEvents(WORKSPACE_ID, disconnectSequence, replayed::add)) {
				assertFalse(replayed.isEmpty());
				assertTrue(replayed.stream().allMatch(event -> event.sequence() > disconnectSequence));
				assertTrue(replayed.stream().anyMatch(event -> event.event().equals("task_log_appended")));
				assertTrue(replayed.stream().anyMatch(event -> event.event().equals("task_completed")
						&& event.payload().getAsJsonObject("task").get("state").getAsString().equals("cancelled")));
			}
		}
	}

	private static UUID startBuild(WorkspaceApplicationService service, long suffix) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(suffix).toString());
		payload.addProperty("scope", "workspace");
		var started = service.execute(Command.of(uuid(suffix + 1), WORKSPACE_ID, 0, Operation.BUILD_WORKSPACE, payload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		return UUID.fromString(started.result().task().getAsJsonObject().get("id").getAsString());
	}

	private static void awaitTaskState(WorkspaceTaskGateway tasks, UUID taskId, String expected) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			var task = tasks.find(WORKSPACE_ID, taskId).orElseThrow();
			if (task.get("state").getAsString().equals(expected)) return;
			Thread.sleep(10);
		}
		throw new AssertionError("Task did not reach state " + expected);
	}

	private static void awaitEvent(List<UiCore.Event> events, String eventName, UUID taskId) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			if (events.stream().anyMatch(event -> event.event().equals(eventName)
					&& event.payload().has("taskId")
					&& event.payload().get("taskId").getAsString().equals(taskId.toString()))) return;
			Thread.sleep(10);
		}
		throw new AssertionError("Task event did not arrive: " + eventName);
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
