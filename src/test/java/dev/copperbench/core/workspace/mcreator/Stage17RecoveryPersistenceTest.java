package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.history.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class Stage17RecoveryPersistenceTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

    @ParameterizedTest
    @ValueSource(strings = {"none", "reload", "revision"})
    void restoreAndInjectedFailuresKeepDiskAndCoreConsistent(String failureStage) throws Exception {
        var settings = new WorkspaceSettings("recovery_contract"); settings.setModName("Recovery Contract");
        settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        UUID workspaceId = UUID.randomUUID(); Path document = root.resolve("recovery_contract.mcreator");
        AtomicBoolean armed = new AtomicBoolean();
        long finalRevision;
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             JGitLocalHistoryService history = JGitLocalHistoryService.open(root, Clock.systemUTC())) {
            var metadata = workspace.getFileManager().loadOrCreateProductMetadata(workspaceId);
            var mapper = new MCreatorWorkspaceStateMapper(); var store = new RevisionedWorkspaceStore();
            store.register(mapper.map(workspace, metadata));
            var real = new MCreatorWorkspaceMutationGateway(workspace, workspaceId);
            WorkspaceMutationGateway mutations = new WorkspaceMutationGateway() {
                @Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation, WorkspaceState.Element element) throws Exception {
                    real.persist(before, after, operation, element);
                }
                @Override public void persistRestoredRevision(WorkspaceState restored, long revision) throws Exception {
                    real.persistRestoredRevision(restored, revision);
                    if (failureStage.equals("revision") && armed.getAndSet(false)) throw new java.io.IOException("Injected failure after durable revision write");
                }
            };
            WorkspaceStateReloader reloader = id -> {
                workspace.reloadFromFileSystem();
                if (failureStage.equals("reload") && armed.getAndSet(false)) throw new java.io.IOException("Injected failure after filesystem restore and workspace reload");
                return mapper.map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(id));
            };
            var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
                    mutations, history, reloader, ignored -> root, Clock.systemUTC(), UUID::randomUUID);
            String blockId = create(service, workspaceId, 0, "block", "recovery_block", "{\"hardness\":3,\"resistance\":6}");
            String initial = "package net.mcreator.recovery_contract; public class manual_probe { public static final int VALUE = 1; }\n";
            JsonObject codeValues = new JsonObject(); codeValues.addProperty("code", initial);
            String codeId = create(service, workspaceId, 1, "code", "manual_probe", codeValues.toString());
            Path source = workspace.getModElementByName("manual_probe").getAssociatedFiles().getFirst().toPath();
            JsonObject pointPayload = new JsonObject(); pointPayload.addProperty("label", "Recover actual definitions and manual source");
            var point = command(service, workspaceId, 2, Operation.CREATE_RECOVERY_POINT, pointPayload);
            assertEquals("committed", point.status()); assertEquals(2, point.newRevision());
            String pointId = point.recoveryPointId();
            update(service, workspaceId, 2, blockId, "/hardness", new JsonPrimitive(5));
            update(service, workspaceId, 3, codeId, "/code", new JsonPrimitive(initial.replace("= 1", "= 2")));
            create(service, workspaceId, 4, "function", "after_point", "{\"commands\":[]}");
            Path ignored = root.resolve(".copperbench/restore-test-marker.txt"); Files.writeString(ignored, "Never restored by history");
            Path definition = root.resolve("elements/recovery_block.mod.json"), added = root.resolve("elements/after_point.mod.json");
            Map<Path, byte[]> changedBytes = new LinkedHashMap<>();
            for (Path file : List.of(document, definition, source, added)) changedBytes.put(file, Files.readAllBytes(file));
            JsonObject payload = new JsonObject(); payload.addProperty("recoveryPointId", pointId); payload.addProperty("userApproved", true);
            armed.set(!failureStage.equals("none"));
            var restored = command(service, workspaceId, 5, Operation.RESTORE_RECOVERY_POINT, payload);
            assertEquals("Never restored by history", Files.readString(ignored));
            assertTrue(workspace.getModElementByName("manual_probe").isCodeLocked());
            if (!failureStage.equals("none")) {
                assertEquals("rejected", restored.status()); assertEquals("RECOVERY_POINT_RESTORE_FAILED", restored.diagnostics().getFirst().code());
                assertEquals(5, restored.newRevision()); assertEquals(5, store.read(workspaceId).orElseThrow().revision());
                changedBytes.forEach((path, bytes) -> { try { assertArrayEquals(bytes, Files.readAllBytes(path), path.toString()); } catch (java.io.IOException error) { throw new AssertionError(error); } });
                assertEquals(5, ((net.mcreator.element.types.Block) workspace.getModElementByName("recovery_block").getGeneratableElement()).hardness);
                finalRevision = 5;
            } else {
                assertEquals("committed", restored.status(), restored.diagnostics().toString()); assertEquals(6, restored.newRevision());
                assertEquals(initial, Files.readString(source)); assertFalse(Files.exists(added));
                assertEquals(3, ((net.mcreator.element.types.Block) workspace.getModElementByName("recovery_block").getGeneratableElement()).hardness);
                assertNull(workspace.getModElementByName("after_point"));
                var safety = history.listRecoveryPoints().stream().filter(p -> p.source() == RecoveryPointSource.RESTORE_SAFETY).findFirst().orElseThrow();
                payload.addProperty("recoveryPointId", safety.id());
                var undone = command(service, workspaceId, 6, Operation.RESTORE_RECOVERY_POINT, payload);
                assertEquals("committed", undone.status(), undone.diagnostics().toString()); assertEquals(7, undone.newRevision());
                assertArrayEquals(changedBytes.get(source), Files.readAllBytes(source)); assertArrayEquals(changedBytes.get(definition), Files.readAllBytes(definition));
                assertArrayEquals(changedBytes.get(added), Files.readAllBytes(added)); finalRevision = 7;
            }
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            var metadata = reopened.getFileManager().loadOrCreateProductMetadata(workspaceId);
            assertEquals(finalRevision, new MCreatorWorkspaceStateMapper().map(reopened, metadata).revision());
            assertEquals(5, ((net.mcreator.element.types.Block) reopened.getModElementByName("recovery_block").getGeneratableElement()).hardness);
            assertTrue(reopened.getModElementByName("manual_probe").isCodeLocked()); assertNotNull(reopened.getModElementByName("after_point"));
        }
    }
    private static CommandResult command(WorkspaceApplicationService service, UUID id, long revision, Operation operation, JsonObject payload) {
        return service.execute(Command.of(UUID.randomUUID(), id, revision, operation, payload), UI).result();
    }
    private static String create(WorkspaceApplicationService service, UUID id, long revision, String type, String name, String values) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", name); payload.add("initialValues", JsonParser.parseString(values));
        var result = command(service, id, revision, Operation.CREATE_MOD_ELEMENT, payload);
        assertEquals("committed", result.status(), result.diagnostics().toString());
        return result.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
    }
    private static void update(WorkspaceApplicationService service, UUID id, long revision, String elementId, String path, JsonElement value) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementId", elementId); JsonArray changes = new JsonArray();
        JsonObject change = new JsonObject(); change.addProperty("path", path); change.add("value", value); changes.add(change); payload.add("changes", changes);
        var result = command(service, id, revision, Operation.UPDATE_MOD_ELEMENT, payload); assertEquals("committed", result.status(), result.diagnostics().toString());
    }
}
