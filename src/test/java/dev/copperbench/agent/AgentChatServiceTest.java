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
