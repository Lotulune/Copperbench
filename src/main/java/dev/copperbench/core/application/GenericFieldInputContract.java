package dev.copperbench.core.application;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.Numeric;
import net.mcreator.generator.mapping.MappableElement;
import java.awt.Color;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;

/** Checks plain Gson fields recursively, before adapters can coerce or discard input. */
public final class GenericFieldInputContract {
    private GenericFieldInputContract() {}

    public static boolean appliesTo(String type) {
        return Set.of("item", "recipe").contains(type) || !ElementMappingSupport.specialized(type);
    }

    public static void reconcileEditedAliases(String type, JsonObject values, JsonArray changes) {
        if (!appliesTo(type)) return;
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

    public static JsonObject capabilities() {
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", 1);
        result.addProperty("scope", "Recursive input types for reflective definition fields, numeric annotations and enums; not resource resolution, contextual validity or gameplay acceptance.");
        result.addProperty("compatibilityPath", "/fields");
        result.addProperty("unknownNestedFields", "Reject new keys; refuse rewriting imported definitions with unknown nested keys that may be lost.");
        result.addProperty("transientFields", "Not writable; values are not persisted by the upstream definition serializer.");
        result.addProperty("customAdapterBoundary", "Built-in procedure, GUI component and property/state-map input shapes use fieldContracts.custom; third-party custom serializers require separate review.");
        return result;
    }

    public static BlockFieldContract.Issue validate(Field field, JsonElement value, String path) {
        return inspect(field.getGenericType(), value, path, field, false, false, 0);
    }

    static Integer fixedArraySize(Field field) {
        return field != null && field.getDeclaringClass() == net.mcreator.element.types.Recipe.class
                && field.getName().equals("recipeSlots") ? 9 : null;
    }

    /** Unknown nested keys can disappear when Gson replaces arrays without stable item identities. */
    public static BlockFieldContract.Issue preservationIssue(Class<?> storage, JsonObject definition) {
        for (Field field : storage.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || !definition.has(field.getName())) continue;
            var issue = inspect(field.getGenericType(), definition.get(field.getName()), "/" + escape(field.getName()), field, false, true, 0);
            if (issue != null) return new BlockFieldContract.Issue("FIELD_PRESERVATION_REQUIRES_REVIEW", issue.path(),
                    "The imported definition contains an unknown nested field. Review it before structured rewriting; original bytes were preserved.");
        }
        return null;
    }

    static BlockFieldContract.Issue inspect(Type type, JsonElement raw, String path, Field field,
            boolean arrayMember, boolean unknownOnly, int depth) {
        if (depth > 64) return issue("FIELD_VALUE_OUT_OF_RANGE", path, "Nested input exceeds 64 levels.");
        Class<?> kind = type instanceof Class<?> c ? c : type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c ? c : null;
        if (kind == null) return null; // Type variables and custom polymorphic adapters retain their own contracts.
        if (raw == null || raw.isJsonNull())
            return !unknownOnly && (kind.isPrimitive() || arrayMember) ? invalid(path, "a non-null " + kind.getSimpleName()) : null;
        if (CustomFieldInputContract.handles(kind))
            return CustomFieldInputContract.inspect(kind, raw, path, field, unknownOnly, depth);
        if (kind.isArray() || Collection.class.isAssignableFrom(kind)) {
            if (!raw.isJsonArray()) return unknownOnly ? null : invalid(path, "an array");
            Integer size = fixedArraySize(field);
            if (!unknownOnly && size != null && raw.getAsJsonArray().size() != size)
                return issue("FIELD_VALUE_OUT_OF_RANGE", path, "Expected exactly " + size + " slots.");
            Type itemType = kind.isArray() ? kind.getComponentType() : type instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
            for (int i = 0; i < raw.getAsJsonArray().size(); i++) {
                var issue = inspect(itemType, raw.getAsJsonArray().get(i), path + "/" + i, null, true, unknownOnly, depth + 1);
                if (issue != null) return issue;
            }
            return null;
        }
        if (Map.class.isAssignableFrom(kind) && !kind.isAnnotationPresent(JsonAdapter.class)) {
            if (!raw.isJsonObject()) return unknownOnly ? null : invalid(path, "an object");
            Type itemType = type instanceof ParameterizedType p ? p.getActualTypeArguments()[1] : Object.class;
            for (var entry : raw.getAsJsonObject().entrySet()) {
                var issue = inspect(itemType, entry.getValue(), path + "/" + escape(entry.getKey()), null, false, unknownOnly, depth + 1);
                if (issue != null) return issue;
            }
            return null;
        }
        if (MappableElement.class.isAssignableFrom(kind)) {
            if (!raw.isJsonObject()) return unknownOnly || isString(raw) ? null : invalid(path, "a reference name string or {value: string}");
            JsonObject object = raw.getAsJsonObject();
            for (String key : object.keySet()) if (!key.equals("value")) return unsupported(path + "/" + escape(key));
            return unknownOnly || object.has("value") && isString(object.get("value")) ? null : invalid(path + "/value", "a reference name string");
        }
        if (kind == Color.class) {
            JsonElement value = raw;
            if (raw.isJsonObject()) {
                for (String key : raw.getAsJsonObject().keySet()) if (!Set.of("value", "falpha").contains(key)) return unsupported(path + "/" + escape(key));
                // The upstream Color serializer emits this legacy constant; alpha is encoded in value.
                JsonElement legacyAlpha = raw.getAsJsonObject().get("falpha");
                if (legacyAlpha != null && (!legacyAlpha.isJsonPrimitive() || !legacyAlpha.getAsJsonPrimitive().isNumber()
                        || !Double.isFinite(legacyAlpha.getAsDouble()) || legacyAlpha.getAsBigDecimal().signum() != 0))
                    return issue("FIELD_VALUE_OUT_OF_RANGE", path + "/falpha", "Legacy falpha must be zero; encode alpha in the integer value.");
                value = raw.getAsJsonObject().get("value"); path += "/value";
            }
            return unknownOnly ? null : number(int.class, value, path, null);
        }
        if (kind == String.class || kind == TextureHolder.class || kind == char.class || kind == Character.class) {
            if (unknownOnly) return null;
            if (!isString(raw) || (kind == char.class || kind == Character.class) && raw.getAsString().length() != 1)
                return invalid(path, "a string");
            return options(field, raw, path);
        }
        if (kind == boolean.class || kind == Boolean.class)
            return unknownOnly || raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean() ? null : invalid(path, "a boolean");
        if (kind.isPrimitive() || Number.class.isAssignableFrom(kind)) {
            if (unknownOnly) return null;
            var issue = number(kind, raw, path, field);
            return issue != null ? issue : options(field, raw, path);
        }
        if (kind.isEnum()) {
            if (unknownOnly) return null;
            if (!isString(raw)) return invalid(path, "an enum string");
            return Arrays.stream(kind.getEnumConstants()).anyMatch(v -> ((Enum<?>) v).name().equals(raw.getAsString()))
                    ? null : issue("FIELD_ENUM_INVALID", path, "Choose a declared enum value.");
        }
        // These types have custom serialized shapes, not ordinary reflective object fields.
        if (kind.isAnnotationPresent(JsonAdapter.class)
                || net.mcreator.element.parts.procedure.RetvalProcedure.class.isAssignableFrom(kind)
                || kind == Object.class || kind.getName().startsWith("java.")) return null;
        return inspectObject(kind, raw, path, unknownOnly, depth);
    }

    static BlockFieldContract.Issue inspectObject(Class<?> kind, JsonElement raw, String path, boolean unknownOnly, int depth) {
        if (depth > 64) return issue("FIELD_VALUE_OUT_OF_RANGE", path, "Nested input exceeds 64 levels.");
        if (!raw.isJsonObject()) return unknownOnly ? null : invalid(path, "an object");
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> current = kind; current != null && current != Object.class; current = current.getSuperclass())
            for (Field member : current.getDeclaredFields())
                if (!Modifier.isStatic(member.getModifiers()) && !Modifier.isTransient(member.getModifiers()) && !member.isSynthetic())
                    fields.putIfAbsent(member.getName(), member);
        for (var entry : raw.getAsJsonObject().entrySet()) {
            String childPath = path + "/" + escape(entry.getKey());
            Field member = fields.get(entry.getKey());
            if (member == null) return unsupported(childPath);
            var issue = inspect(member.getGenericType(), entry.getValue(), childPath, member, false, unknownOnly, depth + 1);
            if (issue != null) return issue;
        }
        return null;
    }

    private static BlockFieldContract.Issue number(Class<?> kind, JsonElement value, String path, Field field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble()))
            return invalid(path, "a finite number");
        boolean integer = Set.of(byte.class, short.class, int.class, long.class, Byte.class, Short.class, Integer.class, Long.class).contains(kind);
        if (integer && !BlockFieldContract.isIntegerInStorageRange(value, kind)
                || (kind == float.class || kind == Float.class) && !Float.isFinite(value.getAsFloat()))
            return issue("FIELD_VALUE_OUT_OF_RANGE", path, "Expected a value in the storage range without integer truncation.");
        Numeric range = field == null ? null : field.getAnnotation(Numeric.class);
        if (range != null && (value.getAsBigDecimal().compareTo(BigDecimal.valueOf(range.min())) < 0
                || value.getAsBigDecimal().compareTo(BigDecimal.valueOf(range.max())) > 0))
            return issue("FIELD_VALUE_OUT_OF_RANGE", path, "Expected a value within " + range.min() + ".." + range.max() + ".");
        return null;
    }

    private static BlockFieldContract.Issue options(Field field, JsonElement raw, String path) {
        LimitedOptions options = field == null ? null : field.getAnnotation(LimitedOptions.class);
        if (options == null) return null;
        boolean invalid = field.getType() == int.class || field.getType() == Integer.class
                ? raw.getAsInt() < 0 || raw.getAsInt() >= options.value().length
                : !Arrays.asList(options.value()).contains(raw.getAsString());
        return invalid ? issue("FIELD_ENUM_INVALID", path, "Choose one of " + Arrays.toString(options.value()) + ".") : null;
    }

    private static boolean isString(JsonElement raw) { return raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString(); }
    private static String escape(String text) { return text.replace("~", "~0").replace("/", "~1"); }
    private static BlockFieldContract.Issue invalid(String path, String expected) { return issue("FIELD_TYPE_INVALID", path, "Expected " + expected + "; no implicit coercion is supported."); }
    private static BlockFieldContract.Issue unsupported(String path) { return issue("FIELD_UNSUPPORTED", path, "This nested key is not stored by the field adapter."); }
    private static BlockFieldContract.Issue issue(String code, String path, String message) { return new BlockFieldContract.Issue(code, path, message); }
}
