package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.LootTable;
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

class Stage17LootTableContractTest {
    @TempDir Path root;
    private final UUID workspaceId = UUID.randomUUID();
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void nestedInvalidValuesAreRejectedWithoutPersistingOrAdvancingRevision() throws Exception {
        try (Workspace workspace = workspace(); MCreatorWorkspaceSession session = session(workspace)) {
            String[][] cases = {
                {"{\"pools\":[{\"entries\":[{\"weight\":1.0000000000000001}]}]}", "FIELD_VALUE_OUT_OF_RANGE", "/pools/0/entries/0/weight"},
                {"{\"pools\":[{\"entries\":[{\"weight\":4294967296}]}]}", "FIELD_VALUE_OUT_OF_RANGE", "/pools/0/entries/0/weight"},
                {"{\"pools\":[{\"entries\":[{\"weight\":\"2\"}]}]}", "FIELD_TYPE_INVALID", "/pools/0/entries/0/weight"},
                {"{\"pools\":[{\"hasBonusRolls\":\"false\"}]}", "FIELD_TYPE_INVALID", "/pools/0/hasBonusRolls"},
                {"{\"pools\":[{\"minRolls\":-1}]}", "FIELD_VALUE_OUT_OF_RANGE", "/pools/0/minRolls"},
                {"{\"pools\":[{\"minRolls\":2,\"maxRolls\":1}]}", "FIELD_VALUE_OUT_OF_RANGE", "/pools/0/maxRolls"},
                {"{\"pools\":[{\"entries\":[{\"type\":\"unsupported\"}]}]}", "FIELD_ENUM_INVALID", "/pools/0/entries/0/type"},
                {"{\"pools\":[{\"entries\":[{\"silkTouchMode\":3}]}]}", "FIELD_ENUM_INVALID", "/pools/0/entries/0/silkTouchMode"},
                {"{\"pools\":[{\"entries\":[{\"typo\":true}]}]}", "FIELD_UNSUPPORTED", "/pools/0/entries/0/typo"},
                {"{\"pools\":[{\"entries\":{}}]}", "FIELD_TYPE_INVALID", "/pools/0/entries"},
                {"{\"pools\":[null]}", "FIELD_TYPE_INVALID", "/pools/0"},
                {"{\"fields\":{\"pools\":[{\"minRolls\":1.5}]}}", "FIELD_VALUE_OUT_OF_RANGE", "/fields/pools/0/minRolls"}
            };
            for (String[] test : cases) {
                var result = create(session, test[0]).result();
                assertEquals("rejected", result.status(), test[0]);
                assertEquals(test[1], result.diagnostics().getFirst().code(), test[0]);
                assertTrue(result.diagnostics().getFirst().path().endsWith(test[2]), result.diagnostics().toString());
                assertEquals(0, result.newRevision());
                assertNull(workspace.getModElementByName("loot_contract"));
                assertFalse(Files.exists(root.resolve("elements/loot_contract.mod.json")));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void validNestedWritesReopenAndRejectedUpdatesLeaveDefinitionUnchanged(String generator) throws Exception {
        Path document = root.resolve("loot_contract.mcreator");
        try (Workspace workspace = workspace(generator); MCreatorWorkspaceSession session = session(workspace)) {
            var environment = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject()));
            assertTrue(environment.data().getAsJsonObject().getAsJsonObject("fieldContracts").has("loottable"));
            var created = create(session, """
                    {"pools":[{"minRolls":2,"entries":[{"weight":1e2,"minCount":2,"maxCount":4}]}]}
                    """);
            assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
            String id = created.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            Path definition = root.resolve("elements/loot_contract.mod.json");
            byte[] original = Files.readAllBytes(definition);
            JsonObject update = JsonParser.parseString("""
                    {"changes":[{"path":"/pools/0/entries/0/maxCount","value":1}]}
                    """).getAsJsonObject();
            update.addProperty("elementId", id);
            var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1,
                    Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", rejected.status());
            assertEquals("FIELD_VALUE_OUT_OF_RANGE", rejected.diagnostics().getFirst().code());
            assertEquals(1, rejected.newRevision());
            assertArrayEquals(original, Files.readAllBytes(definition));
            JsonObject operation = new JsonObject(); operation.addProperty("operation", "update_mod_element");
            operation.add("payload", update);
            JsonArray operations = new JsonArray(); operations.add(operation);
            JsonObject planning = new JsonObject(); planning.add("operations", operations);
            planning.addProperty("expectedRevision", 1); planning.addProperty("idempotencyKey", "invalid-loot-range");
            var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES, planning));
            assertEquals("failed", planned.status(), planned.toString());
            assertTrue(planned.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_VALUE_OUT_OF_RANGE")), planned.toString());
            assertArrayEquals(original, Files.readAllBytes(definition));
            assertTrue(workspace.getGenerator().generateBase());
            assertTrue(workspace.getGenerator().generateElement(workspace.getModElementByName("loot_contract").getGeneratableElement()));
            Path generated = workspace.getModElementByName("loot_contract").getAssociatedFiles().stream()
                    .map(java.io.File::toPath).filter(p -> p.getFileName().toString().equals("loot_contract.json")).findFirst().orElseThrow();
            JsonObject pool = JsonParser.parseString(Files.readString(generated)).getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject();
            assertEquals(2, pool.get("rolls").getAsInt());
            JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals(100, entry.get("weight").getAsInt());
            assertEquals(4, entry.getAsJsonArray("functions").get(0).getAsJsonObject().getAsJsonObject("count").get("max").getAsInt());
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            LootTable stored = (LootTable) reopened.getModElementByName("loot_contract").getGeneratableElement();
            assertEquals(2, stored.pools.getFirst().minrolls);
            assertEquals(2, stored.pools.getFirst().maxrolls);
            assertEquals(100, stored.pools.getFirst().entries.getFirst().weight);
            assertEquals(4, stored.pools.getFirst().entries.getFirst().maxCount);
        }
    }

    private Workspace workspace() throws Exception {
        return workspace("fabric-1.21.1");
    }

    @Test void importedPoolSpellingsAreReadWithoutWritingAndSurviveAnUnrelatedEdit() throws Exception {
        Path document = root.resolve("loot_contract.mcreator"), definition = root.resolve("elements/loot_contract.mod.json");
        try (Workspace workspace = workspace(); MCreatorWorkspaceSession session = session(workspace)) {
            assertEquals("committed", create(session, "{\"pools\":[{\"minRolls\":7,\"maxRolls\":9,\"entries\":[]}]}").result().status());
            workspace.getModElementByName("loot_contract").putMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_VALUES_METADATA, null);
        }
        byte[] original = Files.readAllBytes(definition);
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null); var session = session(workspace)) {
            assertArrayEquals(original, Files.readAllBytes(definition));
            String id = workspace.getModElementByName("loot_contract").getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA).toString();
            JsonObject update = JsonParser.parseString("{\"changes\":[{\"path\":\"/description\",\"value\":\"Reviewed import\"}]}").getAsJsonObject();
            update.addProperty("elementId", id);
            var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("committed", result.status(), result.diagnostics().toString());
            LootTable stored = (LootTable) workspace.getModElementByName("loot_contract").getGeneratableElement();
            assertEquals(7, stored.pools.getFirst().minrolls);
            assertEquals(9, stored.pools.getFirst().maxrolls);
        }
    }

    @Test void unknownImportedEntryDataRequiresReviewAndIsNeverDiscardedByAnUnrelatedEdit() throws Exception {
        Path document = root.resolve("loot_contract.mcreator"), definition = root.resolve("elements/loot_contract.mod.json");
        try (Workspace workspace = workspace(); MCreatorWorkspaceSession session = session(workspace)) {
            assertEquals("committed", create(session, "{\"pools\":[{\"entries\":[{\"weight\":2}]}]}").result().status());
        }
        JsonObject raw = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
        raw.getAsJsonObject("definition").getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                .get(0).getAsJsonObject().addProperty("futurePluginRule", "preserve-me");
        Files.writeString(definition, raw.toString());
        byte[] original = Files.readAllBytes(definition);
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null); var session = session(workspace)) {
            String id = workspace.getModElementByName("loot_contract").getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA).toString();
            JsonObject update = JsonParser.parseString("{\"changes\":[{\"path\":\"/description\",\"value\":\"Changed\"}]}").getAsJsonObject();
            update.addProperty("elementId", id);
            var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("rejected", result.status(), result.diagnostics().toString());
            assertEquals("FIELD_PRESERVATION_REQUIRES_REVIEW", result.diagnostics().getFirst().code());
            assertTrue(result.diagnostics().getFirst().path().endsWith("/pools/0/entries/0/futurePluginRule"));
            assertEquals(1, result.newRevision());
            assertArrayEquals(original, Files.readAllBytes(definition));
        }
        assertArrayEquals(original, Files.readAllBytes(definition));
    }

    private Workspace workspace(String generator) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("loot_contract");
        settings.setModName("Loot Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        return Workspace.createWorkspace(root.resolve("loot_contract.mcreator").toFile(), settings);
    }

    private MCreatorWorkspaceSession session(Workspace workspace) throws java.io.IOException {
        return MCreatorWorkspaceSession.attach(workspace, workspaceId,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }

    private static CommandOutcome create(MCreatorWorkspaceSession session, String values) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", "loottable"); payload.addProperty("name", "loot_contract");
        payload.add("initialValues", JsonParser.parseString(values));
        return session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload));
    }
}
