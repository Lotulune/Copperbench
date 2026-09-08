package dev.copperbench.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LinuxDistributionLayoutTest {
    @TempDir Path root;

    @Test void stage15BaselineAndLayoutAreExplicitWithoutClaimingCertification() throws Exception {
        assertEquals("Ubuntu 24.04 LTS", LinuxSupportTarget.DISTRIBUTION);
        assertEquals("x86_64", LinuxSupportTarget.ARCHITECTURE);
        assertEquals("tar.gz", LinuxSupportTarget.PORTABLE_FORMAT);
        assertEquals("deb", LinuxSupportTarget.DESKTOP_PACKAGE_FORMAT);

        var json = LinuxDistributionLayout.toJson();
        assertEquals("stage15-development", json.get("status").getAsString());
        assertEquals("linux", json.get("os").getAsString());
        assertEquals("copperbench.sh", json.get("executable").getAsString());
        assertEquals("jdk/jbr25_linux_64",
                json.getAsJsonObject("bundledJdk").get("sourceTreeJava25").getAsString());
        assertEquals("jdk/jdk21_linux_64",
                json.getAsJsonObject("bundledJdk").get("sourceTreeJava21").getAsString());

        for (String entry : LinuxDistributionLayout.REQUIRED_ENTRIES) {
            Path path = root.resolve(entry);
            if (entry.contains(".") || entry.contains("bin/")) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "fixture");
            } else {
                Files.createDirectories(path);
            }
        }
        assertTrue(LinuxDistributionLayout.missing(root).isEmpty());
        Files.delete(root.resolve("jdk21/bin/java"));
        assertEquals(java.util.List.of("jdk21/bin/java"), LinuxDistributionLayout.missing(root));
    }
}
