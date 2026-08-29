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

class CefUtilsAccessibilityTest {

	@Test void completeRendererAccessibilityIsAlwaysConfiguredOnce() {
		List<String> arguments = new ArrayList<>();

		CefUtils.addAccessibilityArguments(arguments);
		CefUtils.addAccessibilityArguments(arguments);

		assertEquals(List.of("--force-renderer-accessibility=complete"), arguments);
	}

	@Test @EnabledOnOs(OS.WINDOWS)
	void windowsUsesNativeWindowedRenderingForPlatformAccessibility() {
		assertFalse(CefUtils.useOSR());
	}
}
