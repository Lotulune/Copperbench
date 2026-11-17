package copperbench.eval;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * T1 evaluation. Uses only registry IDs and vanilla APIs, so it judges any implementation of the task text.
 * Compiled in an independent host; the evaluated JAR is loaded unchanged from run/mods.
 */
public final class EmberkitT1Tests implements FabricGameTest {
    private static final ResourceLocation BRICKS = ResourceLocation.fromNamespaceAndPath("emberkit", "ember_bricks");
    private static final ResourceLocation DUST = ResourceLocation.fromNamespaceAndPath("emberkit", "ember_dust");

    @FunctionalInterface private interface Body { void run() throws Exception; }

    /** Mod exceptions become test failures instead of escaping the test runner. */
    private static void check(GameTestHelper helper, Body body) {
        try {
            body.run();
        } catch (GameTestAssertException assertion) {
            throw assertion;
        } catch (Throwable failure) {
            helper.fail("Mod code threw " + failure);
        }
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private static Block bricks(GameTestHelper helper) {
        require(helper, BuiltInRegistries.BLOCK.containsKey(BRICKS), "Block emberkit:ember_bricks is not registered");
        return BuiltInRegistries.BLOCK.get(BRICKS);
    }

    private static Item dust(GameTestHelper helper) {
        require(helper, BuiltInRegistries.ITEM.containsKey(DUST), "Item emberkit:ember_dust is not registered");
        return BuiltInRegistries.ITEM.get(DUST);
    }

    private static boolean inTab(GameTestHelper helper, String tab, Item item) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess());
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace(tab));
        return BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key).contains(new ItemStack(item));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockRegistered(GameTestHelper helper) {
        check(helper, () -> bricks(helper));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockHardness(GameTestHelper helper) {
        check(helper, () -> {
            float hardness = bricks(helper).defaultBlockState().getDestroySpeed(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
            require(helper, hardness == 2.0f, "Hardness is " + hardness + ", expected 2.0");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockExplosionResistance(GameTestHelper helper) {
        check(helper, () -> {
            float resistance = bricks(helper).getExplosionResistance();
            require(helper, resistance == 6.0f, "Explosion resistance is " + resistance + ", expected 6.0");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void requiresPickaxe(GameTestHelper helper) {
        check(helper, () -> {
            BlockState state = bricks(helper).defaultBlockState();
            require(helper, state.requiresCorrectToolForDrops(), "The block drops without the correct tool");
            require(helper, state.is(BlockTags.MINEABLE_WITH_PICKAXE), "The block is not in minecraft:mineable/pickaxe");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anyPickaxeHarvests(GameTestHelper helper) {
        check(helper, () -> {
            BlockState state = bricks(helper).defaultBlockState();
            for (Item pickaxe : List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE,
                    Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE))
                require(helper, new ItemStack(pickaxe).isCorrectToolForDrops(state), BuiltInRegistries.ITEM.getKey(pickaxe) + " cannot harvest it");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void otherToolsCannotHarvest(GameTestHelper helper) {
        check(helper, () -> {
            BlockState state = bricks(helper).defaultBlockState();
            require(helper, state.requiresCorrectToolForDrops() && !ItemStack.EMPTY.isCorrectToolForDrops(state), "A bare hand harvests it");
            for (Item tool : List.of(Items.WOODEN_SHOVEL, Items.WOODEN_AXE, Items.DIAMOND_SHOVEL))
                require(helper, !new ItemStack(tool).isCorrectToolForDrops(state), BuiltInRegistries.ITEM.getKey(tool) + " harvests it");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pickaxeDropsItself(GameTestHelper helper) {
        check(helper, () -> {
            Block block = bricks(helper);
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                    null, null, new ItemStack(Items.WOODEN_PICKAXE));
            require(helper, drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                    "A wooden pickaxe drops " + drops + ", expected one Ember Bricks");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockItemRegistered(GameTestHelper helper) {
        check(helper, () -> {
            Item item = bricks(helper).asItem();
            require(helper, item instanceof BlockItem && BuiltInRegistries.ITEM.getKey(item).equals(BRICKS),
                    "Block item is " + BuiltInRegistries.ITEM.getKey(item));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockItemInBuildingBlocks(GameTestHelper helper) {
        check(helper, () -> require(helper, inTab(helper, "building_blocks", bricks(helper).asItem()),
                "Ember Bricks is not in the Building Blocks tab"));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dustRegistered(GameTestHelper helper) {
        check(helper, () -> dust(helper));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dustStacksTo64(GameTestHelper helper) {
        check(helper, () -> {
            int size = new ItemStack(dust(helper)).getMaxStackSize();
            require(helper, size == 64, "Ember Dust stacks to " + size + ", expected 64");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dustInIngredients(GameTestHelper helper) {
        check(helper, () -> require(helper, inTab(helper, "ingredients", dust(helper)), "Ember Dust is not in the Ingredients tab"));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeIsShaped(GameTestHelper helper) {
        check(helper, () -> {
            var recipe = helper.getLevel().getRecipeManager().byKey(BRICKS);
            require(helper, recipe.isPresent(), "Recipe emberkit:ember_bricks is missing");
            require(helper, recipe.get().value() instanceof ShapedRecipe,
                    "Recipe emberkit:ember_bricks is a " + recipe.get().value().getClass().getSimpleName());
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeProducesFour(GameTestHelper helper) {
        check(helper, () -> {
            ItemStack s = new ItemStack(Items.STONE_BRICKS), d = new ItemStack(dust(helper)), e = ItemStack.EMPTY;
            CraftingInput input = CraftingInput.of(3, 3, List.of(e, s, e, s, d, s, e, s, e));
            var level = helper.getLevel();
            var match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            require(helper, match.isPresent() && match.get().id().equals(BRICKS),
                    "The pattern matches " + match.map(holder -> holder.id().toString()).orElse("no recipe"));
            ItemStack output = match.get().value().assemble(input, level.registryAccess());
            require(helper, output.is(bricks(helper).asItem()) && output.getCount() == 4, "The pattern produces " + output);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeNeedsDust(GameTestHelper helper) {
        check(helper, () -> {
            ItemStack s = new ItemStack(Items.STONE_BRICKS), e = ItemStack.EMPTY;
            CraftingInput input = CraftingInput.of(3, 3, List.of(e, s, e, s, e, s, e, s, e));
            var level = helper.getLevel();
            var match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            require(helper, match.isEmpty() || !match.get().id().equals(BRICKS), "Stone bricks alone craft Ember Bricks");
        });
    }
}
