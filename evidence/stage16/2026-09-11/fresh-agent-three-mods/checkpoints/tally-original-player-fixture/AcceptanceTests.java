package copperbench.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import trial.tally.TallyMod;

public final class AcceptanceTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void itemPlacesZeroCountBlock(GameTestHelper context) {
        ServerPlayer player = player(context);
        BlockPos support = context.absolutePos(new BlockPos(1, 0, 1));
        context.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack stack = new ItemStack(TallyMod.ITEM, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(support)));
        context.assertTrue(result.consumesAction(), "T1: actual BlockItem placement must be accepted");
        BlockState placed = context.getLevel().getBlockState(support.above());
        context.assertTrue(placed.is(TallyMod.STONE), "T1: item places its registered block");
        context.assertValueEqual(placed.getValue(TallyMod.COUNT), 0, "T1: freshly placed count is zero");
        context.assertValueEqual(stack.getCount(), 1, "T1: survival placement consumes one item");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void normalUseIncrementsAndSaturatesAtFifteen(GameTestHelper context) {
        ServerPlayer player = player(context);
        BlockPos pos = place(context, 1);
        click(context, player, pos, false);
        context.assertValueEqual(count(context, pos), 1, "T2: first normal use increments exactly once");
        for (int i = 0; i < 14; i++) click(context, player, pos, false);
        context.assertValueEqual(count(context, pos), 15, "T2: fifteen uses reach fifteen");
        for (int i = 0; i < 8; i++) click(context, player, pos, false);
        context.assertValueEqual(count(context, pos), 15, "T2: repeated use at cap cannot wrap or exceed fifteen");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sneakingResetsIncludingAlreadyZero(GameTestHelper context) {
        ServerPlayer player = player(context);
        BlockPos pos = place(context, 1);
        for (int i = 0; i < 9; i++) click(context, player, pos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        click(context, player, pos, true);
        context.assertValueEqual(count(context, pos), 0, "T3: sneak-use resets accumulated count");
        click(context, player, pos, true);
        context.assertValueEqual(count(context, pos), 0, "T3: resetting zero stays zero");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        click(context, player, pos, false);
        context.assertValueEqual(count(context, pos), 1, "T2/T3: normal use works after reset");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void positionsKeepIndependentCounts(GameTestHelper context) {
        ServerPlayer player = player(context);
        BlockPos first = place(context, 1);
        BlockPos second = place(context, 2);
        for (int i = 0; i < 4; i++) click(context, player, first, false);
        for (int i = 0; i < 7; i++) click(context, player, second, false);
        context.assertValueEqual(count(context, first), 4, "T4: first position retains its own count");
        context.assertValueEqual(count(context, second), 7, "T4: second position has an independent count");
        click(context, player, first, true);
        context.assertValueEqual(count(context, first), 0, "T3/T4: selected position resets");
        context.assertValueEqual(count(context, second), 7, "T4: other position is unaffected by reset");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void chunkDiskSerializationRestoresCount(GameTestHelper context) throws Exception {
        ServerLevel world = context.getLevel();
        ServerPlayer player = player(context);
        BlockPos pos = place(context, 1);
        for (int i = 0; i < 11; i++) click(context, player, pos, false);
        LevelChunk source = world.getChunkAt(pos);
        CompoundTag encoded = ChunkSerializer.write(world, source);
        Path file = world.getServer().getWorldPath(LevelResource.ROOT).resolve("tally-" + UUID.randomUUID() + ".nbt");
        NbtIo.writeCompressed(encoded, file);
        context.assertTrue(Files.size(file) > 0, "T5: chunk data was actually written to disk");
        click(context, player, pos, true);
        context.assertValueEqual(count(context, pos), 0, "T5: live state changes after the saved snapshot");
        CompoundTag disk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        ProtoChunk restored = ChunkSerializer.read(world, world.getPoiManager(),
                new RegionStorageInfo("tally-acceptance", world.dimension(), "chunk"), source.getPos(), disk);
        context.assertTrue(restored.getBlockState(pos).is(TallyMod.STONE), "T5: Minecraft chunk reader restores the custom block");
        context.assertValueEqual(restored.getBlockState(pos).getValue(TallyMod.COUNT), 11, "T5: disk read restores saved count, not current zero");
        context.succeed();
    }

    private static BlockPos place(GameTestHelper context, int x) {
        BlockPos pos = context.absolutePos(new BlockPos(x, 1, 1));
        context.getLevel().setBlock(pos, TallyMod.STONE.defaultBlockState(), Block.UPDATE_ALL);
        return pos;
    }

    private static int count(GameTestHelper context, BlockPos pos) { return context.getLevel().getBlockState(pos).getValue(TallyMod.COUNT); }

    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); }

    private static ServerPlayer player(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        return player;
    }

    private static void click(GameTestHelper context, ServerPlayer player, BlockPos pos, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        var result = player.gameMode.useItemOn(player, context.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit(pos));
        context.assertTrue(result.consumesAction(), "T2/T3: real server interaction accepts the click");
    }
}
