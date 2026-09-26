package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Stage17ElementIdentityTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest @ValueSource(strings = {"fabric-1.21.1", "neoforge-1.21.1"})
    void numericInternalNameAndExplicitResourceIdentityStayStableAcrossCreateReopenAndEdit(String generator) throws Exception {
        var settings = new WorkspaceSettings("identity_test");
        settings.setCurrentGenerator(generator); settings.setModName("Identity Test"); settings.setVersion("1.0.0");
        Path document = root.resolve("identity_test.mcreator");
        UUID workspaceId = UUID.randomUUID();
        String elementId;
        JsonObject identity;
        java.util.List<String> originalPaths;
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            try (var session = session(workspace, workspaceId)) {
                JsonObject payload = JsonParser.parseString("""
                    {"elementType":"block","name":"contract_probe_v6","initialValues":{"hardness":3}}
                    """).getAsJsonObject();
                var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload));
                assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
                var element = created.result().data().getAsJsonObject().getAsJsonObject("element");
                elementId = element.get("id").getAsString();
                assertEquals("contract_probe_v6", element.get("name").getAsString());
                identity = element.getAsJsonObject("identity").deepCopy();
                assertEquals("contract_probe_v6", identity.get("internalName").getAsString());
                assertEquals("contract_probe_v_6", identity.get("registryName").getAsString());
                assertEquals("identity_test:contract_probe_v_6", identity.get("resourceId").getAsString());
                assertEquals(workspace.getModElementByName("contract_probe_v6").getRegistryName(), identity.get("registryName").getAsString());
                assertEquals(identity, editor(session, elementId).getAsJsonObject("element").getAsJsonObject("identity"));
                originalPaths = workspace.getModElementByName("contract_probe_v6").getAssociatedFiles().stream().map(file -> file.getAbsolutePath()).sorted().toList();
            }
        }
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null); var session = session(workspace, workspaceId)) {
            var reopened = editor(session, elementId);
            assertEquals("contract_probe_v6", reopened.getAsJsonObject("element").get("name").getAsString());
            assertEquals(identity, reopened.getAsJsonObject("element").getAsJsonObject("identity"));
            var listPayload = JsonParser.parseString("{\"limit\":100,\"fields\":[\"id\",\"name\",\"identity\"]}").getAsJsonObject();
            var listed = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.LIST_MOD_ELEMENTS, listPayload));
            var listedElement = listed.data().getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
            assertEquals(identity, listedElement.getAsJsonObject("identity"));
            assertEquals("contract_probe_v6", listedElement.get("name").getAsString());
            var payload = JsonParser.parseString("{\"changes\":[{\"path\":\"/hardness\",\"value\":9}]}").getAsJsonObject();
            payload.addProperty("elementId", elementId);
            var updated = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, payload));
            assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
            assertEquals(identity, updated.result().data().getAsJsonObject().getAsJsonObject("element").getAsJsonObject("identity"));
            var actual = workspace.getModElementByName("contract_probe_v6");
            assertEquals(originalPaths, actual.getAssociatedFiles().stream().map(file -> file.getAbsolutePath()).sorted().toList());
            Path source = actual.getAssociatedFiles().stream().filter(file -> file.getName().endsWith("Block.java")).findFirst().orElseThrow().toPath();
            assertTrue(Files.readString(source).contains("strength(9"));
            actual.setCodeLock(true);
            assertFalse(editor(session, elementId).getAsJsonObject("element").getAsJsonObject("identity").has("resourceId"),
                    "Manual source registration cannot be inferred from generator metadata");
        }
    }

    @Test void importedCamelCaseManualElementRetainsStoredRegistryAndFilesDuringIdentityQueries() throws Exception {
        var settings = new WorkspaceSettings("identity_test");
        settings.setCurrentGenerator("fabric-1.21.1"); settings.setModName("Identity Test"); settings.setVersion("1.0.0");
        Path document = root.resolve("identity_test.mcreator");
        UUID workspaceId = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
            var element = new net.mcreator.workspace.elements.ModElement(workspace, "LegacyProbe6",
                    net.mcreator.element.ModElementTypeLoader.getModElementType("block"));
            element.setCodeLock(true);
            workspace.addModElement(element);
            workspace.getModElementManager().storeModElement(new net.mcreator.element.types.Block(element));
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        }
        var stored = JsonParser.parseString(Files.readString(document)).getAsJsonObject();
        stored.getAsJsonArray("mod_elements").get(0).getAsJsonObject().addProperty("registry_name", "custom_registry");
        Files.writeString(document, stored.toString());
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null); var session = session(workspace, workspaceId)) {
            String before = Files.readString(document);
            var payload = JsonParser.parseString("{\"limit\":100}").getAsJsonObject();
            var listed = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.LIST_MOD_ELEMENTS, payload));
            var element = listed.data().getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
            assertEquals("custom_registry", element.get("name").getAsString(), "Keep the legacy public projection for imported names");
            assertEquals("LegacyProbe6", element.getAsJsonObject("identity").get("internalName").getAsString());
            assertEquals("custom_registry", element.getAsJsonObject("identity").get("registryName").getAsString());
            assertFalse(element.getAsJsonObject("identity").has("resourceId"));
            assertEquals(before, Files.readString(document), "Identity queries must not migrate or rewrite imported data");
        }
    }

    private static MCreatorWorkspaceSession session(Workspace workspace, UUID id) throws Exception {
        return MCreatorWorkspaceSession.attach(workspace, id,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }

    private static JsonObject editor(MCreatorWorkspaceSession session, String elementId) {
        var payload = new JsonObject(); payload.addProperty("elementId", elementId);
        return session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_MOD_ELEMENT_EDITOR, payload)).data().getAsJsonObject();
    }
}
