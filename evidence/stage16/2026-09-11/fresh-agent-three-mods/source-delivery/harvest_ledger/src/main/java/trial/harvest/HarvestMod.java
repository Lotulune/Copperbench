package trial.harvest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

public final class HarvestMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (world instanceof ServerLevel serverWorld && !player.isCreative() && state.is(Blocks.WHEAT)
                    && state.getValue(CropBlock.AGE) == ((CropBlock) Blocks.WHEAT).getMaxAge()) {
                LedgerState.forWorld(serverWorld).credit(player.getUUID());
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(Commands.literal("harvestledger").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                long total = LedgerState.forWorld(player.serverLevel()).total(player.getUUID());
                context.getSource().sendSuccess(() -> Component.literal("Mature wheat harvested: " + total), false);
                return (int) Math.min(Integer.MAX_VALUE, total);
            })));
    }
}
