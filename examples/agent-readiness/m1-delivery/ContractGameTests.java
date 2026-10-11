package copperbench.acceptance;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.List;

/** Compiled only in the independent host; no tested-mod implementation source is on its source path. */
public final class ContractGameTests implements FabricGameTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("m1_delivery", path); }
    private static Item item() { return BuiltInRegistries.ITEM.get(id("discovery_item")); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void registeredStackLimit(GameTestHelper h) {
        require(BuiltInRegistries.ITEM.getKey(item()).equals(id("discovery_item")), "Declared item is registered");
        require(new ItemStack(item()).getMaxStackSize() == 16, "Contract stackSize must affect the packaged item");
        h.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void recipeProducesDeclaredItem(GameTestHelper h) {
        var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));
        var holder = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        require(holder.id().equals(id("discovery_recipe")), "Expected fixture recipe");
        var output = holder.value().assemble(input, h.getLevel().registryAccess());
        require(output.is(item()) && output.getCount() == 1, "Stick must craft exactly one fixture item");
        h.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void recipeRejectsWrongIngredient(GameTestHelper h) {
        var holder = h.getLevel().getRecipeManager().byKey(id("discovery_recipe")).orElseThrow();
        var recipe = (net.minecraft.world.item.crafting.CraftingRecipe) holder.value();
        require(!recipe.matches(CraftingInput.of(1, 1, List.of(new ItemStack(Items.DIRT))), h.getLevel()),
                "Recipe must reject dirt");
        h.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void itemStackSurvivesSerialization(GameTestHelper h) {
        ItemStack source = new ItemStack(item(), 12);
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), source.save(h.getLevel().registryAccess())).orElseThrow();
        require(restored.is(item()) && restored.getCount() == 12 && restored.getMaxStackSize() == 16,
                "Item/count/components must survive storage");
        h.succeed();
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void repairedManualCodeHasIndependentExpectedValues(GameTestHelper h) throws Exception {
        var method = Class.forName("net.mcreator.m1_delivery.recovery_probe").getMethod("bundles", int.class);
        int[] input = {0, 1, 15, 16, 17, 31, 32, 33};
        int[] expected = {0, 1, 1, 1, 2, 2, 2, 3};
        for (int i = 0; i < input.length; i++)
            require(method.invoke(null, input[i]).equals(expected[i]), "bundles(" + input[i] + ")");
        h.succeed();
    }
}
