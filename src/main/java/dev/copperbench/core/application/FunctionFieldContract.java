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
}
