/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211GoldenWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineCachedBuildTest {

	@TempDir Path workspace;

	@Test @EnabledIfSystemProperty(named = "copperbench.stage8.offlineBuild", matches = "true")
	void generatedFabric1211WorkspaceBuildsOfflineFromCachedDependencies() throws Exception {
		Path repository = Path.of(".").toAbsolutePath().normalize();
		new Fabric1211Generator(repository).generate(workspace, Fabric1211GoldenWorkspace.create());

		Path jdk21 = repository.resolve("jdk/jdk21_win_64");
		assertTrue(Files.isDirectory(jdk21), "Bundled JDK 21 is required for the 1.21.1 offline build");

		String userHome = System.getProperty("user.home");
		String gradleHome = Path.of(userHome, ".gradle").toString();
		runGradle(workspace, jdk21, gradleHome, false);
		runGradle(workspace, jdk21, gradleHome, true);
		assertTrue(Files.isRegularFile(workspace.resolve("build/libs/copper_trails-1.0.0.jar")));
	}

	@Test @EnabledIfSystemProperty(named = "copperbench.stage8.offlineBuild", matches = "true")
	void generatedNeoForge1211WorkspaceBuildsOfflineFromCachedDependencies() throws Exception {
		Path repository = Path.of(".").toAbsolutePath().normalize();
		new NeoForge1211Generator(repository).generate(workspace, NeoForge1211GoldenWorkspace.create());

		Path jdk21 = repository.resolve("jdk/jdk21_win_64");
		assertTrue(Files.isDirectory(jdk21), "Bundled JDK 21 is required for the 1.21.1 offline build");

		String userHome = System.getProperty("user.home");
		String gradleHome = Path.of(userHome, ".gradle").toString();
		runGradle(workspace, jdk21, gradleHome, false);
		runGradle(workspace, jdk21, gradleHome, true);
		assertTrue(Files.isRegularFile(workspace.resolve("build/libs/copper_trails-1.0.0.jar")));
	}

	private static void runGradle(Path workspace, Path jdk21, String gradleHome, boolean offline) throws Exception {
		java.util.List<String> command = new java.util.ArrayList<>(
				java.util.List.of("cmd.exe", "/c", "gradlew.bat", "--no-daemon", "build", "--stacktrace"));
		if (offline)
			command.add(3, "--offline");
		ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true);
		builder.environment().put("JAVA_HOME", jdk21.toString());
		builder.environment().put("GRADLE_USER_HOME", gradleHome);
		Process process = builder.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
		boolean completed = process.waitFor(Duration.ofMinutes(15).toMillis(), TimeUnit.MILLISECONDS);
		if (!completed) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
		String log = output.get(30, TimeUnit.SECONDS);
		String label = offline ? "offline" : "cache-warm";
		assertTrue(completed, label + " Gradle build timed out:\n" + log);
		assertEquals(0, process.exitValue(), label + "\n" + log);
	}
}
