package copperbench.acceptance;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTest; import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
public final class AcceptanceTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void modLoads(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("tally_stone")) throw new IllegalStateException("Tested mod was not loaded");
        // Replace or extend this smoke check with assertions about your mod's behavior.
        helper.succeed();
    }
}
