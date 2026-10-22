package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentHistoryManagementTest {
    @TempDir Path root;

    @Test void deletingAtTheSessionLimitPersistsAndAllowsANewConversationAfterReopen() throws Exception {
        AgentChatStore store = new AgentChatStore(root);
        for (int i = 0; i < 90; i++) store.create("claude");
        assertEquals("near", AgentJson.string(store.capacity(), "level"));
        for (int i = 90; i < 100; i++) store.create("claude");
        var keep = store.sessions.values().iterator().next(); keep.remoteId = "native-keep";
        store.save();
        String remove = store.selected;
        assertThrows(IllegalStateException.class, () -> store.create("codex"));
        assertEquals("full", AgentJson.string(store.capacity(), "level"));
        store.delete(remove);
        AgentChatStore reopened = new AgentChatStore(root); reopened.load();
        assertFalse(reopened.sessions.containsKey(remove));
        assertEquals("native-keep", reopened.sessions.get(keep.id).remoteId);
        reopened.create("codex"); reopened.save();
        assertEquals(100, reopened.sessions.size());
    }

    @Test void byteBudgetFailureRetainsOldFileAndPendingMessagesUntilBatchCleanupSucceeds() throws Exception {
        AgentChatStore store = new AgentChatStore(root);
        var keep = store.create("codex"); keep.remoteId = "native-keep";
        store.save();
        Path history = root.resolve(".copperbench/agent-chat/history.json");
        String before = Files.readString(history);
        List<String> remove = new ArrayList<>();
        for (int session = 0; session < 3; session++) {
            var large = store.create("grok"); remove.add(large.id);
            for (int i = 0; i < 270; i++) large.messages.add(AgentJson.object("id", "m" + i, "role", "assistant", "text", "x".repeat(32700), "at", 1));
        }
        assertThrows(java.io.IOException.class, store::save);
        assertEquals(before, Files.readString(history));
        assertEquals(270, store.sessions.get(remove.getFirst()).messages.size());
        assertThrows(java.io.IOException.class, () -> store.delete(remove.getFirst()));
        assertTrue(store.sessions.containsKey(remove.getFirst()), "A failed cleanup must roll back the deletion");
        store.delete(remove);
        store.create("claude"); store.save();
        AgentChatStore reopened = new AgentChatStore(root); reopened.load();
        assertEquals(2, reopened.sessions.size());
        assertEquals("native-keep", reopened.sessions.get(keep.id).remoteId);
        assertEquals("ok", AgentJson.string(reopened.capacity(), "level"));
    }

    @Test void oversizedFileIsBackedUpByteForByteBeforeExplicitRecovery() throws Exception {
        Path file = root.resolve(".copperbench/agent-chat/history.json"); Files.createDirectories(file.getParent());
        byte[] original = new byte[AgentChatStore.MAX_FILE_BYTES + 1]; Arrays.fill(original, (byte) 'x'); Files.write(file, original);
        try (AgentChatService service = new AgentChatService(root)) {
            var initial = service.handle(AgentJson.object("operation", "status"));
            assertEquals("unreadable", AgentJson.string(AgentJson.child(initial, "history"), "level"));
            service.handle(AgentJson.object("operation", "recoverHistory", "confirmed", false));
            assertArrayEquals(original, Files.readAllBytes(file));
            var restored = service.handle(AgentJson.object("operation", "recoverHistory", "confirmed", true));
            Path backup = Path.of(AgentJson.string(AgentJson.child(restored, "historyResult"), "path"));
            assertArrayEquals(original, Files.readAllBytes(backup));
            assertFalse(AgentJson.bool(restored, "storageBlocked"));
            assertEquals("idle", AgentJson.string(restored, "state"));
            var next = service.handle(AgentJson.object("operation", "new", "provider", "codex"));
            assertEquals(1, next.getAsJsonArray("sessions").size());
        }
        AgentChatStore reopened = new AgentChatStore(root); reopened.load(); assertEquals(1, reopened.sessions.size());
    }

    @Test void exportAllowlistsFieldsAndDeletionOnlyAffectsTheSelectedCopperbenchRecord() throws Exception {
        AgentChatStore store = new AgentChatStore(root);
        var session = store.create("claude"); session.remoteId = "native-private-resume-id";
        session.messages.add(AgentJson.object("id", "m1", "role", "user", "text", "api_key=example-secret hello", "at", 1, "token", "never-export"));
        Path creation = root.resolve("creation.txt"); Files.writeString(creation, "preserved");
        store.save(); String original = Files.readString(root.resolve(".copperbench/agent-chat/history.json"));
        String exported = Files.readString(store.export(session.id));
        assertFalse(exported.contains("native-private")); assertFalse(exported.contains("never-export")); assertFalse(exported.contains("example-secret"));
        assertTrue(exported.contains("hello"));
        assertEquals(original, Files.readString(root.resolve(".copperbench/agent-chat/history.json")));
        store.delete(session.id); assertEquals("preserved", Files.readString(creation));
    }

    @Test void structuredCopyKeepsStableKeysAndUntouchedFreeText() {
        JsonObject error = new JsonObject(); AgentCopy.put(error, "error", "未找到 Claude Code，请先安装并点击检查连接");
        assertEquals("agent.missingCli", AgentJson.string(AgentJson.child(error, "errorText"), "key"));
        assertEquals("Claude Code", AgentJson.string(AgentJson.child(AgentJson.child(error, "errorText"), "args"), "provider"));
        AgentCopy.put(error, "error", "A raw diagnostic says 新创作");
        assertFalse(error.has("errorText"));
    }

    @Test void committedDeletionStillReportsSuccessWhenAnAttachmentCannotBeCleanedUp() throws Exception {
        AgentAttachments.Item file;
        try (AgentAttachments attachments = new AgentAttachments(root)) {
            file = AgentAttachmentsTest.add(attachments, "locked.txt", "content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        AgentChatStore store = new AgentChatStore(root);
        var session = store.create("codex");
        session.messages.add(AgentJson.object("id", "sent", "role", "user", "text", "file", "at", 1,
                "attachments", AgentJson.array(file.json()), "delivery", "sent"));
        store.save();
        Files.delete(file.path()); Files.createDirectory(file.path());
        try (AgentChatService service = new AgentChatService(root)) {
            JsonObject result = service.handle(AgentJson.object("operation", "deleteConversation", "conversationId", session.id, "confirmed", true));
            assertEquals("deleteConversation", AgentJson.string(AgentJson.child(result, "historyResult"), "operation"));
            assertTrue(result.getAsJsonArray("sessions").isEmpty());
            assertEquals("agent.deletedAttachmentCleanup", AgentJson.string(AgentJson.child(result, "errorText"), "key"));
            assertTrue(Files.exists(file.path()), "Failed cleanup must not remove unexpected content");
        }
        AgentChatStore reopened = new AgentChatStore(root); reopened.load(); assertTrue(reopened.sessions.isEmpty());
    }

    @Test void retryHistoryReportsItsOwnSuccessAndDoesNotReuseItAfterAWriteFailure() throws Exception {
        try (AgentChatService service = new AgentChatService(root)) {
            service.handle(AgentJson.object("operation", "new", "provider", "codex"));
            JsonObject saved = service.handle(AgentJson.object("operation", "retryHistory"));
            assertEquals("retryHistory", AgentJson.string(AgentJson.child(saved, "historyResult"), "operation"));
            assertFalse(AgentJson.bool(saved, "storageBlocked"));
            Path history = root.resolve(".copperbench/agent-chat/history.json");
            Files.delete(history); Files.createDirectory(history); Files.writeString(history.resolve("occupied"), "fixture");
            JsonObject failed = service.handle(AgentJson.object("operation", "retryHistory"));
            assertTrue(failed.has("error"));
            assertFalse(failed.has("historyResult"), "A failed retry must not reuse the previous success receipt");
            Files.delete(history.resolve("occupied")); Files.delete(history);
            JsonObject recovered = service.handle(AgentJson.object("operation", "retryHistory"));
            assertEquals("retryHistory", AgentJson.string(AgentJson.child(recovered, "historyResult"), "operation"));
            assertTrue(Files.isRegularFile(history));
        }
        AgentChatStore reopened = new AgentChatStore(root); reopened.load(); assertEquals(1, reopened.sessions.size());
    }

    @Test void legacyTextAndInvalidOptionalTranslationStayReadableWithoutRewritingTheFile() throws Exception {
        AgentChatStore store = new AgentChatStore(root);
        var session = store.create("codex");
        session.messages.add(AgentJson.object("id", "legacy", "at", 1, "role", "system", "text", "历史自由文本", "textText", "unsupported-old-metadata"));
        store.save(); Path file = root.resolve(".copperbench/agent-chat/history.json"); String original = Files.readString(file);
        AgentChatStore reopened = new AgentChatStore(root); reopened.load();
        JsonObject message = reopened.sessions.get(session.id).messages.get(0).getAsJsonObject();
        assertEquals("历史自由文本", AgentJson.string(message, "text")); assertFalse(message.has("textText"));
        assertEquals(original, Files.readString(file));
    }
}
