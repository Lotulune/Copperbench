package copperbench.acceptance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.RotatedPillarBlock;

import java.util.List;

/** Runs only in the independent host. Expectations do not read the mod's implementation constants. */
public final class AcceptanceTests {
    public AcceptanceTests() {
        // Fabric instantiates this public test entrypoint before registries freeze. Native data files
        // select these functions with the fixed manifest IDs, independent of annotation naming rules.
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t01"), this::t01);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t02"), this::t02);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t03"), this::t03);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t04"), this::t04);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t11"), this::t11);
        Registry.register(BuiltInRegistries.TEST_FUNCTION, id("t12"), this::t12);
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("copperbench_camp_kit", path); }
    private static Item item() { return BuiltInRegistries.ITEM.getValue(id("trail_kit")); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private void t01(GameTestHelper h) {
        require(BuiltInRegistries.ITEM.getKey(item()).equals(id("trail_kit")), "trail_kit must be registered");
        require(new ItemStack(item()).getMaxStackSize() == 16, "trail_kit must stack to exactly 16");
        h.succeed();
    }

    private void t02(GameTestHelper h) {
        var original = new ItemStack(item(), 12);
        require(original.get(DataComponents.CUSTOM_NAME).getString().equals("Copper Camp Kit"), "native default component");
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, original).getOrThrow();
        var restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        require(restored.is(item()) && restored.getCount() == 12 && restored.getMaxStackSize() == 16, "round-trip item/count/limit");
        require(restored.get(DataComponents.CUSTOM_NAME).getString().equals("Copper Camp Kit"), "round-trip custom name");
        h.succeed();
    }

    private void t03(GameTestHelper h) {
        var input = CraftingInput.of(1, 2, List.of(new ItemStack(Items.STICK), new ItemStack(Items.STRING)));
        var holder = h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        require(holder.id().identifier().equals(id("trail_kit")), "correct shaped recipe selected");
        var result = holder.value().assemble(input);
        require(result.is(item()) && result.getCount() == 2, "stick and string must produce exactly two kits");
        h.succeed();
    }

    private void t04(GameTestHelper h) {
        var input = CraftingInput.of(1, 2, List.of(new ItemStack(Items.DIRT), new ItemStack(Items.STRING)));
        var holder = h.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id("trail_kit"))).orElseThrow();
        require(!((CraftingRecipe) holder.value()).matches(input, h.getLevel()), "dirt must not replace the stick");
        require(h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).isEmpty(), "wrong input has no crafting result");
        h.succeed();
    }

    private void t11(GameTestHelper h) {
        var block = BuiltInRegistries.BLOCK.getValue(id("camp_post"));
        require(BuiltInRegistries.BLOCK.getKey(block).equals(id("camp_post")), "camp_post must be registered");
        var pos = new BlockPos(2, 2, 2);
        h.placeBlock(pos, block, Direction.DOWN);
        h.assertBlockPresent(block, pos);
        require(h.getBlockState(pos).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y, "placed post axis");
        h.setBlock(pos, h.getBlockState(pos).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        require(h.getBlockState(pos).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "changed block state in the world");
        h.succeed();
    }

    private void t12(GameTestHelper h) {
        try {
            var method = Class.forName("dev.copperbench.campkit.RecoveryProbe").getMethod("bundles", int.class);
            int[] input = {0, 1, 15, 16, 17, 31, 32, 33, Integer.MAX_VALUE};
            int[] expected = {0, 1, 1, 1, 2, 2, 2, 3, 134217728};
            for (int i = 0; i < input.length; i++)
                require(method.invoke(null, input[i]).equals(expected[i]), "bundles(" + input[i] + ")");
        } catch (ReflectiveOperationException error) { throw new AssertionError("packaged manual code", error); }
        h.succeed();
    }
}
