package copperbench.acceptance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Ordinary dedicated-server checks: GameTestMainUtil deliberately deletes its universe on startup. */
public final class WorldReopenChecks {
    private static final BlockPos SAVED_POS = new BlockPos(8, 80, 8);
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            ServerLevel level = server.overworld();
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("copperbench_camp_kit", "camp_post"));
            var state = level.getBlockState(SAVED_POS);
            require(state.is(block) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z,
                    "T11: the dedicated server must read the saved Z-axis block without replacing it");
            drops(level, block, Items.STICK);
            Path table = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(
                    "copperbench_reload/data/copperbench_camp_kit/loot_table/blocks/camp_post.json");
            JsonObject loot = JsonParser.parseString(Files.readString(table)).getAsJsonObject();
            loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                    .get(0).getAsJsonObject().addProperty("name", "minecraft:paper");
            Files.writeString(table, loot.toString());
            var selected = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
            require(selected.contains("file/copperbench_reload"), "the saved datapack must be active after a normal reopen");
            server.reloadResources(selected).whenComplete((ignored, error) -> server.execute(() -> {
                try {
                    if (error != null) throw new AssertionError("Resource reload failed", error);
                    drops(level, block, Items.PAPER);
                    finish(server, null);
                } catch (Throwable failure) { finish(server, failure); }
            }));
        } catch (Throwable failure) { finish(server, failure); }
    }

    private static void drops(ServerLevel level, Block block, Item expected) {
        var drops = Block.getDrops(level.getBlockState(SAVED_POS), level, SAVED_POS, null);
        require(drops.size() == 1 && drops.getFirst().is(expected) && drops.getFirst().getCount() == 1,
                "T10: actual saved-world drop must be exactly " + expected);
    }

    private static void finish(MinecraftServer server, Throwable failure) {
        JsonObject receipt = new JsonObject();
        receipt.addProperty("status", failure == null ? "passed" : "failed");
        receipt.addProperty("phase", "reopened");
        receipt.addProperty("runner", "ordinary_dedicated_server");
        receipt.addProperty("world", server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
        receipt.addProperty("resourceId", "copperbench_camp_kit:camp_post");
        receipt.addProperty("axis", "z");
        receipt.addProperty("x", 8); receipt.addProperty("y", 80); receipt.addProperty("z", 8);
        receipt.addProperty("checks", "T11 saved Z-axis read; T10 saved stick drop and real reload to paper");
        if (failure != null) {
            receipt.addProperty("failure", failure.toString()); failure.printStackTrace();
        }
        try { Files.writeString(Path.of("block-persistence.json"), receipt.toString()); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot record world reopen", error); }
        finally { server.halt(false); }
        System.out.println("COPPERBENCH_BLOCK_REOPEN " + receipt);
    }
}
