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
		String previousOs = System.getProperty("os.name");
		try {
			System.setProperty("os.name", "Windows 11");
			System.setProperty("copperbench.windows.build", "19045");
			assertFalse(SupportedPlatform.currentHostSupported());
			System.setProperty("copperbench.windows.build", "26100");
			assertTrue(SupportedPlatform.currentHostSupported());
		} finally {
			if (previous == null)
				System.clearProperty("copperbench.windows.build");
			else
				System.setProperty("copperbench.windows.build", previous);
			System.setProperty("os.name", previousOs);
		}
	}

	@Test void stage15LinuxCandidateCanRunWithoutBecomingFormallySupported() {
		String oldOs = System.getProperty("os.name");
		String oldArch = System.getProperty("os.arch");
		String oldCandidate = System.getProperty(SupportedPlatform.STAGE15_LINUX_CANDIDATE_PROPERTY);
		try {
			System.setProperty("os.name", "Linux");
			System.setProperty("os.arch", "x86_64");
			System.setProperty(SupportedPlatform.STAGE15_LINUX_CANDIDATE_PROPERTY, "true");
			assertFalse(SupportedPlatform.currentHostSupported());
			assertTrue(SupportedPlatform.currentHostRunnable());
			System.setProperty("os.arch", "aarch64");
			assertFalse(SupportedPlatform.currentHostRunnable());
		} finally {
			if (oldOs == null) System.clearProperty("os.name"); else System.setProperty("os.name", oldOs);
			if (oldArch == null) System.clearProperty("os.arch"); else System.setProperty("os.arch", oldArch);
			if (oldCandidate == null) System.clearProperty(SupportedPlatform.STAGE15_LINUX_CANDIDATE_PROPERTY);
			else System.setProperty(SupportedPlatform.STAGE15_LINUX_CANDIDATE_PROPERTY, oldCandidate);
		}
	}
}
