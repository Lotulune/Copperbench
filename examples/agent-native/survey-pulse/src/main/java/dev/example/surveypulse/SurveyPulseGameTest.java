package dev.example.surveypulse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Runtime-only gameplay verification for the packaged Survey Pulse example. */
public final class SurveyPulseGameTest implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 140)
    public void rightClickSneakCooldownPermissionAndPlayerIsolation(GameTestHelper helper) {
        List<Observation> observations = new ArrayList<>();
        SurveyPulseMod.setStage14CObserver((player, radius, result) -> observations.add(new Observation(
                player.getUUID(), radius, result.count(), result.nearest().orElse(null))));

        try {
            BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
            BlockPos nearOre = origin.offset(4, 0, 0);
            BlockPos farOre = origin.offset(7, 0, 0);
            helper.getLevel().setBlock(nearOre, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
            helper.getLevel().setBlock(farOre, Blocks.GOLD_ORE.defaultBlockState(), 3);

            ServerPlayer first = makeConnectedServerPlayer(helper, "stage14c-first", false);
            first.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SurveyPulseMod.SURVEY_WAND));
            first.setShiftKeyDown(false);

            sendUseItem(first, 0);
            require(observations.size() == 1, "ordinary right-click did not execute exactly one scan");
            Observation ordinary = observations.get(0);
            require(ordinary.playerId().equals(first.getUUID()), "ordinary scan was attributed to the wrong player");
            require(ordinary.radius() == 5, "ordinary right-click did not select radius 5");
            require(ordinary.nearest() != null && ordinary.nearest().x() == nearOre.getX()
                    && ordinary.nearest().y() == nearOre.getY() && ordinary.nearest().z() == nearOre.getZ(),
                    "ordinary radius 5 did not find the controlled near ore");
            require(first.getCooldowns().isOnCooldown(SurveyPulseMod.SURVEY_WAND),
                    "accepted right-click did not start the 60-tick cooldown");

            // A second packet during the same cooldown window must be rejected before scanning.
            sendUseItem(first, 1);
            require(observations.size() == 1, "cooldown-protected repeat right-click executed another scan");

            ServerPlayer second = makeConnectedServerPlayer(helper, "stage14c-second", false);
            second.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SurveyPulseMod.SURVEY_WAND));
            require(!second.getCooldowns().isOnCooldown(SurveyPulseMod.SURVEY_WAND),
                    "second player inherited first player's cooldown");
            second.setShiftKeyDown(true);
            sendUseItem(second, 0);
            require(observations.size() == 2, "second player's sneak right-click did not scan");
            Observation sneaking = observations.get(1);
            require(sneaking.playerId().equals(second.getUUID()), "sneak scan was attributed to the wrong player");
            require(sneaking.radius() == 8, "sneak right-click did not select radius 8");
            require(sneaking.count() >= ordinary.count() + 1,
                    "radius 8 did not include the controlled far ore outside radius 5");
            require(second.getCooldowns().isOnCooldown(SurveyPulseMod.SURVEY_WAND),
                    "second player's accepted use did not start its own cooldown");
            require(first.getCooldowns().isOnCooldown(SurveyPulseMod.SURVEY_WAND),
                    "second player's use mutated first player's cooldown state");

            ServerPlayer spectator = makeConnectedServerPlayer(helper, "stage14c-spectator", true);
            spectator.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            spectator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SurveyPulseMod.SURVEY_WAND));
            require(spectator.isSpectator(), "spectator fixture did not report spectator mode");
            sendUseItem(spectator, 0);
            require(observations.size() == 2, "spectator right-click was not rejected before scanning");
            require(!spectator.getCooldowns().isOnCooldown(SurveyPulseMod.SURVEY_WAND),
                    "spectator rejection incorrectly started a cooldown");

            helper.runAfterDelay(SurveyPulseMod.COOLDOWN_TICKS + 2L, () -> {
                try {
                    require(!first.getCooldowns().isOnCooldown(SurveyPulseMod.SURVEY_WAND),
                            "first player's cooldown did not naturally expire after 60 server ticks");
                    first.setShiftKeyDown(true);
                    sendUseItem(first, 2);
                    require(observations.size() == 3, "post-cooldown right-click did not execute another scan");
                    Observation afterCooldown = observations.get(2);
                    require(afterCooldown.playerId().equals(first.getUUID()),
                            "post-cooldown scan was attributed to the wrong player");
                    require(afterCooldown.radius() == 8, "post-cooldown sneak use did not select radius 8");
                    require(first.getCooldowns().isOnCooldown(SurveyPulseMod.SURVEY_WAND),
                            "post-cooldown accepted use did not restart cooldown");
                    System.out.println("SURVEY_PULSE_GAMEPLAY_VERIFIED right_click=true sneak_radius=true cooldown=true"
                            + " spectator_rejected=true multiplayer_isolation=true observations=" + observations.size());
                    SurveyPulseMod.clearStage14CObserver();
                    helper.succeed();
                } catch (RuntimeException failure) {
                    SurveyPulseMod.clearStage14CObserver();
                    throw failure;
                }
            });
        } catch (RuntimeException failure) {
            SurveyPulseMod.clearStage14CObserver();
            throw failure;
        }
    }

    private static ServerPlayer makeConnectedServerPlayer(GameTestHelper helper, String name, boolean spectator) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = spectator
                ? new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation()) {
                    @Override
                    public boolean isSpectator() {
                        return true;
                    }
                }
                : new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // GameTestHelper's own mock connection is intentionally not registered with the server network listener.
        // Register ours so the normal ServerConnectionListener tick drives listener.tick -> player.doTick,
        // including the vanilla ItemCooldowns tick path used by real connected players.
        helper.getLevel().getServer().getConnection().getConnections().add(connection);
        return player;
    }

    private static void sendUseItem(ServerPlayer player, int sequence) {
        player.connection.handleUseItem(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, sequence, player.getYRot(), player.getXRot()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Observation(UUID playerId, int radius, int count, ScanResult.Point nearest) {}
}
