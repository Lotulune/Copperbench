package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.ui.blockly.BlocklyEditorType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class Stage17ProcedureTriggerCatalogTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void editorPublishesActiveCatalogWithoutChangingUnknownStoredTrigger(String generator) throws Exception {
        var settings = new WorkspaceSettings("trigger_catalog"); settings.setCurrentGenerator(generator);
        settings.setModName("Trigger Catalog"); settings.setVersion("1.0.0");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("trigger_catalog.mcreator").toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var values = new JsonObject();
            values.addProperty("procedurexml", "<xml><block type=\"event_trigger\"><field name=\"trigger\">legacy_missing_trigger</field></block></xml>");
            var create = new JsonObject(); create.addProperty("elementType", "procedure");
            create.addProperty("name", "catalog_probe"); create.add("initialValues", values);
            var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                    Operation.CREATE_MOD_ELEMENT, create)).result();
            assertEquals("committed", created.status(), created.diagnostics().toString());
            var id = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
            var path = root.resolve("elements/catalog_probe.mod.json");
            byte[] before = Files.readAllBytes(path);
            byte[] workspaceBefore = Files.readAllBytes(workspace.getFileManager().getWorkspaceFile().toPath());
            var payload = new JsonObject(); payload.addProperty("elementId", id);
            var result = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_PROCEDURE_EDITOR, payload));
            assertEquals("succeeded", result.status());
            assertEquals(1, result.revision());
            var projection = result.data().getAsJsonObject();
            assertEquals("legacy_missing_trigger", projection.getAsJsonObject("ir").get("trigger").getAsString());
            assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("PROCEDURE_TRIGGER_UNKNOWN")));
            var catalog = projection.getAsJsonArray("triggerCatalog");
            assertNotNull(catalog);
            var ids = catalog.asList().stream().map(raw -> raw.getAsJsonObject().get("id").getAsString()).collect(Collectors.toSet());
            assertEquals(catalog.size(), ids.size(), "The catalog must have unique trigger IDs");
            assertTrue(ids.contains("player_ticks")); assertTrue(ids.contains("mod_serverload"));
            assertFalse(ids.contains("legacy_missing_trigger"));
            assertFalse(ids.contains("on_block_right_clicked"));
            var supported = workspace.getGenerator().getGeneratorStats().getBlocklyTriggers(BlocklyEditorType.PROCEDURE);
            var loaded = BlocklyLoader.INSTANCE.getExternalTriggerLoader(BlocklyEditorType.PROCEDURE).getExternalTriggers();
            for (var raw : catalog) {
                var item = raw.getAsJsonObject(); String triggerId = item.get("id").getAsString();
                assertTrue(supported.contains(triggerId));
                assertTrue(loaded.stream().anyMatch(trigger -> trigger.getID().equals(triggerId)));
                assertFalse(item.getAsJsonObject("label").get("fallback").getAsString().isBlank());
            }
            var player = catalog.asList().stream().map(raw -> raw.getAsJsonObject()).filter(item -> item.get("id").getAsString().equals("player_ticks")).findFirst().orElseThrow();
            assertTrue(player.getAsJsonArray("dependencies").asList().stream().anyMatch(raw -> {
                var dependency = raw.getAsJsonObject();
                return dependency.get("name").getAsString().equals("x") && dependency.get("type").getAsString().equals("number");
            }));
            assertArrayEquals(before, Files.readAllBytes(path));
            assertArrayEquals(workspaceBefore, Files.readAllBytes(workspace.getFileManager().getWorkspaceFile().toPath()));
        }
    }
}
