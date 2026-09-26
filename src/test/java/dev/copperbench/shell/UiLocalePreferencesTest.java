package dev.copperbench.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UiLocalePreferencesTest {
    @TempDir Path directory;

    @Test void persistsAcrossReadsAndRejectsUnsupportedLocales() throws Exception {
        Path preference = directory.resolve("config/ui-language.txt");
        assertEquals("zh", UiLocalePreferences.read(preference));
        UiLocalePreferences.save(preference, "en");
        assertEquals("en", UiLocalePreferences.read(preference));
        assertThrows(IllegalArgumentException.class, () -> UiLocalePreferences.save(preference, "../invalid"));
        assertEquals("en", UiLocalePreferences.read(preference));
        UiLocalePreferences.save(preference, "zh");
        assertEquals("zh", UiLocalePreferences.read(preference));
        Files.writeString(preference, "invalid");
        assertEquals("zh", UiLocalePreferences.read(preference));
    }

    @Test void failedSaveDoesNotReportSuccess() throws Exception {
        Path blocker = directory.resolve("not-a-directory");
        Files.writeString(blocker, "keep");
        assertThrows(java.io.IOException.class, () -> UiLocalePreferences.save(blocker.resolve("ui-language.txt"), "en"));
        assertEquals("keep", Files.readString(blocker));
    }
}
