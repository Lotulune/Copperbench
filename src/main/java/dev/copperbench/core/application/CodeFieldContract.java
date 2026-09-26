package dev.copperbench.core.application;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;

/** Input validation for manually owned Java text and its existing managed file bundle. */
public final class CodeFieldContract {
    private CodeFieldContract() {}

    public static BlockFieldContract.Issue validate(JsonObject requested) {
        if (requested.has("fields")) {
            if (!requested.get("fields").isJsonObject()) return issue("FIELD_TYPE_INVALID", "/fields", "fields must be an object.");
            for (var entry : requested.getAsJsonObject("fields").entrySet())
                if (requested.has(entry.getKey()) && !requested.get(entry.getKey()).equals(entry.getValue()))
                    return issue("FIELD_ALIAS_CONFLICT", "/fields/" + entry.getKey(), "Conflicting field spellings.");
        }
        JsonObject values = BlockFieldContract.merged(requested);
        for (String key : List.of("name", "displayName", "description", "code"))
            if (values.has(key) && !string(values.get(key))) return invalid(BlockFieldContract.path(requested, key));
        if (values.has("sourceFingerprints")) {
            String path = BlockFieldContract.path(requested, "sourceFingerprints");
            if (!values.get("sourceFingerprints").isJsonObject()) return issue("FIELD_TYPE_INVALID", path, "Expected a map of source paths to fingerprint strings.");
            for (var entry : values.getAsJsonObject("sourceFingerprints").entrySet())
                if (!string(entry.getValue())) return invalid(path + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"));
        }
        if (!values.has("codeFiles")) return null;
        String base = BlockFieldContract.path(requested, "codeFiles");
        if (!values.get("codeFiles").isJsonArray()) return issue("CODE_BUNDLE_INVALID", base, "codeFiles must be an array of Java source files.");
        JsonArray files = values.getAsJsonArray("codeFiles");
        for (int i = 0; i < files.size(); i++) {
            String at = base + "/" + i;
            if (!files.get(i).isJsonObject()) return issue("CODE_BUNDLE_INVALID", at, "Each codeFiles entry must be an object.");
            JsonObject file = files.get(i).getAsJsonObject();
            for (String key : file.keySet()) if (!Set.of("path", "code").contains(key))
                return issue("FIELD_UNSUPPORTED", at + "/" + key.replace("~", "~0").replace("/", "~1"), "Unknown code bundle fields are not applied to source files.");
            for (String key : List.of("path", "code")) if (!string(file.get(key))) return invalid(at + "/" + key);
            String path = file.get("path").getAsString().replace('\\', '/');
            try {
                Path candidate = Path.of(path);
                if (candidate.isAbsolute() || candidate.normalize().startsWith("..") || path.matches("^[A-Za-z]:.*") || !path.endsWith(".java"))
                    return issue("CODE_BUNDLE_INVALID", at + "/path", "Code bundle paths must be relative .java paths inside the generated source package.");
            } catch (RuntimeException exception) {
                return issue("CODE_BUNDLE_INVALID", at + "/path", "Code bundle path is invalid.");
            }
        }
        return null;
    }

    public static void reconcileEditedAliases(JsonObject values, JsonArray changes) {
        Set<String> paths = new HashSet<>();
        changes.forEach(change -> paths.add(change.getAsJsonObject().get("path").getAsString()));
        for (String name : ElementMappingSupport.fields("code")) {
            boolean top = paths.stream().anyMatch(p -> p.equals("/" + name) || p.startsWith("/" + name + "/"));
            boolean nested = paths.stream().anyMatch(p -> p.equals("/fields/" + name) || p.startsWith("/fields/" + name + "/"));
            if (top && !nested && !paths.contains("/fields") && values.has("fields") && values.get("fields").isJsonObject()) values.getAsJsonObject("fields").remove(name);
            if (nested && !top) values.remove(name);
        }
        // The source writer consumes canonical top-level keys. Never erase an explicit conflict.
        if (validate(values) == null) {
            JsonObject merged = BlockFieldContract.merged(values);
            for (String name : ElementMappingSupport.fields("code")) if (merged.has(name)) values.add(name, merged.get(name).deepCopy());
        }
    }

    public static void refreshStoredAliases(JsonObject values) {
        if (values.has("fields") && values.get("fields").isJsonObject())
            for (String name : List.of("code", "codeFiles", "sourceFingerprints"))
                if (values.has(name) && values.getAsJsonObject("fields").has(name))
                    values.getAsJsonObject("fields").add(name, values.get(name).deepCopy());
    }

    public static JsonObject capabilities() {
        JsonObject result = new JsonObject(); result.addProperty("contractVersion", 1);
        result.addProperty("codeType", "string"); result.addProperty("codeFilesType", "array<{path: string, code: string}>");
        result.addProperty("sourceFingerprintsType", "object<string,string>; use the fingerprints returned by the editor to detect external source changes");
        result.addProperty("compatibilityPath", "/fields");
        result.addProperty("pathNormalization", "Backslashes in relative bundle paths are treated as forward slashes on every platform.");
        result.addProperty("scope", "Text and bundle input types; path ownership, duplicate physical paths and source fingerprints are still enforced by persistence. Valid text does not imply Java compilation or lifecycle execution.");
        return result;
    }
    private static boolean string(JsonElement value) { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(); }
    private static BlockFieldContract.Issue invalid(String path) { return issue("FIELD_TYPE_INVALID", path, "Expected a non-null string; numbers and booleans are not source text."); }
    private static BlockFieldContract.Issue issue(String code, String path, String reason) { return new BlockFieldContract.Issue(code, path, reason); }
}
