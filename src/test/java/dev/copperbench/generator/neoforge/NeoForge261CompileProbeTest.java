/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Honest NeoForge 26.1 compile probe. Generate must succeed. Gradle compile
 * outcome is recorded independently of the catalog status.
 */
class NeoForge261CompileProbeTest {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@TempDir Path workspace;

	@Test
	@Timeout(value = 50, unit = TimeUnit.MINUTES)
	@EnabledIfSystemProperty(named = "copperbench.neoforge261Compile", matches = "true")
	void recordsHonestNeoForge261CompileOutcome() throws Exception {
		Instant started = Instant.now();
		Path repository = Path.of(".").toAbsolutePath().normalize();
		Path evidenceDir = repository.resolve("evidence/stage-8/" + LocalDate.now());
		Files.createDirectories(evidenceDir);
		Path evidenceJson = evidenceDir.resolve("neoforge-261-compile.json");
		Path logFile = evidenceDir.resolve("neoforge-261-compile.log");

		JsonObject evidence = baseEvidence(repository);
		StringBuilder combinedLog = new StringBuilder();
		boolean generateSucceeded = false;
		boolean compileSucceeded = false;
		boolean jarPresent = false;
		try {
			new NeoForge1211Generator(repository, NeoForge1211Generator.Profile.NEOFORGE_261)
					.generate(workspace, Fabric1211GoldenWorkspace.createNeoForge261());
			generateSucceeded = true;
			assertTrue(Files.readString(workspace.resolve("gradle.properties")).contains("minecraft_version=26.1.2"));
			assertTrue(Files.readString(workspace.resolve("gradle.properties")).contains("neoforge_version=26.1.2.95"));
			assertTrue(Files.readString(workspace.resolve("build.gradle")).contains("net.neoforged.moddev"));

			Path jbr25 = repository.resolve("jdk/jbr25_win_64");
			assertTrue(Files.isDirectory(jbr25.resolve("bin")), "Bundled JBR 25 is required for the 26.1 compile probe");

			String gradleHome = Path.of(System.getProperty("user.home"), ".gradle").toString();
			GradleRun cacheWarm = runGradle(workspace, jbr25, gradleHome, false);
			appendRun(evidence, combinedLog, "cacheWarm", cacheWarm);
			if (cacheWarm.exitCode() == 0) {
				GradleRun offline = runGradle(workspace, jbr25, gradleHome, true);
				appendRun(evidence, combinedLog, "offline", offline);
			}

			jarPresent = Files.isRegularFile(workspace.resolve("build/libs/copper_trails-1.0.0.jar"));
			JsonObject cacheWarmJson = evidence.has("cacheWarm") ? evidence.getAsJsonObject("cacheWarm") : null;
			boolean cacheWarmOk = cacheWarmJson != null && cacheWarmJson.has("exitCode")
					&& cacheWarmJson.get("exitCode").getAsInt() == 0;
			compileSucceeded = cacheWarmOk && jarPresent;
		} catch (Exception exception) {
			evidence.addProperty("error", String.valueOf(exception.getMessage()));
			throw exception;
		} finally {
			VersionTrackCatalog.CapabilityDecision decision = VersionTrackCatalog.official().decision("neoforge-26.1.2");
			evidence.addProperty("generateSucceeded", generateSucceeded);
			evidence.addProperty("jarPresent", jarPresent);
			evidence.addProperty("compileSucceeded", compileSucceeded);
			evidence.addProperty("goldenClaimed", false);
			evidence.addProperty("catalogStatus", decision.status().name());
			evidence.addProperty("catalogReasonCode", decision.reasonCode());
			evidence.addProperty("catalogGeneratable", decision.generatable());
			evidence.add("diagnostics", diagnostics(combinedLog.toString()));
			evidence.addProperty("logFile", "evidence/stage-8/" + LocalDate.now() + "/neoforge-261-compile.log");
			evidence.addProperty("completedAt", Instant.now().toString());
			evidence.addProperty("durationSeconds",
					Math.round(Duration.between(started, Instant.now()).toMillis() / 10.0) / 100.0);
			Files.writeString(logFile, combinedLog.toString(), StandardCharsets.UTF_8);
			Files.writeString(evidenceJson, JSON.toJson(evidence), StandardCharsets.UTF_8);
		}

		assertTrue(Files.isRegularFile(evidenceJson));
		assertTrue(generateSucceeded);
	}

	private static JsonObject baseEvidence(Path repository) {
		JsonObject evidence = new JsonObject();
		evidence.addProperty("schemaVersion", "1.0");
		evidence.addProperty("kind", "preview-track-compile-probe");
		evidence.addProperty("generatorId", NeoForge1211Generator.Profile.NEOFORGE_261.generatorId());
		evidence.addProperty("minecraftVersion", NeoForge1211Generator.Profile.NEOFORGE_261.minecraftVersion());
		evidence.addProperty("neoForgeVersion", NeoForge1211Generator.Profile.NEOFORGE_261.neoForgeVersion());
		evidence.addProperty("moddevVersion", NeoForge1211Generator.Profile.NEOFORGE_261.moddevVersion());
		evidence.addProperty("javaRelease", NeoForge1211Generator.Profile.NEOFORGE_261.javaRelease());
		evidence.addProperty("jdk", repository.resolve("jdk/jbr25_win_64").toString());
		evidence.addProperty("gradleUserHome", Path.of(System.getProperty("user.home"), ".gradle").toString());
		evidence.addProperty("osNetworkDisconnected", false);
		return evidence;
	}

	private static void appendRun(JsonObject evidence, StringBuilder combinedLog, String label, GradleRun run) {
		JsonObject json = new JsonObject();
		json.addProperty("completed", run.completed());
		json.addProperty("timedOut", run.timedOut());
		json.addProperty("exitCode", run.exitCode());
		json.addProperty("offline", run.offline());
		evidence.add(label, json);
		combinedLog.append("===== ").append(label).append(" exit=").append(run.exitCode()).append(" =====\n");
		combinedLog.append(run.log()).append('\n');
	}

	private static JsonArray diagnostics(String log) {
		JsonArray items = new JsonArray();
		if (log == null || log.isBlank())
			return items;
		for (String line : log.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty())
				continue;
			String lower = trimmed.toLowerCase();
			if (lower.contains("failure:") || lower.contains("what went wrong") || lower.contains("compilation failed")
					|| lower.contains("could not find") || lower.contains("failed to find")
					|| lower.contains("could not resolve") || lower.startsWith("error:")
					|| lower.contains("execution failed") || lower.contains("incompatible")) {
				items.add(trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed);
				if (items.size() >= 20)
					break;
			}
		}
		return items;
	}

	private static GradleRun runGradle(Path workspace, Path jdk, String gradleHome, boolean offline) throws Exception {
		List<String> command = new ArrayList<>(
				List.of("cmd.exe", "/c", "gradlew.bat", "--no-daemon", "build", "--stacktrace"));
		if (offline)
			command.add(3, "--offline");
		ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true);
		builder.environment().put("JAVA_HOME", jdk.toString());
		builder.environment().put("GRADLE_USER_HOME", gradleHome);
		Process process = builder.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
		boolean completed = process.waitFor(Duration.ofMinutes(25).toMillis(), TimeUnit.MILLISECONDS);
		if (!completed) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
		String log = output.get(30, TimeUnit.SECONDS);
		int exitCode = completed ? process.exitValue() : -1;
		return new GradleRun(offline, completed, !completed, exitCode, log);
	}

	private record GradleRun(boolean offline, boolean completed, boolean timedOut, int exitCode, String log) {
	}
}
