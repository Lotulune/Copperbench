package dev.copperbench.core.application;

import com.google.gson.*;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.parts.procedure.*;
import net.mcreator.ui.minecraft.states.*;
import java.lang.reflect.Field;
import java.util.*;

/** Validates the actual serialized shapes of the built-in custom field adapters. */
public final class CustomFieldInputContract {
    private CustomFieldInputContract() {}

    private static final Set<String> PROPERTY_TYPES = Set.of("logic", "integer", "number", "string");

    private static Class<?> fixedType(Class<?> kind) {
        return kind == LogicProcedure.class ? boolean.class : kind == NumberProcedure.class ? double.class
                : kind == StringProcedure.class ? String.class : kind == StringListProcedure.class ? String[].class : null;
    }

    private static Class<?> propertyType(String type) {
        return switch (type) { case "logic" -> boolean.class; case "integer" -> int.class; case "number" -> double.class; default -> String.class; };
    }

    /** The discovery projection lives beside the corresponding custom serializer validation. */
    static JsonObject shape(Class<?> kind, Field field, int depth) {
        if (Procedure.class.isAssignableFrom(kind)) {
            Class<?> fixed = fixedType(kind);
            JsonObject name = FieldInputProjection.typed("string");
            JsonObject object;
            if (fixed == null) object = FieldInputProjection.object(Map.of("name", name), "name");
            else {
                JsonObject value = FieldInputProjection.schema(fixed, field, true, depth + 1);
                object = FieldInputProjection.object(Map.of("name", FieldInputProjection.nullable(name),
                        "fixedValue", FieldInputProjection.nullable(value)));
                JsonObject named = new JsonObject(), namedProperties = new JsonObject(), nonblank = FieldInputProjection.typed("string");
                nonblank.addProperty("pattern", "\\S"); namedProperties.add("name", nonblank);
                named.add("properties", namedProperties); named.add("required", new Gson().toJsonTree(List.of("name")));
                JsonObject constant = new JsonObject(), constantProperties = new JsonObject();
                constantProperties.add("fixedValue", value); constant.add("properties", constantProperties);
                constant.add("required", new Gson().toJsonTree(List.of("fixedValue")));
                object.add("anyOf", FieldInputProjection.union(named, constant).get("anyOf"));
            }
            JsonObject result = FieldInputProjection.union(fixed == null ? name : FieldInputProjection.schema(fixed, field, true, depth + 1), object);
            JsonObject reference = new JsonObject(); reference.addProperty("operation", "list_mod_elements");
            reference.addProperty("elementType", "procedure"); reference.addProperty("encoding", "unprefixed_name");
            result.add("reference", reference);
            return result;
        }
        if (kind == StateMap.class) {
            JsonObject result = FieldInputProjection.typed("array");
            JsonArray variants = new JsonArray();
            for (String type : PROPERTY_TYPES.stream().sorted().toList()) {
                variants.add(FieldInputProjection.object(Map.of("property", propertyShape(type, false, depth),
                        "value", FieldInputProjection.schema(propertyType(type), null, true, depth + 1)), "property", "value"));
            }
            JsonObject entry = new JsonObject(); entry.add("anyOf", variants); result.add("items", entry);
            result.addProperty("valueConstraints", "Property names must be unique; numeric values lie within property.min/max; string values belong to property.arrayData when supplied.");
            return result;
        }
        if (kind == PropertyData.class || kind == PropertyDataWithValue.class) {
            JsonObject result = new JsonObject(); JsonArray variants = new JsonArray();
            for (String type : PROPERTY_TYPES.stream().sorted().toList()) variants.add(propertyShape(type, kind == PropertyDataWithValue.class, depth));
            result.add("anyOf", variants); return result;
        }
        if (kind == GUIComponent.class) {
            JsonObject result = new JsonObject(); JsonArray variants = new JsonArray();
            GUIComponent.getTypeMappings().forEach((name, storage) -> {
                JsonObject discriminator = new JsonObject(); discriminator.addProperty("const", name);
                variants.add(FieldInputProjection.object(Map.of("type", discriminator,
                        "data", FieldInputProjection.schema(storage, null, true, depth + 1)), "type", "data"));
            });
            result.add("anyOf", variants); return result;
        }
        throw new IllegalArgumentException("Unreviewed custom field shape " + kind);
    }

    private static JsonObject propertyShape(String type, boolean hasValue, int depth) {
        Map<String, JsonObject> members = new LinkedHashMap<>();
        JsonObject discriminator = new JsonObject(); discriminator.addProperty("const", type); members.put("type", discriminator);
        JsonObject name = FieldInputProjection.typed("string"); name.addProperty("pattern", "\\S"); members.put("name", name);
        JsonObject value = FieldInputProjection.schema(propertyType(type), null, true, depth + 1);
        if (type.equals("integer") || type.equals("number")) {
            JsonObject bound = value.deepCopy(); bound.addProperty("default", 0);
            members.put("min", bound); members.put("max", bound.deepCopy());
        }
        if (type.equals("string")) members.put("arrayData", FieldInputProjection.schema(String[].class, null, false, depth + 1));
        if (hasValue) members.put("value", value);
        JsonObject result = FieldInputProjection.object(members, hasValue ? new String[]{"type", "name", "value"} : new String[]{"type", "name"});
        result.addProperty("valueConstraints", "min <= max; numeric value is within min/max; string value belongs to arrayData when supplied.");
        return result;
    }

    public static JsonObject capabilities() {
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", 1);
        result.addProperty("scope", "Built-in serialized input shapes, types, property ranges and duplicate state names; procedure existence, return types, GUI rendering and gameplay need separate verification.");
        result.addProperty("procedure", "null, string name or {name: string}");
        result.addProperty("returnValue", "A correctly typed fixed scalar/list, or {name: string|null, fixedValue: typed value}; a fixed value is required without a nonblank procedure name.");
        result.addProperty("guiComponent", "{type: registered type string, data: component fields}; unknown types cannot fall back to placeholders.");
        JsonArray components = new JsonArray(); GUIComponent.getTypeMappings().keySet().stream().sorted().forEach(components::add);
        result.add("guiComponentTypes", components);
        result.addProperty("property", "{type: logic|integer|number|string, name: nonempty string, min/max for numeric types, arrayData for strings}; PropertyDataWithValue includes a typed value in the same object.");
        result.addProperty("omittedNumericBounds", "The storage adapter defaults each omitted numeric bound to zero; send explicit bounds when a different range is intended.");
        result.addProperty("stateMap", "Array of {property: descriptor, value: typed value}; property names must be unique.");
        result.addProperty("unknownFields", "Reject new unknown keys; refuse rewriting stored unknown nested data.");
        return result;
    }

    static boolean handles(Class<?> kind) {
        return Procedure.class.isAssignableFrom(kind) || kind == GUIComponent.class
                || kind == StateMap.class || kind == PropertyDataWithValue.class || kind == PropertyData.class;
    }

    static BlockFieldContract.Issue inspect(Class<?> kind, JsonElement raw, String path, Field field, boolean preservation, int depth) {
        if (Procedure.class.isAssignableFrom(kind)) return procedure(kind, raw, path, field, preservation, depth);
        if (kind == GUIComponent.class) {
            if (!raw.isJsonObject()) return preservation ? null : invalid(path, "an object with type and data");
            JsonObject object = raw.getAsJsonObject();
            var unknown = keys(object, Set.of("type", "data"), path);
            if (unknown != null) return unknown;
            if (!string(object.get("type"))) return preservation ? null : invalid(path + "/type", "a component type string");
            Class<?> component = GUIComponent.getTypeMappings().get(object.get("type").getAsString());
            if (component == null) return issue("FIELD_ENUM_INVALID", path + "/type", "Select a registered GUI component type; unknown components cannot be replaced with placeholders.");
            if (!object.has("data") || !object.get("data").isJsonObject()) return preservation ? null : invalid(path + "/data", "a component data object");
            return GenericFieldInputContract.inspectObject(component, object.get("data"), path + "/data", preservation, depth + 1);
        }
        if (kind == StateMap.class) {
            if (!raw.isJsonArray()) return preservation ? null : invalid(path, "an array of property/value entries");
            Set<String> names = new HashSet<>();
            for (int i = 0; i < raw.getAsJsonArray().size(); i++) {
                JsonElement entry = raw.getAsJsonArray().get(i); String at = path + "/" + i;
                if (!entry.isJsonObject()) { if (preservation) continue; return invalid(at, "a property/value object"); }
                var unknown = keys(entry.getAsJsonObject(), Set.of("property", "value"), at);
                if (unknown != null) return unknown;
                JsonElement property = entry.getAsJsonObject().get("property");
                var problem = property(property, entry.getAsJsonObject().get("value"), at + "/property", at + "/value", true, preservation, depth + 1);
                if (problem != null) return problem;
                if (property != null && property.isJsonObject() && string(property.getAsJsonObject().get("name"))
                        && !names.add(property.getAsJsonObject().get("name").getAsString()))
                    return issue("FIELD_ALIAS_CONFLICT", at + "/property/name", "Each state property name must occur once; duplicates would overwrite an earlier value.");
            }
            return null;
        }
        return property(raw, raw.isJsonObject() ? raw.getAsJsonObject().get("value") : null, path, path + "/value",
                kind == PropertyDataWithValue.class, preservation, depth);
    }

    private static BlockFieldContract.Issue procedure(Class<?> kind, JsonElement raw, String path, Field field, boolean preservation, int depth) {
        Class<?> fixed = fixedType(kind);
        if (!raw.isJsonObject()) {
            if (preservation) return null;
            if (fixed == null) return string(raw) ? null : invalid(path, "a procedure name string, name object or null");
            return GenericFieldInputContract.inspect(fixed, raw, path, field, false, false, depth + 1);
        }
        JsonObject object = raw.getAsJsonObject();
        var unknown = keys(object, fixed == null ? Set.of("name") : Set.of("name", "fixedValue"), path);
        if (unknown != null || preservation) return unknown;
        JsonElement name = object.get("name");
        if (fixed == null && !string(name) || name != null && !name.isJsonNull() && !string(name))
            return invalid(path + "/name", "a procedure name string");
        if (fixed == null) return null;
        JsonElement value = object.get("fixedValue");
        if (value == null || value.isJsonNull()) {
            return string(name) && !name.getAsString().isBlank() ? null
                    : invalid(path + "/fixedValue", "a fixed value when no procedure name is supplied");
        }
        return GenericFieldInputContract.inspect(fixed, value, path + "/fixedValue", field, false, false, depth + 1);
    }

    private static BlockFieldContract.Issue property(JsonElement raw, JsonElement value, String path, String valuePath,
            boolean hasValue, boolean preservation, int depth) {
        if (raw == null || !raw.isJsonObject()) return preservation ? null : invalid(path, "a property descriptor object");
        JsonObject object = raw.getAsJsonObject();
        if (!string(object.get("type"))) return preservation ? null : invalid(path + "/type", "a property type string");
        String type = object.get("type").getAsString();
        if (!PROPERTY_TYPES.contains(type))
            return issue("FIELD_ENUM_INVALID", path + "/type", "Choose a supported property type: logic, integer, number or string.");
        Set<String> allowed = new HashSet<>(Set.of("type", "name"));
        if (type.equals("integer") || type.equals("number")) allowed.addAll(Set.of("min", "max"));
        if (type.equals("string")) allowed.add("arrayData");
        // PropertyDataWithValue is flattened; StateMap uses a separate value member.
        if (hasValue && valuePath.equals(path + "/value")) allowed.add("value");
        var unknown = keys(object, allowed, path);
        if (unknown != null) return unknown;
        if (preservation) return null;
        if (!string(object.get("name")) || object.get("name").getAsString().isBlank()) return invalid(path + "/name", "a non-empty property name");
        Class<?> valueType = propertyType(type);
        if (type.equals("integer") || type.equals("number")) {
            for (String key : List.of("min", "max")) if (object.has(key)) {
                var problem = GenericFieldInputContract.inspect(valueType, object.get(key), path + "/" + key, null, true, false, depth + 1);
                if (problem != null) return problem;
            }
            // Gson allocates descriptors without their argument-taking constructors; omitted bounds are zero.
            var min = object.has("min") ? object.get("min").getAsBigDecimal() : java.math.BigDecimal.ZERO;
            var max = object.has("max") ? object.get("max").getAsBigDecimal() : java.math.BigDecimal.ZERO;
            if (min.compareTo(max) > 0) return issue("FIELD_VALUE_OUT_OF_RANGE", path + "/max", "Maximum must be at least minimum.");
            if (hasValue) {
                var problem = GenericFieldInputContract.inspect(valueType, value, valuePath, null, true, false, depth + 1);
                if (problem != null) return problem;
                if (value.getAsBigDecimal().compareTo(min) < 0 || value.getAsBigDecimal().compareTo(max) > 0)
                    return issue("FIELD_VALUE_OUT_OF_RANGE", valuePath, "Property value must be within the declared minimum and maximum.");
            }
        } else {
            JsonElement choices = object.get("arrayData");
            if (choices != null && !choices.isJsonNull()) {
                var problem = GenericFieldInputContract.inspect(String[].class, choices, path + "/arrayData", null, false, false, depth + 1);
                if (problem != null) return problem;
            }
            if (hasValue) {
                var problem = GenericFieldInputContract.inspect(valueType, value, valuePath, null, true, false, depth + 1);
                if (problem != null) return problem;
                if (type.equals("string") && choices != null && !choices.isJsonNull()
                        && choices.getAsJsonArray().asList().stream().noneMatch(value::equals))
                    return issue("FIELD_ENUM_INVALID", valuePath, "Choose one of the property's arrayData values.");
            }
        }
        return null;
    }

    private static BlockFieldContract.Issue keys(JsonObject object, Set<String> allowed, String path) {
        for (String key : object.keySet()) if (!allowed.contains(key))
            return issue("FIELD_UNSUPPORTED", path + "/" + key.replace("~", "~0").replace("/", "~1"), "This key is not stored by the custom field adapter.");
        return null;
    }
    private static boolean string(JsonElement value) { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(); }
    private static BlockFieldContract.Issue invalid(String path, String expected) { return issue("FIELD_TYPE_INVALID", path, "Expected " + expected + "; implicit conversion is not supported."); }
    private static BlockFieldContract.Issue issue(String code, String path, String message) { return new BlockFieldContract.Issue(code, path, message); }
}
