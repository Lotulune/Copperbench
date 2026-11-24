package copperbench.datapack;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Runs in the Fabric 1.21.1 GameTest host after the Copperbench-built ZIP was copied, unchanged, into the host
 * world's datapacks folder. Every case reads the pack through the running server, not through the ZIP file.
 */
public final class DataPackLoadTests implements FabricGameTest {
    private static final String PACK_ID = "file/copperbench_dp.zip";
    private static final String NAMESPACE = "cbdp";

    @GameTest(template = EMPTY_STRUCTURE)
    public void packIsDiscoveredAndEnabled(GameTestHelper helper) {
        var packs = helper.getLevel().getServer().getPackRepository();
        if (!packs.getAvailableIds().contains(PACK_ID)) helper.fail("Pack not discovered; available: " + packs.getAvailableIds());
        if (!packs.getSelectedIds().contains(PACK_ID)) helper.fail("Pack not enabled; selected: " + packs.getSelectedIds());
        helper.succeed();
    }

    /** A hand-written function written through Core runs and changes the world. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void functionFromThePackRuns(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var function = server.getFunctions().get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "mark"));
        if (function.isEmpty()) helper.fail("Function cbdp:mark is not loaded");
        BlockPos target = new BlockPos(1, 2, 1);
        var source = server.createCommandSourceStack().withLevel(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(target))).withPermission(2).withSuppressedOutput();
        server.getFunctions().execute(function.get(), source);
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, target);
        helper.succeed();
    }

    /** A hand-written recipe JSON written through Core is registered with its result. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeFromThePackIsRegistered(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var recipe = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "stick_to_nuggets"));
        if (recipe.isEmpty()) helper.fail("Recipe cbdp:stick_to_nuggets is not loaded");
        ItemStack result = recipe.get().value().getResultItem(server.registryAccess());
        if (!result.is(Items.GOLD_NUGGET) || result.getCount() != 2) helper.fail("Unexpected recipe result " + result);
        helper.succeed();
    }

    /** A tag added to the workspace registry is rendered by the generator and loaded. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void generatedTagFromThePackIsLoaded(GameTestHelper helper) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "probe_items"));
        if (!Items.STICK.builtInRegistryHolder().is(tag)) helper.fail("Item tag cbdp:probe_items does not contain minecraft:stick");
        helper.succeed();
    }
}
