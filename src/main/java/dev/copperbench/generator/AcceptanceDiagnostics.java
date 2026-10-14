package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/** Read-only presentation of recorded acceptance facts, separately negotiated from strict v1.0 tasks. */
public final class AcceptanceDiagnostics {
    static final String TASK_FIELD = "acceptanceCoverage";
    private AcceptanceDiagnostics() { }

    /** Uses the live task state; persisted running snapshots alone cannot prove a live process. */
    public static JsonObject recorded(Path root, UUID workspaceId, JsonObject liveTask) {
        JsonObject source = liveTask;
        if (root != null) {
            UUID sourceId = UUID.fromString(liveTask.has("verifiedExport")
                    ? liveTask.getAsJsonObject("verifiedExport").get("taskId").getAsString()
                    : liveTask.get("id").getAsString());
            source = WorkspaceTaskRecords.read(root, workspaceId, sourceId)
                    .map(record -> record.getAsJsonObject("task")).orElse(liveTask);
        }
        return project(liveTask, source);
    }

    static JsonObject project(JsonObject liveTask, JsonObject recordedSource) {
        JsonObject value = new JsonObject();
        value.addProperty("schemaVersion", "1");
        value.add("taskId", liveTask.get("id").deepCopy());
        JsonObject verification = object(recordedSource, "verification");
        JsonObject evidence = object(recordedSource, RequiredCaseEvidence.TASK_FIELD);
        JsonObject coverage = recordedSource.has(TASK_FIELD) ? object(recordedSource, TASK_FIELD) : object(evidence, "coverage");
        JsonObject manifest = evidence.has("manifest") ? object(evidence, "manifest") : object(object(evidence, "binding"), "manifest");
        value.addProperty("evidenceScope", manifest.isEmpty() ? "minimum_only_legacy" : "required_cases");
        value.addProperty("inputScope", "unverified");
        for (String field : new String[]{"required", "minimumTests", "uniqueExecuted", "shortfall"})
            value.add(field, coverage.has(field) ? coverage.get(field).deepCopy() : JsonNull.INSTANCE);
        if (!manifest.isEmpty()) {
            value.addProperty("required", manifest.getAsJsonArray("cases").size());
            value.add("minimumTests", manifest.get("minimumTests").deepCopy());
        } else if (verification.has("minimumTests")) {
            value.addProperty("required", 0);
            value.add("minimumTests", verification.get("minimumTests").deepCopy());
        }
        for (String field : new String[]{"duplicates", "missingCases"})
            value.add(field, coverage.has(field) ? coverage.get(field).deepCopy() : new JsonArray());
        String taskState = string(liveTask, "state", "failed");
        if (Set.of("queued", "running").contains(taskState)) {
            for (String field : new String[]{"uniqueExecuted", "shortfall"}) value.add(field, JsonNull.INSTANCE);
            for (String field : new String[]{"duplicates", "missingCases"}) value.add(field, new JsonArray());
            return state(value, "running", "GAMETEST_NOT_COMPLETED");
        }
        if (liveTask.has("verifiedExport") && "succeeded".equals(taskState)) {
            boolean historical = "passed_historical_input".equals(string(object(liveTask, "verifiedExport"), "status", ""));
            value.addProperty("inputScope", historical ? "historical_at_export" : "current_at_export");
            return state(value, historical ? "passed_historical_input" : "passed_current_input", "GAMETEST_PASSED");
        }
        String broad = string(verification, "reasonCode", "GAMETEST_NOT_COMPLETED");
        String precise = string(coverage, "reasonCode", broad);
        if (!Set.of("GAMETEST_PASSED", "GAMETEST_NO_TESTS", "GAMETEST_REPORT_INVALID", "GAMETEST_FAILED").contains(broad)
                || ("GAMETEST_REPORT_INVALID".equals(broad) && "GAMETEST_PASSED".equals(precise)))
            return state(value, "report_unavailable", broad);
        if ("GAMETEST_FAILED".equals(broad)) return state(value, "business_failed", broad);
        if ("GAMETEST_DUPLICATE_TESTS".equals(precise)) return state(value, "duplicate", precise);
        if ("GAMETEST_REPORT_INVALID".equals(precise)) return state(value, "report_unavailable", precise);
        // Older valid records already contain a raw-derived business count; no English detail parsing is needed.
        if (value.get("uniqueExecuted").isJsonNull() && verification.has("acceptanceExecuted"))
            value.add("uniqueExecuted", verification.get("acceptanceExecuted").deepCopy());
        if (!value.get("uniqueExecuted").isJsonNull() && !value.get("minimumTests").isJsonNull()) {
            int unique = value.get("uniqueExecuted").getAsInt(), minimum = value.get("minimumTests").getAsInt();
            value.addProperty("shortfall", Math.max(0, minimum - unique));
            if (unique == 0) return state(value, "no_business", "GAMETEST_NO_TESTS");
        }
        if ("GAMETEST_REQUIRED_CASES_MISSING".equals(precise)) return state(value, "required_cases_missing", precise);
        if (Set.of("GAMETEST_INSUFFICIENT_TESTS", "GAMETEST_NO_TESTS").contains(precise))
            return state(value, "insufficient", "GAMETEST_INSUFFICIENT_TESTS");
        if ("passed".equals(string(verification, "status", "")) && "succeeded".equals(taskState)) {
            value.addProperty("inputScope", "current_at_completion");
            return state(value, "passed_current_input", "GAMETEST_PASSED");
        }
        return state(value, "report_unavailable", broad);
    }

    private static JsonObject object(JsonObject value, String field) {
        return value.has(field) && value.get(field).isJsonObject() ? value.getAsJsonObject(field) : new JsonObject();
    }

    private static String string(JsonObject value, String field, String fallback) {
        return value.has(field) && value.get(field).isJsonPrimitive() ? value.get(field).getAsString() : fallback;
    }

    private static JsonObject state(JsonObject value, String state, String code) {
        if (state.equals("report_unavailable") && !Set.of("GAMETEST_SOURCE_CHANGED", "GAMETEST_ARTIFACT_CHANGED", "GAMETEST_PROCESS_EXITED").contains(code)) {
            value.add("uniqueExecuted", JsonNull.INSTANCE);
            value.add("shortfall", JsonNull.INSTANCE);
        }
        value.addProperty("state", state);
        value.addProperty("reasonCode", code);
        return value;
    }
}
