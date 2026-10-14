package copperbench.acceptance;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/** Independent expectations against the final packaged manual classes and their helper bundle. */
public final class AcceptanceTests {
    public AcceptanceTests() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, Identifier.fromNamespaceAndPath("copperbench_camp_kit", "t12"), this::t12);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void t12(GameTestHelper h) {
        try {
            Class<?> type = Class.forName("dev.copperbench.campkit.recovery_probe");
            var bundles = type.getMethod("bundles", int.class);
            int[] inputs = {0, 1, 15, 16, 17, 31, 32, 33, Integer.MAX_VALUE};
            int[] expected = {0, 1, 1, 1, 2, 2, 2, 3, 134217728};
            JsonArray cases = new JsonArray();
            for (int index = 0; index < inputs.length; index++) {
                Object actual = bundles.invoke(null, inputs[index]);
                require(actual.equals(expected[index]), "independent bundles(" + inputs[index] + ") expectation");
                JsonObject row = new JsonObject(); row.addProperty("input", inputs[index]); row.addProperty("actual", (Integer) actual); cases.add(row);
            }
            try { bundles.invoke(null, -1); throw new AssertionError("negative input must be rejected"); }
            catch (java.lang.reflect.InvocationTargetException error) {
                require(error.getCause() instanceof IllegalArgumentException, "negative input reports its deliberate boundary error");
            }
            require(type.getMethod("externalStamp").invoke(null).equals(41), "the independently edited helper survived metadata edits, reopen and regeneration");
            JsonObject receipt = new JsonObject(); receipt.add("cases", cases); receipt.addProperty("externalStamp", 41);
            receipt.addProperty("negativeInputRejected", true);
            Files.writeString(Path.of("code-effects.json"), receipt.toString());
            System.out.println("COPPERBENCH_CODE_EFFECTS " + receipt);
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new AssertionError("Cannot verify the packaged manual code bundle", error);
        }
        h.succeed();
    }
}
