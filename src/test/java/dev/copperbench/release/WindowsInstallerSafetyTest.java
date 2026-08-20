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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsInstallerSafetyTest {

	@Test void nsisUninstallDefaultsToKeepingUserDataAndNeverWipesWorkspaces() throws Exception {
		String nsis = Files.readString(Path.of("platform/windows/installer/install.nsi"));
		assertTrue(nsis.contains("Function un.onInit"));
		assertTrue(nsis.contains("StrCpy $keepUserDataState 1"));
		assertTrue(nsis.contains("$PROFILE\\.copperbench"));
		assertFalse(nsis.contains("$PROFILE\\.mcreator"));
		assertFalse(nsis.contains("RMDir /r \"$DOCUMENTS"));
		assertFalse(nsis.contains("RMDir /r \"$PROFILE\\Documents"));
		assertTrue(nsis.contains("User-chosen workspace directories are never deleted"));
		assertTrue(nsis.contains("Function UninstallPrevious"));
	}

	@Test void msixDoesNotRequireAnAccountOrInternetCapability() throws Exception {
		String manifest = Files.readString(Path.of("platform/windows/msix/AppxManifest.xml"));
		assertTrue(manifest.contains("runFullTrust"));
		assertFalse(manifest.contains("internetClient"));
		assertFalse(manifest.contains("enterpriseAuthentication"));
		assertTrue(manifest.contains("Name=\"dev.copperbench.studio\""));
	}
}
