package dev.copperbench.generator;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GameTestCoverageTest {
    @TempDir Path root;

    private JsonObject read(String cases, int minimum) throws Exception {
        Path report = root.resolve("results.xml");
        Files.writeString(report, "<testsuite>" + cases + "</testsuite>");
        return GameTestReport.read(report, Instant.now().minusSeconds(1), minimum);
    }

    private String business(int count) {
        StringBuilder xml = new StringBuilder();
        for (int i = 0; i < count; i++) xml.append("<testcase name='business-").append(i).append("'/>");
        return xml.toString();
    }

    @Test void fiveOfSixReportsPreciseShortfallAndRetainsLegacyCode() throws Exception {
        JsonObject result = read(business(5), 6);
        assertEquals("failed", result.get("status").getAsString());
        assertEquals("GAMETEST_NO_TESTS", result.get("reasonCode").getAsString());
        assertTrue(result.get("detail").getAsString().startsWith("GAMETEST_INSUFFICIENT_TESTS:"));
        assertTrue(result.get("detail").getAsString().contains("At least 6"));
        assertTrue(result.get("detail").getAsString().contains("observed 5"));
        assertEquals(5, result.get("acceptanceExecuted").getAsInt());
        assertEquals(5, result.getAsJsonArray("cases").size());
        assertTrue(result.has("reportSha256"));
        assertFalse(result.has("coverage"), "Do not extend the strict v1.0 task schema implicitly");
    }

    @Test void zeroCasesIsNoTestsRatherThanInsufficient() throws Exception {
        JsonObject result = read("", 6);
        assertEquals("GAMETEST_NO_TESTS", result.get("reasonCode").getAsString());
        assertTrue(result.get("detail").getAsString().startsWith("GAMETEST_NO_TESTS:"));
        assertTrue(result.get("detail").getAsString().contains("observed 0"));
    }

    @Test void exactAndExcessCoverageRemainPassedWithoutFailureDetail() throws Exception {
        for (int count : new int[]{5, 6}) {
            JsonObject result = read(business(count), 5);
            assertEquals("passed", result.get("status").getAsString());
            assertEquals("GAMETEST_PASSED", result.get("reasonCode").getAsString());
            assertFalse(result.has("detail"));
            assertEquals(count, result.get("acceptanceExecuted").getAsInt());
        }
    }

    @Test void skippedAndFrameworkCasesCannotFillTheShortfall() throws Exception {
        JsonObject result = read(business(4)
                + "<testcase name='minecraft:always_pass'/><testcase name='disabled'><skipped/></testcase>", 5);
        assertEquals("failed", result.get("status").getAsString());
        assertEquals(6, result.get("discovered").getAsInt());
        assertEquals(5, result.get("executed").getAsInt());
        assertEquals(4, result.get("acceptanceExecuted").getAsInt());
        assertTrue(result.get("detail").getAsString().contains("observed 4"));
    }

    @Test void skippedOnlyAndFrameworkOnlyRemainNoTests() throws Exception {
        for (String cases : new String[]{"<testcase name='disabled'><skipped/></testcase>",
                "<testcase name='minecraft:always_pass'/>"}) {
            JsonObject result = read(cases, 1);
            assertTrue(result.get("detail").getAsString().startsWith("GAMETEST_NO_TESTS:"));
            assertEquals("GAMETEST_NO_TESTS", result.get("reasonCode").getAsString());
        }
    }

    @Test void failedCasesTakePrecedenceOverCoverage() throws Exception {
        for (int minimum : new int[]{1, 2}) {
            JsonObject result = read("<testcase name='business'><failure message='actual failure'/></testcase>", minimum);
            assertEquals("failed", result.get("status").getAsString());
            assertEquals("GAMETEST_FAILED", result.get("reasonCode").getAsString());
            assertEquals(1, result.get("acceptanceExecuted").getAsInt());
            assertFalse(result.has("detail"), "Coverage guidance must not replace the real failing case");
        }
    }

    @Test void malformedAndMissingReportsAreNotRelabeledCoverageFailures() throws Exception {
        Path report = root.resolve("missing.xml");
        JsonObject missing = GameTestReport.read(report, Instant.now(), 5);
        assertEquals("GAMETEST_REPORT_MISSING", missing.get("reasonCode").getAsString());
        Files.writeString(report, "<broken");
        JsonObject invalid = GameTestReport.read(report, Instant.now().minusSeconds(1), 5);
        assertEquals("GAMETEST_REPORT_INVALID", invalid.get("reasonCode").getAsString());
    }

    @Test void legacyMinimumFloorRemainsOne() throws Exception {
        JsonObject result = read("", 0);
        assertTrue(result.get("detail").getAsString().contains("At least 1"));
        assertEquals("failed", result.get("status").getAsString());
    }
}
