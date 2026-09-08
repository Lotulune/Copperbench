package dev.copperbench.assets;

import dev.copperbench.platform.RuntimePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockbenchExecutableLocatorTest {
    @TempDir Path root;

    @Test void linuxFindsBlockbenchFromPath() throws Exception {
        Path bin = root.resolve("bin");
        Files.createDirectories(bin);
        Path executable = bin.resolve("blockbench");
        Files.writeString(executable, "fixture");
        Map<String, String> environment = new HashMap<>();
        environment.put("PATH", bin.toString());
        environment.put("HOME", root.toString());

        Path located = BlockbenchExecutableLocator.locate(RuntimePlatform.detect("Linux", "x86_64"),
                environment, "");
        assertEquals(executable.toAbsolutePath().normalize(), located);
    }

    @Test void explicitExecutableStillWinsOnLinux() throws Exception {
        Path configured = root.resolve("Blockbench.AppImage");
        Files.writeString(configured, "fixture");
        Path pathCandidate = root.resolve("bin/blockbench");
        Files.createDirectories(pathCandidate.getParent());
        Files.writeString(pathCandidate, "fixture");
        Map<String, String> environment = Map.of("PATH", pathCandidate.getParent().toString(),
                "HOME", root.toString());

        assertEquals(configured.toAbsolutePath().normalize(), BlockbenchExecutableLocator.locate(
                RuntimePlatform.detect("Linux", "x86_64"), environment, configured.toString()));
    }
}
