package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.ConflictReason;
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
            assertEquals("src/main/java/net/mcreator/cold_fields/block/cold_blockBlock.java", failure.conflicts().getFirst().relativePath());
            assertEquals(ConflictReason.SOURCE_CHANGED, failure.conflicts().getFirst().reason());
            assertEquals("// Externally authored source\n", Files.readString(target));
            assertNotNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
            assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
            assertFalse(Files.exists(root.resolve("mcreator.gradle")));
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
            assertEquals("src/main/java/net/mcreator/cold_fields/block/cold_blockBlock.java", failure.conflicts().getFirst().relativePath());
            assertEquals(ConflictReason.UNOWNED_ELEMENT_FILE, failure.conflicts().getFirst().reason());
            assertEquals("// Legacy external source\n", Files.readString(target));
            assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
            assertFalse(Files.exists(root.resolve("mcreator.gradle")));
        }
    }

    @Test void reportsAllUnownedBaseCandidatesBeforeAnyFileOrRevisionChange() throws Exception {
        try (Workspace workspace = cold()) {
            workspace.putMetadata("files", List.of());
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            var state = new MCreatorWorkspaceStateMapper().map(workspace,
                    workspace.getFileManager().loadOrCreateProductMetadata(UUID.randomUUID()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var candidates = workspace.getGenerator().getModBaseGeneratorTemplatesList().stream()
                    .map(template -> template.getFile().toPath()).filter(Files::isRegularFile)
                    .map(path -> root.toAbsolutePath().relativize(path.toAbsolutePath()).toString().replace('\\', '/'))
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(candidates.size() > 1, "The fixture must exercise more than one conflicting base target");
            var before = fileBytes();
            var failure = assertThrows(GenerationPreparationException.class,
                    () -> new MCreatorGenerationPreparation(workspace, store).prepare(state, root, Operation.GENERATE_WORKSPACE,
                            ignored -> fail("Source conflicts must stop before dependency setup")));
            assertEquals(candidates, failure.conflicts().stream().map(GenerationPreparationException.SourceConflict::relativePath)
                    .collect(java.util.stream.Collectors.toSet()));
            assertTrue(failure.conflicts().stream().allMatch(conflict -> conflict.reason() == ConflictReason.UNOWNED_BASE_FILE));
            assertEquals(before, fileBytes());
            assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
            assertEquals(List.of(), workspace.getMetadata("files"));
        }
    }

    @Test void traversalAndAbsolutePendingPathsAreRejectedWithoutDisclosingTheirLocations() throws Exception {
        try (Workspace workspace = cold()) {
            var state = new MCreatorWorkspaceStateMapper().map(workspace,
                    workspace.getFileManager().loadOrCreateProductMetadata(UUID.randomUUID()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            for (String path : List.of("../private-source.java", "nested/../../private-source.java",
                    root.resolveSibling("private-source.java").toAbsolutePath().toString())) {
                setPending(workspace, path, "absent");
                var before = fileBytes();
                var failure = assertThrows(GenerationPreparationException.class,
                        () -> new MCreatorGenerationPreparation(workspace, store).prepare(state, root, Operation.GENERATE_WORKSPACE,
                                ignored -> fail("Unsafe inputs must stop before setup")));
                assertEquals(ConflictReason.PATH_OUTSIDE_WORKSPACE, failure.conflicts().getFirst().reason());
                assertNull(failure.conflicts().getFirst().relativePath());
                assertFalse(failure.getMessage().contains("private-source"));
                assertEquals(before, fileBytes());
                assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
            }
        }
    }

    @Test void redirectedPendingSourceIsRejectedWithoutReadingItsTarget() throws Exception {
        try (Workspace workspace = cold()) {
            Path link = root.resolve("src/main/java/Redirected.java");
            Files.createDirectories(link.getParent());
            try { Files.createSymbolicLink(link, root.resolve("private-target.java")); }
            catch (java.io.IOException | UnsupportedOperationException denied) {
                org.junit.jupiter.api.Assumptions.abort("This environment cannot create test symlinks");
            }
            setPending(workspace, "src/main/java/Redirected.java", "absent");
            var failure = assertThrows(GenerationPreparationException.class, () -> MCreatorGenerationPreparation.verifyPending(workspace));
            assertEquals(ConflictReason.UNSAFE_PATH, failure.conflicts().getFirst().reason());
            assertEquals("src/main/java/Redirected.java", failure.conflicts().getFirst().relativePath());
            assertFalse(failure.getMessage().contains("private-target"));
        }
    }

    private static void setPending(Workspace workspace, String path, String hash) {
        JsonObject pending = new JsonObject(), files = new JsonObject();
        files.addProperty(path, hash); pending.add("files", files); pending.add("elements", new com.google.gson.JsonArray());
        workspace.putMetadata(MCreatorGenerationPreparation.PENDING, pending);
    }

    private java.util.Map<String, String> fileBytes() throws Exception {
        var result = new java.util.TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList())
                result.put(root.relativize(path).toString(), java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
        }
        return result;
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
            var changed = assertThrows(GenerationPreparationException.class, () -> MCreatorGenerationPreparation.verifyPending(workspace));
            assertEquals(ConflictReason.SOURCE_CHANGED, changed.conflicts().getFirst().reason());
            assertEquals("src/main/java/net/mcreator/cold_fields/ColdFieldsMod.java", changed.conflicts().getFirst().relativePath());
            Files.writeString(main, authored.replace(end, "// End of user code block wrong region"));
            var malformed = assertThrows(GenerationPreparationException.class, () -> MCreatorGenerationPreparation.verifyPending(workspace));
            assertEquals(ConflictReason.MISMATCHED_USER_CODE_REGION, malformed.conflicts().getFirst().reason());
            assertEquals("src/main/java/net/mcreator/cold_fields/ColdFieldsMod.java", malformed.conflicts().getFirst().relativePath());
        }
    }

    @Test void unknownPreparationCauseCannotSupplyAPublicConflictPathOrRawMessage() throws Exception {
        try (Workspace workspace = cold(); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            assertEquals("committed", session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                    Operation.CREATE_MOD_ELEMENT, createPayload())).result().status());
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
            var store = new RevisionedWorkspaceStore(); store.register(state);
            for (String rawMessage : List.of("private-token at /private/source.java",
                    "GENERATION_SOURCE_CONFLICT: private-token at /private/source.java")) {
                var preparation = new MCreatorGenerationPreparation(workspace, store, javaHome -> (folder, arguments, timeout, output) -> {
                    throw new java.io.IOException(rawMessage);
                });
                var failure = assertThrows(GenerationPreparationException.class,
                        () -> preparation.prepare(state, root, Operation.GENERATE_WORKSPACE, ignored -> {}));
                assertEquals(rawMessage.startsWith("GENERATION_SOURCE_CONFLICT:")
                        ? "GENERATION_SOURCE_CONFLICT" : "GENERATOR_SOURCE_PREPARATION_FAILED", failure.code());
                assertTrue(failure.conflicts().isEmpty());
                assertFalse(failure.getMessage().contains("private-token"));
                assertFalse(failure.getMessage().contains("/private"));
            }
        }
    }

    @Test void buildRestoresMissingGeneratedGradleIncludeWithoutReplacingAnExistingInclude() throws Exception {
        try (Workspace workspace = cold()) {
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
