package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.AcceptanceExtension;
import dev.copperbench.core.contract.UiCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AcceptanceDiagnosticsTest {
    @TempDir Path root;

    JsonObject task(GameTestReport.Assessment assessment, int minimum) {
        JsonObject value = new JsonObject();
        value.addProperty("id", UUID.randomUUID().toString());
        value.addProperty("kind", "run_gametest");
        value.addProperty("state", assessment.verification().get("status").getAsString().equals("passed") ? "succeeded" : "failed");
        JsonObject verification = assessment.verification().deepCopy();
        verification.addProperty("minimumTests", minimum);
        value.add("verification", verification);
        value.add(AcceptanceDiagnostics.TASK_FIELD, assessment.coverage());
        return value;
    }

    void fixture(String name, JsonObject value) throws Exception {
        Path folder = Path.of("output/p263-13/projection-fixtures");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(name + ".json"), UiCore.wireGson().toJson(value));
        JsonObject shared = value.deepCopy();
        shared.addProperty("taskId", "33333333-3333-4333-8333-333333333333");
        assertEquals(JsonParser.parseString(Files.readString(Path.of("ui-core/fixtures/extensions/acceptance-diagnostics", name + ".json"))), shared);
    }

    @ParameterizedTest @CsvSource({"0,1,no_business", "5,6,insufficient", "6,6,passed_current_input", "7,6,passed_current_input"})
    void rawBusinessThresholdsProduceMachineParameters(int actual, int minimum, String expected) throws Exception {
        Path report = root.resolve("result.xml");
        Files.writeString(report, "<testsuite>" + java.util.stream.IntStream.range(0, actual)
                .mapToObj(index -> "<testcase name='case" + index + "'/>").collect(java.util.stream.Collectors.joining()) + "</testsuite>");
        var assessed = GameTestReport.assess(report, Instant.now().minusSeconds(1), minimum);
        JsonObject task = task(assessed, minimum);
        // The new parser observation preserves the complete old v1.0 report, including its readable detail.
        assertEquals(GameTestReport.read(report, Instant.now().minusSeconds(1), minimum), assessed.verification());
        var result = AcceptanceDiagnostics.project(task, task);
        assertEquals(expected, result.get("state").getAsString());
        assertEquals(0, result.get("required").getAsInt());
        assertEquals(actual, result.get("uniqueExecuted").getAsInt());
        assertEquals(Math.max(0, minimum - actual), result.get("shortfall").getAsInt());
        fixture("threshold-" + actual + "-" + minimum, result);
    }

    @ParameterizedTest @CsvSource({"skipped,no_business", "framework,no_business", "failed,business_failed", "duplicate,duplicate",
            "missing,required_cases_missing", "stale,report_unavailable", "broken,report_unavailable", "absent,report_unavailable"})
    void reportFailureKindsRemainDistinctWithoutReadingEnglishDetail(String mode, String expected) throws Exception {
        Path report = root.resolve("result.xml");
        RequiredCaseManifest manifest = RequiredCaseManifest.catalog().getFirst();
        String xml = "<testsuite>" + manifest.cases().stream().map(test -> "<testcase name='"
                + (mode.equals("missing") && test.name().endsWith("12") ? "camp:other" : test.name()) + "'>"
                + (mode.equals("skipped") ? "<skipped/>" : mode.equals("failed") ? "<failure/>" : "")
                + "</testcase>").collect(java.util.stream.Collectors.joining());
        if (mode.equals("duplicate")) xml += "<testcase classname='other_host' name='copperbench_camp_kit:t01'/>";
        xml += "</testsuite>";
        if (mode.equals("framework")) xml = "<testsuite><testcase name='minecraft:always_pass'/></testsuite>";
        if (mode.equals("broken")) xml = "<testsuite><broken";
        if (!mode.equals("absent")) Files.writeString(report, xml);
        if (mode.equals("stale")) Files.setLastModifiedTime(report, FileTime.from(Instant.now().minusSeconds(30)));
        var assessed = GameTestReport.assess(report, Instant.now().minusSeconds(1), manifest);
        var task = task(assessed, 6);
        var named = new JsonObject(); named.add("manifest", manifest.projection());
        task.add(RequiredCaseEvidence.TASK_FIELD, named);
        task.getAsJsonObject("verification").addProperty("detail", "Unrelated localized text; not a machine condition.");
        var result = AcceptanceDiagnostics.project(task, task);
        assertEquals(expected, result.get("state").getAsString(), result.toString());
        assertEquals(6, result.get("required").getAsInt());
        if (mode.equals("duplicate")) assertEquals("copperbench_camp_kit:t01", result.getAsJsonArray("duplicates").get(0).getAsJsonObject().get("name").getAsString());
        if (mode.equals("missing")) {
            assertEquals(6, result.get("uniqueExecuted").getAsInt());
            assertEquals(0, result.get("shortfall").getAsInt());
            assertEquals("copperbench_camp_kit:t12", result.getAsJsonArray("missingCases").get(0).getAsJsonObject().get("name").getAsString());
        }
        if (expected.equals("report_unavailable")) assertTrue(result.get("uniqueExecuted").isJsonNull());
        fixture(mode, result);
    }

    @Test void runningAndHistoricalProjectionUsesRecordedFactsAndKeepsLegacyObjectsStrict() throws Exception {
        Path report = root.resolve("result.xml"); Files.writeString(report, "<testsuite><testcase name='case'/></testsuite>");
        var original = task(GameTestReport.assess(report, Instant.now().minusSeconds(1), 1), 1);
        UUID workspaceId = UUID.randomUUID();
        WorkspaceTaskRecords.save(root, workspaceId, original, List.of(), List.of());
        JsonObject live = RequiredCaseEvidence.legacyTask(original);
        assertFalse(live.has(AcceptanceDiagnostics.TASK_FIELD));
        live.addProperty("state", "running");
        var running = AcceptanceDiagnostics.recorded(root, workspaceId, live);
        assertEquals("running", running.get("state").getAsString());
        assertTrue(running.get("uniqueExecuted").isJsonNull());
        fixture("running", running);
        var exported = new JsonObject(); exported.addProperty("id", UUID.randomUUID().toString());
        exported.addProperty("state", "succeeded");
        var receipt = new JsonObject(); receipt.add("taskId", original.get("id")); receipt.addProperty("status", "passed_historical_input");
        exported.add("verifiedExport", receipt);
        var historical = AcceptanceDiagnostics.recorded(root, workspaceId, exported);
        assertEquals("passed_historical_input", historical.get("state").getAsString());
        assertEquals("historical_at_export", historical.get("inputScope").getAsString());
        assertEquals(1, historical.get("uniqueExecuted").getAsInt());
        fixture("historical", historical);
        receipt.addProperty("status", "passed_current_input");
        var current = AcceptanceDiagnostics.recorded(root, workspaceId, exported);
        assertEquals("current_at_export", current.get("inputScope").getAsString());
        fixture("current-export", current);
    }

    @Test void lateInputOrBindingFailureCannotBePresentedAsPassedCoverage() throws Exception {
        Path report = root.resolve("result.xml"); Files.writeString(report, "<testsuite><testcase name='case'/></testsuite>");
        var task = task(GameTestReport.assess(report, Instant.now().minusSeconds(1), 1), 1);
        for (String code : List.of("GAMETEST_REPORT_INVALID", "GAMETEST_SOURCE_CHANGED", "GAMETEST_ARTIFACT_CHANGED")) {
            task.addProperty("state", "failed"); GameTestReport.reason(task.getAsJsonObject("verification"), code);
            assertEquals("report_unavailable", AcceptanceDiagnostics.project(task, task).get("state").getAsString());
        }
    }

    @Test void negotiationIsReadOnlyAndOldGetDoesNotGainAnyNewField() {
        UUID workspaceId = UUID.randomUUID(), requestId = UUID.randomUUID();
        var task = JsonParser.parseString("{\"id\":\"33333333-3333-4333-8333-333333333333\",\"state\":\"running\"}").getAsJsonObject();
        AtomicInteger queries = new AtomicInteger(), summaries = new AtomicInteger();
        java.util.function.Function<JsonObject, JsonObject> dispatch = request -> AcceptanceExtension.dispatch(requestId, workspaceId, request,
                command -> { throw new AssertionError("No command may run during diagnostics negotiation or read"); },
                query -> { queries.incrementAndGet(); var data = new JsonObject(); data.add("task", task);
                    return new UiCore.QueryResult(null, null, requestId, workspaceId, UiCore.Operation.GET_TASK, "succeeded", 4, data, List.of()); },
                id -> new JsonObject(), live -> { summaries.incrementAndGet(); return AcceptanceDiagnostics.project(live, live); });
        var request = JsonParser.parseString("{\"supportedVersions\":[\"1\"],\"operation\":\"get\",\"payload\":{\"taskId\":\"33333333-3333-4333-8333-333333333333\"}}").getAsJsonObject();
        var legacy = dispatch.apply(request);
        assertFalse(legacy.has("coverageDiagnostics"));
        assertEquals(0, summaries.get());
        request.add("diagnosticVersions", JsonParser.parseString("[\"1\"]"));
        var detailed = dispatch.apply(request);
        assertEquals(legacy.get("core"), detailed.get("core"));
        assertEquals("running", detailed.getAsJsonObject("coverageDiagnostics").get("state").getAsString());
        assertEquals(1, summaries.get());
        request.add("diagnosticVersions", new JsonArray());
        assertEquals("failed", dispatch.apply(request).get("status").getAsString());
        request.add("diagnosticVersions", JsonParser.parseString("[\"99\"]"));
        assertEquals("failed", dispatch.apply(request).get("status").getAsString());
        request.add("diagnosticVersions", JsonParser.parseString("[\"1\"]"));
        request.addProperty("operation", "run");
        assertEquals("failed", dispatch.apply(request).get("status").getAsString());
        assertEquals(2, queries.get());
    }
}
