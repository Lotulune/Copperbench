package dev.copperbench.generator;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedArtifactExporterTest {
    @TempDir Path root;

    @Test void exportsSelectedBytesAndRejectsReplacedEvidenceAndUnapprovedHistoricalInput() throws Exception {
        Path jar = root.resolve(".copperbench/accepted/mod.jar"), report = root.resolve(".copperbench/accepted/report.xml");
        Files.createDirectories(jar.getParent()); Files.writeString(jar, "tested-jar"); Files.writeString(report, "<testsuite/>");
        Path newest = root.resolve("build/libs/newer.jar"); Files.createDirectories(newest.getParent()); Files.writeString(newest, "untested");
        JsonObject verification = new JsonObject(); verification.addProperty("status", "passed");
        verification.addProperty("artifactPath", jar.toString()); verification.addProperty("artifactSha256", WorkspaceExecutionSnapshot.sha256(jar));
        verification.addProperty("reportPath", report.toString()); verification.addProperty("reportSha256", WorkspaceExecutionSnapshot.sha256(report));
        JsonObject snapshot = new JsonObject(); snapshot.addProperty("sha256", WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        verification.add("sourceSnapshot", snapshot);
        JsonObject task = new JsonObject(); task.addProperty("id", UUID.randomUUID().toString()); task.addProperty("state", "succeeded"); task.add("verification", verification);
        JsonObject exported = VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), false);
        Path destination = Path.of(exported.get("exportDirectory").getAsString());
        assertEquals("tested-jar", Files.readString(destination.resolve("verified-mod.jar")));
        assertFalse(Files.readString(destination.resolve("verification.json")).contains(root.toString().replace("\\", "\\\\")));
        Files.writeString(root.resolve("source.java"), "new input");
        assertThrows(java.io.IOException.class, () -> VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), false));
        assertEquals("passed_historical_input", VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), true).get("status").getAsString());
        Files.writeString(jar, "replaced");
        assertThrows(java.io.IOException.class, () -> VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), true));
    }

    @Test void restoredRunningTaskIsUnconfirmedAndCannotAcquireCancellationAuthority() throws Exception {
        UUID workspace = UUID.randomUUID(), id = UUID.randomUUID();
        JsonObject task = new JsonObject(); task.addProperty("id", id.toString()); task.addProperty("state", "running");
        JsonObject log = new JsonObject(); log.addProperty("sequence", 1); log.addProperty("text", "Authorization=secret-token");
        WorkspaceTaskRecords.save(root, workspace, task, List.of(log), List.of());
        var restored = WorkspaceTaskRecords.read(root, workspace, id).orElseThrow();
        assertEquals("unconfirmed_after_session_loss", restored.getAsJsonObject("task").get("resultObservation").getAsString());
        assertFalse(restored.toString().contains("secret-token"));
        assertTrue(WorkspaceTaskRecords.read(root, UUID.randomUUID(), id).isEmpty());
        assertEquals(id.toString(), WorkspaceTaskRecords.recent(root, workspace).getFirst().get("id").getAsString());
        assertTrue(WorkspaceTaskRecords.recent(root, UUID.randomUUID()).isEmpty());
        task.addProperty("state", "succeeded"); WorkspaceTaskRecords.save(root, workspace, task, List.of(), List.of());
        assertEquals("succeeded", WorkspaceTaskRecords.read(root, workspace, id).orElseThrow().getAsJsonObject("task").get("state").getAsString());
    }

    @Test void recoveryRetainsDurableOutputAfterTheLastSnapshotAndIgnoresAnInterruptedFinalAppend() throws Exception {
        UUID workspace = UUID.randomUUID(), id = UUID.randomUUID();
        JsonObject task = new JsonObject(); task.addProperty("id", id.toString()); task.addProperty("state", "running");
        WorkspaceTaskRecords.save(root, workspace, task, List.of(), List.of());
        JsonObject line = new JsonObject(); line.addProperty("sequence", 1);
        line.addProperty("text", "last observed checkpoint; Authorization: Basic fixture-credential");
        WorkspaceTaskRecords.appendLog(root, id, line);
        line.addProperty("sequence", 2); line.addProperty("text", "password='multi word fixture secret'");
        WorkspaceTaskRecords.appendLog(root, id, line);
        Path journal = root.resolve(".copperbench/task-records/" + id + ".logs.jsonl");
        Files.writeString(journal, "{\"sequence\":3,\"text\":", StandardOpenOption.APPEND);
        var restored = WorkspaceTaskRecords.read(root, workspace, id).orElseThrow();
        assertEquals(2, restored.getAsJsonArray("logs").size());
        assertTrue(restored.toString().contains("last observed checkpoint"));
        assertFalse(Files.readString(journal).contains("fixture-credential"));
        assertFalse(Files.readString(journal).contains("multi word fixture secret"));
        assertEquals("unconfirmed_after_session_loss", restored.getAsJsonObject("task").get("resultObservation").getAsString());
    }
}
