package dev.copperbench.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxDesktopPathIntegrationTest {
	@Test void desktopSubsystemsUsePurposeSpecificUserDirectories() throws Exception {
		String logging = Files.readString(Path.of("src/main/java/net/mcreator/io/LoggingSystem.java"));
		assertTrue(logging.contains("UserFolderManager.getStateFolder().getAbsolutePath()"));

		String preferences = Files.readString(Path.of("src/main/java/net/mcreator/preferences/PreferencesManager.java"));
		assertTrue(preferences.contains("getFileFromConfigFolder(\"userpreferences\")"));
		assertTrue(preferences.contains("getFileFromConfigFolder(\"preferences\")"));
		assertFalse(preferences.contains("getFileFromUserFolder(\"userpreferences\")"));

		String singleApp = Files.readString(Path.of("src/main/java/net/mcreator/util/SingleAppHandler.java"));
		assertTrue(singleApp.contains("lockFolder(UserFolderManager.getRuntimeFolder())"));

		String cef = Files.readString(Path.of("src/main/java/net/mcreator/ui/chromium/CefUtils.java"));
		assertTrue(cef.contains("getFileFromStateFolder(\"cef_log.txt\")"));
		assertTrue(cef.contains("getFileFromCacheFolder(\"cef\")"));
		assertFalse(cef.contains("getFileFromUserFolder(\"/cef_log.txt\")"));
	}

	@Test void headlessAndLinuxLauncherArePlatformNeutral() throws Exception {
		String headless = Files.readString(Path.of(
				"src/main/java/dev/copperbench/headless/HeadlessProductLauncher.java"));
		assertFalse(headless.contains("copperbench.exe headless"));

		String launcher = Files.readString(Path.of("platform/linux/copperbench.sh"));
		assertTrue(launcher.contains("SCRIPT_DIR="));
		assertTrue(launcher.contains("$SCRIPT_DIR/jdk/bin/java"));
		assertTrue(launcher.contains("net.mcreator.Launcher \"$@\""));
		assertFalse(launcher.toLowerCase(java.util.Locale.ROOT).contains("powershell"));
		assertFalse(launcher.toLowerCase(java.util.Locale.ROOT).contains(".exe"));
	}
}
