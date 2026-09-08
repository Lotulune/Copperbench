/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in Stage 14C evidence probe. It builds the native Survey Pulse example,
 * deploys only its remapped JAR into a clean Fabric development host, reaches a
 * real dedicated-server world, and executes the example's isolated world probe.
 * The host intentionally contains no Survey Pulse source files, separating
 * development-source initialization from packaged-JAR loading evidence.
 */
class Stage14CPackagedBehaviorTest {

	private static final String ENABLE_PROPERTY = "copperbench.stage14c.packagedBehavior";
	private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(6);
	private static final Duration SERVER_TIMEOUT = Duration.ofMinutes(8);

	@Test @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "true")
	void packagedJarLoadsAndExecutesRealWorldBehavior() throws Exception {
		Path repository = Path.of(".").toAbsolutePath().normalize();
		Path source = repository.resolve("examples/agent-native/survey-pulse");
		Path host = repository.resolve("build/stage14c-packaged-host");
		Path evidenceDir = repository.resolve("evidence/stage14/2026-09-08");
		Path evidenceJson = evidenceDir.resolve("packaged-behavior-fabric-1.21.1.json");
		Path evidenceLog = evidenceDir.resolve("packaged-behavior-fabric-1.21.1.log");
		Path gradleHome = repository.resolve("build/stage8-workspace-generator-gradle/fabric-1_21_1");
		Path java21 = resolveJava21(repository);
		Path temp = repository.resolve("t/stage14c-packaged");
		Files.createDirectories(temp);
		Files.createDirectories(evidenceDir);
		deleteRecursively(host);

		Instant started = Instant.now();
		ProcessRun build = null;
		ProcessRun runtime = null;
		ProcessRun gameplayRuntime = null;
		Path deployedJar = null;
		String combined = "";
		String gameplayOutput = "";
		try {
			build = runGradle(source, gradleHome, java21, temp, BUILD_TIMEOUT,
					List.of("clean", "remapJar"), false, false);
			assertEquals(0, build.exitCode(), diagnosticTail(build.output()));
			assertFalse(build.timedOut(), "Survey Pulse remapJar timed out");

			Path remappedJar = findRemappedJar(source.resolve("build/libs"));
			assertTrue(remappedJar != null, "Survey Pulse remapped JAR was not produced");
			prepareHost(source, host);
			Path run = host.resolve("run");
			Files.createDirectories(run.resolve("mods"));
			deployedJar = run.resolve("mods/survey_pulse-1.0.jar");
			Files.copy(remappedJar, deployedJar);
			Files.writeString(run.resolve("server.properties"), """
					server-ip=127.0.0.1
					server-port=0
					online-mode=false
					level-name=stage14c-isolated-world
					view-distance=4
					simulation-distance=4
					""", StandardCharsets.UTF_8);

			assertFalse(Files.exists(host.resolve("src/main/java/dev/example/surveypulse")),
					"Packaged host must not contain Survey Pulse source");
			assertFalse(Files.exists(run.resolve("eula.txt")),
					"Stage 14C packaged gameplay must not pre-accept the Minecraft EULA");

			gameplayRuntime = runGradle(host, gradleHome, java21, temp, SERVER_TIMEOUT,
					List.of("runServer"), false, true);
			gameplayOutput = gameplayRuntime.output();
			assertFalse(gameplayRuntime.timedOut(), diagnosticTail(gameplayOutput));
			assertEquals(0, gameplayRuntime.exitCode(), diagnosticTail(gameplayOutput));
			assertTrue(gameplayOutput.contains("SURVEY_PULSE_INITIALIZED"), diagnosticTail(gameplayOutput));
			assertTrue(gameplayOutput.contains("Started game test server"), diagnosticTail(gameplayOutput));
			assertTrue(gameplayOutput.contains("SURVEY_PULSE_GAMEPLAY_VERIFIED"), diagnosticTail(gameplayOutput));
			assertTrue(gameplayOutput.contains("All 1 required tests passed"), diagnosticTail(gameplayOutput));
			assertFalse(fatalSeen(gameplayOutput), diagnosticTail(gameplayOutput));
		} finally {
			if (gameplayRuntime != null) gameplayOutput = gameplayRuntime.output();
			String fullLog = "=== PACKAGED GAMEPLAY GAMETEST (NO EULA ACCEPTANCE) ===\n" + gameplayOutput;
			Files.writeString(evidenceLog, fullLog, StandardCharsets.UTF_8);
			JsonObject evidence = evidence(started, java21, host, deployedJar, build, gameplayRuntime, gameplayOutput,
					repository.relativize(evidenceLog).toString().replace('\\', '/'));
			Files.writeString(evidenceJson,
					new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(evidence),
					StandardCharsets.UTF_8);
		}
	}

	private static ProcessRun runGradle(Path project, Path gradleHome, Path java21, Path temp, Duration timeout,
			List<String> tasks, boolean behaviorProbe, boolean gameTestProbe) throws Exception {
		List<String> command = new java.util.ArrayList<>();
		command.add("cmd.exe");
		command.add("/c");
		command.add("gradlew.bat");
		command.add("--no-daemon");
		command.add("--console=plain");
		command.add("-Porg.gradle.java.installations.auto-detect=false");
		command.add("-Porg.gradle.java.installations.paths=" + java21.toString().replace('\\', '/'));
		command.addAll(tasks);
		ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
		builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
		builder.environment().put("GRADLE_USER_HOME", gradleHome.toString());
		builder.environment().put("TEMP", temp.toString());
		builder.environment().put("TMP", temp.toString());
		if (behaviorProbe) builder.environment().put("SURVEY_PULSE_STAGE14C_BEHAVIOR_PROBE", "1");
		if (gameTestProbe) {
			builder.environment().remove("SURVEY_PULSE_STAGE14C_BEHAVIOR_PROBE");
			builder.environment().put("JAVA_TOOL_OPTIONS",
					"-Dfabric-api.gametest=1 -Dfabric-api.gametest.report-file=run/gametest-results.xml");
		}
		Process process = builder.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		});
		boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (!completed) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
			process.waitFor(10, TimeUnit.SECONDS);
		}
		return new ProcessRun(completed ? process.exitValue() : -1, !completed,
				output.get(30, TimeUnit.SECONDS));
	}

	private static void prepareHost(Path source, Path host) throws IOException {
		Files.createDirectories(host.resolve("gradle/wrapper"));
		Files.copy(source.resolve("gradlew.bat"), host.resolve("gradlew.bat"));
		Files.copy(source.resolve("gradlew"), host.resolve("gradlew"));
		Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.jar"), host.resolve("gradle/wrapper/gradle-wrapper.jar"));
		Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.properties"),
				host.resolve("gradle/wrapper/gradle-wrapper.properties"));
		Files.writeString(host.resolve("settings.gradle"), """
				pluginManagement {
				    repositories {
				        maven { url = 'https://maven.fabricmc.net/' }
				        mavenCentral()
				        gradlePluginPortal()
				    }
				}
				rootProject.name = 'stage14c-packaged-host'
				""", StandardCharsets.UTF_8);
		Files.writeString(host.resolve("gradle.properties"), """
				org.gradle.jvmargs=-Xmx1G -Dfile.encoding=UTF-8 -Duser.language=en
				org.gradle.parallel=false
				org.gradle.configuration-cache=false
				""", StandardCharsets.UTF_8);
		Files.writeString(host.resolve("build.gradle"), """
				plugins {
				    id 'net.fabricmc.fabric-loom-remap' version '1.17.19'
				}

				dependencies {
				    minecraft 'com.mojang:minecraft:1.21.1'
				    mappings loom.officialMojangMappings()
				    modImplementation 'net.fabricmc:fabric-loader:0.19.3'
				    modImplementation 'net.fabricmc.fabric-api:fabric-api:0.116.15+1.21.1'
				}

				java {
				    toolchain.languageVersion = JavaLanguageVersion.of(21)
				}
				""", StandardCharsets.UTF_8);
	}

	private static Path resolveJava21(Path repository) {
		for (Path candidate : List.of(repository.resolve("jdk/jdk21_win_64"),
				repository.resolve("../../jdk/jdk21_win_64").normalize(),
				Path.of(System.getProperty("user.home"), ".copperbench/gradle/jdks/eclipse_adoptium-21-amd64-windows.2"))) {
			if (Files.isRegularFile(candidate.resolve("bin/java.exe")))
				return candidate.toAbsolutePath().normalize();
		}
		throw new IllegalStateException("Stage 14C packaged behavior probe requires a usable Java 21 home");
	}

	private static Path findRemappedJar(Path libs) throws IOException {
		if (!Files.isDirectory(libs)) return null;
		try (Stream<Path> entries = Files.list(libs)) {
			return entries.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.filter(path -> !path.getFileName().toString().contains("sources"))
					.filter(path -> !path.getFileName().toString().contains("dev-shadow"))
					.max(Comparator.comparingLong(path -> path.toFile().lastModified())).orElse(null);
		}
	}

	private static JsonObject evidence(Instant started, Path java21, Path host, Path jar, ProcessRun build,
			ProcessRun gameplayRuntime, String gameplayOutput, String logFile) {
		boolean jarPresent = jar != null && Files.isRegularFile(jar);
		boolean noSources = !Files.exists(host.resolve("src/main/java/dev/example/surveypulse"));
		boolean initializer = gameplayOutput.contains("SURVEY_PULSE_INITIALIZED");
		boolean serverReady = gameplayOutput.contains("Started game test server");
		boolean gameplay = gameplayOutput.contains("SURVEY_PULSE_GAMEPLAY_VERIFIED")
				&& gameplayOutput.contains("All 1 required tests passed");
		boolean fatal = fatalSeen(gameplayOutput);
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.1");
		root.addProperty("kind", "stage14c-packaged-real-world-behavior");
		root.addProperty("generatorId", "fabric-1.21.1");
		root.addProperty("minecraftVersion", "1.21.1");
		root.addProperty("loaderVersion", "0.19.3");
		root.addProperty("java21Home", java21.toString());
		root.addProperty("prepared", true);
		root.addProperty("compiled", build != null && build.exitCode() == 0 && !build.timedOut());
		root.addProperty("packagedJarLoaded", jarPresent && initializer);
		root.addProperty("hostContainsSurveySources", !noSources);
		root.addProperty("initializerExecuted", initializer);
		root.addProperty("eulaBoundaryStatus", "not_applicable_game_test_bypass");
		root.addProperty("eulaAcceptedByHarness", false);
		root.addProperty("serverReady", serverReady);
		root.addProperty("serverReadyMode", "fabric_headless_gametest_dedicated");
		root.addProperty("worldBehaviorVerified", gameplay);
		root.addProperty("behaviorVerified", gameplay);
		root.addProperty("gameplayRuntimeExitCode", gameplayRuntime == null ? -1 : gameplayRuntime.exitCode());
		root.addProperty("gameplayRuntimeTimedOut", gameplayRuntime == null || gameplayRuntime.timedOut());
		root.addProperty("fatalSeen", fatal);
		if (jarPresent) {
			try {
				root.addProperty("packagedJarSha256", sha256(jar));
			} catch (Exception ignored) {
			}
		}
		JsonArray behaviorScope = new JsonArray();
		for (String item : List.of("real Minecraft ServerLevel in Fabric headless GameTest server",
				"isolated generated GameTest world", "controlled near/far ore placement",
				"real ServerboundUseItemPacket through ServerGamePacketListenerImpl",
				"Fabric UseItemCallback dispatch", "ordinary radius 5 versus sneak radius 8",
				"60-tick cooldown rejection and natural expiry", "spectator rejection",
				"two ServerPlayer cooldown isolation")) behaviorScope.add(item);
		root.add("behaviorScope", behaviorScope);
		JsonArray notClaimed = new JsonArray();
		for (String item : List.of("ordinary EULA-accepted dedicated-server startup", "physical OS mouse input",
				"rendered client HUD/particle appearance")) notClaimed.add(item);
		root.add("explicitlyNotClaimed", notClaimed);
		root.addProperty("logFile", logFile);
		root.addProperty("durationSeconds", Duration.between(started, Instant.now()).toMillis() / 1000.0);
		root.addProperty("passed", gameplayRuntime != null && gameplayRuntime.exitCode() == 0
				&& !gameplayRuntime.timedOut() && jarPresent && noSources && initializer && serverReady && gameplay && !fatal);
		root.addProperty("completedAt", Instant.now().toString());
		return root;
	}

	private static String sha256(Path path) throws Exception {
		byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
		return java.util.HexFormat.of().formatHex(digest);
	}

	private static boolean fatalSeen(String output) {
		String lower = output.toLowerCase(java.util.Locale.ROOT);
		return lower.contains("crash report") || lower.contains("failed to start the minecraft server")
				|| lower.contains("exception in server tick loop");
	}

	private static String diagnosticTail(String output) {
		if (output == null) return "";
		return output.length() <= 8000 ? output : output.substring(output.length() - 8000);
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private record ProcessRun(int exitCode, boolean timedOut, String output) {
	}
}
