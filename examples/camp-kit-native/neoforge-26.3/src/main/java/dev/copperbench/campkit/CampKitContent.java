package dev.copperbench.campkit;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native NeoForge registrations; no structured element templates are involved. */
public final class CampKitContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("copperbench_camp_kit");
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("copperbench_camp_kit");

    public static void initialize(IEventBus bus) {
        ITEMS.registerSimpleItem("trail_kit", properties -> properties.stacksTo(16)
                .component(DataComponents.CUSTOM_NAME, Component.literal("Copper Camp Kit")));
        var post = BLOCKS.registerBlock("camp_post", RotatedPillarBlock::new, properties -> properties.strength(2.0f));
        ITEMS.registerSimpleBlockItem(post);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
