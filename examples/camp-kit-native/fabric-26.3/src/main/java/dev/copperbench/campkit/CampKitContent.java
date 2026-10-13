package dev.copperbench.campkit;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Handwritten native content, independent of structured element generation. */
public final class CampKitContent {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("copperbench_camp_kit", path); }

    public static void initialize() {
        var kit = ResourceKey.create(Registries.ITEM, id("trail_kit"));
        Registry.register(BuiltInRegistries.ITEM, kit, new Item(new Item.Properties().setId(kit).stacksTo(16)
                .component(DataComponents.CUSTOM_NAME, Component.literal("Copper Camp Kit"))));
        var post = ResourceKey.create(Registries.BLOCK, id("camp_post"));
        Block block = Registry.register(BuiltInRegistries.BLOCK, post,
                new RotatedPillarBlock(BlockBehaviour.Properties.of().setId(post).strength(2.0f)));
        var postItem = ResourceKey.create(Registries.ITEM, id("camp_post"));
        Registry.register(BuiltInRegistries.ITEM, postItem, new BlockItem(block, new Item.Properties().setId(postItem)));
    }
}
