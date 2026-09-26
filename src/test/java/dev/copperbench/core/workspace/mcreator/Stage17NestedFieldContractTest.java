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

class Stage17NestedFieldContractTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private static final String VALID_TRADE = """
            {"villagerProfession":"WANDERING_TRADER","trades":[{"price1":"Items.EMERALD","price2":"",
            "offer":"Items.DIAMOND","countPrice1":2,"countPrice2":1,"countOffer":3,"level":1,
            "maxTrades":37,"xp":5,"priceMultiplier":0.05}]}
            """;

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void nestedUpdatesAndPlansPreserveBytesOnFailureAndReopenValidValues(String generator) throws Exception {
        Path document = root.resolve("nested_contract.mcreator");
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings(generator));
             MCreatorWorkspaceSession session = attach(workspace, UUID.randomUUID())) {
            var result = create(session, 0, "villagertrade", "trade_probe", VALID_TRADE);
            assertEquals("committed", result.status(), result.diagnostics().toString());
            String id = result.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            Path definition = root.resolve("elements/trade_probe.mod.json");
            byte[] original = Files.readAllBytes(definition);
            JsonObject update = new JsonObject(); update.addProperty("elementId", id);
            update.add("changes", JsonParser.parseString("[{\"path\":\"/trades/0/countOffer\",\"value\":1.0000000000000001}]"));
            var invalid = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", invalid.status()); assertEquals("FIELD_VALUE_OUT_OF_RANGE", invalid.diagnostics().getFirst().code());
            assertEquals(1, invalid.newRevision()); assertArrayEquals(original, Files.readAllBytes(definition));
            JsonObject operation = new JsonObject(); operation.addProperty("operation", "update_mod_element"); operation.add("payload", update);
            JsonArray operations = new JsonArray(); operations.add(operation);
            JsonObject plan = new JsonObject(); plan.add("operations", operations); plan.addProperty("expectedRevision", 1); plan.addProperty("idempotencyKey", "invalid-nested-count");
            var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES, plan));
            assertEquals("failed", planned.status());
            assertTrue(planned.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_VALUE_OUT_OF_RANGE")));
            assertArrayEquals(original, Files.readAllBytes(definition));
            JsonArray trades = JsonParser.parseString(VALID_TRADE).getAsJsonObject().getAsJsonArray("trades");
            trades.get(0).getAsJsonObject().addProperty("countOffer", 7);
            JsonObject change = new JsonObject(); change.addProperty("path", "/fields/trades"); change.add("value", trades);
            JsonArray changes = new JsonArray(); changes.add(change); update.add("changes", changes);
            var accepted = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("committed", accepted.status(), accepted.diagnostics().toString());
            var item = create(session, 2, "item", "repair_probe", "{\"repairItems\":[\"Items.DIAMOND\",{\"value\":\"Items.EMERALD\"}],\"providedBannerPatterns\":[\"pattern_name\"]}");
            assertEquals("committed", item.status(), item.diagnostics().toString());
            var potion = create(session, 3, "potion", "potion_probe", "{\"effects\":[{\"effect\":\"SPEED\",\"duration\":2400,\"amplifier\":1,\"ambient\":false,\"showParticles\":true}]}");
            assertEquals("committed", potion.status(), potion.diagnostics().toString());
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            var trade = (net.mcreator.element.types.VillagerTrade) reopened.getModElementByName("trade_probe").getGeneratableElement();
            assertEquals(7, trade.trades.getFirst().countOffer); assertEquals(37, trade.trades.getFirst().maxTrades);
            assertEquals("Items.DIAMOND", trade.trades.getFirst().offer.getUnmappedValue());
            var item = (net.mcreator.element.types.Item) reopened.getModElementByName("repair_probe").getGeneratableElement();
            assertEquals("Items.EMERALD", item.repairItems.get(1).getUnmappedValue());
            assertEquals(java.util.List.of("pattern_name"), item.providedBannerPatterns);
            var potion = (net.mcreator.element.types.Potion) reopened.getModElementByName("potion_probe").getGeneratableElement();
            assertEquals(2400, potion.effects.getFirst().duration); assertFalse(potion.effects.getFirst().ambient);
        }
    }

    @Test void importedUnknownTradeFieldsAreNotLostOnAnUnrelatedEdit() throws Exception {
        Path document = root.resolve("nested_contract.mcreator");
        UUID workspaceId = UUID.randomUUID();
        String id;
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings("fabric-1.21.1"));
             MCreatorWorkspaceSession session = attach(workspace, workspaceId)) {
            var created = create(session, 0, "villagertrade", "trade_probe", VALID_TRADE);
            assertEquals("committed", created.status());
            id = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
        }
        Path definition = root.resolve("elements/trade_probe.mod.json");
        JsonObject raw = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
        raw.getAsJsonObject("definition").getAsJsonArray("trades").get(0).getAsJsonObject().addProperty("thirdPartyFlag", "keep");
        Files.writeString(definition, raw.toString());
        byte[] original = Files.readAllBytes(definition);
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null);
             MCreatorWorkspaceSession session = attach(workspace, workspaceId)) {
            JsonObject update = new JsonObject(); update.addProperty("elementId", id);
            update.add("changes", JsonParser.parseString("[{\"path\":\"/description\",\"value\":\"unrelated\"}]"));
            var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", rejected.status());
            assertEquals("FIELD_PRESERVATION_REQUIRES_REVIEW", rejected.diagnostics().getFirst().code());
            assertTrue(rejected.diagnostics().getFirst().path().endsWith("/trades/0/thirdPartyFlag"));
            assertEquals(1, rejected.newRevision()); assertArrayEquals(original, Files.readAllBytes(definition));
        }
        assertArrayEquals(original, Files.readAllBytes(definition));
    }

    @Test void transientBiomeFieldsCannotBeAdvertisedAsDurableWrites() throws Exception {
        reject("biome", new String[][] {{"{\"groundBlock\":\"Blocks.GRASS_BLOCK\",\"undergroundBlock\":\"Blocks.STONE\",\"genDepthMin\":0.3}", "FIELD_UNSUPPORTED", "/genDepthMin"}});
    }

    @Test void nestedTradeIntegerCannotBeRoundedAndUnknownKeysCannotDisappear() throws Exception {
        reject("villagertrade", new String[][] {
                {"{\"trades\":[{\"countOffer\":1.0000000000000001}]}", "FIELD_VALUE_OUT_OF_RANGE", "/trades/0/countOffer"},
                {"{\"trades\":[{\"countOffer\":100}]}", "FIELD_VALUE_OUT_OF_RANGE", "/trades/0/countOffer"},
                {"{\"trades\":[{\"priceMultiplier\":\"0.5\"}]}", "FIELD_TYPE_INVALID", "/trades/0/priceMultiplier"},
                {"{\"trades\":[{\"typo\":true}]}", "FIELD_UNSUPPORTED", "/trades/0/typo"},
                {"{\"fields\":{\"trades\":[null]}}", "FIELD_TYPE_INVALID", "/fields/trades/0"}
        });
    }

    @Test void referenceAndStringCollectionsCannotCoerceNumbersToText() throws Exception {
        reject("item", new String[][] {
                {"{\"repairItems\":[42]}", "FIELD_TYPE_INVALID", "/repairItems/0"},
                {"{\"repairItems\":[{\"value\":true}]}", "FIELD_TYPE_INVALID", "/repairItems/0/value"},
                {"{\"providedBannerPatterns\":[42]}", "FIELD_TYPE_INVALID", "/providedBannerPatterns/0"},
                {"{\"attributeModifiers\":[{\"operation\":\"typo\"}]}", "FIELD_ENUM_INVALID", "/attributeModifiers/0/operation"},
                {"{\"attributeModifiers\":[{\"armorPieces\":[1]}]}", "FIELD_TYPE_INVALID", "/attributeModifiers/0/armorPieces/0"}
        });
    }

    @Test void nestedBooleansDoNotUseGsonTextCoercion() throws Exception {
        reject("potion", new String[][] {{"{\"effects\":[{\"ambient\":\"false\"}]}", "FIELD_TYPE_INVALID", "/effects/0/ambient"}});
    }

    private void reject(String type, String[][] inputs) throws Exception {
        WorkspaceSettings settings = settings("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("nested_contract.mcreator").toFile(), settings);
             MCreatorWorkspaceSession session = attach(workspace, UUID.randomUUID())) {
            for (String[] input : inputs) {
                var result = create(session, 0, type, "invalid_input", input[0]);
                assertEquals("rejected", result.status(), input[0]);
                assertEquals(input[1], result.diagnostics().getFirst().code(), result.diagnostics().toString());
                assertTrue(result.diagnostics().getFirst().path().endsWith(input[2]), result.diagnostics().toString());
                assertEquals(0, result.newRevision());
                assertNull(workspace.getModElementByName("invalid_input"));
                assertFalse(Files.exists(root.resolve("elements/invalid_input.mod.json")));
            }
        }
    }

    private static WorkspaceSettings settings(String generator) {
        WorkspaceSettings settings = new WorkspaceSettings("nested_contract");
        settings.setModName("Nested Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        return settings;
    }

    private static MCreatorWorkspaceSession attach(Workspace workspace, UUID id) throws Exception {
        return MCreatorWorkspaceSession.attach(workspace, id,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }

    private static CommandResult create(MCreatorWorkspaceSession session, long revision, String type, String name, String values) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", name);
        payload.add("initialValues", JsonParser.parseString(values));
        return session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
    }
}
