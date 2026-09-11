package dev.wayfinder.acceptance;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** This host loads the mod JAR; it contains no implementation source for the tested mod. */
public final class BellGameTest implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 160)
    public void packagedBellBehavior(GameTestHelper helper) {
        Item bell = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("wayfinder_bell", "wayfinder_bell"));
        require(BuiltInRegistries.ITEM.getKey(bell).toString().equals("wayfinder_bell:wayfinder_bell"), "registered item missing");
        require(new ItemStack(bell).getMaxStackSize() == 1, "stack size");
        require(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("wayfinder_bell", "wayfinder_bell")).isPresent(), "recipe not loaded");
        Probe first = connect(helper, "first", false), second = connect(helper, "second", false), spectator = connect(helper, "spectator", true);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Probe p : new Probe[]{first, second, spectator}) {
            p.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(bell));
        }
        CompoundTag unrelated = new CompoundTag(); unrelated.putString("owner_note", "preserve me");
        first.getMainHandItem().set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        use(first, 0);
        require(first.key.equals("message.wayfinder_bell.empty"), "empty-anchor feedback");
        require(!first.getCooldowns().isOnCooldown(bell), "empty anchor starts cooldown");
        first.setShiftKeyDown(true); use(first, 1);
        require(first.key.equals("message.wayfinder_bell.saved"), "anchor save feedback");
        ItemStack saved = first.getMainHandItem().copy();
        require(saved.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("owner_note").equals("preserve me"), "saving destroyed unrelated custom data");
        ItemStack reloaded = ItemStack.parse(helper.getLevel().registryAccess(), saved.save(helper.getLevel().registryAccess())).orElseThrow();
        require(reloaded.get(DataComponents.CUSTOM_DATA).equals(saved.get(DataComponents.CUSTOM_DATA)), "anchor did not survive item serialization");
        CompoundTag anchor = saved.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("wayfinder_bell");
        require(anchor.getInt("x") == origin.getX() && anchor.getInt("y") == origin.getY() && anchor.getInt("z") == origin.getZ(), "saved coordinates");
        require(first.getCooldowns().isOnCooldown(bell), "accepted save has no cooldown");
        first.setPos(origin.getX() + 10.5, origin.getY() + 4, origin.getZ() + .5);
        use(first, 2);
        require(first.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).equals(saved.get(DataComponents.CUSTOM_DATA)), "cooldown did not reject repeat save");
        require(!second.getCooldowns().isOnCooldown(bell), "cooldown leaked to another player");
        second.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
        second.setPos(origin.getX() + 10.5, origin.getY() + 4, origin.getZ() + .5);
        use(second, 0);
        require(second.key.equals("message.wayfinder_bell.direction"), "copied anchor does not navigate");
        require(second.args.length == 3 && second.args[1].equals(10L) && second.args[2].equals(-4), "distance or height");
        require(second.args[0] instanceof Component c && c.getContents() instanceof TranslatableContents t && t.getKey().equals("direction.wayfinder_bell.west"), "wrong compass direction");
        spectator.setShiftKeyDown(true); use(spectator, 0);
        require(!spectator.getMainHandItem().has(DataComponents.CUSTOM_DATA), "spectator mutated anchor");
        require(!spectator.getCooldowns().isOnCooldown(bell), "spectator got cooldown");
        Probe otherDimension = connect(helper, "dimension", false);
        CompoundTag root = saved.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.getCompound("wayfinder_bell").putString("dimension", "minecraft:the_nether");
        ItemStack foreign = saved.copy(); foreign.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        otherDimension.setItemInHand(InteractionHand.MAIN_HAND, foreign); use(otherDimension, 0);
        require(otherDimension.key.equals("message.wayfinder_bell.dimension"), "cross-dimension feedback");
        require(!otherDimension.getCooldowns().isOnCooldown(bell), "cross-dimension rejection starts cooldown");
        helper.runAfterDelay(22, () -> {
            require(!first.getCooldowns().isOnCooldown(bell), "cooldown failed to expire naturally");
            first.setShiftKeyDown(false); use(first, 3);
            require(first.key.equals("message.wayfinder_bell.direction"), "post-cooldown use rejected");
            System.out.println("WAYFINDER_GAMEPLAY_VERIFIED registered=true recipe=true save=true item_serialization=true unrelated_data=true direction=true distance=true height=true cooldown=true spectator=true multiplayer=true cross_dimension=true");
            helper.succeed();
        });
    }

    private static Probe connect(GameTestHelper h, String name, boolean spectator) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "bell-" + name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        Probe player = new Probe(h, profile, cookie, spectator);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        h.getLevel().getServer().getConnection().getConnections().add(connection);
        return player;
    }
    private static void use(Probe p, int sequence) {
        p.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, p.getYRot(), p.getXRot()));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class Probe extends ServerPlayer {
        private final boolean spectator;
        private String key = "";
        private Object[] args = {};
        Probe(GameTestHelper h, GameProfile profile, CommonListenerCookie cookie, boolean spectator) {
            super(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
            this.spectator = spectator;
        }
        @Override public boolean isSpectator() { return spectator; }
        @Override public void displayClientMessage(Component component, boolean actionBar) {
            if (actionBar && component.getContents() instanceof TranslatableContents t) { key = t.getKey(); args = t.getArgs(); }
        }
    }
}
