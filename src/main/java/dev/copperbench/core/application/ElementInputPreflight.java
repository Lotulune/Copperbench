package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Checks input shapes and known generation prerequisites without touching a workspace. */
final class ElementInputPreflight {
    private ElementInputPreflight() {}

    static List<Diagnostic> createPayload(JsonObject payload) {
        List<Diagnostic> issues = new ArrayList<>();
        require(issues, payload.get("elementType"), "/elementType", "string", null,
                "Choose an elementType from the create_mod_element tool schema.");
        require(issues, payload.get("name"), "/name", "string", null,
                "Supply an internal name using lowercase letters, digits and underscores, starting with a letter.");
        require(issues, payload.get("initialValues"), "/initialValues", "object", null,
                "Supply initialValues as an object; use {} for an element whose defaults are sufficient.");
        if (payload.has("initialValues") && payload.get("initialValues").isJsonObject())
            issues.addAll(shape(payload.getAsJsonObject("initialValues"), null));
        return withMissingFields(issues);
    }

    static List<Diagnostic> shape(JsonObject values, UUID elementId) {
        List<Diagnostic> issues = new ArrayList<>();
        String prefix = prefix(elementId);
        if (values.has("fields")) require(issues, values.get("fields"), prefix + "/fields", "object", elementId,
                "Put structured fields inside an object, for example {\"fields\":{\"hardness\":3}}.");
        for (String field : List.of("displayName", "description")) {
            JsonObject effective = BlockFieldContract.merged(values);
            if (effective.has(field)) require(issues, effective.get(field),
                    prefix + BlockFieldContract.path(values, field), "string", elementId,
                    "Supply text for " + field + ".");
        }
        return withMissingFields(issues);
    }

    static List<Diagnostic> requiredValues(String type, JsonObject values, UUID elementId) {
        List<Diagnostic> issues = new ArrayList<>();
        JsonObject effective = BlockFieldContract.merged(values);
        // These fields are dereferenced by the built-in templates/adapters, have no usable default,
        // and previously failed after generation had already begun. Do not infer requirements
        // for optional textures or third-party fields from their names or annotations alone.
        switch (type) {
            case "specialentity" -> {
                texture(issues, values, effective, "entityTexture", "entity", elementId);
                texture(issues, values, effective, "itemTexture", "item", elementId);
            }
            case "tool" -> {
                // Tool.generateModElementPicture may use an alternate GUI texture for custom models.
                if (!nonBlankString(effective.get("guiTexture")))
                    texture(issues, values, effective, "texture", "item", elementId);
            }
            case "livingentity" -> {
                require(issues, effective.get("mobModelName"), fieldPath(values, "mobModelName", elementId),
                        "string", elementId, "Choose a built-in entity model such as Chicken, or an imported model name.");
                texture(issues, values, effective, "mobModelTexture", "entity", elementId);
            }
            default -> { }
        }
        return withMissingFields(issues);
    }

    static JsonObject capabilities() {
        JsonObject result = new JsonObject();
        result.addProperty("diagnosticMetadata", "message.args");
        JsonArray metadata = new JsonArray();
        List.of("field", "expectedType", "actualType", "repairHint", "missingFields").forEach(metadata::add);
        result.add("metadataFields", metadata);
        JsonObject required = new JsonObject();
        required.add("specialentity", strings("entityTexture", "itemTexture"));
        required.add("tool", strings("texture"));
        required.add("livingentity", strings("mobModelName", "mobModelTexture"));
        result.add("requiredGenerationFields", required);
        result.addProperty("toolTextureCondition", "texture is required when guiTexture is absent or empty");
        result.addProperty("scope", "create payload shape and known required generation fields; asset existence and generator-specific rules still require validation/build");
        return result;
    }

    private static void texture(List<Diagnostic> issues, JsonObject values, JsonObject effective,
            String field, String category, UUID elementId) {
        require(issues, effective.get(field), fieldPath(values, field, elementId), "string", elementId,
                "Import or select an " + category
                        + " texture in Assets, then supply its resource name in " + field + ".");
    }

    private static String fieldPath(JsonObject values, String field, UUID elementId) {
        return prefix(elementId) + BlockFieldContract.path(values, field);
    }

    private static String prefix(UUID elementId) {
        return elementId == null ? "/initialValues" : "/elements/" + elementId;
    }

    private static boolean nonBlankString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !value.getAsString().isBlank();
    }

    private static void require(List<Diagnostic> issues, JsonElement value, String path, String expected,
            UUID elementId, String hint) {
        boolean absent = value == null || value.isJsonNull();
        boolean correct = !absent && (expected.equals("object") ? value.isJsonObject()
                : value.isJsonPrimitive() && value.getAsJsonPrimitive().isString());
        boolean blank = correct && expected.equals("string") && value.getAsString().isBlank()
                && !path.endsWith("/description") && !path.endsWith("/displayName");
        if (correct && !blank) return;
        JsonObject args = new JsonObject();
        args.addProperty("field", path);
        args.addProperty("expectedType", expected);
        args.addProperty("actualType", value == null ? "missing" : value.isJsonNull() ? "null"
                : value.isJsonArray() ? "array" : value.isJsonObject() ? "object"
                : value.getAsJsonPrimitive().isBoolean() ? "boolean"
                : value.getAsJsonPrimitive().isNumber() ? "number" : "string");
        args.addProperty("repairHint", hint);
        issues.add(Diagnostic.error(absent || blank ? "FIELD_REQUIRED" : "FIELD_TYPE_INVALID",
                "diagnostic.input_preflight", "{field}: expected {expectedType}. {repairHint}", args, path, elementId));
    }

    private static List<Diagnostic> withMissingFields(List<Diagnostic> issues) {
        JsonArray missing = new JsonArray();
        issues.stream().filter(issue -> issue.code().equals("FIELD_REQUIRED")).forEach(issue -> missing.add(issue.path()));
        // A plan may report its first blocking diagnostic; retain the complete missing-field list there too.
        for (Diagnostic issue : issues) issue.message().args().add("missingFields", missing.deepCopy());
        return List.copyOf(issues);
    }

    private static JsonArray strings(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }
}
