package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.procedure.ProcedureIrCodec;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProcedureFieldContractTest {
    private final ProcedureIrCodec codec = new ProcedureIrCodec();
    private JsonObject body() {
        return codec.toJson(codec.fromBlocklyXml("<xml><block type=\"event_trigger\" x=\"0\" y=\"40.5\"><field name=\"trigger\">no_ext_trigger</field></block></xml>", UUID.randomUUID()));
    }
    @Test void unsupportedXmlConstructsAreReportedInsteadOfBeingSilentlyRewritten() {
        for (String fragment : new String[] {
                "<variables><variable id=\"v\">score</variable></variables>",
                "<block type=\"controls_if\"><mutation elseif=\"1\"/></block>",
                "<block type=\"text\" disabled=\"true\"><field name=\"TEXT\">keep</field></block>",
                "<block type=\"variables_get_number\"><field name=\"VAR\" id=\"v\">score</field></block>",
                "<block type=\"text_print\"><value name=\"TEXT\"><shadow type=\"text\"><field name=\"TEXT\">default</field></shadow></value></block>",
                "<block type=\"text\"><comment>keep</comment></block>",
                "<block type=\"text\"><field name=\"TEXT\">one</field><field name=\"TEXT\">two</field></block>",
                "<block type=\"text_print\"><value name=\"TEXT\"><block type=\"text_print\"/></value></block>"
        }) {
            JsonObject values = new JsonObject(); values.addProperty("procedurexml", "<xml>" + fragment + "</xml>");
            assertNull(ProcedureFieldContract.validate(values), "Original XML remains a supported input");
            var issue = ProcedureFieldContract.structuredEditIssue(values);
            assertNotNull(issue, fragment); assertEquals("PROCEDURE_XML_PRESERVATION_REQUIRED", issue.code());
        }
        String opaque = "<block type=\"future_block\" custom=\"keep\"><mutation custom=\"retain\"/></block>";
        JsonObject values = new JsonObject(); values.addProperty("procedurexml", "<xml>" + opaque + "</xml>");
        assertNull(ProcedureFieldContract.structuredEditIssue(values), "Opaque subtree is retained as a whole");
        values.addProperty("procedurexml", "<xml><block type=\"text_print\"><value name=\"TEXT\"><block type=\"text\"><field name=\"TEXT\">hello</field></block></value></block></xml>");
        assertNull(ProcedureFieldContract.structuredEditIssue(values));
    }
    @Test void malformedStructuredMembersCannotDisappearDuringExport() {
        for (String[] input : new String[][] {
                {"{\"nodes\":false}", "/nodes"}, {"{\"dependencies\":{}}", "/dependencies"},
                {"{\"schemaVersion\":\"2.0\"}", "/schemaVersion"}, {"{\"unknownRoot\":[]}", "/unknownRoot"},
                {"{\"extra\":true}", "/extra"}, {"{\"trigger\":false}", "/trigger"}
        }) {
            JsonObject values = new JsonObject(); values.add("procedureIr", JsonParser.parseString(input[0]));
            var issue = ProcedureFieldContract.validate(values); assertNotNull(issue); assertEquals("/procedureIr" + input[1], issue.path());
        }
        JsonObject ir = body(), values = new JsonObject(); values.add("procedureIr", ir);
        JsonObject node = ir.getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.addProperty("inputs", "ignored"); assertEquals("/procedureIr/nodes/0/inputs", ProcedureFieldContract.validate(values).path());
        node.remove("inputs"); node.addProperty("unknown", "false"); assertEquals("/procedureIr/nodes/0/unknown", ProcedureFieldContract.validate(values).path());
        node.remove("unknown"); ir.getAsJsonArray("nodes").add(node.deepCopy());
        assertEquals("/procedureIr/nodes/1/id", ProcedureFieldContract.validate(values).path());
        JsonObject opaque = body(); node = opaque.getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.addProperty("type", "future_block"); node.addProperty("unknown", true); node.addProperty("rawPayload", "<not_a_block/>");
        values.add("procedureIr", opaque); assertEquals("/procedureIr/nodes/0/rawPayload", ProcedureFieldContract.validate(values).path());
    }
    @Test void matchingBodiesIgnoreEditorIdsButRejectDifferentBlockContent() {
        JsonObject values = new JsonObject(); JsonObject ir = body(); values.add("procedureIr", ir);
        String xml = "<xml><block type=\"event_trigger\" x=\"0\" y=\"40.5\"><field name=\"trigger\">no_ext_trigger</field></block></xml>";
        values.addProperty("procedurexml", xml); assertNull(ProcedureFieldContract.validate(values));
        ProcedureFieldContract.normalize(values, UUID.randomUUID()); assertEquals(xml, values.get("procedurexml").getAsString());
        values.addProperty("procedurexml", xml.replace("no_ext_trigger", "different_trigger"));
        assertEquals("PROCEDURE_BODY_CONFLICT", ProcedureFieldContract.validate(values).code());
    }
    @Test void incompleteGraphsAndOpaqueBlocksRemainAvailableAsDrafts() {
        JsonObject ir = body(), values = new JsonObject();
        JsonObject node = ir.getAsJsonArray("nodes").get(0).getAsJsonObject(); node.addProperty("next", node.get("id").getAsString());
        ir.getAsJsonObject("unknownRoot").addProperty("retained", "draft metadata"); values.add("procedureIr", ir);
        assertNull(ProcedureFieldContract.validate(values)); ProcedureFieldContract.normalize(values, UUID.randomUUID());
        assertEquals(1, values.getAsJsonObject("procedureIr").getAsJsonArray("nodes").size());
        assertTrue(codec.validate(codec.read(values, UUID.randomUUID())).stream().anyMatch(issue -> issue.code().equals("PROCEDURE_GRAPH_CYCLE")));
        assertEquals("draft metadata", values.getAsJsonObject("procedureIr").getAsJsonObject("unknownRoot").get("retained").getAsString());
        String opaque = "<block type=\"future_block\"><mutation custom=\"retain\"/><field name=\"data\">A &amp; B</field></block>";
        values = new JsonObject(); values.add("procedureIr", codec.toJson(codec.fromBlocklyXml("<xml>" + opaque + "</xml>", UUID.randomUUID())));
        assertNull(ProcedureFieldContract.validate(values)); ProcedureFieldContract.normalize(values, UUID.randomUUID());
        assertTrue(values.get("procedurexml").getAsString().contains(opaque));
    }
}
