package copperbench.acceptance;

/** Registers thirteen independent business cases against the same generated mod JAR. */
public final class AcceptanceTests {
    public AcceptanceTests() {
        new ItemAcceptance();
        new RecipeAcceptance();
        new BlockAcceptance();
        new CodeAcceptance();
        new FunctionAcceptance();
    }
}
