package dev.copperbench.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GradleCachePolicyTest {
    @TempDir Path directory;

    @Test void explicitJvmOverrideAndCallerPathsApplyBeforeTheProductDefault() {
        Path product = directory.resolve("default"), property = directory.resolve("property");
        Path generic = directory.resolve("generic"), specific = directory.resolve("specific");
        assertEquals(specific, GradleCachePolicy.userHome(Map.of("COPPERBENCH_GRADLE_USER_HOME", specific.toString(),
                "GRADLE_USER_HOME", generic.toString()), null, product));
        assertEquals(generic, GradleCachePolicy.userHome(Map.of("GRADLE_USER_HOME", generic.toString()), null, product));
        assertEquals(property, GradleCachePolicy.userHome(Map.of("COPPERBENCH_GRADLE_USER_HOME", specific.toString(),
                "GRADLE_USER_HOME", generic.toString()), property.toString(), product));
        assertEquals(property, GradleCachePolicy.userHome(Map.of(), property.toString(), product));
        assertEquals(product, GradleCachePolicy.userHome(Map.of("GRADLE_USER_HOME", " "), "", product));
        assertFalse(Files.exists(product));
        assertFalse(Files.exists(specific));
    }

    @Test void relativePathsAreAnchoredBeforeLaunchingInADifferentWorkspaceDirectory() {
        assertEquals(Path.of("cache with spaces").toAbsolutePath().normalize(), GradleCachePolicy.userHome(
                Map.of("COPPERBENCH_GRADLE_USER_HOME", "nested/../cache with spaces"), null, directory));
        assertEquals("copperbench.gradle.user.home", GradleCachePolicy.userHomeSource(Map.of(), "legacy"));
        assertEquals("product_default", GradleCachePolicy.userHomeSource(Map.of(), null));
    }

    @Test void externalReuseMustBeAnExplicitBoolean() {
        assertTrue(GradleCachePolicy.reuseExternalDistributions(Map.of()));
        assertTrue(GradleCachePolicy.reuseExternalDistributions(Map.of("COPPERBENCH_GRADLE_REUSE_EXTERNAL", "true")));
        assertFalse(GradleCachePolicy.reuseExternalDistributions(Map.of("COPPERBENCH_GRADLE_REUSE_EXTERNAL", "false")));
        assertThrows(IllegalArgumentException.class, () -> GradleCachePolicy.reuseExternalDistributions(
                Map.of("COPPERBENCH_GRADLE_REUSE_EXTERNAL", "disabled")));
    }
}
