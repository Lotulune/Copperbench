package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockbenchConfigurationTest {
	@TempDir Path root;
	@Test void onlyExplicitSelectionWritesAndTheSelectionSurvivesRecreation() throws Exception {
		Path file = root.resolve("settings/blockbench.json");
		var config = new BlockbenchConfiguration(file, new BlockbenchInstallationDetector(path -> "5.1.6"));
		assertNull(config.executable());
		assertFalse(Files.exists(file.getParent()));
		Path executable = root.resolve("Blockbench.exe");
		Files.write(executable, new byte[] { 1 });
		executable.toFile().setExecutable(true);
		config.select(executable);
		assertEquals(executable.toRealPath(), new BlockbenchConfiguration(file).executable());
		assertThrows(BlockbenchBridgeException.class, () -> config.select(root.resolve("arbitrary.exe")));
		assertEquals(executable.toRealPath(), config.executable());
	}
	@Test void invalidConfigurationDoesNotLaunchOrRewriteAnything() throws Exception {
		Path file = root.resolve("blockbench.json");
		Files.writeString(file, "{broken");
		assertNull(new BlockbenchConfiguration(file).executable());
		assertEquals("{broken", Files.readString(file));
	}
	@Test void acceptsOfficialPortableLauncherButNotTheInstaller() throws Exception {
		Path file = root.resolve("settings/blockbench.json");
		var config = new BlockbenchConfiguration(file, new BlockbenchInstallationDetector(path -> "5.1.6"));
		Path portable = root.resolve("Blockbench_5.1.6_portable.exe");
		Files.write(portable, new byte[] { 1 }); portable.toFile().setExecutable(true);
		config.select(portable);
		assertEquals(portable.toRealPath(), new BlockbenchConfiguration(file).executable());
		Path installer = root.resolve("Blockbench_x64_5.1.6.exe");
		Files.write(installer, new byte[] { 1 }); installer.toFile().setExecutable(true);
		assertThrows(BlockbenchBridgeException.class, () -> config.select(installer));
		assertEquals(portable.toRealPath(), config.executable());
	}
	@Test void skippingOnboardingPersistsWithoutLosingSelectedInstallation() throws Exception {
		Path file = root.resolve("settings/blockbench.json");
		var config = new BlockbenchConfiguration(file, new BlockbenchInstallationDetector(path -> "5.1.6"));
		assertFalse(config.onboardingDismissed());
		config.dismissOnboarding();
		assertTrue(new BlockbenchConfiguration(file).onboardingDismissed());
		Path executable = root.resolve("Blockbench.exe"); Files.write(executable, new byte[] { 1 }); executable.toFile().setExecutable(true);
		config.select(executable);
		assertTrue(config.onboardingDismissed());
		config.dismissOnboarding();
		assertEquals(executable.toRealPath(), config.executable());
	}
}
