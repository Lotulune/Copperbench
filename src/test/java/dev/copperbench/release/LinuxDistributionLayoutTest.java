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

    @Test void linuxExportUsesCopperbenchLauncherAndBothBundledJdks() throws Exception {
        String gradle = Files.readString(Path.of("platform/linux/linux.gradle"));
        assertTrue(gradle.contains("dependsOn downloadJDK21Linux64"));
        assertTrue(gradle.contains("platform/linux/copperbench.sh"));
        assertTrue(gradle.contains("writeLinuxCandidateManifest"));
        assertTrue(gradle.contains("prepareLinuxGradleDistPool"));
        assertTrue(gradle.contains("['9.7.0', '9.6.1', '8.8']"));
        assertTrue(gradle.contains("services.gradle.org/distributions"));
        assertTrue(gradle.contains("into('gradle-dists')"));
        assertTrue(gradle.contains("linux-candidate-manifest.json"));
        assertTrue(gradle.contains("into('jdk21') { from 'jdk/jdk21_linux_64' }"));
        assertTrue(gradle.contains("Linux x86_64.tar.gz"));
        assertFalse(gradle.contains("archiveFileName = 'MCreator"));
        assertTrue(gradle.contains("buildDebLinux64"));
        assertTrue(gradle.contains("dpkg-deb"));
        assertTrue(gradle.contains("opt/copperbench"));
        assertTrue(Files.readString(Path.of("platform/linux/deb/copperbench.desktop")).contains("Name=Copperbench"));

        String launcher = Files.readString(Path.of("platform/linux/copperbench.sh"));
        assertTrue(launcher.contains("BASH_SOURCE[0]"));
        assertTrue(launcher.contains("lib/copperbench.jar"));
        assertTrue(launcher.contains("-Dcopperbench.productShell=true"));
        assertTrue(launcher.contains("-Dcopperbench.stage15LinuxCandidate=true"));
        assertTrue(launcher.contains("net.mcreator.Launcher \"$@\""));
        assertTrue(launcher.contains("exec \"$SCRIPT_DIR/jdk/bin/java\""));
        assertFalse(launcher.contains("exec java "));
        assertFalse(launcher.contains("JAVA_HOME/bin/java"));

        String workflow = Files.readString(Path.of(".github/workflows/stage15-linux-candidate.yml"));
        assertTrue(workflow.contains("Launch packaged headless bootstrap without system Java Gradle or Git"));
        assertTrue(workflow.contains("PATH=\"$minimal_path\""));
        assertTrue(workflow.contains("/usr/bin/bash \"$root/copperbench.sh\" bootstrap list-generators"));
        assertTrue(workflow.contains("test ! -e \"$minimal_path/java\""));
        assertTrue(workflow.contains("test ! -e \"$minimal_path/gradle\""));
        assertTrue(workflow.contains("test ! -e \"$minimal_path/git\""));
        assertTrue(workflow.contains("test -d \"$isolated_home/data/copperbench\""));
        assertTrue(workflow.contains("test -d \"$isolated_home/cache/copperbench/gradle\""));
        assertTrue(workflow.contains("test -d \"$isolated_home/runtime/copperbench\""));
        assertTrue(workflow.contains("for version in 9.7.0 9.6.1 8.8; do"));
    }

    @Test void linuxSetupProvidesJava25JcefAndJava21Sidecar() throws Exception {
        String setup = Files.readString(Path.of("platform/setup.gradle"));
        assertTrue(setup.contains("downloadJDKLinux64"));
        assertTrue(setup.contains("downloadJDK21Linux64"));
        assertTrue(setup.contains("jdk/jbr25_linux_64/bin/java"));
        assertTrue(setup.contains("jdk/jdk21_linux_64/bin/java"));
        assertTrue(setup.contains("OpenJDK21U-jdk_x64_linux_hotspot_21.0.12_8.tar.gz"));
        assertTrue(setup.contains("dependsOn downloadJDKLinux64, downloadJDK21Linux64"));
    }
}
