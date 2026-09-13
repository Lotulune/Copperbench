package dev.copperbench.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class GameTestWorkflowTest {
    @TempDir Path temp;

    @ParameterizedTest @ValueSource(strings = {"pass", "zero", "framework_only", "skipped", "failed", "missing", "stale", "process_exit", "artifact_changed", "source_changed"})
    void isolatedAcceptanceUsesRealNativeFilesAndActualReports(String outcome) throws Exception {
        Path root = temp.resolve("native-project");
        write(root.resolve("native.mcreator"), "{}");
        write(root.resolve("src/main/java/custom/Native.java"), "native implementation bytes");
        write(root.resolve("src/main/resources/fabric.mod.json"), "{\"id\":\"native_mod\",\"entrypoints\":{\"main\":[\"custom.Native\"]}}");
        write(root.resolve("build.gradle"), "// native build configuration");
        write(root.resolve("run/world/level.dat"), "existing player world");
        jar(root.resolve("build/libs/old.jar"), "old artifact");
        var environment = new Fabric1211Generator(Path.of(".")).gameTestEnvironment();
        GameTestSupport.prepare(root, environment, "native_mod");
        write(root.resolve("src/gametest/java/custom/Behavior.java"), "custom behavior assertions");
        UUID id = UUID.randomUUID();
        RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
        JsonObject generator = new JsonObject(); generator.addProperty("id", "fabric-1.21.1");
        store.register(new WorkspaceState(id, "native_mod", "mod", 0, false, generator, new JsonObject(), List.of()));
        AtomicInteger invocations = new AtomicInteger();
        Fabric1211ProcessRunner runner = (working, args, timeout, output) -> {
            invocations.incrementAndGet();
            if (args.equals(List.of("build"))) {
                assertEquals("workspace", working.getFileName().toString());
                assertEquals("native implementation bytes", Files.readString(working.resolve("src/main/java/custom/Native.java")));
                assertEquals("// native build configuration", Files.readString(working.resolve("build.gradle")));
                assertTrue(Files.readString(working.resolve("src/main/resources/fabric.mod.json")).contains("custom.Native"));
                assertFalse(Files.exists(working.resolve("build/libs/old.jar")));
                assertFalse(Files.exists(working.resolve("run/world")));
                jar(working.resolve("build/libs/native_mod.jar"), "fresh packaged implementation");
            } else {
                assertEquals(List.of("runGameTest"), args);
                assertEquals("host", working.getFileName().toString());
                assertFalse(Files.exists(working.resolve("src/main/java/custom/Native.java")));
                assertEquals("custom behavior assertions", Files.readString(working.resolve("src/main/java/custom/Behavior.java")));
                Path deployed = working.resolve("run/mods/native_mod.jar");
                assertEquals(WorkspaceExecutionSnapshot.sha256(working.resolveSibling("workspace/build/libs/native_mod.jar")), WorkspaceExecutionSnapshot.sha256(deployed));
                Path report = working.resolve("run/gametest-results.xml");
                String testcase = switch (outcome) {
                    case "zero" -> "";
                    case "framework_only" -> "<testcase name=\"minecraft:always_pass\"/>";
                    case "skipped" -> "<testcase name=\"scenario\"><skipped/></testcase>";
                    case "failed" -> "<testcase name=\"scenario\"><failure message=\"Expected waypoint to survive serialization\"/></testcase>";
                    default -> "<testcase name=\"scenario\" classname=\"Behavior\"/>";
                };
                if (!outcome.equals("missing")) write(report, "<testsuite>" + testcase + "</testsuite>");
                if (outcome.equals("stale")) Files.setLastModifiedTime(report, FileTime.from(Instant.now().minusSeconds(300)));
                if (outcome.equals("artifact_changed")) jar(deployed, "changed after test start");
                if (outcome.equals("source_changed")) write(root.resolve("src/main/java/custom/Native.java"), "newer implementation");
                if (outcome.equals("process_exit")) return new Fabric1211ProcessRunner.ProcessResult(1, false);
            }
            return new Fabric1211ProcessRunner.ProcessResult(0, false);
        };
        try (var gateway = new Fabric1211WorkspaceTaskGateway(store, ignored -> root, Path.of("."), Clock.systemUTC(), UUID::randomUUID, runner)) {
            JsonObject started = gateway.start(id, Operation.RUN_GAMETEST, new JsonObject());
            UUID taskId = UUID.fromString(started.get("id").getAsString());
            JsonObject task = await(gateway, id, taskId);
            assertEquals(outcome.equals("pass") ? "succeeded" : "failed", task.get("state").getAsString(), task.toString());
            assertEquals(2, invocations.get());
            var report = task.getAsJsonObject("verification");
            assertEquals(outcome.equals("pass") ? "passed" : "failed", report.get("status").getAsString());
            assertEquals(task.getAsJsonObject("sourceSnapshot"), report.getAsJsonObject("sourceSnapshot"));
            assertEquals(report, JsonParser.parseString(Files.readString(Path.of(report.get("verificationPath").getAsString()))));
            if (outcome.equals("pass")) { assertEquals(1, report.get("executed").getAsInt()); assertEquals(1, report.get("passed").getAsInt()); }
            if (outcome.equals("failed")) assertTrue(report.getAsJsonArray("cases").toString().contains("survive serialization"));
            assertEquals("existing player world", Files.readString(root.resolve("run/world/level.dat")));
            assertEquals(outcome.equals("source_changed") ? "newer implementation" : "native implementation bytes", Files.readString(root.resolve("src/main/java/custom/Native.java")));
            assertEquals(!outcome.equals("source_changed"), report.get("sourceCurrentAtCompletion").getAsBoolean());
        }
    }

    @Test void scaffoldsEveryJavaTrackAndKeepsAuthoredTestsOnRepeatedPreparation() throws Exception {
        List<JsonObject> environments = new ArrayList<>();
        for (var profile : List.of(Fabric1211Generator.Profile.FABRIC_1201, Fabric1211Generator.Profile.FABRIC_1211,
                Fabric1211Generator.Profile.FABRIC_261, Fabric1211Generator.Profile.FABRIC_262))
            environments.add(new Fabric1211Generator(Path.of("."), profile).gameTestEnvironment());
        for (var profile : List.of(NeoForge1211Generator.Profile.NEOFORGE_1201, NeoForge1211Generator.Profile.NEOFORGE_1211,
                NeoForge1211Generator.Profile.NEOFORGE_261, NeoForge1211Generator.Profile.NEOFORGE_262))
            environments.add(new NeoForge1211Generator(Path.of("."), profile).gameTestEnvironment());
        for (JsonObject environment : environments) {
            Path root = temp.resolve(environment.get("generatorId").getAsString());
            var setup = GameTestSupport.prepare(root, environment, "native_mod");
            assertEquals("mod_loading_only", setup.get("starterScope").getAsString());
            assertEquals("packaged_jar", GameTestSupport.configuration(root).mode());
            Path test = root.resolve("src/gametest/java/copperbench/acceptance/AcceptanceTests.java");
            assertTrue(Files.readString(test).contains("native_mod"));
            Files.writeString(test, "authored tests");
            GameTestSupport.prepare(root, environment, "native_mod");
            assertEquals("authored tests", Files.readString(test));
            String build = GameTestSupport.hostBuild(environment, root.resolve("run/report.xml"));
            assertTrue(build.contains(environment.get("pluginVersion").getAsString()));
            assertTrue(build.contains("runGameTest"));
        }
    }

    @Test void rejectsUnsafeConfigurationEmptyOrAmbiguousArtifactsAndImplicitLegacyEula() throws Exception {
        Path root = temp.resolve("project"); Files.createDirectories(root);
        for (String field : List.of("sourceDirectory", "resourceDirectory", "reportPath")) {
            write(root.resolve(GameTestSupport.CONFIG_FILE), "{\"schemaVersion\":\"1.0\",\"" + field + "\":\"../outside\"}");
            assertThrows(java.io.IOException.class, () -> GameTestSupport.configuration(root));
        }
        write(root.resolve(GameTestSupport.CONFIG_FILE), "{\"schemaVersion\":\"1.0\",\"sourceDirectory\":\"src/main/java\"}");
        assertThrows(java.io.IOException.class, () -> GameTestSupport.configuration(root));
        assertThrows(GameTestSupport.TestSetupException.class, () -> GameTestSupport.artifact(root, "fabric"));
        jar(root.resolve("build/libs/one.jar"), "one"); jar(root.resolve("build/libs/two.jar"), "two");
        assertEquals("GAMETEST_ARTIFACT_AMBIGUOUS", assertThrows(GameTestSupport.TestSetupException.class, () -> GameTestSupport.artifact(root, "fabric")).code());
        var environment = new NeoForge1211Generator(Path.of("."), NeoForge1211Generator.Profile.NEOFORGE_1201).gameTestEnvironment();
        assertEquals("SERVER_EULA_APPROVAL_REQUIRED", assertThrows(GameTestSupport.TestSetupException.class,
                () -> GameTestSupport.requireServerAuthorization(environment, false)).code());
        assertFalse(Files.exists(temp.resolve("host/run/eula.txt")));
    }

    private static JsonObject await(Fabric1211WorkspaceTaskGateway tasks, UUID workspaceId, UUID taskId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            JsonObject task = tasks.find(workspaceId, taskId).orElseThrow();
            if (!List.of("running", "queued").contains(task.get("state").getAsString())) return task;
            Thread.sleep(20);
        }
        throw new AssertionError("Task timed out");
    }
    private static void write(Path path, String bytes) throws Exception { Files.createDirectories(path.getParent()); Files.writeString(path, bytes); }
    private static void jar(Path path, String bytes) throws Exception {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json")); zip.write("{\"id\":\"native_mod\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("custom/Native.class")); zip.write(bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
    }
}
