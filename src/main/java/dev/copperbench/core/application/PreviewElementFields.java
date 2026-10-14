package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.tracks.PreviewCapabilityCatalog;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.util.HashSet;
import java.util.Set;

/** The writable preview slice. Stored fields outside it remain readable, never silently promoted. */
public final class PreviewElementFields {
    public static final Set<String> COMPONENT_FIELDS = Set.of("fuelBurnTime", "compostingLayers");
    public static final Set<String> ITEM_TEXTURES = Set.of("minecraft:barrier", "minecraft:stick", "minecraft:coal", "minecraft:paper");
    public static final Set<String> BLOCK_TEXTURES = Set.of("minecraft:stone", "minecraft:oak_log", "minecraft:cobblestone", "minecraft:iron_block");
    public static final Set<String> RECIPE_TYPES = Set.of("Crafting", "Smelting", "Blasting", "Smoking");
    public static final Set<String> RECIPE_ITEMS = Set.of("Items.STICK", "Items.STRING", "Items.PAPER", "Items.FLINT",
            "Items.DIAMOND", "Items.COAL#0", "Items.IRON_NUGGET", "Blocks.SAND");
    private PreviewElementFields() {}

    public static JsonObject inputSchema(String generator, java.lang.reflect.Field field) {
        if (VersionTrackCatalog.official().requiresPreview(generator) && field.getDeclaringClass() == net.mcreator.element.types.Recipe.class) {
            if (field.getName().equals("recipeType")) {
                JsonObject shape = FieldInputProjection.typed("string");
                shape.add("enum", new com.google.gson.Gson().toJsonTree(RECIPE_TYPES.stream().sorted().toList()));
                return shape;
            }
            JsonObject shape = FieldInputProjection.schema(field);
            if (field.getName().equals("cookingTime")) shape.addProperty("minimum", 1);
            if (field.getType() == net.mcreator.element.parts.MItemBlock.class || field.getName().equals("recipeSlots"))
                restrictRecipeReferenceSchema(shape);
            return shape;
        }
        if (VersionTrackCatalog.official().requiresPreview(generator) && field.getName().equals("texture")) {
            JsonObject shape = FieldInputProjection.typed("string");
            Set<String> textures = field.getDeclaringClass() == net.mcreator.element.types.Block.class ? BLOCK_TEXTURES : ITEM_TEXTURES;
            shape.add("enum", new com.google.gson.Gson().toJsonTree(textures.stream().sorted().toList()));
            return shape;
        }
        if (VersionTrackCatalog.official().requiresPreview(generator) && field.getName().equals("rotationMode")) {
            JsonObject shape = FieldInputProjection.typed("integer");
            shape.add("enum", new com.google.gson.Gson().toJsonTree(java.util.List.of(0, 5)));
            return shape;
        }
        return FieldInputProjection.schema(field);
    }

    public static boolean writable(String generator, String type, String field) {
        if (VersionTrackCatalog.official().requiresPreview(generator))
            return PreviewCapabilityCatalog.official().fields(generator, type).contains(field);
        return !type.equals("item") || !COMPONENT_FIELDS.contains(field);
    }

    public static BlockFieldContract.Issue validateChange(String generator, String type, JsonObject before, JsonObject after) {
        JsonObject oldValues = BlockFieldContract.merged(before), values = BlockFieldContract.merged(after);
        boolean preview = VersionTrackCatalog.official().requiresPreview(generator);
        if (preview && PreviewCapabilityCatalog.official().fields(generator, type).isEmpty())
            return new BlockFieldContract.Issue("PREVIEW_ELEMENT_UNVERIFIED", "/elementType", "This element type has no verified preview fields.");
        boolean recovery = preview && configurationRecovery(type, before, after);
        JsonObject defaults = recovery ? ElementFieldCodec.gson().toJsonTree(
                StructuredElementDefaults.block(null, null, "")).getAsJsonObject() : new JsonObject();
        Set<String> names = new HashSet<>(oldValues.keySet()); names.addAll(values.keySet());
        for (String name : names) {
            // Recovery metadata is checked against the durable fingerprint by the mutation gateway.
            // An effective definition also expands omitted defaults; it must not activate new features.
            if (recovery && (name.equals("configurationResolution")
                    || !oldValues.has(name) && defaults.has(name) && defaults.get(name).equals(values.get(name)))) continue;
            if (!writable(generator, type, name) && !java.util.Objects.equals(oldValues.get(name), values.get(name)))
                return new BlockFieldContract.Issue(preview ? "PREVIEW_FIELD_UNVERIFIED" : "FIELD_UNSUPPORTED",
                        BlockFieldContract.path(after, name), "This field is outside the active generator's writable scope: " + name);
        }
        if (recovery) return validateGeneration(generator, type, after);
        return preview ? validateValues(type, values) : null;
    }

    static boolean configurationRecovery(String type, JsonObject before, JsonObject after) {
        if (!type.equals("block") || before.isEmpty() || !after.has("configurationResolution")
                || !after.get("configurationResolution").isJsonObject()) return false;
        JsonObject resolution = after.getAsJsonObject("configurationResolution");
        return resolution.keySet().equals(Set.of("mode", "sourceFingerprint"))
                && resolution.get("mode").isJsonPrimitive() && resolution.getAsJsonPrimitive("mode").isString()
                && Set.of("adopt_definition", "reapply_declared").contains(resolution.get("mode").getAsString())
                && resolution.get("sourceFingerprint").isJsonPrimitive()
                && resolution.getAsJsonPrimitive("sourceFingerprint").isString()
                && resolution.get("sourceFingerprint").getAsString().matches("[a-f0-9]{64}");
    }

    /** Imported definitions may contain unchanged defaults and opaque metadata, but not active unverified features. */
    public static BlockFieldContract.Issue validateGeneration(String generator, String type, JsonObject raw) {
        if (!VersionTrackCatalog.official().requiresPreview(generator)) return null;
        if (PreviewCapabilityCatalog.official().fields(generator, type).isEmpty())
            return new BlockFieldContract.Issue("PREVIEW_ELEMENT_UNVERIFIED", "/elementType", "This structured type is not verified for this preview.");
        JsonObject values = BlockFieldContract.merged(raw);
        if (type.equals("function")) return FunctionFieldContract.validatePreview(raw);
        if (type.equals("code")) return CodeFieldContract.validate(raw);
        var definition = type.equals("block") ? StructuredElementDefaults.block(null, null, "")
                : type.equals("recipe") ? StructuredElementDefaults.recipe(null, null, "") : StructuredElementDefaults.item(null, null, "");
        JsonObject defaults = ElementFieldCodec.gson().toJsonTree(definition).getAsJsonObject();
        Set<String> known = java.util.Arrays.stream(definition.getClass().getFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers())).map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (var entry : values.entrySet()) {
            String name = entry.getKey();
            if (!writable(generator, type, name) && known.contains(name)
                    && !entry.getValue().equals(defaults.get(name)))
                return new BlockFieldContract.Issue("PREVIEW_FIELD_UNVERIFIED", "/" + name,
                        "Generation would ignore an unsupported value; resolve it before generation: " + name);
        }
        return validateValues(type, values);
    }

    private static BlockFieldContract.Issue validateValues(String type, JsonObject values) {
        if (type.equals("function")) return FunctionFieldContract.validatePreview(values);
        if (type.equals("code")) return CodeFieldContract.validate(values);
        if (type.equals("recipe")) {
            if (values.has("recipeType") && (!values.get("recipeType").isJsonPrimitive()
                    || !values.get("recipeType").getAsJsonPrimitive().isString() || !RECIPE_TYPES.contains(values.get("recipeType").getAsString())))
                return new BlockFieldContract.Issue("FIELD_ENUM_INVALID", "/recipeType", "Choose crafting, smelting, blasting or smoking.");
            if (values.has("cookingTime") && (!BlockFieldContract.isIntegerInStorageRange(values.get("cookingTime"), int.class)
                    || values.get("cookingTime").getAsInt() < 1 || values.get("cookingTime").getAsInt() > 1000000))
                return new BlockFieldContract.Issue("FIELD_VALUE_OUT_OF_RANGE", "/cookingTime", "Cooking time must be 1–1000000 ticks.");
            for (String name : java.util.List.of("recipeReturnStack", "smeltingInputStack", "smeltingReturnStack",
                    "blastingInputStack", "blastingReturnStack", "smokingInputStack", "smokingReturnStack")) {
                var issue = recipeReference(values.get(name), "/" + name);
                if (issue != null) return issue;
            }
            if (values.has("recipeSlots") && values.get("recipeSlots").isJsonArray()) {
                var slots = values.getAsJsonArray("recipeSlots");
                for (int index = 0; index < slots.size(); index++) {
                    var issue = recipeReference(slots.get(index), "/recipeSlots/" + index);
                    if (issue != null) return issue;
                }
            }
        }
        if (Set.of("item", "block").contains(type) && values.has("texture")) {
            var texture = values.get("texture");
            if (!texture.isJsonPrimitive() || !texture.getAsJsonPrimitive().isString()
                    || !(type.equals("item") ? ITEM_TEXTURES : BLOCK_TEXTURES).contains(texture.getAsString()))
                return new BlockFieldContract.Issue("FIELD_REFERENCE_INVALID", "/texture",
                        "Choose a texture from the active field contract.");
        }
        if (type.equals("block") && values.has("rotationMode")) {
            var rotation = values.get("rotationMode");
            if (!BlockFieldContract.isIntegerInStorageRange(rotation, int.class)
                    || !Set.of(0, 5).contains(rotation.getAsInt()))
                return new BlockFieldContract.Issue("FIELD_ENUM_INVALID", "/rotationMode", "Choose no rotation (0) or pillar axis (5).");
        }
        return null;
    }

    private static BlockFieldContract.Issue recipeReference(com.google.gson.JsonElement value, String path) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) value = value.getAsJsonObject().get("value");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            return new BlockFieldContract.Issue("FIELD_TYPE_INVALID", path, "Expected an item reference.");
        String name = value.getAsString();
        if (name.isEmpty() || RECIPE_ITEMS.contains(name) || name.matches("CUSTOM:[A-Za-z][A-Za-z0-9_]{0,63}")) return null;
        return new BlockFieldContract.Issue("FIELD_REFERENCE_INVALID", path, "Choose a published vanilla item or a workspace item reference.");
    }

    private static void restrictRecipeReferenceSchema(com.google.gson.JsonElement value) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(PreviewElementFields::restrictRecipeReferenceSchema); return; }
        if (!value.isJsonObject()) return;
        JsonObject shape = value.getAsJsonObject();
        if (shape.has("type") && shape.get("type").isJsonPrimitive() && shape.get("type").getAsString().equals("string")) {
            var choices = new com.google.gson.JsonArray();
            JsonObject vanilla = new JsonObject();
            var names = new java.util.ArrayList<>(RECIPE_ITEMS); names.add(""); names.sort(String::compareTo);
            vanilla.add("enum", new com.google.gson.Gson().toJsonTree(names)); choices.add(vanilla);
            JsonObject workspace = new JsonObject(); workspace.addProperty("pattern", "^CUSTOM:[A-Za-z][A-Za-z0-9_]{0,63}$"); choices.add(workspace);
            shape.add("anyOf", choices);
        } else shape.entrySet().forEach(entry -> restrictRecipeReferenceSchema(entry.getValue()));
    }
}
