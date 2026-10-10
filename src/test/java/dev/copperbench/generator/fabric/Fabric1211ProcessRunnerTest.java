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
	@Test void renderingObservationRequiresAnActualAtlasCreationLogOnTheRenderThread() {
		assertTrue(Fabric1211ProcessRunner.isMinecraftClientRenderingLine(
				"[22:10:04] [Render thread/INFO] (Minecraft) Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas"));
		assertTrue(Fabric1211ProcessRunner.isMinecraftClientRenderingLine(
				"[Render thread/INFO] [minecraft/TextureAtlas]: Created: 256x128x0 minecraft:blocks-atlas"));
		for (String line : List.of("[Render thread/INFO] COPPERBENCH_STAGE3_READY",
				"[Render thread/INFO] OpenAL initialized", "[Render thread/INFO] Sound engine started",
				"[Server thread/INFO] Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas",
				"[Render thread/INFO] Created: 0x512x4 minecraft:textures/atlas/blocks.png-atlas",
				"[Render thread/ERROR] Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas"))
			assertFalse(Fabric1211ProcessRunner.isMinecraftClientRenderingLine(line), line);
		assertFalse(Fabric1211ProcessRunner.isMinecraftClientRenderingLine(null));
	}

	@Test void managedBuildUsesTheSetupCacheAndMirrorScriptWhilePreservingExplicitOverrides(
			@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
		java.nio.file.Path productHome = root.resolve("product-cache");
		dev.copperbench.network.ChinaMirrorService.applyUserHome(productHome, true);
		var environment = new java.util.HashMap<String, String>();
		Fabric1211ProcessRunner.SystemProcessRunner.configureGradleHome(environment, productHome);
		java.nio.file.Path effective = java.nio.file.Path.of(environment.get("GRADLE_USER_HOME"));
		assertEquals(productHome, effective);
		String init = java.nio.file.Files.readString(effective.resolve("init.d")
				.resolve(dev.copperbench.network.ChinaMirrorService.INIT_SCRIPT_NAME));
		assertTrue(init.contains("maven.aliyun.com"));
		environment.put("GRADLE_USER_HOME", root.resolve("external-cache").toString());
		Fabric1211ProcessRunner.SystemProcessRunner.configureGradleHome(environment, productHome);
		assertEquals(root.resolve("external-cache").toString(), environment.get("GRADLE_USER_HOME"));
		environment.put("COPPERBENCH_GRADLE_USER_HOME", root.resolve("explicit-cache").toString());
		Fabric1211ProcessRunner.SystemProcessRunner.configureGradleHome(environment, productHome);
		assertEquals(root.resolve("explicit-cache").toString(), environment.get("GRADLE_USER_HOME"));
	}

	@Test void externalGradleProcessReceivesTheSameUserHomeAsGuiSetup() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("COPPERBENCH_STAGE5_GRADLE_EXECUTABLE") == null);
		java.nio.file.Path root = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build").toAbsolutePath(), "stage17-process-home-");
		boolean windows = RuntimePlatform.current().operatingSystem() == RuntimePlatform.OperatingSystem.WINDOWS;
		java.nio.file.Path wrapper = root.resolve(windows ? "gradlew.bat" : "gradlew");
		java.nio.file.Files.writeString(wrapper, windows ? "@echo off\r\necho OBSERVED_GRADLE_HOME=%GRADLE_USER_HOME%\r\n"
				: "#!/bin/sh\nprintf 'OBSERVED_GRADLE_HOME=%s\\n' \"$GRADLE_USER_HOME\"\n");
		if (!windows) assertTrue(wrapper.toFile().setExecutable(true));
		List<String> output = new java.util.concurrent.CopyOnWriteArrayList<>();
		var result = Fabric1211ProcessRunner.system().run(root, List.of("build"), java.time.Duration.ofSeconds(15), output::add);
		assertEquals(0, result.exitCode());
		var expected = new java.util.HashMap<>(System.getenv());
		Fabric1211ProcessRunner.SystemProcessRunner.configureGradleHome(expected, net.mcreator.io.UserFolderManager.getGradleHome().toPath());
		assertTrue(output.contains("OBSERVED_GRADLE_HOME=" + expected.get("GRADLE_USER_HOME")), output.toString());
	}

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

	@Test
	@org.junit.jupiter.api.parallel.ResourceLock("MCREATOR_PREFERENCES")
	void externalGradleProcessHonorsTheOfflinePreference() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("COPPERBENCH_STAGE5_GRADLE_EXECUTABLE") == null);
		var root = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build").toAbsolutePath(), "m3-process-offline-");
		boolean windows = RuntimePlatform.current().operatingSystem() == RuntimePlatform.OperatingSystem.WINDOWS;
		var wrapper = root.resolve(windows ? "gradlew.bat" : "gradlew");
		java.nio.file.Files.writeString(wrapper, windows
				? "@echo off\r\n:arguments\r\nif \"%~1\"==\"\" exit /b 0\r\necho OBSERVED_ARG=%~1\r\nshift\r\ngoto arguments\r\n"
				: "#!/bin/sh\nprintf 'OBSERVED_ARG=%s\\n' \"$@\"\n");
		if (!windows) assertTrue(wrapper.toFile().setExecutable(true));
		var previousPreferences = net.mcreator.preferences.PreferencesManager.PREFERENCES;
		if (previousPreferences == null)
			net.mcreator.preferences.PreferencesManager.PREFERENCES = new net.mcreator.preferences.data.PreferencesData();
		var preference = net.mcreator.preferences.PreferencesManager.PREFERENCES.gradle.offline;
		boolean previous = preference.get();
		var runner = Fabric1211ProcessRunner.system();
		try {
			for (boolean offline : List.of(false, true)) {
				preference.set(offline);
				List<String> output = new java.util.concurrent.CopyOnWriteArrayList<>();
				var result = runner.run(root, List.of("help"), java.time.Duration.ofSeconds(15), output::add);
				assertEquals(0, result.exitCode(), output.toString());
				assertEquals(offline ? 1 : 0, output.stream().filter("OBSERVED_ARG=--offline"::equals).count(), output.toString());
			}
			for (String explicitFlag : List.of("--offline", "-o")) {
				List<String> output = new java.util.concurrent.CopyOnWriteArrayList<>();
				var result = runner.run(root, List.of("help", explicitFlag), java.time.Duration.ofSeconds(15), output::add);
				assertEquals(0, result.exitCode(), output.toString());
				assertEquals(1, output.stream().filter(line -> line.equals("OBSERVED_ARG=--offline")
						|| line.equals("OBSERVED_ARG=-o")).count(), output.toString());
			}
		} finally {
			preference.set(previous);
			net.mcreator.preferences.PreferencesManager.PREFERENCES = previousPreferences;
		}
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
