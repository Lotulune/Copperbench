package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.testing.McreatorTestRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static dev.copperbench.core.application.FieldContractProjection.editor;
import static dev.copperbench.core.application.FieldContractProjection.environment;
import static org.junit.jupiter.api.Assertions.*;

class FieldContractProjectionTest {
    private static final Set<String> COMPATIBILITY_TYPES = Set.of("block", "loottable", "function", "projectile",
            "achievement", "generic", "custom", "code", "procedure");

    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest @ValueSource(strings = {"fabric-1.20.1", "neoforge-1.20.1", "fabric-1.21.1", "neoforge-1.21.1",
            "fabric-26.1.2", "neoforge-26.1.2", "fabric-26.2", "neoforge-26.2"})
    void preservesCompleteContractsAndLegacyCapabilitiesOnEveryTrack(String generator) {
        JsonObject contracts = environment(generator);
        assertEquals(Set.of("block", "loottable", "function", "projectile", "achievement", "generic", "custom",
                "code", "procedure", "item", "recipe"), contracts.keySet());
        assertEquals(BlockFieldContract.capabilities(generator), contracts.get("block"));
        assertEquals(LootTableFieldContract.capabilities(), contracts.get("loottable"));
        assertEquals(FunctionFieldContract.capabilities(), contracts.get("function"));
        assertEquals(SpecializedFieldContract.capabilities("projectile"), contracts.get("projectile"));
        assertEquals(SpecializedFieldContract.capabilities("achievement"), contracts.get("achievement"));
        assertEquals(GenericFieldInputContract.capabilities(), contracts.get("generic"));
        assertEquals(CustomFieldInputContract.capabilities(), contracts.get("custom"));
        assertEquals(CodeFieldContract.capabilities(), contracts.get("code"));
        assertEquals(ProcedureFieldContract.capabilities(), contracts.get("procedure"));
        for (String type : List.of("item", "recipe")) {
            assertTrue(contracts.getAsJsonObject(type).get("complete").getAsBoolean());
            assertEquals(ElementFieldContract.discover(type, generator), contracts.get(type));
        }
        for (String type : List.of("block", "item", "recipe"))
            assertEquals(contracts.get(type), editor(type, generator));
        assertEquals("not_exposed", ElementFieldContract.discover("block", generator).get("availability").getAsString(),
                "Legacy block capabilities must not be replaced with the per-type discovery envelope");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = "missing-generator")
    void omitsIncompleteContractsFromEnvironmentButKeepsEditorReason(String generator) {
        JsonObject contracts = environment(generator);
        assertEquals(COMPATIBILITY_TYPES, contracts.keySet());
        assertFalse(contracts.getAsJsonObject("block").get("supported").getAsBoolean());
        for (String type : List.of("item", "recipe")) {
            JsonObject contract = editor(type, generator);
            assertFalse(contract.get("complete").getAsBoolean());
            assertEquals("GENERATOR_NOT_LOADED", contract.get("reasonCode").getAsString());
            assertEquals(ElementFieldContract.discover(type, generator), contract);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"loottable", "function", "projectile", "achievement", "procedure", "code", "beblock"})
    void doesNotAddContractsToOtherEditors(String type) {
        assertNull(editor(type, "fabric-1.21.1"));
    }

    @Test void returnedContractsAreIndependentBetweenCallsAndConsumers() {
        JsonObject first = environment("fabric-1.21.1"), expected = first.deepCopy();
        first.getAsJsonObject("block").getAsJsonArray("fields").asList().clear();
        first.getAsJsonObject("item").getAsJsonArray("fields").asList().clear();
        first.getAsJsonObject("procedure").addProperty("consumerEdit", true);
        first.remove("recipe");
        assertEquals(expected, environment("fabric-1.21.1"));
        for (String type : List.of("block", "item", "recipe")) {
            JsonObject contract = editor(type, "fabric-1.21.1");
            assertEquals(expected.get(type), contract);
            contract.getAsJsonArray("fields").asList().clear();
            assertEquals(expected.get(type), editor(type, "fabric-1.21.1"));
        }
        assertEquals(expected, environment("fabric-1.21.1"));
    }
}
