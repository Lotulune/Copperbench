package trial.resonance;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

public final class ResonanceMod implements ModInitializer {
    public static final String ACTIVE_KEY = "resonance_token:active";
    public static final Item TOKEN = new ResonanceToken(new Item.Properties().stacksTo(1));

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("resonance_token", "resonance_token"), TOKEN);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(TOKEN));
    }

    public static boolean isActive(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
               .copyTag().getBoolean(ACTIVE_KEY);
    }

    private static final class ResonanceToken extends Item {
        private ResonanceToken(Properties settings) { super(settings); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }
            if (!world.isClientSide) {
                CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                boolean active = !data.getBoolean(ACTIVE_KEY);
                data.putBoolean(ACTIVE_KEY, active);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                player.getCooldowns().addCooldown(this, 20);
                player.displayClientMessage(Component.literal(active ? "Resonance: active" : "Resonance: inactive"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
        }
    }
}
