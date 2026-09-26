package dev.copperbench.core;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage17ModelingWorkflowTest {
    @TempDir Path root;

    @Test void selectedElementDefaultsPersistAndRealImportBindingRemainSeparate() throws Exception {
        Clock clock = Clock.systemUTC();
        UUID workspaceId = UUID.randomUUID(), elementId = UUID.randomUUID(), taskId = UUID.randomUUID();
        var store = new RevisionedWorkspaceStore();
        var generator = new JsonObject();
        generator.addProperty("id", "fabric-1.21.1");
        generator.addProperty("loader", "fabric");
        generator.addProperty("minecraftVersion", "1.21.1");
        var element = new WorkspaceState.Element(elementId, "block", "lamp", "Lamp", "valid", "generated", clock.instant(), new JsonObject());
        store.register(new WorkspaceState(workspaceId, "test", "mod", 0, false, generator, new JsonObject(), List.of(element)));
        var actor = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
        try (var history = JGitLocalHistoryService.open(root, clock)) {
            var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID),
                    WorkspaceMutationGateway.noOp(), history, null, id -> root, clock, UUID::randomUUID);
            var begin = new JsonObject();
            begin.addProperty("taskId", taskId.toString()); begin.addProperty("elementId", elementId.toString());
            var started = service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.BEGIN_BLOCKBENCH_TASK, begin), actor).result();
            assertEquals("completed", started.status(), started.diagnostics().toString());
            var task = started.data().getAsJsonObject();
            assertEquals("models/blockbench/lamp.bbmodel", task.get("targetRelativePath").getAsString());
            assertEquals("test:custom/lamp", task.getAsJsonObject("elementContext").get("modelResource").getAsString());
            assertEquals("src/main/resources/assets/test/textures/block", task.getAsJsonObject("elementContext").get("textureDirectory").getAsString());
            Path edit = Path.of(task.get("editPath").getAsString());
            assertEquals(edit, service.blockbenchEditLifecycle(workspaceId).modelingEdit(taskId));
            Files.writeString(edit, "{\"meta\":{\"model_format\":\"java_block\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16]}],\"textures\":[]}");
            var get = new JsonObject(); get.addProperty("taskId", taskId.toString());
            var staleFinish = get.deepCopy(); staleFinish.add("savedSha256", task.get("editSha256"));
            var stale = service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.FINISH_BLOCKBENCH_TASK, staleFinish), actor).result();
            assertEquals("MODEL_EDIT_CHANGED", stale.diagnostics().getFirst().code());
            assertEquals("diagnostic.model_edit_changed", stale.diagnostics().getFirst().message().key());
            assertTrue(stale.diagnostics().getFirst().message().args().get("detail").getAsString().contains("refresh"));
            var refreshed = service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_BLOCKBENCH_TASK, get), actor).data().getAsJsonObject();
            assertEquals("editing", refreshed.get("state").getAsString());
            var finish = get.deepCopy(); finish.add("savedSha256", refreshed.get("editSha256"));
            assertEquals("completed", service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.FINISH_BLOCKBENCH_TASK, finish), actor).result().status());
            Files.writeString(edit.getParent().resolve("game.json"), "{\"textures\":{\"all\":\"test:block/lamp\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"texture\":\"#all\"}}}]}");
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, 2), "png", edit.getParent().resolve("lamp.png").toFile());
            var previewPayload = get.deepCopy(); previewPayload.addProperty("elementId", elementId.toString());
            Path gameExport = edit.getParent().resolve("game.json");
            String validExport = Files.readString(gameExport);
            Files.writeString(gameExport, validExport.replace("test:block/lamp", "test:lamp"));
            var atlasRejected = service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.PREVIEW_BLOCKBENCH_IMPORT, previewPayload), actor);
            assertNotEquals("succeeded", atlasRejected.status());
            assertEquals("MODEL_TEXTURE_ATLAS_PATH", atlasRejected.diagnostics().getFirst().code());
            assertEquals("diagnostic.model_texture_atlas_path", atlasRejected.diagnostics().getFirst().message().key());
            assertTrue(atlasRejected.diagnostics().getFirst().message().args().get("detail").getAsString().contains("test:lamp"));
            Files.writeString(gameExport, validExport);
            var preview = service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.PREVIEW_BLOCKBENCH_IMPORT, previewPayload), actor);
            assertEquals("succeeded", preview.status(), preview.diagnostics().toString());
            var apply = get.deepCopy(); apply.add("planToken", preview.data().getAsJsonObject().get("planToken")); apply.addProperty("confirmReplace", false);
            var imported = service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.IMPORT_BLOCKBENCH_TASK, apply), actor).result();
            assertEquals("committed", imported.status(), imported.diagnostics().toString());
            assertFalse(store.read(workspaceId).orElseThrow().element(elementId).values().has("modelResource"));
            assertEquals("unbound", service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_BLOCKBENCH_TASK, get), actor).data().getAsJsonObject().getAsJsonObject("binding").get("state").getAsString());
            var bind = get.deepCopy(); bind.addProperty("elementId", elementId.toString()); bind.addProperty("modelResource", "test:custom/lamp");
            var bound = service.execute(Command.of(UUID.randomUUID(), workspaceId, imported.newRevision(), Operation.BIND_BLOCKBENCH_MODEL, bind), actor).result();
            assertEquals("committed", bound.status(), bound.diagnostics().toString());
            var reopened = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID),
                    WorkspaceMutationGateway.noOp(), history, null, id -> root, clock, UUID::randomUUID);
            var tasks = reopened.query(Query.of(UUID.randomUUID(), workspaceId, Operation.LIST_BLOCKBENCH_TASKS, new JsonObject()), actor).data().getAsJsonObject().getAsJsonArray("tasks");
            assertEquals(1, tasks.size());
            assertEquals("bound", tasks.get(0).getAsJsonObject().getAsJsonObject("binding").get("state").getAsString());
            assertEquals(elementId.toString(), tasks.get(0).getAsJsonObject().getAsJsonObject("elementContext").get("elementId").getAsString());
            Path evidence = Files.createDirectories(Path.of("build/stage17-modeling-contract-projections", taskId.toString()));
            Files.writeString(evidence.resolve("editing.json"), refreshed.toString());
            Files.writeString(evidence.resolve("imported-bound.json"), tasks.get(0).toString());
            System.out.println("STAGE17_MODELING_CONTRACT_EVIDENCE=" + evidence.toAbsolutePath());
            assertEquals("completed", reopened.execute(Command.of(UUID.randomUUID(), workspaceId, bound.newRevision(), Operation.BIND_BLOCKBENCH_MODEL, bind), actor).result().status());
        }
    }

    @Test void handwrittenTargetIsRejectedBeforeCreatingTaskFiles() throws Exception {
        Clock clock = Clock.systemUTC();
        UUID workspaceId = UUID.randomUUID(), elementId = UUID.randomUUID(), taskId = UUID.randomUUID();
        var store = new RevisionedWorkspaceStore();
        var generator = new JsonObject(); generator.addProperty("id", "fabric-1.21.1");
        generator.addProperty("loader", "fabric"); generator.addProperty("minecraftVersion", "1.21.1");
        store.register(new WorkspaceState(workspaceId, "test", "mod", 0, false, generator, new JsonObject(),
                List.of(new WorkspaceState.Element(elementId, "item", "handwritten", "Handwritten", "valid", "manual", clock.instant(), new JsonObject()))));
        try (var history = JGitLocalHistoryService.open(root, clock)) {
            var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID),
                    WorkspaceMutationGateway.noOp(), history, null, id -> root, clock, UUID::randomUUID);
            var payload = new JsonObject(); payload.addProperty("taskId", taskId.toString()); payload.addProperty("elementId", elementId.toString());
            var result = service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.BEGIN_BLOCKBENCH_TASK, payload), new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)).result();
            assertEquals("rejected", result.status(), result.diagnostics().toString());
            assertEquals("MODEL_BINDING_UNSUPPORTED", result.diagnostics().getFirst().code());
            assertFalse(Files.exists(root.resolve(".copperbench/modeling-tasks/" + taskId)));
        }
    }
}
