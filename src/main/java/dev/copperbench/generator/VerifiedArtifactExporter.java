package dev.copperbench.generator;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Exports the exact artifact selected by an immutable acceptance record, never the newest build/libs file. */
public final class VerifiedArtifactExporter {
    private VerifiedArtifactExporter() {}
    public static final class VerificationException extends IOException {
        public VerificationException(String message) { super(message); }
        public String code() { return getMessage().split(":", 2)[0]; }
    }

    public static JsonObject export(Path root, JsonObject task, UUID exportId, boolean allowHistorical) throws IOException {
        if (!task.has("verification") || !"succeeded".equals(task.get("state").getAsString()))
            throw new VerificationException("VERIFIED_ARTIFACT_UNAVAILABLE: Select a completed acceptance task.");
        JsonObject verification = task.getAsJsonObject("verification");
        if (!"passed".equals(verification.get("status").getAsString()) || !verification.has("artifactPath")
                || !verification.has("artifactSha256") || !verification.has("reportSha256"))
            throw new VerificationException("VERIFIED_ARTIFACT_UNAVAILABLE: A passed packaged-JAR record with artifact and report hashes is required.");
        Path artifact = checkedFile(verification, "artifactPath", "artifactSha256");
        Path report = checkedFile(verification, "reportPath", "reportSha256");
        JsonObject acceptance = validateAcceptance(verification, report);
        if (task.has(RequiredCaseEvidence.TASK_FIELD)) RequiredCaseEvidence.validate(task, verification, report);
        else if ((verification.has("environment") && verification.getAsJsonObject("environment").has("minecraftVersion")
                && "26.3".equals(verification.getAsJsonObject("environment").get("minecraftVersion").getAsString()))
                || (verification.has("verificationPath") && Files.exists(Path.of(verification.get("verificationPath").getAsString())
                        .resolveSibling("required-cases-frozen.json"))))
            throw new VerificationException("VERIFIED_REQUIRED_CASES_MISSING: This task cannot be promoted from legacy evidence.");
        boolean current = verification.has("sourceSnapshot") && verification.getAsJsonObject("sourceSnapshot").get("sha256")
                .getAsString().equals(WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        if (!current && !allowHistorical) throw new VerificationException("VERIFICATION_INPUT_CHANGED: Explicitly select historical export or rerun acceptance.");
        Path destination = root.toAbsolutePath().normalize().resolve(".copperbench/verified-exports/" + exportId);
        WorkspaceExecutionSnapshot.rejectLinks(destination);
        Files.createDirectories(destination.getParent());
        Files.createDirectory(destination);
        // A new export directory cannot overwrite another delivery. A receipt is written only after verification.
        Path copiedJar = destination.resolve("verified-mod.jar");
        Path copiedReport = destination.resolve("gametest-results.xml");
        Files.copy(artifact, copiedJar); Files.copy(report, copiedReport);
        if (!WorkspaceExecutionSnapshot.sha256(copiedJar).equals(verification.get("artifactSha256").getAsString())
                || !WorkspaceExecutionSnapshot.sha256(copiedReport).equals(verification.get("reportSha256").getAsString()))
            throw new VerificationException("VERIFIED_EXPORT_CHANGED: Source bytes changed during copying; no trusted receipt was created.");
        current = current && verification.getAsJsonObject("sourceSnapshot").get("sha256").getAsString()
                .equals(WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        if (!current && !allowHistorical) throw new VerificationException("VERIFICATION_INPUT_CHANGED: Inputs changed during export; no trusted receipt was created.");
        JsonObject receipt = new JsonObject();
        receipt.addProperty("schemaVersion", "1.0");
        receipt.add("taskId", task.get("id").deepCopy());
        receipt.addProperty("status", current ? "passed_current_input" : "passed_historical_input");
        receipt.addProperty("scope", "Only the acceptance cases listed in gametest-results.xml; client behavior is not implied.");
        receipt.addProperty("artifactPath", "verified-mod.jar");
        receipt.addProperty("reportPath", "gametest-results.xml");
        for (String key : new String[]{"artifactSha256", "reportSha256", "passed", "failed", "skipped", "acceptanceExecuted"})
            if (verification.has(key)) receipt.add(key, verification.get(key).deepCopy());
        JsonObject environment = new JsonObject();
        // Preserve observed versions only. Do not substitute the export host's JBR
        // for the workspace Java release, or copy paths/credentials from the environment.
        if (verification.has("environment") && verification.get("environment").isJsonObject()) {
            JsonObject observed = verification.getAsJsonObject("environment");
            for (String key : new String[]{"generatorId", "loader", "minecraftVersion", "loaderVersion",
                    "javaVersion", "javaRelease", "fabricApiVersion", "pluginId", "pluginVersion"})
                if (observed.has(key) && observed.get(key).isJsonPrimitive())
                    environment.add(key, observed.get(key).deepCopy());
        }
        receipt.add("environment", environment);
        if (verification.has("sourceSnapshot")) receipt.add("sourceSha256", verification.getAsJsonObject("sourceSnapshot").get("sha256").deepCopy());
        // Enrich the portable file, not the strict v1.0 verifiedExport wire envelope.
        JsonObject portable = receipt.deepCopy();
        portable.addProperty("portableEvidenceVersion", "1");
        portable.addProperty("environmentSource", "acceptance_record");
        for (String key : new String[]{"mode", "minimumTests", "startedAt", "processExitCode"})
            portable.add(key, verification.get(key).deepCopy());
        JsonObject coverage = new JsonObject();
        int required = verification.get("minimumTests").getAsInt();
        int observed = acceptance.get("acceptanceExecuted").getAsInt();
        coverage.addProperty("minimumTests", required);
        coverage.addProperty("acceptanceExecuted", observed);
        coverage.addProperty("shortfall", Math.max(0, required - observed));
        coverage.addProperty("status", "sufficient"); // Raw acceptance has already passed validation.
        portable.add("coverage", coverage);
        if (task.has(RequiredCaseEvidence.TASK_FIELD)) {
            JsonObject recorded = task.getAsJsonObject(RequiredCaseEvidence.TASK_FIELD);
            JsonObject requiredEvidence = new JsonObject();
            requiredEvidence.addProperty("schemaVersion", "1");
            requiredEvidence.add("manifest", recorded.getAsJsonObject("binding").get("manifest").deepCopy());
            requiredEvidence.add("coverage", recorded.get("coverage").deepCopy());
            JsonObject binding = new JsonObject();
            JsonObject original = recorded.getAsJsonObject("binding");
            for (String key : new String[]{"taskId", "workspaceId", "workspaceRevision"}) binding.add(key, original.get(key).deepCopy());
            binding.add("sourceSha256", original.getAsJsonObject("sourceSnapshot").get("sha256").deepCopy());
            binding.add("environment", environment.deepCopy());
            binding.add("frozenSha256", recorded.get("frozenSha256").deepCopy());
            requiredEvidence.add("binding", binding);
            portable.add("requiredCases", requiredEvidence);
        }
        Files.writeString(destination.resolve("verification.json"), portable.toString() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        JsonObject result = receipt.deepCopy();
        result.addProperty("exportDirectory", destination.toString());
        return result;
    }

    /** Revalidate raw cases at the delivery boundary, including recovered task records. */
    private static JsonObject validateAcceptance(JsonObject record, Path report) throws VerificationException {
        try {
            if (!"packaged_jar".equals(record.get("mode").getAsString())
                    || record.get("processExitCode").getAsBigDecimal().signum() != 0)
                throw new IllegalArgumentException("A successful packaged-JAR process is required.");
            int minimum = record.get("minimumTests").getAsBigDecimal().intValueExact();
            if (minimum < 1 || minimum > 10_000) throw new IllegalArgumentException("Invalid minimumTests.");
            if (!record.getAsJsonObject("sourceSnapshot").get("sha256").getAsString().matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Original input identity is required, including for historical export.");
            JsonObject parsed = GameTestReport.read(report, java.time.Instant.parse(record.get("startedAt").getAsString()), minimum);
            if (!"passed".equals(parsed.get("status").getAsString()))
                throw new VerificationException("VERIFIED_ACCEPTANCE_INVALID: " + parsed.get("reasonCode").getAsString());
            for (String key : new String[]{"reportSha256", "discovered", "executed", "passed", "failed", "skipped", "frameworkTests", "acceptanceExecuted", "cases"}) {
                if (!parsed.get(key).equals(record.get(key)))
                    throw new VerificationException("VERIFIED_ACCEPTANCE_INVALID: Report differs from recorded " + key + ".");
            }
            return parsed;
        } catch (VerificationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VerificationException("VERIFIED_ACCEPTANCE_INVALID: The acceptance record is incomplete or malformed.");
        }
    }

    private static Path checkedFile(JsonObject record, String pathKey, String hashKey) throws IOException {
        if (!record.has(pathKey)) throw new VerificationException("VERIFIED_EVIDENCE_MISSING: " + pathKey);
        Path path = Path.of(record.get(pathKey).getAsString()).toAbsolutePath().normalize();
        WorkspaceExecutionSnapshot.rejectLinks(path);
        if (!Files.isRegularFile(path) || !WorkspaceExecutionSnapshot.sha256(path).equals(record.get(hashKey).getAsString()))
            throw new VerificationException("VERIFIED_EVIDENCE_CHANGED: " + pathKey + " is missing or has different bytes.");
        return path;
    }
}
