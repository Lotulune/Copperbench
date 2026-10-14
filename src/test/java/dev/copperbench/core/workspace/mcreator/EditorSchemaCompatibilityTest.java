package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.bridge.JcefBridgeEndpoint;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorSchemaCompatibilityTest {
    @TempDir Path root;
    @BeforeAll static void init() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    @ParameterizedTest @ValueSource(strings = {"fabric-26.3", "neoforge-26.3"})
    void realEditorsRespectExplicitLegacyAndRichVersions(String generator) throws Exception {
        var settings = new WorkspaceSettings("editor_contract"); settings.setModName("Editor Contract");
        settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        settings.setModElementsPackage("dev.copperbench.editorcontract");
        Path evidence = Path.of("output/p263-27/editor", generator); Files.createDirectories(evidence);
        try (var workspace = Workspace.createWorkspace(root.resolve("editor.mcreator").toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            UUID workspaceId = session.workspaceId();
            var entry = session.uiEntry(); var bridge = new JcefBridgeEndpoint(entry, ignored -> {});
            var optIn = json("{\"operation\":\"opt_in\",\"supportedVersions\":[\"1\"],\"expectedRevision\":0}");
            optIn.addProperty("generatorId", generator);
            assertEquals("succeeded", entry.preview(UUID.randomUUID(), workspaceId, optIn).get("status").getAsString());
            long revision = 0;
            for (String type : List.of("item", "block", "recipe", "function", "code")) {
                JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", "sample_" + type); payload.add("initialValues", new JsonObject());
                var create = json("{\"operation\":\"command\",\"supportedVersions\":[\"1\"],\"command\":\"create_mod_element\"}");
                create.addProperty("generatorId", generator); create.addProperty("expectedRevision", revision); create.add("payload", payload);
                var created = entry.preview(UUID.randomUUID(), workspaceId, create);
                assertEquals("committed", created.get("status").getAsString(), created.toString());
                revision++;
                JsonObject selection = new JsonObject(); selection.add("elementId", created.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id"));
                for (String version : List.of("1.0", "1.1")) {
                    var query = new UiCore.Query("query", version, UUID.randomUUID(), workspaceId, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, selection);
                    var result = entry.query(query); assertEquals("succeeded", result.status(), result.toString());
                    assertEquals(version, result.schemaVersion()); assertEquals(revision, result.revision());
                    var body = result.data().getAsJsonObject();
                    assertEquals(version.equals("1.1"), body.has("configuration"));
                    assertEquals(version.equals("1.1"), body.has("sourceManagement"));
                    var wire = UiCore.wireGson().toJsonTree(result).getAsJsonObject();
                    assertEquals(wire, JsonParser.parseString(bridge.handle(UiCore.wireGson().toJson(query))));
                    Files.writeString(evidence.resolve(type + "-" + version + ".json"), UiCore.wireGson().toJson(result));
                    var headless = session.headlessEntry(UiCore.PermissionProfile.READ_ONLY).query(query);
                    assertEquals(version, headless.schemaVersion());
                    assertTrue(headless.data().getAsJsonObject().getAsJsonArray("sections").asList().stream()
                            .flatMap(s -> s.getAsJsonObject().getAsJsonArray("fields").asList().stream())
                            .allMatch(f -> f.getAsJsonObject().get("readOnly").getAsBoolean()));
                }
            }
            assertThrows(IllegalArgumentException.class, () -> new UiCore.Query("query", "1.1", UUID.randomUUID(), workspaceId, UiCore.Operation.GET_WORKBENCH, new JsonObject()));
            assertThrows(IllegalArgumentException.class, () -> new UiCore.Query("query", "9", UUID.randomUUID(), workspaceId, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, new JsonObject()));
        }
    }
}
