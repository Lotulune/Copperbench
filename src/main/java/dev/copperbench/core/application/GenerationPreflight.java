package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;

/** Wire projection for a read-only source-safety observation, never a generation permit. */
public final class GenerationPreflight {
    public static final int MAX_CONFLICTS = 100;
    public static final int MAX_PATHS = 200;

    private GenerationPreflight() {}

    /** Unknown backends must not infer native-only or managed ownership. */
    public static JsonObject unknown(WorkspaceState state, String reasonCode) {
        JsonObject result = new JsonObject();
        result.addProperty("contractVersion", "1");
        result.addProperty("scope", "generation_source_safety");
        result.addProperty("revision", state.revision());
        result.add("generator", state.generator());
        result.addProperty("status", "unknown");
        result.addProperty("reasonCode", reasonCode);
        result.add("inputFingerprint", JsonNull.INSTANCE);
        result.addProperty("fingerprintScope", "workspace_inputs");
        result.add("managedPaths", new JsonArray());
        result.addProperty("managedPathCount", 0);
        result.addProperty("managedPathsTruncated", false);
        result.add("conflicts", new JsonArray());
        result.addProperty("conflictCount", 0);
        result.addProperty("conflictsTruncated", false);
        result.add("dependenciesRequired", JsonNull.INSTANCE);
        result.addProperty("executionRechecksInputs", true);
        JsonArray nextSteps = new JsonArray();
        nextSteps.add("inspect_source");
        nextSteps.add("keep_native_workflow");
        nextSteps.add("review_migration");
        result.add("nextSteps", nextSteps);
        return result;
    }
}
