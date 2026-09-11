package dev.wayfinder;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;

/** Native Fabric entry point, kept alongside Copperbench's generated source. */
public final class WayfinderBell implements ModInitializer {
    public static final WayfinderBellItem BELL = new WayfinderBellItem();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("wayfinder_bell", "wayfinder_bell"), BELL);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> entries.accept(BELL));
        System.out.println("WAYFINDER_BELL_INITIALIZED");
    }
}
