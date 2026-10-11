package dev.copperbench.core.application;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.interfaces.*;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.references.*;
import java.awt.Color;
import java.lang.reflect.*;
import java.util.*;

/** Read-only projection of the storage fields and custom shapes used by input validation. */
public final class FieldInputProjection {
    private FieldInputProjection() {}

    public static JsonObject schema(Field field) {
        return schema(field.getGenericType(), field, false, 0);
    }

    static JsonObject schema(Type type, Field field, boolean arrayMember, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Recursive field shape is not reviewed");
        Class<?> kind = type instanceof Class<?> c ? c :
                type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c ? c : null;
        if (kind == null) throw new IllegalArgumentException("Unresolved field type " + type);
        JsonObject shape;
        if (CustomFieldInputContract.handles(kind)) shape = CustomFieldInputContract.shape(kind, field, depth);
        else if (kind.isArray() || Collection.class.isAssignableFrom(kind)) {
            shape = typed("array");
            Type member = kind.isArray() ? kind.getComponentType() : ((ParameterizedType) type).getActualTypeArguments()[0];
            shape.add("items", schema(member, null, true, depth + 1));
            Integer size = GenericFieldInputContract.fixedArraySize(field);
            if (size != null) { shape.addProperty("minItems", size); shape.addProperty("maxItems", size); }
        } else if (Map.class.isAssignableFrom(kind) && !kind.isAnnotationPresent(JsonAdapter.class)) {
            shape = typed("object");
            shape.add("additionalProperties", schema(((ParameterizedType) type).getActualTypeArguments()[1], null, false, depth + 1));
        } else if (MappableElement.class.isAssignableFrom(kind)) {
            JsonObject object = object(Map.of("value", typed("string")), "value");
            shape = union(typed("string"), object);
            try {
                var mapped = (MappableElement) kind.getConstructor(Workspace.class, String.class).newInstance(null, "");
                JsonObject reference = new JsonObject();
                reference.addProperty("operation", "get_field_reference_options");
                reference.addProperty("mappingSource", mapped.getMappingSource());
                reference.addProperty("workspaceOperation", "list_mod_elements");
                reference.addProperty("workspacePrefix", net.mcreator.generator.mapping.NameMapper.MCREATOR_PREFIX);
                shape.add("reference", reference);
            } catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Unknown reference adapter " + kind, e); }
        } else if (kind == Color.class) {
            JsonObject value = integer(int.class);
            JsonObject zero = new JsonObject(); zero.addProperty("const", 0);
            shape = union(value, object(Map.of("value", value, "falpha", zero), "value"));
        } else if (kind == String.class || kind == TextureHolder.class || kind == char.class || kind == Character.class) {
            shape = typed("string");
            if (kind == char.class || kind == Character.class) { shape.addProperty("minLength", 1); shape.addProperty("maxLength", 1); }
            if (kind == TextureHolder.class) {
                JsonObject reference = new JsonObject(); reference.addProperty("operation", "list_assets");
                reference.addProperty("kind", "texture"); reference.addProperty("externalSyntax", "namespace:path");
                shape.add("reference", reference);
            }
        } else if (kind == boolean.class || kind == Boolean.class) shape = typed("boolean");
        else if (kind.isPrimitive() || Number.class.isAssignableFrom(kind)) {
            shape = integerType(kind) ? integer(kind) : typed("number");
            if (!integerType(kind)) shape.addProperty("maximum", kind == float.class || kind == Float.class ? Float.MAX_VALUE : Double.MAX_VALUE);
            if (!integerType(kind)) shape.addProperty("minimum", kind == float.class || kind == Float.class ? -Float.MAX_VALUE : -Double.MAX_VALUE);
        } else if (kind.isEnum()) {
            shape = typed("string"); JsonArray options = new JsonArray();
            for (Object value : kind.getEnumConstants()) options.add(((Enum<?>) value).name());
            shape.add("enum", options);
        } else {
            if (kind.isAnnotationPresent(JsonAdapter.class) || kind.getName().startsWith("java.") || kind == Object.class)
                throw new IllegalArgumentException("Unreviewed field adapter " + kind);
            Map<String, JsonObject> members = new LinkedHashMap<>();
            for (Class<?> current = kind; current != null && current != Object.class; current = current.getSuperclass())
                for (Field member : current.getDeclaredFields())
                    if (!Modifier.isStatic(member.getModifiers()) && !Modifier.isTransient(member.getModifiers()) && !member.isSynthetic())
                        members.putIfAbsent(member.getName(), schema(member.getGenericType(), member, false, depth + 1));
            shape = object(members);
        }
        if (field != null) {
            Numeric numeric = field.getAnnotation(Numeric.class);
            if (numeric != null && (kind.isPrimitive() && kind != boolean.class || Number.class.isAssignableFrom(kind))) {
                shape.addProperty("minimum", numeric.min()); shape.addProperty("maximum", numeric.max());
                shape.addProperty("suggestedStep", numeric.step());
            }
            LimitedOptions choices = field.getAnnotation(LimitedOptions.class);
            if (choices != null) {
                JsonArray options = new JsonArray();
                for (int i = 0; i < choices.value().length; i++)
                    if (kind == int.class || kind == Integer.class) options.add(i); else options.add(choices.value()[i]);
                shape.add("enum", options);
                if (kind == int.class || kind == Integer.class) shape.add("optionLabels", new Gson().toJsonTree(choices.value()));
            }
            if (field.isAnnotationPresent(ModElementReference.class)) {
                JsonObject ref = shape.has("reference") ? shape.getAsJsonObject("reference") : new JsonObject();
                if (!ref.has("operation")) ref.addProperty("operation", "list_mod_elements");
                JsonArray types = new JsonArray();
                for (Class<?> accepted : field.getAnnotation(ModElementReference.class).acceptedTypes()) types.add(accepted.getSimpleName().toLowerCase(Locale.ROOT));
                ref.add("acceptedStorageTypes", types); shape.add("reference", ref);
            }
            if (field.isAnnotationPresent(ResourceReference.class)) shape.add("resourceKind", new Gson().toJsonTree(field.getAnnotation(ResourceReference.class).value()));
            if (field.isAnnotationPresent(TextureReference.class)) shape.addProperty("textureKind", field.getAnnotation(TextureReference.class).value().name().toLowerCase(Locale.ROOT));
            JsonObject condition = condition(field);
            if (condition != null) shape.add("requiredWhen", condition);
        }
        // Input nullability is distinct from generator-required/defaulted fields.
        if (!kind.isPrimitive() && !arrayMember) return nullable(shape);
        return shape;
    }

    public static JsonObject constraints(Field field) {
        if (field == null || !field.isAnnotationPresent(Numeric.class)) return null;
        Numeric n = field.getAnnotation(Numeric.class);
        JsonObject result = new JsonObject();
        result.addProperty("min", n.min()); result.addProperty("max", n.max()); result.addProperty("step", n.step());
        return result;
    }

    public static JsonObject condition(Field field) {
        if (field == null || !field.isAnnotationPresent(NonNullIf.class)) return null;
        JsonObject result = new JsonObject(); result.addProperty("operator", "any_truthy");
        result.addProperty("expressionLanguage", "generator_template_condition");
        JsonArray expressions = new JsonArray(), paths = new JsonArray();
        for (String expression : field.getAnnotation(NonNullIf.class).value()) {
            expressions.add(expression);
            String dependency = expression.trim().replaceFirst("^!\\s*", "").split("\\s*(?:#\\?=|#=|%=)", 2)[0].trim();
            paths.add("/" + dependency);
        }
        result.add("expressions", expressions); result.add("paths", paths);
        return result;
    }

    static boolean integerType(Class<?> kind) {
        return Set.of(byte.class, short.class, int.class, long.class, Byte.class, Short.class, Integer.class, Long.class).contains(kind);
    }
    static JsonObject integer(Class<?> kind) {
        JsonObject result = typed("integer");
        long min = kind == byte.class || kind == Byte.class ? Byte.MIN_VALUE : kind == short.class || kind == Short.class ? Short.MIN_VALUE :
                kind == long.class || kind == Long.class ? Long.MIN_VALUE : Integer.MIN_VALUE;
        long max = kind == byte.class || kind == Byte.class ? Byte.MAX_VALUE : kind == short.class || kind == Short.class ? Short.MAX_VALUE :
                kind == long.class || kind == Long.class ? Long.MAX_VALUE : Integer.MAX_VALUE;
        result.addProperty("minimum", min); result.addProperty("maximum", max); return result;
    }
    static JsonObject typed(String type) { JsonObject result = new JsonObject(); result.addProperty("type", type); return result; }
    static JsonObject object(Map<String, JsonObject> properties, String... required) {
        JsonObject result = typed("object"), children = new JsonObject();
        properties.forEach(children::add); result.add("properties", children); result.addProperty("additionalProperties", false);
        result.add("required", new Gson().toJsonTree(required)); return result;
    }
    static JsonObject union(JsonObject... options) {
        JsonObject result = new JsonObject(); JsonArray choices = new JsonArray();
        for (var choice : options) choices.add(choice); result.add("anyOf", choices); return result;
    }
    static JsonObject nullable(JsonObject value) {
        // Metadata stays on the field for consumers; JSON Schema constraints stay inside anyOf.
        JsonObject result = union(value, typed("null"));
        for (String key : List.of("reference", "requiredWhen", "textureKind", "resourceKind"))
            if (value.has(key)) result.add(key, value.get(key).deepCopy());
        return result;
    }
}
