package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Block;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** RF-01: assert durable definitions and generated Java, never just request metadata. */
class Stage17BlockConsistencyTest {
    @TempDir Path root;

    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void structuredBlockPersistsAndGeneratesRequestedBehavior(String generator) throws Exception {
        String selected = System.getProperty("copperbench.stage17.buildTracks", "");
        org.junit.jupiter.api.Assumptions.assumeTrue(selected.isBlank() || java.util.List.of(selected.split(",")).contains(generator),
                "This invocation only reruns explicitly selected tracks; prior evidence remains separate");
        boolean build = Boolean.getBoolean("copperbench.stage17.structuredBuild");
        // Keep real-build inputs as evidence. The upstream cache loader retains open JARs on Windows.
        if (build) root = Files.createDirectories(Path.of("build/stage17-structured-block-builds", generator,
                "workspace-" + UUID.randomUUID()).toAbsolutePath());
        WorkspaceSettings settings = new WorkspaceSettings("structured_forge");
        settings.setModName("Structured Forge");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator(generator);
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("structured_forge.mcreator").toFile(), settings)) {
            if (build) {
                net.mcreator.generator.setup.WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
                net.mcreator.gradle.GradleUtils.updateMCreatorBuildFile(workspace);
                String syncTask = workspace.getGeneratorConfiguration().getGradleTaskFor("sync_task");
                var syncLog = new StringBuilder();
                var sync = dev.copperbench.generator.fabric.Fabric1211ProcessRunner.system("unused-sync-marker",
                                Path.of(net.mcreator.gradle.GradleUtils.getJavaHome(generator)))
                        .run(root, gradleArguments(syncTask == null ? "help" : syncTask, "--console=plain", "-g",
                                net.mcreator.io.UserFolderManager.getGradleHome().getAbsolutePath()),
                                java.time.Duration.ofMinutes(10), line -> syncLog.append(line).append('\n'));
                Path syncEvidence = root.resolve("build/stage17-evidence");
                Files.createDirectories(syncEvidence); Files.writeString(syncEvidence.resolve("sync.log"), syncLog);
                assertEquals(0, sync.exitCode(), syncLog.toString());
                workspace.getGenerator().reloadGradleCaches();
                workspace.getGenerator().runResourceSetupTasks();
            }
            assertTrue(workspace.getGenerator().generateBase());
            try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                JsonObject payload = JsonParser.parseString("""
                    {"elementType":"block","name":"resonance_forge","initialValues":{
                      "displayName":"Resonance Forge","hasInventory":true,"inventorySize":3,
                      "inventoryStackSize":64,"inventoryDropWhenDestroyed":true,"openGUIOnRightClick":false,
                      "hardness":3,"resistance":6,"rotationMode":1,"maxStackSize":16,"rarity":"RARE",
                      "boundingBoxes":[{"mx":0,"my":0,"mz":0,"Mx":16,"My":12,"Mz":16,"subtract":false}]}}
                    """).getAsJsonObject();
                payload.addProperty("clientMutationId", UUID.randomUUID().toString());
                var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                        Operation.CREATE_MOD_ELEMENT, payload));
                assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
                Block block = (Block) workspace.getModElementByName("resonance_forge").getGeneratableElement();
                assertEquals(3, block.hardness);
                assertEquals(6, block.resistance);
                assertEquals(1, block.rotationMode);
                assertTrue(block.hasInventory);
                assertEquals(3, block.inventorySize);
                assertEquals(64, block.inventoryStackSize);
                assertTrue(block.inventoryDropWhenDestroyed);
                assertEquals(16, block.maxStackSize);
                assertEquals("RARE", block.rarity);
                assertFalse(block.isBoundingBoxEmpty());
                assertFalse(block.isFullCube());
                assertTrue(workspace.getGenerator().generateElement(block), "Custom collision geometry must render through the track's template");
                String source = Files.readString(workspace.getModElementByName("resonance_forge").getAssociatedFiles()
                        .stream().filter(f -> f.getName().endsWith("Block.java")).findFirst().orElseThrow().toPath());
                assertTrue(source.contains("strength(3") || source.contains("destroyTime(3"), source);
                assertTrue(source.contains("FACING"), source);
                assertFalse(source.contains("Shapes.empty()"), source);
                assertTrue(workspace.getModElementByName("resonance_forge").getAssociatedFiles().stream()
                        .anyMatch(f -> f.getName().endsWith("BlockEntity.java")));
                assertTrue(workspace.getGenerator().generateBase());
                Path assets = root.resolve("src/main/resources/assets/structured_forge");
                JsonObject model = JsonParser.parseString(Files.readString(
                        assets.resolve("models/block/resonance_forge.json"))).getAsJsonObject();
                assertEquals("block/cube", model.get("parent").getAsString());
                for (String face : java.util.List.of("down", "up", "north", "east", "south", "west", "particle"))
                    assertEquals("minecraft:block/stone", model.getAsJsonObject("textures").get(face).getAsString());
                JsonObject language = JsonParser.parseString(Files.readString(assets.resolve("lang/en_us.json")))
                        .getAsJsonObject();
                String translationPrefix = generator.endsWith("1.20.1") || generator.endsWith("1.21.1")
                        ? "block." : "item.";
                assertEquals("Resonance Forge", language.get(translationPrefix + "structured_forge.resonance_forge").getAsString());
                try (var sources = Files.walk(root.resolve("src/main/java"))) {
                    Path items = sources.filter(path -> path.getFileName().toString().endsWith("Items.java"))
                            .findFirst().orElseThrow();
                    String registration = Files.readString(items);
                    assertTrue(registration.contains(".stacksTo(16)"), registration);
                    assertTrue(registration.contains(".rarity(Rarity.RARE)"), registration);
                }
                JsonObject stored = JsonParser.parseString(Files.readString(root.resolve("elements/resonance_forge.mod.json")))
                        .getAsJsonObject().getAsJsonObject("definition");
                assertEquals(3, stored.get("hardness").getAsDouble());
                assertEquals(3, stored.get("inventorySize").getAsInt());
                String id = created.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
                JsonObject update = new JsonObject(); update.addProperty("elementId", id);
                update.add("changes", JsonParser.parseString("[{\"path\":\"/hardness\",\"value\":5}]"));
                var changed = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1,
                        Operation.UPDATE_MOD_ELEMENT, update));
                assertEquals("committed", changed.result().status(), changed.result().diagnostics().toString());
                assertEquals(5, ((Block) workspace.getModElementByName("resonance_forge").getGeneratableElement()).hardness);
                workspace.reloadFromFileSystem();
                assertEquals(5, ((Block) workspace.getModElementByName("resonance_forge").getGeneratableElement()).hardness);
                if (generator.endsWith("1.20.1")) {
                    JsonObject plantPayload = JsonParser.parseString("""
                        {"elementType":"plant","name":"shaped_plant","initialValues":{
                          "customBoundingBox":true,"disableOffset":false,"offsetType":"XZ",
                          "plantType":"normal","texture":"minecraft:poppy","soundOnStep":"PLANT","colorOnMap":"PLANT","isSolid":true,
                          "suspiciousStewEffect":"SPEED","suspiciousStewDuration":100,
                          "boundingBoxes":[{"mx":2,"my":0,"mz":2,"Mx":14,"My":12,"Mz":14,"subtract":false}]}}
                        """).getAsJsonObject();
                    var plantCreated = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 2,
                            Operation.CREATE_MOD_ELEMENT, plantPayload));
                    assertEquals("committed", plantCreated.result().status(), plantCreated.result().diagnostics().toString());
                    var plant = workspace.getModElementByName("shaped_plant");
                    assertTrue(workspace.getGenerator().generateElement(plant.getGeneratableElement()));
                    String plantSource = Files.readString(plant.getAssociatedFiles().stream()
                            .filter(file -> file.getName().endsWith("Block.java")).findFirst().orElseThrow().toPath());
                    assertTrue(plantSource.contains(".move(offset.x, offset.y, offset.z)"), plantSource);
                    assertTrue(plantSource.contains("MobEffects.MOVEMENT_SPEED"), plantSource);
                }
                if (build) {
                    var log = new StringBuilder();
                    var built = dev.copperbench.generator.fabric.Fabric1211ProcessRunner.system("unused-build-marker",
                                    Path.of(net.mcreator.gradle.GradleUtils.getJavaHome(generator)))
                            .run(root, gradleArguments("build", "--console=plain", "-g",
                                    net.mcreator.io.UserFolderManager.getGradleHome().getAbsolutePath()),
                                    java.time.Duration.ofMinutes(15), line -> log.append(line).append('\n'));
                    Path evidence = root.resolve("build/stage17-evidence");
                    Files.createDirectories(evidence);
                    Files.writeString(evidence.resolve("build.log"), log);
                    assertEquals(0, built.exitCode(), log.toString());
                    try (var jars = Files.list(root.resolve("build/libs"))) {
                        Path jar = jars.filter(path -> path.toString().endsWith(".jar") && !path.toString().endsWith("-sources.jar")
                                && !path.toString().endsWith("-dev.jar")).findFirst().orElseThrow();
                        Files.copy(jar, evidence.resolve("structured-forge.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        Files.writeString(evidence.resolve("sha256.txt"), dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(jar));
                    }
                }
            }
        }
    }

    private static java.util.List<String> gradleArguments(String... arguments) {
        var result = new java.util.ArrayList<>(java.util.List.of(arguments));
        String configured = System.getProperty("copperbench.stage17.gradleProxy", "");
        if (!configured.isBlank()) {
            var proxy = java.net.URI.create(configured);
            assertEquals("http", proxy.getScheme());
            assertTrue(java.util.Set.of("localhost", "127.0.0.1").contains(proxy.getHost()));
            assertTrue(proxy.getPort() > 0 && proxy.getPort() <= 65535);
            assertNull(proxy.getUserInfo()); assertNull(proxy.getQuery()); assertNull(proxy.getFragment());
            assertTrue(proxy.getPath().isEmpty());
            for (String protocol : java.util.List.of("http", "https")) {
                result.add("-D" + protocol + ".proxyHost=" + proxy.getHost());
                result.add("-D" + protocol + ".proxyPort=" + proxy.getPort());
            }
        }
        return result;
    }

    @ParameterizedTest @ValueSource(strings = {"adopt_definition", "reapply_declared"})
    void legacyDriftRequiresExplicitChoiceAndRejectsChangedSources(String mode) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("legacy_forge");
        settings.setModName("Legacy Forge"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        UUID workspaceId = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("legacy_forge.mcreator").toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            try (MCreatorWorkspaceSession session = session(workspace, workspaceId)) {
                JsonObject payload = JsonParser.parseString("""
                    {"elementType":"block","name":"legacy_forge","initialValues":{"hardness":3}}
                    """).getAsJsonObject();
                var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload));
                assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
            }
            // Freeze the original defect: successful product metadata claims 3, durable definition contains 0.
            var block = (Block) workspace.getModElementByName("legacy_forge").getGeneratableElement();
            block.hardness = 0;
            workspace.getModElementManager().storeModElement(block);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            Path definition = root.resolve("elements/legacy_forge.mod.json");
            String before = Files.readString(definition);
            try (MCreatorWorkspaceSession session = session(workspace, workspaceId)) {
                String id = workspace.getModElementByName("legacy_forge").getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA).toString();
                JsonObject query = new JsonObject(); query.addProperty("elementId", id);
                var editor = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_MOD_ELEMENT_EDITOR, query));
                JsonObject configuration = editor.data().getAsJsonObject().getAsJsonObject("configuration");
                assertEquals("drift", configuration.get("status").getAsString());
                assertEquals(0, configuration.getAsJsonObject("effectiveValues").get("hardness").getAsDouble());
                assertEquals(3, configuration.getAsJsonObject("declaredValues").get("hardness").getAsDouble());
                assertEquals(before, Files.readString(definition), "Reading must not repair legacy data");
                JsonObject chosen = configuration.getAsJsonObject("planOperations").getAsJsonObject("reapply_declared").getAsJsonObject("payload");
                Files.writeString(definition, before + "\n");
                var stale = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, chosen));
                assertEquals("rejected", stale.result().status());
                assertEquals("SOURCE_CONTENT_CONFLICT", stale.result().diagnostics().getFirst().code());
                editor = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_MOD_ELEMENT_EDITOR, query));
                JsonObject operation = editor.data().getAsJsonObject().getAsJsonObject("configuration").getAsJsonObject("planOperations")
                        .getAsJsonObject(mode);
                JsonArray operations = new JsonArray(); operations.add(operation);
                JsonObject planning = new JsonObject(); planning.add("operations", operations);
                planning.addProperty("expectedRevision", 1); planning.addProperty("idempotencyKey", "resolve-legacy");
                var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.PLAN_WORKSPACE_CHANGES, planning));
                assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
                JsonObject apply = new JsonObject(); apply.add("plan", planned.data());
                var applied = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.APPLY_WORKSPACE_PLAN, apply));
                assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
                if (mode.equals("adopt_definition")) assertEquals(before + "\n", Files.readString(definition), "Adoption does not regenerate files");
                else assertEquals(3, ((Block) workspace.getModElementByName("legacy_forge").getGeneratableElement()).hardness);
                var replay = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.APPLY_WORKSPACE_PLAN, apply));
                assertEquals("committed", replay.result().status(), replay.result().diagnostics().toString());
                assertEquals(2, replay.result().newRevision(), "Replaying the same resolution must not advance revision");
                editor = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_MOD_ELEMENT_EDITOR, query));
                assertEquals("consistent", editor.data().getAsJsonObject().getAsJsonObject("configuration").get("status").getAsString());
            }
        }
    }

    private MCreatorWorkspaceSession session(Workspace workspace, UUID id) throws Exception {
        return MCreatorWorkspaceSession.attach(workspace, id,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }

    @Test void latePersistenceFailureRestoresDefinitionGeneratedSourcesAndRevision() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("rollback_forge");
        settings.setModName("Rollback Forge"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        UUID workspaceId = UUID.randomUUID();
        var injectFailure = new java.util.concurrent.atomic.AtomicBoolean();
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("rollback_forge.mcreator").toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID,
                    java.util.List.of((w, before, after, operation, element) -> {
                        if (injectFailure.get()) throw new java.io.IOException("injected post-generation persistence failure");
                    }))) {
                JsonObject create = JsonParser.parseString("{\"elementType\":\"block\",\"name\":\"rollback_forge\",\"initialValues\":{\"hardness\":3}}").getAsJsonObject();
                var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, create));
                assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
                var before = sourceFiles();
                injectFailure.set(true);
                JsonObject update = new JsonObject();
                update.add("elementId", created.result().data().getAsJsonObject().getAsJsonObject("element").get("id"));
                update.add("changes", JsonParser.parseString("[{\"path\":\"/hasInventory\",\"value\":true},{\"path\":\"/inventorySize\",\"value\":3},{\"path\":\"/hardness\",\"value\":5}]"));
                var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update));
                assertEquals("rejected", rejected.result().status()); assertEquals(1, rejected.result().newRevision());
                var after = sourceFiles(); assertEquals(before.keySet(), after.keySet());
                before.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path), path.toString()));
                Block block = (Block) workspace.getModElementByName("rollback_forge").getGeneratableElement();
                assertEquals(3, block.hardness); assertFalse(block.hasInventory);
            }
        }
    }

    private java.util.Map<Path, byte[]> sourceFiles() throws Exception {
        var result = new java.util.LinkedHashMap<Path, byte[]>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.startsWith("src/") || relative.startsWith("elements/") || relative.endsWith(".mcreator"))
                    result.put(root.relativize(path), Files.readAllBytes(path));
            }
        }
        return result;
    }
}
