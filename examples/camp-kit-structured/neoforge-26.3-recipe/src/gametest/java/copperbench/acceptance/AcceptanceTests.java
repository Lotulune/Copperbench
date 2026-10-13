package copperbench.acceptance;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/** Independent expectations against the packaged JAR; no generated implementation classes are imported. */
@net.neoforged.fml.common.Mod("copperbench_test_host")
public final class AcceptanceTests {
    public AcceptanceTests(net.neoforged.bus.api.IEventBus bus) {
        var functions = net.neoforged.neoforge.registries.DeferredRegister.<java.util.function.Consumer<GameTestHelper>>create(
                Registries.TEST_FUNCTION, "copperbench_camp_kit");
        functions.register("t03", () -> this::t03);
        functions.register("t04", () -> this::t04);
        functions.register("t07", () -> this::t07);
        functions.register("t08", () -> this::t08);
        functions.register("t09", () -> this::t09);
        functions.register(bus);
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("copperbench_camp_kit", path); }
    private static Item kit() { return BuiltInRegistries.ITEM.getValue(id("actual_trail_kit")); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static CraftingInput shapedInput() {
        return CraftingInput.of(1, 2, List.of(new ItemStack(Items.STICK), new ItemStack(Items.STRING)));
    }
    private static RecipeHolder<CraftingRecipe> matching(GameTestHelper h, CraftingInput input, String name) {
        var holder = h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        require(holder.id().identifier().equals(id(name)), "the expected generated recipe is selected");
        return holder;
    }
    private static void result(ItemStack value, int count) {
        require(value.is(kit()) && value.getCount() == count, "recipe output must use the actual registered item and exact count " + count);
    }
    private static void write(Path file, String value) {
        try { Files.createDirectories(file.toAbsolutePath().getParent()); Files.writeString(file, value); }
        catch (java.io.IOException error) { throw new AssertionError("Cannot write controlled test data", error); }
    }

    private void t03(GameTestHelper h) {
        var shaped = shapedInput(); result(matching(h, shaped, "camp_shaped").value().assemble(shaped), 2);
        var shapeless = CraftingInput.of(2, 1, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.FLINT)));
        result(matching(h, shapeless, "camp_shapeless").value().assemble(shapeless), 3);
        h.succeed();
    }

    private void t04(GameTestHelper h) {
        for (var input : List.of(
                CraftingInput.of(1, 2, List.of(new ItemStack(Items.DIRT), new ItemStack(Items.STRING))),
                CraftingInput.of(2, 1, List.of(new ItemStack(Items.DIRT), new ItemStack(Items.FLINT)))))
            require(h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).isEmpty(),
                    "wrong shaped/shapeless ingredients must not match a recipe");
        var relative = new BlockPos(1, 1, 1); h.setBlock(relative, Blocks.FURNACE);
        var pos = h.absolutePos(relative);
        var furnace = (AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.DIRT)); furnace.setItem(1, new ItemStack(kit(), 2));
        for (int tick = 0; tick < 220; tick++)
            AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        require(furnace.getItem(0).is(Items.DIRT) && furnace.getItem(1).getCount() == 2 && furnace.getItem(2).isEmpty(),
                "a wrong furnace input consumes neither ingredients nor fuel and yields no output");
        h.succeed();
    }

    private void t07(GameTestHelper h) {
        cook(h, new BlockPos(1, 1, 1), Blocks.FURNACE, Items.STRING, "camp_smelting", 200, 2);
        h.succeed();
    }

    private void t08(GameTestHelper h) {
        cook(h, new BlockPos(1, 1, 1), Blocks.BLAST_FURNACE, Items.FLINT, "camp_blasting", 100, 1);
        cook(h, new BlockPos(2, 1, 2), Blocks.SMOKER, Items.PAPER, "camp_smoking", 100, 1);
        h.succeed();
    }

    private static void cook(GameTestHelper h, BlockPos relative, Block block, Item input, String recipe, int ticks, int count) {
        var loaded = h.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id(recipe))).orElseThrow();
        require(loaded.value() instanceof AbstractCookingRecipe, "the real recipe holder resolves a cooking recipe");
        require(((AbstractCookingRecipe) loaded.value()).cookingTime() == ticks, "cookingtime is preserved as ticks");
        h.setBlock(relative, block); BlockPos pos = h.absolutePos(relative);
        var furnace = (AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(input, 2)); furnace.setItem(1, new ItemStack(kit(), 2));
        AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        var initial = furnace.saveWithoutMetadata(h.getLevel().registryAccess());
        require(initial.getInt("cooking_total_time").orElseThrow() == ticks, "actual furnace duration follows the recipe");
        require(initial.getFloat("speed_multiplier").orElseThrow() == 1.0f, "the sample fuel's speed component is exactly one");
        require(initial.getInt("lit_total_time").orElseThrow() == 400, "one fuel supplies 400 ticks");
        require(furnace.getItem(1).getCount() == 1, "exactly one fuel is consumed at ignition");
        for (int tick = 1; tick < ticks - 1; tick++)
            AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        require(furnace.getItem(2).isEmpty() && furnace.getItem(0).getCount() == 2, "one tick before completion there is no output or input consumption");
        AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        result(furnace.getItem(2), count);
        require(furnace.getItem(0).getCount() == 1 && furnace.getItem(1).getCount() == 1,
                "completion consumes exactly one ingredient and does not consume another fuel");
        var complete = furnace.saveWithoutMetadata(h.getLevel().registryAccess());
        require(complete.getInt("lit_time_remaining").orElseThrow() == 401 - ticks, "actual remaining burn budget matches elapsed ticks");
        JsonObject receipt = new JsonObject(); receipt.addProperty("recipe", recipe); receipt.addProperty("ticks", ticks);
        receipt.addProperty("speedMultiplier", 1); receipt.addProperty("inputConsumed", 1); receipt.addProperty("fuelConsumed", 1);
        receipt.addProperty("outputCount", count); receipt.addProperty("burnRemaining", 401 - ticks);
        write(Path.of(recipe + "-cooking.json"), receipt.toString());
        System.out.println("COPPERBENCH_COOKING " + receipt);
    }

    private void t09(GameTestHelper h) {
        // Other tests finish their synchronous initial-data assertions before this reload changes a shared recipe.
        h.runAfterDelay(20, () -> {
            var input = shapedInput(); result(matching(h, input, "camp_shaped").value().assemble(input), 2);
            JsonObject modified = JsonParser.parseString("""
                    {"type":"minecraft:crafting_shaped","pattern":["S","T"],
                     "key":{"S":"minecraft:stick","T":"minecraft:string"},
                     "result":{"id":"copperbench_camp_kit:actual_trail_kit","count":4}}
                    """).getAsJsonObject();
            JsonObject invalid = modified.deepCopy(); invalid.getAsJsonObject("result").addProperty("id", "copperbench_camp_kit:missing_recipe_output");
            var decoded = Recipe.DIRECT_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), invalid);
            require(decoded.error().isPresent(), "the real registry-aware recipe codec must reject an unknown output");
            String error = decoded.error().orElseThrow().message();
            require(error.contains("missing_recipe_output"), "the original codec error identifies the missing output");
            JsonObject negative = new JsonObject(); negative.addProperty("status", "rejected"); negative.addProperty("message", error);
            write(Path.of("invalid-recipe-reference.json"), negative.toString());
            var server = h.getLevel().getServer();
            Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("copperbench_recipe_reload");
            write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Controlled recipe reload\",\"min_format\":[121,0],\"max_format\":[121,0]}}");
            write(pack.resolve("data/copperbench_camp_kit/recipe/camp_shaped.json"), modified.toString());
            var repository = server.getPackRepository(); repository.reload();
            Set<String> selected = new LinkedHashSet<>(repository.getSelectedIds());
            require(repository.getAvailableIds().contains("file/copperbench_recipe_reload"), "controlled recipe datapack is discoverable");
            selected.add("file/copperbench_recipe_reload");
            var reload = server.reloadResources(selected);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(reload.isDone(), "waiting for actual recipe reload"))
                    .thenExecute(() -> {
                        reload.join(); result(matching(h, input, "camp_shaped").value().assemble(input), 4);
                        System.out.println("COPPERBENCH_RECIPE_RELOAD_APPLIED count=4");
                    }).thenSucceed();
        });
    }
}
