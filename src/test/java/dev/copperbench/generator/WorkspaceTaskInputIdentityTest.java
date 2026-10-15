package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real file identities and production lifecycle tasks; process execution is an explicit fixture. */
class WorkspaceTaskInputIdentityTest {
    @TempDir Path root;
    private static final UUID WORKSPACE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Clock CLOCK = Clock.systemUTC();

    @Test void buildClientAndExportDetectSameRevisionExternalChangesWithoutClaimingAcceptance() throws Exception {
        Path source = source("class Manual {}\n");
        var store = store(); long revision = store.read(WORKSPACE).orElseThrow().revision();
        List<JsonObject> tasks = new ArrayList<>();
        try (var gateway = gateway(store, (directory, args, timeout, output) -> successfulProcess(directory, args))) {
            for (Operation operation : List.of(Operation.BUILD_WORKSPACE, Operation.RUN_CLIENT, Operation.EXPORT_WORKSPACE)) {
                JsonObject payload = new JsonObject(); payload.addProperty("output", "build/libs/delivery/result.jar");
                tasks.add(completed(gateway, gateway.start(WORKSPACE, operation, payload)));
            }
            JsonObject unchanged = WorkspaceTaskInputIdentity.observe(root, WORKSPACE, tasks, CLOCK);
            for (JsonObject task : tasks) {
                assertEquals("current", fact(unchanged, task).get("status").getAsString());
                assertFalse(task.has("verification"), "ordinary lifecycle facts are not GameTest acceptance");
                assertTrue(Files.isRegularFile(Path.of(task.getAsJsonObject("sourceSnapshot").get("manifestPath").getAsString())));
            }
            Files.writeString(source, "class Manual {} // external edit\n", StandardCharsets.UTF_8);
            assertEquals(revision, store.read(WORKSPACE).orElseThrow().revision());
            JsonObject changed = WorkspaceTaskInputIdentity.observe(root, WORKSPACE, tasks, CLOCK);
            for (JsonObject task : tasks) {
                assertEquals("succeeded", gateway.find(WORKSPACE, UUID.fromString(task.get("id").getAsString())).orElseThrow().get("state").getAsString());
                assertEquals("changed", fact(changed, task).get("status").getAsString());
            }
            assertFalse(Files.exists(root.resolve(".copperbench/task-runs/build")), "identity observation must not copy an execution workspace");
        }
    }

    @Test void aChangeDuringExecutionRemainsUnconfirmedEvenAfterItsOriginalBytesAreRestored() throws Exception {
        String original = "class Manual {}\n"; Path source = source(original);
        var store = store();
        try (var gateway = gateway(store, (directory, args, timeout, output) -> {
            Files.writeString(source, "class Manual {} // changed during process\n", StandardCharsets.UTF_8);
            return successfulProcess(directory, args);
        })) {
            JsonObject task = completed(gateway, gateway.start(WORKSPACE, Operation.BUILD_WORKSPACE, new JsonObject()));
            assertEquals("succeeded", task.get("state").getAsString());
            Files.writeString(source, original, StandardCharsets.UTF_8);
            assertEquals("changed_during_task", fact(WorkspaceTaskInputIdentity.observe(root, WORKSPACE, List.of(task), CLOCK), task).get("status").getAsString());
        }
    }

    @Test void legacyAndIncompleteIdentityReceiptsStayUnknown() throws Exception {
        source("class Manual {}\n");
        JsonObject legacy = task(UUID.randomUUID());
        JsonObject incomplete = task(UUID.randomUUID());
        incomplete.add("sourceSnapshot", WorkspaceTaskInputIdentity.start(root, WORKSPACE,
                UUID.fromString(incomplete.get("id").getAsString()), 4, CLOCK, () -> false));
        JsonObject observation = WorkspaceTaskInputIdentity.observe(root, WORKSPACE, List.of(legacy, incomplete), CLOCK);
        assertEquals("unknown", fact(observation, legacy).get("status").getAsString());
        assertEquals("unknown", fact(observation, incomplete).get("status").getAsString());
        assertTrue(observation.get("currentSha256").isJsonNull());
    }

    @Test void anEditRestoredBeforeProcessExitIsStillDetectedByInputEvents() throws Exception {
        String original = "class Manual {}\n"; Path source = source(original);
        try (var gateway = gateway(store(), (directory, args, timeout, output) -> {
            Files.writeString(source, "class Manual {} // transient edit\n", StandardCharsets.UTF_8);
            Files.writeString(source, original, StandardCharsets.UTF_8);
            return successfulProcess(directory, args);
        })) {
            JsonObject task = completed(gateway, gateway.start(WORKSPACE, Operation.BUILD_WORKSPACE, new JsonObject()));
            assertEquals("succeeded", task.get("state").getAsString());
            assertEquals("changed_during_task", fact(WorkspaceTaskInputIdentity.observe(root, WORKSPACE, List.of(task), CLOCK), task).get("status").getAsString());
        }
    }

    @Test void unavailableIdentityStorageDoesNotBlockOrdinaryExportOrOverwriteItsConflictingPath() throws Exception {
        source("class Manual {}\n");
        Files.createDirectories(root.resolve(".copperbench"));
        Path conflicting = root.resolve(".copperbench/task-inputs");
        Files.writeString(conflicting, "preserve this file", StandardCharsets.UTF_8);
        var store = store();
        try (var gateway = gateway(store, (directory, args, timeout, output) -> successfulProcess(directory, args))) {
            JsonObject payload = new JsonObject(); payload.addProperty("output", "build/libs/delivery/result.jar");
            JsonObject task = completed(gateway, gateway.start(WORKSPACE, Operation.EXPORT_WORKSPACE, payload));
            assertEquals("succeeded", task.get("state").getAsString());
            assertTrue(Files.isRegularFile(root.resolve("build/libs/delivery/result.jar")));
            assertFalse(task.has("sourceSnapshot"));
            assertEquals("unknown", fact(WorkspaceTaskInputIdentity.observe(root, WORKSPACE, List.of(task), CLOCK), task).get("status").getAsString());
            assertTrue(gateway.logs(WORKSPACE, UUID.fromString(task.get("id").getAsString())).toString().contains("Task input identity unavailable"));
            assertEquals("preserve this file", Files.readString(conflicting));
        }
    }

    @Test void anIdentityReadFailureRetainsItsCauseAndCannotBecomeCurrent() throws Exception {
        source("class Manual {}\n"); UUID id = UUID.randomUUID(); JsonObject task = task(id);
        task.add("sourceSnapshot", WorkspaceTaskInputIdentity.start(root, WORKSPACE, id, 4, CLOCK, () -> false));
        WorkspaceTaskInputIdentity.unavailable(root, WORKSPACE, id, "WORKSPACE_SNAPSHOT_TOO_LARGE: bounded input observation unavailable", CLOCK);
        JsonObject fact = fact(WorkspaceTaskInputIdentity.observe(root, WORKSPACE, List.of(task), CLOCK), task);
        assertEquals("unknown", fact.get("status").getAsString());
        assertTrue(fact.get("detail").getAsString().contains("WORKSPACE_SNAPSHOT_TOO_LARGE"));
    }

    @Test void aFailedBuildRemainsFailedAndItsOriginalLogIsAvailable() throws Exception {
        source("class Manual {}\n");
        try (var gateway = gateway(store(), (directory, args, timeout, output) -> {
            output.accept("Original compiler failure"); return new GradleProcessRunner.ProcessResult(7, false);
        })) {
            JsonObject task = completed(gateway, gateway.start(WORKSPACE, Operation.BUILD_WORKSPACE, new JsonObject()));
            assertEquals("failed", task.get("state").getAsString());
            assertTrue(gateway.logs(WORKSPACE, UUID.fromString(task.get("id").getAsString())).toString().contains("Original compiler failure"));
            assertEquals("unknown", fact(WorkspaceTaskInputIdentity.observe(root, WORKSPACE, List.of(task), CLOCK), task).get("status").getAsString());
        }
    }

    @Test void cancellationWaitsForCleanupBeforeChangingTheOriginalTaskToCancelled() throws Exception {
        source("class Manual {}\n");
        var started = new CountDownLatch(1); var cleanup = new CountDownLatch(1); var finishCleanup = new CountDownLatch(1);
        try (var gateway = gateway(store(), (directory, args, timeout, output) -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException cancelled) {
                cleanup.countDown(); finishCleanup.await(5, TimeUnit.SECONDS); throw cancelled;
            }
            throw new AssertionError("Only the cancellation path can finish this process fixture");
        }); var cancelCalls = Executors.newVirtualThreadPerTaskExecutor()) {
            JsonObject original = gateway.start(WORKSPACE, Operation.BUILD_WORKSPACE, new JsonObject());
            UUID id = UUID.fromString(original.get("id").getAsString());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var pending = cancelCalls.submit(() -> gateway.cancel(WORKSPACE, id).orElseThrow());
            assertTrue(cleanup.await(5, TimeUnit.SECONDS));
            assertFalse(pending.isDone());
            assertEquals("running", gateway.find(WORKSPACE, id).orElseThrow().get("state").getAsString());
            finishCleanup.countDown();
            assertEquals("cancelled", pending.get(5, TimeUnit.SECONDS).get("state").getAsString());
            assertEquals(id.toString(), gateway.find(WORKSPACE, id).orElseThrow().get("id").getAsString());
        } finally { finishCleanup.countDown(); }
    }

    private Path source(String text) throws Exception {
        Path path = root.resolve("src/main/java/Manual.java"); Files.createDirectories(path.getParent());
        return Files.writeString(path, text, StandardCharsets.UTF_8);
    }
    private static RevisionedWorkspaceStore store() {
        var store = new RevisionedWorkspaceStore(); store.register(Fabric1211GoldenWorkspace.create()); return store;
    }
    private GradleWorkspaceTaskGateway gateway(RevisionedWorkspaceStore store, GradleProcessRunner runner) {
        GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
            public String displayName() { return "Identity lifecycle fixture"; }
            public String diagnosticPrefix() { return "IDENTITY"; }
            public List<ValidationIssue> validate(WorkspaceState workspace) { return List.of(); }
            public GenerationResult generate(Path target, WorkspaceState workspace) { return new GenerationResult("fabric-1.21.1", "identity_fixture", List.of()); }
        };
        return new GradleWorkspaceTaskGateway(store, ignored -> root, backend, CLOCK, UUID::randomUUID, runner);
    }
    private static GradleProcessRunner.ProcessResult successfulProcess(Path directory, List<String> args) throws Exception {
        Files.createDirectories(directory.resolve("build/libs"));
        Files.writeString(directory.resolve("build/libs/mod.jar"), "fixture artifact", StandardCharsets.UTF_8);
        GradleArchiveOutputFixture.record(args, directory.resolve("build/libs/mod.jar"));
        return new GradleProcessRunner.ProcessResult(0, true);
    }
    private static JsonObject task(UUID id) {
        JsonObject task = new JsonObject(); task.addProperty("id", id.toString()); task.addProperty("state", "succeeded"); return task;
    }
    private static JsonObject fact(JsonObject observation, JsonObject task) {
        return observation.getAsJsonObject("tasks").getAsJsonObject(task.get("id").getAsString());
    }
    private static JsonObject completed(GradleWorkspaceTaskGateway gateway, JsonObject started) throws Exception {
        UUID id = UUID.fromString(started.get("id").getAsString());
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        JsonObject task;
        do {
            task = gateway.find(WORKSPACE, id).orElseThrow();
            if (!List.of("queued", "running").contains(task.get("state").getAsString())) return task;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("The isolated task did not complete"); return task;
    }
}
