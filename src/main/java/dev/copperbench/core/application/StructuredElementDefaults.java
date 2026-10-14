package dev.copperbench.core.application;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.parts.*;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.Recipe;
import net.mcreator.element.types.Tool;
import net.mcreator.element.types.LivingEntity;
import net.mcreator.element.types.SpecialEntity;
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

    public static GeneratableElement forType(String type, ModElement element, Workspace workspace, String name) {
        return switch (type) {
            case "item" -> item(element, workspace, name);
            case "recipe" -> recipe(element, workspace, name);
            case "tool" -> tool(element, workspace, name);
            case "livingentity" -> livingEntity(element, workspace, name);
            case "specialentity" -> specialEntity(element, workspace, name);
            default -> throw new IllegalArgumentException("No structured creation defaults for " + type);
        };
    }

    private static Tool tool(ModElement element, Workspace workspace, String name) {
        Tool tool = new Tool(element);
        tool.name = name;
        tool.toolType = "Pickaxe";
        tool.customModelName = "Normal";
        tool.texture = new TextureHolder(workspace, "minecraft:iron_pickaxe");
        tool.efficiency = 4;
        tool.enchantability = 2;
        tool.damageVsEntity = 4;
        tool.usageCount = 100;
        fillStrings(tool);
        return tool;
    }

    private static LivingEntity livingEntity(ModElement element, Workspace workspace, String name) {
        LivingEntity entity = new LivingEntity(element);
        entity.mobName = name;
        entity.mobModelName = "Biped";
        entity.mobBehaviourType = "Creature";
        entity.modelWidth = 0.6;
        entity.modelHeight = 1.8;
        entity.health = 10;
        entity.movementSpeed = 0.3;
        entity.mobSpawningType = new MobSpawnType(workspace, "creature");
        entity.hasSpawnEgg = true;
        entity.spawnEggBaseColor = new java.awt.Color(0xc47c32);
        entity.spawnEggDotColor = new java.awt.Color(0x67492c);
        entity.spawningProbability = 20;
        entity.minNumberOfMobsPerGroup = 4;
        entity.maxNumberOfMobsPerGroup = 4;
        entity.equipmentMainHand = new MItemBlock(workspace, "");
        entity.equipmentOffHand = new MItemBlock(workspace, "");
        entity.equipmentHelmet = new MItemBlock(workspace, "");
        entity.equipmentBody = new MItemBlock(workspace, "");
        entity.equipmentLeggings = new MItemBlock(workspace, "");
        entity.equipmentBoots = new MItemBlock(workspace, "");
        entity.mobDrop = new MItemBlock(workspace, "");
        try {
            entity.aixml = LivingEntity.class.getField("aixml")
                    .getAnnotation(net.mcreator.blockly.data.BlocklyXML.class).defaultXML();
        } catch (NoSuchFieldException exception) { throw new IllegalStateException(exception); }
        fillStrings(entity);
        return entity;
    }

    private static SpecialEntity specialEntity(ModElement element, Workspace workspace, String name) {
        SpecialEntity entity = new SpecialEntity(element);
        entity.name = name;
        entity.entityType = "Boat";
        entity.itemTexture = new TextureHolder(workspace, "minecraft:oak_boat");
        fillStrings(entity);
        return entity;
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
