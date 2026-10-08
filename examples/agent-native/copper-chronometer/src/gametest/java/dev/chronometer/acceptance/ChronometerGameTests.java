package dev.chronometer.acceptance;

import com.mojang.authlib.GameProfile;
import dev.chronometer.ChronometerItem;
import dev.chronometer.ClockReading;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Behavioral acceptance against the mod JAR loaded by Copperbench's independent
 * GameTest host. No implementation classes are copied into this test source set.
 *
 * The connected-player fixture is adapted from the repository's Wayfinder Bell
 * BellGameTest. The lightweight player/cooldown fixture follows the archived
 * Stage 16 Resonance Token acceptance test. Assertions and chronometer scenarios
 * are new. Tests do not change shared world time or global locale.
 */
public final class ChronometerGameTests implements FabricGameTest {
    private static final ResourceLocation ITEM_ID = id("chronometer");

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void registeredItemAndRecipeLoadFromPackagedJar(GameTestHelper helper) {
        Item item = chronometer();
        require(item instanceof ChronometerItem, "registered item must execute ChronometerItem behavior");
        require(new ItemStack(item).getMaxStackSize() == 1, "chronometer must be unstackable");
        require(helper.getLevel().getRecipeManager().byKey(ITEM_ID).isPresent(),
                "copper_chronometer:chronometer recipe must load from the packaged mod");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void preferencesBelongToIndividualItemStacks(GameTestHelper helper) {
        ItemStack first = new ItemStack(chronometer());
        ItemStack second = new ItemStack(chronometer());
        require(!ChronometerItem.isTwelveHour(first), "fresh item must default to 24-hour format");
        ChronometerItem.setTwelveHour(first, true);
        require(ChronometerItem.isTwelveHour(first), "requested 12-hour preference was not stored");
        require(!ChronometerItem.isTwelveHour(second), "preference leaked to an unrelated item stack");
        ItemStack copy = first.copy();
        ChronometerItem.setTwelveHour(copy, false);
        require(ChronometerItem.isTwelveHour(first), "updating a copied stack mutated the original");
        require(!ChronometerItem.isTwelveHour(copy), "copy did not accept its independent preference");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void preferenceUpdatesPreserveUnrelatedNbtAndComponents(GameTestHelper helper) {
        ItemStack stack = decoratedStack();
        ChronometerItem.setTwelveHour(stack, true);
        decorationSurvives(stack);
        require(customTag(stack).getCompound("copper_chronometer").getBoolean("twelve_hour"),
                "12-hour preference must be stored under copper_chronometer.twelve_hour");
        ChronometerItem.setTwelveHour(stack, false);
        decorationSurvives(stack);
        require(!ChronometerItem.isTwelveHour(stack), "false preference was not applied");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bothPreferencesSurviveItemSerialization(GameTestHelper helper) {
        ItemStack original = decoratedStack();
        ChronometerItem.setTwelveHour(original, true);
        ItemStack twelveHour = roundTrip(helper, original);
        require(twelveHour != original && ChronometerItem.isTwelveHour(twelveHour),
                "serialized 12-hour preference did not survive reload into a separate item");
        decorationSurvives(twelveHour);
        ChronometerItem.setTwelveHour(twelveHour, false);
        ItemStack twentyFourHour = roundTrip(helper, twelveHour);
        require(!ChronometerItem.isTwelveHour(twentyFourHour), "24-hour preference did not survive reload");
        decorationSurvives(twentyFourHour);
        require(ChronometerItem.isTwelveHour(original), "reloaded item still shares mutable data with original");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ordinaryUseReportsActualWorldTimeWithoutWritingPreference(GameTestHelper helper) {
        RecordingPlayer player = player(helper, false);
        ItemStack stack = decoratedStack();
        CompoundTag before = customTag(stack);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ClockReading expected = ClockReading.at(helper.getLevel().getDayTime());
        use(player, InteractionHand.MAIN_HAND);
        readingMessage(player.feedback, expected, false);
        require(customTag(stack).equals(before), "ordinary time reading must not modify item preference or unrelated NBT");
        require(player.getCooldowns().isOnCooldown(chronometer()), "successful reading did not start a cooldown");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sneakingUseTogglesModeAndChangesSubsequentReadout(GameTestHelper helper) {
        RecordingPlayer player = player(helper, false);
        ItemStack stack = decoratedStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(true);
        use(player, InteractionHand.MAIN_HAND);
        require(ChronometerItem.isTwelveHour(stack), "first sneaking use must enable 12-hour mode");
        require(modeMessage(player.feedback).equals("format.copper_chronometer.twelve_hour"),
                "12-hour toggle returned the wrong translated format label");
        decorationSurvives(stack);
        expireCooldown(player);
        player.setShiftKeyDown(false);
        ClockReading expected = ClockReading.at(helper.getLevel().getDayTime());
        use(player, InteractionHand.MAIN_HAND);
        readingMessage(player.feedback, expected, true);
        expireCooldown(player);
        player.setShiftKeyDown(true);
        use(player, InteractionHand.MAIN_HAND);
        require(!ChronometerItem.isTwelveHour(stack), "second permitted sneaking use must restore 24-hour mode");
        require(modeMessage(player.feedback).equals("format.copper_chronometer.twenty_four_hour"),
                "24-hour toggle returned the wrong translated format label");
        decorationSurvives(stack);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void itemItselfRejectsCooldownUntilExactlyTwentyTicks(GameTestHelper helper) {
        RecordingPlayer player = player(helper, false);
        ItemStack stack = new ItemStack(chronometer());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(true);
        use(player, InteractionHand.MAIN_HAND);
        require(ChronometerItem.isTwelveHour(stack), "initial toggle failed");
        require(player.feedback.count == 1, "successful toggle should emit one actionbar message");
        use(player, InteractionHand.MAIN_HAND);
        require(ChronometerItem.isTwelveHour(stack), "immediate repeat bypassed item's cooldown guard");
        require(player.feedback.count == 1, "cooldown rejection emitted success feedback");
        for (int tick = 0; tick < 19; tick++) player.getCooldowns().tick();
        require(player.getCooldowns().isOnCooldown(chronometer()), "cooldown expired before 20 ticks");
        use(player, InteractionHand.MAIN_HAND);
        require(ChronometerItem.isTwelveHour(stack) && player.feedback.count == 1,
                "item use at tick 19 must remain rejected");
        player.getCooldowns().tick();
        require(!player.getCooldowns().isOnCooldown(chronometer()), "cooldown did not expire at tick 20");
        use(player, InteractionHand.MAIN_HAND);
        require(!ChronometerItem.isTwelveHour(stack) && player.feedback.count == 2,
                "first allowed post-cooldown use did not toggle exactly once");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void spectatorsCannotReadOrMutateTheChronometer(GameTestHelper helper) {
        RecordingPlayer spectator = player(helper, true);
        ItemStack stack = decoratedStack();
        CompoundTag before = customTag(stack);
        spectator.setItemInHand(InteractionHand.MAIN_HAND, stack);
        spectator.setShiftKeyDown(true);
        use(spectator, InteractionHand.MAIN_HAND);
        spectator.setShiftKeyDown(false);
        use(spectator, InteractionHand.MAIN_HAND);
        require(customTag(stack).equals(before), "spectator use mutated custom item data");
        require(!ChronometerItem.isTwelveHour(stack), "spectator changed display mode");
        require(!spectator.getCooldowns().isOnCooldown(chronometer()), "rejected spectator use started cooldown");
        require(spectator.feedback.count == 0, "spectator use emitted successful feedback");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void offhandUseOnlyTogglesTheOffhandStack(GameTestHelper helper) {
        RecordingPlayer player = player(helper, false);
        ItemStack main = new ItemStack(chronometer());
        ItemStack off = decoratedStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        player.setShiftKeyDown(true);
        use(player, InteractionHand.OFF_HAND);
        require(ChronometerItem.isTwelveHour(off), "offhand stack was not toggled");
        require(!ChronometerItem.isTwelveHour(main), "offhand use toggled main-hand state");
        require(!main.has(DataComponents.CUSTOM_DATA), "offhand use wrote main-hand NBT");
        decorationSurvives(off);
        require(modeMessage(player.feedback).equals("format.copper_chronometer.twelve_hour"),
                "offhand toggle returned the wrong translated format label");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void playersHaveIndependentPreferencesAndCooldowns(GameTestHelper helper) {
        RecordingPlayer first = player(helper, false);
        RecordingPlayer second = player(helper, false);
        ItemStack firstStack = new ItemStack(chronometer());
        ItemStack secondStack = new ItemStack(chronometer());
        first.setItemInHand(InteractionHand.MAIN_HAND, firstStack);
        second.setItemInHand(InteractionHand.MAIN_HAND, secondStack);
        first.setShiftKeyDown(true);
        use(first, InteractionHand.MAIN_HAND);
        require(first.getCooldowns().isOnCooldown(chronometer()), "first player's accepted use needs cooldown");
        require(!second.getCooldowns().isOnCooldown(chronometer()), "cooldown leaked to another player");
        ClockReading expected = ClockReading.at(helper.getLevel().getDayTime());
        use(second, InteractionHand.MAIN_HAND);
        readingMessage(second.feedback, expected, false);
        require(ChronometerItem.isTwelveHour(firstStack) && !ChronometerItem.isTwelveHour(secondStack),
                "players' independently held items share display mode");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void netherReadingIsRejectedWithoutCooldownOrMutation(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        require(nether != null, "acceptance host must load the Nether for dimension rejection coverage");
        RecordingPlayer player = new RecordingPlayer(nether, false);
        ItemStack stack = decoratedStack();
        CompoundTag before = customTag(stack);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        use(player, InteractionHand.MAIN_HAND);
        require(player.feedback.key.equals("message.copper_chronometer.dimension"),
                "Nether time reading must return the dimension-specific rejection");
        require(player.feedback.actionBar, "dimension rejection must be visible in the actionbar");
        require(!player.getCooldowns().isOnCooldown(chronometer()), "dimension rejection starts a cooldown");
        require(customTag(stack).equals(before), "dimension rejection mutated item custom data");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
    public void networkUseAndNaturalServerTicksCompleteTheCooldown(GameTestHelper helper) {
        ConnectedPlayer player = connect(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(chronometer()));
        ClockReading expected = ClockReading.at(helper.getLevel().getDayTime());
        packetUse(player, 0);
        readingMessage(player.feedback, expected, false);
        require(player.getCooldowns().isOnCooldown(chronometer()), "packet-driven reading did not start cooldown");
        int messageCount = player.feedback.count;
        packetUse(player, 1);
        require(player.feedback.count == messageCount, "immediate use packet bypassed cooldown");
        helper.runAfterDelay(22, () -> {
            require(!player.getCooldowns().isOnCooldown(chronometer()), "server ticks did not expire the cooldown naturally");
            ClockReading after = ClockReading.at(player.level().getDayTime());
            packetUse(player, 2);
            readingMessage(player.feedback, after, false);
            require(player.feedback.count == messageCount + 1, "first packet after expiry must read once");
            require(player.getCooldowns().isOnCooldown(chronometer()), "accepted follow-up packet did not restart cooldown");
            helper.succeed();
        });
    }

    private static Item chronometer() {
        Item item = BuiltInRegistries.ITEM.get(ITEM_ID);
        require(BuiltInRegistries.ITEM.getKey(item).equals(ITEM_ID), "packaged chronometer item is missing");
        return item;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("copper_chronometer", path);
    }

    private static ItemStack decoratedStack() {
        ItemStack stack = new ItemStack(chronometer());
        CompoundTag root = new CompoundTag();
        root.putString("other_mod:owner", "preserve-me");
        root.putInt("other_mod:score", 37);
        CompoundTag ownNamespace = new CompoundTag();
        ownNamespace.putString("user_note", "keep-this-too");
        root.put("copper_chronometer", ownNamespace);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Traveler's timepiece"));
        return stack;
    }

    private static CompoundTag customTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void decorationSurvives(ItemStack stack) {
        CompoundTag root = customTag(stack);
        require(root.getString("other_mod:owner").equals("preserve-me"), "unrelated custom-data string was lost");
        require(root.getInt("other_mod:score") == 37, "unrelated custom-data integer was lost");
        require(root.getCompound("copper_chronometer").getString("user_note").equals("keep-this-too"),
                "unrelated sibling in the chronometer namespace was lost");
        require(stack.getHoverName().getString().equals("Traveler's timepiece"), "separate custom-name component was lost");
    }

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack stack) {
        return ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess()))
                .orElseThrow(() -> new AssertionError("item failed to deserialize"));
    }

    private static RecordingPlayer player(GameTestHelper helper, boolean spectator) {
        require(helper.getLevel().dimension().equals(Level.OVERWORLD), "time-reading acceptance requires an Overworld test host");
        return new RecordingPlayer(helper.getLevel(), spectator);
    }

    private static void use(RecordingPlayer player, InteractionHand hand) {
        player.getItemInHand(hand).getItem().use(player.level(), player, hand);
    }

    private static void expireCooldown(Player player) {
        for (int tick = 0; tick < 20; tick++) player.getCooldowns().tick();
    }

    private static void readingMessage(Feedback feedback, ClockReading expected, boolean twelveHour) {
        require(feedback.key.equals("message.copper_chronometer.reading") && feedback.actionBar,
                "expected an actionbar time reading, got " + feedback.key);
        require(feedback.args.length == 4, "time reading must contain day, time, phase, and ticks to next phase");
        require(feedback.args[0].equals(expected.day()), "time-reading message contains the wrong day number or numeric type");
        require(feedback.args[1].equals(expected.format(twelveHour)), "time-reading message contains the wrong formatted time");
        require(feedback.args[2] instanceof Component, "phase label must be a translatable component");
        Component phase = (Component) feedback.args[2];
        require(phase.getContents() instanceof TranslatableContents, "phase label is not translatable");
        String phaseKey = ((TranslatableContents) phase.getContents()).getKey();
        require(phaseKey.equals("phase.copper_chronometer." + expected.phase()),
                "time-reading message contains the wrong translated phase: " + phaseKey);
        require(feedback.args[3].equals(expected.ticksToNextPhase()), "time-reading message has the wrong boundary countdown or numeric type");
    }

    private static String modeMessage(Feedback feedback) {
        require(feedback.key.equals("message.copper_chronometer.mode") && feedback.actionBar,
                "expected an actionbar mode confirmation, got " + feedback.key);
        require(feedback.args.length == 1 && feedback.args[0] instanceof Component,
                "mode confirmation must contain one translatable format label");
        Component format = (Component) feedback.args[0];
        require(format.getContents() instanceof TranslatableContents, "mode label is not translatable");
        return ((TranslatableContents) format.getContents()).getKey();
    }

    private static ConnectedPlayer connect(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "chrono-network");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ConnectedPlayer player = new ConnectedPlayer(helper, profile, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        helper.getLevel().getServer().getConnection().getConnections().add(connection);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        return player;
    }

    private static void packetUse(ConnectedPlayer player, int sequence) {
        player.connection.handleUseItem(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, sequence, player.getYRot(), player.getXRot()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Feedback {
        private int count;
        private String key = "";
        private Object[] args = {};
        private boolean actionBar;

        private void record(Component component, boolean overlay) {
            count++;
            actionBar = overlay;
            key = "";
            args = new Object[0];
            if (component.getContents() instanceof TranslatableContents translated) {
                key = translated.getKey();
                args = translated.getArgs();
            }
        }
    }

    private static final class RecordingPlayer extends Player {
        private final boolean spectator;
        private final Feedback feedback = new Feedback();

        private RecordingPlayer(ServerLevel level, boolean spectator) {
            super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "ChronoTester"));
            this.spectator = spectator;
        }

        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean isCreative() { return false; }
        @Override public void displayClientMessage(Component component, boolean actionBar) {
            feedback.record(component, actionBar);
        }
    }

    private static final class ConnectedPlayer extends ServerPlayer {
        private final Feedback feedback = new Feedback();

        private ConnectedPlayer(GameTestHelper helper, GameProfile profile, CommonListenerCookie cookie) {
            super(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        }

        @Override public boolean isSpectator() { return false; }
        @Override public void displayClientMessage(Component component, boolean actionBar) {
            feedback.record(component, actionBar);
        }
    }
}
