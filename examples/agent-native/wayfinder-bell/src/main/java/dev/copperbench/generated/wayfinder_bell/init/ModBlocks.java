package dev.copperbench.generated.wayfinder_bell.init;

import dev.copperbench.generated.wayfinder_bell.WayfinderBellMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

	private ModBlocks() {
	}

	public static void register() {
	}

	private static void register(String name, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, WayfinderBellMod.id(name), block);
		BlockItem item = Registry.register(BuiltInRegistries.ITEM, WayfinderBellMod.id(name),
				new BlockItem(block, new Item.Properties()));
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(entries -> entries.accept(item));
	}
}
