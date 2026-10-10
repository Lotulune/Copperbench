package dev.copperbench.gradle;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GradleCacheProcessTest {
    @TempDir Path directory;

    @Test void setupAndBackendObserveTheSameIsolatedHomeWithoutImportingExistingDistributions() throws Exception {
        Path selected = directory.resolve("isolated cache"), pool = directory.resolve("external-pool");
        Path sentinel = pool.resolve("gradle-9.7.0-bin/prepared/gradle-9.7.0/bin/gradle");
        Files.createDirectories(sentinel.getParent());
        Files.writeString(sentinel, "Never execute or import this external distribution");
        Path output = directory.resolve("probe.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dcopperbench.gradle.pool=" + pool,
                "-cp", System.getProperty("copperbench.test.runtimeClasspath"), GradleCacheProcessProbe.class.getName(),
                directory.resolve("workspace").toString()).redirectErrorStream(true).redirectOutput(output.toFile());
        process.environment().put("COPPERBENCH_GRADLE_USER_HOME", selected.toString());
        process.environment().put("GRADLE_USER_HOME", directory.resolve("other-cache").toString());
        process.environment().put("COPPERBENCH_GRADLE_REUSE_EXTERNAL", "false");
        var child = process.start();
        try {
            assertTrue(child.waitFor(45, TimeUnit.SECONDS), "Cache probe did not exit");
            assertEquals(0, child.exitValue(), Files.readString(output));
            String line = Files.readAllLines(output).stream().filter(value -> value.startsWith("CACHE_PROBE=")).findFirst().orElseThrow();
            var result = JsonParser.parseString(line.substring("CACHE_PROBE=".length())).getAsJsonObject();
            assertEquals(selected.toString(), result.get("setupHome").getAsString());
            var gradle = result.getAsJsonObject("backend").getAsJsonObject("gradle");
            assertEquals(selected.toString(), gradle.get("userHome").getAsString());
            assertEquals("COPPERBENCH_GRADLE_USER_HOME", gradle.get("userHomeSource").getAsString());
            assertFalse(gradle.get("reuseExternalDistributions").getAsBoolean());
            assertEquals(0, result.get("externalRoots").getAsInt());
            assertEquals(0, result.get("seeded").getAsInt());
            assertFalse(Files.exists(selected), "Read-only configuration and a disabled pool must not prepare cache files");
            assertEquals("Never execute or import this external distribution", Files.readString(sentinel));
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
        }
    }
}
