package dev.copperbench.core.application;

import com.google.gson.*;
import java.util.Set;

/** Explicit projection into the unchanged UI-Core 1.0 editor shape. */
final class LegacyEditorProjection {
    private static final Set<String> FIELDS = Set.of("path", "label", "help", "control", "required", "readOnly",
            "value", "options", "constraints", "diagnostics", "source");
    private static final Set<String> CONSTRAINTS = Set.of("min", "max", "step", "minLength", "maxLength", "pattern");

    static JsonObject project(JsonObject rich) {
        JsonObject legacy = rich.deepCopy();
        legacy.keySet().retainAll(Set.of("element", "sections", "capabilities"));
        for (JsonElement section : legacy.getAsJsonArray("sections")) {
            for (JsonElement item : section.getAsJsonObject().getAsJsonArray("fields")) {
                JsonObject field = item.getAsJsonObject();
                field.keySet().retainAll(FIELDS);
                // Legacy clients can edit the same reference token as text; the typed picker is 1.1-only.
                if ("element_reference".equals(field.get("control").getAsString())) field.addProperty("control", "text");
                if (Set.of("element_reference_list", "structured_list").contains(field.get("control").getAsString()))
                    field.addProperty("control", "json");
                if (field.has("constraints")) field.getAsJsonObject("constraints").keySet().retainAll(CONSTRAINTS);
                if (field.has("source") && !field.getAsJsonObject("source").get("path").getAsString().matches("^/src/main/java/.+\\.java$"))
                    field.remove("source");
            }
        }
        localizations(legacy);
        return legacy;
    }

    private static void localizations(JsonElement value) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(LegacyEditorProjection::localizations);
        else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("key") && object.has("fallback") && object.get("key").isJsonPrimitive()) {
                String key = object.get("key").getAsString();
                if (!key.matches("^[a-z][a-z0-9._-]{0,127}$")) {
                    key = key.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
                    if (key.isEmpty() || !Character.isLetter(key.charAt(0))) key = "editor." + key;
                    object.addProperty("key", key.substring(0, Math.min(128, key.length())));
                }
            }
            // User JSON values and interpolation arguments are data, never localization objects.
            object.entrySet().stream().filter(e -> !Set.of("value", "args", "source").contains(e.getKey()))
                    .forEach(e -> localizations(e.getValue()));
        }
    }
}
