package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Block;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.*;
import java.time.Clock;
import java.util.UUID;

import static dev.copperbench.core.workspace.mcreator.Fabric263ItemTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PreviewBlockRecoveryTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @CsvSource({"fabric,adopt_definition", "fabric,reapply_declared", "neoforge,adopt_definition", "neoforge,reapply_declared"})
    void reviewedRecoveryRetainsPreviewAndSourceGuards(String loader, String mode) throws Exception {
        String generator = loader + "-26.3";
        var settings = new WorkspaceSettings("recovery_test"); settings.setModName("Recovery Test");
        settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        UUID id = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("recovery.mcreator").toFile(), settings)) {
            try (var session = attach(workspace, id)) {
                var choice = request("opt_in", 0); choice.addProperty("generatorId", generator);
                session.uiEntry().preview(UUID.randomUUID(), id, choice);
                var create = request("command", 0); create.addProperty("generatorId", generator);
                create.addProperty("command", "create_mod_element");
                create.add("payload", json("{\"elementType\":\"block\",\"name\":\"post\",\"initialValues\":{\"hardness\":3}}"));
                var created = session.uiEntry().preview(UUID.randomUUID(), id, create);
                assertEquals("committed", created.get("status").getAsString(), created.toString());
            }
            var block = (Block) workspace.getModElementByName("post").getGeneratableElement();
            block.hardness = 1; workspace.getModElementManager().storeModElement(block);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            Path definition = root.resolve("elements/post.mod.json");
            String original = Files.readString(definition);
            try (var session = MCreatorWorkspaceSession.attach(workspace,
                    ignored -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                assertEquals(id, session.workspaceId());
                String elementId = workspace.getModElementByName("post").getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA).toString();
                JsonObject query = new JsonObject(); query.addProperty("elementId", elementId);
                var entry = session.uiEntry();
                var editor = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, query));
                var configuration = editor.data().getAsJsonObject().getAsJsonObject("configuration");
                assertEquals("drift", configuration.get("status").getAsString());
                var operation = configuration.getAsJsonObject("planOperations").getAsJsonObject(mode);
                JsonObject planning = json("{\"expectedRevision\":1,\"idempotencyKey\":\"recover\",\"requireRecoveryPoint\":true}");
                var operations = new JsonArray(); operations.add(operation); planning.add("operations", operations);
                var planned = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.PLAN_WORKSPACE_CHANGES, planning));
                assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
                var apply = request("command", 1); apply.addProperty("generatorId", generator);
                apply.addProperty("command", "apply_workspace_plan");
                var payload = new JsonObject(); payload.add("plan", planned.data()); apply.add("payload", payload);
                assertTrue(entry.preview(UUID.randomUUID(), id, apply).toString().contains("PREVIEW_OPT_IN_REQUIRED"));
                var choice = request("opt_in", 1); choice.addProperty("generatorId", generator);
                entry.preview(UUID.randomUUID(), id, choice);
                assertTrue(session.headlessEntry(UiCore.PermissionProfile.READ_ONLY).preview(UUID.randomUUID(), id, apply)
                        .toString().contains("PERMISSION_DENIED"));
                var staleRevision = apply.deepCopy(); staleRevision.addProperty("expectedRevision", 0);
                assertTrue(entry.preview(UUID.randomUUID(), id, staleRevision).toString().contains("REVISION_CONFLICT"));
                var mixed = apply.deepCopy();
                mixed.getAsJsonObject("payload").getAsJsonObject("plan").getAsJsonArray("operations")
                        .add(json("{\"operation\":\"delete_mod_element\",\"payload\":{\"elementId\":\"" + elementId + "\"}}"));
                assertTrue(entry.preview(UUID.randomUUID(), id, mixed).toString().contains("PREVIEW_OPERATION_UNSUPPORTED"));
                var withAssets = apply.deepCopy();
                withAssets.getAsJsonObject("payload").getAsJsonObject("plan").add("templateAssets", JsonParser.parseString("[{}]"));
                assertTrue(entry.preview(UUID.randomUUID(), id, withAssets).toString().contains("PREVIEW_OPERATION_UNSUPPORTED"));
                choice.addProperty("operation", "revoke"); entry.preview(UUID.randomUUID(), id, choice);
                assertTrue(entry.preview(UUID.randomUUID(), id, apply).toString().contains("PREVIEW_OPT_IN_REQUIRED"));
                choice.addProperty("operation", "opt_in"); entry.preview(UUID.randomUUID(), id, choice);
                Files.writeString(definition, original + "\n");
                var direct = request("command", 1); direct.addProperty("generatorId", generator);
                direct.addProperty("command", "update_mod_element"); direct.add("payload", operation.getAsJsonObject("payload"));
                var staleUpdate = entry.preview(UUID.randomUUID(), id, direct);
                assertTrue(staleUpdate.toString().contains("SOURCE_CONTENT_CONFLICT"), staleUpdate.toString());
                var stale = entry.preview(UUID.randomUUID(), id, apply);
                assertTrue(stale.toString().contains("WORKSPACE_PLAN_SOURCE_CONFLICT"), stale.toString());
                assertEquals(original + "\n", Files.readString(definition));
                editor = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, query));
                operation = editor.data().getAsJsonObject().getAsJsonObject("configuration").getAsJsonObject("planOperations").getAsJsonObject(mode);
                operations.set(0, operation); planning.addProperty("idempotencyKey", "recover-current");
                planned = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.PLAN_WORKSPACE_CHANGES, planning));
                assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
                payload.add("plan", planned.data());
                var applied = entry.preview(UUID.randomUUID(), id, apply);
                assertEquals("committed", applied.get("status").getAsString(), applied.toString());
                assertEquals(2, applied.get("revision").getAsInt());
                assertFalse(applied.getAsJsonObject("core").get("recoveryPointId").isJsonNull());
                if (mode.equals("adopt_definition")) assertEquals(original + "\n", Files.readString(definition));
                else assertEquals(3, ((Block) workspace.getModElementByName("post").getGeneratableElement()).hardness);
                editor = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, query));
                assertEquals("consistent", editor.data().getAsJsonObject().getAsJsonObject("configuration").get("status").getAsString());
            }
        }
    }
}
