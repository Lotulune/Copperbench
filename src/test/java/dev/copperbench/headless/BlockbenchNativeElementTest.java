package dev.copperbench.headless;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Imported models need a block that the real upstream template engine can persist. */
class BlockbenchNativeElementTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void importedRevisionIsDurableBeforeWorkspaceCloseAndReplaysAfterReopen() throws Exception {
        var settings = new WorkspaceSettings("revision_gate");
        settings.setModName("Revision Gate");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator("fabric-1.21.1");
        Path file = root.resolve("revision_gate.mcreator");
        var clock = Clock.systemUTC();
        UUID taskId = UUID.randomUUID();
        JsonObject importedArguments;
        UUID workspaceId;
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     ignored -> new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID)) {
            workspaceId = session.workspaceId();
            Files.createDirectories(workspace.getGenerator().getSourceRoot().toPath());
            var entry = session.headlessEntry(PermissionProfile.WORKSPACE);
            JsonObject begin = new JsonObject(); begin.addProperty("taskId", taskId.toString());
            begin.addProperty("targetRelativePath", "models/revision_gate.bbmodel");
            var started = entry.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.BEGIN_BLOCKBENCH_TASK, begin)).result();
            assertEquals("completed", started.status());
            Path edit = Path.of(started.data().getAsJsonObject().get("editPath").getAsString());
            Files.writeString(edit, "{\"meta\":{\"model_format\":\"java_block\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16]}],\"textures\":[]}");
            JsonObject id = new JsonObject(); id.addProperty("taskId", taskId.toString());
            var saved = entry.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_BLOCKBENCH_TASK, id));
            JsonObject finish = id.deepCopy(); finish.add("savedSha256", saved.data().getAsJsonObject().get("editSha256"));
            assertEquals("completed", entry.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.FINISH_BLOCKBENCH_TASK, finish)).result().status());
            Files.writeString(edit.resolveSibling("game.json"), "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
            JsonObject preview = id.deepCopy();
            preview.add("outputs", JsonParser.parseString("[{\"sourceRelativePath\":\"game.json\",\"targetRelativePath\":\"src/main/resources/assets/revision_gate/models/custom/lamp.json\"}]"));
            var planned = entry.query(Query.of(UUID.randomUUID(), workspaceId, Operation.PREVIEW_BLOCKBENCH_IMPORT, preview));
            importedArguments = id.deepCopy(); importedArguments.add("planToken", planned.data().getAsJsonObject().get("planToken"));
            var imported = entry.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.IMPORT_BLOCKBENCH_TASK, importedArguments)).result();
            assertEquals("committed", imported.status(), imported.toString());
            assertEquals(1, imported.newRevision());
            assertEquals(1, workspace.getFileManager().loadOrCreateProductMetadata(workspaceId).revision());
            JsonObject create = JsonParser.parseString("{\"elementType\":\"block\",\"name\":\"bound_lamp\",\"initialValues\":{}}").getAsJsonObject();
            var created = entry.execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.CREATE_MOD_ELEMENT, create)).result();
            assertEquals("committed", created.status(), created.toString());
            JsonObject bind = id.deepCopy();
            bind.add("elementId", created.data().getAsJsonObject().getAsJsonObject("element").get("id"));
            bind.addProperty("modelResource", "revision_gate:custom/lamp");
            var bound = entry.execute(Command.of(UUID.randomUUID(), workspaceId, 2, Operation.BIND_BLOCKBENCH_MODEL, bind)).result();
            assertEquals("committed", bound.status(), bound.toString());
            assertEquals(3, bound.newRevision());
        }
        try (Workspace workspace = Workspace.readFromFS(file.toFile(), null);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     ignored -> new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID)) {
            assertEquals(3, workspace.getFileManager().loadOrCreateProductMetadata(workspaceId).revision());
            assertTrue(workspace.getModElementByName("bound_lamp").getMetadata("dev.copperbench.values").toString().contains("revision_gate:custom/lamp"));
            var replay = session.headlessEntry(PermissionProfile.WORKSPACE).execute(
                    Command.of(UUID.randomUUID(), workspaceId, 0, Operation.IMPORT_BLOCKBENCH_TASK, importedArguments)).result();
            assertEquals("completed", replay.status());
            assertEquals(3, replay.newRevision());
            assertTrue(replay.data().getAsJsonObject().get("idempotentReplay").getAsBoolean());
        }
    }

    @Test void emptyBlockValuesCreateAnUpstreamElementReadyForModelBinding() throws Exception {
        var settings = new WorkspaceSettings("blockbench_gate");
        settings.setModName("Blockbench Gate");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator("fabric-1.21.1");
        Path file = root.resolve("blockbench_gate.mcreator");
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings)) {
            assertNotNull(workspace);
            assertNotNull(workspace.getGenerator());
            java.nio.file.Files.createDirectories(workspace.getGenerator().getSourceRoot().toPath());
        }
        var output = new StringWriter();
        String request = "{\"id\":\"55837224-980b-4628-9c84-3c28fe60b839\",\"kind\":\"command\",\"operation\":\"create_mod_element\","
                + "\"expectedRevision\":0,\"payload\":{\"elementType\":\"block\",\"name\":\"verification_lamp\",\"initialValues\":{}}}\n";
        assertEquals(0, HeadlessProductLauncher.run(new String[]{"--workspace", file.toString(), "api"},
                new PrintWriter(output), new StringReader(request)), output.toString());
        var replies = output.toString().lines().map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
        assertTrue(replies.size() > 1 && replies.get(1).has("result"), output.toString());
        assertEquals("committed", replies.get(1).getAsJsonObject("result").get("status").getAsString(), output.toString());
        try (Workspace workspace = Workspace.readFromFS(file.toFile(), null)) {
            assertNotNull(workspace.getModElementByName("verification_lamp"));
            assertTrue(workspace.getModElementByName("verification_lamp").getAssociatedFiles().stream()
                    .anyMatch(path -> path.getName().endsWith(".java") && path.isFile()));
        }
    }
}
