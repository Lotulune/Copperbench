/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.chromium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CefUtilsAccessibilityTest {

	@Test void completeRendererAccessibilityIsAlwaysConfiguredOnce() {
		List<String> arguments = new ArrayList<>();

		CefUtils.addAccessibilityArguments(arguments);
		CefUtils.addAccessibilityArguments(arguments);

		assertEquals(List.of("--force-renderer-accessibility=complete"), arguments);
	}

	@Test @EnabledOnOs(OS.WINDOWS)
	void windowsUsesWindowedRenderingNormallyAndOsrForSoftwareFallback() {
		assertFalse(CefUtils.useOSROnWindows(true, false));
		assertTrue(CefUtils.useOSROnWindows(false, false));
		assertTrue(CefUtils.useOSROnWindows(true, true));
	}

	@Test void linuxX11OsrUsesSoftwareRenderingFallbackWithoutAffectingWayland() {
		assertTrue(CefUtils.useSoftwareRenderingOnLinuxX11("x11", true));
		assertTrue(CefUtils.useSoftwareRenderingOnLinuxX11("X11", true));
		assertFalse(CefUtils.useSoftwareRenderingOnLinuxX11("wayland", true));
		assertFalse(CefUtils.useSoftwareRenderingOnLinuxX11("x11", false));
		assertFalse(CefUtils.useSoftwareRenderingOnLinuxX11(null, true));
	}

	@Test void linuxHelperDisablesOnlyForkCanaryReseedingAndNormalizesConflictingArguments() {
		List<String> arguments = new ArrayList<>(List.of("--force-renderer-accessibility=complete",
				"--change-stack-guard-on-fork=enable", "--disable-features=Vulkan",
				"--change-stack-guard-on-fork=disable"));

		CefUtils.addLinuxHelperCompatibilityArguments(arguments, true);
		CefUtils.addLinuxHelperCompatibilityArguments(arguments, true);

		assertEquals(List.of("--force-renderer-accessibility=complete", "--disable-features=Vulkan",
				"--change-stack-guard-on-fork=disable"), arguments);
	}

	@Test void nonLinuxHelperArgumentsRemainUnchanged() {
		List<String> arguments = new ArrayList<>(List.of("--change-stack-guard-on-fork=enable"));
		CefUtils.addLinuxHelperCompatibilityArguments(arguments, false);
		assertEquals(List.of("--change-stack-guard-on-fork=enable"), arguments);
	}
}
