package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.procedure.ProcedureIrCodec;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage17ProcedureBodyContractTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private static final UUID TRIGGER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static String xml(String trigger) {
        return "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\" id=\"" + TRIGGER
                + "\" x=\"0\" y=\"40.5\"><field name=\"trigger\">" + trigger + "</field></block></xml>";
    }
    private Workspace workspace(String generator) throws Exception {
        var settings = new WorkspaceSettings("procedure_contract"); settings.setModName("Procedure Contract");
        settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        return Workspace.createWorkspace(root.resolve("procedure_contract.mcreator").toFile(), settings);
    }
    @Test void malformedBodiesMustNotBeCommittedAsEmptyGraphs() throws Exception {
        try (var workspace = workspace("fabric-1.21.1"); var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            for (String input : new String[] {"{\"procedureIr\":{\"nodes\":false}}", "{\"procedurexml\":true}",
                    "{\"procedurexml\":\"<not_blockly/>\"}", "{\"fields\":{\"procedureIr\":null}}"}) {
                var payload = new JsonObject(); payload.addProperty("elementType", "procedure"); payload.addProperty("name", "invalid_input");
                payload.add("initialValues", JsonParser.parseString(input));
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertNotEquals("committed", result.status(), input); assertEquals(0, result.newRevision());
                assertFalse(Files.exists(root.resolve("elements/invalid_input.mod.json")));
            }
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void irBodyMustReachTheDefinitionAndXmlEditsMustReplaceStaleIr(String generator) throws Exception {
        var codec = new ProcedureIrCodec(); UUID workspaceId = UUID.randomUUID(); String id;
        try (var workspace = workspace(generator); var session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var values = new JsonObject(); var fields = new JsonObject();
            fields.add("procedureIr", codec.toJson(codec.fromBlocklyXml(xml("player_tick"), UUID.randomUUID()))); values.add("fields", fields);
            var payload = new JsonObject(); payload.addProperty("elementType", "procedure"); payload.addProperty("name", "body_probe"); payload.add("initialValues", values);
            var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
            assertEquals("committed", created.status(), created.diagnostics().toString());
            id = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            String stored = ((net.mcreator.element.types.Procedure) workspace.getModElementByName("body_probe").getGeneratableElement()).procedurexml;
            assertEquals("player_tick", codec.fromBlocklyXml(stored, UUID.fromString(id)).trigger());
            assertEquals(0, codec.fromBlocklyXml(stored, UUID.fromString(id)).nodeIndex().get(TRIGGER).x());
            assertEquals(40.5, codec.fromBlocklyXml(stored, UUID.fromString(id)).nodeIndex().get(TRIGGER).y());
            var update = new JsonObject(); update.addProperty("elementId", id);
            byte[] definitionBefore = Files.readAllBytes(root.resolve("elements/body_probe.mod.json"));
            update.add("changes", JsonParser.parseString("[{\"path\":\"/procedureIr/nodes/0/x\",\"value\":\"12\"}]"));
            var invalid = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", invalid.status()); assertEquals("FIELD_TYPE_INVALID", invalid.diagnostics().getFirst().code());
            assertEquals(1, invalid.newRevision()); assertArrayEquals(definitionBefore, Files.readAllBytes(root.resolve("elements/body_probe.mod.json")));
            var operation = new JsonObject(); operation.addProperty("operation", "update_mod_element"); operation.add("payload", update.deepCopy());
            var operations = new JsonArray(); operations.add(operation); var plan = new JsonObject(); plan.add("operations", operations);
            plan.addProperty("expectedRevision", 1); plan.addProperty("idempotencyKey", "invalid-procedure-body");
            var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.PLAN_WORKSPACE_CHANGES, plan));
            assertEquals("failed", planned.status()); assertArrayEquals(definitionBefore, Files.readAllBytes(root.resolve("elements/body_probe.mod.json")));
            var change = new JsonObject(); change.addProperty("path", "/fields/procedurexml"); change.addProperty("value", xml("no_ext_trigger"));
            var changes = new JsonArray(); changes.add(change); update.add("changes", changes);
            var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("committed", result.status(), result.diagnostics().toString());
            var query = new JsonObject(); query.addProperty("elementId", id);
            var editor = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_PROCEDURE_EDITOR, query));
            var editorProjection = editor.data().getAsJsonObject();
            assertEquals("no_ext_trigger", editorProjection.getAsJsonObject("ir").get("trigger").getAsString());
            // The active trigger catalog can legitimately contain player_tick; assert the
            // stored procedure body instead of searching the whole projection string.
            assertNotEquals("player_tick", editorProjection.getAsJsonObject("ir").get("trigger").getAsString());
            assertEquals(xml("no_ext_trigger"), ((net.mcreator.element.types.Procedure) workspace.getModElementByName("body_probe").getGeneratableElement()).procedurexml);
        }
        try (var reopened = Workspace.readFromFS(root.resolve("procedure_contract.mcreator").toFile(), null);
             var session = MCreatorWorkspaceSession.attach(reopened, workspaceId,
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            assertEquals(xml("no_ext_trigger"), ((net.mcreator.element.types.Procedure) reopened.getModElementByName("body_probe").getGeneratableElement()).procedurexml);
            var query = new JsonObject(); query.addProperty("elementId", id);
            var editor = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_PROCEDURE_EDITOR, query));
            assertNotEquals("player_tick", editor.data().getAsJsonObject().getAsJsonObject("ir").get("trigger").getAsString());
        }
    }
}
