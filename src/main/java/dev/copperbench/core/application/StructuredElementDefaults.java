package dev.copperbench.core.application;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.parts.*;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.Recipe;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import java.lang.reflect.Modifier;

/** Pure construction defaults shared by discovery and the persisted definition adapter. */
public final class StructuredElementDefaults {
    private StructuredElementDefaults() {}

    public static Block block(ModElement element, Workspace workspace, String displayName) {
        Block block = new Block(element);
        block.name = displayName;
        block.customModelName = "Normal";
        block.renderType = 10;
        block.texture = new TextureHolder(workspace, "minecraft:stone");
        block.transparencyType = "SOLID";
        block.colorOnMap = new MapColor(workspace, "DEFAULT");
        block.noteBlockInstrument = new NoteBlockInstrument(workspace, "harp");
        block.aiPathNodeType = new AIPathNodeType(workspace, "DEFAULT");
        block.soundOnStep = new StepSound(workspace, "STONE");
        block.luminance = new net.mcreator.element.parts.procedure.NumberProcedure(null, 0);
        block.inventoryStackSize = 99;
        block.frequencyPerChunks = 10;
        block.frequencyOnChunk = 16;
        block.maxGenerateHeight = 64;
        return block;
    }

    public static Item item(ModElement element, Workspace workspace, String displayName) {
        Item item = new Item(element);
        item.name = displayName;
        item.customModelName = "Normal";
        item.stackSize = 64;
        item.toolType = 1;
        item.animation = new ItemUseAnimation(workspace, "eat");
        item.texture = new TextureHolder(workspace, "minecraft:barrier");
        fillStrings(item);
        return item;
    }

    public static Recipe recipe(ModElement element, Workspace workspace, String name) {
        Recipe recipe = new Recipe(element);
        recipe.name = name;
        recipe.recipeType = "Crafting";
        recipe.recipeSlots = new MItemBlock[9];
        for (int index = 0; index < recipe.recipeSlots.length; index++)
            recipe.recipeSlots[index] = new MItemBlock(workspace, "");
        recipe.recipeReturnStack = new MItemBlock(workspace, "");
        fillStrings(recipe);
        return recipe;
    }

    public static void fillStrings(GeneratableElement definition) {
        for (var field : definition.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
            try {
                if (field.get(definition) != null) continue;
                var options = field.getAnnotation(LimitedOptions.class);
                field.set(definition, options != null && options.value().length > 0 ? options.value()[0] : "");
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot read public definition field " + field.getName(), exception);
            }
        }
    }
}
