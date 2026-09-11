package dev.copperbench.platform;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DesktopSessionCapabilitiesTest {
	@Test void waylandIsThePrimaryStage15TargetEvenWhenXwaylandDisplayExists() {
		RuntimePlatform linux = RuntimePlatform.detect("Linux", "x86_64");
		var capabilities = DesktopSessionCapabilities.detect(linux,
				Map.of("XDG_SESSION_TYPE", "wayland", "WAYLAND_DISPLAY", "wayland-0", "DISPLAY", ":0"), false);
		assertEquals(DesktopSessionCapabilities.Session.WAYLAND, capabilities.session());
		assertTrue(capabilities.waylandDisplayPresent());
		assertTrue(capabilities.x11DisplayPresent());
		var desktop = capabilities.toJson().getAsJsonObject("desktop");
		assertEquals("stage15-primary-target", desktop.get("certificationRole").getAsString());
		assertTrue(desktop.get("stage15CertificationPending").getAsBoolean());
		assertEquals(0, desktop.getAsJsonArray("knownLimitations").size());
	}

	@Test void x11IsReportedAsASeparateCompatibilityTarget() {
		RuntimePlatform linux = RuntimePlatform.detect("Linux", "amd64");
		var capabilities = DesktopSessionCapabilities.detect(linux,
				Map.of("XDG_SESSION_TYPE", "x11", "DISPLAY", ":0"), false);
		assertEquals(DesktopSessionCapabilities.Session.X11, capabilities.session());
		var desktop = capabilities.toJson().getAsJsonObject("desktop");
		assertEquals("stage15-compatibility-target", desktop.get("certificationRole").getAsString());
		assertFalse(desktop.getAsJsonArray("knownLimitations").isEmpty());
	}

	@Test void headlessLinuxCannotSilentlyMasqueradeAsGraphical() {
		RuntimePlatform linux = RuntimePlatform.detect("Linux", "x86_64");
		var capabilities = DesktopSessionCapabilities.detect(linux, Map.of("WAYLAND_DISPLAY", "wayland-0"), true);
		assertEquals(DesktopSessionCapabilities.Session.HEADLESS, capabilities.session());
		var desktop = capabilities.toJson().getAsJsonObject("desktop");
		assertEquals("not-graphical", desktop.get("certificationRole").getAsString());
		assertFalse(desktop.getAsJsonArray("knownLimitations").isEmpty());
	}

	@Test void unknownLinuxBackendIsExplicitlyUnverified() {
		RuntimePlatform linux = RuntimePlatform.detect("Linux", "x86_64");
		var capabilities = DesktopSessionCapabilities.detect(linux, Map.of(), false);
		assertEquals(DesktopSessionCapabilities.Session.UNKNOWN, capabilities.session());
		assertEquals("unverified", capabilities.toJson().getAsJsonObject("desktop")
				.get("certificationRole").getAsString());
	}

	@Test void nonLinuxHostsDoNotAcquireStage15CertificationState() {
		RuntimePlatform windows = RuntimePlatform.detect("Windows 11", "amd64");
		var capabilities = DesktopSessionCapabilities.detect(windows, Map.of(), false);
		assertEquals(DesktopSessionCapabilities.Session.NATIVE, capabilities.session());
		var desktop = capabilities.toJson().getAsJsonObject("desktop");
		assertEquals("not-applicable", desktop.get("certificationRole").getAsString());
		assertFalse(desktop.has("stage15CertificationPending"));
	}
}
