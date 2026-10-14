package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RequiredCasesWorkflowTest {
    @TempDir Path root;

    @ParameterizedTest @ValueSource(strings = {"pass", "missing", "skipped", "duplicate_resource", "frozen_changed"})
    void taskFreezesRequiredIdentitiesAndRecoversWithoutChangingLegacyProjection(String outcome) throws Exception {
        RequiredCaseManifest manifest = RequiredCaseManifest.catalog().getFirst();
        UUID workspaceId = UUID.randomUUID();
        var store = workspace(workspaceId);
        Fabric1211ProcessRunner process = (working, args, timeout, output) -> {
            Path binding = working.resolveSibling("required-cases-frozen.json");
            assertTrue(Files.isRegularFile(binding), "The selection must be frozen before either process starts");
            if (args.equals(List.of("build"))) jar(working.resolve("build/libs/native.jar"));
            else {
                assertEquals(List.of("runGameTest"), args);
                StringBuilder xml = new StringBuilder("<testsuite>");
                for (var test : manifest.cases()) {
                    boolean last = test.name().endsWith("t12");
                    String name = last && outcome.equals("missing") ? "copperbench_camp_kit:unrelated" : test.name();
                    xml.append("<testcase classname='host:empty' name='").append(name).append("'>");
                    if (last && outcome.equals("skipped")) xml.append("<skipped/>");
                    xml.append("</testcase>");
                }
                if (outcome.equals("skipped")) xml.append("<testcase name='copperbench_camp_kit:extra'/>");
                if (outcome.equals("duplicate_resource")) xml.append("<testcase classname='different_template' name='copperbench_camp_kit:t01'/>");
                xml.append("</testsuite>");
                write(working.resolve("run/gametest-results.xml"), xml.toString());
                if (outcome.equals("frozen_changed")) Files.writeString(binding, "{}", StandardCharsets.UTF_8);
            }
            return new Fabric1211ProcessRunner.ProcessResult(0, false);
        };
        JsonObject payload = new JsonObject(); payload.add("requiredCases", manifest.projection());
        UUID taskId;
        try (var gateway = new Fabric1211WorkspaceTaskGateway(store, ignored -> root, Path.of("."), Clock.systemUTC(), UUID::randomUUID, process)) {
            taskId = UUID.fromString(gateway.start(workspaceId, Operation.RUN_GAMETEST, payload).get("id").getAsString());
            // Caller edits cannot replace the copied request or the frozen list.
            payload.getAsJsonObject("requiredCases").getAsJsonArray("cases").remove(0);
            JsonObject task = await(gateway, workspaceId, taskId);
            assertEquals(outcome.equals("pass") ? "succeeded" : "failed", task.get("state").getAsString(), task.toString());
            assertFalse(task.has(RequiredCaseEvidence.TASK_FIELD));
            assertFalse(task.has(AcceptanceDiagnostics.TASK_FIELD));
            assertFalse(task.getAsJsonObject("verification").has("requiredCases"));
            if (!outcome.equals("pass")) {
                var service = new dev.copperbench.core.application.WorkspaceApplicationService(store, gateway, Clock.systemUTC(), UUID::randomUUID);
                JsonObject query = new JsonObject(); query.addProperty("taskId", taskId.toString());
                var result = service.query(dev.copperbench.core.contract.UiCore.Query.of(UUID.randomUUID(), workspaceId, Operation.GET_TASK, query),
                        new dev.copperbench.core.contract.UiCore.RequestContext(dev.copperbench.core.contract.UiCore.Actor.HEADLESS,
                                dev.copperbench.core.contract.UiCore.PermissionProfile.READ_ONLY));
                var diagnostics = result.data().getAsJsonObject().getAsJsonArray("diagnostics");
                assertFalse(diagnostics.isEmpty());
                assertTrue(diagnostics.get(0).getAsJsonObject().has("path"), "The v1.0 diagnostic location cannot be dropped by tree serialization");
            }
        }
        JsonObject raw = WorkspaceTaskRecords.read(root, workspaceId, taskId).orElseThrow().getAsJsonObject("task");
        JsonObject recovered = RequiredCaseEvidence.recorded(root, workspaceId, taskId);
        assertEquals(manifest.projection(), recovered.getAsJsonObject("binding").get("manifest"));
        assertEquals(outcome.equals("pass") ? "passed" : "failed", recovered.get("status").getAsString());
        if (!outcome.equals("pass")) {
            assertThrows(VerifiedArtifactExporter.VerificationException.class, () -> VerifiedArtifactExporter.export(root, raw, UUID.randomUUID(), true));
            return;
        }
        JsonObject exported = VerifiedArtifactExporter.export(root, raw, UUID.randomUUID(), false);
        assertFalse(exported.has("requiredCases"), "The strict v1.0 export envelope remains unchanged");
        Path receipt = Path.of(exported.get("exportDirectory").getAsString()).resolve("verification.json");
        JsonObject portable = com.google.gson.JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
        assertEquals(manifest.projection(), portable.getAsJsonObject("requiredCases").get("manifest"));
        assertFalse(portable.toString().contains("frozenPath"));
        JsonObject wrongTask = raw.deepCopy(); wrongTask.addProperty("id", UUID.randomUUID().toString());
        assertRejected(wrongTask);
        JsonObject otherLoader = raw.deepCopy();
        otherLoader.getAsJsonObject("verification").getAsJsonObject("environment").addProperty("loader", "neoforge");
        assertRejected(otherLoader);
        // Even identical passing bytes cannot be borrowed from another run's host.
        JsonObject borrowedReport = raw.deepCopy();
        Path anotherReport = root.resolve(".copperbench/task-runs/run_gametest/").resolve(UUID.randomUUID().toString())
                .resolve("host/run/gametest-results.xml");
        Files.createDirectories(anotherReport.getParent());
        Files.copy(Path.of(raw.getAsJsonObject("verification").get("reportPath").getAsString()), anotherReport);
        borrowedReport.getAsJsonObject("verification").addProperty("reportPath", anotherReport.toAbsolutePath().toString());
        assertRejected(borrowedReport);
        JsonObject missingBinding = raw.deepCopy(); missingBinding.remove(RequiredCaseEvidence.TASK_FIELD);
        assertRejected(missingBinding);
        JsonObject changedManifest = raw.deepCopy();
        changedManifest.getAsJsonObject(RequiredCaseEvidence.TASK_FIELD).getAsJsonObject("binding")
                .getAsJsonObject("manifest").addProperty("sha256", "0".repeat(64));
        assertRejected(changedManifest);
        // Counts and labels can be made consistent with a new report; the original required list still rejects it.
        JsonObject verification = raw.getAsJsonObject("verification");
        Path report = Path.of(verification.get("reportPath").getAsString());
        Files.writeString(report, Files.readString(report).replace("copperbench_camp_kit:t12", "copperbench_camp_kit:extra"));
        JsonObject reread = GameTestReport.read(report, Instant.parse(verification.get("startedAt").getAsString()), 6);
        reread.entrySet().forEach(entry -> verification.add(entry.getKey(), entry.getValue()));
        assertEquals("passed", verification.get("status").getAsString());
        assertRejected(raw);
    }

    @Test void manifestPinsRawIdentityDigestAndPublishedGateSize() {
        var nativeManifest = RequiredCaseManifest.catalog().getFirst();
        assertEquals(6, nativeManifest.cases().size());
        assertEquals(13, RequiredCaseManifest.catalog().getLast().cases().size());
        assertEquals(nativeManifest, RequiredCaseManifest.parse(nativeManifest.projection()));
        JsonObject changed = nativeManifest.projection(); changed.addProperty("sha256", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> RequiredCaseManifest.parse(changed));
        assertThrows(IllegalArgumentException.class, () -> new RequiredCaseManifest("camp-kit-native-v1", RequiredCaseManifest.GAMETEST,
                5, nativeManifest.cases().subList(0, 5)));
        String longPrefix = "x".repeat(700);
        var tuple = new RequiredCaseManifest("raw-v1", RequiredCaseManifest.JUNIT, 2, List.of(
                new RequiredCaseManifest.Identity("a#b", longPrefix + "c"), new RequiredCaseManifest.Identity("a", "b#" + longPrefix + "c")));
        assertEquals(tuple, RequiredCaseManifest.parse(tuple.projection()));
        assertNotEquals(tuple.sha256(), nativeManifest.sha256());
    }

    private void assertRejected(JsonObject task) {
        for (boolean historical : List.of(false, true))
            assertThrows(VerifiedArtifactExporter.VerificationException.class, () -> VerifiedArtifactExporter.export(root, task, UUID.randomUUID(), historical));
    }

    private RevisionedWorkspaceStore workspace(UUID id) throws Exception {
        write(root.resolve("native.mcreator"), "{}");
        write(root.resolve("build.gradle"), "// native build");
        write(root.resolve("src/main/java/custom/Native.java"), "native implementation bytes");
        write(root.resolve("src/main/resources/fabric.mod.json"), "{\"id\":\"native_mod\",\"entrypoints\":{\"main\":[\"custom.Native\"]}}");
        GameTestSupport.prepare(root, new Fabric1211Generator(Path.of(".")).gameTestEnvironment(), "native_mod");
        var store = new RevisionedWorkspaceStore();
        JsonObject generator = new JsonObject(); generator.addProperty("id", "fabric-1.21.1");
        store.register(new WorkspaceState(id, "native_mod", "mod", 0, false, generator, new JsonObject(), List.of()));
        return store;
    }

    private static JsonObject await(Fabric1211WorkspaceTaskGateway tasks, UUID workspaceId, UUID taskId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            JsonObject task = tasks.find(workspaceId, taskId).orElseThrow();
            if (!List.of("queued", "running").contains(task.get("state").getAsString())) return task;
            Thread.sleep(20);
        }
        throw new AssertionError("Acceptance task timed out");
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent()); Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void jar(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write("{\"id\":\"native_mod\"}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
    }
}
