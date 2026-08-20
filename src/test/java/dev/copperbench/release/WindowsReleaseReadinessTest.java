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

class WindowsReleaseReadinessTest {

	@Test void vmwareProbeDoesNotCreateAVm() throws Exception {
		String script = Files.readString(Path.of("scripts/verify-stage-8-vmware-ready.ps1"));
		assertTrue(script.contains("createsVirtualMachine = $false"));
		assertFalse(script.contains("New-VM"));
		assertFalse(script.contains("Enable-WindowsOptionalFeature"));
	}

	@Test void signingProbeDoesNotInventCertificates() throws Exception {
		String script = Files.readString(Path.of("scripts/verify-stage-8-signing-ready.ps1"));
		assertTrue(script.contains("jsign-7.4.jar"));
		assertTrue(script.contains("Get-AuthenticodeSignature"));
		assertFalse(script.contains("New-SelfSignedCertificate"));
		String gradle = Files.readString(Path.of("platform/windows/windows.gradle"));
		assertTrue(gradle.contains("jsign-7.4.jar"));
		assertTrue(gradle.contains("WIN_CERT_KEYSTORE"));
	}
}
