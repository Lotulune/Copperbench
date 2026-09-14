package dev.copperbench.shell;

import dev.copperbench.assets.BlockbenchConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchStartupPanelTest {
	@TempDir Path root;

	@Test void firstStartWithoutWorkspaceIsSkippableAndKeepsSettingsReachableAfterReopen() throws Exception {
		Path configFile = root.resolve("config/blockbench.json");
		AtomicInteger guides = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new BlockbenchStartupPanel(new BlockbenchConfiguration(configFile), guides::incrementAndGet);
			assertFalse(Files.exists(configFile), "Rendering first-start setup must not write settings");
			var guide = (JButton) named(panel, "blockbench-startup-guide");
			guide.doClick();
			assertEquals(1, guides.get());
			assertFalse(Files.exists(configFile), "Opening guidance must not persist an installation decision");
			((JButton) named(panel, "blockbench-startup-skip")).doClick();
			assertTrue(new BlockbenchConfiguration(configFile).onboardingDismissed());
			var reopened = new BlockbenchStartupPanel(new BlockbenchConfiguration(configFile), guides::incrementAndGet);
			assertFalse(named(reopened, "blockbench-startup-introduction").isVisible());
			assertFalse(named(reopened, "blockbench-startup-skip").isVisible());
			assertTrue(named(reopened, "blockbench-startup-guide").isVisible());
		});
	}

	private static Component named(Container root, String name) {
		for (Component child : root.getComponents()) {
			if (name.equals(child.getName())) return child;
			if (child instanceof Container container) {
				Component found = named(container, name);
				if (found != null) return found;
			}
		}
		return null;
	}
}
