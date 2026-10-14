package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.tracks.PreviewCapabilityCatalog;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.copperbench.core.workspace.mcreator.Fabric263ItemTest.*;

class Fabric263FunctionTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private Workspace create() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("copperbench_camp_kit");
        settings.setModName("Copperbench Camp Kit"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-26.3");
        return Workspace.createWorkspace(temporary.resolve("function.mcreator").toFile(), settings);
    }
    private static void save(String name, Object value) throws Exception {
        Path output = Path.of("output/p263-20/core-ui"); Files.createDirectories(output);
        Files.writeString(output.resolve(name + ".json"), UiCore.wireGson().toJson(value));
    }
    private static UiCore.Query editor(UUID workspace, String element) {
        return UiCore.Query.of(UUID.randomUUID(), workspace, UiCore.Operation.GET_MOD_ELEMENT_EDITOR,
                json("{\"elementId\":\"" + element + "\"}"));
    }
    private static void generate(Workspace workspace, String name) {
        assertTrue(workspace.getGenerator().generateBase());
        assertTrue(workspace.getGenerator().generateElement(workspace.getModElementByName(name).getGeneratableElement()));
    }

    @Test void specializedDiscoveryProvidesAConsumableExampleWithoutClaimingAGenericContract() throws Exception {
        UUID id = UUID.randomUUID();
        try (var workspace = create(); var session = attach(workspace, id)) {
            var environment = session.uiEntry().query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject()));
            var contract = environment.data().getAsJsonObject().getAsJsonObject("fieldContracts").getAsJsonObject("function");
            assertEquals(FunctionFieldContract.capabilities("fabric-26.3"), contract);
            assertEquals(PreviewCapabilityCatalog.FUNCTION_FIELDS, new HashSet<>(contract.getAsJsonArray("fields").asList().stream().map(JsonElement::getAsString).toList()));
            assertTrue(FunctionFieldContract.capabilities("neoforge-26.3").get("supported").getAsBoolean());
            assertFalse(ElementFieldContract.discover("function", "fabric-26.3").get("complete").getAsBoolean());
            session.uiEntry().preview(UUID.randomUUID(), id, request("opt_in", 0));
            var created = command(session.uiEntry(), id, 0, "create_mod_element", contract.getAsJsonObject("minimalExample"));
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            generate(workspace, "camp_signal");
            assertTrue(Files.readString(temporary.resolve("src/main/resources/data/copperbench_camp_kit/function/camp_signal.mcfunction")).contains("camp_probe camp_count 7"));
            save("function-contract", contract);
        }
    }

    @Test void emptyCreationEditingGenerationAndReopenPreserveBodyAndOpaqueFields() throws Exception {
        UUID id = UUID.randomUUID(); String elementId;
        String body = "# Camp signal 中文\nscoreboard objectives add camp_count dummy\nscoreboard players set camp_probe camp_count 11\n";
        try (var workspace = create(); var session = attach(workspace, id)) {
            var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            var created = command(entry, id, 0, "create_mod_element", json("{\"elementType\":\"function\",\"name\":\"camp_signal\",\"initialValues\":{}}"));
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            elementId = created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
            var initial = entry.query(editor(id, elementId)); save("new-editor", initial);
            var fields = initial.data().getAsJsonObject().getAsJsonArray("sections").asList().stream()
                    .flatMap(s -> s.getAsJsonObject().getAsJsonArray("fields").asList().stream()).map(JsonElement::getAsJsonObject).toList();
            assertEquals("# New Copperbench function\n", fields.stream().filter(f -> f.get("path").getAsString().equals("/code")).findFirst().orElseThrow().get("value").getAsString());
            assertTrue(fields.stream().filter(f -> f.get("path").getAsString().equals("/namespace")).findFirst().orElseThrow().get("readOnly").getAsBoolean());
            JsonObject payload = new JsonObject(); payload.addProperty("elementId", elementId);
            JsonArray changes = new JsonArray(); JsonObject change = new JsonObject(); change.addProperty("path", "/code"); change.addProperty("value", body); changes.add(change); payload.add("changes", changes);
            var updated = command(entry, id, 1, "update_mod_element", payload);
            assertEquals("committed", updated.get("status").getAsString(), updated.toString());
            generate(workspace, "camp_signal");
            assertEquals(body, Files.readString(temporary.resolve("src/main/resources/data/copperbench_camp_kit/function/camp_signal.mcfunction")));
            save("editor", entry.query(editor(id, elementId)));
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        }
        Path definition = temporary.resolve("elements/camp_signal.mod.json");
        var raw = json(Files.readString(definition)); raw.getAsJsonObject("definition").add("futureFunctionField", json("{\"keep\":[1,2]}")); Files.writeString(definition, raw.toString());
        try (var workspace = Workspace.readFromFS(temporary.resolve("function.mcreator").toFile(), null); var session = attach(workspace, id)) {
            var function = (net.mcreator.element.types.Function) workspace.getModElementByName("camp_signal").getGeneratableElement();
            assertEquals(body, function.code);
            session.uiEntry().preview(UUID.randomUUID(), id, request("opt_in", 2));
            var updated = command(session.uiEntry(), id, 2, "update_mod_element", json("{\"elementId\":\""+elementId+"\",\"changes\":[{\"path\":\"/description\",\"value\":\"Reopened\"}]}"));
            assertEquals("committed", updated.get("status").getAsString(), updated.toString());
            assertEquals(raw.getAsJsonObject("definition").get("futureFunctionField"), json(Files.readString(definition)).getAsJsonObject("definition").get("futureFunctionField"));
            assertTrue(workspace.getGenerator().generateElement(function));
            assertEquals(body, Files.readString(temporary.resolve("src/main/resources/data/copperbench_camp_kit/function/camp_signal.mcfunction")));
        }
    }

    @Test void invalidBodyNamespaceAndResourceNameLeaveRevisionAndDefinitionUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        try (var workspace = create(); var session = attach(workspace, id)) {
            var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            var created = command(entry, id, 0, "create_mod_element", json("{\"elementType\":\"function\",\"name\":\"camp_signal\",\"initialValues\":{}}"));
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            String element = created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
            byte[] before = Files.readAllBytes(temporary.resolve("elements/camp_signal.mod.json"));
            for (var invalid : Map.of("code", "/say invalid", "namespace", "minecraft", "name", "../outside").entrySet()) {
                JsonObject change = new JsonObject(); change.addProperty("path", "/"+invalid.getKey()); change.addProperty("value", invalid.getValue());
                JsonArray changes = new JsonArray(); changes.add(change);
                JsonObject payload = new JsonObject(); payload.addProperty("elementId", element); payload.add("changes", changes);
                var rejected = command(entry, id, 1, "update_mod_element", payload);
                assertEquals("rejected", rejected.get("status").getAsString(), rejected.toString());
                assertEquals(1, rejected.get("revision").getAsLong());
                assertTrue(rejected.toString().contains("/"+invalid.getKey()), rejected.toString());
                assertArrayEquals(before, Files.readAllBytes(temporary.resolve("elements/camp_signal.mod.json")));
            }
            assertEquals("FIELD_ENUM_INVALID", PreviewElementFields.validateGeneration("fabric-26.3", "function", json("{\"namespace\":\"minecraft\",\"code\":\"say hi\"}")).code());
        }
    }

    @Test void commandArraysKeepTheExistingExactBodyAndAliasRules() throws Exception {
        UUID id = UUID.randomUUID();
        try (var workspace = create(); var session = attach(workspace, id)) {
            var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            var created = command(entry, id, 0, "create_mod_element", json("{\"elementType\":\"function\",\"name\":\"array_body\",\"initialValues\":{\"commands\":[\"say first\",\"say second\"]}}"));
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            generate(workspace, "array_body");
            assertEquals("say first\nsay second\n", Files.readString(temporary.resolve("src/main/resources/data/copperbench_camp_kit/function/array_body.mcfunction")));
            assertEquals("FIELD_ALIAS_CONFLICT", FunctionFieldContract.validatePreview(json("{\"commands\":[\"say first\"],\"code\":\"say different\"}")).code());
            assertEquals("FIELD_TYPE_INVALID", FunctionFieldContract.validatePreview(json("{\"commands\":[42]}")).code());
            assertEquals("FUNCTION_CONTENT_INVALID", FunctionFieldContract.validatePreview(json("{\"code\":\"say ok\\n/say bad\"}")).code());
        }
    }
}
