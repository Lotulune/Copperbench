package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformInstallRollbackTest {
    @TempDir Path directory;

    @Test void componentUpdateDoesNotStartTheLocationsHelper() throws Exception {
        Path missingRegistrar = directory.resolve("missing-registrar.exe");
        Path ini = directory.resolve("register.ini");
        assertTrue(PlatformInstall.registrationShortcuts(missingRegistrar, ini, true, true, InstallTrace.NONE).isEmpty());
        assertTrue(PlatformInstall.registrationShortcuts(missingRegistrar, ini, false, true, InstallTrace.NONE).isEmpty());
        assertFalse(Files.exists(ini));
        assertThrows(java.io.IOException.class,
                () -> PlatformInstall.registrationShortcuts(missingRegistrar, ini, true, false, InstallTrace.NONE),
                "Full setup must still invoke its registrar to resolve shell folders");
    }

    @Test void updateRegistrationDoesNotIncludeAnyShortcutInItsRollback() throws Exception {
        Properties locations = new Properties();
        locations.setProperty("ShortcutPolicy", "preserve");
        assertTrue(PlatformInstall.registrationShortcuts(locations, true).isEmpty());
        assertTrue(PlatformInstall.registrationShortcuts(locations, false).isEmpty());
    }

    @Test void fullAndLegacySetupKeepTheirRequestedShortcutContract() throws Exception {
        Properties locations = new Properties();
        Path programs = directory.resolve("programs"), desktop = directory.resolve("desktop");
        locations.setProperty("Programs", programs.toString()); locations.setProperty("DesktopPath", desktop.toString());
        assertEquals(List.of(programs.resolve("Copperbench/Copperbench.lnk")), PlatformInstall.registrationShortcuts(locations, false));
        assertEquals(List.of(programs.resolve("Copperbench/Copperbench.lnk"), desktop.resolve("Copperbench.lnk")), PlatformInstall.registrationShortcuts(locations, true));
        locations.setProperty("ShortcutPolicy", "create");
        assertEquals(2, PlatformInstall.registrationShortcuts(locations, true).size());
        locations.setProperty("ShortcutPolicy", "unknown");
        assertThrows(java.io.IOException.class, () -> PlatformInstall.registrationShortcuts(locations, true));
    }

    @Test void unchangedIntegrationFilesAreNotRewritten() throws Exception {
        Path shortcut = directory.resolve("Copperbench.lnk");
        Files.writeString(shortcut, "existing user-customized shortcut");
        byte[] original = Files.readAllBytes(shortcut);
        Files.setLastModifiedTime(shortcut, FileTime.fromMillis(1_650_000_000_000L));
        FileTime before = Files.getLastModifiedTime(shortcut);

        PlatformInstall.restore(Map.of(shortcut, original));

        assertArrayEquals(original, Files.readAllBytes(shortcut));
        assertEquals(before, Files.getLastModifiedTime(shortcut), "An unchanged shortcut must not receive another write");
    }

    @Test void changedAndMissingFilesAreRestoredAndNewFilesAreRemoved() throws Exception {
        Path changed = directory.resolve("changed.lnk"), missing = directory.resolve("missing.lnk"), created = directory.resolve("created.lnk");
        byte[] original = "original shortcut".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(changed, "replacement shortcut");
        Files.writeString(created, "new shortcut");
        Map<Path, byte[]> before = new LinkedHashMap<>();
        before.put(changed, original); before.put(missing, original); before.put(created, null);

        PlatformInstall.restore(before);

        assertArrayEquals(original, Files.readAllBytes(changed));
        assertArrayEquals(original, Files.readAllBytes(missing));
        assertFalse(Files.exists(created));
    }
}
