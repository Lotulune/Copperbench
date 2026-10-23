package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTaskCancellationTest {
    @TempDir Path root;
    @Test @Timeout(35) void cancellationMustNotWaitForAWorkerBlockedOnTheWorkspaceLock() throws Exception {
        UUID id = UUID.randomUUID(); RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(id, "Probe", "mod", 0, false, new JsonObject(), new JsonObject(), List.of()));
        CountDownLatch beforeCoreListener = new CountDownLatch(1), releaseListener = new CountDownLatch(1), cancellationObserved = new CountDownLatch(1);
        GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
            public String displayName() { return "Review fixture"; }
            public String diagnosticPrefix() { return "REVIEW"; }
            public List<ValidationIssue> validate(WorkspaceState state) { return List.of(); }
            public GenerationResult generate(Path path, WorkspaceState state) { return new GenerationResult("fixture", "probe", List.of()); }
        };
        try (var tasks = new GradleWorkspaceTaskGateway(store, ignored -> root, backend, Clock.systemUTC(), UUID::randomUUID,
                (path, args, timeout, output) -> { throw new AssertionError("No external process should start"); })) {
            tasks.subscribeTaskEvents(event -> {
                if (!event.event().equals("task_progressed") || beforeCoreListener.getCount() == 0) return;
                beforeCoreListener.countDown(); boolean interrupted = false;
                while (true) try { releaseListener.await(); break; }
                catch (InterruptedException exception) { interrupted = true; cancellationObserved.countDown(); }
                if (interrupted) Thread.currentThread().interrupt();
            });
            var service = new WorkspaceApplicationService(store, tasks, Clock.systemUTC(), UUID::randomUUID);
            var context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
            var start = service.execute(Command.of(UUID.randomUUID(), id, 0, Operation.GENERATE_WORKSPACE, new JsonObject()), context);
            UUID taskId = UUID.fromString(start.result().task().getAsJsonObject().get("id").getAsString());
            assertTrue(beforeCoreListener.await(5, TimeUnit.SECONDS));
            JsonObject payload = new JsonObject(); payload.addProperty("taskId", taskId.toString());
            long begin = System.nanoTime();
            CompletableFuture<CommandOutcome> cancelled = CompletableFuture.supplyAsync(() ->
                    service.execute(Command.of(UUID.randomUUID(), id, 0, Operation.CANCEL_TASK, payload), context));
            try { assertTrue(cancellationObserved.await(5, TimeUnit.SECONDS)); }
            finally { releaseListener.countDown(); }
            var result = cancelled.get(20, TimeUnit.SECONDS).result();
            assertEquals("cancelled", result.status());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin) < 5000,
                    "Cancellation must not consume the 15-second lock timeout");
        } finally { releaseListener.countDown(); }
    }
}
