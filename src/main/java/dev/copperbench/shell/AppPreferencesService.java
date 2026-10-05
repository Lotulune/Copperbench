package dev.copperbench.shell;

import com.google.gson.*;
import net.mcreator.io.UserFolderManager;
import net.mcreator.preferences.PreferencesEntry;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.preferences.entries.BooleanEntry;
import net.mcreator.preferences.entries.IntegerEntry;
import net.mcreator.preferences.entries.StringEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** A narrow, validated view of existing preferences, with atomic patch persistence. */
public final class AppPreferencesService {
    private static final Object STORE_LOCK = new Object();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> EXPOSED = List.of(
            "ui.nativeFileChooser", "ui.remindOfUnsavedChanges", "ui.autoReloadTabs", "ui.expandSectionsByDefault",
            "ide.editorTheme", "ide.fontSize", "ide.useLigatures", "ide.autocomplete", "ide.autocompleteMode",
            "ide.autocompleteDocWindow", "ide.lineNumbers", "ide.errorInfoEnable",
            "gradle.Xmx", "gradle.offline", "gradle.buildOnSave", "gradle.passLangToMinecraft", "gradle.enablePerformanceMonitor");
    private final Path file;
    private final Map<String, PreferencesEntry<?>> entries;
    private final Writer writer;

    @FunctionalInterface interface Writer { void write(Path file, JsonObject document) throws IOException; }

    public AppPreferencesService(Path file, Map<String, PreferencesEntry<?>> entries) {
        this(file, entries, AppPreferencesService::writeAtomically);
    }

    AppPreferencesService(Path file, Map<String, PreferencesEntry<?>> entries, Writer writer) {
        this.file = file.toAbsolutePath().normalize();
        this.entries = new LinkedHashMap<>();
        for (String key : EXPOSED) if (entries.containsKey(key)) this.entries.put(key, entries.get(key));
        this.writer = writer;
    }

    public static AppPreferencesService current() {
        Map<String, PreferencesEntry<?>> entries = new LinkedHashMap<>();
        for (PreferencesEntry<?> entry : PreferencesManager.getPreferencesRegistry().getOrDefault("core", List.of()))
            entries.put(entry.getSectionKey() + "." + entry.getID(), entry);
        return new AppPreferencesService(UserFolderManager.getFileFromConfigFolder("userpreferences").toPath(), entries);
    }

    public JsonObject read() throws IOException {
        synchronized (STORE_LOCK) { return snapshot(readDocument()); }
    }

    public JsonObject save(JsonObject request) throws IOException {
        synchronized (STORE_LOCK) {
            if (request == null || !request.keySet().equals(Set.of("revision", "changes"))
                    || !isString(request.get("revision")) || !request.get("changes").isJsonObject())
                throw new IllegalArgumentException("PREFERENCES_INVALID: Expected revision and changes");
            JsonObject document = readDocument();
            if (!revision(document).equals(request.get("revision").getAsString()))
                throw new IllegalStateException("PREFERENCES_CONFLICT: Preferences changed; reload before saving");
            JsonObject changes = request.getAsJsonObject("changes");
            for (var change : changes.entrySet()) validate(change.getKey(), change.getValue());
            if (changes.isEmpty()) return snapshot(document);

            JsonObject next = document.deepCopy();
            JsonObject core = objectChild(next, "core");
            Map<String, JsonElement> previous = new LinkedHashMap<>();
            for (var change : changes.entrySet()) {
                String[] path = change.getKey().split("\\.", 2);
                objectChild(core, path[0]).add(path[1], change.getValue().deepCopy());
                previous.put(change.getKey(), entries.get(change.getKey()).getSerializedValue().deepCopy());
            }
            try {
                changes.entrySet().forEach(change -> entries.get(change.getKey()).setValueFromJsonElement(change.getValue()));
                writer.write(file, next);
            } catch (IOException | RuntimeException failure) {
                previous.forEach((key, value) -> entries.get(key).setValueFromJsonElement(value));
                throw failure;
            }
            return snapshot(next);
        }
    }

    private JsonObject snapshot(JsonObject document) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", "1.0");
        result.addProperty("revision", revision(document));
        JsonArray fields = new JsonArray();
        entries.forEach((key, entry) -> {
            JsonObject field = new JsonObject();
            field.addProperty("key", key);
            field.addProperty("section", key.substring(0, key.indexOf('.')));
            field.add("value", entry.getSerializedValue());
            if (entry instanceof BooleanEntry) field.addProperty("type", "boolean");
            else if (entry instanceof IntegerEntry integer) {
                field.addProperty("type", "integer");
                field.addProperty("min", integer.minimum()); field.addProperty("max", integer.maximum());
            } else if (entry instanceof StringEntry string && !string.editable() && string.choices().length > 0) {
                field.addProperty("type", "choice"); field.add("options", JSON.toJsonTree(string.choices()));
            } else throw new IllegalStateException("PREFERENCES_UNSUPPORTED: " + key);
            fields.add(field);
        });
        result.add("entries", fields);
        return result;
    }

    private void validate(String key, JsonElement value) {
        PreferencesEntry<?> entry = entries.get(key);
        boolean valid = entry != null && value != null && value.isJsonPrimitive();
        if (valid && entry instanceof BooleanEntry) valid = value.getAsJsonPrimitive().isBoolean();
        else if (valid && entry instanceof IntegerEntry integer) {
            valid = value.getAsJsonPrimitive().isNumber();
            if (valid) try {
                int number = value.getAsBigDecimal().intValueExact();
                valid = number >= integer.minimum() && number <= integer.maximum();
            } catch (ArithmeticException | NumberFormatException ignored) { valid = false; }
        } else if (valid && entry instanceof StringEntry string)
            valid = isString(value) && !string.editable() && Arrays.asList(string.choices()).contains(value.getAsString());
        else valid = false;
        if (!valid) throw new IllegalArgumentException("PREFERENCES_INVALID: " + key);
    }

    private JsonObject readDocument() throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        try {
            JsonElement document = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!document.isJsonObject()) throw new JsonParseException("Expected preferences object");
            return document.getAsJsonObject();
        } catch (JsonParseException exception) { throw new IOException("PREFERENCES_UNREADABLE", exception); }
    }

    private String revision(JsonObject document) {
        JsonObject state = new JsonObject(); state.add("stored", document);
        JsonObject current = new JsonObject(); entries.forEach((key, entry) -> current.add(key, entry.getSerializedValue()));
        state.add("current", current);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static JsonObject objectChild(JsonObject parent, String key) {
        if (!parent.has(key)) parent.add(key, new JsonObject());
        if (!parent.get(key).isJsonObject()) throw new IllegalArgumentException("PREFERENCES_INVALID: Stored section " + key);
        return parent.getAsJsonObject(key);
    }

    private static void writeAtomically(Path file, JsonObject document) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "preferences-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(document), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
