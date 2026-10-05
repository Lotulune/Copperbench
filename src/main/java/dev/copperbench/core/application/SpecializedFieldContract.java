package dev.copperbench.core.application;

import com.google.gson.*;
import net.mcreator.element.types.Achievement;
import net.mcreator.element.types.Projectile;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.Numeric;
import java.lang.reflect.Field;
import java.util.*;

/** Public spellings and strict input types for the projectile and advancement adapters. */
public final class SpecializedFieldContract {
    private SpecializedFieldContract() {}

    public static final String DEFAULT_ADVANCEMENT_TRIGGER_XML = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
            + "<block type=\"advancement_trigger\" deletable=\"false\" x=\"40\" y=\"80\">"
            + "<next><shadow type=\"custom_trigger\"></shadow></next></block></xml>";

    public static JsonObject projectAdvancementDefinition(JsonObject definition) {
        JsonObject result = definition.deepCopy();
        ACHIEVEMENT_ALIASES.forEach((publicName, storedName) -> {
            if (result.has(storedName)) {
                if (!result.has(publicName)) result.add(publicName, result.get(storedName));
                result.remove(storedName);
            }
        });
        return result;
    }

    private static final Map<String, String> ACHIEVEMENT_ALIASES = Map.of(
            "title", "achievementName", "description", "achievementDescription",
            "icon", "achievementIcon", "frame", "achievementType");

    public static boolean supports(String type) { return Set.of("projectile", "achievement").contains(type); }

    private static Field storageField(String type, String name) {
        try {
            return (type.equals("projectile") ? Projectile.class : Achievement.class)
                    .getField(type.equals("achievement") ? ACHIEVEMENT_ALIASES.getOrDefault(name, name) : name);
        } catch (NoSuchFieldException ignored) { return null; }
    }

    private static String inputType(String type, String name) {
        Field field = storageField(type, name);
        if (field == null) return "string";
        if (field.getType() == boolean.class) return "boolean";
        if (field.getType() == int.class) return "integer";
        if (field.getType() == double.class) return "number";
        if (Collection.class.isAssignableFrom(field.getType())) return "array<string>";
        // These adapters take reference names, not serialized upstream reference objects.
        return "string";
    }

    private static boolean nullable(String type, String name) {
        return type.equals("achievement") && name.equals("rewardFunction")
                || type.equals("projectile") && Set.of("onHitsBlock", "onHitsPlayer", "onHitsEntity", "onFlyingTick").contains(name);
    }

    public static BlockFieldContract.Issue validate(String type, JsonObject requested) {
        if (!supports(type)) return null;
        JsonObject values = BlockFieldContract.merged(requested);
        for (String name : new TreeSet<>(ElementMappingSupport.fields(type))) {
            if (!values.has(name)) continue;
            JsonElement raw = values.get(name);
            String path = BlockFieldContract.path(requested, name);
            String expected = inputType(type, name);
            if (raw.isJsonNull() && nullable(type, name)) continue;
            if (expected.equals("array<string>")) {
                if (!raw.isJsonArray()) return invalid(path, expected);
                JsonArray entries = raw.getAsJsonArray();
                for (int i = 0; i < entries.size(); i++)
                    if (!isString(entries.get(i))) return invalid(path + "/" + i, "string");
                continue;
            }
            if (!raw.isJsonPrimitive()) return invalid(path, expected);
            JsonPrimitive primitive = raw.getAsJsonPrimitive();
            if (expected.equals("string") && !primitive.isString()
                    || expected.equals("boolean") && !primitive.isBoolean()) return invalid(path, expected);
            Field field = storageField(type, name);
            if (expected.equals("integer") || expected.equals("number")) {
                if (!primitive.isNumber() || !Double.isFinite(raw.getAsDouble())) return invalid(path, expected);
                Numeric numeric = field.getAnnotation(Numeric.class);
                if (expected.equals("integer") && !BlockFieldContract.isIntegerInStorageRange(raw, int.class)
                        || numeric != null && (raw.getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(numeric.min())) < 0
                        || raw.getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(numeric.max())) > 0))
                    return new BlockFieldContract.Issue("FIELD_VALUE_OUT_OF_RANGE", path,
                            "Expected " + expected + " within " + (numeric == null ? "the storage range" : numeric.min() + ".." + numeric.max()) + ".");
            }
            LimitedOptions options = field == null ? null : field.getAnnotation(LimitedOptions.class);
            if (options != null && !Arrays.asList(options.value()).contains(raw.getAsString()))
                return new BlockFieldContract.Issue("FIELD_ENUM_INVALID", path, "Choose one of " + Arrays.toString(options.value()) + ".");
        }
        return null;
    }

    private static boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static BlockFieldContract.Issue invalid(String path, String expected) {
        return new BlockFieldContract.Issue("FIELD_TYPE_INVALID", path,
                "Expected " + expected + "; values are not silently coerced or replaced with defaults.");
    }

    public static void reconcileEditedAliases(String type, JsonObject values, JsonArray changes) {
        if (!supports(type)) return;
        Set<String> paths = new HashSet<>();
        changes.forEach(change -> paths.add(change.getAsJsonObject().get("path").getAsString()));
        for (String name : ElementMappingSupport.fields(type)) {
            boolean top = paths.stream().anyMatch(p -> p.equals("/" + name) || p.startsWith("/" + name + "/"));
            boolean nested = paths.stream().anyMatch(p -> p.equals("/fields/" + name) || p.startsWith("/fields/" + name + "/"));
            if (top && !nested && !paths.contains("/fields") && values.has("fields") && values.get("fields").isJsonObject())
                values.getAsJsonObject("fields").remove(name);
            if (nested && !top) values.remove(name);
        }
    }

    public static JsonObject capabilities(String type) {
        if (!supports(type)) throw new IllegalArgumentException("Unsupported specialized contract: " + type);
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", 1);
        result.addProperty("scope", "Input types, numeric bounds and enums; resource resolution and gameplay require separate validation.");
        result.addProperty("compatibilityPath", "/fields");
        JsonArray fields = new JsonArray();
        for (String name : new TreeSet<>(ElementMappingSupport.fields(type))) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", name); entry.addProperty("type", inputType(type, name));
            entry.addProperty("nullable", nullable(type, name));
            Field field = storageField(type, name);
            if (field != null) {
                entry.addProperty("definitionField", field.getName());
                Numeric numeric = field.getAnnotation(Numeric.class);
                if (numeric != null) { entry.addProperty("min", numeric.min()); entry.addProperty("max", numeric.max()); }
                LimitedOptions options = field.getAnnotation(LimitedOptions.class);
                if (options != null) {
                    JsonArray values = new JsonArray(); Arrays.stream(options.value()).forEach(values::add); entry.add("options", values);
                }
            }
            fields.add(entry);
        }
        result.add("fields", fields);
        return result;
    }
}
