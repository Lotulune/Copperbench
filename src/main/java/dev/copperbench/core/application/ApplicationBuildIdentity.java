package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;

/** Facts about the running class source; a source checkout is never presented as a verified release commit. */
public final class ApplicationBuildIdentity {
    private ApplicationBuildIdentity() {}
    public static JsonObject inspect() {
        JsonObject result = new JsonObject();
        result.addProperty("javaVersion", System.getProperty("java.runtime.version"));
        result.addProperty("javaVendor", System.getProperty("java.vendor"));
        result.addProperty("mcpSdkVersion", io.modelcontextprotocol.client.McpClient.class.getPackage().getImplementationVersion());
        result.addProperty("sourceState", "development_or_unverified");
        result.addProperty("version", ApplicationBuildIdentity.class.getPackage().getImplementationVersion());
        try {
            Path source = Path.of(ApplicationBuildIdentity.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(source)) {
                result.addProperty("sourceState", "packaged_binary");
                result.addProperty("applicationSha256", dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(source));
                try (var jar = new java.util.jar.JarFile(source.toFile())) {
                    var manifest = jar.getManifest();
                    if (manifest != null) {
                        var attributes = manifest.getMainAttributes();
                        String version = attributes.getValue("Product-Version");
                        if (version != null) result.addProperty("version", version);
                        result.addProperty("buildDate", attributes.getValue("Build-Date"));
                        result.addProperty("snapshot", attributes.getValue("Build-Is-Snapshot"));
                    }
                }
            }
        } catch (Exception ignored) { /* Keep the unknown-source state rather than inventing a commit. */ }
        return result;
    }
}
