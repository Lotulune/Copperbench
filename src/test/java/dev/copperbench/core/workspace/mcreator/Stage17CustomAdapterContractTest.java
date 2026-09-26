package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
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

class Stage17CustomAdapterContractTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private static final String GUI_INPUT = """
            {"components":[{"type":"label","data":{"name":"status","x":12,"y":20,
            "text":{"name":null,"fixedValue":"Verified text"},"color":-1,"hasShadow":true}}]}
            """;

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void validCustomShapesReopenAndRejectedEditsAndPlansPreserveBytes(String generator) throws Exception {
        Path document = root.resolve("custom_contract.mcreator"); UUID workspaceId = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings(generator));
             MCreatorWorkspaceSession session = attach(workspace, workspaceId)) {
            var item = create(session, 0, "item", "item_probe", """
                    {"glowCondition":false,"specialInformation":["one","two"],"onRightClickedInAir":null,
                    "states":[{"renderType":0,"customModelName":"Normal","texture":"minecraft:barrier",
                    "stateMap":[{"property":{"type":"logic","name":"blocking"},"value":true}]}]}
                    """);
            assertEquals("committed", item.status(), item.diagnostics().toString());
            var gui = create(session, 1, "gui", "gui_probe", GUI_INPUT);
            assertEquals("committed", gui.status(), gui.diagnostics().toString());
            JsonObject update = new JsonObject(); update.add("elementId", gui.data().getAsJsonObject().getAsJsonObject("element").get("id"));
            update.add("changes", JsonParser.parseString("[{\"path\":\"/components/0/data/x\",\"value\":1.0000000000000001}]"));
            Path definition = root.resolve("elements/gui_probe.mod.json"); byte[] before = Files.readAllBytes(definition);
            var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 2, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", rejected.status()); assertEquals("FIELD_VALUE_OUT_OF_RANGE", rejected.diagnostics().getFirst().code());
            assertEquals(2, rejected.newRevision()); assertArrayEquals(before, Files.readAllBytes(definition));
            JsonObject operation = new JsonObject(); operation.addProperty("operation", "update_mod_element"); operation.add("payload", update);
            JsonArray operations = new JsonArray(); operations.add(operation); JsonObject plan = new JsonObject(); plan.add("operations", operations);
            plan.addProperty("expectedRevision", 2); plan.addProperty("idempotencyKey", "invalid-coordinate");
            var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.PLAN_WORKSPACE_CHANGES, plan));
            assertEquals("failed", planned.status());
            assertTrue(planned.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_VALUE_OUT_OF_RANGE")));
            assertArrayEquals(before, Files.readAllBytes(definition));
            update.add("changes", JsonParser.parseString("[{\"path\":\"/components/0/data/x\",\"value\":24}]"));
            var accepted = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 2, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("committed", accepted.status(), accepted.diagnostics().toString());
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            var item = (net.mcreator.element.types.Item) reopened.getModElementByName("item_probe").getGeneratableElement();
            assertFalse(item.glowCondition.getFixedValue()); assertEquals(java.util.List.of("one", "two"), item.specialInformation.getFixedValue());
            assertEquals(1, item.states.getFirst().stateMap.size());
            assertEquals(true, item.states.getFirst().stateMap.values().iterator().next());
            var gui = (net.mcreator.element.types.GUI) reopened.getModElementByName("gui_probe").getGeneratableElement();
            var label = (net.mcreator.element.parts.gui.Label) gui.components.getFirst();
            assertEquals(24, label.x); assertEquals("Verified text", label.text.getFixedValue()); assertTrue(label.hasShadow);
        }
    }

    @Test void importedGuiExtensionIsPreservedOnUnrelatedEdits() throws Exception {
        Path document = root.resolve("custom_contract.mcreator"); UUID id = UUID.randomUUID(); String elementId;
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings("fabric-1.21.1"));
             MCreatorWorkspaceSession session = attach(workspace, id)) {
            var created = create(session, 0, "gui", "gui_probe", GUI_INPUT);
            assertEquals("committed", created.status(), created.diagnostics().toString());
            elementId = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
        }
        Path definition = root.resolve("elements/gui_probe.mod.json");
        JsonObject raw = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
        raw.getAsJsonObject("definition").getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonObject("data").addProperty("pluginValue", "preserve");
        Files.writeString(definition, raw.toString()); byte[] before = Files.readAllBytes(definition);
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null); MCreatorWorkspaceSession session = attach(workspace, id)) {
            JsonObject update = new JsonObject(); update.addProperty("elementId", elementId);
            update.add("changes", JsonParser.parseString("[{\"path\":\"/description\",\"value\":\"unrelated\"}]"));
            var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), id, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", rejected.status()); assertEquals("FIELD_PRESERVATION_REQUIRES_REVIEW", rejected.diagnostics().getFirst().code());
            assertTrue(rejected.diagnostics().getFirst().path().endsWith("/components/0/data/pluginValue"));
            assertEquals(1, rejected.newRevision()); assertArrayEquals(before, Files.readAllBytes(definition));
        }
        assertArrayEquals(before, Files.readAllBytes(definition));
    }

    private static WorkspaceSettings settings(String generator) {
        WorkspaceSettings settings = new WorkspaceSettings("custom_contract");
        settings.setModName("Custom Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator); return settings;
    }
    private static MCreatorWorkspaceSession attach(Workspace workspace, UUID id) throws Exception {
        return MCreatorWorkspaceSession.attach(workspace, id, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }
    private static CommandResult create(MCreatorWorkspaceSession session, long revision, String type, String name, String input) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", name); payload.add("initialValues", JsonParser.parseString(input));
        return session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
    }

    @Test void procedureAdaptersCannotCoerceOrDiscardValues() throws Exception {
        reject("item", new String[][] {
                {"{\"glowCondition\":\"false\"}", "FIELD_TYPE_INVALID", "/glowCondition"},
                {"{\"glowCondition\":{\"fixedValue\":\"false\"}}", "FIELD_TYPE_INVALID", "/glowCondition/fixedValue"},
                {"{\"glowCondition\":{\"fixedValue\":true,\"typo\":1}}", "FIELD_UNSUPPORTED", "/glowCondition/typo"},
                {"{\"specialInformation\":[42]}", "FIELD_TYPE_INVALID", "/specialInformation/0"},
                {"{\"onRightClickedInAir\":42}", "FIELD_TYPE_INVALID", "/onRightClickedInAir"}
        });
    }

    @Test void guiUnknownKindsAndCoercedCoordinatesCannotBeCommitted() throws Exception {
        reject("gui", new String[][] {
                {"{\"components\":[{\"type\":\"typo\",\"data\":{\"secret\":\"lost\"}}]}", "FIELD_ENUM_INVALID", "/components/0/type"},
                {"{\"components\":[{\"type\":\"label\",\"data\":{\"name\":\"label\",\"x\":1.0000000000000001,\"text\":\"hello\"}}]}", "FIELD_VALUE_OUT_OF_RANGE", "/components/0/data/x"},
                {"{\"components\":[{\"type\":\"label\",\"data\":{\"name\":\"label\",\"text\":\"hello\",\"hasShadow\":\"false\"}}]}", "FIELD_TYPE_INVALID", "/components/0/data/hasShadow"}
        });
    }

    private void reject(String type, String[][] cases) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("custom_contract");
        settings.setModName("Custom Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("custom_contract.mcreator").toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            for (String[] input : cases) {
                JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", "invalid_input");
                payload.add("initialValues", JsonParser.parseString(input[0]));
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertEquals("rejected", result.status(), input[0]);
                assertEquals(input[1], result.diagnostics().getFirst().code(), result.diagnostics().toString());
                assertTrue(result.diagnostics().getFirst().path().endsWith(input[2]), result.diagnostics().toString());
                assertEquals(0, result.newRevision()); assertFalse(Files.exists(root.resolve("elements/invalid_input.mod.json")));
            }
        }
    }
}
