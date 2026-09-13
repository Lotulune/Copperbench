package dev.wayfinder;

import java.util.Optional;
import net.minecraft.core.BlockPos;
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

public final class WayfinderBellItem extends Item {
    public static final int COOLDOWN_TICKS = 20;
    private static final String KEY = "wayfinder_bell";

    public WayfinderBellItem() { super(new Item.Properties().stacksTo(1)); }

    public record Anchor(String dimension, BlockPos position) {}

    public static Optional<Anchor> readAnchor(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(KEY, 10)) return Optional.empty();
        CompoundTag tag = root.getCompound(KEY);
        if (!tag.contains("dimension", 8) || !tag.contains("x", 3)
            || !tag.contains("y", 3) || !tag.contains("z", 3)) return Optional.empty();
        return Optional.of(new Anchor(tag.getString("dimension"),
            new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);

        BlockPos at = player.blockPosition();
        if (player.isShiftKeyDown()) {
            CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", level.dimension().location().toString());
            tag.putInt("x", at.getX()); tag.putInt("y", at.getY()); tag.putInt("z", at.getZ());
            root.put(KEY, tag);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            player.displayClientMessage(Component.translatable("message.wayfinder_bell.saved",
                at.getX(), at.getY(), at.getZ()), true);
        } else {
            Optional<Anchor> anchor = readAnchor(stack);
            if (anchor.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.wayfinder_bell.empty"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!anchor.get().dimension().equals(level.dimension().location().toString())) {
                player.displayClientMessage(Component.translatable("message.wayfinder_bell.dimension",
                    anchor.get().dimension()), true);
                return InteractionResultHolder.fail(stack);
            }
            BlockPos target = anchor.get().position();
            Navigation.Reading reading = Navigation.between(at.getX(), at.getY(), at.getZ(),
                target.getX(), target.getY(), target.getZ());
            player.displayClientMessage(Component.translatable("message.wayfinder_bell.direction",
                Component.translatable("direction.wayfinder_bell." + reading.direction()),
                Math.round(reading.horizontalDistance()), reading.heightDifference()), true);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
        return InteractionResultHolder.consume(stack);
    }
}
