package dev.copperbench.agent;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AgentVerificationLifecycleTest {
    @TempDir Path root;

    private String seed() throws Exception {
        AgentChatStore store = new AgentChatStore(root); var session = store.create("codex"); store.save();
        try (var journal = new AgentRunJournal(root)) {
            journal.acquire();
            JsonObject run = journal.begin(UUID.randomUUID().toString(), session.id,
                    new AgentProvider("codex", "Codex CLI", null, "fixture", "https://example.invalid", null),
                    AgentJson.object("task", "Fixture", "attachments", new JsonArray()));
            journal.finish(run, "completed", AgentJson.object("cleanup", "confirmed"), null,
                    AgentJson.object("coverage", "complete", "files", new JsonArray()));
            return AgentJson.string(run, "runId");
        }
    }

    private AgentVerification verification(ControlledTasks tasks) {
        UUID workspace = UUID.randomUUID(); var store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(workspace, "Verification", "mod", 0, false,
                AgentJson.object("id", "fabric-1.21.1", "loader", "fabric", "minecraftVersion", "1.21.1", "state", "ready"), new JsonObject(), List.of()));
        var core = new WorkspaceApplicationService(store, tasks, Clock.systemUTC(), UUID::randomUUID);
        return new AgentVerification(new WorkspaceEntryAdapter(core, new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)), workspace);
    }
    private JsonObject request(String run) {
        return AgentJson.object("operation", "verify", "runId", run, "kind", "build", "requestId", UUID.randomUUID());
    }

    @Test void closingCancelsATaskWhoseStartReceiptArrivesAfterShutdown() throws Exception {
        String run = seed(); var tasks = new ControlledTasks(true, false);
        AgentChatService service = new AgentChatService(root); service.setVerification(verification(tasks));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                service.handle(request(run)); assertTrue(tasks.entered.await(5, TimeUnit.SECONDS));
                executor.submit(service::close).get(5, TimeUnit.SECONDS);
            } finally { tasks.release.countDown(); }
            assertTrue(tasks.cancelled.await(5, TimeUnit.SECONDS), "A late accepted Core task must still be cancelled");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            JsonObject check;
            do {
                check = JsonParser.parseString(Files.readString(root.resolve(".copperbench/agent-chat/runs/" + run + ".json"))).getAsJsonObject().getAsJsonObject("verification");
                if (AgentJson.string(check, "cleanup").equals("core_terminal")) break;
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            assertEquals("cancelled", AgentJson.string(check, "status"));
            assertEquals("core_terminal", AgentJson.string(check, "cleanup"));
        } finally { tasks.release.countDown(); service.close(); }
    }

    @Test void failedFinalFingerprintKeepsTheKnownCoreOutcomeAndReleasesTheExecutionBlock() throws Exception {
        String run = seed(); var tasks = new ControlledTasks(false, true); AtomicInteger captures = new AtomicInteger();
        try (var service = new AgentChatService(new AgentWorkspaceContext(root, null, root), new AgentDesktopActions(), path -> {
            if (captures.incrementAndGet() == 2) throw new IOException("Final fingerprint unavailable");
            return AgentFileSnapshot.capture(path);
        })) {
            service.setVerification(verification(tasks)); service.handle(request(run));
            JsonObject finished = ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
            JsonObject check = AgentJson.child(AgentJson.child(finished, "run"), "verification");
            assertEquals("passed", AgentJson.string(check, "status"));
            assertEquals("core_terminal", AgentJson.string(check, "cleanup"));
            assertTrue(AgentJson.bool(check, "stale")); assertTrue(check.has("fingerprintError"));
            assertFalse(AgentJson.bool(finished, "writeBlocked"));
            try (var journal = new AgentRunJournal(root)) { journal.acquire(); journal.release(); }
            assertFalse(service.handle(AgentJson.object("operation", "new", "provider", "codex")).has("error"));
            assertFalse(service.handle(AgentJson.object("operation", "deleteConversation", "conversationId", finished.get("conversationId"), "confirmed", true)).has("error"));
        }
    }

    private static final class ControlledTasks implements WorkspaceTaskGateway {
        final InMemoryWorkspaceTaskGateway delegate = new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID);
        final CountDownLatch entered = new CountDownLatch(1), release, cancelled = new CountDownLatch(1);
        final boolean completed;
        ControlledTasks(boolean delay, boolean completed) { release = new CountDownLatch(delay ? 1 : 0); this.completed = completed; }
        @Override public JsonObject start(UUID workspace, Operation operation, JsonObject payload) {
            entered.countDown(); boolean interrupted = false;
            while (release.getCount() > 0) try { release.await(); } catch (InterruptedException stopped) { interrupted = true; }
            JsonObject task = delegate.start(workspace, operation, payload);
            if (interrupted) Thread.currentThread().interrupt();
            return result(task);
        }
        private JsonObject result(JsonObject task) {
            if (completed) { task.addProperty("state", "succeeded"); task.addProperty("progress", 1); }
            return task;
        }
        @Override public Optional<JsonObject> find(UUID workspace, UUID task) { return delegate.find(workspace, task).map(this::result); }
        @Override public List<JsonObject> active(UUID workspace) { return completed ? List.of() : delegate.active(workspace); }
        @Override public Optional<JsonObject> cancel(UUID workspace, UUID task) {
            var result = delegate.cancel(workspace, task); cancelled.countDown(); return result;
        }
    }
}
