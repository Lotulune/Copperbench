package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.SpecializedFieldContract;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.AchievementEntry;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.element.types.Achievement;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementPersistenceTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void importedAndPartialMetadataAdvancementsRetainRealConditionsOnDescriptionEdit(boolean partialMetadata) throws Exception {
        var settings = new WorkspaceSettings("advancement_test");
        settings.setCurrentGenerator("datapack-1.21.1"); settings.setModName("Advancement Test"); settings.setVersion("1.0.0");
        Path document = root.resolve("advancement_test.mcreator");
        String original = SpecializedFieldContract.DEFAULT_ADVANCEMENT_TRIGGER_XML.replace("x=\"40\"", "x=\"120\"");
        String updated = original.replace("120", "160");
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
            var element = new ModElement(workspace, "legacy_advancement", ModElementType.ADVANCEMENT);
            workspace.addModElement(element);
            var achievement = new Achievement(element);
            achievement.achievementName = "Actual title"; achievement.achievementDescription = "Actual description";
            achievement.achievementIcon = new MItemBlock(workspace, "Items.DIAMOND");
            achievement.achievementType = "goal"; achievement.parent = new AchievementEntry(workspace, "ROOT");
            achievement.background = "Default"; achievement.showPopup = true; achievement.announceToChat = true;
            achievement.rewardXP = 7; achievement.rewardLoot = List.of(); achievement.rewardRecipes = List.of();
            achievement.triggerxml = original;
            workspace.getModElementManager().storeModElement(achievement);
            if (partialMetadata) {
                JsonObject metadata = new JsonObject(); metadata.addProperty("title", "Actual title");
                element.putMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_VALUES_METADATA, metadata);
            }
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            try (var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                UUID id = MCreatorWorkspaceStateMapper.elementId(session.workspaceId(), element);
                JsonObject payload = new JsonObject(); payload.addProperty("elementId", id.toString());
                var editor = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_MOD_ELEMENT_EDITOR, payload));
                Map<String, JsonObject> fields = new HashMap<>();
                editor.data().getAsJsonObject().getAsJsonArray("sections").forEach(section ->
                        section.getAsJsonObject().getAsJsonArray("fields").forEach(raw -> {
                            var field = raw.getAsJsonObject(); fields.put(field.get("path").getAsString(), field);
                        }));
                assertEquals(original, fields.get("/triggerxml").get("value").getAsString());
                assertEquals("Actual title", fields.get("/title").get("value").getAsString());
                assertFalse(fields.get("/description").get("readOnly").getAsBoolean());
                update(session, id, 0, "/description", "Edited description");
                Achievement stored = (Achievement) element.getGeneratableElement();
                assertEquals(original, stored.triggerxml);
                assertEquals("Actual title", stored.achievementName);
                assertEquals(7, stored.rewardXP);
                update(session, id, 1, "/triggerxml", updated);
                assertTrue(workspace.getGenerator().generateBase());
                assertTrue(workspace.getGenerator().generateElement(element.getGeneratableElement()));
                Path generated = element.getAssociatedFiles().stream().map(java.io.File::toPath)
                        .filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
                JsonObject json = JsonParser.parseString(Files.readString(generated)).getAsJsonObject();
                assertEquals("Edited description", json.getAsJsonObject("display").get("description").getAsString());
                assertFalse(json.getAsJsonObject("criteria").isEmpty());
            }
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            Achievement stored = (Achievement) reopened.getModElementByName("legacy_advancement").getGeneratableElement();
            assertEquals(updated, stored.triggerxml);
            assertEquals("Edited description", stored.achievementDescription);
            assertEquals("goal", stored.achievementType);
        }
    }

    private static void update(MCreatorWorkspaceSession session, UUID element, long revision, String path, String value) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementId", element.toString());
        JsonObject change = new JsonObject(); change.addProperty("path", path); change.addProperty("value", value);
        JsonArray changes = new JsonArray(); changes.add(change); payload.add("changes", changes);
        var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision,
                Operation.UPDATE_MOD_ELEMENT, payload)).result();
        assertEquals("committed", result.status(), result.diagnostics().toString());
    }
}
