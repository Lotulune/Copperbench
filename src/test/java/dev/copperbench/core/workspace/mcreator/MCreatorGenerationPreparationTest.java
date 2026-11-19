package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MCreatorGenerationPreparationTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    private Workspace cold() throws Exception {
        var settings = new WorkspaceSettings("cold_fields"); settings.setCurrentGenerator("fabric-1.21.1");
        settings.setModName("Cold Fields"); settings.setVersion("1.0.0");
        Workspace workspace = Workspace.createWorkspace(root.resolve("cold_fields.mcreator").toFile(), settings);
        assertTrue(workspace.getGenerator().generateBase());
        Files.writeString(root.resolve("build.gradle"), "// Never executed by these guard tests\n");
        Files.createDirectories(root.resolve("gradle/wrapper")); Files.write(root.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[0]);
        workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        return workspace;
    }

    private JsonObject createPayload() {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", "block"); payload.addProperty("name", "cold_block");
        JsonObject values = new JsonObject(); values.addProperty("hardness", 4); payload.add("initialValues", values); return payload;
    }

    @Test void compilerOwnershipUsesActualAssociatedPathsAndNeverSameNameManualFiles() throws Exception {
        try (Workspace workspace = cold(); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            assertEquals("committed", session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                    Operation.CREATE_MOD_ELEMENT, createPayload())).result().status());
            var element = workspace.getModElementByName("cold_block");
            Path generated = root.resolve("src/main/java/actual/Shared.java");
            Path manual = root.resolve("src/main/java/manual/Shared.java");
            for (Path file : List.of(generated, manual)) { Files.createDirectories(file.getParent()); Files.writeString(file, "class Shared {}\n"); }
            element.setAssociatedFiles(List.of(generated.toFile()));
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var preparation = new MCreatorGenerationPreparation(workspace, store);
            var owners = preparation.sourceElementOwners(state, root);
            UUID storedId = UUID.fromString(element.getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA).toString());
            assertNotEquals(MCreatorWorkspaceStateMapper.elementId(state.id(), element), storedId,
                    "Core-created elements use their persisted ID, not the deterministic imported-element fallback");
            assertNotNull(state.element(storedId));
            assertEquals(storedId, owners.get("src/main/java/actual/Shared.java"));
            assertFalse(owners.containsKey("src/main/java/manual/Shared.java"));

            element.putMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA, null);
            var legacyState = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
            UUID legacyId = MCreatorWorkspaceStateMapper.elementId(legacyState.id(), element);
            assertNotNull(legacyState.element(legacyId));
            assertEquals(legacyId, preparation.sourceElementOwners(legacyState, root).get("src/main/java/actual/Shared.java"));
            assertFalse(preparation.sourceElementOwners(legacyState, root).containsKey("src/main/java/manual/Shared.java"));
            element.setCodeLock(true);
            assertTrue(preparation.sourceElementOwners(legacyState, root).isEmpty());
        }
    }

    @Test void coldDefinitionPersistsWithoutRunningGradleOrWritingUnresolvedJavaAndSurvivesReopen() throws Exception {
        try (Workspace workspace = cold(); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var response = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, createPayload()));
            assertEquals("committed", response.result().status(), response.result().diagnostics().toString());
            assertTrue(Files.isRegularFile(root.resolve("elements/cold_block.mod.json")));
            assertFalse(Files.exists(root.resolve("src/main/java/net/mcreator/cold_fields/block/cold_blockBlock.java")));
            assertNotNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
            assertNull(workspace.getGenerator().getGradleCache());
        }
        try (Workspace reopened = Workspace.readFromFS(root.resolve("cold_fields.mcreator").toFile(), null)) {
            assertNotNull(reopened.getMetadata(MCreatorGenerationPreparation.PENDING));
            MCreatorGenerationPreparation.verifyPending(reopened);
            assertEquals(4, ((net.mcreator.element.types.Block) reopened.getModElementByName("cold_block").getGeneratableElement()).hardness);
        }
    }

    @Test void externalFileAtDeferredTargetIsNotOverwrittenAndGetsSpecificTaskDiagnostic() throws Exception {
        try (Workspace workspace = cold(); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var response = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, createPayload()));
            assertEquals("committed", response.result().status());
            Path target = root.resolve("src/main/java/net/mcreator/cold_fields/block/cold_blockBlock.java");
            Files.createDirectories(target.getParent()); Files.writeString(target, "// Externally authored source\n");
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var preparation = new MCreatorGenerationPreparation(workspace, store);
            var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                    () -> preparation.prepare(state, root, Operation.BUILD_WORKSPACE, ignored -> fail("No external setup should start before conflict detection")));
            assertEquals("GENERATION_SOURCE_CONFLICT", failure.code());
            assertEquals("// Externally authored source\n", Files.readString(target));
            assertNotNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
        }
    }

    @Test void failedDefinitionMutationRollsBackItsPendingGenerationMetadata() throws Exception {
        try (Workspace workspace = cold(); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID,
                List.of((opened, before, after, operation, element) -> { throw new java.io.IOException("Injected persistence failure"); }))) {
            var response = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, createPayload()));
            assertNotEquals("committed", response.result().status());
            assertNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
            assertNull(workspace.getModElementByName("cold_block"));
            assertFalse(Files.exists(root.resolve("elements/cold_block.mod.json")));
        }
    }

    @Test void legacyColdBuildStillRejectsUnownedSourceBeforeDependencyPreparation() throws Exception {
        try (Workspace workspace = cold(); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var response = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, createPayload()));
            assertEquals("committed", response.result().status());
            workspace.putMetadata(MCreatorGenerationPreparation.PENDING, null);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            Path target = root.resolve("src/main/java/net/mcreator/cold_fields/block/cold_blockBlock.java");
            Files.createDirectories(target.getParent()); Files.writeString(target, "// Legacy external source\n");
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                    () -> new MCreatorGenerationPreparation(workspace, store).prepare(state, root, Operation.BUILD_WORKSPACE,
                            ignored -> fail("Ownership conflicts must be rejected before dependency setup")));
            assertEquals("GENERATION_SOURCE_CONFLICT", failure.code());
            assertEquals("// Legacy external source\n", Files.readString(target));
        }
    }

    @Test void documentedUserCodeEditsRemainPreservedButGeneratedCodeAndBoundaryEditsAreRejected() throws Exception {
        try (Workspace workspace = cold()) {
            Path main = root.resolve("src/main/java/net/mcreator/cold_fields/ColdFieldsMod.java");
            String original = Files.readString(main);
            String end = "// End of user code block mod init";
            assertTrue(original.contains(end));
            MCreatorGenerationPreparation.recordPending(workspace, List.of(main), List.of());
            String authored = original.replace(end, "stage17_machine_runtime.init();\n\t\t" + end);
            Files.writeString(main, authored);
            MCreatorGenerationPreparation.verifyPending(workspace);
            String regenerated = net.mcreator.generator.usercode.UserCodeProcessor.processUserCode(main.toFile(), original, "//");
            assertTrue(regenerated.contains("stage17_machine_runtime.init();"));
            Files.writeString(main, authored + "\n// Changed generated region\n");
            assertThrows(java.io.IOException.class, () -> MCreatorGenerationPreparation.verifyPending(workspace));
            Files.writeString(main, authored.replace(end, "// End of user code block wrong region"));
            assertThrows(java.io.IOException.class, () -> MCreatorGenerationPreparation.verifyPending(workspace));
        }
    }

    @Test void elementFreeColdBuildPreparesDependenciesBeforeCompilingGeneratedBaseFiles() throws Exception {
        try (Workspace workspace = cold()) {
            // Only hand-written code: no managed elements and no pending generation, but the import index is missing,
            // so the generated base files would compile without their imports.
            assertTrue(MCreatorGenerationPreparation.managedElements(workspace).isEmpty());
            assertNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(UUID.randomUUID()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var logs = new java.util.ArrayList<String>();
            var preparation = new MCreatorGenerationPreparation(workspace, store, javaHome -> (folder, arguments, timeout, output) -> {
                calls.incrementAndGet();
                return new dev.copperbench.generator.fabric.Fabric1211ProcessRunner.ProcessResult(1, false);
            });
            var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                    () -> preparation.prepare(state, root, Operation.BUILD_WORKSPACE, logs::add));
            assertEquals("GENERATOR_DEPENDENCIES_UNAVAILABLE", failure.code());
            assertEquals(1, calls.get(), "the first build requests the dependency setup that indexes imports");
            assertTrue(logs.stream().anyMatch(line -> line.startsWith("GENERATOR_DEPENDENCIES_PREPARING")), logs.toString());
        }
    }

    @Test void buildRestoresMissingGeneratedGradleIncludeWithoutReplacingAnExistingInclude() throws Exception {
        try (Workspace workspace = cold()) {
            // Without a wrapper JAR no dependency setup is possible; only the include is restored.
            Files.delete(root.resolve("gradle/wrapper/gradle-wrapper.jar"));
            var metadata = workspace.getFileManager().loadOrCreateProductMetadata(UUID.randomUUID());
            var state = new MCreatorWorkspaceStateMapper().map(workspace, metadata);
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var preparation = new MCreatorGenerationPreparation(workspace, store);
            Path include = root.resolve("mcreator.gradle");
            assertFalse(Files.exists(include));
            preparation.prepare(state, root, Operation.BUILD_WORKSPACE, ignored -> fail("No dependency setup was requested"));
            assertTrue(Files.isRegularFile(include));
            assertEquals(net.mcreator.gradle.GradleUtils.mcreatorBuildFileContent(workspace), Files.readString(include));
            Files.writeString(include, "// Retained custom include\n");
            preparation.prepare(state, root, Operation.BUILD_WORKSPACE, ignored -> fail("No dependency setup was requested"));
            assertEquals("// Retained custom include\n", Files.readString(include));
            assertNull(workspace.getGenerator().getGradleCache());
        }
    }

    @Test void preparedWorkspaceRegistryEditRefreshesGeneratedLanguageFilesBeforeTheNextBuild() throws Exception {
        try (Workspace workspace = cold()) {
            // Without a wrapper JAR the workspace counts as prepared, like a product-created one after setup.
            Files.delete(root.resolve("gradle/wrapper/gradle-wrapper.jar"));
            Path lang = root.resolve("src/main/resources/assets/cold_fields/lang");
            assertTrue(workspace.getGenerator().generateBase());
            String before = Files.readString(lang.resolve("en_us.json"));
            try (var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                JsonObject payload = com.google.gson.JsonParser.parseString("{\"registry\":\"languageKeys\",\"entry\":{"
                        + "\"key\":\"block.cold_fields.ember\",\"translations\":{\"en_us\":\"Ember\",\"zh_cn\":\"余烬\"}}}").getAsJsonObject();
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                        Operation.CREATE_REGISTRY_ENTRY, payload)).result();
                assertEquals("committed", result.status(), result.diagnostics().toString());
                // The build skips generation for a prepared workspace without pending work, so the edit itself must
                // leave the generator-owned language files current (2026-11-19 native-first comparison, T1).
                var english = com.google.gson.JsonParser.parseString(Files.readString(lang.resolve("en_us.json"))).getAsJsonObject();
                var chinese = com.google.gson.JsonParser.parseString(Files.readString(lang.resolve("zh_cn.json"))).getAsJsonObject();
                assertAll(() -> assertNotEquals(before, Files.readString(lang.resolve("en_us.json"))),
                        () -> assertEquals("Ember", english.get("block.cold_fields.ember").getAsString()),
                        () -> assertEquals("余烬", chinese.get("block.cold_fields.ember").getAsString()));
            }
        }
    }

    @Test void rejectedRegistryEditRestoresTheRegeneratedLanguageFiles() throws Exception {
        try (Workspace workspace = cold()) {
            Files.delete(root.resolve("gradle/wrapper/gradle-wrapper.jar"));
            Path lang = root.resolve("src/main/resources/assets/cold_fields/lang");
            assertTrue(workspace.getGenerator().generateBase());
            byte[] english = Files.readAllBytes(lang.resolve("en_us.json"));
            UUID id = UUID.randomUUID();
            var metadata = workspace.getFileManager().loadOrCreateProductMetadata(id);
            var before = new MCreatorWorkspaceStateMapper().map(workspace, metadata);
            var after = before.copy();
            JsonObject registries = before.registries().deepCopy();
            registries.getAsJsonArray("languageKeys").add(com.google.gson.JsonParser.parseString(
                    "{\"key\":\"block.cold_fields.ember\",\"translations\":{\"en_us\":\"Ember\",\"zh_cn\":\"余烬\"}}"));
            after.replaceRegistries(registries);
            // A durable revision conflict is detected after the language files were regenerated.
            workspace.getFileManager().synchronizeProductRevision(id, metadata.revision() + 1);
            var gateway = new MCreatorWorkspaceMutationGateway(workspace, id);
            Exception failure = assertThrows(Exception.class,
                    () -> gateway.persistWorkspaceData(before, after, Operation.CREATE_REGISTRY_ENTRY));
            assertInstanceOf(dev.copperbench.core.workspace.ProductMetadataManager.RevisionConflictException.class, failure);
            assertArrayEquals(english, Files.readAllBytes(lang.resolve("en_us.json")));
            assertFalse(Files.exists(lang.resolve("zh_cn.json")), "A language file created by the rejected edit is removed");
        }
    }

    @Test void setupFailureClassificationPreservesDeferredDefinitions() throws Exception {
        try (Workspace workspace = cold(); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            assertEquals("committed", session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                    Operation.CREATE_MOD_ELEMENT, createPayload())).result().status());
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            Path definition = root.resolve("elements/cold_block.mod.json");
            byte[] original = Files.readAllBytes(definition);
            var scenarios = new String[][] {
                    {"> Failed to decompile, java.nio.file.FileSystemException: C:\\Users\\tester\\Temp\\loom123ipc: cannot access file", "GENERATOR_LOCAL_IPC_UNAVAILABLE"},
                    {"> Could not download dependency: java.net.ConnectException: Connection reset", "GENERATOR_DEPENDENCIES_UNAVAILABLE"},
                    {"> Task :neoFormDecompile FAILED", "GENERATOR_DECOMPILATION_FAILED"},
                    {"Execution failed for task ':neoFormDecompile'.", "GENERATOR_DECOMPILATION_FAILED"},
                    {"> Task :tools:neoFormDecompile FAILED", "GENERATOR_DECOMPILATION_FAILED"},
                    {"> Task :neoFormDecompile UP-TO-DATE", "GENERATOR_DEPENDENCIES_UNAVAILABLE"},
                    {"> Task :neoFormDecompile", "GENERATOR_DEPENDENCIES_UNAVAILABLE"},
                    {"> Task :neoFormDecompileClasspath FAILED", "GENERATOR_DEPENDENCIES_UNAVAILABLE"}
            };
            for (String[] scenario : scenarios) {
                var calls = new java.util.concurrent.atomic.AtomicInteger();
                var logs = new java.util.ArrayList<String>();
                var preparation = new MCreatorGenerationPreparation(workspace, store, javaHome -> (folder, arguments, timeout, output) -> {
                    calls.incrementAndGet();
                    assertEquals(root.toAbsolutePath(), folder);
                    output.accept("GRADLE_IPC_TCP_FALLBACK: selector fallback was used");
                    output.accept(scenario[0]);
                    return new dev.copperbench.generator.fabric.Fabric1211ProcessRunner.ProcessResult(1, false);
                });
                var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                        () -> preparation.prepare(state, root, Operation.GENERATE_WORKSPACE, logs::add));
                assertEquals(scenario[1], failure.code(), scenario[0]);
                assertEquals(1, calls.get());
                assertTrue(logs.contains(scenario[0]));
                assertArrayEquals(original, Files.readAllBytes(definition));
                assertNotNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
                assertFalse(Files.exists(root.resolve("src/main/java/net/mcreator/cold_fields/block/cold_blockBlock.java")));
            }
        }
    }
}
