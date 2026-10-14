package copperbench.acceptance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

/** Independent packaged-JAR expectations. No implementation constants or mod sources on the host classpath. */
public final class AcceptanceTests {
    public AcceptanceTests() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t01"), this::t01);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t02"), this::t02);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t05"), this::t05);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t06"), this::t06);
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("copperbench_camp_kit", path); }
    private static Item item() { return BuiltInRegistries.ITEM.getValue(id("trail_kit")); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private void t01(GameTestHelper h) {
        require(BuiltInRegistries.ITEM.getKey(item()).equals(id("trail_kit")), "generated trail_kit registration");
        require(new ItemStack(item()).getMaxStackSize() == 16, "generated item stacks to 16");
        h.succeed();
    }

    private void t02(GameTestHelper h) {
        try (var stream = getClass().getClassLoader().getResourceAsStream("assets/copperbench_camp_kit/lang/en_us.json")) {
            require(stream != null, "generated language resource is packaged in the tested JAR");
            var language = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            require(language.get("item.copperbench_camp_kit.trail_kit").getAsString().equals("Copper Camp Kit"), "generated display name");
        } catch (java.io.IOException error) { throw new AssertionError("packaged item language", error); }
        ItemStack original = new ItemStack(item(), 12);
        require(original.get(DataComponents.RARITY) == Rarity.RARE, "generated rarity component");
        require(original.has(DataComponents.DAMAGE_RESISTANT), "generated fire resistance component");
        require(original.has(DataComponents.COOKING_FUEL) && original.has(DataComponents.COMPOSTABLE), "generated behavior components");
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Saved Camp Kit"));
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, original).getOrThrow();
        ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        require(restored.is(item()) && restored.getCount() == 12 && restored.getMaxStackSize() == 16, "round-trip registry/count/stack");
        require(restored.get(DataComponents.CUSTOM_NAME).getString().equals("Saved Camp Kit"), "round-trip component patch");
        for (var type : java.util.List.of(DataComponents.RARITY, DataComponents.DAMAGE_RESISTANT,
                DataComponents.COOKING_FUEL, DataComponents.COMPOSTABLE))
            require(java.util.Objects.equals(original.get(type), restored.get(type)), "round-trip generated component " + type);
        h.succeed();
    }

    private void t05(GameTestHelper h) {
        BlockPos relative = new BlockPos(1, 1, 1), pos = h.absolutePos(relative);
        h.setBlock(relative, Blocks.FURNACE);
        var furnace = (FurnaceBlockEntity) h.getLevel().getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.SAND));
        furnace.setItem(1, new ItemStack(item(), 2));
        // Call the real block-entity ticker within one server turn for exact, reproducible tick counts.
        AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        require(furnace.getItem(1).getCount() == 1, "one generated fuel item is consumed on ignition");
        require(h.getBlockState(relative).getValue(AbstractFurnaceBlock.LIT), "furnace really becomes lit");
        var burning = furnace.saveWithoutMetadata(h.getLevel().registryAccess());
        require(burning.getInt("lit_total_time").orElseThrow() == 200, "declared fuel lasts 200 ticks");
        require(burning.getInt("lit_time_remaining").orElseThrow() == 200, "initial burn budget");
        require(burning.getFloat("speed_multiplier").orElseThrow() == 1.0f, "bounded fuel uses normal cooking speed");
        for (int tick = 0; tick < 199; tick++)
            AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        require(furnace.getItem(0).isEmpty() && furnace.getItem(2).is(Items.GLASS) && furnace.getItem(2).getCount() == 1,
                "real smelting consumes sand and produces glass");
        require(furnace.getItem(1).getCount() == 1, "no second fuel is consumed");
        AbstractFurnaceBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), furnace);
        require(!h.getBlockState(relative).getValue(AbstractFurnaceBlock.LIT), "fuel expires after its declared budget");
        h.succeed();
    }

    private void t06(GameTestHelper h) {
        BlockPos relative = new BlockPos(2, 1, 2), pos = h.absolutePos(relative);
        h.setBlock(relative, Blocks.COMPOSTER);
        ItemStack kits = new ItemStack(item(), 9);
        var state = h.getBlockState(relative);
        for (int layer = 1; layer <= 7; layer++) {
            state = ComposterBlock.insertItem(null, state, h.getLevel(), kits, pos);
            require(state.getValue(ComposterBlock.LEVEL) == layer, "constant component adds exactly one layer");
            require(h.getBlockState(relative).getValue(ComposterBlock.LEVEL) == layer, "layer is written to the world");
            require(kits.getCount() == 9 - layer, "one item is consumed per layer");
        }
        ComposterBlock.insertItem(null, state, h.getLevel(), kits, pos);
        require(kits.getCount() == 2, "full composter consumes no more items");
        h.succeed();
    }
}
