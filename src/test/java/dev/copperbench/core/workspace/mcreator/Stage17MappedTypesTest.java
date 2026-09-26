package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.Recipe;
import net.mcreator.element.types.Plant;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage17MappedTypesTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void functionCommandInputsCannotBeSilentlyDiscardedOrCoerced() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("function_contract");
        settings.setModName("Function Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("function_contract.mcreator").toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            for (String[] input : new String[][] {
                    {"{\"commands\":\"say lost\"}", "FIELD_TYPE_INVALID", "/commands"},
                    {"{\"commands\":[42]}", "FIELD_TYPE_INVALID", "/commands/0"},
                    {"{\"commands\":[null]}", "FIELD_TYPE_INVALID", "/commands/0"},
                    {"{\"commands\":null}", "FIELD_TYPE_INVALID", "/commands"},
                    {"{\"code\":42}", "FIELD_TYPE_INVALID", "/code"},
                    {"{\"fields\":{\"code\":null}}", "FIELD_TYPE_INVALID", "/fields/code"},
                    {"{\"fields\":{\"commands\":[true]}}", "FIELD_TYPE_INVALID", "/fields/commands/0"},
                    {"{\"commands\":[\"say first\"],\"code\":\"say lost\\n\"}", "FIELD_ALIAS_CONFLICT", "/code"}
            }) {
                var result = create(session, 0, "function", "invalid_function", input[0]).result();
                assertEquals("rejected", result.status(), input[0]);
                assertEquals(input[1], result.diagnostics().getFirst().code(), input[0]);
                assertTrue(result.diagnostics().getFirst().path().endsWith(input[2]), result.diagnostics().toString());
                assertEquals(0, result.newRevision());
                assertNull(workspace.getModElementByName("invalid_function"));
            }
            var codeOnly = create(session, 0, "function", "code_only", "{\"code\":\"say exact\"}").result();
            assertEquals("committed", codeOnly.status(), codeOnly.diagnostics().toString());
            assertEquals("say exact", ((net.mcreator.element.types.Function) workspace.getModElementByName("code_only").getGeneratableElement()).code);
            var empty = create(session, 1, "function", "empty_body", "{\"commands\":[]}").result();
            assertEquals("committed", empty.status(), empty.diagnostics().toString());
            assertEquals("", ((net.mcreator.element.types.Function) workspace.getModElementByName("empty_body").getGeneratableElement()).code);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void functionBodiesGenerateAndReopenWhileInvalidUpdatesAndPlansPreserveBytes(String generator) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("function_contract");
        settings.setModName("Function Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        Path document = root.resolve("function_contract.mcreator");
        UUID workspaceId = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var environment = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject()));
            assertTrue(environment.data().getAsJsonObject().getAsJsonObject("fieldContracts").has("function"));
            var created = create(session, 0, "function", "function_probe", """
                    {"fields":{"commands":["say first","say second"]},"code":"say first\\nsay second\\n"}
                    """);
            assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
            String id = created.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            Path definition = root.resolve("elements/function_probe.mod.json");
            byte[] original = java.nio.file.Files.readAllBytes(definition);
            JsonObject update = JsonParser.parseString("""
                    {"changes":[{"path":"/code","value":"say silently_lost\\n"}]}
                    """).getAsJsonObject();
            update.addProperty("elementId", id);
            var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", rejected.status());
            assertEquals("FIELD_ALIAS_CONFLICT", rejected.diagnostics().getFirst().code());
            assertEquals(1, rejected.newRevision());
            assertArrayEquals(original, java.nio.file.Files.readAllBytes(definition));
            JsonObject operation = new JsonObject(); operation.addProperty("operation", "update_mod_element"); operation.add("payload", update);
            JsonArray operations = new JsonArray(); operations.add(operation);
            JsonObject plan = new JsonObject(); plan.add("operations", operations);
            plan.addProperty("expectedRevision", 1); plan.addProperty("idempotencyKey", "invalid-function-body");
            var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.PLAN_WORKSPACE_CHANGES, plan));
            assertEquals("failed", planned.status());
            assertTrue(planned.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_ALIAS_CONFLICT")));
            assertArrayEquals(original, java.nio.file.Files.readAllBytes(definition));
            update.add("changes", JsonParser.parseString("""
                    [{"path":"/code","value":"say updated\\n"},{"path":"/fields/commands","value":["say updated"]}]
                    """));
            var updated = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("committed", updated.status(), updated.diagnostics().toString());
            assertTrue(workspace.getGenerator().generateBase());
            var element = workspace.getModElementByName("function_probe");
            assertTrue(workspace.getGenerator().generateElement(element.getGeneratableElement()));
            Path generated = element.getAssociatedFiles().stream().map(java.io.File::toPath)
                    .filter(p -> p.toString().endsWith(".mcfunction")).findFirst().orElseThrow();
            assertEquals("say updated", java.nio.file.Files.readString(generated).strip());
        }
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null)) {
            var stored = (net.mcreator.element.types.Function) workspace.getModElementByName("function_probe").getGeneratableElement();
            assertEquals("say updated\n", stored.code);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void plantDefaultsAndMissingInputsUseThePublicFieldContract(String generator) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("plant_defaults");
        settings.setModName("Plant Defaults"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        Path document = root.resolve("plant_defaults.mcreator");
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                var missingEffect = create(session, 0, "plant", "missing_effect", "{\"texture\":\"minecraft:poppy\"}");
                assertEquals("rejected", missingEffect.result().status());
                assertEquals("FIELD_REQUIRED_BY_CONDITION", missingEffect.result().diagnostics().getFirst().code());
                assertEquals("/initialValues/suspiciousStewEffect", missingEffect.result().diagnostics().getFirst().path());
                assertNull(workspace.getModElementByName("missing_effect"));
                assertEquals(0, missingEffect.result().newRevision());
                var missingTexture = create(session, 0, "plant", "missing_texture", "{\"suspiciousStewEffect\":\"SPEED\"}");
                assertEquals("FIELD_REQUIRED", missingTexture.result().diagnostics().getFirst().code());
                assertEquals("/initialValues/texture", missingTexture.result().diagnostics().getFirst().path());
                assertNull(workspace.getModElementByName("missing_texture"));
                var created = create(session, 0, "plant", "simple_flower", "{\"texture\":\"minecraft:poppy\",\"suspiciousStewEffect\":\"SPEED\"}");
                assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
                Plant stored = (Plant) workspace.getModElementByName("simple_flower").getGeneratableElement();
                assertEquals("normal", stored.plantType);
                assertEquals(12, stored.renderType);
                assertEquals("PLANT", stored.soundOnStep.getUnmappedValue());
                assertEquals("DEFAULT", stored.colorOnMap.getUnmappedValue());
                assertEquals("SPEED", stored.suspiciousStewEffect.getUnmappedValue());
                assertTrue(workspace.getGenerator().generateElement(stored));
            }
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            Plant stored = (Plant) reopened.getModElementByName("simple_flower").getGeneratableElement();
            assertEquals("normal", stored.plantType);
            assertEquals("PLANT", stored.soundOnStep.getUnmappedValue());
            assertEquals(12, stored.renderType);
        }
    }

    @Test void itemAndRecipeWritesReachTheirStorageTypesAndUnsupportedFieldsAreRejected() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("mapped_types");
        settings.setModName("Mapped Types"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("mapped_types.mcreator").toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                var food = create(session, 0, "item", "test_food", "{\"isFood\":true,\"nutritionalValue\":7,\"saturation\":0.8}");
                assertEquals("committed", food.result().status(), food.result().diagnostics().toString());
                Item item = (Item) workspace.getModElementByName("test_food").getGeneratableElement();
                assertTrue(item.isFood); assertEquals(7, item.nutritionalValue); assertEquals(0.8, item.saturation);
                var recipe = create(session, 1, "recipe", "test_recipe", """
                    {"recipeType":"Crafting","recipeRetstackSize":2,"recipeReturnStack":"Blocks.STONE",
                     "recipeSlots":["Blocks.COBBLESTONE","","","","","","","",""]}
                    """);
                assertEquals("committed", recipe.result().status(), recipe.result().diagnostics().toString());
                Recipe stored = (Recipe) workspace.getModElementByName("test_recipe").getGeneratableElement();
                assertEquals(2, stored.recipeRetstackSize);
                assertEquals("Blocks.STONE", stored.recipeReturnStack.getUnmappedValue());
                assertEquals("Blocks.COBBLESTONE", stored.recipeSlots[0].getUnmappedValue());
                var invalid = create(session, 2, "recipe", "bad_recipe", "{\"madeUpField\":true}");
                assertEquals("rejected", invalid.result().status());
                assertEquals("FIELD_UNSUPPORTED", invalid.result().diagnostics().getFirst().code());
                assertEquals(2, invalid.result().newRevision());
                assertNull(workspace.getModElementByName("bad_recipe"));
            }
        }
    }

    @Test void unannotatedIntegerFieldsRejectOverflowAndFractionalPrecisionLoss() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("integer_fields");
        settings.setModName("Integer Fields"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("integer_fields.mcreator").toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            for (String input : java.util.List.of("2147483648", "-2147483649", "4294967296", "1.0000000000000001")) {
                var rejected = create(session, 0, "item", "invalid_duration", "{\"musicDiscLengthInTicks\":" + input + "}");
                assertEquals("rejected", rejected.result().status(), input);
                assertEquals("FIELD_VALUE_OUT_OF_RANGE", rejected.result().diagnostics().getFirst().code(), input);
                assertTrue(rejected.result().diagnostics().getFirst().path().endsWith("/musicDiscLengthInTicks"));
                assertEquals(0, rejected.result().newRevision());
                assertNull(workspace.getModElementByName("invalid_duration"));
            }
            var accepted = create(session, 0, "item", "valid_duration", "{\"musicDiscLengthInTicks\":1e2}");
            assertEquals("committed", accepted.result().status(), accepted.result().diagnostics().toString());
            assertEquals(100, ((Item) workspace.getModElementByName("valid_duration").getGeneratableElement()).musicDiscLengthInTicks);
        }
    }

    private static CommandOutcome create(MCreatorWorkspaceSession session, long revision, String type, String name, String values) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", name);
        payload.add("initialValues", JsonParser.parseString(values));
        return session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload));
    }
}
