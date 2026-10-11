package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Small delivery manifest; hashes establish internal consistency, never external provenance. */
final class PortableEvidenceBundle {
    private PortableEvidenceBundle() {}

    static void write(Path directory, JsonObject task, JsonObject portable) throws IOException {
        JsonObject identity = new JsonObject();
        identity.addProperty("identityVersion", "1"); identity.add("taskId", task.get("id").deepCopy());
        JsonObject application = new JsonObject();
        JsonObject verification = task.getAsJsonObject("verification");
        if (verification.has("environment") && verification.get("environment").isJsonObject()) {
            JsonObject environment = verification.getAsJsonObject("environment");
            if (environment.has("application") && environment.get("application").isJsonObject()) {
                JsonObject observed = environment.getAsJsonObject("application");
                for (String key : new String[]{"sourceState", "applicationSha256", "version", "buildDate", "snapshot",
                        "javaVersion", "javaVendor", "mcpSdkVersion"}) {
                    if (observed.has(key) && observed.get(key).isJsonPrimitive()
                            && observed.getAsJsonPrimitive(key).isString() && observed.get(key).getAsString().length() <= 256)
                        application.add(key, observed.get(key).deepCopy());
                }
            }
        }
        identity.addProperty("source", application.has("sourceState") ? "acceptance_record" : "unavailable");
        identity.add("application", application);
        writeJson(directory.resolve("application-identity.json"), identity);
        portable.addProperty("applicationIdentitySha256", WorkspaceExecutionSnapshot.sha256(directory.resolve("application-identity.json")));
        writeJson(directory.resolve("verification.json"), portable);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("bundleVersion", "1");
        for (String key : new String[]{"taskId", "status", "sourceSha256", "artifactSha256", "reportSha256", "mode", "minimumTests", "applicationIdentitySha256"})
            manifest.add(key, portable.get(key).deepCopy());
        JsonArray files = new JsonArray();
        for (String name : new String[]{"verified-mod.jar", "gametest-results.xml", "verification.json", "application-identity.json"}) {
            Path file = directory.resolve(name);
            long limit = switch (name) {
                case "verified-mod.jar" -> 256L * 1024 * 1024;
                case "gametest-results.xml" -> 8L * 1024 * 1024;
                case "verification.json" -> 1024L * 1024;
                default -> 64L * 1024;
            };
            if (Files.size(file) > limit) throw new VerifiedArtifactExporter.VerificationException(
                    "VERIFIED_BUNDLE_TOO_LARGE: " + name + " exceeds the portable bundle limit.");
            JsonObject item = new JsonObject(); item.addProperty("path", name);
            item.addProperty("size", Files.size(file)); item.addProperty("sha256", WorkspaceExecutionSnapshot.sha256(file));
            files.add(item);
        }
        manifest.add("files", files);
        writeJson(directory.resolve("manifest.json"), manifest);
    }

    private static void writeJson(Path path, JsonObject value) throws IOException {
        Files.writeString(path, value + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
