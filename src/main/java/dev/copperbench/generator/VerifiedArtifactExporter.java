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
        validateAcceptance(verification, report);
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
        if (verification.has("environment")) for (String key : new String[]{"generatorId", "loader", "minecraftVersion", "loaderVersion", "javaVersion"})
            if (verification.getAsJsonObject("environment").has(key)) environment.add(key, verification.getAsJsonObject("environment").get(key).deepCopy());
        receipt.add("environment", environment);
        if (verification.has("sourceSnapshot")) receipt.add("sourceSha256", verification.getAsJsonObject("sourceSnapshot").get("sha256").deepCopy());
        Files.writeString(destination.resolve("verification.json"), receipt.toString() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        JsonObject result = receipt.deepCopy();
        result.addProperty("exportDirectory", destination.toString());
        return result;
    }

    /** Revalidate raw cases at the delivery boundary, including recovered task records. */
    private static void validateAcceptance(JsonObject record, Path report) throws VerificationException {
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
