package dev.copperbench.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserDataPathsTest {
    @TempDir Path home;

    @Test void linuxUsesAbsoluteXdgDirectories() {
        Path root = home.resolve("xdg");
        Map<String, String> env = Map.of(
                "XDG_DATA_HOME", root.resolve("data").toString(),
                "XDG_CONFIG_HOME", root.resolve("config").toString(),
                "XDG_CACHE_HOME", root.resolve("cache").toString(),
                "XDG_STATE_HOME", root.resolve("state").toString(),
                "XDG_RUNTIME_DIR", root.resolve("runtime").toString());
        UserDataPaths paths = UserDataPaths.resolve(RuntimePlatform.detect("Linux", "x86_64"), env, home);
        assertEquals(root.resolve("data/copperbench").toAbsolutePath().normalize(), paths.data());
        assertEquals(root.resolve("config/copperbench").toAbsolutePath().normalize(), paths.config());
        assertEquals(root.resolve("cache/copperbench").toAbsolutePath().normalize(), paths.cache());
        assertEquals(root.resolve("state/copperbench").toAbsolutePath().normalize(), paths.state());
        assertEquals(root.resolve("runtime/copperbench").toAbsolutePath().normalize(), paths.runtime());
    }

    @Test void linuxFallsBackWhenXdgValuesAreRelativeOrMissing() {
        Map<String, String> env = new HashMap<>();
        env.put("XDG_DATA_HOME", "relative-data");
        env.put("XDG_RUNTIME_DIR", "relative-runtime");
        UserDataPaths paths = UserDataPaths.resolve(RuntimePlatform.detect("Linux", "amd64"), env, home);
        assertEquals(home.resolve(".local/share/copperbench").toAbsolutePath().normalize(), paths.data());
        assertEquals(home.resolve(".config/copperbench").toAbsolutePath().normalize(), paths.config());
        assertEquals(home.resolve(".cache/copperbench").toAbsolutePath().normalize(), paths.cache());
        assertEquals(home.resolve(".local/state/copperbench").toAbsolutePath().normalize(), paths.state());
        assertEquals(home.resolve(".local/state/copperbench/run").toAbsolutePath().normalize(), paths.runtime());
    }

    @Test void windowsKeepsLegacySingleRoot() {
        UserDataPaths paths = UserDataPaths.resolve(RuntimePlatform.detect("Windows 11", "amd64"), Map.of(), home);
        Path expected = home.resolve(".copperbench").toAbsolutePath().normalize();
        assertEquals(expected, paths.data());
        assertEquals(expected, paths.config());
        assertEquals(expected, paths.cache());
        assertEquals(expected, paths.state());
        assertEquals(expected, paths.runtime());
    }

    @Test void copperbenchHomeWinsAndLegacyMcreatorHomeRemainsCompatible() {
        Path preferred = home.resolve("preferred").toAbsolutePath();
        Path legacy = home.resolve("legacy").toAbsolutePath();
        Map<String, String> both = Map.of("COPPERBENCH_HOME", preferred.toString(), "MCREATOR_HOME", legacy.toString());
        UserDataPaths paths = UserDataPaths.resolve(RuntimePlatform.detect("Linux", "x86_64"), both, home);
        assertEquals(preferred.normalize(), paths.data());
        assertEquals(preferred.normalize(), paths.config());
        assertEquals(preferred.normalize(), paths.cache());

        UserDataPaths legacyPaths = UserDataPaths.resolve(RuntimePlatform.detect("Linux", "x86_64"),
                Map.of("MCREATOR_HOME", legacy.toString()), home);
        assertEquals(legacy.normalize(), legacyPaths.data());
        assertEquals(legacy.normalize(), legacyPaths.runtime());
    }
}
