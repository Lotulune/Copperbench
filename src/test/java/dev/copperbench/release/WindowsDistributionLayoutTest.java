/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsDistributionLayoutTest {

	@Test void exportRecipeBundlesJdkJcefLicenseAndPlugins() throws Exception {
		String gradle = Files.readString(Path.of("platform/windows/windows.gradle"));
		assertTrue(gradle.contains("bundledJrePath = 'jdk'"));
		assertTrue(gradle.contains("from 'jdk/jbr25_win_64'"));
		assertTrue(gradle.contains("from file('LICENSE.txt')"));
		assertTrue(gradle.contains("into('plugins')"));
		assertTrue(gradle.contains("into('license')"));
		assertTrue(gradle.contains("docs/user/README.md"));
		assertTrue(gradle.contains("copperbench.exe"));
		assertFalse(gradle.contains("mcreator.exe"));
	}

	@Test void existingWin64ExportContainsTheRuntimeIfPresent() {
		Path win64 = Path.of("build/export/win64");
		Assumptions.assumeTrue(Files.isDirectory(win64), "Windows export has not been built in this checkout");
		assertTrue(WindowsDistributionLayout.missing(win64).isEmpty(),
				() -> "Missing: " + WindowsDistributionLayout.missing(win64));
		assertFalse(Files.exists(win64.resolve("mcreator.exe")));
		for (var plugin : BundledPluginInventory.FIRST_PARTY)
			assertTrue(Files.isRegularFile(win64.resolve("plugins/" + plugin.packageName() + ".zip")),
					plugin.packageName());
	}
}
