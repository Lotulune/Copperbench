package dev.copperbench.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class UiLocalePreferencesTest {
    @TempDir Path directory;

    @Test void persistsAcrossReadsAndRejectsUnsupportedLocales() throws Exception {
        Path preference = directory.resolve("config/ui-language.txt");
        assertEquals("en", UiLocalePreferences.read(preference, null, Locale.US));
        UiLocalePreferences.save(preference, "en");
        assertEquals("en", UiLocalePreferences.read(preference));
        assertThrows(IllegalArgumentException.class, () -> UiLocalePreferences.save(preference, "../invalid"));
        assertEquals("en", UiLocalePreferences.read(preference));
        UiLocalePreferences.save(preference, "zh");
        assertEquals("zh", UiLocalePreferences.read(preference, null, Locale.US));
        Files.writeString(preference, "invalid");
        assertEquals("en", UiLocalePreferences.read(preference, null, Locale.US));
    }

    @Test void installationChoiceAndExplicitPreferenceWinOverSystemLanguage() throws Exception {
        Path preference = directory.resolve("ui-language.txt");
        Path installed = directory.resolve("install-language.txt");
        assertEquals("zh", UiLocalePreferences.read(preference, installed, Locale.TRADITIONAL_CHINESE));
        assertEquals("en", UiLocalePreferences.read(preference, installed, Locale.JAPANESE));
        Files.writeString(installed, "zh\n");
        assertEquals("zh", UiLocalePreferences.read(preference, installed, Locale.US));
        UiLocalePreferences.save(preference, "en");
        assertEquals("en", UiLocalePreferences.read(preference, installed, Locale.SIMPLIFIED_CHINESE));
        Files.writeString(preference, "invalid");
        assertEquals("zh", UiLocalePreferences.read(preference, installed, Locale.US));
    }

    @Test void failedSaveDoesNotReportSuccess() throws Exception {
        Path blocker = directory.resolve("not-a-directory");
        Files.writeString(blocker, "keep");
        assertThrows(java.io.IOException.class, () -> UiLocalePreferences.save(blocker.resolve("ui-language.txt"), "en"));
        assertEquals("keep", Files.readString(blocker));
    }

    @Test void automaticDetectionNeverTurnsTheSystemLanguageIntoAnExplicitPreference() {
        Path preference = directory.resolve("ui-language.txt");
        assertEquals("zh", UiLocalePreferences.read(preference, null, Locale.SIMPLIFIED_CHINESE));
        assertFalse(Files.exists(preference));
        assertEquals("en", UiLocalePreferences.read(preference, null, Locale.US));
        assertFalse(Files.exists(preference));
    }
}
