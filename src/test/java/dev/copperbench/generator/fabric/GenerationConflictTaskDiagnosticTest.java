package dev.copperbench.generator.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GenerationConflictTaskDiagnosticTest {
    @TempDir Path root;

    @Test void conflictsKeepTheirPathsAndOnlyReadableJavaSourcesOfferPreview() throws Exception {
        String javaPath = "src/main/java/dev/coppertrails/Handwritten.java";
        String resourcePath = "src/main/resources/fabric.mod.json";
        String original = "// Preserve this handwritten source.\n";
        write(javaPath, original);
        write(resourcePath, "{\"id\":\"manual\"}\n");
        write("src/main/java/Unrelated.java", "// Must not be readable through this diagnostic\n");
        var state = Fabric1211GoldenWorkspace.create();
        var store = new RevisionedWorkspaceStore(); store.register(state);
        JsonObject details = conflicts(javaPath, resourcePath, null);
        UUID taskId;
        try (var tasks = gateway(store)) {
            taskId = reject(tasks, state.id(), details);
            var diagnostics = tasks.diagnostics(state.id(), taskId);
            assertEquals(3, diagnostics.size());
            var java = diagnostics.getFirst();
            assertEquals("/" + javaPath, java.get("path").getAsString());
            assertEquals("GENERATION_SOURCE_CONFLICT", java.get("code").getAsString());
            var args = java.getAsJsonObject("message").getAsJsonObject("args");
            assertEquals(details.get("conflicts"), args.get("conflicts"));
            assertEquals(javaPath, args.get("sourcePath").getAsString());
            assertEquals("SOURCE_CHANGED", args.get("reasonCode").getAsString());
            assertTrue(hasSourceAction(java));
            assertEquals(original, tasks.sourcePreview(state.id(), taskId, "/" + javaPath)
                    .orElseThrow().get("content").getAsString());
            assertThrows(IllegalArgumentException.class,
                    () -> tasks.sourcePreview(state.id(), taskId, "/src/main/java/Unrelated.java"));
            assertEquals("/" + resourcePath, diagnostics.get(1).get("path").getAsString());
            assertFalse(hasSourceAction(diagnostics.get(1)));
            assertTrue(diagnostics.get(2).get("path").isJsonNull());
            assertFalse(hasSourceAction(diagnostics.get(2)));
            assertEquals(original, Files.readString(root.resolve(javaPath)));
            assertEquals("{\"id\":\"manual\"}\n", Files.readString(root.resolve(resourcePath)));
            assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
        }
        try (var reopened = gateway(store)) {
            var restored = reopened.diagnostics(state.id(), taskId);
            assertEquals(3, restored.size());
            assertEquals("/" + javaPath, restored.getFirst().get("path").getAsString());
        }
    }

    @Test void unsafeAndMissingPathsNeverBecomeSourceActions() throws Exception {
        var state = Fabric1211GoldenWorkspace.create();
        var store = new RevisionedWorkspaceStore(); store.register(state);
        String absent = "src/main/java/Absent.java";
        String oversized = "src/main/java/Oversized.java";
        write(oversized, "x".repeat(1024 * 1024));
        try (var tasks = gateway(store)) {
            var id = reject(tasks, state.id(), conflicts("../outside.java", "/private/secret.java",
                    "C:/private/secret.java", "\\\\server\\private\\secret.java", absent, oversized));
            var diagnostics = tasks.diagnostics(state.id(), id);
            assertEquals(6, diagnostics.size());
            for (int index = 0; index < 4; index++) assertTrue(diagnostics.get(index).get("path").isJsonNull());
            for (var diagnostic : diagnostics) assertFalse(hasSourceAction(diagnostic));
            assertTrue(tasks.sourcePreview(state.id(), id, "/" + absent).isEmpty());
            assertTrue(tasks.sourcePreview(state.id(), id, "/" + oversized).isEmpty());
        }
    }

    @Test void unavailableLegacyLocationDoesNotInventASourceTarget() throws Exception {
        var state = Fabric1211GoldenWorkspace.create();
        var store = new RevisionedWorkspaceStore(); store.register(state);
        try (var tasks = gateway(store)) {
            var id = reject(tasks, state.id(), new JsonObject());
            var diagnostic = tasks.diagnostics(state.id(), id).getFirst();
            assertTrue(diagnostic.get("path").isJsonNull());
            assertFalse(hasSourceAction(diagnostic));
        }
    }

    @Test void displayPathIsBoundedWhileStructuredPathRemainsExact() throws Exception {
        String relative = "src/field\u202e\u2028/" + "x".repeat(600) + ".java";
        var state = Fabric1211GoldenWorkspace.create();
        var store = new RevisionedWorkspaceStore(); store.register(state);
        try (var tasks = gateway(store)) {
            var id = reject(tasks, state.id(), conflicts(relative));
            var diagnostic = tasks.diagnostics(state.id(), id).getFirst();
            assertEquals("/" + relative, diagnostic.get("path").getAsString());
            var args = diagnostic.getAsJsonObject("message").getAsJsonObject("args");
            assertEquals(relative, args.get("sourcePath").getAsString());
            String display = args.get("displaySourcePath").getAsString();
            assertTrue(display.endsWith(" [truncated]"));
            assertTrue(display.codePointCount(0, display.length()) <= 524);
            assertFalse(display.contains("\u202e"));
            assertFalse(display.contains("\u2028"));
            assertFalse(hasSourceAction(diagnostic));
        }
    }

    private void write(String relative, String text) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    private Fabric1211WorkspaceTaskGateway gateway(RevisionedWorkspaceStore store) {
        return new Fabric1211WorkspaceTaskGateway(store, ignored -> root, Path.of("."),
                Clock.systemUTC(), UUID::randomUUID, (folder, arguments, timeout, output) -> {
                    throw new AssertionError("A source conflict must stop before Gradle runs");
                });
    }

    private static UUID reject(Fabric1211WorkspaceTaskGateway tasks, UUID workspaceId, JsonObject details) throws Exception {
        tasks.setGenerationPreparation((snapshot, folder, operation, output) -> {
            throw new GenerationPreparationException("GENERATION_SOURCE_CONFLICT", "Source files changed.", null, details);
        });
        UUID id = UUID.fromString(tasks.start(workspaceId, Operation.GENERATE_WORKSPACE, new JsonObject()).get("id").getAsString());
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var task = tasks.find(workspaceId, id).orElseThrow();
            if (List.of("succeeded", "failed", "cancelled").contains(task.get("state").getAsString())) {
                assertEquals("failed", task.get("state").getAsString());
                return id;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Generation conflict task did not terminate");
    }

    private static JsonObject conflicts(String... paths) {
        JsonArray conflicts = new JsonArray();
        for (String path : paths) {
            JsonObject conflict = new JsonObject();
            if (path == null) conflict.add("relativePath", JsonNull.INSTANCE);
            else conflict.addProperty("relativePath", path);
            conflict.addProperty("reasonCode", "SOURCE_CHANGED");
            conflict.addProperty("expectedOwnership", "generated");
            conflict.addProperty("observedOwnership", "unknown");
            conflicts.add(conflict);
        }
        JsonObject details = new JsonObject();
        details.add("conflicts", conflicts);
        details.addProperty("conflictCount", paths.length);
        details.addProperty("conflictsTruncated", false);
        return details;
    }

    private static boolean hasSourceAction(JsonObject diagnostic) {
        return diagnostic.getAsJsonArray("actions").asList().stream()
                .anyMatch(action -> action.getAsJsonObject().get("kind").getAsString().equals("open_source"));
    }
}
