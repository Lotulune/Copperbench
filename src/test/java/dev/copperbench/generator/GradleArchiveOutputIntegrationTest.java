package dev.copperbench.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Real Gradle, no Minecraft download: exercise the receipt action with a cache-enabled project. */
class GradleArchiveOutputIntegrationTest {
    @TempDir Path temporary;

    @Test void repeatedExportsCaptureFreshArchivesWhenTheProjectEnablesConfigurationCache() throws Exception {
        Path project = Files.createDirectories(temporary.resolve("project"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'archive-receipt-regression'\n");
        Files.writeString(project.resolve("gradle.properties"), "org.gradle.configuration-cache=true\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'base' }
                tasks.register('jar', Jar) {
                    from 'payload'
                    archiveFileName = 'declared.jar'
                    destinationDirectory = layout.buildDirectory.dir('cloud-archives')
                }
                tasks.named('build') { dependsOn 'jar' }
                """);
        Path input = Files.createDirectories(project.resolve("payload")).resolve("content.txt");
        String previousHash = null;
        for (String contents : List.of("first payload", "second changed payload")) {
            Files.writeString(input, contents);
            var request = GradleArchiveOutput.prepare(project, UUID.randomUUID(), List.of("jar"));
            runGradle(project, request.arguments(List.of("build")), request.receipt().getParent().resolve("gradle.log"));
            var artifact = request.artifact();
            assertEquals(project.resolve("build/cloud-archives/declared.jar"), artifact.path());
            assertFalse(Files.exists(project.resolve("build/libs")));
            assertNotEquals(previousHash, artifact.sha256(), "A later export must not reuse an older archive receipt");
            try (JarFile jar = new JarFile(artifact.path().toFile())) {
                assertEquals(contents, new String(jar.getInputStream(jar.getJarEntry("content.txt")).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals("org.gradle.configuration-cache=true\n", Files.readString(project.resolve("gradle.properties")),
                    "The export must not change the project's configuration-cache preference");
            previousHash = artifact.sha256();
        }
    }

    private void runGradle(Path project, List<String> arguments, Path log) throws Exception {
        Path installation = Path.of(System.getProperty("copperbench.test.gradleHome"));
        assertTrue(Files.isDirectory(installation.resolve("lib")), "Use the Gradle test task's running distribution");
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin",
                windows ? "java.exe" : "java").toString(), "-cp", installation.resolve("lib") + java.io.File.separator + "*",
                "org.gradle.launcher.GradleMain", "--offline", "--no-daemon", "--max-workers=1",
                "--gradle-user-home", temporary.resolve("gradle-user").toString()));
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Archive receipt regression timed out; log: " + Files.readString(log));
        }
        assertEquals(0, process.exitValue(), () -> {
            try { return Files.readString(log); }
            catch (Exception error) { return error.toString(); }
        });
    }
}
