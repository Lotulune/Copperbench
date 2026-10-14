package dev.copperbench.generator;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GameTestIdentityTest {
    @TempDir Path root;

    @Test void rejectsRepeatedBusinessIdentityAtThreshold() throws Exception {
        JsonObject result = read("<testsuite>"
                + "<testcase classname='camp.Contract' name='stack_limit'/>".repeat(6) + "</testsuite>", 6);
        assertEquals("failed", result.get("status").getAsString());
        assertEquals("GAMETEST_REPORT_INVALID", result.get("reasonCode").getAsString());
        assertEquals(1, result.get("acceptanceExecuted").getAsInt());
        assertTrue(result.get("detail").getAsString().startsWith("GAMETEST_DUPLICATE_TESTS:"));
    }

    @Test void rejectsDuplicateAcrossSuites() throws Exception {
        JsonObject result = read("<testsuites>"
                + "<testsuite name='first'><testcase classname='camp.Contract' name='stack_limit'/></testsuite>"
                + "<testsuite name='second'><testcase classname='camp.Contract' name='stack_limit'/></testsuite>"
                + "</testsuites>", 2);
        assertEquals("GAMETEST_REPORT_INVALID", result.get("reasonCode").getAsString());
        assertEquals(1, result.get("acceptanceExecuted").getAsInt());
    }

    @Test void preservesDistinctClassesAndExplicitParameterNames() throws Exception {
        JsonObject result = read("<testsuite>"
                + "<testcase classname='camp.First' name='stack_limit'/><testcase classname='camp.Second' name='stack_limit'/>"
                + "<testcase classname='camp.Parameters' name='component[variant=one]'/><testcase classname='camp.Parameters' name='component[variant=two]'/>"
                + "</testsuite>", 4);
        assertEquals("passed", result.get("status").getAsString());
        assertEquals(4, result.get("acceptanceExecuted").getAsInt());
    }

    @Test void usesFullRawNamesBeforeDisplayTruncation() throws Exception {
        String prefix = "case".repeat(128);
        JsonObject result = read("<testsuite><testcase classname='camp.Contract' name='" + prefix + "-one'/>"
                + "<testcase classname='camp.Contract' name='" + prefix + "-two'/></testsuite>", 2);
        assertEquals("passed", result.get("status").getAsString());
        assertEquals(2, result.get("acceptanceExecuted").getAsInt());
        assertEquals(result.getAsJsonArray("cases").get(0).getAsJsonObject().get("name"),
                result.getAsJsonArray("cases").get(1).getAsJsonObject().get("name"));
    }

    @Test void usesFullRawClassesBeforeDisplayTruncation() throws Exception {
        String prefix = "type".repeat(128);
        JsonObject result = read("<testsuite><testcase classname='" + prefix + ".First' name='stack_limit'/>"
                + "<testcase classname='" + prefix + ".Second' name='stack_limit'/></testsuite>", 2);
        assertEquals("passed", result.get("status").getAsString());
        assertEquals(2, result.get("acceptanceExecuted").getAsInt());
        assertEquals(result.getAsJsonArray("cases").get(0).getAsJsonObject().get("className"),
                result.getAsJsonArray("cases").get(1).getAsJsonObject().get("className"));
    }

    @Test void treatsClassAndNameAsSeparateIdentityParts() throws Exception {
        JsonObject result = read("<testsuite><testcase classname='camp#variant' name='limit'/>"
                + "<testcase classname='camp' name='variant#limit'/></testsuite>", 2);
        assertEquals("passed", result.get("status").getAsString());
        assertEquals(2, result.get("acceptanceExecuted").getAsInt());
    }

    @Test void retainsFullGameTestResourceNamesWithoutInventingSuiteIdentity() throws Exception {
        JsonObject result = read("<testsuite><testcase name='first:stack_limit'/><testcase name='second:stack_limit'/></testsuite>", 2);
        assertEquals("passed", result.get("status").getAsString());
        assertEquals(2, result.get("acceptanceExecuted").getAsInt());
        JsonObject repeated = read("<testsuites><testsuite name='one'><testcase name='first:stack_limit'/></testsuite>"
                + "<testsuite name='two'><testcase classname='' name='first:stack_limit'/></testsuite></testsuites>", 1);
        assertEquals("GAMETEST_REPORT_INVALID", repeated.get("reasonCode").getAsString());
        assertEquals(1, repeated.get("acceptanceExecuted").getAsInt());
    }

    @Test void aLaterAttemptCannotHideFailureOrIncreaseBusinessCoverage() throws Exception {
        JsonObject result = read("<testsuite><testcase name='camp:behavior'><failure message='first attempt failed'/></testcase>"
                + "<testcase name='camp:behavior'/></testsuite>", 2);
        assertEquals("GAMETEST_FAILED", result.get("reasonCode").getAsString());
        assertEquals(1, result.get("acceptanceExecuted").getAsInt());
        assertEquals(1, result.get("failed").getAsInt());
        assertEquals("first attempt failed", result.getAsJsonArray("cases").get(0).getAsJsonObject().get("message").getAsString());
    }

    @Test void aSkippedAttemptCannotEraseARealExecution() throws Exception {
        for (String cases : new String[]{
                "<testcase name='camp:behavior'><skipped/></testcase><testcase name='camp:behavior'/>",
                "<testcase name='camp:behavior'/><testcase name='camp:behavior'><skipped/></testcase>"}) {
            JsonObject result = read("<testsuite>" + cases + "</testsuite>", 1);
            assertEquals("GAMETEST_REPORT_INVALID", result.get("reasonCode").getAsString());
            assertEquals(1, result.get("acceptanceExecuted").getAsInt());
        }
    }

    @Test void repeatedSkippedOrFrameworkCasesDoNotBecomeBusinessCoverage() throws Exception {
        for (String test : new String[]{"<testcase name='camp:skipped'><skipped/></testcase>",
                "<testcase name='minecraft:always_pass'/>"}) {
            JsonObject result = read("<testsuite>" + test.repeat(6) + "</testsuite>", 1);
            assertEquals("GAMETEST_REPORT_INVALID", result.get("reasonCode").getAsString());
            assertEquals(0, result.get("acceptanceExecuted").getAsInt());
        }
    }

    @Test void duplicateFailureUsesOnlyExistingV1Fields() throws Exception {
        JsonObject result = read("<testsuite><testcase name='camp:behavior'/><testcase name='camp:behavior'/></testsuite>", 1);
        assertEquals(Set.of("schemaVersion", "status", "reasonCode", "discovered", "executed", "passed", "failed",
                "skipped", "frameworkTests", "acceptanceExecuted", "cases", "reportPath", "reportSha256", "detail"), result.keySet());
        assertEquals(Set.of("name", "className", "status", "scope"), result.getAsJsonArray("cases").get(0).getAsJsonObject().keySet());
    }

    private JsonObject read(String xml, int minimum) throws Exception {
        Path report = root.resolve("report.xml");
        Files.writeString(report, xml);
        return GameTestReport.read(report, Instant.now().minusSeconds(1), minimum);
    }
}
