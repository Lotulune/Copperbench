/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

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

class NeoForge1211GoldenBuildTest {

	@TempDir Path workspace;

	@Test @EnabledIfSystemProperty(named = "copperbench.stage5.neoforgeBuild", matches = "true")
	void generatedGoldenWorkspaceBuildsANeoForgeJar() throws Exception {
		Path repository = Path.of(".").toAbsolutePath().normalize();
		new NeoForge1211Generator(repository).generate(workspace, NeoForge1211GoldenWorkspace.create());

		String gradleExecutable = System.getenv().getOrDefault("COPPERBENCH_STAGE5_GRADLE_EXECUTABLE",
				repository.resolve("gradlew.bat").toString());
		ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", gradleExecutable,
				"--no-daemon", "-p", workspace.toString(), "build", "--stacktrace")
				.directory(repository.toFile()).redirectErrorStream(true);
		builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
		Process process = builder.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
		boolean completed = process.waitFor(Duration.ofMinutes(20).toMillis(), TimeUnit.MILLISECONDS);
		if (!completed) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
		String log = output.get(30, TimeUnit.SECONDS);

		assertTrue(completed, "NeoForge build timed out:\n" + log);
		assertEquals(0, process.exitValue(), log);
		assertTrue(Files.isRegularFile(workspace.resolve("build/libs/copper_trails-1.0.0.jar")), log);
	}
}
