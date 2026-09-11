/* Copyright (C) 2026 Copperbench contributors. SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPreferencesMigrationTest {
    @TempDir Path home;
    private final RuntimePlatform linux = RuntimePlatform.detect("Linux", "amd64");

    private Path legacy(String name, String data) throws Exception {
        Path path = home.resolve(".copperbench").resolve(name);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, data);
    }

    @Test void importsModernFormatByteForByteAndPreservesOriginal() throws Exception {
        Path source = legacy("userpreferences", "{\"core\":{\"ui\":{\"backgroundSource\":\"None\"}}}");
        legacy("preferences", "older format must not win");
        LegacyPreferencesMigration.migrate(linux, Map.of(), home);
        Path active = home.resolve(".config/copperbench/userpreferences");
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(active));
        assertFalse(Files.exists(active.resolveSibling("preferences")));
        if (PrivatePathPermissions.posixSupported(active))
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(active));
        Files.writeString(active, "new setting");
        LegacyPreferencesMigration.migrate(linux, Map.of(), home);
        assertEquals("new setting", Files.readString(active));
        assertTrue(Files.readString(source).contains("None"));
    }

    @Test void importsOldFormatIntoCustomXdgLocationForExistingConverter() throws Exception {
        Path source = legacy("preferences", "{\"ui\":{\"backgroundSource\":\"None\"}}");
        Path configHome = home.resolve("custom-config");
        LegacyPreferencesMigration.migrate(linux, Map.of("XDG_CONFIG_HOME", configHome.toString()), home);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(configHome.resolve("copperbench/preferences")));
        assertTrue(Files.exists(source));
    }

    @Test void existingOldFormatInActiveDirectoryAlsoWins() throws Exception {
        legacy("userpreferences", "legacy modern");
        Path active = home.resolve(".config/copperbench/preferences");
        Files.createDirectories(active.getParent());
        Files.writeString(active, "active old");
        LegacyPreferencesMigration.migrate(linux, Map.of(), home);
        assertEquals("active old", Files.readString(active));
        assertFalse(Files.exists(active.resolveSibling("userpreferences")));
    }

    @Test void explicitHomesRemainIsolatedAndWindowsDoesNotMigrate() throws Exception {
        legacy("userpreferences", "legacy");
        for (String key : new String[]{"COPPERBENCH_HOME", "MCREATOR_HOME"}) {
            Path isolated = home.resolve(key);
            LegacyPreferencesMigration.migrate(linux, Map.of(key, isolated.toString()), home);
            assertFalse(Files.exists(isolated));
        }
        LegacyPreferencesMigration.migrate(RuntimePlatform.detect("Windows 11", "amd64"), Map.of(), home);
        assertFalse(Files.exists(home.resolve(".config")));
    }
}
