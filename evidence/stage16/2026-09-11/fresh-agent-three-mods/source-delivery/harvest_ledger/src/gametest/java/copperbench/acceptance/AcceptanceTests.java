package copperbench.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import trial.harvest.LedgerState;

public final class AcceptanceTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void matureSuccessfulBreakCreditsExactlyOnce(GameTestHelper context) {
        ServerPlayer player = player(context, GameType.SURVIVAL);
        BlockPos pos = wheat(context, 1, 7);
        context.assertValueEqual(total(context, player), 0L, "H1/H3: new player begins with zero total");
        context.assertTrue(player.gameMode.destroyBlock(pos), "H1: actual server block break succeeds");
        context.assertTrue(context.getLevel().getBlockState(pos).isAir(), "H1: mature crop is really removed");
        context.assertValueEqual(total(context, player), 1L, "H1: successful mature break awards exactly one");
        player.gameMode.destroyBlock(pos);
        context.assertValueEqual(total(context, player), 1L, "H1/H2: retry against now-empty position cannot award again");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void immatureWheatAndOtherCropsDoNotCredit(GameTestHelper context) {
        ServerPlayer player = player(context, GameType.SURVIVAL);
        for (int age = 0; age < 7; age++) {
            BlockPos pos = wheat(context, 1, age);
            context.assertTrue(player.gameMode.destroyBlock(pos), "H2: immature crop really breaks");
            context.assertValueEqual(total(context, player), 0L, "H2: no credit for wheat age " + age);
        }
        BlockPos other = context.absolutePos(new BlockPos(2, 1, 1));
        context.getLevel().setBlock(other.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        context.getLevel().setBlock(other, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7), Block.UPDATE_ALL);
        context.assertTrue(player.gameMode.destroyBlock(other), "H2: mature non-wheat crop really breaks");
        context.assertValueEqual(total(context, player), 0L, "H2: mature carrot is excluded");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeMatureBreakIsExcluded(GameTestHelper context) {
        ServerPlayer player = player(context, GameType.CREATIVE);
        BlockPos pos = wheat(context, 1, 7);
        context.assertTrue(player.gameMode.destroyBlock(pos), "H2: creative break really succeeds");
        context.assertTrue(context.getLevel().getBlockState(pos).isAir(), "H2: creative crop is actually removed");
        context.assertValueEqual(total(context, player), 0L, "H2: creative mature break earns no credit");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cancelledBreakDoesNotCreditAndLaterSuccessDoes(GameTestHelper context) {
        ServerPlayer player = player(context, GameType.SURVIVAL);
        BlockPos pos = wheat(context, 1, 7);
        AtomicBoolean cancelled = new AtomicBoolean(true);
        PlayerBlockBreakEvents.BEFORE.register((world, breaker, target, state, entity) ->
                breaker != player || !target.equals(pos) || !cancelled.get());
        context.assertFalse(player.gameMode.destroyBlock(pos), "H2: another event listener cancels the real break");
        context.assertTrue(context.getLevel().getBlockState(pos).is(Blocks.WHEAT), "H2: cancelled crop remains in the world");
        context.assertValueEqual(total(context, player), 0L, "H2: cancelled break must not credit");
        cancelled.set(false);
        context.assertTrue(player.gameMode.destroyBlock(pos), "H1: same crop can later break successfully");
        context.assertValueEqual(total(context, player), 1L, "H1/H2: only the later successful break credits");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void independentPlayersAccumulateOwnTotals(GameTestHelper context) {
        ServerPlayer first = player(context, GameType.SURVIVAL);
        ServerPlayer second = player(context, GameType.SURVIVAL);
        context.assertFalse(first.getUUID().equals(second.getUUID()), "H3: test uses distinct player identities");
        for (int i = 0; i < 3; i++) {
            context.assertTrue(first.gameMode.destroyBlock(wheat(context, 1, 7)), "H3: first player harvest succeeds");
        }
        context.assertTrue(second.gameMode.destroyBlock(wheat(context, 2, 7)), "H3: second player harvest succeeds");
        context.assertValueEqual(total(context, first), 3L, "H3: first player accumulates three");
        context.assertValueEqual(total(context, second), 1L, "H3: second player's independent total is one");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void persistentStateLoadsActualWorldDataFile(GameTestHelper context) throws Exception {
        ServerPlayer first = player(context, GameType.SURVIVAL);
        ServerPlayer second = player(context, GameType.SURVIVAL);
        for (int i = 0; i < 2; i++) {
            context.assertTrue(first.gameMode.destroyBlock(wheat(context, 1, 7)), "H4: first player's actual harvest succeeds");
        }
        context.assertTrue(second.gameMode.destroyBlock(wheat(context, 2, 7)), "H4: second player's actual harvest succeeds");
        ServerLevel overworld = context.getLevel().getServer().overworld();
        LedgerState live = LedgerState.forWorld(overworld);
        overworld.getDataStorage().save();
        Path data = overworld.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        context.assertTrue(Files.size(data.resolve(LedgerState.SAVE_ID + ".dat")) > 0, "H4: vanilla state manager writes the actual world data file");
        DimensionDataStorage reloadedManager = new DimensionDataStorage(data.toFile(),
                overworld.getServer().getFixerUpper(), overworld.registryAccess());
        LedgerState reloaded = reloadedManager.get(LedgerState.TYPE, LedgerState.SAVE_ID);
        context.assertTrue(reloaded != null && reloaded != live, "H4: a fresh state manager reads a distinct object from disk");
        context.assertValueEqual(reloaded.total(first.getUUID()), 2L, "H4: first player's saved total is restored");
        context.assertValueEqual(reloaded.total(second.getUUID()), 1L, "H4: second player's saved total is restored");
        context.assertValueEqual(reloaded.total(UUID.randomUUID()), 0L, "H3/H4: unknown player still starts at zero after read");
        context.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void reportCommandReturnsTotalWithoutMutatingIt(GameTestHelper context) throws Exception {
        ServerPlayer player = player(context, GameType.SURVIVAL);
        for (int i = 0; i < 2; i++) {
            context.assertTrue(player.gameMode.destroyBlock(wheat(context, 1, 7)), "H5: actual harvest prepares a nonzero total");
        }
        int result = context.getLevel().getServer().getCommands().getDispatcher()
               .execute("harvestledger", player.createCommandSourceStack());
        context.assertValueEqual(result, 2, "H5: real registered command reports this player's total");
        context.assertValueEqual(total(context, player), 2L, "H5: reporting does not change the ledger");
        context.succeed();
    }

    private static ServerPlayer player(GameTestHelper context, GameType mode) {
        return RealTestPlayers.create(context, mode);
    }

    private static long total(GameTestHelper context, ServerPlayer player) {
        return LedgerState.forWorld(context.getLevel()).total(player.getUUID());
    }

    private static BlockPos wheat(GameTestHelper context, int x, int age) {
        BlockPos pos = context.absolutePos(new BlockPos(x, 1, 1));
        context.getLevel().setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        BlockState crop = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age);
        context.getLevel().setBlock(pos, crop, Block.UPDATE_ALL);
        return pos;
    }
}
