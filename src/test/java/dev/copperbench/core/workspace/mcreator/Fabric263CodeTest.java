package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.copperbench.core.workspace.mcreator.Fabric263ItemTest.*;

class Fabric263CodeTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private Workspace create() throws Exception {
        var settings = new WorkspaceSettings("code_contract");
        settings.setModName("Code Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-26.3");
        return Workspace.createWorkspace(root.resolve("code.mcreator").toFile(), settings);
    }
    private static void save(String name, Object value) throws Exception {
        Path output = Path.of("output/p263-22/core-ui"); Files.createDirectories(output);
        Files.writeString(output.resolve(name + ".json"), UiCore.wireGson().toJson(value));
    }
    private static UiCore.Query editor(UUID id, String element) {
        return UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_MOD_ELEMENT_EDITOR, json("{\"elementId\":\""+element+"\"}"));
    }
    private static JsonObject values(UiCore.QueryResult result) {
        JsonObject values = new JsonObject();
        result.data().getAsJsonObject().getAsJsonArray("sections").forEach(s -> s.getAsJsonObject().getAsJsonArray("fields").forEach(f -> {
            var field = f.getAsJsonObject(); String path = field.get("path").getAsString().replaceFirst("^/fields/", "/");
            if (path.startsWith("/sourceFingerprints/")) {
                if (!values.has("sourceFingerprints")) values.add("sourceFingerprints", new JsonObject());
                values.getAsJsonObject("sourceFingerprints").add(path.substring("/sourceFingerprints/".length()).replace("~1", "/").replace("~0", "~"), field.get("value"));
            } else values.add(path.substring(1), field.get("value"));
        }));
        return values;
    }
    private static JsonObject update(String element, JsonObject values) {
        JsonObject result = new JsonObject(); result.addProperty("elementId", element); JsonArray changes = new JsonArray();
        values.entrySet().forEach(e -> { JsonObject change = new JsonObject(); change.addProperty("path", "/"+e.getKey()); change.add("value", e.getValue()); changes.add(change); });
        result.add("changes", changes); return result;
    }
    private static String id(JsonObject response) {
        assertEquals("committed", response.get("status").getAsString(), response.toString());
        return response.getAsJsonObject("core").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
    }

    @Test void specializedDiscoveryAndEmptyUiCreationExposeAnEditableBundle() throws Exception {
        UUID id = UUID.randomUUID();
        try (var workspace = create(); var session = attach(workspace, id)) {
            var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            var environment = entry.query(UiCore.Query.of(UUID.randomUUID(), id, UiCore.Operation.GET_WORKSPACE_ENVIRONMENT, new JsonObject()));
            var contract = environment.data().getAsJsonObject().getAsJsonObject("fieldContracts").getAsJsonObject("code");
            assertEquals(CodeFieldContract.capabilities("fabric-26.3"), contract);
            assertTrue(CodeFieldContract.capabilities("neoforge-26.3").get("supported").getAsBoolean());
            assertFalse(ElementFieldContract.discover("code", "fabric-26.3").get("complete").getAsBoolean());
            String elementId = id(command(entry, id, 0, "create_mod_element", json("{\"elementType\":\"code\",\"name\":\"manual_code\",\"initialValues\":{}}")));
            var projection = entry.query(editor(id, elementId)); JsonObject fields = values(projection);
            assertEquals("", fields.get("code").getAsString()); assertTrue(fields.getAsJsonArray("codeFiles").isEmpty());
            assertEquals(64, fields.getAsJsonObject("sourceFingerprints").get("$primary").getAsString().length());
            assertTrue(workspace.getModElementByName("manual_code").isCodeLocked());
            assertEquals("", Files.readString(root.resolve("src/main/java/net/mcreator/code_contract/manual_code.java")));
            save("new-editor", projection); save("code-contract", contract);
            var example = contract.getAsJsonObject("minimalExample").deepCopy(); example.addProperty("name", "second_code");
            id(command(entry, id, 1, "create_mod_element", example));
        }
    }

    @Test void externalSourcesSurviveMetadataUpdatesAndReopenWhileStaleFingerprintsAndOwnershipFail() throws Exception {
        UUID id = UUID.randomUUID(); String elementId;
        String initial = "package net.mcreator.code_contract; public final class manual_code { public static final int VALUE = 1; }\n";
        String helper = "package net.mcreator.code_contract.parts; public final class Helper { public static final int VALUE = 7; }\n";
        Path primary = root.resolve("src/main/java/net/mcreator/code_contract/manual_code.java");
        Path extra = primary.getParent().resolve("parts/Helper.java");
        try (var workspace = create(); var session = attach(workspace, id)) {
            var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            var payload = json("{\"elementType\":\"code\",\"name\":\"manual_code\",\"initialValues\":{}}");
            var input = payload.getAsJsonObject("initialValues"); input.addProperty("code", initial);
            var file = new JsonObject(); file.addProperty("path", "parts/Helper.java"); file.addProperty("code", helper); var files = new JsonArray(); files.add(file); input.add("codeFiles", files);
            elementId = id(command(entry, id, 0, "create_mod_element", payload));
            assertEquals(initial, Files.readString(primary)); assertEquals(helper, Files.readString(extra));
            var observation = values(entry.query(editor(id, elementId)));
            Files.writeString(primary, initial + "// External primary 中文\n"); Files.writeString(extra, helper.replace("= 7", "= 41"));
            var metadata = command(entry, id, 1, "update_mod_element", update(elementId, json("{\"description\":\"Metadata only\"}")));
            assertEquals("committed", metadata.get("status").getAsString(), metadata.toString());
            assertEquals(initial + "// External primary 中文\n", Files.readString(primary)); assertEquals(helper.replace("= 7", "= 41"), Files.readString(extra));
            var stale = new JsonObject(); stale.addProperty("code", initial.replace("= 1", "= 2")); stale.add("sourceFingerprints", observation.get("sourceFingerprints"));
            var conflict = command(entry, id, 2, "update_mod_element", update(elementId, stale));
            assertEquals("rejected", conflict.get("status").getAsString(), conflict.toString()); assertTrue(conflict.toString().contains("SOURCE_CONTENT_CONFLICT"));
            assertEquals(2, conflict.get("revision").getAsLong());
            var revision = command(entry, id, 1, "update_mod_element", update(elementId, json("{\"description\":\"Stale revision\"}")));
            assertTrue(revision.toString().contains("REVISION_CONFLICT"), revision.toString());
            var collision = payload.deepCopy(); collision.addProperty("name", "other_code");
            var rejected = command(entry, id, 2, "create_mod_element", collision);
            assertEquals("rejected", rejected.get("status").getAsString(), rejected.toString());
            assertTrue(rejected.toString().contains("WORKSPACE_PERSISTENCE_FAILED"), rejected.toString());
            assertFalse(Files.exists(primary.resolveSibling("other_code.java"))); assertNull(workspace.getModElementByName("other_code"));
            assertEquals(initial + "// External primary 中文\n", Files.readString(primary)); assertEquals(helper.replace("= 7", "= 41"), Files.readString(extra));
            save("editor", entry.query(editor(id, elementId)));
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        }
        Files.writeString(extra, helper.replace("= 7", "= 42"));
        try (var workspace = Workspace.readFromFS(root.resolve("code.mcreator").toFile(), null); var session = attach(workspace, id)) {
            var observed = values(session.uiEntry().query(editor(id, elementId)));
            assertEquals(initial + "// External primary 中文\n", observed.get("code").getAsString());
            assertTrue(observed.getAsJsonArray("codeFiles").get(0).getAsJsonObject().get("code").getAsString().contains("= 42"));
            assertTrue(workspace.getModElementByName("manual_code").isCodeLocked());
            assertTrue(workspace.getGenerator().generateBase());
            assertEquals(initial + "// External primary 中文\n", Files.readString(primary)); assertEquals(helper.replace("= 7", "= 42"), Files.readString(extra));
        }
    }

    @Test void unsafeBundleInputsAreRejectedBeforeAnyFileIsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        try (var workspace = create(); var session = attach(workspace, id)) {
            var entry = session.uiEntry(); entry.preview(UUID.randomUUID(), id, request("opt_in", 0));
            for (String input : List.of("{\"code\":true}", "{\"codeFiles\":[{\"path\":\"../Escape.java\",\"code\":\"\"}]}", "{\"codeFiles\":[{\"path\":\"Helper.java\",\"code\":42}]}")) {
                var payload = json("{\"elementType\":\"code\",\"name\":\"invalid_code\"}"); payload.add("initialValues", json(input));
                var rejected = command(entry, id, 0, "create_mod_element", payload);
                assertEquals("rejected", rejected.get("status").getAsString(), rejected.toString());
                assertEquals(0, rejected.get("revision").getAsLong()); assertNull(workspace.getModElementByName("invalid_code"));
            }
        }
    }
}
