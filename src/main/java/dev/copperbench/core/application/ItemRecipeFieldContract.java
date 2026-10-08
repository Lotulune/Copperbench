package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.Recipe;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.Numeric;
import net.mcreator.generator.mapping.MappableElement;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Partial discovery over the storage fields already checked by GenericFieldInputContract. */
final class ItemRecipeFieldContract {
    private ItemRecipeFieldContract() {}

    // This reviewed subset deliberately excludes custom adapters and legacy aliases.
    // Adding a field here publishes its input shape; it does not grant a new write capability.
    private static final List<String> ITEM_FIELDS = List.of(
            "texture", "customModelName", "guiTexture", "rarity", "stackSize", "enchantability",
            "useDuration", "damageCount", "recipeRemainder", "immuneToFire", "isPiglinCurrency",
            "repairItems", "stayInGridWhenCrafting", "damageOnCrafting", "enableMeleeDamage",
            "damageVsEntity", "attackSpeed", "inventorySize", "inventoryStackSize", "isFood",
            "nutritionalValue", "saturation", "eatResultItem", "isMeat", "isAlwaysEdible");
    private static final List<String> RECIPE_FIELDS = List.of(
            "namespace", "recipeType", "recipeRetstackSize", "group", "unlockingItems",
            "cookingBookCategory", "xpReward", "cookingTime", "craftingBookCategory", "recipeShapeless",
            "recipeSlots", "recipeReturnStack", "smeltingInputStack", "smeltingReturnStack",
            "blastingInputStack", "blastingReturnStack", "smokingInputStack", "smokingReturnStack",
            "stoneCuttingInputStack", "stoneCuttingReturnStack", "campfireCookingInputStack",
            "campfireCookingReturnStack", "smithingInputStack", "smithingInputAdditionStack",
            "smithingInputTemplateStack", "smithingReturnStack", "brewingInputStack",
            "brewingIngredientStack", "brewingReturnStack");

    static JsonObject capabilities(String elementType) {
        Class<?> storage = switch (elementType) {
            case "item" -> Item.class;
            case "recipe" -> Recipe.class;
            default -> throw new IllegalArgumentException("No reviewed field projection for " + elementType);
        };
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", 1);
        result.addProperty("elementType", elementType);
        result.addProperty("coverage", "partial");
        result.addProperty("scope", "Input shapes, numeric ranges and options for the listed definition fields, "
                + "from the storage types and annotations used by write validation. Not a complete element schema, "
                + "default-value contract, conditional validation, reference resolution, generator support or gameplay acceptance.");
        result.addProperty("compatibilityPath", "/fields");
        result.addProperty("aliasPolicy", "Use a top-level field or its /fields spelling; simultaneous spellings must agree.");
        result.addProperty("nullPolicy", "nullable describes the input-type check only; contextual checks and adapters "
                + "may reject or normalize null. Array members must be non-null.");
        result.addProperty("additionalFields", "Unlisted fields are not certified by this partial projection. "
                + "Inspect get_mod_element_editor for an existing element and fieldContracts.generic/custom for their boundaries.");
        if (elementType.equals("item")) result.addProperty("stackSizePolicy",
                "Use canonical stackSize. The legacy maxStackSize adapter alias is outside this strict input-shape subset.");
        JsonArray fields = new JsonArray();
        Set<String> writable = ElementMappingSupport.fields(elementType);
        for (String name : elementType.equals("item") ? ITEM_FIELDS : RECIPE_FIELDS) {
            Field reflected = field(storage, name);
            if (!writable.contains(name)
                    || Modifier.isStatic(reflected.getModifiers()) || Modifier.isFinal(reflected.getModifiers())
                    || Modifier.isTransient(reflected.getModifiers()))
                throw new IllegalStateException("Discovered field is not writable: " + elementType + "." + name);
            JsonObject field = shape(reflected.getGenericType(), false);
            field.addProperty("name", name);
            field.addProperty("path", "/" + name);
            field.addProperty("compatibilityPath", "/fields/" + name);
            field.addProperty("writable", true);
            Numeric range = reflected.getAnnotation(Numeric.class);
            if (range != null) {
                field.addProperty("min", range.min());
                field.addProperty("max", range.max());
            }
            LimitedOptions options = reflected.getAnnotation(LimitedOptions.class);
            if (options != null) {
                JsonArray values = new JsonArray();
                for (String option : options.value()) values.add(option);
                field.add("options", values);
            }
            fields.add(field);
        }
        result.add("fields", fields);
        return result;
    }

    private static Field field(Class<?> storage, String name) {
        try { return storage.getField(name); }
        catch (NoSuchFieldException exception) { throw new IllegalStateException(exception); }
    }

    private static JsonObject shape(Type type, boolean arrayMember) {
        Class<?> kind = type instanceof Class<?> c ? c
                : type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c ? c : null;
        JsonObject result = new JsonObject();
        if (kind == int.class) result.addProperty("type", "integer");
        else if (kind == double.class) {
            result.addProperty("type", "number");
            result.addProperty("finite", true);
        } else if (kind == boolean.class) result.addProperty("type", "boolean");
        else if (kind == String.class || kind == TextureHolder.class) result.addProperty("type", "string");
        else if (kind != null && MappableElement.class.isAssignableFrom(kind)) {
            JsonArray types = new JsonArray(); types.add("string"); types.add("object");
            result.add("type", types);
            JsonObject value = new JsonObject(); value.addProperty("type", "string");
            JsonObject properties = new JsonObject(); properties.add("value", value);
            result.add("properties", properties);
            JsonArray required = new JsonArray(); required.add("value");
            result.add("required", required);
            result.addProperty("additionalProperties", false);
            result.addProperty("referenceResolution", "Input form only: a reference name string or {value: string}.");
        } else if (kind != null && (kind.isArray() || Collection.class.isAssignableFrom(kind))) {
            Type itemType = kind.isArray() ? kind.getComponentType()
                    : ((ParameterizedType) type).getActualTypeArguments()[0];
            result.addProperty("type", "array");
            result.add("items", shape(itemType, true));
        } else throw new IllegalStateException("Unreviewed field input type: " + type);
        result.addProperty("nullable", !kind.isPrimitive() && !arrayMember);
        return result;
    }
}
