package dev.copperbench.release;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductUpdatesTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void launchUsesTheLatestUiLanguageAndThemeAndKeepsThePreparedTarget() throws Exception {
        java.nio.file.Path request = directory.resolve("request.properties");
        java.nio.file.Files.writeString(request, "language=en\ntheme=dark\nversion=0.1.10\ntarget=installed-app\nscope=user\n");
        ProductUpdates.refreshLaunchPreferences(request, "zh", "light");
        var preferences = new java.util.Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(request)) { preferences.load(reader); }
        assertEquals("zh", preferences.getProperty("language"));
        assertEquals("light", preferences.getProperty("theme"));
        assertEquals("installed-app", preferences.getProperty("target"));
        assertEquals("0.1.10", preferences.getProperty("version"));
        assertEquals("user", preferences.getProperty("scope"));
    }
    private JsonObject release(String tag, boolean preview) {
        JsonObject release = new JsonObject(); release.addProperty("tag_name", tag); release.addProperty("draft", false); release.addProperty("prerelease", preview);
        JsonObject asset = new JsonObject(); asset.addProperty("name", "update.properties");
        asset.addProperty("browser_download_url", "https://github.com/Lotulune/Copperbench/releases/download/" + tag + "/update.properties");
        JsonArray assets = new JsonArray(); assets.add(asset); release.add("assets", assets); return release;
    }
    @Test void usesNumericStableVersionsAndSeparatesLinuxAndWindows() throws Exception {
        JsonArray releases = new JsonArray(); releases.add(release("v0.1.9", false)); releases.add(release("v0.1.10", false));
        releases.add(release("v2.0.0", true)); releases.add(release("v0.2.0-linux-stable", false));
        assertEquals("0.1.10", ProductUpdates.selectRelease(releases, "windows-x64", "0.1.8").orElseThrow().version());
        assertEquals("0.2.0", ProductUpdates.selectRelease(releases, "linux-x64", "0.1.8").orElseThrow().version());
        assertTrue(ProductUpdates.selectRelease(releases, "windows-x64", "0.1.10").isEmpty());
    }
    @Test void refusesAnAssetOutsideTheSelectedOfficialRelease() {
        JsonArray releases = new JsonArray(); JsonObject bad = release("v1.0.0", false);
        bad.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("browser_download_url", "https://example.org/installer.exe"); releases.add(bad);
        assertThrows(java.io.IOException.class, () -> ProductUpdates.selectRelease(releases, "windows-x64", "0.1.0"));
    }
}
