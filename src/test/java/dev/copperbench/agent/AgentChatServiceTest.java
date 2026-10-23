package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("agent-cli-system-properties")
class AgentChatServiceTest {
    @TempDir Path workspace;

    @Test void legacy015HistoryResumesTheSameNativeSessionWithoutAModelOverrideAfterReopen() throws Exception {
        String key = "copperbench.agent.codex", old = System.getProperty(key);
        System.setProperty(key, wrapper("codex").toString());
        Path history = workspace.resolve(".copperbench/agent-chat/history.json"); Files.createDirectories(history.getParent());
        try (var input = getClass().getResourceAsStream("/dev/copperbench/agent/history-0.1.15.json")) {
            assertNotNull(input); Files.copy(input, history);
        }
        String original = Files.readString(history), conversation = "70d6cf9c-d27b-4b51-89c6-599ba8a9db08";
        Path work = workspace.resolve("existing-work.txt"); Files.writeString(work, "Keep the original workspace");
        try {
            try (AgentChatService service = service()) {
                JsonObject loaded = service.handle(new JsonObject());
                assertTrue(AgentJson.bool(loaded, "usesSessionModel"));
                assertEquals(conversation, AgentJson.string(loaded, "conversationId"));
                assertTrue(loaded.get("model").isJsonNull());
                assertEquals(original, Files.readString(history), "Reading old history must not migrate it implicitly");
                assertFalse(Files.exists(workspace.resolve("fixture-frames.jsonl")));
            }
            for (int reopen = 0; reopen < 2; reopen++) {
                try (AgentChatService service = service()) {
                    assertTrue(AgentJson.bool(service.handle(new JsonObject()), "usesSessionModel"));
                    service.handle(AgentJson.object("operation", "send", "provider", "codex", "conversationId", conversation, "text", "Continue the original block " + reopen));
                    JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                    assertEquals(conversation, AgentJson.string(waiting, "conversationId"));
                    assertEquals(1, waiting.getAsJsonArray("sessions").size());
                    assertTrue(waiting.get("messages").toString().contains("Created the original blue block"));
                    String approval = AgentJson.string(waiting.getAsJsonArray("approvals").get(0).getAsJsonObject(), "id");
                    service.handle(AgentJson.object("operation", "approve", "id", approval, "choice", "choice-0"));
                    await(service, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
                }
                AgentChatStore saved = new AgentChatStore(workspace); saved.load();
                assertNull(saved.sessions.get(conversation).model);
                assertEquals("codex-fixture", saved.sessions.get(conversation).remoteId);
            }
            var frames = Files.readAllLines(workspace.resolve("fixture-frames.jsonl")).stream()
                    .map(JsonParser::parseString).map(JsonElement::getAsJsonObject).toList();
            assertEquals(2, frames.stream().filter(frame -> AgentJson.string(frame, "method").equals("thread/resume")).count());
            assertFalse(frames.stream().anyMatch(frame -> AgentJson.string(frame, "method").equals("thread/start")));
            for (JsonObject frame : frames) if (Set.of("thread/resume", "turn/start").contains(AgentJson.string(frame, "method"))) {
                JsonObject params = AgentJson.child(frame, "params");
                assertEquals("codex-fixture", AgentJson.string(params, "threadId"));
                assertFalse(params.has("model"), "Resume must not substitute the catalog or configured default model");
                if (params.has("cwd")) assertEquals(workspace.toRealPath().toString(), AgentJson.string(params, "cwd"));
            }
            assertEquals("Keep the original workspace", Files.readString(work));
            try (AgentChatService service = service()) {
                JsonObject changed = service.handle(AgentJson.object("operation", "new", "provider", "codex", "model", "chosen-model"));
                assertNotEquals(conversation, AgentJson.string(changed, "conversationId"));
                assertFalse(AgentJson.bool(changed, "usesSessionModel"));
                JsonObject empty = service.handle(AgentJson.object("operation", "new", "provider", "codex"));
                assertFalse(AgentJson.bool(empty, "usesSessionModel"), "An empty new conversation has no native model to inherit");
            }
        } finally { restore(key, old); }
    }

    @Test void attachmentsAndTurnSettingsPersistAndDeletionReclaimsOnlyUnreferencedCopies() throws Exception {
        String key = "copperbench.agent.codex", old = System.getProperty(key);
        System.setProperty(key, wrapper("codex").toString());
        String attachmentId, conversation; Path copied;
        try {
            try (AgentChatService service = service()) {
                var begin = service.handleAttachment(AgentJson.object("operation", "attachmentBegin", "name", "note.txt", "size", 4));
                attachmentId = AgentJson.required(begin, "id");
                service.handleAttachment(AgentJson.object("operation", "attachmentChunk", "id", attachmentId, "offset", 0, "data", "bm90ZQ=="));
                assertTrue(service.handleAttachment(AgentJson.object("operation", "attachmentCommit", "id", attachmentId)).get("ok").getAsBoolean());
                copied = new AgentAttachments(workspace).read(attachmentId).path();
                service.handle(AgentJson.object("operation", "send", "provider", "codex", "model", "fixture-model", "text", "Read the note",
                        "effort", "xhigh", "permission", "default", "attachments", AgentJson.array(new JsonPrimitive(attachmentId))));
                JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                conversation = AgentJson.string(waiting, "conversationId");
                assertEquals("xhigh", AgentJson.string(waiting, "effort"));
                assertEquals(1, waiting.getAsJsonArray("messages").get(0).getAsJsonObject().getAsJsonArray("attachments").size());
                service.handleAttachment(AgentJson.object("operation", "attachmentDiscard", "id", attachmentId));
                assertTrue(Files.exists(copied));
                String approval = AgentJson.string(waiting.getAsJsonArray("approvals").get(0).getAsJsonObject(), "id");
                service.handle(AgentJson.object("operation", "approve", "id", approval, "choice", "choice-0"));
                await(service, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
            }
            try (AgentChatService reopened = service()) {
                JsonObject snapshot = reopened.handle(AgentJson.object("operation", "status"));
                assertEquals("xhigh", AgentJson.string(snapshot, "effort"));
                assertEquals("default", AgentJson.string(snapshot, "permission"));
                reopened.handle(AgentJson.object("operation", "deleteConversation", "conversationId", conversation, "confirmed", true));
                assertFalse(Files.exists(copied));
            }
        } finally { restore(key, old); }
    }

    @Test void newConversationDoesNotRequireInstallationAndDoesNotLaunchAProcess() throws Exception {
        String key = "copperbench.agent.codex", old = System.getProperty(key);
        System.setProperty(key, workspace.resolve("missing-cli.exe").toString());
        try (AgentChatService service = service()) {
            JsonObject created = service.handle(AgentJson.object("operation", "new", "provider", "codex"));
            assertEquals("idle", AgentJson.string(created, "state"));
            assertFalse(created.get("conversationId").isJsonNull());
            assertFalse(Files.exists(workspace.resolve("fixture-frames.jsonl")));
            JsonObject denied = service.handle(AgentJson.object("operation", "send", "provider", "codex", "text", "Create a block"));
            assertEquals("failed", AgentJson.string(denied, "state")); assertTrue(AgentJson.string(denied, "error").contains("未找到"));
        } finally { restore(key, old); }
    }

    @Test void realProcessFramesRequireApprovalAndResumeOnlyAfterAnExplicitSend() throws Exception {
        String key = "copperbench.agent.codex", old = System.getProperty(key);
        System.setProperty(key, wrapper("codex").toString());
        String conversation;
        try {
            try (AgentChatService service = service()) {
                service.handle(AgentJson.object("operation", "send", "provider", "codex", "text", "Create a blue block"));
                JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                conversation = AgentJson.string(waiting, "conversationId");
                JsonObject approval = waiting.getAsJsonArray("approvals").get(0).getAsJsonObject();
                JsonObject rejected = service.handle(AgentJson.object("operation", "approve", "id", AgentJson.string(approval, "id"), "choice", "fabricated-allow"));
                assertEquals(1, rejected.getAsJsonArray("approvals").size());
                service.handle(AgentJson.object("operation", "approve", "id", AgentJson.string(approval, "id"), "choice", "choice-0"));
                JsonObject finished = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
                assertTrue(finished.get("messages").toString().contains("Created a block"));
                assertFalse(service.isBusy());
            }
            long framesBefore = Files.size(workspace.resolve("fixture-frames.jsonl"));
            try (AgentChatService reopened = service()) {
                assertEquals("idle", AgentJson.string(reopened.handle(new JsonObject()), "state"));
                assertEquals(framesBefore, Files.size(workspace.resolve("fixture-frames.jsonl")));
                reopened.handle(AgentJson.object("operation", "select", "conversationId", conversation));
                assertEquals(framesBefore, Files.size(workspace.resolve("fixture-frames.jsonl")));
                reopened.handle(AgentJson.object("operation", "send", "provider", "codex", "conversationId", conversation, "text", "Make it brighter"));
                await(reopened, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                assertTrue(Files.readString(workspace.resolve("fixture-frames.jsonl")).contains("thread/resume"));
                reopened.handle(AgentJson.object("operation", "stop"));
                JsonObject stopped = await(reopened, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
                assertTrue(stopped.getAsJsonArray("approvals").isEmpty());
                assertTrue(stopped.get("messages").toString().contains("已停止"));
            }
        } finally { restore(key, old); }
    }

    @Test void claudeControlIdsAreMatchedByActualPipes() throws Exception {
        String key = "copperbench.agent.claude", old = System.getProperty(key);
        System.setProperty(key, wrapper("claude").toString());
        try (AgentChatService service = service()) {
            service.handle(AgentJson.object("operation", "send", "provider", "claude", "text", "Build the mod"));
            JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
            String id = AgentJson.string(waiting.getAsJsonArray("approvals").get(0).getAsJsonObject(), "id");
            service.handle(AgentJson.object("operation", "approve", "id", id, "choice", "choice-0"));
            JsonObject done = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
            assertTrue(done.get("messages").toString().contains("Built the fixture"));
            String frames = Files.readString(workspace.resolve("fixture-frames.jsonl"));
            assertTrue(frames.contains("permission-fixture")); assertTrue(frames.contains("updatedInput"));
            assertFalse(done.has("error"));
        } finally { restore(key, old); }
    }

    @Test void grokRpcTerminalResponseSettlesAndAntigravityZeroExitDenialFails() throws Exception {
        for (String provider : List.of("grok", "antigravity")) {
            String key = "copperbench.agent." + provider, old = System.getProperty(key);
            System.setProperty(key, wrapper(provider).toString());
            try (AgentChatService service = service()) {
                service.handle(AgentJson.object("operation", "send", "provider", provider, "text", "Build"));
                JsonObject settled = await(service, snapshot -> Set.of("idle", "failed").contains(AgentJson.string(snapshot, "state")));
                assertEquals(provider.equals("grok") ? "idle" : "failed", AgentJson.string(settled, "state"), settled.toString());
                if (provider.equals("antigravity")) assertTrue(AgentJson.string(settled, "error").contains("权限"));
            } finally { restore(key, old); }
        }
    }

    @Test void deepseekPersistsTheSessionAfterApprovalAndResumesOnlyOnExplicitSend() throws Exception {
        String key = "copperbench.agent.deepseek", old = System.getProperty(key);
        System.setProperty(key, wrapper("deepseek").toString());
        try {
            String conversation;
            try (AgentChatService service = service()) {
                service.handle(AgentJson.object("operation", "send", "provider", "deepseek", "text", "Build the lantern"));
                JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                conversation = AgentJson.string(waiting, "conversationId");
                JsonObject approval = waiting.getAsJsonArray("approvals").get(0).getAsJsonObject();
                assertEquals("pwsh", AgentJson.string(approval, "title"));
                assertTrue(AgentJson.string(approval, "detail").contains("gradle build"));
                JsonObject provider = waiting.getAsJsonArray("providers").asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(item -> AgentJson.string(item, "id").equals("deepseek")).findFirst().orElseThrow();
                assertFalse(AgentJson.bool(provider, "loginAvailable"));
                service.handle(AgentJson.object("operation", "approve", "id", AgentJson.string(approval, "id"), "choice", "choice-0"));
                JsonObject done = await(service, snapshot -> Set.of("idle", "failed").contains(AgentJson.string(snapshot, "state")));
                assertEquals("idle", AgentJson.string(done, "state"), done.toString());
                assertTrue(done.get("messages").toString().contains("DeepSeek fixture completed"));
                assertTrue(done.get("messages").toString().contains("model listing from"));
                assertTrue(Files.exists(workspace.resolve("fixture-close.marker")));
                assertTrue(Files.exists(workspace.resolve("fixture-eof.marker")));
            }
            long framesBefore = Files.size(workspace.resolve("fixture-frames.jsonl"));
            try (AgentChatService reopened = service()) {
                reopened.handle(AgentJson.object("operation", "select", "conversationId", conversation));
                assertEquals(framesBefore, Files.size(workspace.resolve("fixture-frames.jsonl")));
                reopened.handle(AgentJson.object("operation", "send", "provider", "deepseek", "conversationId", conversation, "text", "Continue"));
                await(reopened, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                assertTrue(Files.readString(workspace.resolve("fixture-frames.jsonl")).contains("session/resume"));
                reopened.handle(AgentJson.object("operation", "stop"));
                JsonObject stopped = await(reopened, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
                assertTrue(stopped.getAsJsonArray("approvals").isEmpty());
                assertTrue(Files.readString(workspace.resolve("fixture-frames.jsonl")).contains("session/cancel"));
                assertFalse(reopened.isBusy());
            }
        } finally { restore(key, old); }
    }

    @Test void deepseekClosePersistenceFailureCannotBecomeASuccessfulTurn() throws Exception {
        String key = "copperbench.agent.deepseek", old = System.getProperty(key);
        System.setProperty(key, wrapper("deepseek-close-error").toString());
        try (AgentChatService service = service()) {
            service.handle(AgentJson.object("operation", "send", "provider", "deepseek", "text", "Build"));
            JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
            String id = AgentJson.string(waiting.getAsJsonArray("approvals").get(0).getAsJsonObject(), "id");
            service.handle(AgentJson.object("operation", "approve", "id", id, "choice", "choice-0"));
            JsonObject done = await(service, snapshot -> Set.of("idle", "failed").contains(AgentJson.string(snapshot, "state")));
            assertEquals("failed", AgentJson.string(done, "state"), done.toString());
            assertTrue(AgentJson.string(done, "error").contains("session persistence failed"));
        } finally { restore(key, old); }
    }

    @Test void questionsValidateSelectionsAndSendAllAnswersWithoutTreatingCustomTextAsAnId() throws Exception {
        String key = "copperbench.agent.claude", old = System.getProperty(key);
        System.setProperty(key, wrapper("claude-questions").toString());
        try (AgentChatService service = service()) {
            service.handle(AgentJson.object("operation", "send", "provider", "claude", "text", "Choose effects and a name"));
            JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval") && snapshot.getAsJsonArray("approvals").size() == 2);
            JsonObject multi = waiting.getAsJsonArray("approvals").get(0).getAsJsonObject();
            JsonObject single = waiting.getAsJsonArray("approvals").get(1).getAsJsonObject();
            String multiId = AgentJson.string(multi, "id"), singleId = AgentJson.string(single, "id");
            assertEquals("question", AgentJson.string(multi, "kind")); assertTrue(AgentJson.bool(multi, "multiSelect"));
            assertTrue(AgentJson.bool(single, "allowCustom")); assertFalse(AgentJson.bool(single, "multiSelect"));
            JsonObject invalid = service.handle(AgentJson.object("operation", "approve", "id", multiId, "choices", AgentJson.array(new JsonPrimitive("fabricated-choice"))));
            assertEquals(2, invalid.getAsJsonArray("approvals").size());
            JsonObject duplicate = service.handle(AgentJson.object("operation", "approve", "id", multiId, "choices", AgentJson.array(new JsonPrimitive("choice-0"), new JsonPrimitive("choice-0"))));
            assertEquals(2, duplicate.getAsJsonArray("approvals").size());
            service.handle(AgentJson.object("operation", "approve", "id", multiId, "choices", AgentJson.array(new JsonPrimitive("choice-0"), new JsonPrimitive("choice-1")), "answer", "Frost"));
            JsonObject mixedSingle = service.handle(AgentJson.object("operation", "approve", "id", singleId, "choices", AgentJson.array(new JsonPrimitive("choice-0")), "answer", "Custom name"));
            assertEquals(1, mixedSingle.getAsJsonArray("approvals").size());
            assertTrue(AgentJson.string(mixedSingle, "error").contains("一个回答"));
            service.handle(AgentJson.object("operation", "approve", "id", singleId, "choices", new JsonArray(), "answer", "choice-0"));
            JsonObject done = await(service, snapshot -> Set.of("idle", "failed").contains(AgentJson.string(snapshot, "state")));
            assertEquals("idle", AgentJson.string(done, "state"), done.toString());
            assertTrue(done.get("messages").toString().contains("Built the fixture"));
            List<String> replies = Files.readAllLines(workspace.resolve("fixture-frames.jsonl")).stream()
                    .filter(line -> AgentJson.string(JsonParser.parseString(line).getAsJsonObject(), "type").equals("control_response")).toList();
            assertEquals(1, replies.size(), "All question answers are returned together exactly once");
        } finally { restore(key, old); }
    }

    @Test void aClosedCliReleasesTheCloseRpcWhileRecoveredToolsPreserveTheSuccessfulTurn() throws Exception {
        for (String mode : List.of("deepseek-close-eof", "deepseek-recovered")) {
            String key = "copperbench.agent.deepseek", old = System.getProperty(key);
            System.setProperty(key, wrapper(mode).toString());
            try (AgentChatService service = service()) {
                service.handle(AgentJson.object("operation", "send", "provider", "deepseek", "text", "Build"));
                JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                String id = AgentJson.string(waiting.getAsJsonArray("approvals").get(0).getAsJsonObject(), "id");
                service.handle(AgentJson.object("operation", "approve", "id", id, "choice", "choice-0"));
                // The shared await limit is 20s: an unresolved 60s RPC would fail this regression.
                JsonObject done = await(service, snapshot -> Set.of("idle", "failed").contains(AgentJson.string(snapshot, "state")));
                if (!mode.equals("deepseek-recovered")) {
                    assertEquals("failed", AgentJson.string(done, "state"));
                    assertTrue(AgentJson.string(done, "error").contains("关闭未获确认"), done.toString());
                    assertFalse(AgentJson.string(done, "error").contains("超时"));
                } else {
                    assertEquals("idle", AgentJson.string(done, "state"), done.toString());
                    assertTrue(done.get("messages").toString().contains("First build failed"));
                    assertTrue(done.get("messages").toString().contains("BUILD SUCCESSFUL"));
                    assertFalse(done.has("error"));
                }
                assertFalse(service.isBusy());
                assertTrue(Files.exists(workspace.resolve("fixture-close.marker")));
            } finally { restore(key, old); }
        }
    }

    @Test void corruptHistoryIsPreservedAndNeverOverwrittenByANewConversation() throws Exception {
        Path history = workspace.resolve(".copperbench/agent-chat/history.json");
        Files.createDirectories(history.getParent()); Files.writeString(history, "{corrupt history");
        try (AgentChatService service = service()) {
            JsonObject snapshot = service.handle(AgentJson.object("operation", "new", "provider", "codex"));
            assertEquals("failed", AgentJson.string(snapshot, "state")); assertTrue(snapshot.getAsJsonArray("sessions").isEmpty());
        }
        assertEquals("{corrupt history", Files.readString(history));
    }

    @Test void completedAntigravityPersistentStreamIsCleanedUpBeforeBecomingIdle() throws Exception {
        String key = "copperbench.agent.antigravity", old = System.getProperty(key);
        System.setProperty(key, wrapper("antigravity-persistent").toString());
        try (AgentChatService service = service()) {
            service.handle(AgentJson.object("operation", "send", "provider", "antigravity", "text", "Check the connection"));
            JsonObject done = await(service, snapshot -> Set.of("idle", "failed").contains(AgentJson.string(snapshot, "state")));
            assertEquals("idle", AgentJson.string(done, "state"), done.toString());
            assertFalse(done.has("error"));
            assertTrue(Files.exists(workspace.resolve("fixture-eof.marker")), "The fake stream stayed open after observing stdin EOF");
            JsonObject pids = JsonParser.parseString(Files.readString(workspace.resolve("fixture-pids.json"))).getAsJsonObject();
            for (String name : List.of("parent", "child"))
                assertFalse(ProcessHandle.of(pids.get(name).getAsLong()).map(ProcessHandle::isAlive).orElse(false), "Must not report idle while the owned " + name + " is alive");
        } finally { restore(key, old); }
    }

    @Test void persistentStreamPolicyDoesNotHideNonzeroExitOrMissingTerminalResult() throws Exception {
        for (String mode : List.of("antigravity-failed-exit", "antigravity-no-result")) {
            String key = "copperbench.agent.antigravity", old = System.getProperty(key);
            System.setProperty(key, wrapper(mode).toString());
            try (AgentChatService service = service()) {
                service.handle(AgentJson.object("operation", "send", "provider", "antigravity", "text", "Check the connection"));
                JsonObject done = await(service, snapshot -> Set.of("idle", "failed").contains(AgentJson.string(snapshot, "state")));
                assertEquals("failed", AgentJson.string(done, "state"), done.toString());
                if (mode.equals("antigravity-failed-exit")) assertTrue(AgentJson.string(done, "error").contains("7"));
                else assertTrue(AgentJson.string(done, "error").contains("完成前关闭"));
            } finally { restore(key, old); }
        }
    }

    @Test void historyMasksSecretsAndRetainsRemoteSessionIdentity() throws Exception {
        AgentChatStore store = new AgentChatStore(workspace);
        AgentChatStore.Conversation session = store.create("grok");
        session.remoteId = "persisted-session";
        session.messages.add(AgentJson.object("id", "m1", "role", "assistant", "text", AgentJson.redacted("api_key=not-real-secret"), "at", 1));
        session.interrupted = true; store.save();
        AgentChatStore reopened = new AgentChatStore(workspace); reopened.load();
        assertEquals("persisted-session", reopened.sessions.get(session.id).remoteId);
        assertTrue(reopened.sessions.get(session.id).interrupted);
        assertFalse(Files.readString(workspace.resolve(".copperbench/agent-chat/history.json")).contains("not-real-secret"));
    }

    private AgentChatService service() {
        return new AgentChatService(new AgentWorkspaceContext(workspace, null, workspace.resolve("sdk/python")), new AgentDesktopActions() {
            @Override void setup(AgentProvider provider) { fail("Tests must never open external setup"); }
            @Override void login(AgentProvider provider, Path root) { fail("Tests must never launch real authentication"); }
        });
    }

    @Test void handshakeNeverCreatesOrResumesAConversationOrMutatesWorkspaceFiles() throws Exception {
        for (String id : List.of("codex", "claude", "grok", "deepseek")) {
            String key = "copperbench.agent." + id, old = System.getProperty(key);
            System.setProperty(key, wrapper(id).toString());
            Path content = workspace.resolve("user-creation.txt"); Files.writeString(content, "unchanged");
            try (AgentChatService service = service()) {
                service.handle(AgentJson.object("operation", "checkConnection", "provider", id));
                JsonObject done = await(service, snapshot -> !AgentJson.bool(snapshot, "checking"));
                JsonObject provider = done.getAsJsonArray("providers").asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(item -> AgentJson.string(item, "id").equals(id)).findFirst().orElseThrow();
                assertEquals("verified", AgentJson.string(AgentJson.child(provider, "connection"), "state"), done.toString());
                assertEquals("protocol", AgentJson.string(AgentJson.child(provider, "connection"), "scope"));
                assertTrue(done.getAsJsonArray("sessions").isEmpty());
                assertFalse(Files.exists(workspace.resolve("fixture-frames.jsonl")));
                assertEquals("unchanged", Files.readString(content));
            } finally { restore(key, old); }
        }
    }

    @Test void liveApprovalSnapshotHasLocalizedMetadataAndActiveHistoryCannotBeDeleted() throws Exception {
        String key = "copperbench.agent.codex", old = System.getProperty(key);
        System.setProperty(key, wrapper("codex").toString());
        try (AgentChatService service = service()) {
            service.handle(AgentJson.object("operation", "send", "provider", "codex", "text", "新创作"));
            JsonObject waiting = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
            JsonObject approval = waiting.getAsJsonArray("approvals").get(0).getAsJsonObject();
            assertEquals("agent.commandApproval", AgentJson.string(AgentJson.child(approval, "titleText"), "key"));
            assertTrue(AgentJson.string(AgentJson.child(approval.getAsJsonArray("choices").get(0).getAsJsonObject(), "labelText"), "en").contains("Allow"));
            assertFalse(waiting.getAsJsonArray("messages").get(0).getAsJsonObject().has("textText"), "User input stays unchanged");
            assertFalse(waiting.getAsJsonArray("sessions").get(0).getAsJsonObject().has("titleText"), "A user-derived title stays unchanged");
            Path fixture = Path.of("build/agent-runtime-ui.json"); Files.createDirectories(fixture.getParent()); Files.writeString(fixture, waiting.toString());
            JsonObject denied = service.handle(AgentJson.object("operation", "deleteConversation", "conversationId", AgentJson.string(waiting, "conversationId"), "confirmed", true));
            assertEquals(1, denied.getAsJsonArray("sessions").size());
            assertEquals("agent.activeDelete", AgentJson.string(AgentJson.child(denied, "errorText"), "key"));
            service.handle(AgentJson.object("operation", "stop"));
            JsonObject stopped = await(service, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
            assertTrue(stopped.getAsJsonArray("messages").asList().stream().map(JsonElement::getAsJsonObject)
                    .anyMatch(message -> AgentJson.string(AgentJson.child(message, "textText"), "key").equals("agent.stopped")));
        } finally { restore(key, old); }
    }

    @Test void discoveredModelsDoNotSendPromptsAndTheSelectedModelSurvivesReopen() throws Exception {
        String key = "copperbench.agent.codex", old = System.getProperty(key);
        System.setProperty(key, wrapper("codex").toString());
        String session;
        try {
            try (AgentChatService service = service()) {
                service.handle(AgentJson.object("operation", "models", "provider", "codex"));
                JsonObject done = await(service, snapshot -> snapshot.getAsJsonArray("providers").asList().stream()
                        .map(JsonElement::getAsJsonObject).anyMatch(provider -> AgentJson.string(AgentJson.child(provider, "modelCatalog"), "state").equals("ready")));
                JsonObject catalog = AgentJson.child(done.getAsJsonArray("providers").get(0).getAsJsonObject(), "modelCatalog");
                assertEquals(2, catalog.getAsJsonArray("models").size());
                assertEquals("fixture-model-a", AgentJson.string(catalog, "defaultModel"));
                assertTrue(done.getAsJsonArray("sessions").isEmpty());
                assertTrue(done.getAsJsonArray("messages").isEmpty());
                assertFalse(Files.exists(workspace.resolve("fixture-frames.jsonl")));
                JsonObject created = service.handle(AgentJson.object("operation", "new", "provider", "codex", "model", "fixture-model-b"));
                session = AgentJson.string(created, "conversationId");
                JsonObject rejected = service.handle(AgentJson.object("operation", "send", "provider", "codex", "conversationId", session, "model", "fixture-model-a", "text", "keep my draft"));
                assertEquals("agent.modelFixed", AgentJson.string(AgentJson.child(rejected, "errorText"), "key"));
                assertTrue(rejected.getAsJsonArray("messages").isEmpty());
            }
            try (AgentChatService service = service()) {
                assertEquals("fixture-model-b", AgentJson.string(service.handle(new JsonObject()), "model"));
                service.handle(AgentJson.object("operation", "send", "provider", "codex", "conversationId", session, "model", "fixture-model-b", "text", "create"));
                await(service, snapshot -> AgentJson.string(snapshot, "state").equals("waiting_approval"));
                var frames = Files.readAllLines(workspace.resolve("fixture-frames.jsonl")).stream().map(JsonParser::parseString).map(JsonElement::getAsJsonObject).toList();
                for (String method : List.of("thread/start", "turn/start")) {
                    JsonObject frame = frames.stream().filter(row -> AgentJson.string(row, "method").equals(method)).findFirst().orElseThrow();
                    assertEquals("fixture-model-b", AgentJson.string(AgentJson.child(frame, "params"), "model"));
                }
                service.handle(AgentJson.object("operation", "stop"));
                await(service, snapshot -> AgentJson.string(snapshot, "state").equals("idle"));
            }
        } finally { restore(key, old); }
    }

    private Path wrapper(String mode) throws Exception {
        String classpath = fixtureClasspath();
        Path file = workspace.resolve("fake-" + mode + (AgentProcess.windows() ? ".ps1" : ".sh"));
        String content = AgentProcess.windows()
                ? "& '" + FakeAgentCli.java().replace("'", "''") + "' '-cp' '" + classpath.replace("'", "''") + "' '" + FakeAgentCli.class.getName() + "' '" + mode + "' @args\nexit $LASTEXITCODE\n"
                : "#!/bin/sh\nexec " + quote(FakeAgentCli.java()) + " -cp " + quote(classpath) + " " + FakeAgentCli.class.getName() + " " + mode + " \"$@\"\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);
        if (!AgentProcess.windows()) assertTrue(file.toFile().setExecutable(true));
        return file;
    }

    static String fixtureClasspath() throws Exception {
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> type : List.of(FakeAgentCli.class, AgentProcess.class, Gson.class))
            entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        return String.join(java.io.File.pathSeparator, entries);
    }
    private static String quote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }
    private static void restore(String key, String value) { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }

    static JsonObject await(AgentChatService service, Predicate<JsonObject> predicate) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos(); JsonObject latest;
        do {
            latest = service.handle(AgentJson.object("operation", "status"));
            if (predicate.test(latest)) return latest;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail("CLI fixture did not reach expected state: " + latest); return latest;
    }
}
