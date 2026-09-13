package trial.tally;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class TallyMod implements ModInitializer {
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 15);
    public static final Block STONE = new TallyStone(BlockBehaviour.Properties.of().strength(1.5f));
    public static final Item ITEM = new BlockItem(STONE, new Item.Properties());

    @Override
    public void onInitialize() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("tally_stone", "tally_stone");
        Registry.register(BuiltInRegistries.BLOCK, id, STONE);
        Registry.register(BuiltInRegistries.ITEM, id, ITEM);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(ITEM));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockState state = world.getBlockState(hit.getBlockPos());
            if (!player.isSpectator() && player.isShiftKeyDown() && state.is(STONE)) {
                return useStone(state, world, hit.getBlockPos(), player);
            }
            return InteractionResult.PASS;
        });
    }

    private static InteractionResult useStone(BlockState state, Level world, BlockPos pos, Player player) {
        if (!world.isClientSide) {
            int count = player.isShiftKeyDown() ? 0 : Math.min(15, state.getValue(COUNT) + 1);
            world.setBlock(pos, state.setValue(COUNT, count), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Tally: " + count), true);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    private static final class TallyStone extends Block {
        private TallyStone(Properties settings) {
            super(settings);
            registerDefaultState(getStateDefinition().any().setValue(COUNT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(COUNT); }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            return useStone(state, world, pos, player);
        }
    }
}
