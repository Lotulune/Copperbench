package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import dev.copperbench.procedure.ProcedureIrCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Stage17ProcedureContextTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void previewAndGenerationRejectUnavailableTriggerContextWithoutTouchingInputs(String generator) throws Exception {
        var settings = new WorkspaceSettings("context_contract"); settings.setModName("Context Contract");
        settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        Path document = root.resolve("context_contract.mcreator");
        UUID workspaceId = UUID.randomUUID();
        UUID coordinate = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            JsonObject values = new JsonObject();
            values.addProperty("procedurexml", "<xml><block type=\"event_trigger\"><field name=\"trigger\">no_ext_trigger</field><next>"
                    + "<block type=\"return_number\"><value name=\"VALUE\"><block type=\"coord_x\" id=\"" + coordinate
                    + "\"/></value></block></next></block></xml>");
            JsonObject create = new JsonObject(); create.addProperty("elementType", "procedure"); create.addProperty("name", "context_reader"); create.add("initialValues", values);
            var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, create)).result();
            assertEquals("committed", created.status(), created.diagnostics().toString());
            String id = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            JsonObject payload = triggerEdit(id, "mod_serverload");
            byte[] before = Files.readAllBytes(root.resolve("elements/context_reader.mod.json"));
            var preview = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_PROCEDURE_CHANGE, payload));
            assertEquals("succeeded", preview.status());
            assertFalse(preview.data().getAsJsonObject().get("canGenerate").getAsBoolean(), "Server startup provides no x context");
            assertTrue(preview.data().getAsJsonObject().get("canSaveDraft").getAsBoolean());
            JsonObject diagnostic = preview.data().getAsJsonObject().getAsJsonArray("diagnostics").get(0).getAsJsonObject();
            assertEquals("PROCEDURE_CONTEXT_MISSING", diagnostic.get("code").getAsString());
            assertTrue(diagnostic.get("path").getAsString().contains(coordinate.toString()));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("elements/context_reader.mod.json")));
            var invalid = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_PROCEDURE, payload)).result();
            assertEquals("committed", invalid.status());
            assertEquals("invalid", invalid.data().getAsJsonObject().getAsJsonObject("element").get("state").getAsString());
            var health = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_WORKSPACE_HEALTH, new JsonObject()));
            var current = health.data().getAsJsonObject().getAsJsonObject("diagnostics");
            assertEquals(1, current.get("error").getAsInt());
            assertEquals(1, current.get("total").getAsInt());
            assertEquals(1, health.data().getAsJsonObject().getAsJsonObject("elements").get("invalid").getAsInt());
            assertTrue(current.getAsJsonArray("items").asList().stream().anyMatch(item -> item.getAsJsonObject().get("code").getAsString().equals("PROCEDURE_CONTEXT_MISSING")));
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
            assertEquals("invalid", state.element(UUID.fromString(id)).state(), "A fresh projection must not reset invalid Procedures to valid");
            var store = new RevisionedWorkspaceStore(); store.register(state);
            var preparation = new MCreatorGenerationPreparation(workspace, store);
            byte[] invalidBytes = Files.readAllBytes(root.resolve("elements/context_reader.mod.json"));
            byte[] workspaceBytes = Files.readAllBytes(document);
            var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                    () -> preparation.prepare(state, root, Operation.BUILD_WORKSPACE, ignored -> fail("No external setup may start")));
            assertEquals("PROCEDURE_CONTEXT_MISSING", failure.code());
            assertArrayEquals(invalidBytes, Files.readAllBytes(root.resolve("elements/context_reader.mod.json")));
            assertArrayEquals(workspaceBytes, Files.readAllBytes(document));
            var valid = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_PROCEDURE_CHANGE, triggerEdit(id, "player_ticks")));
            assertTrue(valid.data().getAsJsonObject().get("canGenerate").getAsBoolean(), valid.data().toString());
            var reusable = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_PROCEDURE_CHANGE, triggerEdit(id, "no_ext_trigger")));
            assertTrue(reusable.data().getAsJsonObject().get("canGenerate").getAsBoolean(), "Reusable procedures receive context from their caller");
            var unknown = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_PROCEDURE_CHANGE, triggerEdit(id, "missing_test_trigger")));
            assertFalse(unknown.data().getAsJsonObject().get("canGenerate").getAsBoolean());
            assertTrue(unknown.data().getAsJsonObject().getAsJsonArray("diagnostics").asList().stream().anyMatch(item -> item.getAsJsonObject().get("code").getAsString().equals("PROCEDURE_TRIGGER_UNKNOWN")));
            var repaired = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 2, Operation.UPDATE_PROCEDURE, triggerEdit(id, "player_ticks"))).result();
            assertEquals("committed", repaired.status());
            var after = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_WORKSPACE_HEALTH, new JsonObject()));
            assertEquals(0, after.data().getAsJsonObject().getAsJsonObject("diagnostics").get("error").getAsInt());
            assertFalse(after.data().getAsJsonObject().getAsJsonObject("diagnostics").getAsJsonArray("items").asList().stream()
                    .anyMatch(item -> item.getAsJsonObject().get("code").getAsString().equals("PROCEDURE_CONTEXT_MISSING")));
            assertEquals("valid", repaired.data().getAsJsonObject().getAsJsonObject("element").get("state").getAsString());
            JsonArray changes = new JsonArray(); JsonObject change = new JsonObject();
            change.addProperty("path", "/procedurexml");
            change.addProperty("value", values.get("procedurexml").getAsString().replace("no_ext_trigger", "mod_serverload"));
            changes.add(change); JsonObject generic = new JsonObject(); generic.addProperty("elementId", id); generic.add("changes", changes);
            var genericUpdate = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 3, Operation.UPDATE_MOD_ELEMENT, generic)).result();
            assertEquals("committed", genericUpdate.status(), genericUpdate.diagnostics().toString());
            assertEquals("invalid", genericUpdate.data().getAsJsonObject().getAsJsonObject("element").get("state").getAsString());
            var directCreate = create.deepCopy(); directCreate.addProperty("name", "invalid_at_creation");
            directCreate.getAsJsonObject("initialValues").addProperty("procedurexml", change.get("value").getAsString());
            var direct = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 4, Operation.CREATE_MOD_ELEMENT, directCreate)).result();
            assertEquals("committed", direct.status(), direct.diagnostics().toString());
            assertEquals("invalid", direct.data().getAsJsonObject().getAsJsonObject("element").get("state").getAsString());
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            var metadata = reopened.getFileManager().loadOrCreateProductMetadata(workspaceId);
            var state = new MCreatorWorkspaceStateMapper().map(reopened, metadata);
            assertEquals(2, state.elements().size());
            assertTrue(state.elements().stream().filter(element -> element.type().equals("procedure")).allMatch(element -> element.state().equals("invalid")));
        }
    }

    @ParameterizedTest
    @CsvSource({"entity_from_deps,player_ticks,true", "coord_x,player_ticks,true", "coord_y,player_ticks,true", "coord_z,player_ticks,true",
            "source_entity_from_deps,player_ticks,false", "immediate_source_entity_from_deps,player_ticks,false",
            "entity_from_deps,mod_serverload,false", "coord_x,mod_serverload,false", "coord_y,mod_serverload,false", "coord_z,mod_serverload,false",
            "source_entity_from_deps,no_ext_trigger,true", "immediate_source_entity_from_deps,no_ext_trigger,true"})
    void directContextUsesExactNamesFromLoadedTriggerMetadata(String nodeType, String trigger, boolean valid) throws Exception {
        var settings = new WorkspaceSettings("direct_context"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("direct_context.mcreator").toFile(), settings)) {
            var ir = new ProcedureIrCodec().fromBlocklyXml("<xml><block type=\"event_trigger\"><field name=\"trigger\">" + trigger
                    + "</field><next><block type=\"text_print\"><value name=\"TEXT\"><block type=\"" + nodeType
                    + "\"/></value></block></next></block></xml>", UUID.randomUUID());
            var issues = MCreatorProcedureContextValidation.validate(workspace, ir);
            assertEquals(valid, issues.isEmpty(), issues.toString());
        }
    }

    private static JsonObject triggerEdit(String elementId, String trigger) {
        JsonObject edit = new JsonObject(); edit.addProperty("operation", "set_trigger"); edit.addProperty("trigger", trigger);
        JsonArray edits = new JsonArray(); edits.add(edit);
        JsonObject payload = new JsonObject(); payload.addProperty("elementId", elementId); payload.add("edits", edits); return payload;
    }
}
