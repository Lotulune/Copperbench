package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("agent-cli-system-properties")
class AgentPreparationLifecycleTest {
    @TempDir Path root;
    String previous;
    @BeforeEach void configure() throws Exception { previous = System.getProperty("copperbench.agent.codex"); System.setProperty("copperbench.agent.codex", ProfessionalAgentTestSupport.wrapper(root).toString()); }
    @AfterEach void restore() { if (previous == null) System.clearProperty("copperbench.agent.codex"); else System.setProperty("copperbench.agent.codex", previous); }
    private JsonObject send() { return AgentJson.object("operation", "send", "provider", "codex", "text", "Check this fixture", "requestId", UUID.randomUUID()); }
    private AgentChatService service(AgentRunJournal.Capture capture) {
        return new AgentChatService(new AgentWorkspaceContext(root, null, root), new AgentDesktopActions(), capture);
    }
    @Test void slowBaselineLeavesStatusAndStopResponsiveAndCannotLaunchAfterCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); AtomicInteger calls = new AtomicInteger();
        try (AgentChatService service = service(path -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown(); try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture release timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            return AgentFileSnapshot.capture(path);
        }); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                service.handle(send()); assertTrue(entered.await(5, TimeUnit.SECONDS));
                JsonObject status = executor.submit(() -> service.handle(new JsonObject())).get(1, TimeUnit.SECONDS);
                assertEquals("preparing", AgentJson.string(AgentJson.child(status, "run"), "state"));
                JsonObject stopped = executor.submit(() -> service.handle(AgentJson.object("operation", "stop"))).get(1, TimeUnit.SECONDS);
                assertEquals("stopping", AgentJson.string(stopped, "state"));
            } finally { release.countDown(); }
            JsonObject done = ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
            assertEquals("cancelled", AgentJson.string(AgentJson.child(done, "run"), "state"));
            JsonObject execution = AgentJson.child(AgentJson.child(done, "run"), "execution");
            assertEquals("not_needed", AgentJson.string(execution, "cleanup")); assertFalse(execution.has("pid"));
            assertFalse(Files.exists(root.resolve("fixture-frames.jsonl")), "Cancellation during baseline must not start the CLI later");
        }
    }
    @Test void finalSnapshotDoesNotBlockStatusOrChangeNaturalCompletionIntoCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); AtomicInteger calls = new AtomicInteger();
        try (AgentChatService service = service(path -> {
            if (calls.incrementAndGet() == 2) {
                entered.countDown(); try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture release timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            return AgentFileSnapshot.capture(path);
        }); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                service.handle(send()); JsonObject waiting = ProfessionalAgentTestSupport.await(service, value -> AgentJson.string(value, "state").equals("waiting_approval"));
                String id = AgentJson.string(waiting.getAsJsonArray("approvals").get(0).getAsJsonObject(), "id");
                service.handle(AgentJson.object("operation", "approve", "id", id, "choice", "choice-0")); assertTrue(entered.await(10, TimeUnit.SECONDS));
                JsonObject status = executor.submit(() -> service.handle(new JsonObject())).get(1, TimeUnit.SECONDS);
                assertEquals("finalizing", AgentJson.string(status, "activeRunPhase"));
                JsonObject stopped = executor.submit(() -> service.handle(AgentJson.object("operation", "stop"))).get(1, TimeUnit.SECONDS);
                assertEquals("finalizing", AgentJson.string(stopped, "activeRunPhase"));
            } finally { release.countDown(); }
            JsonObject done = ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
            assertEquals("completed", AgentJson.string(AgentJson.child(done, "run"), "state"));
            assertFalse(AgentJson.child(done, "run").has("cancelRequestedAt"));
        }
    }
}
