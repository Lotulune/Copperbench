package dev.example.surveypulse;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class SurveyPulseMod implements ModInitializer {
    public static final String MOD_ID = "survey_pulse";
    public static final int COOLDOWN_TICKS = 60;
    public static final Item SURVEY_WAND = new Item(new Item.Properties().stacksTo(1));

    @FunctionalInterface
    interface Stage14CObserver {
        void onScan(net.minecraft.world.entity.player.Player player, int radius, ScanResult result);
    }

    private static volatile Stage14CObserver stage14CObserver;

    static void setStage14CObserver(Stage14CObserver observer) {
        stage14CObserver = observer;
    }

    static void clearStage14CObserver() {
        stage14CObserver = null;
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "survey_wand"), SURVEY_WAND);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(SURVEY_WAND)) return InteractionResultHolder.pass(held);
            if (player.isSpectator() || player.getCooldowns().isOnCooldown(SURVEY_WAND)) {
                return InteractionResultHolder.fail(held);
            }
            if (level instanceof ServerLevel serverLevel) {
                BlockPos at = player.blockPosition();
                int radius = player.isShiftKeyDown() ? 8 : 5;
                ScanResult result = OreScanner.scan(point -> {
                    BlockPos position = new BlockPos(point.x(), point.y(), point.z());
                    if (!serverLevel.hasChunkAt(position)) return null;
                    return BuiltInRegistries.BLOCK.getKey(serverLevel.getBlockState(position).getBlock()).getPath();
                }, new ScanResult.Point(at.getX(), at.getY(), at.getZ()), radius);
                player.displayClientMessage(Component.literal(result.message(radius)), true);
                Stage14CObserver observer = stage14CObserver;
                if (observer != null) observer.onScan(player, radius, result);
                player.getCooldowns().addCooldown(SURVEY_WAND, COOLDOWN_TICKS);
                result.nearest().ifPresent(point -> serverLevel.sendParticles(ParticleTypes.END_ROD,
                        point.x() + 0.5, point.y() + 1.0, point.z() + 0.5,
                        12, 0.25, 0.35, 0.25, 0.01));
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
        });
        if ("1".equals(System.getenv("SURVEY_PULSE_STAGE14C_BEHAVIOR_PROBE"))) {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                ServerLevel level = server.overworld();
                BlockPos sharedSpawn = level.getSharedSpawnPos();
                BlockPos origin = new BlockPos(sharedSpawn.getX(), level.getMaxBuildHeight() - 20, sharedSpawn.getZ());
                BlockPos nearOre = origin.offset(4, 0, 0);
                BlockPos farOre = origin.offset(7, 0, 0);
                BlockState originalNear = level.getBlockState(nearOre);
                BlockState originalFar = level.getBlockState(farOre);
                try {
                    level.setBlock(nearOre, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
                    level.setBlock(farOre, Blocks.GOLD_ORE.defaultBlockState(), 3);
                    OreScanner.BlockReader reader = point -> {
                        BlockPos position = new BlockPos(point.x(), point.y(), point.z());
                        if (!level.hasChunkAt(position)) return null;
                        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).getPath();
                    };
                    ScanResult radius5 = OreScanner.scan(reader,
                            new ScanResult.Point(origin.getX(), origin.getY(), origin.getZ()), 5);
                    ScanResult radius8 = OreScanner.scan(reader,
                            new ScanResult.Point(origin.getX(), origin.getY(), origin.getZ()), 8);
                    boolean nearVisible = radius5.nearest().map(point -> point.x() == nearOre.getX()
                            && point.y() == nearOre.getY() && point.z() == nearOre.getZ()).orElse(false);
                    if (!nearVisible || radius8.count() < radius5.count() + 1) {
                        throw new IllegalStateException("Stage14C world behavior probe failed: radius5="
                                + radius5.count() + ", radius8=" + radius8.count() + ", nearest=" + radius5.nearest());
                    }
                    System.out.println("SURVEY_PULSE_BEHAVIOR_VERIFIED radius5=" + radius5.count()
                            + " radius8=" + radius8.count() + " nearest=4,0,0");
                } finally {
                    level.setBlock(nearOre, originalNear, 3);
                    level.setBlock(farOre, originalFar, 3);
                }
                server.halt(false);
            });
        }
        System.out.println("SURVEY_PULSE_INITIALIZED");
    }
}
