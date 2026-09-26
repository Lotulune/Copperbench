package dev.copperbench.core.application;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockFieldContractTest {
    @Test void integerFieldsAndSlotsRejectDecimalPrecisionLoss() {
        assertIssue("{\"inventorySize\":1.0000000000000001}", "FIELD_VALUE_OUT_OF_RANGE", "/inventorySize");
        assertIssue("{\"inventorySize\":3,\"inventoryInSlotIDs\":[4294967296]}", "FIELD_VALUE_OUT_OF_RANGE", "/inventoryInSlotIDs");
        assertIssue("{\"inventorySize\":3,\"inventoryInSlotIDs\":[1.0000000000000001]}", "FIELD_VALUE_OUT_OF_RANGE", "/inventoryInSlotIDs");
        assertTrue(BlockFieldContract.isIntegerInStorageRange(JsonParser.parseString("9223372036854775807"), long.class));
        assertFalse(BlockFieldContract.isIntegerInStorageRange(JsonParser.parseString("9223372036854775808"), long.class));
        assertTrue(BlockFieldContract.isIntegerInStorageRange(JsonParser.parseString("1e2"), int.class));
    }

    @Test void rejectsWrongTypesAliasesAndUnsafeShapesWithFieldPaths() {
        assertIssue("{\"hardness\":\"3\"}", "FIELD_TYPE_INVALID", "/hardness");
        assertIssue("{\"inventorySize\":3.5}", "FIELD_VALUE_OUT_OF_RANGE", "/inventorySize");
        assertIssue("{\"rotationMode\":6}", "FIELD_ENUM_INVALID", "/rotationMode");
        assertIssue("{\"hardness\":3,\"fields\":{\"hardness\":4}}", "FIELD_ALIAS_CONFLICT", "/fields/hardness");
        assertIssue("{\"hasInventory\":true}", "FIELD_REQUIRED_BY_CONDITION", "/inventorySize");
        assertIssue("{\"boundingBoxes\":[{\"mx\":16,\"Mx\":0}]}", "FIELD_VALUE_OUT_OF_RANGE", "/boundingBoxes/0");
        assertIssue("{\"boundingBoxes\":[{\"width\":16}]}", "FIELD_UNSUPPORTED", "/boundingBoxes/0/width");
    }

    @Test void preservesLegalLegacyFormAndExplicitEmptyShape() {
        assertNull(BlockFieldContract.validate(JsonParser.parseString("""
            {"fields":{"hardness":3,"rotationMode":1,"hasInventory":true,"inventorySize":3,"inventoryStackSize":64}}
            """).getAsJsonObject()));
        assertNull(BlockFieldContract.validate(JsonParser.parseString("{\"boundingBoxes\":[]}").getAsJsonObject()));
    }

    @Test void aSingleEditCanSwitchAliasFormButConflictingSuppliedFormsRemainInvalid() {
        var values = JsonParser.parseString("{\"hardness\":3,\"fields\":{\"hardness\":5}}").getAsJsonObject();
        BlockFieldContract.reconcileEditedAliases(values, JsonParser.parseString("[{\"path\":\"/fields/hardness\",\"value\":5}]").getAsJsonArray());
        assertFalse(values.has("hardness")); assertNull(BlockFieldContract.validate(values));
        values.addProperty("hardness", 7);
        BlockFieldContract.reconcileEditedAliases(values, JsonParser.parseString("[{\"path\":\"/fields/hardness\",\"value\":5},{\"path\":\"/hardness\",\"value\":7}]").getAsJsonArray());
        assertEquals("FIELD_ALIAS_CONFLICT", BlockFieldContract.validate(values).code());
    }

    private void assertIssue(String json, String code, String path) {
        var issue = BlockFieldContract.validate(JsonParser.parseString(json).getAsJsonObject());
        assertNotNull(issue); assertEquals(code, issue.code()); assertEquals(path, issue.path());
    }
}
