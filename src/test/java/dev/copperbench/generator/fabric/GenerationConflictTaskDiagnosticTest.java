package dev.copperbench.generator.fabric;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.ConflictReason;
import dev.copperbench.core.application.WorkspaceTaskGateway.GenerationPreparationException.SourceConflict;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GenerationConflictTaskDiagnosticTest {
    @TempDir Path root;

    @Test void multipleConflictsExposeSafePathsReasonsAndOnlyAvailableSourceActions() throws Exception {
        var state = Fabric1211GoldenWorkspace.create();
        var store = new RevisionedWorkspaceStore(); store.register(state);
        String javaPath = "src/main/java/dev/coppertrails/Handwritten.java";
        String resourcePath = "src/main/resources/fabric.mod.json";
        Files.createDirectories(root.resolve(javaPath).getParent());
        Files.writeString(root.resolve(javaPath), "// Keep this handwritten file exactly as it is.\n");
        Files.createDirectories(root.resolve(resourcePath).getParent());
        Files.writeString(root.resolve(resourcePath), "{\"id\":\"handwritten\"}\n");
        byte[] javaBefore = Files.readAllBytes(root.resolve(javaPath)), resourceBefore = Files.readAllBytes(root.resolve(resourcePath));
        UUID taskId;
        try (var tasks = gateway(store)) {
            tasks.setGenerationPreparation((snapshot, folder, operation, output) -> {
                throw new GenerationPreparationException(List.of(
                        new SourceConflict(javaPath, ConflictReason.UNOWNED_BASE_FILE),
                        new SourceConflict(resourcePath, ConflictReason.SOURCE_CHANGED)),
                        new IOException("private-token at /private/secret/source.java"));
            });
            taskId = UUID.fromString(tasks.start(state.id(), Operation.GENERATE_WORKSPACE, new JsonObject()).get("id").getAsString());
            assertEquals("failed", await(tasks, state.id(), taskId).get("state").getAsString());
            var diagnostics = tasks.diagnostics(state.id(), taskId);
            assertEquals(2, diagnostics.size());
            var source = diagnostics.getFirst();
            assertEquals("GENERATION_SOURCE_CONFLICT", source.get("code").getAsString());
            assertEquals("/" + javaPath, source.get("path").getAsString());
            var args = source.getAsJsonObject("message").getAsJsonObject("args");
            assertEquals(javaPath, args.get("sourcePath").getAsString());
            assertEquals(javaPath, args.get("displaySourcePath").getAsString());
            assertTrue(tasks.logs(state.id(), taskId).toString().contains(javaPath));
            assertEquals("UNOWNED_BASE_FILE", args.get("reasonCode").getAsString());
            assertEquals("not_owned_by_generator", args.get("ownership").getAsString());
            assertTrue(source.getAsJsonArray("actions").asList().stream().map(value -> value.getAsJsonObject())
                    .anyMatch(action -> action.get("kind").getAsString().equals("open_source")
                            && action.get("target").getAsString().equals("/" + javaPath)));
            assertEquals(new String(javaBefore, java.nio.charset.StandardCharsets.UTF_8),
                    tasks.sourcePreview(state.id(), taskId, "/" + javaPath).orElseThrow().get("content").getAsString());
            assertThrows(IllegalArgumentException.class,
                    () -> tasks.sourcePreview(state.id(), taskId, "/src/main/java/dev/coppertrails/Unrelated.java"));
            var resource = diagnostics.get(1);
            assertEquals("/" + resourcePath, resource.get("path").getAsString());
            assertEquals("recorded_input", resource.getAsJsonObject("message").getAsJsonObject("args").get("ownership").getAsString());
            assertTrue(resource.getAsJsonArray("actions").asList().stream()
                    .allMatch(value -> value.getAsJsonObject().get("kind").getAsString().equals("open_logs")));
            assertSafePublicOutput(diagnostics.toString() + tasks.logs(state.id(), taskId));
            assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
            assertArrayEquals(javaBefore, Files.readAllBytes(root.resolve(javaPath)));
            assertArrayEquals(resourceBefore, Files.readAllBytes(root.resolve(resourcePath)));
        }
        try (var reopened = gateway(store)) {
            var restored = reopened.diagnostics(state.id(), taskId);
            assertEquals(2, restored.size());
            assertEquals("/" + javaPath, restored.getFirst().get("path").getAsString());
            assertEquals("UNOWNED_BASE_FILE", restored.getFirst().getAsJsonObject("message")
                    .getAsJsonObject("args").get("reasonCode").getAsString());
        }
    }

    @Test void legacyAndUnlocatedConflictsNeverExposeCauseOrMessageAsAPath() throws Exception {
        var state = Fabric1211GoldenWorkspace.create();
        var store = new RevisionedWorkspaceStore(); store.register(state);
        var cause = new IOException("private-token at /private/secret/source.java");
        for (var failure : List.of(
                new GenerationPreparationException("GENERATION_SOURCE_CONFLICT", cause.getMessage(), cause),
                new GenerationPreparationException("GENERATION_SOURCE_CONFLICT",
                        "GENERATION_SOURCE_CONFLICT: " + cause.getMessage(), cause),
                new GenerationPreparationException(List.of(new SourceConflict(null, ConflictReason.PATH_OUTSIDE_WORKSPACE)), cause))) {
            try (var tasks = gateway(store)) {
                tasks.setGenerationPreparation((snapshot, folder, operation, output) -> { throw failure; });
                var taskId = UUID.fromString(tasks.start(state.id(), Operation.GENERATE_WORKSPACE, new JsonObject()).get("id").getAsString());
                assertEquals("failed", await(tasks, state.id(), taskId).get("state").getAsString());
                var diagnostic = tasks.diagnostics(state.id(), taskId).getFirst();
                assertEquals("GENERATION_SOURCE_CONFLICT", diagnostic.get("code").getAsString());
                assertTrue(diagnostic.get("path").isJsonNull());
                assertFalse(diagnostic.getAsJsonObject("message").getAsJsonObject("args").has("sourcePath"));
                assertTrue(diagnostic.getAsJsonArray("actions").asList().stream()
                        .allMatch(value -> value.getAsJsonObject().get("kind").getAsString().equals("open_logs")));
                assertSafePublicOutput(diagnostic.toString() + tasks.logs(state.id(), taskId));
                assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
            }
        }
    }

    @Test void boundedDisplayPreservesStructuredLocationWithoutInventingSourceActions() throws Exception {
        var state = Fabric1211GoldenWorkspace.create();
        var store = new RevisionedWorkspaceStore(); store.register(state);
        String relative = "src/铜仪\u202e\u2028/" + "x".repeat(600) + ".java";
        try (var tasks = gateway(store)) {
            tasks.setGenerationPreparation((snapshot, folder, operation, output) -> {
                throw new GenerationPreparationException(List.of(
                        new SourceConflict(relative, ConflictReason.SOURCE_CHANGED)),
                        new IOException("private-token at /private/secret/source.java"));
            });
            var taskId = UUID.fromString(tasks.start(state.id(), Operation.GENERATE_WORKSPACE, new JsonObject()).get("id").getAsString());
            assertEquals("failed", await(tasks, state.id(), taskId).get("state").getAsString());
            var diagnostic = tasks.diagnostics(state.id(), taskId).getFirst();
            var args = diagnostic.getAsJsonObject("message").getAsJsonObject("args");
            assertEquals(relative, args.get("sourcePath").getAsString());
            assertEquals("/" + relative, diagnostic.get("path").getAsString());
            String display = args.get("displaySourcePath").getAsString();
            assertTrue(display.endsWith(" [truncated]"));
            assertTrue(display.codePointCount(0, display.length()) <= 524);
            assertFalse(display.contains("\u202e"));
            assertFalse(display.contains("\u2028"));
            assertEquals("SOURCE_CHANGED", args.get("reasonCode").getAsString());
            assertTrue(diagnostic.getAsJsonArray("actions").asList().stream()
                    .allMatch(value -> value.getAsJsonObject().get("kind").getAsString().equals("open_logs")));
            assertSafePublicOutput(diagnostic.toString() + tasks.logs(state.id(), taskId));
            assertFalse(tasks.logs(state.id(), taskId).toString().contains("x".repeat(600)));
        }
    }

    private Fabric1211WorkspaceTaskGateway gateway(RevisionedWorkspaceStore store) {
        return new Fabric1211WorkspaceTaskGateway(store, ignored -> root, Path.of(".").toAbsolutePath(),
                Clock.systemUTC(), UUID::randomUUID, (folder, arguments, timeout, output) -> {
                    throw new AssertionError("A source conflict must stop before Gradle runs");
                });
    }

    private static JsonObject await(Fabric1211WorkspaceTaskGateway tasks, UUID workspaceId, UUID taskId) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var task = tasks.find(workspaceId, taskId).orElseThrow();
            if (List.of("succeeded", "failed", "cancelled").contains(task.get("state").getAsString())) return task;
            Thread.sleep(10);
        }
        throw new AssertionError("Generation conflict task did not terminate");
    }

    private void assertSafePublicOutput(String output) {
        assertFalse(output.contains("private-token"), output);
        assertFalse(output.contains("/private/secret"), output);
        assertFalse(output.contains(root.toAbsolutePath().toString()), output);
    }
}
