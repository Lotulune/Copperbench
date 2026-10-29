package dev.copperbench.generator;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VerifiedExportLocationTest {
    @TempDir Path directory;
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    private Path exportDirectory(Path root) { return root.resolve(".copperbench/verified-exports/" + taskId); }
    private JsonObject record(Path root, Path exportDirectory) throws IOException {
        JsonObject receipt = new JsonObject(); receipt.addProperty("exportDirectory", exportDirectory.toString());
        receipt.addProperty("status", "passed_current_input"); receipt.addProperty("artifactSha256", "a".repeat(64));
        JsonObject task = new JsonObject(); task.addProperty("id", taskId.toString()); task.addProperty("kind", "export");
        task.addProperty("state", "succeeded"); task.add("verifiedExport", receipt);
        WorkspaceTaskRecords.save(root, workspaceId, task, List.of(), List.of());
        return task;
    }

    @Test void opensOnlyTheActualRecordedDirectoryForThisWorkspaceAndExportTask() throws Exception {
        Path root = Files.createDirectories(directory.resolve("workspace"));
        Path output = Files.createDirectories(exportDirectory(root)); Files.writeString(output.resolve("verification.json"), "{}");
        record(root, output);
        assertEquals(output.toRealPath(), VerifiedArtifactExporter.exportDirectory(root, workspaceId, taskId));
        assertThrows(IOException.class, () -> VerifiedArtifactExporter.exportDirectory(root, UUID.randomUUID(), taskId));
        assertThrows(IOException.class, () -> VerifiedArtifactExporter.exportDirectory(root, workspaceId, UUID.randomUUID()));
    }

    @Test void forgedDirectoryOutsideTheScopedExportIsRejected() throws Exception {
        Path root = Files.createDirectories(directory.resolve("workspace"));
        Path outside = Files.createDirectories(directory.resolve("other-output")); Files.writeString(outside.resolve("verification.json"), "{}");
        record(root, outside);
        assertThrows(IOException.class, () -> VerifiedArtifactExporter.exportDirectory(root, workspaceId, taskId));
    }

    @Test void ordinaryExportsAndMissingReceiptsDoNotGainAnExactExportFolder() throws Exception {
        Path root = Files.createDirectories(directory.resolve("workspace"));
        Path output = Files.createDirectories(exportDirectory(root));
        JsonObject task = record(root, output);
        assertThrows(IOException.class, () -> VerifiedArtifactExporter.exportDirectory(root, workspaceId, taskId));
        Files.writeString(output.resolve("verification.json"), "{}"); task.remove("verifiedExport");
        WorkspaceTaskRecords.save(root, workspaceId, task, List.of(), List.of());
        assertThrows(IOException.class, () -> VerifiedArtifactExporter.exportDirectory(root, workspaceId, taskId));
    }

    @Test void redirectedExportDirectoriesAreRejected() throws Exception {
        Path root = Files.createDirectories(directory.resolve("workspace"));
        Path external = Files.createDirectories(directory.resolve("external")); Files.writeString(external.resolve("verification.json"), "{}");
        Path output = exportDirectory(root); Files.createDirectories(output.getParent());
        try { Files.createSymbolicLink(output, external); }
        catch (IOException | UnsupportedOperationException exception) { assumeTrue(false, "Symbolic links unavailable: " + exception.getMessage()); }
        try {
            record(root, output);
            assertThrows(IOException.class, () -> VerifiedArtifactExporter.exportDirectory(root, workspaceId, taskId));
        } finally { Files.deleteIfExists(output); }
    }
}
