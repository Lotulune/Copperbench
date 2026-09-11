package dev.copperbench.gradle;

import dev.copperbench.generator.BundledJdkLocator;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.gradle.GradleUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopGradleRuntimeTest {
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void setupUsesTheSameBundledJavaTrackAsCoreTasks() {
        for (var track : dev.copperbench.tracks.VersionTrackCatalog.official().tracks())
            for (var loader : track.loaders())
                assertEquals(BundledJdkLocator.locate(Path.of("."), loader.javaRelease()),
                        Path.of(GradleUtils.getJavaHome(loader.generatorId())), loader.generatorId());
        assertEquals(GradleUtils.getJavaHome(), GradleUtils.getJavaHome("external-plugin"));
    }

    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @org.junit.jupiter.api.Timeout(value = 3, unit = java.util.concurrent.TimeUnit.MINUTES)
    void toolingDaemonReceivesTheVerifiedPipeOptionAsJvmArgument(
            @org.junit.jupiter.api.io.TempDir Path project) throws Exception {
        var home = net.mcreator.io.UserFolderManager.getGradleHome().toPath();
        var install = GradleDistributionPool.findReadyInstall("9.7.0", home,
                java.util.List.of(Path.of("build/export/win64/gradle-dists")));
        org.junit.jupiter.api.Assumptions.assumeTrue(install.isPresent(), "Requires a locally seeded Gradle distribution");
        Path javaHome = BundledJdkLocator.locate(Path.of("."), 21);
        var environment = new java.util.HashMap<>(System.getenv());
        for (String key : java.util.List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) environment.remove(key);
        var messages = new java.util.ArrayList<String>();
        GradleRuntimeCompatibility.configure(javaHome, environment, messages::add);
        java.nio.file.Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'tooling-ipc-probe'\n");
        java.nio.file.Files.writeString(project.resolve("build.gradle"), """
                tasks.register('verifyLocalIpc') {
                    doLast {
                        def selector = java.nio.channels.Selector.open()
                        selector.close()
                        file('result.txt').text = System.getProperty('java.home') + '\\n' + System.getProperty('jdk.net.unixdomain.tmpdir', '')
                    }
                }
                """);
        var connector = org.gradle.tooling.GradleConnector.newConnector().forProjectDirectory(project.toFile())
                .useInstallation(install.orElseThrow().resolve("gradle-9.7.0").toFile()).useGradleUserHomeDir(home.toFile());
        try (var connection = connector.connect()) {
            GradleUtils.getGradleTaskLauncher(net.mcreator.generator.Generator.GENERATOR_CACHE.get("fabric-1.21.1"),
                    connection, "verifyLocalIpc").run();
        } finally { connector.disconnect(); }
        var result = java.nio.file.Files.readAllLines(project.resolve("result.txt"));
        assertEquals(javaHome.toRealPath(), Path.of(result.getFirst()).toRealPath());
        if (!messages.isEmpty()) assertEquals(javaHome.resolve("bin/java.exe").toAbsolutePath().normalize().toString(), result.get(1));
    }
}
