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

class Stage17SpecializedFieldContractTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void projectileCannotCoerceTextToBooleanOrTruncateNumbers() throws Exception {
        rejectInputs("projectile", new String[][] {
            {"{\"showParticles\":\"false\"}", "FIELD_TYPE_INVALID", "/showParticles"},
            {"{\"knockback\":1.0000000000000001}", "FIELD_VALUE_OUT_OF_RANGE", "/knockback"},
            {"{\"damage\":10001}", "FIELD_VALUE_OUT_OF_RANGE", "/damage"},
            {"{\"fields\":{\"power\":null}}", "FIELD_TYPE_INVALID", "/fields/power"},
            {"{\"onHitsBlock\":{\"name\":\"lost\"}}", "FIELD_TYPE_INVALID", "/onHitsBlock"}
        });
    }

    @Test void achievementCannotDiscardRewardListsOrCoerceMembers() throws Exception {
        rejectInputs("achievement", new String[][] {
            {"{\"rewardLoot\":\"lost\"}", "FIELD_TYPE_INVALID", "/rewardLoot"},
            {"{\"rewardRecipes\":[42]}", "FIELD_TYPE_INVALID", "/rewardRecipes/0"},
            {"{\"rewardXP\":1.0000000000000001}", "FIELD_VALUE_OUT_OF_RANGE", "/rewardXP"},
            {"{\"frame\":\"typo\"}", "FIELD_ENUM_INVALID", "/frame"},
            {"{\"fields\":{\"title\":false}}", "FIELD_TYPE_INVALID", "/fields/title"}
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void validUpdatesGenerateAndReopenWhileInvalidUpdatesAndPlansPreserveBytes(String generator) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("specialized_contract");
        settings.setModName("Specialized Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        Path document = root.resolve("specialized_contract.mcreator");
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var environment = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject()));
            JsonObject contracts = environment.data().getAsJsonObject().getAsJsonObject("fieldContracts");
            assertTrue(contracts.has("projectile")); assertTrue(contracts.has("achievement"));
            long revision = 0;
            for (String type : new String[] {"projectile", "achievement"}) {
                JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", type + "_probe");
                payload.add("initialValues", JsonParser.parseString(type.equals("projectile")
                        ? "{\"fields\":{\"damage\":7,\"knockback\":1e2,\"showParticles\":true}}"
                        : "{\"fields\":{\"title\":\"Verified title\",\"frame\":\"goal\",\"rewardXP\":100,\"rewardLoot\":[],\"rewardRecipes\":[]}}"));
                var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertEquals("committed", created.status(), created.diagnostics().toString()); revision++;
                String id = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
                Path definition = root.resolve("elements/" + type + "_probe.mod.json");
                byte[] original = Files.readAllBytes(definition);
                String field = type.equals("projectile") ? "knockback" : "rewardXP";
                JsonObject update = new JsonObject(); update.addProperty("elementId", id);
                update.add("changes", JsonParser.parseString("[{\"path\":\"/fields/" + field + "\",\"value\":1.0000000000000001}]"));
                var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.UPDATE_MOD_ELEMENT, update)).result();
                assertEquals("rejected", rejected.status());
                assertEquals("FIELD_VALUE_OUT_OF_RANGE", rejected.diagnostics().getFirst().code());
                assertEquals(revision, rejected.newRevision());
                assertArrayEquals(original, Files.readAllBytes(definition));
                JsonObject operation = new JsonObject(); operation.addProperty("operation", "update_mod_element"); operation.add("payload", update);
                JsonArray operations = new JsonArray(); operations.add(operation);
                JsonObject plan = new JsonObject(); plan.add("operations", operations); plan.addProperty("expectedRevision", revision);
                plan.addProperty("idempotencyKey", type + "-invalid");
                var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES, plan));
                assertEquals("failed", planned.status());
                assertTrue(planned.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_VALUE_OUT_OF_RANGE")));
                assertArrayEquals(original, Files.readAllBytes(definition));
                update.add("changes", JsonParser.parseString("[{\"path\":\"/fields/" + field + "\",\"value\":42}]"));
                var accepted = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.UPDATE_MOD_ELEMENT, update)).result();
                assertEquals("committed", accepted.status(), accepted.diagnostics().toString()); revision++;
                byte[] updated = Files.readAllBytes(definition);
                update.add("changes", JsonParser.parseString("[{\"path\":\"/" + field + "\",\"value\":43},{\"path\":\"/fields/" + field + "\",\"value\":44}]"));
                var conflict = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.UPDATE_MOD_ELEMENT, update)).result();
                assertEquals("rejected", conflict.status());
                assertEquals("FIELD_ALIAS_CONFLICT", conflict.diagnostics().getFirst().code());
                assertArrayEquals(updated, Files.readAllBytes(definition));
            }
            assertTrue(workspace.getGenerator().generateBase());
            for (String type : new String[] {"projectile", "achievement"}) {
                var element = workspace.getModElementByName(type + "_probe");
                assertTrue(workspace.getGenerator().generateElement(element.getGeneratableElement()));
                if (type.equals("projectile")) {
                    Path generatedSource = element.getAssociatedFiles().stream().map(java.io.File::toPath)
                            .filter(p -> p.getFileName().toString().equals(element.getName() + "Entity.java")).findFirst().orElseThrow();
                    assertTrue(Files.readString(generatedSource).contains("setKnockback(42)"));
                } else {
                    Path json = element.getAssociatedFiles().stream().map(java.io.File::toPath)
                            .filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
                    JsonObject generated = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                    assertEquals(42, generated.getAsJsonObject("rewards").get("experience").getAsInt());
                    assertEquals("goal", generated.getAsJsonObject("display").get("frame").getAsString());
                }
            }
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            var projectile = (net.mcreator.element.types.Projectile) reopened.getModElementByName("projectile_probe").getGeneratableElement();
            assertEquals(42, projectile.knockback); assertEquals(7, projectile.damage); assertTrue(projectile.showParticles);
            var achievement = (net.mcreator.element.types.Achievement) reopened.getModElementByName("achievement_probe").getGeneratableElement();
            assertEquals(42, achievement.rewardXP); assertEquals("Verified title", achievement.achievementName);
            assertEquals("goal", achievement.achievementType);
        }
    }

    private void rejectInputs(String type, String[][] inputs) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("specialized_contract");
        settings.setModName("Specialized Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("specialized_contract.mcreator").toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            for (String[] input : inputs) {
                JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", "invalid_input");
                payload.add("initialValues", JsonParser.parseString(input[0]));
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertEquals("rejected", result.status(), input[0]);
                assertEquals(input[1], result.diagnostics().getFirst().code(), result.diagnostics().toString());
                assertTrue(result.diagnostics().getFirst().path().endsWith(input[2]), result.diagnostics().toString());
                assertEquals(0, result.newRevision());
                assertNull(workspace.getModElementByName("invalid_input"));
                assertFalse(Files.exists(root.resolve("elements/invalid_input.mod.json")));
            }
        }
    }
}
