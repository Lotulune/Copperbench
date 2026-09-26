package dev.copperbench.core.workspace.mcreator;

import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.workspace.Workspace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit diagnostic probe on copies of independently failed inputs; not a public-flow acceptance claim. */
@EnabledIfSystemProperty(named = "copperbench.stage17.coldWorkspaceRoot", matches = ".+")
class Stage17ColdWorkspaceProbeTest {
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void legacyModelWorkspaceBuildPreparesImportsAndRetainsImportedAssetsWithoutPendingMetadata() throws Exception {
        Path source = Path.of(System.getProperty("copperbench.stage17.coldWorkspaceRoot"));
        Path evidence = Path.of("build/stage17-legacy-model-build", UUID.randomUUID().toString()).toAbsolutePath();
        Path root = evidence.resolve("workspace");
        WorkspaceExecutionSnapshot.capture(source, root, UUID.randomUUID(), 0, Clock.systemUTC(), () -> false);
        var retained = new java.util.LinkedHashMap<Path, String>();
        for (String asset : List.of("models/blockbench/independent_protocol.bbmodel",
                "src/main/resources/assets/structured_forge/models/custom/resonance_forge.json",
                "src/main/resources/assets/structured_forge/textures/block/independent_protocol.png")) {
            Path file = root.resolve(asset);
            retained.put(file, WorkspaceExecutionSnapshot.sha256(file));
        }
        Path unowned = root.resolve("src/main/java/LegacyUnownedSentinel.java");
        Files.writeString(unowned, "// Retained legacy user source\nclass LegacyUnownedSentinel {}\n");
        retained.put(unowned, WorkspaceExecutionSnapshot.sha256(unowned));
        try (Workspace workspace = Workspace.readFromFS(root.resolve("structured_forge.mcreator").toFile(), null)) {
            assertNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
            assertTrue(MCreatorGenerationPreparation.needsDependencies(workspace));
            Path entity = root.resolve("src/main/java/net/mcreator/structured_forge/block/entity/resonance_forgeBlockEntity.java");
            assertFalse(Files.readString(entity).contains("import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;"));
            try (var session = MCreatorWorkspaceSession.attach(workspace,
                    store -> new dev.copperbench.generator.LoaderRoutingWorkspaceTaskGateway(store, ignored -> root,
                            Path.of(".").toAbsolutePath(), Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                long revision = workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()).revision();
                var payload = new com.google.gson.JsonObject(); payload.addProperty("scope", "workspace");
                var started = session.uiEntry().execute(dev.copperbench.core.contract.UiCore.Command.of(UUID.randomUUID(), session.workspaceId(),
                        revision, dev.copperbench.core.contract.UiCore.Operation.BUILD_WORKSPACE, payload));
                assertEquals("accepted", started.result().status(), started.result().diagnostics().toString());
                UUID taskId = UUID.fromString(started.result().task().getAsJsonObject().get("id").getAsString());
                com.google.gson.JsonObject task = null;
                long deadline = System.nanoTime() + Duration.ofMinutes(18).toNanos();
                while (System.nanoTime() < deadline) {
                    var query = new com.google.gson.JsonObject(); query.addProperty("taskId", taskId.toString());
                    task = session.uiEntry().query(dev.copperbench.core.contract.UiCore.Query.of(UUID.randomUUID(), session.workspaceId(),
                            dev.copperbench.core.contract.UiCore.Operation.GET_TASK, query)).data().getAsJsonObject();
                    if (!task.getAsJsonObject("task").get("state").getAsString().equals("running")) break;
                    Thread.sleep(200);
                }
                Files.writeString(evidence.resolve("build-task.json"), task.toString());
                assertEquals("succeeded", task.getAsJsonObject("task").get("state").getAsString(), task.toString());
                assertTrue(Files.readString(entity).contains("import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;"));
                for (var entry : retained.entrySet()) assertEquals(entry.getValue(), WorkspaceExecutionSnapshot.sha256(entry.getKey()), entry.getKey().toString());
                Path jar = root.resolve("build/libs/structured_forge-1.0.jar");
                assertTrue(Files.isRegularFile(jar));
                try (var archive = new java.util.zip.ZipFile(jar.toFile())) {
                    for (var entry : retained.entrySet()) if (entry.getKey().startsWith(root.resolve("src/main/resources"))) {
                        String name = root.resolve("src/main/resources").relativize(entry.getKey()).toString().replace('\\', '/');
                        var archived = archive.getEntry(name);
                        assertNotNull(archived, name);
                        assertArrayEquals(Files.readAllBytes(entry.getKey()), archive.getInputStream(archived).readAllBytes(), name);
                    }
                }
            }
        } finally { System.out.println("STAGE17_LEGACY_MODEL_BUILD_EVIDENCE=" + evidence); }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void coldMutationThenManagedBuildPreparesImportsWithoutOverwritingManualSources(String loader) throws Exception {
        Path source = Path.of(System.getProperty("copperbench.stage17.coldWorkspaceRoot"), loader);
        Path evidence = Path.of("build/stage17-managed-cold", loader + "-" + UUID.randomUUID()).toAbsolutePath();
        Path root = evidence.resolve("workspace");
        WorkspaceExecutionSnapshot.capture(source, root, UUID.randomUUID(), 0, Clock.systemUTC(), () -> false);
        try (Workspace workspace = Workspace.readFromFS(root.resolve("structured_forge.mcreator").toFile(), null)) {
            assertNull(workspace.getGenerator().getGradleCache());
            var manual = workspace.getModElementByName("resonance_forge");
            manual.setCodeLock(true);
            var retained = new java.util.LinkedHashMap<Path, String>();
            for (var file : manual.getAssociatedFiles()) if (file.isFile()) retained.put(file.toPath(), WorkspaceExecutionSnapshot.sha256(file.toPath()));
            Path unowned = root.resolve("src/main/java/UnownedSentinel.java");
            Files.writeString(unowned, "// Retained independent user source\nclass UnownedSentinel {}\n");
            retained.put(unowned, WorkspaceExecutionSnapshot.sha256(unowned));
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            try (var session = MCreatorWorkspaceSession.attach(workspace,
                    store -> new dev.copperbench.generator.LoaderRoutingWorkspaceTaskGateway(store, ignored -> root,
                            Path.of(".").toAbsolutePath(), Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                long revision = workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()).revision();
                var element = workspace.getModElementByName("contract_probe");
                Path javaFile = element.getAssociatedFiles().stream().filter(file -> file.getName().equals("contract_probeBlock.java")).findFirst().orElseThrow().toPath();
                String before = Files.readString(javaFile);
                var payload = new com.google.gson.JsonObject();
                payload.addProperty("elementId", element.getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA).toString());
                payload.add("changes", com.google.gson.JsonParser.parseString("[{\"path\":\"/hardness\",\"value\":6}]"));
                var updated = session.uiEntry().execute(dev.copperbench.core.contract.UiCore.Command.of(UUID.randomUUID(),
                        session.workspaceId(), revision, dev.copperbench.core.contract.UiCore.Operation.UPDATE_MOD_ELEMENT, payload));
                assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
                assertNull(workspace.getGenerator().getGradleCache(), "Field mutation must not perform implicit dependency downloads");
                assertEquals(before, Files.readString(javaFile), "Cold mutation must not replace sources with unresolved template text");
                assertNotNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
                payload = new com.google.gson.JsonObject(); payload.addProperty("scope", "workspace");
                var started = session.uiEntry().execute(dev.copperbench.core.contract.UiCore.Command.of(UUID.randomUUID(), session.workspaceId(),
                        revision + 1, dev.copperbench.core.contract.UiCore.Operation.BUILD_WORKSPACE, payload));
                assertEquals("accepted", started.result().status(), started.result().diagnostics().toString());
                UUID taskId = UUID.fromString(started.result().task().getAsJsonObject().get("id").getAsString());
                com.google.gson.JsonObject task = null;
                long deadline = System.nanoTime() + Duration.ofMinutes(18).toNanos();
                while (System.nanoTime() < deadline) {
                    var query = new com.google.gson.JsonObject(); query.addProperty("taskId", taskId.toString());
                    task = session.uiEntry().query(dev.copperbench.core.contract.UiCore.Query.of(UUID.randomUUID(), session.workspaceId(),
                            dev.copperbench.core.contract.UiCore.Operation.GET_TASK, query)).data().getAsJsonObject();
                    if (!task.getAsJsonObject("task").get("state").getAsString().equals("running")) break;
                    Thread.sleep(200);
                }
                Files.writeString(evidence.resolve("managed-build.json"), task.toString());
                assertEquals("succeeded", task.getAsJsonObject("task").get("state").getAsString(), task.toString());
                assertTrue(Files.readString(javaFile).contains("strength(6"), Files.readString(javaFile));
                assertNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
                for (var entry : retained.entrySet()) assertEquals(entry.getValue(), WorkspaceExecutionSnapshot.sha256(entry.getKey()), entry.getKey().toString());
                assertTrue(Files.isRegularFile(root.resolve("build/libs/structured_forge-1.0.jar")));
            }
        } finally { System.out.println("STAGE17_MANAGED_COLD_EVIDENCE=" + evidence); }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void testWhetherExplicitSetupAndRegenerationRepairsTheIndependentColdInput(String loader) throws Exception {
        Path source = Path.of(System.getProperty("copperbench.stage17.coldWorkspaceRoot"), loader);
        Path evidence = Path.of("build/stage17-cold-probes", loader + "-" + UUID.randomUUID()).toAbsolutePath();
        Path root = evidence.resolve("workspace");
        WorkspaceExecutionSnapshot.capture(source, root, UUID.randomUUID(), 0, Clock.systemUTC(), () -> false);
        var lines = new ArrayList<String>();
        try (Workspace workspace = Workspace.readFromFS(root.resolve("structured_forge.mcreator").toFile(), null)) {
            assertNull(workspace.getGenerator().getGradleCache(), "Fixture must reproduce the missing index before setup");
            Path javaHome = Path.of(dev.copperbench.platform.RuntimePlatform.current().sourceJavaHome(21)).toAbsolutePath();
            var previousJavaHome = PreferencesManager.PREFERENCES.hidden.java_home.get();
            try {
                PreferencesManager.PREFERENCES.hidden.java_home.set(javaHome.resolve("bin")
                        .resolve(dev.copperbench.platform.RuntimePlatform.current().javaExecutableName()).toFile());
                net.mcreator.gradle.GradleUtils.updateMCreatorBuildFile(workspace);
                String syncTask = workspace.getGeneratorConfiguration().getGradleTaskFor("sync_task");
                var runner = Fabric1211ProcessRunner.system("unused", javaHome);
                var sync = runner.run(root, List.of(syncTask == null ? "help" : syncTask, "--offline", "--console=plain"),
                        Duration.ofMinutes(8), lines::add);
                assertEquals(0, sync.exitCode(), String.join("\n", lines));
                workspace.getGenerator().reloadGradleCaches();
                assertNotNull(workspace.getGenerator().getGradleCache().getImportTree().get("Block"));
                workspace.getGenerator().runResourceSetupTasks();
                assertTrue(workspace.getGenerator().generateBase());
                for (var element : workspace.getModElements()) {
                    if (!element.isCodeLocked() && element.getGeneratableElement() != null)
                        assertTrue(workspace.getGenerator().generateElement(element.getGeneratableElement()), element.getName());
                }
                assertTrue(workspace.getGenerator().generateBase());
                WorkspaceGeneratorSetup.completeSetup(workspace.getGenerator());
                var build = runner.run(root, List.of("build", "--offline", "--console=plain"), Duration.ofMinutes(8), lines::add);
                assertEquals(0, build.exitCode(), String.join("\n", lines));
            } finally {
                PreferencesManager.PREFERENCES.hidden.java_home.set(previousJavaHome);
            }
        } finally {
            Files.writeString(evidence.resolve("probe.log"), String.join("\n", lines));
            System.out.println("STAGE17_COLD_WORKSPACE_EVIDENCE=" + evidence);
        }
    }
}
