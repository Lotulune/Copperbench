package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.Recipe;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.copperbench.generator.fabric.Fabric1211Generator.Profile.*;
import static dev.copperbench.generator.neoforge.NeoForge1211Generator.Profile.*;

/** Adapter/template coverage of all declared Java tracks, deliberately separate from real builds. */
class ElementFieldDiscoveryTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest @ValueSource(strings = {"fabric-1.20.1", "neoforge-1.20.1", "fabric-1.21.1", "neoforge-1.21.1",
            "fabric-26.1.2", "neoforge-26.1.2", "fabric-26.2", "neoforge-26.2"})
    void consumesCompleteExamplesWithoutDiscoveryWritesThenGeneratesAndReopens(String generator) throws Exception {
        Path root = temporary.resolve(generator), file = root.resolve("discovery.mcreator");
        WorkspaceSettings settings = new WorkspaceSettings("discovery"); settings.setModName("Discovery"); settings.setVersion("1.0");
        settings.setCurrentGenerator(generator);
        UUID workspaceId;
        Map<String, JsonObject> contracts = new LinkedHashMap<>();
        Map<String, JsonObject> definitions = new LinkedHashMap<>();
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings);
             var session = attach(workspace, UUID.randomUUID())) {
            workspaceId = session.workspaceId();
            Map<String, String> before = inventory(root);
            for (String type : List.of("item", "recipe")) {
                JsonObject payload = new JsonObject(); payload.addProperty("elementType", type);
                var query = Query.of(UUID.randomUUID(), workspaceId, Operation.GET_MOD_ELEMENT_FIELD_CONTRACT, payload);
                var observed = session.headlessEntry(PermissionProfile.READ_ONLY).query(query);
                assertEquals("succeeded", observed.status(), observed.toString());
                assertEquals(0, observed.revision());
                JsonObject contract = observed.data().getAsJsonObject();
                assertTrue(contract.get("complete").getAsBoolean(), contract.toString());
                assertEquals("available", contract.get("availability").getAsString());
                assertEquals(contract, session.mcpEntry(PermissionProfile.READ_ONLY).query(query).data());
                assertEquals(contract, session.uiEntry().query(query).data());
                contracts.put(type, contract);
                Set<String> paths = new HashSet<>();
                contract.getAsJsonArray("fields").forEach(f -> paths.add(f.getAsJsonObject().get("path").getAsString()));
                for (String name : ElementMappingSupport.fields(type)) assertTrue(paths.contains("/" + name), name);
                Path evidence = Path.of("build/reports/m1-discovery", generator + "-" + type + ".json");
                Files.createDirectories(evidence.getParent()); Files.writeString(evidence, UiCore.wireGson().toJson(observed));
            }
            var environment = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject()));
            contracts.forEach((type, contract) -> assertEquals(contract, environment.data().getAsJsonObject().getAsJsonObject("fieldContracts").get(type)));
            JsonObject optionsPayload = JsonParser.parseString("{\"elementType\":\"recipe\",\"mappingSource\":\"blocksitems\",\"search\":\"Items.STICK\",\"limit\":1}").getAsJsonObject();
            var options = session.headlessEntry(PermissionProfile.READ_ONLY).query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_FIELD_REFERENCE_OPTIONS, optionsPayload));
            assertEquals("succeeded", options.status(), options.toString());
            assertEquals("Items.STICK", options.data().getAsJsonObject().getAsJsonArray("options").get(0).getAsJsonObject().get("value").getAsString());
            assertEquals(before, inventory(root), "Discovery must not create or rewrite files, or download dependencies");
            long revision = 0;
            for (var entry : contracts.entrySet()) {
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, revision,
                        Operation.CREATE_MOD_ELEMENT, entry.getValue().getAsJsonObject("minimalExample").deepCopy())).result();
                assertEquals("committed", result.status(), result.diagnostics().toString()); revision = result.newRevision();
                JsonObject editorPayload = new JsonObject(); editorPayload.add("elementId", result.data().getAsJsonObject().getAsJsonObject("element").get("id"));
                var editor = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_MOD_ELEMENT_EDITOR, editorPayload));
                assertEquals(entry.getValue(), editor.data().getAsJsonObject().get("fieldContract"), "UI and SDK must share the Core contract");
            }
            // No dependency process is needed to exercise actual generator templates.
            for (var element : workspace.getModElements()) {
                var definition = element.getGeneratableElement();
                assertNotNull(definition);
                assertTrue(workspace.getGenerator().generateElement(definition), element.getName());
                definitions.put(element.getName(), JsonParser.parseString(Files.readString(root.resolve("elements/" + element.getName() + ".mod.json"))).getAsJsonObject());
            }
            assertTrue(workspace.getGenerator().generateBase());
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            var state = new MCreatorWorkspaceStateMapper().map(workspace, workspace.getFileManager().loadOrCreateProductMetadata(workspaceId));
            var issues = generator.startsWith("fabric-")
                    ? new dev.copperbench.generator.fabric.Fabric1211Generator(Path.of("."),
                        List.of(FABRIC_1201, FABRIC_1211, FABRIC_261, FABRIC_262).stream().filter(p -> p.generatorId().equals(generator)).findFirst().orElseThrow()).validate(state)
                    : new dev.copperbench.generator.neoforge.NeoForge1211Generator(Path.of("."),
                        List.of(NEOFORGE_1201, NEOFORGE_1211, NEOFORGE_261, NEOFORGE_262).stream().filter(p -> p.generatorId().equals(generator)).findFirst().orElseThrow()).validate(state);
            assertTrue(issues.isEmpty(), "Task validation must accept the same native definitions: " + issues);
            try (var sources = Files.walk(root.resolve("src"))) {
                assertTrue(sources.anyMatch(p -> p.toString().endsWith(".java")));
            }
        }
        try (Workspace reopened = Workspace.readFromFS(file.toFile(), null); var session = attach(reopened, workspaceId)) {
            assertEquals(2, reopened.getModElements().size());
            Item item = (Item) reopened.getModElementByName("discovery_item").getGeneratableElement();
            assertEquals(16, item.stackSize); assertEquals(1.6, item.attackSpeed);
            Recipe recipe = (Recipe) reopened.getModElementByName("discovery_recipe").getGeneratableElement();
            assertEquals("Items.STICK", recipe.recipeSlots[0].getUnmappedValue());
            assertEquals("Items.DIAMOND", recipe.recipeReturnStack.getUnmappedValue());
            for (var entry : definitions.entrySet())
                assertEquals(entry.getValue(), JsonParser.parseString(Files.readString(root.resolve("elements/" + entry.getKey() + ".mod.json"))));
            for (String type : contracts.keySet()) assertEquals(contracts.get(type), ElementFieldContract.discover(type, generator));
        }
    }

    @Test void shapesExposeActualDefaultsNestedAdaptersConditionsAndGeneratorLimits() throws Exception {
        JsonObject item = ElementFieldContract.discover("item", "fabric-1.21.1");
        assertTrue(item.get("complete").getAsBoolean(), item.toString());
        JsonObject speed = field(item, "/attackSpeed"), stack = field(item, "/stackSize");
        assertEquals(1.6, speed.get("default").getAsDouble());
        assertEquals(64, stack.get("default").getAsInt());
        assertEquals(1, stack.getAsJsonObject("inputSchema").get("minimum").getAsInt());
        assertEquals(99, stack.getAsJsonObject("inputSchema").get("maximum").getAsInt());
        assertTrue(field(item, "/projectile").getAsJsonObject("requiredWhen").getAsJsonArray("expressions").toString().contains("enableRanged"));
        assertTrue(field(item, "/states").get("inputSchema").toString().contains("stateMap"));
        assertTrue(field(item, "/states").get("inputSchema").toString().contains("Property names must be unique"));
        assertTrue(field(item, "/specialInformation").get("inputSchema").toString().contains("fixedValue"));
        assertFalse(item.getAsJsonObject("generatorRestrictions").get("scope").getAsString().isBlank());
        assertNotNull(GenericFieldInputContract.validate(Item.class.getField("specialInformation"), JsonParser.parseString("{\"fixedValue\":1}"), "/specialInformation"));
        assertNull(GenericFieldInputContract.validate(Item.class.getField("specialInformation"), JsonParser.parseString("{\"fixedValue\":[\"tip\"]}"), "/specialInformation"));
        assertEquals("FIELD_VALUE_OUT_OF_RANGE", ElementMappingSupport.unsupportedChange("item", new JsonObject(), JsonParser.parseString("{\"maxStackSize\":100}").getAsJsonObject()).code());
        assertEquals("FIELD_VALUE_OUT_OF_RANGE", GenericFieldInputContract.validate(Recipe.class.getField("recipeSlots"),
                JsonParser.parseString("[\"Items.STICK\"]"), "/recipeSlots").code());
    }

    @Test void unknownUnloadedUnsupportedAndUnexposedRemainDistinct() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ElementFieldContract.discover("not_a_type", "fabric-1.21.1"));
        JsonObject unloaded = ElementFieldContract.discover("item", "missing-generator");
        assertEquals("not_exposed", unloaded.get("availability").getAsString());
        assertEquals("GENERATOR_NOT_LOADED", unloaded.get("reasonCode").getAsString());
        assertEquals("not_exposed", ElementFieldContract.discover("block", "fabric-1.21.1").get("availability").getAsString());
        assertEquals("unsupported", ElementFieldContract.discover("beblock", "fabric-1.21.1").get("availability").getAsString());
        assertThrows(IllegalArgumentException.class, () -> ElementFieldContract.referenceOptions("item", "fabric-1.21.1", "../private", "", 0, 100));
    }

    @Test void nativeGenerationValidatesRecipeSlotsAndBothStackSizeSpellings() {
        JsonObject recipe = ElementFieldContract.discover("recipe", "fabric-1.21.1").getAsJsonObject("minimalExample").getAsJsonObject("initialValues").deepCopy();
        assertNull(ElementGenerationValidation.validate("recipe", recipe, Set.of()));
        recipe.addProperty("recipeReturnStack", "CUSTOM:missing_item");
        assertEquals("FIELD_REFERENCE_INVALID", ElementGenerationValidation.validate("recipe", recipe, Set.of()).code());
        assertNull(ElementGenerationValidation.validate("recipe", recipe, Set.of("missing_item")));
        recipe.add("recipeReturnStack", JsonNull.INSTANCE);
        assertEquals("FIELD_REQUIRED_BY_CONDITION", ElementGenerationValidation.validate("recipe", recipe, Set.of()).code());
        recipe.addProperty("recipeReturnStack", "Items.DIAMOND");
        for (int i = 0; i < 9; i++) recipe.getAsJsonArray("recipeSlots").set(i, new JsonPrimitive(""));
        assertEquals("FIELD_REQUIRED", ElementGenerationValidation.validate("recipe", recipe, Set.of()).code());
        assertNull(ElementGenerationValidation.validate("item", JsonParser.parseString("{\"maxStackSize\":99}").getAsJsonObject(), Set.of()));
        assertEquals("FIELD_VALUE_OUT_OF_RANGE", ElementGenerationValidation.validate("item",
                JsonParser.parseString("{\"stackSize\":100}").getAsJsonObject(), Set.of()).code());
        assertEquals("FIELD_ALIAS_CONFLICT", ElementGenerationValidation.validate("item",
                JsonParser.parseString("{\"stackSize\":1,\"maxStackSize\":2}").getAsJsonObject(), Set.of()).code());
    }

    private static JsonObject field(JsonObject contract, String path) {
        return contract.getAsJsonArray("fields").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(f -> f.get("path").getAsString().equals(path)).findFirst().orElseThrow();
    }
    private static MCreatorWorkspaceSession attach(Workspace workspace, UUID id) throws Exception {
        return MCreatorWorkspaceSession.attach(workspace, id, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }
    private static Map<String, String> inventory(Path root) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                if (name.equals(".copperbench/workspace.write.lock")) result.put(name, "lock:" + Files.size(path));
                else result.put(name, Files.isRegularFile(path) ? WorkspaceExecutionSnapshot.sha256(path) : "directory");
            }
        }
        return result;
    }
}
