package copperbench.acceptance;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

final class RealTestPlayers {
    private RealTestPlayers() {}

    static ServerPlayer create(GameTestHelper context, GameType mode) {
        UUID uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(uuid, "Trial" + uuid.toString().substring(0, 8));
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(context.getLevel().getServer(), context.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        context.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        context.assertValueEqual(player.isCreative(), mode == GameType.CREATIVE, "Test fixture must preserve the real requested game mode");
        return player;
    }
}
