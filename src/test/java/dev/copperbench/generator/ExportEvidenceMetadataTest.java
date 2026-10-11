package dev.copperbench.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Tests export receipts using real temporary files, not a Minecraft gameplay claim. */
class ExportEvidenceMetadataTest {
    @TempDir Path root;

    private JsonObject evidence(int minimum) throws Exception {
        Path directory = root.resolve(".copperbench/accepted/" + UUID.randomUUID());
        Files.createDirectories(directory);
        Path jar = directory.resolve("mod.jar"), report = directory.resolve("report.xml");
        Files.writeString(jar, "test artifact bytes");
        Files.writeString(report, "<testsuite><testcase name='business'/></testsuite>");
        Instant started = Instant.now().minusSeconds(1);
        JsonObject verification = GameTestReport.read(report, started, minimum);
        verification.addProperty("mode", "packaged_jar");
        verification.addProperty("minimumTests", minimum);
        verification.addProperty("startedAt", started.toString());
        verification.addProperty("processExitCode", 0);
        verification.addProperty("artifactPath", jar.toString());
        verification.addProperty("artifactSha256", WorkspaceExecutionSnapshot.sha256(jar));
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("sha256", WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        verification.add("sourceSnapshot", snapshot);
        JsonObject task = new JsonObject();
        task.addProperty("id", UUID.randomUUID().toString());
        task.addProperty("state", "succeeded");
        task.add("verification", verification);
        return task;
    }

    private JsonObject portable(JsonObject receipt) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(receipt.get("exportDirectory").getAsString())
                .resolve("verification.json"))).getAsJsonObject();
    }

    @Test void exportsObservedToolchainAndValidatedAcceptanceWithoutMutatingRecord() throws Exception {
        JsonObject task = evidence(1), record = task.getAsJsonObject("verification");
        JsonObject environment = JsonParser.parseString("""
                {"generatorId":"fabric-1.21.1","loader":"fabric","minecraftVersion":"1.21.1",
                 "loaderVersion":"0.19.3","javaRelease":21,"fabricApiVersion":"0.116.15+1.21.1",
                 "pluginId":"net.fabricmc.fabric-loom-remap","pluginVersion":"1.17.19"}
                """).getAsJsonObject();
        record.add("environment", environment);
        JsonObject original = task.deepCopy();
        JsonObject receipt = VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), false);
        assertEquals(environment, receipt.getAsJsonObject("environment"));
        assertFalse(receipt.getAsJsonObject("environment").has("javaVersion"));
        assertEquals(original, task);
        Path directory = Path.of(receipt.get("exportDirectory").getAsString());
        JsonObject disk = JsonParser.parseString(Files.readString(directory.resolve("verification.json"))).getAsJsonObject();
        JsonObject expectedDisk = receipt.deepCopy(); expectedDisk.remove("exportDirectory");
        JsonObject legacyFields = disk.deepCopy();
        for (String key : new String[]{"portableEvidenceVersion", "environmentSource", "mode", "minimumTests",
                "startedAt", "processExitCode", "coverage", "applicationIdentitySha256"}) {
            legacyFields.remove(key);
            assertFalse(receipt.has(key), "Strict v1.0 wire response must not gain " + key);
        }
        assertEquals(expectedDisk, legacyFields);
        assertEquals(WorkspaceExecutionSnapshot.sha256(directory.resolve("application-identity.json")),
                disk.get("applicationIdentitySha256").getAsString());
        assertEquals("acceptance_record", disk.get("environmentSource").getAsString());
        assertEquals("1", disk.get("portableEvidenceVersion").getAsString());
        assertEquals("packaged_jar", disk.get("mode").getAsString());
        assertEquals(1, disk.get("minimumTests").getAsInt());
        assertEquals(0, disk.get("processExitCode").getAsInt());
        assertEquals(record.get("startedAt"), disk.get("startedAt"));
        assertEquals("sufficient", disk.getAsJsonObject("coverage").get("status").getAsString());
        assertEquals(record.get("artifactSha256").getAsString(), WorkspaceExecutionSnapshot.sha256(directory.resolve("verified-mod.jar")));
        assertEquals(record.get("reportSha256").getAsString(), WorkspaceExecutionSnapshot.sha256(directory.resolve("gametest-results.xml")));
    }

    @Test void environmentProjectionDoesNotCopyUnknownPathsOrStructuredValues() throws Exception {
        JsonObject task = evidence(1);
        task.getAsJsonObject("verification").add("environment", JsonParser.parseString("""
                {"javaVersion":"21.0.12","javaRelease":21,"pluginId":null,
                 "pluginVersion":{"unexpected":"shape"},"javaHome":"/private/runtime",
                 "token":"must-not-be-copied","otherFutureMetadata":{"secret":"must-not-be-copied"}}
                """));
        JsonObject receipt = VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), false);
        JsonObject environment = receipt.getAsJsonObject("environment");
        assertEquals(2, environment.size());
        assertEquals("21.0.12", environment.get("javaVersion").getAsString());
        assertEquals(21, environment.get("javaRelease").getAsInt());
        assertFalse(receipt.toString().contains("must-not-be-copied"));
        assertFalse(receipt.toString().contains("/private/runtime"));
    }

    @Test void legacyRecordsWithoutNewMetadataRemainExportable() throws Exception {
        JsonObject task = evidence(1);
        task.getAsJsonObject("verification").remove("coverage");
        JsonObject receipt = VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), false);
        assertTrue(receipt.getAsJsonObject("environment").isEmpty());
        assertEquals(1, portable(receipt).getAsJsonObject("coverage").get("acceptanceExecuted").getAsInt());
        assertEquals("passed_current_input", receipt.get("status").getAsString());
    }

    @Test void coverageIsRecomputedFromRawXmlNotCopiedFromRestoredMetadata() throws Exception {
        JsonObject task = evidence(1);
        task.getAsJsonObject("verification").add("coverage", JsonParser.parseString("""
                {"status":"sufficient","minimumTests":0,"acceptanceExecuted":999,"shortfall":0}
                """));
        JsonObject receipt = VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), false);
        assertEquals(1, portable(receipt).getAsJsonObject("coverage").get("minimumTests").getAsInt());
        assertEquals(1, portable(receipt).getAsJsonObject("coverage").get("acceptanceExecuted").getAsInt());
    }

    @Test void insufficientRawCoverageCannotBePromotedByAdditionalMetadata() throws Exception {
        JsonObject task = evidence(2);
        task.getAsJsonObject("verification").addProperty("status", "passed");
        task.getAsJsonObject("verification").add("coverage", JsonParser.parseString("{\"status\":\"sufficient\"}"));
        for (boolean historical : new boolean[]{false, true}) {
            var error = assertThrows(VerifiedArtifactExporter.VerificationException.class,
                    () -> VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), historical));
            assertEquals("VERIFIED_ACCEPTANCE_INVALID", error.code());
        }
        assertFalse(Files.exists(root.resolve(".copperbench/verified-exports")));
    }
}
