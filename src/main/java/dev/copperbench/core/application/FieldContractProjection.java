package dev.copperbench.core.application;

import com.google.gson.JsonObject;

import java.util.List;

/** Read-only contract selection for workspace environment and editor projections. */
final class FieldContractProjection {
    private FieldContractProjection() {}

    static JsonObject environment(String generatorId) {
        JsonObject contracts = new JsonObject();
        contracts.add("block", BlockFieldContract.capabilities(generatorId));
        contracts.add("loottable", LootTableFieldContract.capabilities());
        contracts.add("function", FunctionFieldContract.capabilities());
        contracts.add("projectile", SpecializedFieldContract.capabilities("projectile"));
        contracts.add("achievement", SpecializedFieldContract.capabilities("achievement"));
        contracts.add("generic", GenericFieldInputContract.capabilities());
        contracts.add("custom", CustomFieldInputContract.capabilities());
        contracts.add("code", CodeFieldContract.capabilities());
        contracts.add("procedure", ProcedureFieldContract.capabilities());
        // The compatibility table only advertises complete creation contracts.
        for (String type : List.of("item", "recipe")) {
            JsonObject contract = ElementFieldContract.discover(type, generatorId);
            if (contract.get("complete").getAsBoolean()) contracts.add(type, contract);
        }
        return contracts;
    }

    /** Returns a fresh contract, or null when this editor does not expose one. */
    static JsonObject editor(String type, String generatorId) {
        // Existing item/recipe editors retain the reason when discovery is incomplete.
        if (ElementFieldContract.supports(type)) return ElementFieldContract.discover(type, generatorId);
        if ("block".equals(type)) return BlockFieldContract.capabilities(generatorId);
        return null;
    }
}
