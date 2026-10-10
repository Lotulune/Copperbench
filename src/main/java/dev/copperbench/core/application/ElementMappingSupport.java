package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.mcreator.element.ModElementTypeLoader;
import java.lang.reflect.Modifier;
import java.util.*;

/** Inventory of the existing specialized adapters; unknown metadata does not imply a write capability. */
public final class ElementMappingSupport {
    public static final java.util.regex.Pattern ELEMENT_NAME = java.util.regex.Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private ElementMappingSupport() {}
    private static final Map<String, Set<String>> SPECIALIZED = Map.of(
        "procedure", Set.of("procedurexml", "procedureIr"),
        "projectile", Set.of("projectileItem", "entityModel", "customModelTexture", "actionSound", "power", "damage", "knockback",
                "showParticles", "disableGravity", "igniteFire", "disableDiscarding", "onHitsBlock", "onHitsPlayer", "onHitsEntity", "onFlyingTick"),
        "function", Set.of("namespace", "commands", "code"),
        "loottable", Set.of("namespace", "type", "pools"),
        "achievement", Set.of("title", "description", "icon", "background", "disableDisplay", "showPopup", "announceToChat",
                "hideIfNotCompleted", "rewardLoot", "rewardRecipes", "rewardFunction", "rewardXP", "frame", "parent", "triggerxml"),
        "code", Set.of("code", "codeFiles", "sourceFingerprints"));

    public static Set<String> fields(String type) {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(Set.of("name", "displayName", "description"));
        if (type.equals("block")) {
            fields.addAll(BlockFieldContract.DEFINITION_FIELDS); fields.addAll(BlockFieldContract.PRODUCT_FIELDS);
            fields.add("configurationResolution");
        } else if (SPECIALIZED.containsKey(type)) fields.addAll(SPECIALIZED.get(type));
        else {
            Class<?> storage = switch (type) {
                case "item" -> net.mcreator.element.types.Item.class;
                case "recipe" -> net.mcreator.element.types.Recipe.class;
                default -> {
                    try { yield ModElementTypeLoader.getModElementType(type).getModElementStorageClass(); }
                    catch (IllegalArgumentException ignored) { yield null; }
                }
            };
            if (storage != null) for (var field : storage.getFields())
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                        && !Modifier.isTransient(field.getModifiers())) fields.add(field.getName());
            if (type.equals("item")) fields.addAll(Set.of("maxStackSize", "modelResource", "textureBase64"));
        }
        return Collections.unmodifiableSet(fields);
    }

    public static boolean specialized(String type) { return SPECIALIZED.containsKey(type) || Set.of("block", "item", "recipe").contains(type); }

    public static BlockFieldContract.Issue unsupportedChange(String type, JsonObject before, JsonObject after) {
        JsonObject oldValues = BlockFieldContract.merged(before), nextValues = BlockFieldContract.merged(after);
        Set<String> supported = fields(type);
        if (Set.of("item", "recipe").contains(type)) {
            for (String name : List.of("displayName", "description", "modelResource", "textureBase64")) {
                JsonElement value = nextValues.get(name);
                if (value != null && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()))
                    return new BlockFieldContract.Issue("FIELD_TYPE_INVALID", BlockFieldContract.path(after, name),
                            "Expected a non-null string.");
            }
        }
        for (var entry : nextValues.entrySet())
            if (!supported.contains(entry.getKey()) && !entry.getValue().equals(oldValues.get(entry.getKey())))
                return new BlockFieldContract.Issue("FIELD_UNSUPPORTED", BlockFieldContract.path(after, entry.getKey()),
                        "This field has no supported " + type + " definition adapter; imported values are preserved read-only.");
        if (type.equals("item") && nextValues.has("stackSize") && nextValues.has("maxStackSize")
                && !nextValues.get("stackSize").equals(nextValues.get("maxStackSize")))
            return new BlockFieldContract.Issue("FIELD_ALIAS_CONFLICT", "/maxStackSize", "stackSize and maxStackSize must agree.");
        if (type.equals("item") && nextValues.has("maxStackSize")) {
            try {
                var issue = GenericFieldInputContract.validate(net.mcreator.element.types.Item.class.getField("stackSize"),
                        nextValues.get("maxStackSize"), BlockFieldContract.path(after, "maxStackSize"));
                if (issue != null) return issue;
            } catch (NoSuchFieldException exception) { throw new IllegalStateException(exception); }
        }
        if (type.equals("loottable")) return LootTableFieldContract.validate(before, after);
        if (type.equals("function")) return FunctionFieldContract.validate(after);
        if (type.equals("code")) return CodeFieldContract.validate(after);
        if (type.equals("procedure")) return ProcedureFieldContract.validate(after);
        if (SpecializedFieldContract.supports(type)) return SpecializedFieldContract.validate(type, after);
        return null;
    }
}
