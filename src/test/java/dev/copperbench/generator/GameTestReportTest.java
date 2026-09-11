package dev.copperbench.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class GameTestReportTest {
    @TempDir Path root;
    private final Instant start = Instant.now();

    @Test void acceptsNestedMinecraftReportAndCountsRealCases() throws Exception {
        var report = read("<testsuite><testsuite><testcase classname='behavior' name='save'/><testcase name='skip'><skipped/></testcase></testsuite></testsuite>");
        assertEquals("passed", report.get("status").getAsString());
        assertEquals(2, report.get("discovered").getAsInt());
        assertEquals(1, report.get("executed").getAsInt());
        assertEquals(1, report.get("skipped").getAsInt());
        assertTrue(report.has("reportSha256"));
    }
    @Test void rejectsNoCasesSkippedOnlyErrorsAndDeclaredFailuresWithoutCaseDetails() throws Exception {
        assertEquals("GAMETEST_NO_TESTS", read("<testsuite tests='1'/> ").get("reasonCode").getAsString());
        assertEquals("GAMETEST_NO_TESTS", read("<testsuite><testcase name='skip'><skipped/></testcase></testsuite>").get("reasonCode").getAsString());
        assertEquals("GAMETEST_FAILED", read("<testsuite><testcase name='bad'><error>boom</error></testcase></testsuite>").get("reasonCode").getAsString());
        assertEquals("GAMETEST_FAILED", read("<testsuite failures='1'><testcase name='incomplete'/></testsuite>").get("reasonCode").getAsString());
    }
    @Test void rejectsStaleMissingMalformedAndExternalEntityReports() throws Exception {
        Path path = root.resolve("report.xml");
        assertEquals("GAMETEST_REPORT_MISSING", GameTestReport.read(path, start, 1).get("reasonCode").getAsString());
        read("<testsuite><testcase name='old'/></testsuite>");
        Files.setLastModifiedTime(path, FileTime.from(start.minusSeconds(60)));
        assertEquals("GAMETEST_REPORT_STALE", GameTestReport.read(path, start, 1).get("reasonCode").getAsString());
        assertEquals("GAMETEST_REPORT_INVALID", read("<testsuite>").get("reasonCode").getAsString());
        assertEquals("GAMETEST_REPORT_INVALID", read("<!DOCTYPE testsuite [<!ENTITY secret SYSTEM 'file:///private'>]><testsuite>&secret;</testsuite>").get("reasonCode").getAsString());
    }
    private com.google.gson.JsonObject read(String xml) throws Exception {
        Path path = root.resolve("report.xml"); Files.writeString(path, xml);
        return GameTestReport.read(path, start, 1);
    }

    @Test void rejectsUnnamedNestedAndExcessivelyDeepCasesAndReadsFailureAttributes() throws Exception {
        assertEquals("GAMETEST_REPORT_INVALID", read("<testsuite><testcase/></testsuite>").get("reasonCode").getAsString());
        assertEquals("GAMETEST_REPORT_INVALID", read("<testsuite><testcase name='outer'><testcase name='inner'/></testcase></testsuite>").get("reasonCode").getAsString());
        assertEquals("GAMETEST_REPORT_INVALID", read("<testsuite>".repeat(80) + "<testcase name='too-deep'/>" + "</testsuite>".repeat(80)).get("reasonCode").getAsString());
        var failure = read("<testsuites failures='1'><testsuite><testcase name='save'><failure message='Anchor did not persist'/></testcase></testsuite></testsuites>");
        assertEquals("GAMETEST_FAILED", failure.get("reasonCode").getAsString());
        assertEquals("Anchor did not persist", failure.getAsJsonArray("cases").get(0).getAsJsonObject().get("message").getAsString());
    }

    @Test void minecraftAlwaysPassSelfCheckCannotSatisfyTheAcceptanceMinimum() throws Exception {
        String builtin = "<testcase name='minecraft:always_pass'/>";
        var empty = read("<testsuite>" + builtin + "</testsuite>");
        assertEquals("GAMETEST_NO_TESTS", empty.get("reasonCode").getAsString());
        assertEquals(1, empty.get("passed").getAsInt());
        assertEquals(0, empty.get("acceptanceExecuted").getAsInt());
        var real = read("<testsuite>" + builtin + "<testcase name='mod:behavior'/></testsuite>");
        assertEquals("passed", real.get("status").getAsString());
        assertEquals(2, real.get("executed").getAsInt());
        assertEquals(1, real.get("acceptanceExecuted").getAsInt());
        assertEquals(1, real.get("frameworkTests").getAsInt());
        assertEquals("GAMETEST_NO_TESTS", GameTestReport.read(root.resolve("report.xml"), start, 2).get("reasonCode").getAsString());
        assertEquals("GAMETEST_NO_TESTS", read("<testsuite>" + builtin + "<testcase name='mod:skip'><skipped/></testcase></testsuite>").get("reasonCode").getAsString());
    }
}
