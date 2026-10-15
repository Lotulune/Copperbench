package dev.copperbench.generator;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Process fixtures declare their actual output just as the selected Gradle archive task does. */
public final class GradleArchiveOutputFixture {
    private GradleArchiveOutputFixture() {}

    public static void record(List<String> arguments, Path artifact) throws Exception {
        int option = arguments.indexOf("--init-script");
        if (option < 0) return;
        Path script = Path.of(arguments.get(option + 1));
        JsonObject receipt = new JsonObject(); receipt.addProperty("schemaVersion", "1.0");
        receipt.addProperty("taskId", script.getParent().getFileName().toString());
        receipt.addProperty("archiveTask", ":jar");
        receipt.addProperty("artifactPath", artifact.toAbsolutePath().normalize().toString());
        receipt.addProperty("sha256", WorkspaceExecutionSnapshot.sha256(artifact));
        Files.writeString(script.resolveSibling("archive-output.json"), receipt + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
