package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.tracks.PreviewCapabilityCatalog;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.types.Recipe;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.copperbench.core.workspace.mcreator.Fabric263ItemTest.*;

class Fabric263RecipeTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private Workspace create(String filename) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("copperbench_camp_kit");
        settings.setModName("Copperbench Camp Kit"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-26.3");
        return Workspace.createWorkspace(temporary.resolve(filename).toFile(), settings);
    }

    @Test void discoveryPublishesOnlyBoundedInputsAndResolvableVanillaOptions() {
        var contract = ElementFieldContract.discover("recipe", "fabric-26.3");
        assertTrue(contract.get("complete").getAsBoolean(), contract.toString());
        Set<String> fields = new HashSet<>();
        contract.getAsJsonArray("fields").forEach(f -> fields.add(f.getAsJsonObject().get("path").getAsString().substring(1)));
        assertEquals(PreviewCapabilityCatalog.RECIPE_FIELDS, fields);
        assertTrue(ElementFieldContract.discover("recipe", "neoforge-26.3").get("complete").getAsBoolean());
        var options = ElementFieldContract.referenceOptions("recipe", "fabric-26.3", "blocksitems", "", 0, 100);
        assertEquals(PreviewElementFields.RECIPE_ITEMS.size(), options.getAsJsonArray("options").size());
        assertNull(PreviewElementFields.validateGeneration("fabric-26.3", "recipe", contract.getAsJsonObject("minimalExample").getAsJsonObject("initialValues")));
    }

    @Test void emptyUiCreationHasEditableRecipeDefaults() throws Exception {
        UUID id = UUID.randomUUID();
        try (var workspace = create("empty.mcreator"); var session = attach(workspace, id)) {
            var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            var created = command(entry, id, 0, "create_mod_element", json("{\"elementType\":\"recipe\",\"name\":\"new_recipe\",\"initialValues\":{}}"));
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            String elementId = created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
            var editor = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, json("{\"elementId\":\""+elementId+"\"}")));
            Path output = Path.of("output/p263-18/core-ui"); Files.createDirectories(output);
            Files.writeString(output.resolve("new-editor.json"), UiCore.wireGson().toJson(editor));
            var fields = editor.data().getAsJsonObject().getAsJsonArray("sections").asList().stream()
                    .flatMap(s -> s.getAsJsonObject().getAsJsonArray("fields").asList().stream()).map(JsonElement::getAsJsonObject).toList();
            assertEquals(4, fields.stream().filter(f -> f.get("path").getAsString().equals("/recipeType")).findFirst().orElseThrow().getAsJsonArray("options").size());
            assertEquals("json", fields.stream().filter(f -> f.get("path").getAsString().equals("/recipeSlots")).findFirst().orElseThrow().get("control").getAsString());
            assertEquals(1, fields.stream().filter(f -> f.get("path").getAsString().equals("/cookingTime")).findFirst().orElseThrow().getAsJsonObject("constraints").get("min").getAsInt());
        }
    }

    @Test void legacyInternalIdentityResolvesToActualRegistryNameAcrossGenerationAndReopen() throws Exception {
        UUID id = UUID.randomUUID(); String elementId;
        try (var workspace = create("recipe.mcreator")) {
            var mod = new ModElement(workspace, "LegacyKit", ModElementTypeLoader.getModElementType("item"));
            mod.setRegistryName("actual_trail_kit"); workspace.addModElement(mod);
            var item = StructuredElementDefaults.item(mod, workspace, "Legacy Kit");
            workspace.getModElementManager().storeModElement(item);
            assertTrue(workspace.getGenerator().generateElement(item));
            try (var session = attach(workspace, id)) {
                var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
                var contract = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_FIELD_CONTRACT, json("{\"elementType\":\"recipe\"}")));
                var payload = contract.data().getAsJsonObject().getAsJsonObject("minimalExample").deepCopy();
                payload.addProperty("name", "camp_recipe"); payload.getAsJsonObject("initialValues").addProperty("recipeReturnStack", "CUSTOM:LegacyKit");
                var created = command(entry, id, 0, "create_mod_element", payload);
                assertEquals("committed", created.get("status").getAsString(), created.toString());
                elementId = created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
                Path resource = temporary.resolve("src/main/resources/data/copperbench_camp_kit/recipe/camp_recipe.json");
                assertEquals("copperbench_camp_kit:actual_trail_kit", json(Files.readString(resource)).getAsJsonObject("result").get("id").getAsString());
                var update = json("{\"changes\":[{\"path\":\"/recipeType\",\"value\":\"Smelting\"},{\"path\":\"/smeltingInputStack\",\"value\":\"Items.STRING\"},{\"path\":\"/smeltingReturnStack\",\"value\":\"CUSTOM:LegacyKit\"},{\"path\":\"/cookingTime\",\"value\":200},{\"path\":\"/recipeRetstackSize\",\"value\":2}]}");
                update.addProperty("elementId", elementId);
                assertEquals("committed", command(entry, id, 1, "update_mod_element", update).get("status").getAsString());
                assertEquals("minecraft:smelting", json(Files.readString(resource)).get("type").getAsString());
                assertEquals(200, json(Files.readString(resource)).get("cookingtime").getAsInt());
                assertEquals(2, json(Files.readString(resource)).getAsJsonObject("result").get("count").getAsInt());
                byte[] before = Files.readAllBytes(temporary.resolve("elements/camp_recipe.mod.json"));
                var invalid = command(entry, id, 2, "update_mod_element", json("{\"elementId\":\""+elementId+"\",\"changes\":[{\"path\":\"/smeltingReturnStack\",\"value\":\"CUSTOM:MissingKit\"}]}"));
                assertEquals("rejected", invalid.get("status").getAsString(), invalid.toString());
                assertTrue(invalid.toString().contains("FIELD_REFERENCE_INVALID"), invalid.toString());
                assertArrayEquals(before, Files.readAllBytes(temporary.resolve("elements/camp_recipe.mod.json")));
                Path output = Path.of("output/p263-18/core-ui"); Files.createDirectories(output);
                Files.writeString(output.resolve("field-contract.json"), UiCore.wireGson().toJson(contract));
                Files.writeString(output.resolve("editor.json"), UiCore.wireGson().toJson(entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, json("{\"elementId\":\""+elementId+"\"}")))));
                workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            }
        }
        Path stored = temporary.resolve("elements/camp_recipe.mod.json");
        var raw = json(Files.readString(stored)); raw.getAsJsonObject("definition").add("futureRecipeField", json("{\"keep\":[1,2]}")); Files.writeString(stored, raw.toString());
        try (var workspace = Workspace.readFromFS(temporary.resolve("recipe.mcreator").toFile(), null); var session = attach(workspace, id)) {
            var recipe = (Recipe) workspace.getModElementByName("camp_recipe").getGeneratableElement();
            assertEquals("CUSTOM:LegacyKit", recipe.smeltingReturnStack.getUnmappedValue());
            session.uiEntry().preview(UUID.randomUUID(), id, request("opt_in", 2));
            var updated = command(session.uiEntry(), id, 2, "update_mod_element", json("{\"elementId\":\""+elementId+"\",\"changes\":[{\"path\":\"/description\",\"value\":\"Reopened\"}]}"));
            assertEquals("committed", updated.get("status").getAsString(), updated.toString());
            assertEquals(raw.getAsJsonObject("definition").get("futureRecipeField"), json(Files.readString(stored)).getAsJsonObject("definition").get("futureRecipeField"));
        }
    }

    @Test void unverifiedRecipeSubtypesAndReferencesAreRejected() {
        for (String type : List.of("Brewing", "Smithing", "Campfire cooking"))
            assertEquals("FIELD_ENUM_INVALID", PreviewElementFields.validateGeneration("fabric-26.3", "recipe", json("{\"recipeType\":\""+type+"\"}")).code());
        assertEquals("FIELD_VALUE_OUT_OF_RANGE", PreviewElementFields.validateGeneration("fabric-26.3", "recipe", json("{\"cookingTime\":0}")).code());
        assertEquals("FIELD_REFERENCE_INVALID", PreviewElementFields.validateGeneration("fabric-26.3", "recipe", json("{\"recipeReturnStack\":\"Items.NOT_REAL\"}")).code());
        assertEquals("PREVIEW_FIELD_UNVERIFIED", PreviewElementFields.validateGeneration("fabric-26.3", "recipe", json("{\"namespace\":\"minecraft\"}")).code());
    }
}
