package dev.copperbench.generated.wayfinder_bell.init;

import dev.copperbench.generated.wayfinder_bell.WayfinderBellMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {

	private ModItems() {
	}

	public static void register() {
	}

	private static void register(String name, Item item) {
		Registry.register(BuiltInRegistries.ITEM, WayfinderBellMod.id(name), item);
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
				.register(entries -> entries.accept(item));
	}
}
