package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.core.workspace.WorkspaceState;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.element.types.Recipe;
import net.mcreator.element.types.interfaces.NonNullIf;
import net.mcreator.generator.GeneratorWrapper;
import net.mcreator.generator.mapping.NameMapper;
import java.lang.reflect.Modifier;
import java.util.Set;

/** Generation checks for native item/recipe definitions, using the same input fields and defaults as Core. */
public final class ElementGenerationValidation {
    private ElementGenerationValidation() {}
    public static boolean nativeDefinitions(WorkspaceState workspace) {
        // This is the persisted upstream settings format used by MCreatorWorkspaceStateMapper.
        return workspace.upstreamDocument().has("workspaceSettings");
    }

    public static BlockFieldContract.Issue validate(String type, JsonObject raw, Set<String> elementNames) {
        JsonObject values = BlockFieldContract.merged(raw);
        GeneratableElement defaults = type.equals("item") ? StructuredElementDefaults.item(null, null, "") :
                StructuredElementDefaults.recipe(null, null, "");
        JsonObject effective = ElementFieldCodec.gson().toJsonTree(defaults).getAsJsonObject();
        for (var entry : values.entrySet()) effective.add(entry.getKey(), entry.getValue());
        try {
            if (type.equals("item") && values.has("maxStackSize")) {
                if (values.has("stackSize") && !values.get("stackSize").equals(values.get("maxStackSize")))
                    return new BlockFieldContract.Issue("FIELD_ALIAS_CONFLICT", "/maxStackSize", "stackSize and maxStackSize must agree.");
                var issue = GenericFieldInputContract.validate(defaults.getClass().getField("stackSize"), values.get("maxStackSize"), "/maxStackSize");
                if (issue != null) return issue;
                effective.add("stackSize", values.get("maxStackSize"));
            }
            for (var field : defaults.getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
                if (values.has(field.getName())) {
                    var issue = GenericFieldInputContract.validate(field, values.get(field.getName()), "/" + field.getName());
                    if (issue != null) return issue;
                    if (field.getType() == String.class && values.get(field.getName()).isJsonNull()) {
                        var choices = field.getAnnotation(net.mcreator.element.types.interfaces.LimitedOptions.class);
                        effective.addProperty(field.getName(), choices != null && choices.value().length > 0 ? choices.value()[0] : "");
                    }
                }
                NonNullIf condition = field.getAnnotation(NonNullIf.class);
                if (condition != null) for (String expression : condition.value()) {
                    if (WorkspaceApplicationService.conditionExpressionMatches(effective, expression) && empty(effective.get(field.getName())))
                        return new BlockFieldContract.Issue("FIELD_REQUIRED_BY_CONDITION", "/" + field.getName(),
                                field.getName() + " is required when " + expression + ".");
                }
                if (field.getType() == MItemBlock.class) {
                    var issue = reference(effective.get(field.getName()), "/" + field.getName(), elementNames);
                    if (issue != null) return issue;
                }
            }
            if (type.equals("recipe") && "Crafting".equals(effective.get("recipeType").getAsString())) {
                JsonArray slots = effective.getAsJsonArray("recipeSlots");
                if (slots == null || slots.size() != 9)
                    return new BlockFieldContract.Issue("FIELD_VALUE_OUT_OF_RANGE", "/recipeSlots", "Crafting recipeSlots must contain nine slots.");
                boolean ingredient = false;
                for (int i = 0; i < slots.size(); i++) {
                    ingredient |= !empty(slots.get(i));
                    var issue = reference(slots.get(i), "/recipeSlots/" + i, elementNames);
                    if (issue != null) return issue;
                }
                if (!ingredient) return new BlockFieldContract.Issue("FIELD_REQUIRED", "/recipeSlots", "A crafting recipe needs at least one ingredient.");
            }
        } catch (NoSuchFieldException exception) { throw new IllegalStateException(exception); }
        return null;
    }

    private static boolean empty(JsonElement value) {
        if (value == null || value.isJsonNull()) return true;
        if (value.isJsonObject() && value.getAsJsonObject().has("value")) return empty(value.getAsJsonObject().get("value"));
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString().isBlank();
        return value.isJsonArray() && value.getAsJsonArray().isEmpty();
    }
    private static BlockFieldContract.Issue reference(JsonElement value, String path, Set<String> names) {
        if (empty(value)) return null;
        String name = value.isJsonObject() ? value.getAsJsonObject().get("value").getAsString() : value.getAsString();
        if (name.startsWith(NameMapper.MCREATOR_PREFIX) && !names.contains(GeneratorWrapper.getElementPlainName(name)))
            return new BlockFieldContract.Issue("FIELD_REFERENCE_INVALID", path, "Referenced workspace element does not exist: " + name);
        return null;
    }
}
