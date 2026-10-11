package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import java.nio.file.Path;

/** Versioned read-only interpretation; never changes the legacy task or its acceptance decision. */
public final class TaskVerificationResult {
    private TaskVerificationResult() {}

    public static JsonObject inspect(JsonObject task, Path workspaceRoot) {
        JsonObject verification = task.has("verification") ? task.getAsJsonObject("verification") : new JsonObject();
        String state = text(task, "state", "unknown");
        String code = text(verification, "reasonCode", "VERIFICATION_UNAVAILABLE");
        Integer minimum = count(verification, "minimumTests"), executed = count(verification, "acceptanceExecuted");
        if (minimum != null && minimum < 1) minimum = null;
        Integer shortfall = minimum == null || executed == null ? null : Math.max(0, minimum - executed);
        String reason;
        if (task.has("resultObservation")) reason = "unconfirmed";
        else if (state.equals("running") || state.equals("queued")) reason = "running";
        else if (state.equals("cancelled")) reason = "cancelled";
        else if (code.equals("GAMETEST_REPORT_MISSING")) reason = "report_missing";
        else if (code.equals("GAMETEST_REPORT_INVALID")) reason = "report_invalid";
        else if (code.equals("GAMETEST_REPORT_STALE")) reason = "report_stale";
        else if (code.equals("GAMETEST_FAILED") || positive(verification, "failed")) reason = "business_failed";
        else if (code.equals("GAMETEST_NO_TESTS") && executed != null && executed == 0) reason = "no_business_tests";
        else if (code.equals("GAMETEST_NO_TESTS") && shortfall != null && shortfall > 0) reason = "insufficient_business_tests";
        else if (state.equals("succeeded") && text(verification, "status", "").equals("passed")) {
            reason = "passed_input_unconfirmed";
            if (workspaceRoot != null && verification.has("sourceSnapshot")) {
                try {
                    String expected = verification.getAsJsonObject("sourceSnapshot").get("sha256").getAsString();
                    if (expected.matches("[0-9a-f]{64}")) reason = expected.equals(
                            WorkspaceExecutionSnapshot.fingerprint(workspaceRoot, () -> false))
                            ? "passed_current_input" : "passed_historical_input";
                } catch (Exception ignored) { /* A read failure cannot establish current input identity. */ }
            }
        } else reason = verification.isEmpty() ? "unavailable" : "failed";

        if (java.util.Set.of("running", "cancelled", "unconfirmed", "report_missing", "report_invalid", "report_stale", "unavailable").contains(reason)
                || text(verification, "status", "").equals("pending")) {
            executed = null; shortfall = null;
        }
        JsonObject result = new JsonObject();
        result.addProperty("resultVersion", "1"); result.add("taskId", task.get("id").deepCopy());
        result.addProperty("taskState", state); result.addProperty("reasonCode", code); result.addProperty("subreason", reason);
        JsonObject coverage = new JsonObject();
        coverage.addProperty("minimumTests", minimum); coverage.addProperty("acceptanceExecuted", executed);
        coverage.addProperty("shortfall", shortfall); result.add("coverage", coverage);
        JsonObject message = new JsonObject();
        message.addProperty("key", "verification." + reason);
        message.addProperty("fallback", switch (reason) {
            case "running" -> "The task is running.";
            case "cancelled" -> "The task was cancelled.";
            case "unconfirmed" -> "The task outcome could not be confirmed after reconnecting.";
            case "report_missing" -> "The test report is missing.";
            case "report_invalid" -> "The test report is invalid.";
            case "report_stale" -> "The test report predates this run.";
            case "business_failed" -> "Test failures prevent acceptance. Inspect the failed cases.";
            case "no_business_tests" -> "No business tests executed. Skipped and framework cases do not count.";
            case "insufficient_business_tests" -> "{minimumTests} required; {acceptanceExecuted} business tests executed; {shortfall} missing.";
            case "passed_current_input" -> "The current input passed acceptance.";
            case "passed_historical_input" -> "Only the previously tested input passed acceptance. Verify the current input before exporting it.";
            case "passed_input_unconfirmed" -> "The tested input passed acceptance; its match to current input is unconfirmed.";
            case "unavailable" -> "No acceptance report is available for this task.";
            default -> "Acceptance did not pass ({reasonCode}).";
        });
        JsonObject args = coverage.deepCopy(); args.addProperty("reasonCode", code);
        message.add("args", args); result.add("message", message);
        return result;
    }

    private static String text(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
    private static Integer count(JsonObject object, String key) {
        try {
            if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) return null;
            int value = object.get(key).getAsBigDecimal().intValueExact();
            return value >= 0 ? value : null;
        } catch (RuntimeException exception) { return null; }
    }
    private static boolean positive(JsonObject object, String key) {
        Integer value = count(object, key); return value != null && value > 0;
    }
}
