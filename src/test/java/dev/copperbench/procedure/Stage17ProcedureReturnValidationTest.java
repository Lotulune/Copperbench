package dev.copperbench.procedure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Stage17ProcedureReturnValidationTest {
    private static final UUID ELEMENT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID RETURN = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private final ProcedureIrCodec codec = new ProcedureIrCodec();

    @ParameterizedTest
    @CsvSource({"number,text", "logic,math_number", "string,logic_boolean", "entity,text", "itemstack,entity_from_deps"})
    void rejectsKnownIncompatibleReturnValues(String returnType, String valueType) {
        var issues = codec.validate(body(returnType, "<block type=\"" + valueType + "\"/>"));
        var mismatch = issues.stream().filter(issue -> issue.code().equals("PROCEDURE_RETURN_TYPE_MISMATCH")).findFirst().orElseThrow();
        assertEquals(RETURN, mismatch.nodeId());
        assertEquals("VALUE", mismatch.port());
        assertTrue(mismatch.error());
    }

    @ParameterizedTest
    @CsvSource({"number,math_number", "logic,logic_boolean", "string,text", "entity,entity_from_deps", "itemstack,mcitem_all"})
    void acceptsKnownMatchingReturnValues(String returnType, String valueType) {
        assertTrue(codec.validate(body(returnType, "<block type=\"" + valueType + "\"/>")).isEmpty());
    }

    @Test void rejectsReturnWithoutAValueAndReportsItsNode() {
        var issues = codec.validate(body("number", ""));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("PROCEDURE_RETURN_VALUE_REQUIRED") && RETURN.equals(issue.nodeId())));
    }

    @Test void doesNotGuessUnknownPluginOutputTypesOrRewriteTheirPayload() {
        String opaque = "<block type=\"plugin_number\"><mutation custom=\"kept\"/><field name=\"data\">untouched</field></block>";
        var ir = body("number", opaque);
        assertTrue(codec.validate(ir).isEmpty());
        assertTrue(codec.toBlocklyXml(ir).contains(opaque));
    }

    private ProcedureIr body(String type, String value) {
        String input = value.isEmpty() ? "" : "<value name=\"VALUE\">" + value + "</value>";
        return codec.fromBlocklyXml("<xml><block type=\"event_trigger\"><field name=\"trigger\">no_ext_trigger</field><next>"
                + "<block type=\"return_" + type + "\" id=\"" + RETURN + "\">" + input + "</block></next></block></xml>", ELEMENT);
    }
}
