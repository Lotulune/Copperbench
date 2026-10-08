package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.Recipe;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real headless Core discovery and persistence; no generator or gameplay claims. */
class Stage18ItemRecipeFieldContractTest {
    @TempDir Path root;

    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void discoveredItemAndRecipeInputsPersistAndRejectedUpdatesLeaveBytesAndRevisionIntact() throws Exception {
        Path document = root.resolve("field_discovery.mcreator");
        WorkspaceSettings settings = new WorkspaceSettings("field_discovery");
        settings.setModName("Field Discovery");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var headless = session.headlessEntry(PermissionProfile.WORKSPACE);
            var environment = headless.query(Query.of(UUID.randomUUID(), session.workspaceId(),
                    Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject()));
            assertEquals("succeeded", environment.status(), environment.diagnostics().toString());
            JsonObject contracts = environment.data().getAsJsonObject().getAsJsonObject("fieldContracts");
            assertEquals("partial", contracts.getAsJsonObject("item").get("coverage").getAsString());
            assertEquals("partial", contracts.getAsJsonObject("recipe").get("coverage").getAsString());
            assertEquals(99, field(contracts, "item", "stackSize").get("max").getAsInt());
            assertEquals("array", field(contracts, "recipe", "recipeSlots").get("type").getAsString());

            var item = create(session, 0, "item", "contract_item", """
                    {"displayName":"Contract Item","stackSize":1,"rarity":"UNCOMMON","immuneToFire":true}
                    """);
            assertEquals("committed", item.status(), item.diagnostics().toString());
            var recipe = create(session, 1, "recipe", "contract_recipe", """
                    {"recipeType":"Crafting","recipeRetstackSize":1,"recipeShapeless":false,
                     "recipeSlots":["Items.COPPER_INGOT","Items.COPPER_INGOT","Items.COPPER_INGOT",
                                    "Items.COPPER_INGOT","Items.CLOCK","Items.COPPER_INGOT",
                                    "Items.COPPER_INGOT","Items.REDSTONE","Items.COPPER_INGOT"],
                     "recipeReturnStack":{"value":"Items.COMPASS"}}
                    """);
            assertEquals("committed", recipe.status(), recipe.diagnostics().toString());
            String itemId = item.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            String recipeId = recipe.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            Path itemFile = root.resolve("elements/contract_item.mod.json");
            Path recipeFile = root.resolve("elements/contract_recipe.mod.json");
            byte[] itemBytes = Files.readAllBytes(itemFile);
            byte[] recipeBytes = Files.readAllBytes(recipeFile);

            var invalidStack = update(session, 2, itemId, "/stackSize", "0");
            assertEquals("rejected", invalidStack.status());
            assertEquals("FIELD_VALUE_OUT_OF_RANGE", invalidStack.diagnostics().getFirst().code());
            assertEquals(2, invalidStack.newRevision());
            assertArrayEquals(itemBytes, Files.readAllBytes(itemFile));
            var invalidRecipe = update(session, 2, recipeId, "/recipeType", "\"crafting\"");
            assertEquals("rejected", invalidRecipe.status());
            assertEquals("FIELD_ENUM_INVALID", invalidRecipe.diagnostics().getFirst().code());
            assertEquals(2, invalidRecipe.newRevision());
            assertArrayEquals(recipeBytes, Files.readAllBytes(recipeFile));

            var accepted = update(session, 2, itemId, "/fields/stackSize", "4");
            assertEquals("committed", accepted.status(), accepted.diagnostics().toString());
            assertEquals(3, accepted.newRevision());
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            Item item = (Item) reopened.getModElementByName("contract_item").getGeneratableElement();
            assertEquals(4, item.stackSize);
            assertEquals("UNCOMMON", item.rarity);
            assertTrue(item.immuneToFire);
            Recipe recipe = (Recipe) reopened.getModElementByName("contract_recipe").getGeneratableElement();
            assertEquals("Crafting", recipe.recipeType);
            assertEquals(1, recipe.recipeRetstackSize);
            assertEquals(9, recipe.recipeSlots.length);
            assertEquals("Items.CLOCK", recipe.recipeSlots[4].getUnmappedValue());
            assertEquals("Items.COMPASS", recipe.recipeReturnStack.getUnmappedValue());
        }
    }

    private static JsonObject field(JsonObject contracts, String type, String name) {
        for (var raw : contracts.getAsJsonObject(type).getAsJsonArray("fields"))
            if (name.equals(raw.getAsJsonObject().get("name").getAsString())) return raw.getAsJsonObject();
        throw new AssertionError("Missing field " + type + "." + name);
    }

    private static CommandResult create(MCreatorWorkspaceSession session, long revision, String type, String name, String values) {
        JsonObject payload = new JsonObject();
        payload.addProperty("elementType", type);
        payload.addProperty("name", name);
        payload.add("initialValues", JsonParser.parseString(values));
        return session.headlessEntry(PermissionProfile.WORKSPACE).execute(Command.of(UUID.randomUUID(),
                session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
    }

    private static CommandResult update(MCreatorWorkspaceSession session, long revision, String id, String path, String value) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementId", id);
        JsonObject change = new JsonObject(); change.addProperty("path", path); change.add("value", JsonParser.parseString(value));
        var changes = new com.google.gson.JsonArray(); changes.add(change); payload.add("changes", changes);
        return session.headlessEntry(PermissionProfile.WORKSPACE).execute(Command.of(UUID.randomUUID(),
                session.workspaceId(), revision, Operation.UPDATE_MOD_ELEMENT, payload)).result();
    }
}
