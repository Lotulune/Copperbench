package copperbench.acceptance;

/** Registers thirteen independent business cases against the same generated mod JAR. */
@net.neoforged.fml.common.Mod("copperbench_test_host")
public final class AcceptanceTests {
    public AcceptanceTests(net.neoforged.bus.api.IEventBus bus) {
        new ItemAcceptance(bus);
        new RecipeAcceptance(bus);
        new BlockAcceptance(bus);
        new CodeAcceptance(bus);
        new FunctionAcceptance(bus);
    }
}
