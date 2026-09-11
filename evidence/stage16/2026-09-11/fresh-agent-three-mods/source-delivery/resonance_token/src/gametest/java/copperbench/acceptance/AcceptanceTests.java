package copperbench.acceptance;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import trial.resonance.ResonanceMod;

public final class AcceptanceTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void defaultToggleAndIndependentStacks(GameTestHelper context) {
        RecordingPlayer player = new RecordingPlayer(context.getLevel());
        ItemStack first = new ItemStack(ResonanceMod.TOKEN);
        ItemStack second = new ItemStack(ResonanceMod.TOKEN);
        context.assertFalse(active(first), "R1: a fresh stack must be inactive");
        use(player, first, InteractionHand.MAIN_HAND);
        context.assertTrue(active(first), "R1: first right-click activates the used stack");
        context.assertFalse(active(second), "R4: a distinct untouched stack remains inactive");
        expire(player);
        use(player, second, InteractionHand.OFF_HAND);
        context.assertTrue(active(second), "R1: offhand use operates on the offhand stack");
        expire(player);
        use(player, first, InteractionHand.MAIN_HAND);
        context.assertFalse(active(first), "R1: next permitted use toggles off");
        context.assertTrue(active(second), "R4: toggling the first stack leaves the second active");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cooldownRejectsUseUntilExactlyTwentyTicks(GameTestHelper context) {
        RecordingPlayer player = new RecordingPlayer(context.getLevel());
        ItemStack stack = new ItemStack(ResonanceMod.TOKEN);
        use(player, stack, InteractionHand.MAIN_HAND);
        context.assertTrue(player.getCooldowns().isOnCooldown(ResonanceMod.TOKEN), "R2: cooldown begins immediately");
        use(player, stack, InteractionHand.MAIN_HAND);
        context.assertTrue(active(stack), "R2: immediate repeat cannot toggle");
        for (int tick = 1; tick <= 19; tick++) player.getCooldowns().tick();
        context.assertTrue(player.getCooldowns().isOnCooldown(ResonanceMod.TOKEN), "R2: cooldown must still exist after 19 ticks");
        use(player, stack, InteractionHand.MAIN_HAND);
        context.assertTrue(active(stack), "R2: use at 19 ticks must not toggle");
        context.assertValueEqual(player.messages.size(), 1, "R2: rejected uses do not send successful-toggle feedback");
        player.getCooldowns().tick();
        context.assertFalse(player.getCooldowns().isOnCooldown(ResonanceMod.TOKEN), "R2: cooldown ends at tick 20");
        use(player, stack, InteractionHand.MAIN_HAND);
        context.assertFalse(active(stack), "R2: tick-20 use is permitted");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void feedbackUsesActionbarForBothStates(GameTestHelper context) {
        RecordingPlayer player = new RecordingPlayer(context.getLevel());
        ItemStack stack = new ItemStack(ResonanceMod.TOKEN);
        use(player, stack, InteractionHand.MAIN_HAND);
        context.assertValueEqual(player.messages.get(0), "Resonance: active", "R3: active feedback text");
        context.assertTrue(player.overlays.get(0), "R3: active feedback targets the actionbar");
        expire(player);
        use(player, stack, InteractionHand.MAIN_HAND);
        context.assertValueEqual(player.messages.get(1), "Resonance: inactive", "R3: inactive feedback text");
        context.assertTrue(player.overlays.get(1), "R3: inactive feedback targets the actionbar");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unrelatedStackDataSurvivesBothDirections(GameTestHelper context) {
        RecordingPlayer player = new RecordingPlayer(context.getLevel());
        ItemStack stack = decoratedStack();
        use(player, stack, InteractionHand.MAIN_HAND);
        checkDecoration(context, stack);
        expire(player);
        use(player, stack, InteractionHand.MAIN_HAND);
        checkDecoration(context, stack);
        context.assertFalse(active(stack), "R1: second allowed use restores inactive state");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void activeAndInactiveSurviveItemCodecRoundTrip(GameTestHelper context) {
        RecordingPlayer player = new RecordingPlayer(context.getLevel());
        ItemStack original = decoratedStack();
        use(player, original, InteractionHand.MAIN_HAND);
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, context.getLevel().registryAccess());
        ItemStack restoredActive = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, original).getOrThrow()).getOrThrow();
        context.assertTrue(restoredActive != original, "R5: deserialization creates a separate stack");
        context.assertTrue(active(restoredActive), "R5: saved active state survives codec read");
        checkDecoration(context, restoredActive);
        expire(player);
        use(player, restoredActive, InteractionHand.MAIN_HAND);
        ItemStack restoredInactive = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, restoredActive).getOrThrow()).getOrThrow();
        context.assertFalse(active(restoredInactive), "R5: saved inactive state survives codec read");
        checkDecoration(context, restoredInactive);
        context.assertTrue(active(original), "R4/R5: modifying restored stack does not mutate original");
        context.succeed();
    }

    private static boolean active(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("resonance_token:active");
    }

    private static ItemStack decoratedStack() {
        ItemStack stack = new ItemStack(ResonanceMod.TOKEN);
        CompoundTag unrelated = new CompoundTag();
        unrelated.putString("other_mod:owner", "keep-me");
        unrelated.putInt("other_mod:score", 37);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Independent keepsake"));
        return stack;
    }

    private static void checkDecoration(GameTestHelper context, ItemStack stack) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        context.assertValueEqual(nbt.getString("other_mod:owner"), "keep-me", "R4: unrelated string field survives");
        context.assertValueEqual(nbt.getInt("other_mod:score"), 37, "R4: unrelated integer field survives");
        context.assertValueEqual(stack.getHoverName().getString(), "Independent keepsake", "R4: separate custom-name component survives");
    }

    private static void use(RecordingPlayer player, ItemStack stack, InteractionHand hand) {
        player.setItemInHand(hand, stack);
        stack.use(player.level(), player, hand);
    }

    private static void expire(RecordingPlayer player) {
        for (int tick = 0; tick < 20; tick++) player.getCooldowns().tick();
    }

    private static final class RecordingPlayer extends Player {
        final List<String> messages = new ArrayList<>();
        final List<Boolean> overlays = new ArrayList<>();
        RecordingPlayer(ServerLevel world) { super(world, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "TokenTester")); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public void displayClientMessage(Component message, boolean overlay) { messages.add(message.getString()); overlays.add(overlay); }
    }
}
