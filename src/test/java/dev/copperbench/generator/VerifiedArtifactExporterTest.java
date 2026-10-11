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
        Files.createDirectories(jar.getParent()); Files.writeString(jar, "tested-jar"); Files.writeString(report, "<testsuite><testcase name=\"recipe-output\"/></testsuite>");
        Path newest = root.resolve("build/libs/newer.jar"); Files.createDirectories(newest.getParent()); Files.writeString(newest, "untested");
        JsonObject verification = GameTestReport.read(report, java.time.Instant.now().minusSeconds(1), 1);
        verification.addProperty("mode", "packaged_jar"); verification.addProperty("minimumTests", 1);
        verification.addProperty("startedAt", java.time.Instant.now().minusSeconds(1).toString()); verification.addProperty("processExitCode", 0);
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

    @Test void refusesHashConsistentButInvalidOrInsufficientAcceptanceEvenForHistoricalExport() throws Exception {
        for (String xml : List.of("<testsuite/>",
                "<testsuite><testcase name=\"ignored\"><skipped/></testcase></testsuite>",
                "<testsuite><testcase name=\"minecraft:always_pass\"/></testsuite>",
                "<testsuite><testcase name=\"business\"><failure message=\"bad\"/></testcase></testsuite>",
                "<broken")) {
            JsonObject task = evidence(xml, 1);
            // Simulate an incorrectly restored record that claims success and has matching file hashes.
            task.getAsJsonObject("verification").addProperty("status", "passed");
            for (boolean historical : List.of(false, true)) {
                var failure = assertThrows(VerifiedArtifactExporter.VerificationException.class,
                        () -> VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), historical), xml);
                assertEquals("VERIFIED_ACCEPTANCE_INVALID", failure.code(), xml);
            }
        }
        JsonObject insufficient = evidence("<testsuite><testcase name=\"business\"/></testsuite>", 2);
        insufficient.getAsJsonObject("verification").addProperty("status", "passed");
        assertEquals("VERIFIED_ACCEPTANCE_INVALID", assertThrows(VerifiedArtifactExporter.VerificationException.class,
                () -> VerifiedArtifactExporter.export(root, insufficient, UUID.randomUUID(), true)).code());
        assertFalse(Files.exists(root.resolve(".copperbench/verified-exports")));
    }

    @Test void rejectsReportTamperingAndRecordedCountDriftInsteadOfTrustingPassedLabel() throws Exception {
        JsonObject original = evidence("<testsuite><testcase name=\"business\"/></testsuite>", 1);
        for (String key : List.of("passed", "executed", "acceptanceExecuted", "skipped")) {
            JsonObject altered = original.deepCopy(); altered.getAsJsonObject("verification").addProperty(key, 99);
            assertEquals("VERIFIED_ACCEPTANCE_INVALID", assertThrows(VerifiedArtifactExporter.VerificationException.class,
                    () -> VerifiedArtifactExporter.export(root, altered, UUID.randomUUID(), false)).code(), key);
        }
        for (String key : List.of("minimumTests", "startedAt", "mode", "processExitCode", "cases", "sourceSnapshot")) {
            JsonObject incomplete = original.deepCopy(); incomplete.getAsJsonObject("verification").remove(key);
            assertEquals("VERIFIED_ACCEPTANCE_INVALID", assertThrows(VerifiedArtifactExporter.VerificationException.class,
                    () -> VerifiedArtifactExporter.export(root, incomplete, UUID.randomUUID(), true)).code(), key);
        }
        Files.writeString(Path.of(original.getAsJsonObject("verification").get("reportPath").getAsString()), "<testsuite/>");
        assertEquals("VERIFIED_EVIDENCE_CHANGED", assertThrows(VerifiedArtifactExporter.VerificationException.class,
                () -> VerifiedArtifactExporter.export(root, original, UUID.randomUUID(), true)).code());
    }

    private JsonObject evidence(String xml, int minimum) throws Exception {
        Path directory = root.resolve(".copperbench/accepted/" + UUID.randomUUID()); Files.createDirectories(directory);
        Path jar = directory.resolve("mod.jar"), report = directory.resolve("report.xml");
        Files.writeString(jar, "tested-jar"); Files.writeString(report, xml);
        var started = java.time.Instant.now().minusSeconds(1);
        JsonObject verification = GameTestReport.read(report, started, minimum);
        verification.addProperty("artifactPath", jar.toString());
        verification.addProperty("artifactSha256", WorkspaceExecutionSnapshot.sha256(jar));
        verification.addProperty("reportSha256", WorkspaceExecutionSnapshot.sha256(report));
        verification.addProperty("mode", "packaged_jar"); verification.addProperty("minimumTests", minimum);
        verification.addProperty("startedAt", started.toString()); verification.addProperty("processExitCode", 0);
        JsonObject snapshot = new JsonObject(); snapshot.addProperty("sha256", WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        verification.add("sourceSnapshot", snapshot);
        JsonObject task = new JsonObject(); task.addProperty("id", UUID.randomUUID().toString()); task.addProperty("state", "succeeded");
        task.add("verification", verification); return task;
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
