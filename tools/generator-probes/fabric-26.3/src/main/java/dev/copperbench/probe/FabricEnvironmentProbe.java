package dev.copperbench.probe;

import net.fabricmc.api.ModInitializer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Compile-time qualification of the exact Minecraft and Fabric dependency graph. */
public final class FabricEnvironmentProbe implements ModInitializer {
    @Override public void onInitialize() {}

    public static Item.Properties itemProperties() {
        return new Item.Properties().stacksTo(16);
    }

    public static ItemStack sampleStack() {
        return new ItemStack(Items.STICK, 16).copy();
    }
}
