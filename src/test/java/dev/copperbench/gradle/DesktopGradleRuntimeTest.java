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
}
