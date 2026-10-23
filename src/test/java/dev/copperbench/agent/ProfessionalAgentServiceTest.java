package dev.copperbench.agent;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("agent-cli-system-properties")
class ProfessionalAgentServiceTest {
    @TempDir Path root;
    private String previous;
    @BeforeEach void setup() throws Exception {
        previous = System.getProperty("copperbench.agent.codex");
        Path wrapper = root.resolve(AgentProcess.windows() ? "fixture.ps1" : "fixture.sh");
        String java = ProfessionalAgentFixtureCli.java(), cp = ProfessionalAgentTestSupport.classpath();
        Files.writeString(wrapper, AgentProcess.windows()
                ? "& '" + java.replace("'", "''") + "' '-cp' '" + cp.replace("'", "''") + "' '" + ProfessionalAgentFixtureCli.class.getName() + "' 'codex' @args\nexit $LASTEXITCODE\n"
                : "#!/bin/sh\nexec '" + java + "' -cp '" + cp + "' " + ProfessionalAgentFixtureCli.class.getName() + " codex \"$@\"\n");
        if (!AgentProcess.windows()) assertTrue(wrapper.toFile().setExecutable(true));
        System.setProperty("copperbench.agent.codex", wrapper.toString());
    }
    @AfterEach void restore() { if (previous == null) System.clearProperty("copperbench.agent.codex"); else System.setProperty("copperbench.agent.codex", previous); }
    private JsonObject send(String id) { return AgentJson.object("operation", "send", "provider", "codex", "text", "Preserve the user's edit; repair the fixture", "requestId", id); }

    @Test void modelDiscoveryDoesNotSendATurnAndConfiguredRunsRecordTheSelectedSettings() throws Exception {
        try (AgentChatService service = new AgentChatService(root)) {
            service.handle(AgentJson.object("operation", "settingsCatalog", "provider", "codex"));
            JsonObject catalog = ProfessionalAgentTestSupport.await(service, value -> "ready".equals(AgentJson.string(AgentJson.child(AgentJson.child(value, "modelCatalogs"), "codex"), "state")));
            assertTrue(catalog.getAsJsonArray("messages").isEmpty()); assertFalse(service.isBusy());
            assertFalse(Files.readString(root.resolve("fixture-frames.jsonl")).contains("turn/start"));
            JsonObject request = send(UUID.randomUUID().toString()); request.add("settings", new AgentRunSettings("fixture-model", "high", "workspace").json());
            service.handle(request);
            JsonObject waiting = ProfessionalAgentTestSupport.await(service, value -> AgentJson.string(value, "state").equals("waiting_approval"));
            assertEquals("high", AgentJson.string(AgentJson.child(waiting, "settings"), "effort"));
            assertEquals("fixture-model", AgentJson.string(AgentJson.child(AgentJson.child(AgentJson.child(waiting, "run"), "context"), "settings"), "model"));
            service.handle(AgentJson.object("operation", "stop")); ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
        }
        try (AgentChatService reopened = new AgentChatService(root)) {
            assertEquals("high", AgentJson.string(AgentJson.child(reopened.handle(new JsonObject()), "settings"), "effort"));
            reopened.handle(send(UUID.randomUUID().toString()));
            ProfessionalAgentTestSupport.await(reopened, value -> AgentJson.string(value, "state").equals("waiting_approval"));
            assertTrue(Files.readString(root.resolve("fixture-frames.jsonl")).contains("\"effort\":\"high\""));
            reopened.handle(AgentJson.object("operation", "stop")); ProfessionalAgentTestSupport.await(reopened, value -> !reopened.isBusy());
        }
    }

    @Test void duplicateSubmissionAndReconnectNeverStartAnotherProcess() throws Exception {
        String id = UUID.randomUUID().toString();
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject first = service.handle(send(id));
            JsonObject waiting = ProfessionalAgentTestSupport.await(service, value -> AgentJson.string(value, "state").equals("waiting_approval"));
            String runId = AgentJson.string(AgentJson.child(first, "run"), "runId");
            assertEquals(runId, AgentJson.string(AgentJson.child(service.handle(send(id)), "run"), "runId"));
            assertEquals(1, service.handle(new JsonObject()).getAsJsonArray("runs").size());
            JsonObject approval = waiting.getAsJsonArray("approvals").get(0).getAsJsonObject();
            service.handle(AgentJson.object("operation", "stop")); service.handle(AgentJson.object("operation", "stop"));
            service.handle(AgentJson.object("operation", "approve", "id", approval.get("id"), "choice", "choice-0"));
            JsonObject stopped = ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
            JsonObject run = AgentJson.child(stopped, "run");
            assertEquals("cancelled", AgentJson.string(run, "state"));
            assertEquals("confirmed", AgentJson.string(AgentJson.child(run, "execution"), "cleanup"));
            assertTrue(AgentJson.list(stopped, "approvals").isEmpty());
            assertEquals(runId, AgentJson.string(AgentJson.child(service.handle(send(id)), "run"), "runId"));
            assertFalse(service.isBusy());
        }
        try (AgentChatService reopened = new AgentChatService(root)) {
            assertEquals(1, AgentJson.list(reopened.handle(new JsonObject()), "runs").size());
            assertFalse(reopened.isBusy());
            assertEquals(1, Files.readString(root.resolve("fixture-frames.jsonl")).lines().filter(line -> line.contains("\"method\":\"turn/start\"")).count());
        }
    }

    @Test void readyAfterAnEarlyApprovalPreservesTheWaitingRunState() throws Exception {
        Files.writeString(root.resolve("fixture-early-approval"), "enabled");
        try (var service = new AgentChatService(root)) {
            service.handle(send(UUID.randomUUID().toString()));
            JsonObject waiting = ProfessionalAgentTestSupport.await(service, value -> AgentJson.string(value, "state").equals("waiting_approval")
                    && AgentJson.list(value, "messages").asList().stream().map(JsonElement::getAsJsonObject)
                        .anyMatch(message -> AgentJson.string(message, "role").equals("user") && AgentJson.string(message, "delivery").equals("sent")));
            assertEquals("waiting_for_input", AgentJson.string(AgentJson.child(waiting, "run"), "state"));
            service.handle(AgentJson.object("operation", "stop"));
            ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
        }
    }

    @Test void fileChangesAndCoreVerificationHaveIndependentStatesAndCancellation() throws Exception {
        Files.writeString(root.resolve("existing.txt"), "user edit\n");
        try (AgentChatService service = new AgentChatService(root)) {
            service.handle(send(UUID.randomUUID().toString()));
            JsonObject waiting = ProfessionalAgentTestSupport.await(service, value -> AgentJson.string(value, "state").equals("waiting_approval"));
            Files.writeString(root.resolve("existing.txt"), "user edit\nadditional edit\n");
            JsonObject approval = waiting.getAsJsonArray("approvals").get(0).getAsJsonObject();
            service.handle(AgentJson.object("operation", "approve", "id", approval.get("id"), "choice", "choice-0"));
            JsonObject completed = ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
            JsonObject run = AgentJson.child(completed, "run"); String runId = AgentJson.string(run, "runId");
            assertEquals("completed", AgentJson.string(run, "state"));
            assertEquals("not_run", AgentJson.string(AgentJson.child(run, "verification"), "status"));
            JsonObject diff = AgentJson.list(AgentJson.child(run, "changes"), "files").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(file -> AgentJson.string(file, "path").equals("existing.txt")).findFirst().orElseThrow();
            assertEquals("user edit\n", AgentJson.string(AgentJson.child(diff, "before"), "text"));
            UUID workspaceId = UUID.randomUUID(); RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
            store.register(new WorkspaceState(workspaceId, "Acceptance", "mod", 0, false,
                    AgentJson.object("id", "fabric-1.21.1", "loader", "fabric", "minecraftVersion", "1.21.1", "displayName", "Fabric", "state", "ready"), new JsonObject(), List.of()));
            var core = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
            service.setVerification(new AgentVerification(new WorkspaceEntryAdapter(core, new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)), workspaceId));
            JsonObject verify = AgentJson.object("operation", "verify", "runId", runId, "kind", "build", "requestId", UUID.randomUUID());
            JsonObject started = service.handle(verify);
            assertEquals("running", AgentJson.string(AgentJson.child(AgentJson.child(started, "run"), "verification"), "status"), started.toString());
            started = ProfessionalAgentTestSupport.await(service, value -> !AgentJson.string(AgentJson.child(AgentJson.child(value, "run"), "verification"), "executionTaskId").isEmpty());
            String taskId = AgentJson.string(AgentJson.child(AgentJson.child(started, "run"), "verification"), "executionTaskId");
            assertEquals(taskId, AgentJson.string(AgentJson.child(AgentJson.child(service.handle(verify), "run"), "verification"), "executionTaskId"));
            Files.writeString(root.resolve("during-verification.txt"), "An independent edit during verification");
            service.handle(AgentJson.object("operation", "stopVerification", "runId", runId));
            JsonObject stopped = ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
            assertEquals("completed", AgentJson.string(AgentJson.child(stopped, "run"), "state"));
            assertEquals("cancelled", AgentJson.string(AgentJson.child(AgentJson.child(stopped, "run"), "verification"), "status"));
            assertTrue(AgentJson.bool(AgentJson.child(AgentJson.child(stopped, "run"), "verification"), "stale"));
            assertEquals("user edit\nadditional edit\n", Files.readString(root.resolve("existing.txt")));
        }
    }
    @Test void contextAndProjectIdentityAreValidatedBeforeLaunching() throws Exception {
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject request = send(UUID.randomUUID().toString()); request.addProperty("projectId", "another-project");
            assertTrue(service.handle(request).has("error")); assertFalse(Files.exists(root.resolve("fixture-frames.jsonl")));
            request.remove("projectId"); request.add("context", AgentJson.array(AgentJson.object("source", "failed-build", "kind", "log", "text", "api_key=private-value\n" + "x".repeat(70000))));
            service.handle(request);
            JsonObject waiting = ProfessionalAgentTestSupport.await(service, value -> AgentJson.string(value, "state").equals("waiting_approval"));
            JsonObject manifest = AgentJson.child(AgentJson.child(waiting, "run"), "context");
            assertFalse(manifest.toString().contains("private-value"));
            assertTrue(AgentJson.bool(AgentJson.list(manifest, "attachments").get(0).getAsJsonObject(), "truncated"));
            service.handle(AgentJson.object("operation", "stop")); ProfessionalAgentTestSupport.await(service, value -> !service.isBusy());
        }
    }
    @Test void nativeExportWritesOnlyTheSelectedRedactedRunWithoutABrowserDownload() throws Exception {
        try (AgentRunJournal journal = new AgentRunJournal(root)) {
            AgentChatStore store = new AgentChatStore(root); var session = store.create("codex"); store.save();
            journal.acquire(); JsonObject run = journal.begin(UUID.randomUUID().toString(), session.id, new AgentProvider("codex", "Codex", null, "fixture", "", null),
                    AgentJson.object("task", "api_key=private-value", "attachments", new JsonArray()));
            journal.finish(run, "completed", AgentJson.object("cleanup", "confirmed"), null);
        }
        var opened = new java.util.concurrent.CompletableFuture<Path>();
        try (AgentChatService service = new AgentChatService(new AgentWorkspaceContext(root, null, root), new AgentDesktopActions() {
            @Override void reveal(Path file) { opened.complete(file); }
        })) {
            String id = AgentJson.string(AgentJson.child(service.handle(new JsonObject()), "run"), "runId");
            JsonObject result = service.handle(AgentJson.object("operation", "exportRun", "runId", id));
            Path file = Path.of(AgentJson.string(AgentJson.child(result, "exportedRun"), "path"));
            assertEquals(file, opened.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(file.startsWith(root.resolve(".copperbench/agent-chat/exports")));
            assertFalse(Files.readString(file).contains("private-value"));
            assertEquals(id, AgentJson.string(JsonParser.parseString(Files.readString(file)).getAsJsonObject(), "runId"));
        }
    }
}
