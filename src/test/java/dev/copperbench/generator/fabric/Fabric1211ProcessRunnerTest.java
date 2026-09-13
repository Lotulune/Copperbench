/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import dev.copperbench.platform.RuntimePlatform;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric1211ProcessRunnerTest {
	@Test void recognizesTheObservedWindowsOpenGlDriverFailure() {
		assertEquals("WINDOWS_OPENGL_INITIALIZATION_FAILED", Fabric1211ProcessRunner.graphicalFailureCode(
				RuntimePlatform.OperatingSystem.WINDOWS,
				"Window$WindowInitFailed: GLFW error 65542: WGL: The driver does not appear to support OpenGL"));
		assertNull(Fabric1211ProcessRunner.graphicalFailureCode(RuntimePlatform.OperatingSystem.WINDOWS,
				"[Render thread/INFO] OpenGL vendor: NVIDIA"));
		assertNull(Fabric1211ProcessRunner.graphicalFailureCode(RuntimePlatform.OperatingSystem.WINDOWS,
				"GLFW error 65550: X11: The DISPLAY environment variable is missing"));
		assertEquals("LINUX_DISPLAY_UNAVAILABLE", Fabric1211ProcessRunner.graphicalFailureCode(
				RuntimePlatform.OperatingSystem.LINUX,
				"GLFW error 65550: X11: The DISPLAY environment variable is missing"));
	}

	@Test void recognizesRootAndQualifiedRunClientTasks() {
		assertTrue(Fabric1211ProcessRunner.isClientRun(List.of("runClient")));
		assertTrue(Fabric1211ProcessRunner.isClientRun(List.of(":packloader:runClient")));
		assertFalse(Fabric1211ProcessRunner.isClientRun(List.of("build")));
	}

	@Test void requiresTheFullServerStabilityWindow() {
		Instant readyAt = Instant.parse("2026-08-29T00:00:00Z");
		assertFalse(Fabric1211ProcessRunner.SystemProcessRunner.stabilityWindowSatisfied(
				readyAt, readyAt.plusMillis(1999)));
		assertTrue(Fabric1211ProcessRunner.SystemProcessRunner.stabilityWindowSatisfied(
				readyAt, readyAt.plusSeconds(2)));
		assertFalse(Fabric1211ProcessRunner.SystemProcessRunner.stabilityWindowSatisfied(
				null, readyAt.plusSeconds(10)));
	}

	@Test void recognizesRootAndQualifiedRunServerTasks() {
		assertTrue(Fabric1211ProcessRunner.isServerRun(List.of("runServer")));
		assertTrue(Fabric1211ProcessRunner.isServerRun(List.of(":server:runServer")));
		assertFalse(Fabric1211ProcessRunner.isServerRun(List.of("runDatagen")));
	}

	@Test void recognizesVanillaDedicatedServerReadinessLine() {
		assertTrue(Fabric1211ProcessRunner.isMinecraftServerReadyLine(
				"[Server thread/INFO]: Done (3.214s)! For help, type \"help\""));
		assertFalse(Fabric1211ProcessRunner.isMinecraftServerReadyLine(
				"[Server thread/INFO]: COPPERBENCH_STAGE7_FABRIC262_READY"));
		assertFalse(Fabric1211ProcessRunner.isMinecraftServerReadyLine("Done loading data packs"));
	}

	@Test void recognizesImmediateDedicatedServerFatalLines() {
		assertTrue(Fabric1211ProcessRunner.isMinecraftServerFatalLine(
				"[Server thread/FATAL] [ne.mi.co.ForgeMod/]: Preparing crash report"));
		assertTrue(Fabric1211ProcessRunner.isMinecraftServerFatalLine(
				"Attempted to load class net/minecraft/client/server/LanServerPinger for invalid dist DEDICATED_SERVER"));
		assertFalse(Fabric1211ProcessRunner.isMinecraftServerFatalLine(
				"[Server thread/INFO]: Done (3.214s)! For help, type \"help\""));
	}

	@Test void classifiesLinuxGraphicalInitializationFailures() {
		assertEquals("LINUX_DISPLAY_UNAVAILABLE", Fabric1211ProcessRunner.linuxGraphicalFailureCode(
				"GLFW error 65550: X11: The DISPLAY environment variable is missing"));
		assertEquals("LINUX_DISPLAY_UNAVAILABLE", Fabric1211ProcessRunner.linuxGraphicalFailureCode(
				"GLFW error 65550: Wayland: Failed to connect to display"));
		assertEquals("LINUX_GLFW_INITIALIZATION_FAILED", Fabric1211ProcessRunner.linuxGraphicalFailureCode(
				"Failed to initialize GLFW, errors: [65544]"));
		assertEquals("LINUX_OPENGL_INITIALIZATION_FAILED", Fabric1211ProcessRunner.linuxGraphicalFailureCode(
				"libGL error: failed to load driver: iris"));
		assertEquals("LINUX_OPENGL_INITIALIZATION_FAILED", Fabric1211ProcessRunner.linuxGraphicalFailureCode(
				"GLFW error 65543: GLX: Failed to create context: GLXBadFBConfig"));
		assertNull(Fabric1211ProcessRunner.linuxGraphicalFailureCode("[Render thread/INFO] OpenAL initialized"));
	}
	@Test void profilesSelectBundledJdkCompatibleWithTheirGradleRuntime() {
		RuntimePlatform platform = RuntimePlatform.current();
		assertTrue(Fabric1211Generator.Profile.FABRIC_1201.jdkRelativePath().equals(platform.sourceJavaHome(17)));
		assertTrue(Fabric1211Generator.Profile.FABRIC_1211.jdkRelativePath().equals(platform.sourceJavaHome(21)));
		assertTrue(Fabric1211Generator.Profile.FABRIC_261.jdkRelativePath().equals(platform.sourceJavaHome(25)));
		assertTrue(Fabric1211Generator.Profile.FABRIC_262.jdkRelativePath().equals(platform.sourceJavaHome(25)));
	}
}
