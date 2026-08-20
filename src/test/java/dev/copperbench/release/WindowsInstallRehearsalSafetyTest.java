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

class WindowsInstallRehearsalSafetyTest {

	@Test void rehearsalScriptRefusesAnExistingInstallAndProtectsWorkspaces() throws Exception {
		String script = Files.readString(Path.of("scripts/verify-stage-8-install-rehearsal.ps1"));
		assertTrue(script.contains("AllowExistingInstall"));
		assertTrue(script.contains("WOW6432Node"));
		assertTrue(script.contains("Refusing to run UninstallPrevious against a real install"));
		assertTrue(script.contains("g7-rehearsal\\workspace") || script.contains("g7-rehearsal'"));
		assertTrue(script.contains("workspace.mcreator"));
		assertTrue(script.contains("g7-rehearsal-keep.txt"));
		assertFalse(script.contains("Remove-Item -LiteralPath $userFolder"));
		assertTrue(script.contains("/S"));
		assertTrue(script.contains("/D="));
	}

	@Test void nsisSilentUpgradeKeepsUserDataByDefault() throws Exception {
		String nsis = Files.readString(Path.of("platform/windows/installer/install.nsi"));
		assertTrue(nsis.contains("Function un.onInit"));
		assertTrue(nsis.contains("StrCpy $keepUserDataState 1"));
		assertTrue(nsis.contains("ExecWait '\"$0\" /S _?=$1'"));
	}
}
