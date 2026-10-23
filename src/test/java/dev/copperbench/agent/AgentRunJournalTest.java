package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentRunJournalTest {
    @TempDir Path root;
    private JsonObject begin(AgentRunJournal journal) throws Exception {
        journal.acquire();
        return journal.begin(UUID.randomUUID().toString(), "conversation", new AgentProvider("codex", "Codex CLI", null, "fixture", "", null), new JsonObject());
    }
    @Test void exactWorkingTreeBaselinePreservesExistingEditsAndHistoryDoesNotFollowLaterEdits() throws Exception {
        Path file = root.resolve("existing.txt"); Files.writeString(file, "original user edit\n");
        Files.writeString(root.resolve("untracked.txt"), "keep me"); String id;
        try (AgentRunJournal journal = new AgentRunJournal(root)) {
            JsonObject run = begin(journal); id = AgentJson.string(run, "runId"); journal.baseline(run);
            Files.writeString(file, "original user edit\nagent addition\n");
            journal.finish(run, "completed", AgentJson.object("cleanup", "confirmed"), null);
            JsonObject changed = AgentJson.list(AgentJson.child(run, "changes"), "files").get(0).getAsJsonObject();
            assertEquals("original user edit\n", AgentJson.string(AgentJson.child(changed, "before"), "text"));
            assertEquals("original user edit\nagent addition\n", AgentJson.string(AgentJson.child(changed, "after"), "text"));
            assertEquals("mixed_or_unknown", AgentJson.string(AgentJson.child(run, "changes"), "attribution"));
            Files.writeString(file, "later user edit\n");
        }
        try (AgentRunJournal reopened = new AgentRunJournal(root)) {
            assertTrue(reopened.runs.get(id).get("changes").toString().contains("agent addition"));
            assertFalse(reopened.runs.get(id).get("changes").toString().contains("later user edit"));
            assertEquals("not_run", AgentJson.string(AgentJson.child(reopened.runs.get(id), "verification"), "status"));
        }
        assertEquals("keep me", Files.readString(root.resolve("untracked.txt")));
        assertEquals("later user edit\n", Files.readString(file));
    }
    @Test void binaryOversizeSecretsAndExcludedDirectoriesHaveExplicitCoverage() throws Exception {
        Files.write(root.resolve("image.bin"), new byte[]{0, 1, 2});
        Files.writeString(root.resolve("large.txt"), "x".repeat(AgentFileSnapshot.MAX_TEXT_BYTES + 1));
        Files.writeString(root.resolve(".env.local"), "API_KEY=private-value");
        Files.writeString(root.resolve("config.txt"), "api_key=private-value\nnormal=value");
        Files.createDirectory(root.resolve("build")); Files.writeString(root.resolve("build/generated.txt"), "ignore");
        JsonObject snapshot = AgentFileSnapshot.capture(root);
        assertEquals("partial", AgentJson.string(snapshot, "coverage"));
        assertFalse(snapshot.toString().contains("private-value"));
        assertFalse(snapshot.toString().contains("generated.txt"));
        assertEquals("binary", AgentJson.string(AgentJson.child(AgentJson.child(snapshot, "files"), "image.bin"), "omitted"));
        assertTrue(AgentJson.child(AgentJson.child(snapshot, "files"), "image.bin").has("hash"));
    }
    @Test void separateWindowsCannotWriteTheSameCanonicalDirectory() throws Exception {
        try (AgentRunJournal a = new AgentRunJournal(root); AgentRunJournal b = new AgentRunJournal(root.resolve("."))) {
            a.acquire(); assertThrows(java.io.IOException.class, b::acquire); a.release(); assertDoesNotThrow(b::acquire);
        }
    }
    @Test void restartDoesNotResumeOrDeclareUnconfirmedResourcesStopped() throws Exception {
        String id;
        try (AgentRunJournal journal = new AgentRunJournal(root)) { id = AgentJson.string(begin(journal), "runId"); }
        try (AgentRunJournal reopened = new AgentRunJournal(root)) {
            assertEquals("interrupted", AgentJson.string(reopened.runs.get(id), "state"));
            assertTrue(reopened.blocked()); assertThrows(java.io.IOException.class, reopened::acquire);
        }
    }
    @Test void structuredRedactionKeepsNestedJsonParseable() {
        JsonObject evidence = AgentJson.object("authorization", "Bearer some-secret", "log", "{\"api_key\":\"private-value\"}");
        JsonElement safe = AgentJson.redactedTree(evidence);
        assertFalse(safe.toString().contains("private-value")); assertFalse(safe.toString().contains("some-secret"));
        assertEquals(safe, JsonParser.parseString(safe.toString()));
    }
    @Test void unknownRecordSchemaIsPreservedInsteadOfRecoveredOrOverwritten() throws Exception {
        Path file;
        try (AgentRunJournal journal = new AgentRunJournal(root)) {
            JsonObject run = begin(journal); journal.finish(run, "completed", AgentJson.object("cleanup", "confirmed"), null);
            run.addProperty("schemaVersion", 99); journal.save(run);
            file = root.resolve(".copperbench/agent-chat/runs/" + AgentJson.string(run, "runId") + ".json");
        }
        String original = Files.readString(file);
        assertThrows(java.io.IOException.class, () -> new AgentRunJournal(root));
        assertEquals(original, Files.readString(file));
    }
}
