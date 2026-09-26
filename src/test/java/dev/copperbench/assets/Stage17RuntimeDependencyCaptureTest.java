package dev.copperbench.assets;

import com.google.gson.JsonParser;
import dev.copperbench.generator.PluginWorkspaceLayout;
import dev.copperbench.generator.ResourceDependencyCapture;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class Stage17RuntimeDependencyCaptureTest {
    @Test
    @EnabledIfSystemProperty(named = "copperbench.stage17.runtimeDependencyProbe", matches = "true")
    void existingFabricAndNeoForgeProjectsCaptureRealRuntimeArtifactsWithoutChangingTheirJars() throws Exception {
        Map<String, String> inputs = Map.of(
                "fabric-1.21.1", "workspace-48fe1bcc-3794-4ce2-bb32-8694ccefa87b",
                "neoforge-1.21.1", "workspace-9de3c58f-a496-4944-a67c-142a49aba744");
        Map<String, String> hashes = Map.of("fabric-1.21.1", "36bdb79284c22bdb748a400494fe9b60f1e5519a4802697fd509d3b1d118db47",
                "neoforge-1.21.1", "62285080758905de476b5cce3ca767823c769d2416061ea1ee53290a577a54e1");
        Path evidence = Path.of("build/stage17-deps", UUID.randomUUID().toString().substring(0, 8)).toAbsolutePath();
        for (String generator : List.of("fabric-1.21.1", "neoforge-1.21.1")) {
            Path source = Path.of("build/stage17-structured-block-builds", generator, inputs.get(generator));
            assertTrue(Files.isDirectory(source), "Run the structured build fixture before this explicit probe: " + source);
            Path root = evidence.resolve(generator).resolve("workspace");
            WorkspaceExecutionSnapshot.capture(source, root, UUID.randomUUID(), 2, java.time.Clock.systemUTC(), () -> false);
            var lines = new java.util.concurrent.CopyOnWriteArrayList<String>();
            try {
                Path javaHome = Path.of(dev.copperbench.platform.RuntimePlatform.current().sourceJavaHome(21)).toAbsolutePath();
                var process = Fabric1211ProcessRunner.system("unused", javaHome).run(root,
                        List.of("build", "--offline", "--console=plain"), Duration.ofMinutes(6), lines::add);
                assertEquals(0, process.exitCode(), String.join("\n", lines));
                var captured = JsonParser.parseString(Files.readString(root.resolve(ResourceDependencyCapture.INDEX))).getAsJsonObject();
                assertEquals("available", captured.get("state").getAsString(), captured.toString());
                assertFalse(captured.getAsJsonArray("artifacts").isEmpty());
                var index = DependencyResourceIndex.discover(root, generator);
                assertTrue(index.available(), index.reason());
                String jarHash = WorkspaceExecutionSnapshot.sha256(root.resolve("build/libs/structured_forge-1.0.jar"));
                assertEquals(hashes.get(generator), jarHash, "Resource indexing must not alter the generated mod");
                Files.writeString(root.resolve("build/mod-jar.sha256"), jarHash);
            } finally {
                Files.createDirectories(root.resolve("build"));
                Files.writeString(root.resolve("build/capture.log"), String.join("\n", lines));
                System.out.println("STAGE17_RUNTIME_LOADER_EVIDENCE=" + root);
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "copperbench.stage17.runtimeDependencyProbe", matches = "true")
    void realOfflineGradleRuntimeConfigurationIncludesRuntimeOnlyAndExcludesCompileOnly() throws Exception {
        Path root = Path.of("build/stage17-runtime-dependency-probes", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(root.resolve("libraries"));
        Files.writeString(root.resolve("probe.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}");
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'stage17-runtime-capture'\n");
        Files.writeString(root.resolve("build.gradle"), """
                plugins { id 'java' }
                dependencies {
                    runtimeOnly files('libraries/active.jar')
                    compileOnly files('libraries/inactive.jar')
                }
                """);
        Files.writeString(root.resolve("gradle.properties"), "org.gradle.jvmargs=-Xmx512m\n");
        archive(root.resolve("libraries/active.jar"), "active");
        archive(root.resolve("libraries/inactive.jar"), "inactive");
        Path model = root.resolve("src/main/resources/assets/probe/models/block/child.json");
        Files.createDirectories(model.getParent()); Files.writeString(model, "{\"parent\":\"active:block/base\"}");
        PluginWorkspaceLayout.ensureGradleRuntime(root, Path.of(".").toAbsolutePath(), "gradle-9.7.0-bin.zip");
        var lines = new java.util.concurrent.CopyOnWriteArrayList<String>();
        try {
            var process = Fabric1211ProcessRunner.system().run(root, List.of("build", "--offline", "--console=plain"), Duration.ofMinutes(3), lines::add);
            assertEquals(0, process.exitCode(), String.join("\n", lines));
            var captured = JsonParser.parseString(Files.readString(root.resolve(ResourceDependencyCapture.INDEX))).getAsJsonObject();
            assertEquals("available", captured.get("state").getAsString(), captured.toString());
            assertEquals(1, captured.getAsJsonArray("artifacts").size(), captured.toString());
            assertTrue(captured.getAsJsonArray("artifacts").get(0).getAsJsonObject().get("path").getAsString().endsWith("active.jar"));
            var graph = new AssetWorkspaceService(root).referenceGraph();
            assertTrue(graph.diagnostics().isEmpty(), graph.diagnostics().toString());
            assertEquals("dependency_resolved", graph.references().getFirst().resolution());
            assertEquals("missing", LocalResourceIndex.discover(root).resolve("assets/inactive/models/block/base.json", "probe").state());
        } finally {
            Files.createDirectories(root.resolve("build"));
            Files.writeString(root.resolve("build/capture.log"), String.join("\n", lines));
            System.out.println("STAGE17_RUNTIME_DEPENDENCY_EVIDENCE=" + root);
        }
    }

    private static void archive(Path path, String id) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : Map.of("fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1.0.0\"}",
                    "assets/" + id + "/models/block/base.json", "{}").entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
    }
}
