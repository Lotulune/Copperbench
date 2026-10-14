package dev.copperbench.core.application;

import com.google.gson.*;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Validates the two public representations of a function body before persistence. */
public final class FunctionFieldContract {
    private FunctionFieldContract() {}

    /** Editing a compatibility path supersedes its old spelling, not an explicitly supplied conflict. */
    public static void reconcileEditedAliases(JsonObject values, JsonArray changes) {
        Set<String> paths = new HashSet<>();
        changes.forEach(change -> paths.add(change.getAsJsonObject().get("path").getAsString()));
        for (String key : List.of("name", "displayName", "description", "namespace", "commands", "code")) {
            boolean top = paths.stream().anyMatch(path -> path.equals("/" + key) || path.startsWith("/" + key + "/"));
            boolean nested = paths.stream().anyMatch(path -> path.equals("/fields/" + key) || path.startsWith("/fields/" + key + "/"));
            if (top && !nested && !paths.contains("/fields") && values.has("fields") && values.get("fields").isJsonObject())
                values.getAsJsonObject("fields").remove(key);
            if (nested && !top) values.remove(key);
        }
    }

    public static BlockFieldContract.Issue validate(JsonObject requested) {
        JsonObject values = BlockFieldContract.merged(requested);
        for (String key : List.of("name", "displayName", "description", "namespace", "code")) {
            if (values.has(key) && !isString(values.get(key)))
                return new BlockFieldContract.Issue("FIELD_TYPE_INVALID", BlockFieldContract.path(requested, key),
                        "Expected a non-null string; numbers and booleans are not converted to text.");
        }
        if (!values.has("commands")) return null;
        String path = BlockFieldContract.path(requested, "commands");
        if (!values.get("commands").isJsonArray())
            return new BlockFieldContract.Issue("FIELD_TYPE_INVALID", path, "Expected an array of command strings, or use code for a text body.");
        JsonArray commands = values.getAsJsonArray("commands");
        for (int i = 0; i < commands.size(); i++)
            if (!isString(commands.get(i)))
                return new BlockFieldContract.Issue("FIELD_TYPE_INVALID", path + "/" + i, "Expected a non-null command string.");
        if (values.has("code") && !body(commands).equals(values.get("code").getAsString()))
            return new BlockFieldContract.Issue("FIELD_ALIAS_CONFLICT", BlockFieldContract.path(requested, "code"),
                    "commands and code describe the same body. Supply one representation, or make code equal to commands joined by LF with a final LF (empty array gives empty text).");
        return null;
    }

    private static boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    /** Bounded preview input checks; Minecraft still owns command parsing and registry resolution. */
    public static BlockFieldContract.Issue validatePreview(JsonObject requested) {
        var issue = validate(requested);
        if (issue != null) return issue;
        JsonObject values = BlockFieldContract.merged(requested);
        if (values.has("namespace") && !values.get("namespace").getAsString().equals("mod"))
            return new BlockFieldContract.Issue("FIELD_ENUM_INVALID", BlockFieldContract.path(requested, "namespace"),
                    "The 26.3 function preview uses the workspace mod namespace.");
        if (values.has("name") && !ElementMappingSupport.ELEMENT_NAME.matcher(values.get("name").getAsString()).matches())
            return new BlockFieldContract.Issue("FIELD_VALUE_INVALID", BlockFieldContract.path(requested, "name"),
                    "Use a lowercase function identifier with letters, digits and underscores, starting with a letter.");
        String key = values.has("commands") ? "commands" : "code";
        String code = values.has("commands") ? body(values.getAsJsonArray("commands"))
                : values.has("code") ? values.get("code").getAsString() : "";
        String[] lines = code.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].stripLeading();
            if (line.indexOf('\0') >= 0 || line.startsWith("/"))
                return new BlockFieldContract.Issue("FUNCTION_CONTENT_INVALID", BlockFieldContract.path(requested, key),
                        "Line " + (index + 1) + ": function commands cannot contain NUL or start with '/'.");
        }
        return null;
    }

    public static String body(JsonArray commands) {
        StringBuilder body = new StringBuilder();
        commands.forEach(command -> body.append(command.getAsString()).append('\n'));
        return body.toString();
    }

    public static JsonObject capabilities() {
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", 1);
        result.addProperty("scope", "Function body input validation; command syntax and runtime execution require separate validation.");
        result.addProperty("commandsType", "array<string>");
        result.addProperty("codeType", "string");
        result.addProperty("compatibilityPath", "/fields");
        result.addProperty("bodyMapping", "Join commands with LF and append a final LF; an empty array produces empty text.");
        result.addProperty("aliasPolicy", "commands and code must produce exactly equal text when both are present.");
        return result;
    }

    /** The existing specialized function contract, qualified for an exact preview generator. */
    public static JsonObject capabilities(String generatorId) {
        JsonObject result = capabilities();
        var fields = dev.copperbench.tracks.PreviewCapabilityCatalog.official().fields(generatorId, "function");
        result.addProperty("generatorId", generatorId);
        result.addProperty("supported", !fields.isEmpty());
        result.add("fields", new Gson().toJsonTree(fields.stream().sorted().toList()));
        if (!fields.isEmpty()) {
            result.add("namespaceValues", new Gson().toJsonTree(List.of("mod")));
            result.addProperty("namePattern", ElementMappingSupport.ELEMENT_NAME.pattern());
            result.addProperty("contentChecks", "String bodies, matching aliases, no NUL or leading slash; game parsing reports other syntax and reference errors.");
            JsonObject example = new JsonObject();
            example.addProperty("elementType", "function"); example.addProperty("name", "camp_signal");
            JsonObject values = new JsonObject(); values.addProperty("namespace", "mod");
            values.addProperty("code", "scoreboard objectives add camp_count dummy\nscoreboard players set camp_probe camp_count 7\n");
            example.add("initialValues", values); result.add("minimalExample", example);
        }
        return result;
    }
}
