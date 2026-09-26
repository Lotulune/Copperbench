package dev.copperbench.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.generator.ResourceDependencyCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DependencyResourceIndexTest {
    @TempDir Path temporary;

    private Path workspace() throws Exception {
        Path root = temporary.resolve("workspace"); Files.createDirectories(root);
        Files.createDirectories(root.resolve(".mcreator"));
        Files.writeString(root.resolve("test.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}");
        write(root, "src/main/resources/assets/probe/models/block/child.json", "{\"parent\":\"dep:block/base\"}");
        return root;
    }

    private Path archive(String name, String parent) throws Exception {
        Path jar = temporary.resolve(name + ".jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var entry : Map.of("fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"" + name + "\",\"version\":\"1.0.0\"}",
                    "assets/dep/models/block/base.json", parent,
                    "assets/dep/textures/block/surface.png", "fixture").entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return jar;
    }

    private void capture(Path root, List<Path> artifacts, String configuration) throws Exception {
        var command = new ArrayList<>(List.of("build"));
        var ticket = ResourceDependencyCapture.prepare(root, command, ignored -> {});
        assertNotNull(ticket);
        assertTrue(command.contains("--init-script"));
        JsonObject resolved = new JsonObject(); resolved.addProperty("complete", true);
        resolved.add("configurations", new JsonArray());
        JsonArray files = new JsonArray();
        for (Path artifact : artifacts) {
            JsonObject entry = new JsonObject(); entry.addProperty("path", artifact.toString());
            entry.addProperty("configuration", configuration); entry.addProperty("component", "org.example:" + artifact.getFileName().toString().replace(".jar", "") + ":1.0.0");
            files.add(entry);
        }
        resolved.add("artifacts", files);
        Files.writeString(root.resolve(ticket.capturePath()), resolved.toString());
        ticket.finish(true);
    }

    private static Path write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, content); return file;
    }

    @Test void resolvesOnlyCapturedRuntimeArchivesWithVersionHashAndInheritedTextures() throws Exception {
        Path root = workspace();
        Path active = archive("active", "{\"textures\":{\"particle\":\"dep:block/surface\"}}");
        archive("unrelated_cache", "{}");
        capture(root, List.of(active), ":runtimeClasspath");
        Path manifest = root.resolve(ResourceDependencyCapture.INDEX);
        String before = Files.readString(manifest);
        var graph = new AssetWorkspaceService(root).referenceGraph();
        assertTrue(graph.diagnostics().isEmpty(), graph.diagnostics().toString());
        assertEquals("dependency_resolved", graph.references().getFirst().resolution());
        assertEquals("1.0.0", graph.references().getFirst().resourceVersion());
        assertTrue(graph.references().getFirst().resourceSource().contains("org.example:active:1.0.0 sha256="));
        assertEquals(before, Files.readString(manifest), "Health queries must not update the dependency snapshot");
    }

    @Test void unlistedArchiveAndCompileClasspathCannotEstablishRuntimeAvailability() throws Exception {
        Path root = workspace(); Path jar = archive("inactive", "{}");
        capture(root, List.of(), ":runtimeClasspath");
        assertTrue(new AssetWorkspaceService(root).referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("MISSING_ASSET_REFERENCE")));
        capture(root, List.of(jar), ":compileClasspath");
        assertTrue(new AssetWorkspaceService(root).referenceGraph().diagnostics().stream().anyMatch(d -> d.code().equals("EXTERNAL_ASSET_REFERENCE_UNVERIFIED")));
    }

    @Test void nativeInputAndArchiveChangesInvalidateTheSnapshotWithoutRevisionChanges() throws Exception {
        Path root = workspace(); Path jar = archive("active", "{}");
        capture(root, List.of(jar), ":runtimeClasspath");
        assertTrue(new AssetWorkspaceService(root).referenceGraph().diagnostics().isEmpty());
        write(root, "build.gradle", "// external dependency configuration changed\n");
        assertEquals("unverified", new AssetWorkspaceService(root).referenceGraph().references().getFirst().resolution());
        assertEquals("unverified", LocalResourceIndex.discover(root).resolve("assets/minecraft/models/block/cube_all.json", "probe").state(),
                "A stale dependency catalog must not silently fall back to a possibly overridden vanilla resource");
        capture(root, List.of(jar), ":runtimeClasspath");
        archive("active", "{\"textures\":{\"particle\":\"dep:block/changed\"}}");
        assertEquals("unverified", new AssetWorkspaceService(root).referenceGraph().references().getFirst().resolution());
    }

    @Test void overlappingRuntimePacksAndWorkspaceOverridesNeverGuessAWinningPack() throws Exception {
        Path root = workspace(); Path first = archive("first", "{}"), second = archive("second", "{\"ambientocclusion\":false}");
        capture(root, List.of(first, second), ":runtimeClasspath");
        var overlap = new AssetWorkspaceService(root).referenceGraph().references().getFirst();
        assertEquals("unverified", overlap.resolution());
        assertTrue(overlap.resourceSource().contains("order"));
        write(root, "src/main/resources/assets/dep/models/block/base.json", "{}");
        capture(root, List.of(first), ":runtimeClasspath");
        var workspaceOverlap = new AssetWorkspaceService(root).referenceGraph().references().stream()
                .filter(reference -> reference.rawValue().equals("dep:block/base")).findFirst().orElseThrow();
        assertEquals("unverified", workspaceOverlap.resolution());
        assertTrue(workspaceOverlap.resourceSource().contains("workspace_and_dependency"));
    }

    @Test void failedCaptureMakesThePreviousCatalogUnavailable() throws Exception {
        Path root = workspace(); Path jar = archive("active", "{}");
        capture(root, List.of(jar), ":runtimeClasspath");
        var ticket = ResourceDependencyCapture.prepare(root, new ArrayList<>(List.of("build")), ignored -> {});
        assertNotNull(ticket);
        ticket.finish(false);
        assertEquals("unverified", new AssetWorkspaceService(root).referenceGraph().references().getFirst().resolution());
    }

    @Test void runtimeDirectoryResourcesPreventClaimingACompleteArchiveCatalog() throws Exception {
        Path root = workspace();
        Path directory = Files.createDirectories(temporary.resolve("runtime-directory"));
        write(directory, "assets/dep/models/block/base.json", "{}");
        capture(root, List.of(directory), ":runtimeClasspath");
        assertEquals("unavailable", com.google.gson.JsonParser.parseString(
                Files.readString(root.resolve(ResourceDependencyCapture.INDEX))).getAsJsonObject().get("state").getAsString());
        assertEquals("unverified", new AssetWorkspaceService(root).referenceGraph().references().getFirst().resolution());
    }
}
