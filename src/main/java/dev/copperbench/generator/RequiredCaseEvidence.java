package dev.copperbench.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Immutable run binding plus a separate result, outside the frozen v1.0 task projection. */
public final class RequiredCaseEvidence {
    public static final String TASK_FIELD = "requiredCaseEvidence";
    private RequiredCaseEvidence() {}

    static JsonObject freeze(Path snapshot, JsonObject task, UUID workspaceId, long revision,
            JsonObject source, JsonObject environment, RequiredCaseManifest manifest, Path host, Path report) throws IOException {
        JsonObject frozen = new JsonObject();
        frozen.addProperty("schemaVersion", "1");
        frozen.add("manifest", manifest.projection());
        frozen.add("taskId", task.get("id").deepCopy());
        frozen.addProperty("workspaceId", workspaceId.toString());
        frozen.addProperty("workspaceRevision", revision);
        frozen.add("sourceSnapshot", source.deepCopy());
        frozen.add("environment", environment.deepCopy());
        frozen.addProperty("hostPath", host.toAbsolutePath().normalize().toString());
        frozen.addProperty("reportPath", report.toAbsolutePath().normalize().toString());
        Path path = snapshot.resolveSibling("required-cases-frozen.json");
        WorkspaceExecutionSnapshot.rejectLinks(path);
        Files.writeString(path, frozen + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", "1");
        result.addProperty("frozenPath", path.toString());
        result.addProperty("frozenSha256", WorkspaceExecutionSnapshot.sha256(path));
        result.add("binding", frozen);
        result.addProperty("status", "pending");
        return result;
    }

    /** Removes only the internal evidence field; callers continue receiving the identical v1.0 shape. */
    public static JsonObject legacyTask(JsonObject recorded) {
        JsonObject task = recorded.deepCopy();
        task.remove(TASK_FIELD);
        task.remove(AcceptanceDiagnostics.TASK_FIELD);
        return task;
    }

    /** Reads a workspace-owned record after reconnect, with the same identity checks as legacy task recovery. */
    public static JsonObject recorded(Path root, UUID workspaceId, UUID taskId) {
        if (root == null) return legacyScope();
        return WorkspaceTaskRecords.read(root, workspaceId, taskId)
                .map(record -> record.getAsJsonObject("task"))
                .filter(task -> task.has(TASK_FIELD))
                .map(task -> task.getAsJsonObject(TASK_FIELD).deepCopy()).orElseGet(RequiredCaseEvidence::legacyScope);
    }

    private static JsonObject legacyScope() {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", "1");
        result.addProperty("status", "legacy_scope");
        result.addProperty("reasonCode", "REQUIRED_CASES_NOT_RECORDED");
        return result;
    }

    /** Rechecks the original binding and single raw report at the export boundary, including historical exports. */
    static GameTestReport.Assessment validate(JsonObject task, JsonObject verification, Path report) throws IOException {
        try {
            JsonObject evidence = task.getAsJsonObject(TASK_FIELD);
            if (!"1".equals(evidence.get("schemaVersion").getAsString())) throw new IllegalArgumentException("Unknown evidence version.");
            Path path = Path.of(evidence.get("frozenPath").getAsString()).toAbsolutePath().normalize();
            WorkspaceExecutionSnapshot.rejectLinks(path);
            if (Files.size(path) > 4 * 1024 * 1024 || !WorkspaceExecutionSnapshot.sha256(path).equals(evidence.get("frozenSha256").getAsString()))
                throw new IllegalArgumentException("Frozen binding is missing or changed.");
            JsonObject frozen = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!frozen.equals(evidence.get("binding")) || !frozen.get("taskId").equals(task.get("id")))
                throw new IllegalArgumentException("Binding differs from the recorded task.");
            for (String key : new String[]{"taskId", "workspaceId", "workspaceRevision", "sourceSnapshot", "environment", "reportPath"})
                if (!frozen.get(key).equals(verification.get(key))) throw new IllegalArgumentException("Binding differs from " + key + ".");
            if (!report.toAbsolutePath().normalize().getParent().getParent().equals(Path.of(frozen.get("hostPath").getAsString())))
                throw new IllegalArgumentException("The report is not owned by the frozen host.");
            Path artifact = Path.of(verification.get("artifactPath").getAsString()).toAbsolutePath().normalize();
            if (!artifact.getParent().equals(Path.of(frozen.get("hostPath").getAsString()).resolve("run/mods")))
                throw new IllegalArgumentException("The artifact is not owned by the frozen host.");
            RequiredCaseManifest manifest = RequiredCaseManifest.parse(frozen.getAsJsonObject("manifest"));
            if (manifest.minimumTests() != verification.get("minimumTests").getAsInt())
                throw new IllegalArgumentException("The threshold differs from the frozen manifest.");
            var result = GameTestReport.assess(report, java.time.Instant.parse(verification.get("startedAt").getAsString()), manifest);
            if (!result.coverage().equals(evidence.get("coverage")) || !"passed".equals(result.verification().get("status").getAsString()))
                throw new IllegalArgumentException("The raw report does not satisfy the recorded required cases.");
            return result;
        } catch (IOException | RuntimeException exception) {
            throw new VerifiedArtifactExporter.VerificationException("VERIFIED_REQUIRED_CASES_INVALID: " + exception.getMessage());
        }
    }
}
