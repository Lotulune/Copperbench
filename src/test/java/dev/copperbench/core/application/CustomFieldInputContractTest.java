package dev.copperbench.core.application;

import com.google.gson.*;
import net.mcreator.element.parts.procedure.*;
import net.mcreator.ui.minecraft.states.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomFieldInputContractTest {
    static class Fields {
        public PropertyDataWithValue<?> property;
        public StateMap states;
        public NumberProcedure number;
        public StringProcedure text;
    }
    private static Gson gson() {
        GsonBuilder builder = new GsonBuilder(); RetvalProcedure.GSON_ADAPTERS.forEach(builder::registerTypeAdapter);
        builder.registerTypeAdapter(StateMap.class, new StateMap.GSONAdapter());
        return builder.create();
    }
    private static BlockFieldContract.Issue validate(String field, String raw) throws Exception {
        return GenericFieldInputContract.validate(Fields.class.getField(field), JsonParser.parseString(raw), "/" + field);
    }
    @Test void propertyValuesRespectTheirSerializedTypeBoundsAndChoices() throws Exception {
        for (String[] row : new String[][] {
                {"{\"type\":\"integer\",\"name\":\"count\",\"min\":0,\"max\":10,\"value\":1.0000000000000001}", "FIELD_VALUE_OUT_OF_RANGE", "/property/value"},
                {"{\"type\":\"integer\",\"name\":\"count\",\"min\":0,\"max\":10,\"value\":11}", "FIELD_VALUE_OUT_OF_RANGE", "/property/value"},
                {"{\"type\":\"integer\",\"name\":\"count\",\"min\":11,\"max\":10,\"value\":1}", "FIELD_VALUE_OUT_OF_RANGE", "/property/max"},
                {"{\"type\":\"logic\",\"name\":\"enabled\",\"value\":\"false\"}", "FIELD_TYPE_INVALID", "/property/value"},
                {"{\"type\":\"string\",\"name\":\"mode\",\"arrayData\":[\"on\",\"off\"],\"value\":\"typo\"}", "FIELD_ENUM_INVALID", "/property/value"},
                {"{\"type\":\"logic\",\"name\":\"enabled\",\"value\":false,\"typo\":true}", "FIELD_UNSUPPORTED", "/property/typo"}
        }) {
            var issue = validate("property", row[0]); assertNotNull(issue, row[0]);
            assertEquals(row[1], issue.code()); assertEquals(row[2], issue.path());
        }
        String valid = "{\"type\":\"integer\",\"name\":\"count\",\"min\":0,\"max\":10,\"value\":4}";
        assertNull(validate("property", valid));
        PropertyDataWithValue<?> stored = gson().fromJson(valid, Fields.class.getField("property").getGenericType());
        assertEquals(4, stored.value()); assertEquals(10, ((PropertyData.IntegerType) stored.property()).getMax());
        assertEquals(JsonParser.parseString(valid), gson().toJsonTree(stored, Fields.class.getField("property").getGenericType()));
    }
    @Test void duplicateStateNamesCannotOverwriteEarlierEntries() throws Exception {
        String state = "{\"property\":{\"type\":\"logic\",\"name\":\"enabled\"},\"value\":false}";
        assertNull(validate("states", "[" + state + "]"));
        StateMap stored = gson().fromJson("[" + state + "]", StateMap.class);
        assertEquals(1, stored.size()); assertEquals(false, stored.values().iterator().next());
        var duplicate = validate("states", "[" + state + "," + state + "]");
        assertEquals("FIELD_ALIAS_CONFLICT", duplicate.code()); assertEquals("/states/1/property/name", duplicate.path());
    }
    @Test void typedReturnValuesRetainValidScalarAndObjectForms() throws Exception {
        assertNull(validate("number", "7.25"));
        assertEquals(7.25, gson().fromJson("7.25", NumberProcedure.class).getFixedValue());
        assertNull(validate("text", "\"unchanged\""));
        assertEquals("unchanged", gson().fromJson("\"unchanged\"", StringProcedure.class).getFixedValue());
        assertNull(validate("number", "{\"name\":null,\"fixedValue\":7.25}"));
        assertEquals("FIELD_TYPE_INVALID", validate("number", "\"7.25\"").code());
        assertEquals("FIELD_TYPE_INVALID", validate("text", "42").code());
        assertEquals("FIELD_TYPE_INVALID", validate("number", "{}").code());
    }
}
