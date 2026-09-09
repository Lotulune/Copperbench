package dev.copperbench.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

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
        assertTrue(gradle.contains("platform/linux/LINUX-CANDIDATE.md"));
        assertTrue(gradle.contains("writeLinuxCandidateManifest"));
        assertTrue(gradle.contains("prepareLinuxGradleDistPool"));
        assertTrue(gradle.contains("['9.7.0', '9.6.1', '8.8']"));
        assertTrue(gradle.contains("services.gradle.org/distributions"));
        assertTrue(gradle.contains("into('gradle-dists')"));
        assertTrue(gradle.contains("linux-candidate-manifest.json"));
        assertTrue(gradle.contains("into('jdk21') { from 'jdk/jdk21_linux_64' }"));
        assertTrue(gradle.contains("Linux x86_64.tar.gz"));
        assertTrue(gradle.contains("def applyExecPermissions = { spec ->"));
        assertTrue(gradle.contains("applyExecPermissions(delegate)"));
        assertFalse(gradle.contains("from('build/export/linux64/copperbench.sh', execPermissions)"));
        assertFalse(gradle.contains("from('build/export/linux64/gradlew', execPermissions)"));
        assertFalse(gradle.contains("archiveFileName = 'MCreator"));
        assertTrue(gradle.contains("buildDebLinux64"));
        assertTrue(gradle.contains("dpkg-deb"));
        assertTrue(gradle.contains("opt/copperbench"));
        assertTrue(Files.readString(Path.of("platform/linux/deb/copperbench.desktop")).contains("Name=Copperbench"));

        String graphicalVerifier = Files.readString(Path.of("scripts/stage15/Stage15GraphicalProbeVerifier.java"));
        assertTrue(graphicalVerifier.contains("[x11|wayland]"));
        assertTrue(graphicalVerifier.contains("stage15-primary-target"));
        assertTrue(graphicalVerifier.contains("stage15-compatibility-target"));
        assertTrue(graphicalVerifier.contains("Expected desktop session must be x11 or wayland"));
        assertTrue(graphicalVerifier.contains("Stage 15 graphical probe must keep formal certification pending"));

        Path blockbenchVerifierPath = Path.of("scripts/stage15/Stage15InstalledBlockbenchVerifier.java");
        String blockbenchVerifier = Files.readString(blockbenchVerifierPath);
        assertTrue(blockbenchVerifier.contains("BlockbenchExecutableLocator.locate()"));
        assertTrue(blockbenchVerifier.contains("BLOCKBENCH_ASSET_LEASED"));
        assertTrue(blockbenchVerifier.contains("ASSET_CHANGED_EXTERNALLY"));
        assertTrue(blockbenchVerifier.contains("formalSupportClaim"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Stage 15 verifier contract requires a full JDK");
        Path verifierClasses = Files.createDirectories(root.resolve("stage15-blockbench-verifier-classes"));
        assertEquals(0, compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"),
                "-d", verifierClasses.toString(), blockbenchVerifierPath.toString()));

        String installedGate = Files.readString(Path.of("scripts/verify-stage15-linux-installed-guest.sh"));
        assertTrue(installedGate.contains("guest desktop is not GNOME"));
        assertTrue(installedGate.contains("for tool in java gradle git"));
        assertTrue(installedGate.contains("preinstall-system-tooling.txt"));
        assertTrue(installedGate.contains("systemJavaGradleGitAbsentBeforeInstall\":true"));
        assertTrue(installedGate.contains("/usr/share/applications/copperbench.desktop"));
        assertTrue(installedGate.contains("Exec=/usr/bin/copperbench %F"));
        assertTrue(installedGate.contains("/usr/share/icons/hicolor/256x256/apps/copperbench.png"));
        assertTrue(installedGate.contains("confirm-user-visible-minecraft-window-during-agent-helper-runclient"));
        assertTrue(installedGate.contains("copy-one-time-desktop-mcp-token-from-ui-and-run-bundled-agent-helper"));
        assertTrue(installedGate.contains("normal-close-copperbench-after-agent-helper-prompt"));
        assertTrue(installedGate.contains("run-bundled-blockbench-helper-edit-save-close"));
        assertTrue(installedGate.contains("confirm-installed-asset-center-launches-real-blockbench"));

        String installedAgentGate = Files.readString(Path.of("scripts/verify-stage15-linux-installed-agent.py"));
        assertTrue(installedAgentGate.contains("call_tool(\"run_client\""));
        assertTrue(installedAgentGate.contains("Backend library: LWJGL version"));
        assertTrue(installedAgentGate.contains("minecraft:textures/atlas/blocks.png-atlas"));
        assertTrue(installedAgentGate.contains("stableBeforeUserCloseSeconds"));
        assertTrue(installedAgentGate.contains("terminalStateAfterUserClose"));

        String candidateInstructions = Files.readString(Path.of("platform/linux/LINUX-CANDIDATE.md"));
        assertTrue(candidateInstructions.contains("Stage 15 development candidate"));
        assertTrue(candidateInstructions.contains("sudo apt remove copperbench"));
        assertTrue(candidateInstructions.contains("~/.local/share/copperbench"));
        assertTrue(candidateInstructions.contains("~/.config/copperbench"));
        assertTrue(candidateInstructions.contains("~/.cache/copperbench"));
        assertTrue(candidateInstructions.contains("~/.local/state/copperbench"));
        assertTrue(candidateInstructions.contains("never part of package cleanup"));

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
        assertTrue(workflow.contains("Run packaged Fabric 1.21.1 X11 render preflight under Xvfb"));
        assertTrue(workflow.contains("Run packaged NeoForge 1.21.1 X11 render preflight under Xvfb"));
        assertTrue(workflow.contains("verify-stage15-linux-runclient-ci-smoke.sh"));
        assertTrue(workflow.contains("stage15-linux-installed-gate.tests.mjs"));
        assertTrue(workflow.contains("Stage15LinuxHyperVHarness.tests.ps1"));
        assertTrue(workflow.contains("pwsh -NoProfile -File scripts/tests/Stage15LinuxHyperVHarness.tests.ps1"));
        assertTrue(workflow.contains("scripts/New-Stage15LinuxHyperVGuest.ps1"));
        assertTrue(workflow.contains("scripts/New-Stage15LinuxAutoinstallSeed.ps1"));
        assertTrue(workflow.contains("scripts/verify-stage15-linux-hyperv-ready.ps1"));
        assertTrue(workflow.contains("stage15-linux-installed-gate-harness"));
        assertTrue(workflow.contains("scripts/verify-stage15-linux-installed-guest.sh"));
        assertTrue(workflow.contains("scripts/verify-stage15-linux-installed-agent.py"));
        assertTrue(workflow.contains("scripts/stage15/Stage15GraphicalProbeVerifier.java"));
        assertTrue(workflow.contains("scripts/stage15/Stage15InstalledBlockbenchVerifier.java"));
        assertTrue(workflow.contains("scripts/stage15/INSTALLED-GATE.md"));
        assertTrue(workflow.contains("sdk/python/copperbench.py"));
        assertTrue(workflow.contains("stage15-linux-installed-gate-harness.tar.gz"));
        assertTrue(workflow.contains("tar -C \"$harness_root\" -czf \"$archive\" ."));
        assertTrue(workflow.contains("tar -tzf \"$archive\" > \"$archive_contents\""));
        assertTrue(workflow.contains("tar -tvzf \"$archive\" > \"$archive_listing\""));
        assertFalse(workflow.contains("tar -tzf \"$archive\" | grep -q"));
        assertTrue(workflow.contains("-rwxr-xr-x"));
        assertTrue(workflow.contains("desktop_entry=\"build/stage15-deb-smoke/usr/share/applications/copperbench.desktop\""));
        assertTrue(workflow.contains("desktop_icon=\"build/stage15-deb-smoke/usr/share/icons/hicolor/256x256/apps/copperbench.png\""));
        assertTrue(workflow.contains("grep -Fxq 'Exec=/usr/bin/copperbench %F' \"$desktop_entry\""));
        assertFalse(workflow.contains("verify-stage15-linux-fabric-runclient-ci-smoke.sh"));
        assertTrue(workflow.contains("\"fabric-1.21.1\""));
        assertTrue(workflow.contains("\"neoforge-1.21.1\""));

        String runClientSmoke = Files.readString(Path.of("scripts/verify-stage15-linux-runclient-ci-smoke.sh"));
        assertTrue(runClientSmoke.contains("headless --workspace \"$workspace_file\" build"));
        assertTrue(runClientSmoke.contains("headless --workspace \"$workspace_file\" run-client"));
        assertTrue(runClientSmoke.contains("Loading Minecraft 1.21.1 with Fabric Loader"));
        assertTrue(runClientSmoke.contains("NeoForge 21.1.232 (neoforge)"));
        assertTrue(runClientSmoke.contains("grep -Fq -- \"$loader_marker\""));
        assertTrue(runClientSmoke.contains("grep -F -- \"$loader_marker\""));
        assertTrue(runClientSmoke.contains("Backend library: LWJGL version"));
        assertTrue(runClientSmoke.contains("Reloading ResourceManager:"));
        assertTrue(runClientSmoke.contains("minecraft:textures/atlas/blocks.png-atlas"));
        assertTrue(runClientSmoke.contains("minecraft-render-proof.txt"));
        assertTrue(runClientSmoke.contains("for ((attempt = 0; attempt < 20; attempt++))"));
        assertTrue(runClientSmoke.contains("GLFW error"));
        assertTrue(runClientSmoke.contains("failed to initialize the mod loading system and display"));
        assertFalse(runClientSmoke.contains("xdotool search --onlyvisible"));
        assertTrue(runClientSmoke.contains("COPPERBENCH_GRADLE_USER_HOME"));
        assertFalse(runClientSmoke.contains("./gradlew runClient"));
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
