package dev.copperbench.probe;

import net.neoforged.fml.common.Mod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Compile-time qualification of the exact Minecraft and NeoForge dependency graph. */
@Mod("copperbench_environment_probe")
public final class NeoForgeEnvironmentProbe {
    public static Item.Properties itemProperties() {
        return new Item.Properties().stacksTo(16);
    }

    public static ItemStack sampleStack() {
        return new ItemStack(Items.STICK, 16).copy();
    }
}
