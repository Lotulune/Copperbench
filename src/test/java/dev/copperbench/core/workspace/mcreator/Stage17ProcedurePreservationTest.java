package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage17ProcedurePreservationTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    @Test void structuredEditsAndPlansMustNotEraseRootVariablesOrKnownBlockMutations() throws Exception {
        var settings = new WorkspaceSettings("preserved_graph"); settings.setCurrentGenerator("fabric-1.21.1");
        settings.setModName("Preserved Graph"); settings.setVersion("1.0.0");
        UUID nodeId = UUID.randomUUID();
        String xml = "<xml><variables><variable id=\"local-value\">score</variable></variables>"
                + "<block type=\"event_trigger\" id=\"" + nodeId + "\"><field name=\"trigger\">no_ext_trigger</field>"
                + "<mutation extension=\"preserve\"/></block></xml>";
        try (var workspace = Workspace.createWorkspace(root.resolve("preserved_graph.mcreator").toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var payload = new JsonObject(); payload.addProperty("elementType", "procedure"); payload.addProperty("name", "preserved_body");
            var values = new JsonObject(); values.addProperty("procedurexml", xml); payload.add("initialValues", values);
            var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
            assertEquals("committed", created.status(), created.diagnostics().toString());
            String id = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            var edit = new JsonObject(); edit.addProperty("operation", "move_node"); edit.addProperty("nodeId", nodeId.toString());
            edit.addProperty("x", 80); edit.addProperty("y", 40); var edits = new JsonArray(); edits.add(edit);
            payload = new JsonObject(); payload.addProperty("elementId", id); payload.add("edits", edits);
            Path definition = root.resolve("elements/preserved_body.mod.json"); byte[] before = Files.readAllBytes(definition);
            var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_PROCEDURE, payload)).result();
            assertEquals("rejected", rejected.status()); assertEquals("PROCEDURE_XML_PRESERVATION_REQUIRED", rejected.diagnostics().getFirst().code());
            assertEquals(1, rejected.newRevision()); assertArrayEquals(before, Files.readAllBytes(definition));
            var query = new JsonObject(); query.addProperty("elementId", id);
            var editor = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_PROCEDURE_EDITOR, query));
            assertTrue(editor.data().getAsJsonObject().get("readOnly").getAsBoolean());
            assertTrue(editor.diagnostics().stream().anyMatch(d -> d.code().equals("PROCEDURE_XML_PRESERVATION_REQUIRED")));
            var preview = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PREVIEW_PROCEDURE_CHANGE, payload));
            assertEquals("rejected", preview.status()); assertEquals("PROCEDURE_XML_PRESERVATION_REQUIRED", preview.diagnostics().getFirst().code());
            var operation = new JsonObject(); operation.addProperty("operation", "update_procedure"); operation.add("payload", payload);
            var operations = new JsonArray(); operations.add(operation); var plan = new JsonObject(); plan.add("operations", operations);
            plan.addProperty("expectedRevision", 1); plan.addProperty("idempotencyKey", "preserve-xml");
            assertEquals("failed", session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES, plan)).status());
            var changes = new JsonArray(); var change = new JsonObject(); change.addProperty("path", "/procedureIr/nodes/0/x"); change.addProperty("value", 80); changes.add(change);
            var update = new JsonObject(); update.addProperty("elementId", id); update.add("changes", changes);
            rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", rejected.status()); assertEquals("PROCEDURE_XML_PRESERVATION_REQUIRED", rejected.diagnostics().getFirst().code());
            assertArrayEquals(before, Files.readAllBytes(definition));
            change.addProperty("path", "/description"); change.addProperty("value", "Safe metadata change");
            assertEquals("committed", session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_MOD_ELEMENT, update)).result().status());
            assertEquals(xml, ((net.mcreator.element.types.Procedure) workspace.getModElementByName("preserved_body").getGeneratableElement()).procedurexml);
            change.addProperty("path", "/procedurexml"); change.addProperty("value", xml.replace("score", "score_renamed"));
            assertEquals("committed", session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 2, Operation.UPDATE_MOD_ELEMENT, update)).result().status());
            assertEquals(xml.replace("score", "score_renamed"), ((net.mcreator.element.types.Procedure) workspace.getModElementByName("preserved_body").getGeneratableElement()).procedurexml);
            String plain = "<xml><block type=\"event_trigger\" id=\"" + nodeId + "\"><field name=\"trigger\">no_ext_trigger</field></block></xml>";
            change.addProperty("value", plain);
            assertEquals("committed", session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 3, Operation.UPDATE_MOD_ELEMENT, update)).result().status());
            editor = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_PROCEDURE_EDITOR, query));
            assertFalse(editor.data().getAsJsonObject().get("readOnly").getAsBoolean());
            assertEquals("committed", session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 4, Operation.UPDATE_PROCEDURE, payload)).result().status());
        }
    }
}
