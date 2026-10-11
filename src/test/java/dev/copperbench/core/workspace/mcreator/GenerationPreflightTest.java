package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.GenerationPreflight;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real Fabric 1.21.1 adapter/filesystem tests; no Gradle or Minecraft process is used. */
class GenerationPreflightTest {
    @TempDir Path directory;
    private Path root;
    private static final String MAIN = "src/main/java/net/mcreator/preflight/PreflightMod.java";
    private static final String BLOCK = "src/main/java/net/mcreator/preflight/block/check_blockBlock.java";

    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    private Workspace cold() throws Exception {
        root = directory.resolve("workspace");
        var settings = new WorkspaceSettings("preflight");
        settings.setCurrentGenerator("fabric-1.21.1"); settings.setModName("Preflight"); settings.setVersion("1.0.0");
        Workspace workspace = Workspace.createWorkspace(root.resolve("preflight.mcreator").toFile(), settings);
        assertTrue(workspace.getGenerator().generateBase());
        Files.writeString(root.resolve("build.gradle"), "// Guard test: never run Gradle\n");
        Files.createDirectories(root.resolve("gradle/wrapper"));
        Files.write(root.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[0]);
        workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        return workspace;
    }

    private MCreatorWorkspaceSession attach(Workspace workspace) throws IOException {
        return MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }

    private void block(MCreatorWorkspaceSession session) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", "block"); payload.addProperty("name", "check_block");
        JsonObject values = new JsonObject(); values.addProperty("hardness", 4); payload.add("initialValues", values);
        var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                Operation.CREATE_MOD_ELEMENT, payload)).result();
        assertEquals("committed", result.status(), result.diagnostics().toString());
    }

    private JsonObject preview(MCreatorWorkspaceSession session) throws IOException {
        Query query = Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_GENERATION, new JsonObject());
        var result = session.headlessEntry(PermissionProfile.READ_ONLY).query(query);
        assertEquals("succeeded", result.status(), result.diagnostics().toString());
        assertEquals(result.revision(), result.data().getAsJsonObject().get("revision").getAsLong());
        Path evidence = Path.of("build/reports/generation-preflight", session.workspaceId() + ".json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, dev.copperbench.core.contract.UiCore.wireGson().toJson(result));
        return result.data().getAsJsonObject();
    }

    private WorkspaceState state(Workspace workspace, MCreatorWorkspaceSession session) throws IOException {
        return new MCreatorWorkspaceStateMapper().map(workspace,
                workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
    }

    private WorkspaceTaskGateway.GenerationPreparationException rejected(Workspace workspace, MCreatorWorkspaceSession session) throws Exception {
        WorkspaceState state = state(workspace, session);
        var store = new RevisionedWorkspaceStore(); store.register(state);
        var preparation = new MCreatorGenerationPreparation(workspace, store, ignored -> {
            fail("Source rejection must precede dependency preparation"); return null;
        });
        Map<String, String> before = inventory();
        var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                () -> preparation.prepare(state, root, Operation.GENERATE_WORKSPACE,
                        ignored -> fail("No process or generation output before rejection")));
        assertEquals(before, inventory(), "A rejected plan must not even restore mcreator.gradle");
        assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
        return failure;
    }

    private Map<String, String> inventory() throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                String relative = root.relativize(path).toString();
                if (path.equals(root.resolve(".copperbench/workspace.write.lock")))
                    result.put(relative, "active-writer-lease:" + Files.size(path));
                else if (Files.isSymbolicLink(path)) result.put(relative, "link:" + Files.readSymbolicLink(path));
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) result.put(relative, WorkspaceExecutionSnapshot.sha256(path));
                else result.put(relative, "directory");
            }
        }
        return result;
    }

    private JsonObject conflict(JsonObject result, String reason, String path) {
        for (var item : result.getAsJsonArray("conflicts")) {
            JsonObject conflict = item.getAsJsonObject();
            if (reason.equals(conflict.get("reasonCode").getAsString())
                    && Objects.equals(path, conflict.get("relativePath").isJsonNull() ? null : conflict.get("relativePath").getAsString()))
                return conflict;
        }
        fail("Missing " + reason + " at " + path + ": " + result);
        return null;
    }

    @Test void readOnlyAdaptersShareAStablePlanWithoutFilesRevisionOrDependenciesChanging() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            Map<String, String> before = inventory();
            JsonObject result = preview(session);
            assertEquals("ready", result.get("status").getAsString(), result.toString());
            Query query = Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_GENERATION, new JsonObject());
            assertEquals(result, session.mcpEntry(PermissionProfile.READ_ONLY).query(query).data());
            assertEquals(result, session.uiEntry().query(query).data());
            assertEquals(1, result.get("revision").getAsLong());
            assertTrue(result.get("dependenciesRequired").getAsBoolean());
            assertTrue(result.get("executionRechecksInputs").getAsBoolean());
            assertEquals(WorkspaceExecutionSnapshot.fingerprint(root, () -> false), result.get("inputFingerprint").getAsString());
            assertTrue(result.getAsJsonArray("managedPaths").asList().stream().anyMatch(path -> path.getAsString().equals(BLOCK)));
            assertEquals(before, inventory());
            assertNull(workspace.getGenerator().getGradleCache());
            assertFalse(Files.exists(root.resolve("mcreator.gradle")));
            assertFalse(Files.exists(root.resolve(BLOCK)));
        }
    }

    @Test void unownedBaseAndElementFilesAreListedTogetherAndExecutionKeepsTheirBytes() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            workspace.putMetadata(MCreatorGenerationPreparation.PENDING, null);
            workspace.putMetadata("files", List.of());
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            Files.createDirectories(root.resolve(BLOCK).getParent());
            Files.writeString(root.resolve(BLOCK), "// handwritten block\n");
            Map<String, String> before = inventory();
            JsonObject result = preview(session);
            assertEquals("conflicted", result.get("status").getAsString());
            assertEquals("unowned", conflict(result, "UNOWNED_BASE_FILE", MAIN).get("observedOwnership").getAsString());
            conflict(result, "UNOWNED_ELEMENT_FILE", BLOCK);
            assertTrue(result.get("conflictCount").getAsInt() >= 2);
            var failure = rejected(workspace, session);
            assertEquals("GENERATION_SOURCE_CONFLICT", failure.code());
            assertEquals(result.get("conflicts"), failure.details().get("conflicts"));
            assertNotNull(failure.getCause());
            assertEquals(before, inventory());
        }
    }

    @Test void aSuccessfulPreviewIsNotALockAndDoesNotAuthorizeLaterSourceChanges() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            JsonObject accepted = preview(session);
            assertEquals("ready", accepted.get("status").getAsString());
            Files.writeString(root.resolve(MAIN), Files.readString(root.resolve(MAIN)) + "\n// external change\n");
            JsonObject changed = preview(session);
            conflict(changed, "SOURCE_CHANGED", MAIN);
            assertNotEquals(accepted.get("inputFingerprint"), changed.get("inputFingerprint"));
            conflict(rejected(workspace, session).details(), "SOURCE_CHANGED", MAIN);
        }
    }

    @Test void userCodeContentsRemainAllowedWhileBrokenBoundariesAreLocalized() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            String source = Files.readString(root.resolve(MAIN));
            String end = "// End of user code block mod init";
            assertTrue(source.contains(end));
            Files.writeString(root.resolve(MAIN), source.replace(end, "int authored = 7;\n\t\t" + end));
            assertEquals("ready", preview(session).get("status").getAsString());
            Files.writeString(root.resolve(MAIN), source.replace(end, "// End of user code block unexpected"));
            conflict(preview(session), "USER_CODE_BOUNDARY_INVALID", MAIN);
            conflict(rejected(workspace, session).details(), "USER_CODE_BOUNDARY_INVALID", MAIN);
        }
    }

    @Test void escapingOwnershipIsReportedWithoutExposingOrTouchingAnOutsidePath() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            Path outside = directory.resolve("outside.java"); Files.writeString(outside, "// retained\n");
            workspace.getModElementByName("check_block").putMetadata("files", List.of("../outside.java"));
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            JsonObject result = preview(session);
            conflict(result, "PATH_OUTSIDE_WORKSPACE", null);
            assertFalse(result.toString().contains("../outside.java"));
            assertFalse(result.toString().contains(outside.toString()));
            conflict(rejected(workspace, session).details(), "PATH_OUTSIDE_WORKSPACE", null);
            assertEquals("// retained\n", Files.readString(outside));
        }
    }

    @Test void linkedSourceIsNeverFollowedByPreviewOrExecution() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            Path outside = directory.resolve("outside.java"); Files.writeString(outside, "// linked source\n");
            Path link = root.resolve(BLOCK); Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, outside);
            try {
                conflict(preview(session), "UNSAFE_PATH", BLOCK);
                conflict(rejected(workspace, session).details(), "UNSAFE_PATH", BLOCK);
                assertEquals("// linked source\n", Files.readString(outside));
            } finally { Files.delete(link); }
        }
    }

    @Test void conflictsAndPathsHaveExplicitTruncationWithAccurateTotals() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            List<String> owned = new ArrayList<>();
            for (int i = 0; i < GenerationPreflight.MAX_PATHS + 3; i++) owned.add("src/main/resources/missing_" + i + ".txt");
            workspace.putMetadata("files", owned);
            JsonObject pending = new JsonObject(), files = new JsonObject();
            for (int i = 0; i < GenerationPreflight.MAX_CONFLICTS + 5; i++) {
                String path = "src/main/resources/conflict_" + i + ".txt";
                Files.createDirectories(root.resolve(path).getParent()); Files.writeString(root.resolve(path), "retained");
                files.addProperty(path, "absent");
            }
            pending.add("files", files); pending.add("elements", new JsonArray());
            workspace.putMetadata(MCreatorGenerationPreparation.PENDING, pending);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            JsonObject result = preview(session);
            assertEquals(GenerationPreflight.MAX_CONFLICTS, result.getAsJsonArray("conflicts").size());
            assertTrue(result.get("conflictCount").getAsInt() >= GenerationPreflight.MAX_CONFLICTS + 5);
            assertTrue(result.get("conflictsTruncated").getAsBoolean());
            assertEquals(GenerationPreflight.MAX_PATHS, result.getAsJsonArray("managedPaths").size());
            assertTrue(result.get("managedPathCount").getAsInt() > GenerationPreflight.MAX_PATHS);
            assertTrue(result.get("managedPathsTruncated").getAsBoolean());
        }
    }

    @Test void malformedMetadataAndUnavailableAdaptersDoNotClaimAReadyPlan() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            workspace.putMetadata("files", "broken ownership");
            JsonObject result = preview(session);
            assertNotEquals("ready", result.get("status").getAsString());
            assertEquals("OWNERSHIP_METADATA_INVALID", result.get("reasonCode").getAsString());
            var unknown = WorkspaceMutationGateway.noOp().previewGeneration(state(workspace, session));
            assertEquals("unknown", unknown.get("status").getAsString());
            assertTrue(unknown.get("inputFingerprint").isJsonNull());
            workspace.putMetadata("files", List.of());
            workspace.putMetadata(MCreatorGenerationPreparation.PENDING, Map.of("files", 7));
            result = preview(session);
            assertNotEquals("ready", result.get("status").getAsString());
            assertEquals("PENDING_METADATA_INVALID", result.get("reasonCode").getAsString());
        }
    }

    @Test void anOlderFormatWithNoApplicableConverterRemainsReadableWithoutSaving() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            workspace.putMetadata(MCreatorGenerationPreparation.PENDING, null);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            Path definition = root.resolve("elements/check_block.mod.json");
            JsonObject compatible = com.google.gson.JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
            // Block converters currently end at FV84; later format changes concern other types.
            compatible.addProperty("_fv", 84);
            Files.writeString(definition, compatible.toString());
            workspace.getModElementManager().invalidateCache();
            Map<String, String> before = inventory();
            assertEquals("ready", preview(session).get("status").getAsString());
            assertEquals(before, inventory());
        }
    }

    @Test void oldDefinitionsAreNotConvertedOrSavedByPreviewOrRejectedPreparation() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            WorkspaceState state = state(workspace, session);
            Path definition = root.resolve("elements/check_block.mod.json");
            JsonObject old = com.google.gson.JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
            old.addProperty("_fv", 0);
            Files.writeString(definition, old.toString());
            workspace.getModElementManager().invalidateCache();
            Map<String, String> before = inventory();
            JsonObject result = preview(session);
            assertEquals("unknown", result.get("status").getAsString());
            assertEquals("ELEMENT_CONVERSION_REQUIRED", result.get("reasonCode").getAsString());
            assertEquals(before, inventory());
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                    () -> new MCreatorGenerationPreparation(workspace, store).prepare(state, root, Operation.GENERATE_WORKSPACE,
                            ignored -> fail("Legacy definitions require a separate conversion decision")));
            assertEquals("GENERATION_PREFLIGHT_UNAVAILABLE", failure.code());
            assertEquals(before, inventory());
        }
    }

    @Test void realTaskFailureExposesTheSameConflictWithoutRunningGradle() throws Exception {
        try (Workspace workspace = cold()) {
            workspace.putMetadata("files", List.of());
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            try (var session = MCreatorWorkspaceSession.attach(workspace,
                    store -> new dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway(store,
                            ignored -> root, Path.of(".").toAbsolutePath(), Clock.systemUTC(), UUID::randomUUID,
                            (folder, arguments, timeout, output) -> { fail("Conflicts must not start Gradle"); return null; }),
                    Clock.systemUTC(), UUID::randomUUID)) {
                JsonObject observation = preview(session);
                conflict(observation, "UNOWNED_BASE_FILE", MAIN);
                byte[] source = Files.readAllBytes(root.resolve(MAIN));
                byte[] definition = Files.readAllBytes(root.resolve("preflight.mcreator"));
                JsonObject payload = new JsonObject(); payload.addProperty("scope", "workspace");
                payload.addProperty("clientMutationId", UUID.randomUUID().toString());
                var accepted = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                        Operation.GENERATE_WORKSPACE, payload)).result();
                assertEquals("accepted", accepted.status(), accepted.diagnostics().toString());
                JsonObject query = new JsonObject(); query.add("taskId", accepted.task().getAsJsonObject().get("id"));
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
                JsonObject result;
                do {
                    result = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_TASK, query)).data().getAsJsonObject();
                    if (Set.of("failed", "succeeded", "cancelled").contains(result.getAsJsonObject("task").get("state").getAsString())) break;
                    Thread.sleep(25);
                } while (System.nanoTime() < deadline);
                assertEquals("failed", result.getAsJsonObject("task").get("state").getAsString(), result.toString());
                JsonObject diagnostic = result.getAsJsonArray("diagnostics").get(0).getAsJsonObject();
                assertEquals("GENERATION_SOURCE_CONFLICT", diagnostic.get("code").getAsString(), result.toString());
                conflict(diagnostic.getAsJsonObject("message").getAsJsonObject("args"), "UNOWNED_BASE_FILE", MAIN);
                String reportedPath = diagnostic.getAsJsonObject("message").getAsJsonObject("args")
                        .getAsJsonArray("conflicts").get(0).getAsJsonObject().get("relativePath").getAsString();
                assertTrue(result.getAsJsonArray("logs").toString().contains(reportedPath));
                assertFalse(result.getAsJsonArray("logs").toString().contains("GENERATOR_DEPENDENCIES_PREPARING"));
                assertArrayEquals(source, Files.readAllBytes(root.resolve(MAIN)));
                assertArrayEquals(definition, Files.readAllBytes(root.resolve("preflight.mcreator")));
                assertFalse(Files.exists(root.resolve("mcreator.gradle")));
            }
        }
    }

    @Test void aDirectoryAtASourceTargetCannotPassAsARegularFile() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            block(session);
            Files.createDirectories(root.resolve(BLOCK));
            conflict(preview(session), "NOT_REGULAR_FILE", BLOCK);
            conflict(rejected(workspace, session).details(), "NOT_REGULAR_FILE", BLOCK);
        }
    }

    @Test void noPayloadCanTurnTheReadIntoAnOwnershipChange() throws Exception {
        try (Workspace workspace = cold(); var session = attach(workspace)) {
            JsonObject payload = new JsonObject(); payload.addProperty("takeOwnership", true);
            Map<String, String> before = inventory();
            var result = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_GENERATION, payload));
            assertNotEquals("succeeded", result.status());
            assertEquals(before, inventory());
        }
    }
}
