package dev.chronometer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;

public final class CopperChronometer implements ModInitializer {
    public static final String MOD_ID = "copper_chronometer";
    public static final ChronometerItem CHRONOMETER = new ChronometerItem();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "chronometer"), CHRONOMETER);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> entries.accept(CHRONOMETER));
        System.out.println("COPPER_CHRONOMETER_INITIALIZED");
    }
}
