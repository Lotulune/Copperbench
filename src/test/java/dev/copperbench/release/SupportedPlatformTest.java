package dev.copperbench.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedPlatformTest {

	@Test void windows11IsSupportedAndWindows10IsNot() {
		assertEquals("Windows 11", SupportedPlatform.MINIMUM_OS);
		assertTrue(SupportedPlatform.isSupported("Windows 11", 22000));
		assertTrue(SupportedPlatform.isSupported("Windows 11", 26100));
		assertFalse(SupportedPlatform.isSupported("Windows 10", 19045));
		assertFalse(SupportedPlatform.isSupported("Linux", 26100));
		assertFalse(SupportedPlatform.isSupported("Mac OS X", 26100));
	}

	@Test void buildOverrideRefusesWindows10AndAllowsWindows11() {
		String previous = System.getProperty("copperbench.windows.build");
		try {
			System.setProperty("copperbench.windows.build", "19045");
			assertFalse(SupportedPlatform.currentHostSupported());
			System.setProperty("copperbench.windows.build", "26100");
			assertTrue(SupportedPlatform.currentHostSupported());
		} finally {
			if (previous == null)
				System.clearProperty("copperbench.windows.build");
			else
				System.setProperty("copperbench.windows.build", previous);
		}
	}
}
