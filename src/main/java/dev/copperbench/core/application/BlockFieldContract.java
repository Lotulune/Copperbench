package dev.copperbench.core.application;

import com.google.gson.*;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.Numeric;

import java.lang.reflect.Field;
import java.util.*;

/** Reviewed structured-block write contract. Unknown imported data is preserved, never made writable. */
public final class BlockFieldContract {
    private BlockFieldContract() {}

    public static final Set<String> DEFINITION_FIELDS = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            "hardness", "resistance", "rotationMode", "enablePitch", "boundingBoxes", "isNotColidable",
            "hasInventory", "inventorySize", "inventoryStackSize", "inventoryDropWhenDestroyed",
            "inventoryComparatorPower", "inventoryInSlotIDs", "inventoryOutSlotIDs", "openGUIOnRightClick",
            "guiBoundTo", "texture", "textureTop", "textureLeft", "textureFront", "textureRight", "textureBack",
            "hasBlockItem", "maxStackSize", "rarity", "hasGravity", "isWaterloggable", "unbreakable",
            "hasTransparency", "transparencyType", "renderType", "customModelName")));
    public static final Set<String> PRODUCT_FIELDS = Set.of("displayName", "name", "description", "modelResource", "textureBase64");

    public record Issue(String code, String path, String message) {}

    public static JsonObject merged(JsonObject values) {
        JsonObject result = values.deepCopy();
        result.remove("fields");
        if (values.has("fields") && values.get("fields").isJsonObject())
            values.getAsJsonObject("fields").entrySet().forEach(e -> result.add(e.getKey(), e.getValue().deepCopy()));
        return result;
    }

    public static String path(JsonObject values, String name) {
        return (values.has("fields") && values.get("fields").isJsonObject()
                && values.getAsJsonObject("fields").has(name) ? "/fields/" : "/") + name;
    }

    public static boolean writable(String path) {
        String field = path.replaceFirst("^/fields/", "/").split("/", -1)[1];
        return !field.equals("name") && (DEFINITION_FIELDS.contains(field) || PRODUCT_FIELDS.contains(field));
    }

    /** An edit in one spelling replaces a pre-existing alias; two supplied spellings still have to agree. */
    public static void reconcileEditedAliases(JsonObject values, JsonArray changes) {
        Set<String> paths = new HashSet<>();
        changes.forEach(change -> paths.add(change.getAsJsonObject().get("path").getAsString()));
        Set<String> aliases = new HashSet<>(DEFINITION_FIELDS); aliases.addAll(PRODUCT_FIELDS);
        for (String name : aliases) {
            if (paths.contains("/" + name) && !paths.contains("/fields/" + name) && !paths.contains("/fields")
                    && values.has("fields") && values.get("fields").isJsonObject()) values.getAsJsonObject("fields").remove(name);
            if (paths.contains("/fields/" + name) && !paths.contains("/" + name)) values.remove(name);
        }
        // Product adapters consume these canonical top-level keys; retain equal legacy aliases for old readers.
        JsonObject merged = merged(values);
        for (String name : PRODUCT_FIELDS) if (merged.has(name)) values.add(name, merged.get(name).deepCopy());
    }

    public static Issue validate(JsonObject values) {
        if (values.has("fields") && !values.get("fields").isJsonObject())
            return new Issue("FIELD_TYPE_INVALID", "/fields", "fields must be an object.");
        if (values.has("fields")) {
            for (var entry : values.getAsJsonObject("fields").entrySet()) {
                if (values.has(entry.getKey()) && !values.get(entry.getKey()).equals(entry.getValue()))
                    return new Issue("FIELD_ALIAS_CONFLICT", "/fields/" + entry.getKey(),
                            "Conflicting top-level and fields values. Supply one form or equal values.");
            }
        }
        JsonObject merged = merged(values);
        for (String name : PRODUCT_FIELDS) if (merged.has(name)
                && (!merged.get(name).isJsonPrimitive() || !merged.getAsJsonPrimitive(name).isString()))
            return new Issue("FIELD_TYPE_INVALID", path(values, name), "Expected a string.");
        for (String name : DEFINITION_FIELDS) {
            if (!merged.has(name)) continue;
            JsonElement value = merged.get(name);
            String path = path(values, name);
            Field field = field(name);
            Class<?> type = field.getType();
            if (value.isJsonNull()) {
                if (name.equals("guiBoundTo")) continue;
                return new Issue("FIELD_TYPE_INVALID", path, "Supply a non-null " + type.getSimpleName() + " value.");
            }
            if (type == boolean.class && !(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean())
                    || type == String.class && !(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
                    || (type == int.class || type == double.class)
                    && !(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()))
                return new Issue("FIELD_TYPE_INVALID", path, "Expected " + type.getSimpleName() + "; strings are not coerced.");
            if (type == int.class || type == double.class) {
                double number = value.getAsDouble();
                Numeric range = field.getAnnotation(Numeric.class);
                if (!Double.isFinite(number) || type == int.class && !isIntegerInStorageRange(value, type)
                        || range != null && (number < range.min() || number > range.max()))
                    return new Issue("FIELD_VALUE_OUT_OF_RANGE", path, range == null ? "Expected a finite integer."
                            : "Expected " + (type == int.class ? "an integer" : "a finite number")
                            + " between " + range.min() + " and " + range.max() + ".");
            }
            LimitedOptions options = field.getAnnotation(LimitedOptions.class);
            if (options != null && (type == int.class
                    ? value.getAsInt() < 0 || value.getAsInt() >= options.value().length
                    : !Arrays.asList(options.value()).contains(value.getAsString())))
                return new Issue("FIELD_ENUM_INVALID", path, "Choose a documented option: " + Arrays.toString(options.value()));
            if (name.startsWith("texture") && !(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()))
                return new Issue("FIELD_TYPE_INVALID", path, "Expected a texture reference string.");
            if (name.equals("boundingBoxes")) {
                Issue issue = validateBoxes(value, path);
                if (issue != null) return issue;
            }
            if (name.equals("inventoryInSlotIDs") || name.equals("inventoryOutSlotIDs")) {
                if (!value.isJsonArray()) return new Issue("FIELD_TYPE_INVALID", path, "Expected an array of slot indices.");
                int size = merged.has("inventorySize") ? merged.get("inventorySize").getAsInt() : 0;
                for (JsonElement slot : value.getAsJsonArray()) {
                    if (!isIntegerInStorageRange(slot, int.class) || slot.getAsInt() < 0 || slot.getAsInt() >= size)
                        return new Issue("FIELD_VALUE_OUT_OF_RANGE", path, "Every slot index must be inside inventorySize.");
                }
            }
        }
        if (merged.has("hasInventory") && merged.get("hasInventory").getAsBoolean()
                && (!merged.has("inventorySize") || merged.get("inventorySize").getAsInt() < 1))
            return new Issue("FIELD_REQUIRED_BY_CONDITION", path(values, "inventorySize"),
                    "Set inventorySize to 1..256 when hasInventory is true.");
        if (merged.has("openGUIOnRightClick") && merged.get("openGUIOnRightClick").getAsBoolean()
                && (!merged.has("guiBoundTo") || merged.get("guiBoundTo").isJsonNull() || merged.get("guiBoundTo").getAsString().isBlank()))
            return new Issue("FIELD_REQUIRED_BY_CONDITION", path(values, "guiBoundTo"), "Select a GUI before enabling openGUIOnRightClick.");
        return null;
    }

    /** Check the original decimal value before Gson or double conversion can discard precision. */
    public static boolean isIntegerInStorageRange(JsonElement value, Class<?> type) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try {
            var decimal = value.getAsBigDecimal();
            if (type == byte.class || type == Byte.class) decimal.byteValueExact();
            else if (type == short.class || type == Short.class) decimal.shortValueExact();
            else if (type == int.class || type == Integer.class) decimal.intValueExact();
            else if (type == long.class || type == Long.class) decimal.longValueExact();
            else return false;
            return true;
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private static Issue validateBoxes(JsonElement value, String path) {
        if (!value.isJsonArray()) return new Issue("FIELD_TYPE_INVALID", path, "Expected an array of bounding boxes; [] explicitly selects an empty shape.");
        for (int i = 0; i < value.getAsJsonArray().size(); i++) {
            JsonElement raw = value.getAsJsonArray().get(i);
            String boxPath = path + "/" + i;
            if (!raw.isJsonObject()) return new Issue("FIELD_TYPE_INVALID", boxPath, "Expected a bounding box object.");
            JsonObject box = raw.getAsJsonObject();
            for (String key : box.keySet()) {
                if (!Set.of("mx", "my", "mz", "Mx", "My", "Mz", "subtract").contains(key))
                    return new Issue("FIELD_UNSUPPORTED", boxPath + "/" + key, "Unknown bounding-box property.");
                JsonElement n = box.get(key);
                if (key.equals("subtract")) {
                    if (!n.isJsonPrimitive() || !n.getAsJsonPrimitive().isBoolean())
                        return new Issue("FIELD_TYPE_INVALID", boxPath + "/" + key, "Expected a boolean.");
                } else if (!n.isJsonPrimitive() || !n.getAsJsonPrimitive().isNumber() || !Double.isFinite(n.getAsDouble())
                        || n.getAsDouble() < -100 || n.getAsDouble() > 100)
                    return new Issue("FIELD_VALUE_OUT_OF_RANGE", boxPath + "/" + key, "Expected a coordinate between -100 and 100.");
            }
            for (String axis : List.of("x", "y", "z")) {
                double min = box.has("m" + axis) ? box.get("m" + axis).getAsDouble() : 0;
                double max = box.has("M" + axis) ? box.get("M" + axis).getAsDouble() : 16;
                if (min >= max) return new Issue("FIELD_VALUE_OUT_OF_RANGE", boxPath, "Each box minimum must be smaller than its maximum.");
            }
        }
        return null;
    }

    public static Field field(String name) {
        try { return Block.class.getField(name); }
        catch (NoSuchFieldException e) { throw new IllegalStateException("Reviewed block field no longer exists: " + name, e); }
    }

    public static JsonObject capabilities(String generatorId) {
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", 1);
        result.addProperty("generatorId", generatorId);
        result.addProperty("unknownFields", "preserve_read_only");
        boolean supported = generatorId != null && generatorId.matches("(?:fabric|neoforge)-(?:1\\.20\\.1|1\\.21\\.1|26\\.1\\.2|26\\.2)");
        result.addProperty("supported", supported);
        if (!supported) result.addProperty("reason", "This structured-block contract is limited to the eight Stage 17 Java tracks.");
        result.addProperty("internalNamePattern", "^[a-z][a-z0-9_]{0,63}$");
        JsonArray fields = new JsonArray();
        for (String name : DEFINITION_FIELDS) {
            Field reflected = field(name);
            JsonObject field = new JsonObject();
            field.addProperty("name", name);
            field.addProperty("path", "/" + name);
            field.addProperty("compatibilityPath", "/fields/" + name);
            field.addProperty("type", reflected.getType().getSimpleName());
            field.addProperty("writable", supported);
            if (name.startsWith("inventory") && !name.equals("inventorySize")) field.addProperty("condition", "hasInventory=true");
            if (name.equals("inventorySize")) field.addProperty("condition", "hasInventory=true requires inventorySize>=1");
            if (name.equals("guiBoundTo")) field.addProperty("condition", "openGUIOnRightClick=true requires an existing GUI");
            try {
                Object defaultValue = reflected.get(new Block(null));
                if (name.equals("inventoryStackSize")) defaultValue = 99;
                if (name.equals("customModelName")) defaultValue = "Normal";
                if (name.equals("renderType")) defaultValue = 10;
                if (name.equals("texture")) defaultValue = "minecraft:stone";
                if (name.equals("transparencyType")) defaultValue = "SOLID";
                field.add("default", new Gson().toJsonTree(defaultValue));
            } catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
            Numeric range = reflected.getAnnotation(Numeric.class);
            if (range != null) { field.addProperty("min", range.min()); field.addProperty("max", range.max()); }
            LimitedOptions options = reflected.getAnnotation(LimitedOptions.class);
            if (options != null) {
                JsonArray choices = new JsonArray();
                for (int i = 0; i < options.value().length; i++) {
                    JsonObject choice = new JsonObject();
                    if (reflected.getType() == int.class) choice.addProperty("value", i);
                    else choice.addProperty("value", options.value()[i]);
                    choice.addProperty("label", options.value()[i]); choices.add(choice);
                }
                field.add("options", choices);
            }
            fields.add(field);
        }
        result.add("fields", fields);
        return result;
    }
}
