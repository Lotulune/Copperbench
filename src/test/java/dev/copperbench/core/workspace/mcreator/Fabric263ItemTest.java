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
import java.time.Clock;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Fabric263ItemTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void discoveryMatchesWriteScopeAndExcludesOtherTargets() {
        JsonObject contract = ElementFieldContract.discover("item", "fabric-26.3");
        assertTrue(contract.get("complete").getAsBoolean(), contract.toString());
        Set<String> fields = new HashSet<>();
        contract.getAsJsonArray("fields").forEach(raw -> fields.add(raw.getAsJsonObject().get("path").getAsString().substring(1)));
        assertEquals(PreviewCapabilityCatalog.ITEM_FIELDS, fields);
        for (String type : List.of("function", "code", "itemextension"))
            assertFalse(ElementFieldContract.discover(type, "fabric-26.3").get("complete").getAsBoolean(), type);
        assertTrue(ElementFieldContract.discover("item", "neoforge-26.3").get("complete").getAsBoolean());
        for (String generator : List.of("fabric-26.2", "neoforge-26.2")) {
            String old = ElementFieldContract.discover("item", generator).toString();
            assertFalse(old.contains("/fuelBurnTime")); assertFalse(old.contains("/compostingLayers"));
            assertNotNull(PreviewElementFields.validateChange(generator, "item", new JsonObject(), json("{\"fuelBurnTime\":100}")));
        }
    }

    @Test void uiAndSdkShareBoundedCreationEditingGenerationAndReopen() throws Exception {
        Path root = temporary.resolve("workspace"), file = root.resolve("item.mcreator");
        WorkspaceSettings settings = new WorkspaceSettings("copperbench_camp_kit");
        settings.setModName("Copperbench Camp Kit"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-26.3");
        UUID id = UUID.randomUUID(); String elementId;
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings); var session = attach(workspace, id)) {
            var entry = session.uiEntry();
            var contract = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_FIELD_CONTRACT,
                    json("{\"elementType\":\"item\"}")));
            assertEquals("succeeded", contract.status());
            assertEquals(contract.data(), session.headlessEntry(UiCore.PermissionProfile.READ_ONLY).query(
                    UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_FIELD_CONTRACT,
                            json("{\"elementType\":\"item\"}"))).data());
            assertEquals("succeeded", entry.preview(UUID.randomUUID(), id, request("opt_in", 0)).get("status").getAsString());
            JsonObject payload = contract.data().getAsJsonObject().getAsJsonObject("minimalExample").deepCopy();
            payload.addProperty("name", "trail_kit");
            JsonObject created = command(entry, id, 0, "create_mod_element", payload);
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            elementId = created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
            JsonObject update = json("{\"changes\":[{\"path\":\"/displayName\",\"value\":\"Copper Camp Kit\"},"
                    + "{\"path\":\"/rarity\",\"value\":\"RARE\"},{\"path\":\"/immuneToFire\",\"value\":true},"
                    + "{\"path\":\"/fuelBurnTime\",\"value\":200},{\"path\":\"/compostingLayers\",\"value\":1}]}");
            update.addProperty("elementId", elementId);
            JsonObject changed = command(entry, id, 1, "update_mod_element", update);
            assertEquals("committed", changed.get("status").getAsString(), changed.toString());
            var editorResponse = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR,
                    json("{\"elementId\":\"" + elementId + "\"}")));
            JsonObject editor = editorResponse.data().getAsJsonObject();
            var editable = editor.getAsJsonArray("sections").asList().stream().flatMap(s -> s.getAsJsonObject().getAsJsonArray("fields").asList().stream())
                    .map(JsonElement::getAsJsonObject).filter(f -> !f.get("readOnly").getAsBoolean()).toList();
            for (var field : editable) assertTrue(PreviewCapabilityCatalog.ITEM_FIELDS.contains(field.get("path").getAsString().substring(1)), field.toString());
            var texture = editable.stream().filter(f -> f.get("path").getAsString().equals("/texture")).findFirst().orElseThrow();
            assertEquals("select", texture.get("control").getAsString()); assertEquals(4, texture.getAsJsonArray("options").size());
            Path uiEvidence = Path.of("output/p263-14/core-ui"); Files.createDirectories(uiEvidence);
            Files.writeString(uiEvidence.resolve("editor.json"), UiCore.wireGson().toJson(editorResponse));
            Files.writeString(uiEvidence.resolve("field-contract.json"), UiCore.wireGson().toJson(contract));
            var definition = (net.mcreator.element.types.Item) workspace.getModElementByName("trail_kit").getGeneratableElement();
            assertEquals(16, definition.stackSize); assertEquals(200, definition.fuelBurnTime); assertEquals(1, definition.compostingLayers);
            assertTrue(workspace.getGenerator().generateElement(definition)); assertTrue(workspace.getGenerator().generateBase());
            String source;
            try (var paths = Files.walk(root.resolve("src/main/java"))) {
                source = Files.readString(paths.filter(p -> p.getFileName().toString().equals("trail_kitItem.java")).findFirst().orElseThrow());
            }
            assertTrue(source.contains("DataComponents.COOKING_FUEL"), source);
            assertTrue(source.contains("ResolvableInt.Constant(200)"), source);
            assertTrue(source.contains("DataComponents.COMPOSTABLE"), source);
            assertFalse(source.contains("FuelValueEvents"));
            assertEquals("Copper Camp Kit", json(Files.readString(root.resolve("src/main/resources/assets/copperbench_camp_kit/lang/en_us.json")))
                    .get("item.copperbench_camp_kit.trail_kit").getAsString());
            assertEquals("minecraft:item/barrier", JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/copperbench_camp_kit/models/item/trail_kit.json")))
                    .getAsJsonObject().getAsJsonObject("textures").get("layer0").getAsString());
            for (var bad : Map.of("enableRanged", "true", "texture", "\"minecraft:does_not_exist\"", "fuelBurnTime", "-1", "compostingLayers", "8").entrySet()) {
                JsonObject attempt = json("{\"elementId\":\"" + elementId + "\",\"changes\":[{\"path\":\"/" + bad.getKey() + "\",\"value\":" + bad.getValue() + "}]}");
                var rejected = command(entry, id, 2, "update_mod_element", attempt);
                assertEquals("rejected", rejected.get("status").getAsString(), rejected.toString());
                assertEquals(2, rejected.get("revision").getAsLong());
            }
            assertTrue(command(entry, id, 2, "create_mod_element", json("{\"elementType\":\"loottable\",\"name\":\"not_ready\"}"))
                    .toString().contains("PREVIEW_ELEMENT_UNVERIFIED"));
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        }
        Path stored = root.resolve("elements/trail_kit.mod.json");
        JsonObject raw = json(Files.readString(stored)); raw.getAsJsonObject("definition").add("futureField", json("{\"keep\":[1,2,3]}"));
        Files.writeString(stored, raw.toString());
        try (Workspace workspace = Workspace.readFromFS(file.toFile(), null); var session = attach(workspace, id)) {
            var definition = (net.mcreator.element.types.Item) workspace.getModElementByName("trail_kit").getGeneratableElement();
            assertEquals(200, definition.fuelBurnTime); assertEquals(1, definition.compostingLayers); assertEquals("RARE", definition.rarity);
            session.uiEntry().preview(UUID.randomUUID(), id, request("opt_in", 2));
            var updated = command(session.uiEntry(), id, 2, "update_mod_element", json("{\"elementId\":\"" + elementId + "\",\"changes\":[{\"path\":\"/description\",\"value\":\"Reopened\"}]}"));
            assertEquals("committed", updated.get("status").getAsString(), updated.toString());
            assertEquals(raw.getAsJsonObject("definition").get("futureField"), json(Files.readString(stored)).getAsJsonObject("definition").get("futureField"));
            // A pre-existing unsupported definition must not be silently regenerated by an unrelated edit.
            definition = (net.mcreator.element.types.Item) workspace.getModElementByName("trail_kit").getGeneratableElement();
            definition.isFood = true;
            workspace.getModElementManager().storeModElement(definition);
            byte[] beforeUnsupportedEdit = Files.readAllBytes(stored);
            var refused = command(session.uiEntry(), id, 3, "update_mod_element", json("{\"elementId\":\"" + elementId + "\",\"changes\":[{\"path\":\"/description\",\"value\":\"Do not ignore food\"}]}"));
            assertTrue(refused.toString().contains("PREVIEW_FIELD_UNVERIFIED"), refused.toString());
            assertEquals("rejected", refused.get("status").getAsString());
            assertArrayEquals(beforeUnsupportedEdit, Files.readAllBytes(stored));

        }
    }

    @Test void importedUnsupportedActiveFeaturesCannotGenerate() {
        assertEquals("PREVIEW_FIELD_UNVERIFIED", PreviewElementFields.validateGeneration("fabric-26.3", "item", json("{\"enableRanged\":true}")).code());
        assertNull(PreviewElementFields.validateGeneration("fabric-26.3", "item", json("{\"enableRanged\":false,\"futureField\":{\"opaque\":true}}")));
        assertEquals("FIELD_REFERENCE_INVALID", PreviewElementFields.validateGeneration("fabric-26.3", "item", json("{\"texture\":\"../private\"}")).code());
    }

    @Test void emptyUiCreationProjectsTheActualEditableDefaults() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("copperbench_camp_kit");
        settings.setModName("Copperbench Camp Kit"); settings.setCurrentGenerator("fabric-26.3");
        UUID id = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(temporary.resolve("defaults.mcreator").toFile(), settings);
             var session = attach(workspace, id)) {
            session.uiEntry().preview(UUID.randomUUID(), id, request("opt_in", 0));
            var created = command(session.uiEntry(), id, 0, "create_mod_element",
                    json("{\"elementType\":\"item\",\"name\":\"trail_kit\",\"initialValues\":{}}"));
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            String elementId = created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
            var result = session.uiEntry().query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR,
                    json("{\"elementId\":\"" + elementId + "\"}")));
            JsonObject editor = result.data().getAsJsonObject();
            JsonObject values = new JsonObject();
            editor.getAsJsonArray("sections").forEach(s -> s.getAsJsonObject().getAsJsonArray("fields").forEach(f ->
                    values.add(f.getAsJsonObject().get("path").getAsString(), f.getAsJsonObject().get("value"))));
            assertEquals(64, values.get("/stackSize").getAsInt());
            assertEquals(0, values.get("/fuelBurnTime").getAsInt()); assertEquals(0, values.get("/compostingLayers").getAsInt());
            assertEquals("COMMON", values.get("/rarity").getAsString()); assertFalse(values.get("/immuneToFire").getAsBoolean());
            Path evidence = Path.of("output/p263-14/core-ui"); Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("new-editor.json"), UiCore.wireGson().toJson(result));
        }
    }

    static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    static JsonObject request(String operation, long revision) {
        JsonObject result = json("{\"supportedVersions\":[\"1\"],\"generatorId\":\"fabric-26.3\"}");
        result.addProperty("operation", operation); result.addProperty("expectedRevision", revision); return result;
    }
    static JsonObject command(WorkspaceEntryAdapter entry, UUID id, long revision, String operation, JsonObject payload) {
        JsonObject request = request("command", revision); request.addProperty("command", operation); request.add("payload", payload);
        return entry.preview(UUID.randomUUID(), id, request);
    }
    static MCreatorWorkspaceSession attach(Workspace workspace, UUID id) throws Exception {
        return MCreatorWorkspaceSession.attach(workspace, id, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }
}
