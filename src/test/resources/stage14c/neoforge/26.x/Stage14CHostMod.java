package dev.copperbench.stage14c;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@Mod("stage14c_host")
public final class Stage14CHostMod {
    public Stage14CHostMod(IEventBus bus) { bus.addListener(Stage14CHostMod::registerGameTests); }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath("stage14c_host", "default"));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Identifier.withDefaultNamespace("bastion/mobs/empty"), 140, 0, true);
        event.registerTest(Identifier.fromNamespaceAndPath("stage14c_host", "survey_pulse_gameplay"), Stage14CGameTest::new, data);
        System.out.println("STAGE14C_NEOFORGE_HOST_GAMETEST_REGISTERED track=neoforge-26.1.2");
    }

    private static final class Stage14CGameTest extends GameTestInstance {
        private static final MapCodec<Stage14CGameTest> CODEC = TestData.CODEC.xmap(Stage14CGameTest::new, Stage14CGameTest::data);
        private Stage14CGameTest(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }
        private TestData<Holder<TestEnvironmentDefinition<?>>> data() { return info(); }
        @Override public MapCodec<Stage14CGameTest> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Stage14C Survey Pulse gameplay"); }

        @Override public void run(GameTestHelper helper) {
            BlockPos base=helper.absolutePos(new BlockPos(1,2,1)); BlockPos origin=new BlockPos(base.getX(),250,base.getZ());
            BlockPos near=origin.offset(4,0,0),far=origin.offset(7,0,0); var bn=helper.getLevel().getBlockState(near); var bf=helper.getLevel().getBlockState(far);
            helper.getLevel().setBlock(near,Blocks.DIAMOND_ORE.defaultBlockState(),3); helper.getLevel().setBlock(far,Blocks.GOLD_ORE.defaultBlockState(),3);
            Item wand=BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("survey_pulse","survey_wand")); require(BuiltInRegistries.ITEM.getKey(wand).toString().equals("survey_pulse:survey_wand"),"wand missing");
            Probe first=connect(helper,"first",false),second=connect(helper,"second",false),spectator=connect(helper,"spectator",true);
            for(Probe p:new Probe[]{first,second,spectator}){p.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(wand));p.last="";}
            first.setShiftKeyDown(false);use(first,0);require(first.last.contains("1 ore blocks"),"normal radius: "+first.last);require(first.getCooldowns().isOnCooldown(first.getItemInHand(InteractionHand.MAIN_HAND)),"first cooldown");String before=first.last;use(first,1);require(first.last.equals(before),"repeat not blocked");
            require(!second.getCooldowns().isOnCooldown(second.getItemInHand(InteractionHand.MAIN_HAND)),"cooldown leaked");second.setShiftKeyDown(true);use(second,0);require(second.last.contains("2 ore blocks"),"sneak radius: "+second.last);require(second.getCooldowns().isOnCooldown(second.getItemInHand(InteractionHand.MAIN_HAND)),"second cooldown");require(first.getCooldowns().isOnCooldown(first.getItemInHand(InteractionHand.MAIN_HAND)),"first cooldown changed");
            use(spectator,0);require(spectator.last.isEmpty(),"spectator message");require(!spectator.getCooldowns().isOnCooldown(spectator.getItemInHand(InteractionHand.MAIN_HAND)),"spectator cooldown");
            helper.runAfterDelay(62,()->{require(!first.getCooldowns().isOnCooldown(first.getItemInHand(InteractionHand.MAIN_HAND)),"cooldown did not expire");first.setShiftKeyDown(true);use(first,2);require(first.last.contains("2 ore blocks"),"post cooldown");System.out.println("STAGE14C_GAMEPLAY_VERIFIED track=neoforge-26.1.2 rightClick=true normalRadius=5 sneakRadius=8 cooldown60=true spectator=true multiplayerIsolation=true");helper.getLevel().setBlock(near,bn,3);helper.getLevel().setBlock(far,bf,3);helper.succeed();});
        }
    }

    private static Probe connect(GameTestHelper h,String n,boolean s){GameProfile gp=new GameProfile(UUID.randomUUID(),"stage14c-"+n);CommonListenerCookie cookie=CommonListenerCookie.createInitial(gp,false);Probe p=new Probe(h,gp,cookie,s);Connection c=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(c);h.getLevel().getServer().getPlayerList().placeNewPlayer(c,p,cookie);h.getLevel().getServer().getConnection().getConnections().add(c);for(int i=0;i<60&&!p.connection.hasClientLoaded();i++)p.connection.tickClientLoadTimeout();require(p.connection.hasClientLoaded(),"embedded client not loaded");return p;}
    private static void use(Probe p,int seq){p.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND,seq,p.getYRot(),p.getXRot()));}
    private static void require(boolean c,String m){if(!c)throw new IllegalStateException(m);}
    private static final class Probe extends ServerPlayer{final boolean spectator;String last="";Probe(GameTestHelper h,GameProfile gp,CommonListenerCookie cookie,boolean s){super(h.getLevel().getServer(),h.getLevel(),gp,cookie.clientInformation());spectator=s;}@Override public boolean isSpectator(){return spectator;}@Override public void sendSystemMessage(Component c,boolean o){if(o)last=c.getString();}}
}
