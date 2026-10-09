package dev.chronometer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Server-authoritative use; the format preference belongs to each physical item stack. */
public final class ChronometerItem extends Item {
    public static final int COOLDOWN_TICKS = 20;
    private static final String KEY = "copper_chronometer";

    public ChronometerItem() { super(new Item.Properties().stacksTo(1)); }

    public static boolean isTwelveHour(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.contains(KEY, 10) && root.getCompound(KEY).getBoolean("twelve_hour");
    }

    public static void setTwelveHour(ItemStack stack, boolean enabled) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag settings = root.getCompound(KEY).copy();
        settings.putBoolean("twelve_hour", enabled);
        root.put(KEY, settings);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);

        if (player.isShiftKeyDown()) {
            boolean next = !isTwelveHour(stack);
            setTwelveHour(stack, next);
            String formatKey = next ? "twelve_hour" : "twenty_four_hour";
            player.displayClientMessage(Component.translatable("message.copper_chronometer.mode",
                Component.translatable("format.copper_chronometer." + formatKey)), true);
        } else {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                player.displayClientMessage(Component.translatable("message.copper_chronometer.dimension"), true);
                return InteractionResultHolder.fail(stack);
            }
            ClockReading reading = ClockReading.at(level.getDayTime());
            player.displayClientMessage(Component.translatable("message.copper_chronometer.reading",
                reading.day(), reading.format(isTwelveHour(stack)),
                Component.translatable("phase.copper_chronometer." + reading.phase()),
                reading.ticksToNextPhase()), true);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS, 0.6F, 1.0F);
        return InteractionResultHolder.consume(stack);
    }
}
