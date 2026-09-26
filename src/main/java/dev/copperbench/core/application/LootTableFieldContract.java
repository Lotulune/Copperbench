package dev.copperbench.core.application;

import com.google.gson.*;
import java.util.*;

/** Public camel-case loot fields mapped by the dedicated definition adapter. */
public final class LootTableFieldContract {
    private LootTableFieldContract() {}

    private static final Set<String> POOL_FIELDS = Set.of("minRolls", "maxRolls", "hasBonusRolls",
            "minBonusRolls", "maxBonusRolls", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of("type", "item", "weight", "minCount", "maxCount",
            "minEnchantmentLevel", "maxEnchantmentLevel", "affectedByFortune", "explosionDecay", "silkTouchMode");
    private static final Set<String> TYPES = Set.of("Block", "Entity", "Generic", "Chest", "Fishing", "Empty",
            "Advancement reward", "Gift", "Barter", "Archaeology");
    private static final Map<String, String> POOL_STORAGE_NAMES = Map.of("minrolls", "minRolls", "maxrolls", "maxRolls",
            "hasbonusrolls", "hasBonusRolls", "minbonusrolls", "minBonusRolls", "maxbonusrolls", "maxBonusRolls");

    /** Translate a legacy definition projection without changing its file or dropping unknown data. */
    public static JsonObject projectDefinition(JsonObject definition) {
        JsonObject result = definition.deepCopy();
        if (result.has("pools") && result.get("pools").isJsonArray())
            for (JsonElement raw : result.getAsJsonArray("pools")) if (raw.isJsonObject()) {
                JsonObject pool = raw.getAsJsonObject();
                POOL_STORAGE_NAMES.forEach((stored, exposed) -> {
                    if (pool.has(stored) && !pool.has(exposed)) pool.add(exposed, pool.remove(stored));
                });
            }
        return result;
    }

    /** Anonymous pool/entry objects cannot safely attach unknown source fields after a rewrite. */
    public static BlockFieldContract.Issue preservationIssue(JsonObject definition) {
        if (!definition.has("pools") || !definition.get("pools").isJsonArray()) return null;
        JsonArray pools = definition.getAsJsonArray("pools");
        for (int i = 0; i < pools.size(); i++) {
            if (!pools.get(i).isJsonObject()) continue;
            JsonObject pool = pools.get(i).getAsJsonObject();
            for (String key : pool.keySet())
                if (!POOL_STORAGE_NAMES.containsKey(key) && !key.equals("entries"))
                    return preservationIssue("/pools/" + i + "/" + escape(key));
            if (!pool.has("entries") || !pool.get("entries").isJsonArray()) continue;
            JsonArray entries = pool.getAsJsonArray("entries");
            for (int j = 0; j < entries.size(); j++) if (entries.get(j).isJsonObject())
                for (String key : entries.get(j).getAsJsonObject().keySet()) if (!ENTRY_FIELDS.contains(key))
                    return preservationIssue("/pools/" + i + "/entries/" + j + "/" + escape(key));
        }
        return null;
    }

    private static String escape(String key) { return key.replace("~", "~0").replace("/", "~1"); }

    private static BlockFieldContract.Issue preservationIssue(String path) {
        return issue("FIELD_PRESERVATION_REQUIRES_REVIEW", path,
                "This imported pool or entry contains an unknown source field. Review it before structured editing; the original file was preserved.");
    }

    public static JsonObject capabilities() {
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", 1);
        result.addProperty("scope", "Definition input validation; references and runtime behavior require separate validation.");
        result.addProperty("compatibilityPath", "/fields/pools");
        result.addProperty("unknownFields", "preserve_read_only");
        result.addProperty("unknownSourceFields", "Unknown pool/entry source fields require review before any structured edit.");
        result.add("tableTypes", strings(TYPES));
        result.add("poolFields", fields(POOL_FIELDS, "/pools/*"));
        result.add("entryFields", fields(ENTRY_FIELDS, "/pools/*/entries/*"));
        result.addProperty("omittedMaximum", "Uses the effective minimum of the same range.");
        return result;
    }

    private static JsonArray fields(Set<String> names, String path) {
        JsonArray result = new JsonArray();
        for (String name : new TreeSet<>(names)) {
            JsonObject field = new JsonObject();
            String kind = kind(name);
            field.addProperty("name", name); field.addProperty("pathPattern", path + "/" + name);
            field.addProperty("type", kind);
            if (kind.equals("integer")) {
                field.addProperty("min", 0); field.addProperty("max", name.equals("silkTouchMode") ? 2 : 64000);
            }
            if (name.equals("type")) field.add("options", strings(Set.of("item")));
            result.add(field);
        }
        return result;
    }

    private static JsonArray strings(Set<String> values) {
        JsonArray result = new JsonArray(); new TreeSet<>(values).forEach(result::add); return result;
    }

    private static String kind(String key) {
        return key.equals("entries") ? "array" : Set.of("hasBonusRolls", "affectedByFortune", "explosionDecay").contains(key)
                ? "boolean" : Set.of("type", "item").contains(key) ? "string" : "integer";
    }

    public static BlockFieldContract.Issue validate(JsonObject previous, JsonObject requested) {
        JsonObject values = BlockFieldContract.merged(requested), old = BlockFieldContract.merged(previous);
        for (String key : List.of("name", "displayName", "description", "namespace", "type")) {
            var issue = scalar(values, key, BlockFieldContract.path(requested, key), "string");
            if (issue != null) return issue;
        }
        if (values.has("type") && !TYPES.contains(values.get("type").getAsString()))
            return issue("FIELD_ENUM_INVALID", BlockFieldContract.path(requested, "type"), "Choose a supported loot table type.");
        if (!values.has("pools")) return null;
        return array(values.get("pools"), old.get("pools"), BlockFieldContract.path(requested, "pools"), true);
    }

    private static BlockFieldContract.Issue array(JsonElement value, JsonElement previous, String path, boolean pools) {
        if (!value.isJsonArray()) return issue("FIELD_TYPE_INVALID", path, "Expected an array.");
        for (int index = 0; index < value.getAsJsonArray().size(); index++) {
            JsonElement raw = value.getAsJsonArray().get(index);
            String itemPath = path + "/" + index;
            if (!raw.isJsonObject()) return issue("FIELD_TYPE_INVALID", itemPath, "Expected an object.");
            JsonElement old = previous != null && previous.isJsonArray() && index < previous.getAsJsonArray().size()
                    ? previous.getAsJsonArray().get(index) : null;
            var result = object(raw.getAsJsonObject(), old != null && old.isJsonObject() ? old.getAsJsonObject() : new JsonObject(), itemPath, pools);
            if (result != null) return result;
        }
        return null;
    }

    private static BlockFieldContract.Issue object(JsonObject value, JsonObject previous, String path, boolean pool) {
        Set<String> supported = pool ? POOL_FIELDS : ENTRY_FIELDS;
        for (var entry : value.entrySet()) {
            String key = entry.getKey(), fieldPath = path + "/" + key.replace("~", "~0").replace("/", "~1");
            if (!supported.contains(key)) {
                if (!entry.getValue().equals(previous.get(key)))
                    return issue("FIELD_UNSUPPORTED", fieldPath, "Unknown imported fields are preserved read-only.");
                continue;
            }
            if (pool && key.equals("entries")) {
                var result = array(entry.getValue(), previous.get(key), fieldPath, false);
                if (result != null) return result;
                continue;
            }
            String kind = kind(key);
            var result = scalar(value, key, fieldPath, kind);
            if (result != null) return result;
            if (key.equals("type") && !entry.getValue().getAsString().equals("item"))
                return issue("FIELD_ENUM_INVALID", fieldPath, "Only item entries are supported.");
            if (key.equals("silkTouchMode") && entry.getValue().getAsInt() > 2)
                return issue("FIELD_ENUM_INVALID", fieldPath, "Choose silk touch mode 0, 1 or 2.");
        }
        for (String key : previous.keySet())
            if (!supported.contains(key) && !value.has(key))
                return issue("FIELD_UNSUPPORTED", path + "/" + key.replace("~", "~0").replace("/", "~1"),
                        "Unknown imported fields are preserved read-only.");
        if (pool) {
            var result = ordered(value, "minRolls", "maxRolls", 1, path);
            return result != null ? result : ordered(value, "minBonusRolls", "maxBonusRolls", 0, path);
        }
        var result = ordered(value, "minCount", "maxCount", 1, path);
        return result != null ? result : ordered(value, "minEnchantmentLevel", "maxEnchantmentLevel", 0, path);
    }

    private static BlockFieldContract.Issue scalar(JsonObject values, String key, String path, String kind) {
        if (!values.has(key)) return null;
        JsonElement raw = values.get(key);
        if (!raw.isJsonPrimitive() || switch (kind) {
            case "string" -> !raw.getAsJsonPrimitive().isString();
            case "boolean" -> !raw.getAsJsonPrimitive().isBoolean();
            default -> !raw.getAsJsonPrimitive().isNumber();
        }) return issue("FIELD_TYPE_INVALID", path, "Expected a non-null " + kind + "; strings are not coerced.");
        if (kind.equals("integer") && (!BlockFieldContract.isIntegerInStorageRange(raw, int.class)
                || raw.getAsDouble() < 0 || raw.getAsDouble() > 64000))
            return issue("FIELD_VALUE_OUT_OF_RANGE", path, "Expected an integer between 0 and 64000.");
        return null;
    }

    private static BlockFieldContract.Issue ordered(JsonObject value, String minKey, String maxKey, int fallback, String path) {
        int min = value.has(minKey) ? value.get(minKey).getAsInt() : fallback;
        int max = value.has(maxKey) ? value.get(maxKey).getAsInt() : min;
        return min <= max ? null : issue("FIELD_VALUE_OUT_OF_RANGE", path + "/" + maxKey, "Maximum must be at least the minimum.");
    }

    private static BlockFieldContract.Issue issue(String code, String path, String message) {
        return new BlockFieldContract.Issue(code, path, message);
    }
}
