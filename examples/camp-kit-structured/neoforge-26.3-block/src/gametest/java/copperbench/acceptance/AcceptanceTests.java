package copperbench.acceptance;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootTable;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Independent runtime expectations for the generated block, native data reload, and saved-world reopen. */
@net.neoforged.fml.common.Mod("copperbench_test_host")
public final class AcceptanceTests {
    private static final BlockPos SAVED_POS = new BlockPos(8, 80, 8);
    public AcceptanceTests(net.neoforged.bus.api.IEventBus bus) {
        var functions = net.neoforged.neoforge.registries.DeferredRegister.<java.util.function.Consumer<GameTestHelper>>create(
                net.minecraft.core.registries.Registries.TEST_FUNCTION, "copperbench_camp_kit");
        functions.register("t10", () -> this::t10);
        functions.register("t11", () -> this::t11);
        functions.register(bus);
        if (Boolean.getBoolean("copperbench.worldReopen"))
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WorldReopenChecks::onServerStarted);
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("copperbench_camp_kit", path); }
    private static Block block() { return BuiltInRegistries.BLOCK.getValue(id("camp_post")); }
    private static boolean reopening() { return Files.isRegularFile(Path.of("copperbench-reopen-mode")); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void write(Path file, String value) {
        try { Files.createDirectories(file.toAbsolutePath().getParent()); Files.writeString(file, value); }
        catch (IOException error) { throw new AssertionError("Cannot write controlled test data", error); }
    }
    private static JsonObject loot(String item) {
        return JsonParser.parseString("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\""+item+"\"}]}]}").getAsJsonObject();
    }
    private static void drops(GameTestHelper h, Item expected) {
        var values = Block.getDrops(block().defaultBlockState(), h.getLevel(), h.absolutePos(new BlockPos(2,1,2)), null);
        require(values.size() == 1 && values.getFirst().is(expected) && values.getFirst().getCount() == 1,
                "actual block drops must contain exactly the expected referenced item: " + expected);
    }

    private void t10(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 1, 2), block());
        drops(h, reopening() ? Items.STICK : block().asItem());
        // The game's real registry-aware codec must reject an unknown item, not quietly turn it into air.
        var invalid = LootTable.DIRECT_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                loot("copperbench_camp_kit:missing_drop"));
        require(invalid.error().isPresent(), "unknown loot item reference must fail the Minecraft codec");
        String error = invalid.error().orElseThrow().message();
        require(error.contains("missing_drop"), "negative receipt must identify the missing item");
        JsonObject negative = new JsonObject(); negative.addProperty("status", "rejected"); negative.addProperty("message", error);
        write(Path.of("invalid-loot-reference.json"), negative.toString());
        System.out.println("COPPERBENCH_INVALID_LOOT_REFERENCE_REJECTED " + error);

        var server = h.getLevel().getServer();
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("copperbench_reload");
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Controlled block reload\",\"min_format\":[121,0],\"max_format\":[121,0]}}");
        Item expected = reopening() ? Items.PAPER : Items.STICK;
        write(pack.resolve("data/copperbench_camp_kit/loot_table/blocks/camp_post.json"),
                loot(reopening() ? "minecraft:paper" : "minecraft:stick").toString());
        var repository = server.getPackRepository(); repository.reload();
        Set<String> selected = new LinkedHashSet<>(repository.getSelectedIds());
        require(repository.getAvailableIds().contains("file/copperbench_reload"), "controlled datapack is discoverable");
        selected.add("file/copperbench_reload");
        var reload = server.reloadResources(selected);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(reload.isDone(), "waiting for actual resource reload"))
                .thenExecute(() -> {
                    reload.join(); drops(h, expected);
                    System.out.println("COPPERBENCH_LOOT_RELOAD_APPLIED " + BuiltInRegistries.ITEM.getKey(expected));
                }).thenSucceed();
    }

    private void t11(GameTestHelper h) {
        require(BuiltInRegistries.BLOCK.getKey(block()).equals(id("camp_post")), "generated block is registered");
        var item = new ItemStack(block().asItem());
        require(item.getMaxStackSize() == 16 && item.getRarity() == Rarity.UNCOMMON, "generated block item properties");
        if (!reopening()) {
            var relative = new BlockPos(1, 1, 1);
            h.placeBlock(relative, block(), Direction.DOWN);
            h.assertBlockPresent(block(), relative);
            require(h.getBlockState(relative).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y, "placed pillar axis");
            h.setBlock(relative, h.getBlockState(relative).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            require(h.getBlockState(relative).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "changed world state");
            h.getLevel().getChunk(SAVED_POS);
            h.getLevel().setBlock(SAVED_POS, block().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), 3);
            require(h.getLevel().getServer().saveEverything(false, true, true), "world save must complete");
        } else {
            // No placement or state write occurs in this branch; the second server reads copied saved-world bytes.
            var restored = h.getLevel().getBlockState(SAVED_POS);
            require(restored.is(block()) && restored.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z,
                    "a new server process must load the saved Z-axis block");
        }
        JsonObject receipt = new JsonObject(); receipt.addProperty("status", "passed");
        receipt.addProperty("phase", reopening() ? "reopened" : "saved");
        receipt.addProperty("world", h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
        receipt.addProperty("resourceId", "copperbench_camp_kit:camp_post"); receipt.addProperty("axis", "z");
        receipt.addProperty("x", 8); receipt.addProperty("y", 80); receipt.addProperty("z", 8);
        write(Path.of("block-persistence.json"), receipt.toString());
        System.out.println("COPPERBENCH_BLOCK_PERSISTENCE " + receipt);
        h.succeed();
    }
}
