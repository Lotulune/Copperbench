package dev.copperbench.shell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.mcreator.preferences.PreferencesEntry;
import net.mcreator.preferences.entries.BooleanEntry;
import net.mcreator.preferences.entries.IntegerEntry;
import net.mcreator.preferences.entries.StringEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppPreferencesServiceTest {
    @TempDir Path directory;
    private final IntegerEntry fontSize = new IntegerEntry("fontSize", 12, 5, 48);
    private final BooleanEntry offline = new BooleanEntry("offline", false);
    private final StringEntry theme = new StringEntry("editorTheme", "MCreator", "MCreator", "Dark");

    private Map<String, PreferencesEntry<?>> entries() {
        // Deliberately avoid PreferencesManager.init(), sections and the user's global registry.
        return Map.of("ide.fontSize", fontSize, "gradle.offline", offline, "ide.editorTheme", theme);
    }
    private Path file() { return directory.resolve("config/userpreferences"); }
    private AppPreferencesService service() { return new AppPreferencesService(file(), entries()); }
    private JsonObject patch(AppPreferencesService service, String changes) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("revision", service.read().get("revision").getAsString());
        request.add("changes", JsonParser.parseString(changes));
        return request;
    }

    @Test void readingUsesExistingDescriptorsWithoutCreatingPreferences() throws Exception {
        var result = service().read();
        assertEquals("1.0", result.get("schemaVersion").getAsString());
        var number = result.getAsJsonArray("entries").get(1).getAsJsonObject();
        assertEquals("ide.fontSize", number.get("key").getAsString());
        assertEquals(5, number.get("min").getAsInt());
        assertEquals(48, number.get("max").getAsInt());
        assertFalse(Files.exists(file()));
    }

    @Test void savePatchesOnlySelectedValuesAndPreservesUnknownConfiguration() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), """
                {"core":{"ide":{"fontSize":12,"futureEditorOption":"keep"},"hidden":{"token":"opaque"}},
                 "custom-plugin":{"options":{"enabled":true}},"futureTopLevel":[1,2]}
                """);
        AppPreferencesService service = service();
        JsonObject request = patch(service, "{\"ide.fontSize\":18,\"gradle.offline\":true}");
        JsonObject result = service.save(request);
        assertNotEquals(request.get("revision"), result.get("revision"));
        assertEquals(18, fontSize.get());
        assertTrue(offline.get());
        JsonObject disk = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        assertEquals(18, disk.getAsJsonObject("core").getAsJsonObject("ide").get("fontSize").getAsInt());
        assertEquals("keep", disk.getAsJsonObject("core").getAsJsonObject("ide").get("futureEditorOption").getAsString());
        assertEquals("opaque", disk.getAsJsonObject("core").getAsJsonObject("hidden").get("token").getAsString());
        assertTrue(disk.getAsJsonObject("custom-plugin").getAsJsonObject("options").get("enabled").getAsBoolean());
        assertEquals(2, disk.getAsJsonArray("futureTopLevel").size());
        try (var files = Files.list(file().getParent())) { assertEquals(1, files.count()); }
    }

    @Test void invalidChangesAreRejectedAsAWholeBeforeWritingOrChangingMemory() throws Exception {
        AppPreferencesService service = service();
        for (String invalid : new String[] {"{\"ide.fontSize\":4}", "{\"ide.fontSize\":12.5}",
                "{\"ide.fontSize\":\"18\"}", "{\"ide.fontSize\":99999999999999}",
                "{\"gradle.offline\":1}", "{\"ide.editorTheme\":\"missing-theme\"}",
                "{\"ide.fontSize\":18,\"unknown\":true}", "{\"ide.fontSize\":null}"}) {
            assertThrows(IllegalArgumentException.class, () -> service.save(patch(service, invalid)), invalid);
            assertEquals(12, fontSize.get()); assertFalse(offline.get()); assertFalse(Files.exists(file()));
        }
    }

    @Test void staleDiskAndRuntimeRevisionsCannotOverwriteNewerPreferences() throws Exception {
        AppPreferencesService service = service();
        JsonObject beforeDiskChange = patch(service, "{\"ide.fontSize\":18}");
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{\"external\":true}");
        assertThrows(IllegalStateException.class, () -> service.save(beforeDiskChange));
        assertEquals("{\"external\":true}", Files.readString(file()));
        JsonObject beforeNativeChange = patch(service, "{\"ide.fontSize\":18}");
        fontSize.setValueFromJsonElement(new JsonPrimitive(16));
        assertThrows(IllegalStateException.class, () -> service.save(beforeNativeChange));
        assertEquals(16, fontSize.get());
    }

    @Test void failedPersistenceRollsBackEveryInMemoryChange() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{}");
        AppPreferencesService service = new AppPreferencesService(file(), entries(), (path, document) -> {
            throw new IOException("Storage is unavailable");
        });
        assertThrows(IOException.class, () -> service.save(patch(service, "{\"ide.fontSize\":18,\"gradle.offline\":true}")));
        assertEquals(12, fontSize.get()); assertFalse(offline.get());
        assertEquals("{}", Files.readString(file()));
    }

    @Test void malformedStoredConfigurationIsNeverReplaced() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "not-json");
        assertThrows(IOException.class, () -> service().read());
        assertEquals("not-json", Files.readString(file()));
    }

    @Test void emptyPatchDoesNotCreateAFile() throws Exception {
        AppPreferencesService service = service();
        assertEquals(service.read(), service.save(patch(service, "{}")));
        assertFalse(Files.exists(file()));
    }
}
