package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.tracks.PreviewCapabilityCatalog;
import net.mcreator.element.types.Block;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.copperbench.core.workspace.mcreator.NeoForge263ItemTest.*;

class NeoForge263BlockTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void blockDiscoveryUsesTheExistingBoundedContract() {
        JsonObject contract = BlockFieldContract.capabilities("neoforge-26.3");
        assertTrue(contract.get("supported").getAsBoolean());
        Set<String> fields = new HashSet<>();
        contract.getAsJsonArray("fields").forEach(f -> fields.add(f.getAsJsonObject().get("name").getAsString()));
        assertEquals(PreviewCapabilityCatalog.BLOCK_FIELDS, fields);
        var rotation = contract.getAsJsonArray("fields").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(f -> f.get("name").getAsString().equals("rotationMode")).findFirst().orElseThrow();
        assertEquals("[0,5]", rotation.getAsJsonObject("inputSchema").get("enum").toString());
        assertEquals(2, rotation.getAsJsonArray("options").size());
        assertTrue(BlockFieldContract.capabilities("fabric-26.3").get("supported").getAsBoolean());
    }

    @Test void emptyUiCreationEditingGenerationAndReopenPreserveTheActualBlockDefinition() throws Exception {
        Path file = temporary.resolve("block.mcreator"); UUID id = UUID.randomUUID(); String elementId;
        WorkspaceSettings settings = new WorkspaceSettings("copperbench_camp_kit");
        settings.setModName("Copperbench Camp Kit"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("neoforge-26.3");
        Path evidence = Path.of("output/p263-17/core-ui"); Files.createDirectories(evidence);
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings); var session = attach(workspace, id)) {
            var entry = session.uiEntry();
            var contract = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR,
                    json("{\"elementType\":\"block\"}")));
            assertEquals("succeeded", contract.status());
            assertEquals(contract.data(), session.headlessEntry(UiCore.PermissionProfile.READ_ONLY).query(
                    UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR,
                            json("{\"elementType\":\"block\"}"))).data());
            Files.writeString(evidence.resolve("field-contract.json"), UiCore.wireGson().toJson(contract));
            entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            var created = command(entry, id, 0, "create_mod_element", json("{\"elementType\":\"block\",\"name\":\"camp_post\",\"initialValues\":{}}"));
            assertEquals("committed", created.get("status").getAsString(), created.toString());
            elementId = created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
            var editor = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, json("{\"elementId\":\""+elementId+"\"}")));
            Files.writeString(evidence.resolve("new-editor.json"), UiCore.wireGson().toJson(editor));
            var definition = (Block) workspace.getModElementByName("camp_post").getGeneratableElement();
            assertEquals(64, definition.maxStackSize); assertEquals(0, definition.rotationMode);
            assertEquals(0, definition.hardness); assertEquals(0, definition.resistance); assertTrue(definition.hasBlockItem);
            var fields = editor.data().getAsJsonObject().getAsJsonArray("sections").asList().stream()
                    .flatMap(s -> s.getAsJsonObject().getAsJsonArray("fields").asList().stream()).map(JsonElement::getAsJsonObject).toList();
            var rotation = fields.stream().filter(f -> f.get("path").getAsString().equals("/rotationMode")).findFirst().orElseThrow();
            assertEquals(2, rotation.getAsJsonArray("options").size()); assertEquals(0, rotation.get("value").getAsInt());
            assertEquals(64, fields.stream().filter(f -> f.get("path").getAsString().equals("/maxStackSize")).findFirst().orElseThrow().get("value").getAsInt());
            var texture = fields.stream().filter(f -> f.get("path").getAsString().equals("/texture")).findFirst().orElseThrow();
            assertEquals("select", texture.get("control").getAsString()); assertEquals(4, texture.getAsJsonArray("options").size());
            for (var field : fields) if (!field.get("readOnly").getAsBoolean())
                assertTrue(PreviewCapabilityCatalog.BLOCK_FIELDS.contains(field.get("path").getAsString().substring(1)), field.toString());
            assertTrue(workspace.getGenerator().generateElement(definition));
            try (var paths = Files.walk(temporary.resolve("src/main/java"))) {
                String source = Files.readString(paths.filter(p -> p.getFileName().toString().equals("camp_postBlock.java")).findFirst().orElseThrow());
                assertTrue(source.contains("extends net.minecraft.world.level.block.Block"), source);
            }
            JsonObject update = json("""
                    {"changes":[{"path":"/displayName","value":"Camp Post"},{"path":"/hardness","value":3},
                    {"path":"/resistance","value":6},{"path":"/rotationMode","value":5},{"path":"/maxStackSize","value":16},
                    {"path":"/rarity","value":"UNCOMMON"},{"path":"/texture","value":"minecraft:oak_log"}]}
                    """); update.addProperty("elementId", elementId);
            var changed = command(entry, id, 1, "update_mod_element", update);
            assertEquals("committed", changed.get("status").getAsString(), changed.toString());
            editor = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, json("{\"elementId\":\""+elementId+"\"}")));
            Files.writeString(evidence.resolve("editor.json"), UiCore.wireGson().toJson(editor));
            definition = (Block) workspace.getModElementByName("camp_post").getGeneratableElement();
            assertEquals(3, definition.hardness); assertEquals(5, definition.rotationMode);
            assertTrue(workspace.getGenerator().generateElement(definition)); assertTrue(workspace.getGenerator().generateBase());
            assertEquals(3, json(Files.readString(temporary.resolve("src/main/resources/assets/copperbench_camp_kit/blockstates/camp_post.json"))).getAsJsonObject("variants").size());
            assertEquals("Camp Post", json(Files.readString(temporary.resolve("src/main/resources/assets/copperbench_camp_kit/lang/en_us.json"))).get("block.copperbench_camp_kit.camp_post").getAsString());
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        }
        Path stored = temporary.resolve("elements/camp_post.mod.json");
        JsonObject raw = json(Files.readString(stored)); raw.getAsJsonObject("definition").add("futureBlockField", json("{\"keep\":[1,2]}"));
        Files.writeString(stored, raw.toString());
        try (Workspace workspace = Workspace.readFromFS(file.toFile(), null); var session = attach(workspace, id)) {
            var definition = (Block) workspace.getModElementByName("camp_post").getGeneratableElement();
            assertEquals(5, definition.rotationMode); assertEquals(16, definition.maxStackSize);
            session.uiEntry().preview(UUID.randomUUID(), id, request("opt_in", 2));
            var changed = command(session.uiEntry(), id, 2, "update_mod_element", json("{\"elementId\":\""+elementId+"\",\"changes\":[{\"path\":\"/description\",\"value\":\"Reopened\"}]}"));
            assertEquals("committed", changed.get("status").getAsString(), changed.toString());
            assertEquals(raw.getAsJsonObject("definition").get("futureBlockField"), json(Files.readString(stored)).getAsJsonObject("definition").get("futureBlockField"));
            definition = (Block) workspace.getModElementByName("camp_post").getGeneratableElement();
            definition.hasInventory = true; workspace.getModElementManager().storeModElement(definition);
            byte[] before = Files.readAllBytes(stored);
            var refused = command(session.uiEntry(), id, 3, "update_mod_element", json("{\"elementId\":\""+elementId+"\",\"changes\":[{\"path\":\"/description\",\"value\":\"Cannot ignore inventory\"}]}"));
            assertEquals("rejected", refused.get("status").getAsString()); assertTrue(refused.toString().contains("PREVIEW_FIELD_UNVERIFIED"));
            assertArrayEquals(before, Files.readAllBytes(stored));
        }
    }

    @Test void unverifiedBlockFeaturesAndReferencesCannotBeSilentlyGenerated() {
        for (String value : List.of("{\"generateFeature\":true}", "{\"hasInventory\":true}", "{\"hasBlockItem\":false}"))
            assertEquals("PREVIEW_FIELD_UNVERIFIED", PreviewElementFields.validateGeneration("neoforge-26.3", "block", json(value)).code());
        assertEquals("FIELD_ENUM_INVALID", PreviewElementFields.validateGeneration("neoforge-26.3", "block", json("{\"rotationMode\":1}")).code());
        assertEquals("FIELD_REFERENCE_INVALID", PreviewElementFields.validateGeneration("neoforge-26.3", "block", json("{\"texture\":\"minecraft:missing\"}")).code());
        assertNull(PreviewElementFields.validateGeneration("fabric-26.3", "block", new JsonObject()));
        assertNull(PreviewElementFields.validateGeneration("neoforge-26.3", "block", json("{\"futureBlockField\":{\"opaque\":true}}")));
    }
}
