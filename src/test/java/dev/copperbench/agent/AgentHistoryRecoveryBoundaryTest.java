package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AgentHistoryRecoveryBoundaryTest {
    @TempDir Path root;
    private record Saved(String conversation, String run, Path history, Path record) { }

    private Saved saved(Path directory, boolean finished) throws Exception {
        Files.createDirectories(directory);
        AgentChatStore store = new AgentChatStore(directory);
        var session = store.create("codex"); store.save();
        JsonObject run;
        try (var journal = new AgentRunJournal(directory)) {
            journal.acquire();
            run = journal.begin(UUID.randomUUID().toString(), session.id,
                    new AgentProvider("codex", "Codex CLI", null, "fixture", "https://example.invalid", null),
                    AgentJson.object("task", "Fixture", "attachments", new JsonArray()));
            if (finished) journal.finish(run, "completed", AgentJson.object("cleanup", "confirmed"), null,
                    AgentJson.object("coverage", "complete", "files", new JsonArray()));
        }
        String id = AgentJson.string(run, "runId");
        return new Saved(session.id, id, directory.resolve(".copperbench/agent-chat/history.json"),
                directory.resolve(".copperbench/agent-chat/runs/" + id + ".json"));
    }

    @Test void failedRunDeletionKeepsTheConversationAvailableForRetry() throws Exception {
        Saved saved = saved(root, true);
        byte[] record = Files.readAllBytes(saved.record());
        try (var service = new AgentChatService(root)) {
            Files.delete(saved.record()); Files.createDirectory(saved.record());
            Path obstruction = Files.writeString(saved.record().resolve("held"), "keep");
            JsonObject request = AgentJson.object("operation", "deleteConversation", "conversationId", saved.conversation(), "confirmed", true);
            JsonObject failed = service.handle(request);
            assertTrue(failed.has("error"));
            assertEquals(1, AgentJson.list(failed, "sessions").size());
            assertTrue(Files.readString(saved.history()).contains(saved.conversation()));
            Files.delete(obstruction); Files.delete(saved.record()); Files.write(saved.record(), record);
            JsonObject retried = service.handle(request);
            assertFalse(retried.has("error"), retried.toString());
            assertEquals(0, AgentJson.list(retried, "sessions").size());
            assertEquals(0, retried.getAsJsonObject("runHistoryCapacity").get("count").getAsInt());
        }
    }

    @Test void damagedChatHistoryArchivesFinishedRunsAndReleasesTheirCapacity() throws Exception {
        Saved saved = saved(root, true);
        byte[] record = Files.readAllBytes(saved.record());
        Path baseline = saved.record().resolveSibling(saved.run() + ".baseline.json");
        Files.writeString(baseline, "{\"baseline\":true}"); Files.writeString(saved.history(), "damaged conversation history");
        try (var service = new AgentChatService(root)) {
            JsonObject recovered = recover(service);
            assertFalse(recovered.has("error"), recovered.toString());
            assertFalse(AgentJson.bool(recovered, "writeBlocked"));
            Path archive = Path.of(AgentJson.string(AgentJson.child(recovered, "historyResult"), "archivePath"));
            assertEquals("damaged conversation history", Files.readString(archive.resolve("history.json")));
            assertArrayEquals(record, Files.readAllBytes(archive.resolve("runs").resolve(saved.record().getFileName())));
            assertTrue(Files.exists(archive.resolve("runs").resolve(baseline.getFileName())));
            assertEquals(0, recovered.getAsJsonObject("runHistoryCapacity").get("count").getAsInt());
            assertFalse(service.handle(AgentJson.object("operation", "new", "provider", "codex")).has("error"));
        }
    }

    @Test void movedProjectCanArchiveFinishedHistoryAndStartWithANewIdentity() throws Exception {
        Saved original = saved(root.resolve("original"), true);
        Path copy = Files.createDirectories(root.resolve("copy"));
        Path history = copy.resolve(".copperbench/agent-chat/history.json"), record = copy.resolve(".copperbench/agent-chat/runs/" + original.run() + ".json");
        Files.createDirectories(record.getParent()); Files.copy(original.history(), history); Files.copy(original.record(), record);
        try (var service = new AgentChatService(copy)) {
            assertEquals("unreadable", AgentJson.string(AgentJson.child(service.handle(new JsonObject()), "history"), "level"));
            JsonObject recovered = recover(service);
            assertFalse(recovered.has("error"), recovered.toString());
            assertFalse(AgentJson.bool(recovered, "writeBlocked"));
            assertTrue(Files.exists(original.record()));
            assertFalse(service.handle(AgentJson.object("operation", "new", "provider", "codex")).has("error"));
        }
    }

    @Test void recoveryCannotDiscardAnUnconfirmedExecution() throws Exception {
        Saved saved = saved(root, false); Files.writeString(saved.history(), "damaged");
        try (var service = new AgentChatService(root)) {
            byte[] record = Files.readAllBytes(saved.record());
            JsonObject rejected = recover(service);
            assertTrue(rejected.has("error")); assertTrue(AgentJson.bool(rejected, "writeBlocked"));
            assertArrayEquals(record, Files.readAllBytes(saved.record()));
            assertEquals("damaged", Files.readString(saved.history()));
        }
    }

    @Test void unreadableRunCleanupFailsClosedWithoutPretendingRecoverySucceeded() throws Exception {
        Saved saved = saved(root, true); Files.writeString(saved.record(), "damaged run record");
        try (var service = new AgentChatService(root)) {
            JsonObject rejected = recover(service);
            assertTrue(rejected.has("error")); assertTrue(AgentJson.bool(rejected, "writeBlocked"));
            assertTrue(AgentJson.string(rejected, "error").contains("cleanup could not be verified"));
            assertEquals("damaged run record", Files.readString(saved.record()));
            assertTrue(Files.exists(saved.history()));
        }
    }

    @Test void anotherExecutionOwnerPreventsHistoryRecovery() throws Exception {
        Saved saved = saved(root, true); Files.writeString(saved.history(), "damaged");
        try (var service = new AgentChatService(root);
             var channel = FileChannel.open(saved.record().resolveSibling("active.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            JsonObject rejected = recover(service);
            assertTrue(rejected.has("error")); assertEquals("damaged", Files.readString(saved.history()));
            assertTrue(Files.exists(saved.record()));
        }
    }

    private JsonObject recover(AgentChatService service) {
        return service.handle(AgentJson.object("operation", "recoverHistory", "confirmed", true));
    }

    @Test void unknownVerificationCleanupCannotBeDeletedOrRecovered() throws Exception {
        JsonElement[] invalid = {JsonNull.INSTANCE, AgentJson.object("status", "failed", "cleanup", "pending"),
                AgentJson.object("status", "failed"), AgentJson.object("status", "passed", "cleanup", "unknown"),
                AgentJson.object("status", "not_run", "executionTaskId", "unconfirmed-task")};
        for (int i = 0; i < invalid.length; i++) {
            Path workspace = root.resolve("case-" + i); Saved saved = saved(workspace, true);
            JsonObject record = JsonParser.parseString(Files.readString(saved.record())).getAsJsonObject();
            if (invalid[i].isJsonNull()) record.remove("verification"); else record.add("verification", invalid[i]);
            Files.writeString(saved.record(), record.toString());
            try (var service = new AgentChatService(workspace)) {
                JsonObject rejected = service.handle(AgentJson.object("operation", "deleteConversation", "conversationId", saved.conversation(), "confirmed", true));
                assertTrue(rejected.has("error")); assertTrue(AgentJson.bool(rejected, "writeBlocked"));
                assertTrue(Files.exists(saved.record())); assertTrue(Files.exists(saved.history()));
            }
            Files.writeString(saved.history(), "damaged");
            try (var service = new AgentChatService(workspace)) {
                assertTrue(recover(service).has("error")); assertTrue(Files.exists(saved.record()));
                assertEquals("damaged", Files.readString(saved.history()));
            }
        }
    }

    @Test void anotherExecutionOwnerPreventsDeletingCachedFinishedRuns() throws Exception {
        Saved saved = saved(root, true);
        try (var service = new AgentChatService(root)) {
            try (var channel = FileChannel.open(saved.record().resolveSibling("active.lock"), StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                JsonObject rejected = service.handle(AgentJson.object("operation", "deleteConversation", "conversationId", saved.conversation(), "confirmed", true));
                assertTrue(rejected.has("error")); assertEquals(1, AgentJson.list(rejected, "sessions").size());
                assertTrue(Files.exists(saved.record()));
            }
            assertFalse(service.handle(AgentJson.object("operation", "deleteConversation", "conversationId", saved.conversation(), "confirmed", true)).has("error"));
        }
    }

    @Test void deletionRechecksVerificationStateOnDiskUnderTheExecutionLock() throws Exception {
        Saved saved = saved(root, true);
        try (var service = new AgentChatService(root)) {
            JsonObject changed = JsonParser.parseString(Files.readString(saved.record())).getAsJsonObject();
            changed.add("verification", AgentJson.object("status", "failed", "cleanup", "pending"));
            Files.writeString(saved.record(), changed.toString());
            JsonObject rejected = service.handle(AgentJson.object("operation", "deleteConversation", "conversationId", saved.conversation(), "confirmed", true));
            assertTrue(rejected.has("error")); assertEquals(1, AgentJson.list(rejected, "sessions").size());
            assertEquals(changed.toString(), Files.readString(saved.record()));
        }
    }

    @Test void acquiringAfterAnotherOwnerDisappearsRechecksUnfinishedDiskRecords() throws Exception {
        Files.createDirectories(root.resolve(".copperbench/agent-chat"));
        try (var owner = new AgentRunJournal(root)) {
            owner.acquire();
            owner.begin(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    new AgentProvider("codex", "Codex CLI", null, "fixture", "https://example.invalid", null),
                    AgentJson.object("task", "Still preparing", "attachments", new JsonArray()));
            try (var observer = new AgentRunJournal(root)) {
                owner.close();
                assertThrows(java.io.IOException.class, observer::acquire);
            }
        }
    }
}
