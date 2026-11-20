package m1delivery.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Hand-written mod content, outside the generator-managed package. The generated main class calls
 * {@link #register()} from its "mod init" user code block.
 */
public final class DeliveryContent {
    public static final Item DISCOVERY_ITEM = new Item(new Item.Properties().stacksTo(16));
    private static boolean registered;

    private DeliveryContent() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("m1_delivery", "discovery_item"),
                DISCOVERY_ITEM);
    }

    /** Ceiling division; the test host checks it against independent constants. */
    public static int bundles(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be nonnegative");
        return (count + 15) / 16;
    }
}
