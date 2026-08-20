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
import dev.copperbench.tracks.VersionTrackCatalog.SupportStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Honest NeoForge 1.20.1 compile probe. Generate must succeed. Gradle compile
 * outcome is recorded independently of the catalog SUPPORTED claim.
 */
class NeoForge1201CompileProbeTest {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@TempDir Path workspace;

	@Test
	@Timeout(value = 50, unit = TimeUnit.MINUTES)
	@EnabledIfSystemProperty(named = "copperbench.neoforge1201Compile", matches = "true")
	void recordsHonestNeoForge1201CompileOutcomeWithoutPromotingSupport() throws Exception {
		Instant started = Instant.now();
		Path repository = Path.of(".").toAbsolutePath().normalize();
		Path evidenceDir = repository.resolve("evidence/stage-8/" + LocalDate.now());
		Files.createDirectories(evidenceDir);
		Path evidenceJson = evidenceDir.resolve("neoforge-1201-compile.json");
		Path logFile = evidenceDir.resolve("neoforge-1201-compile.log");

		JsonObject evidence = baseEvidence(repository);
		StringBuilder combinedLog = new StringBuilder();
		boolean generateSucceeded = false;
		boolean compileSucceeded = false;
		boolean jarPresent = false;
		try {
			new NeoForge1211Generator(repository, NeoForge1211Generator.Profile.NEOFORGE_1201)
					.generate(workspace, Fabric1211GoldenWorkspace.createNeoForge1201());
			generateSucceeded = true;
			assertTrue(Files.readString(workspace.resolve("gradle.properties")).contains("minecraft_version=1.20.1"));
			assertTrue(Files.readString(workspace.resolve("build.gradle")).contains("net.neoforged.gradle.userdev"));
			assertTrue(Files.readString(workspace.resolve("build.gradle"))
					.contains("net.neoforged:forge:${minecraft_version}-${neoforge_version}"));
			assertTrue(Files.readString(workspace.resolve("gradle.properties")).contains("neoforge_version=47.1.106"));
			assertTrue(Files.readString(workspace.resolve("gradle/wrapper/gradle-wrapper.properties"))
					.contains("gradle-8.8-bin.zip"));

			Path jdk21 = repository.resolve("jdk/jdk21_win_64");
			assertTrue(Files.isDirectory(jdk21.resolve("bin")), "Bundled JDK 21 is required for the 1.20.1 compile probe");

			String gradleHome = Path.of(System.getProperty("user.home"), ".gradle").toString();
			GradleRun cacheWarm = runGradle(workspace, jdk21, gradleHome, false);
			appendRun(evidence, combinedLog, "cacheWarm", cacheWarm);
			if (cacheWarm.exitCode() == 0) {
				GradleRun offline = runGradle(workspace, jdk21, gradleHome, true);
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
			VersionTrackCatalog.CapabilityDecision decision = VersionTrackCatalog.official().decision("neoforge-1.20.1");
			evidence.addProperty("generateSucceeded", generateSucceeded);
			evidence.addProperty("jarPresent", jarPresent);
			evidence.addProperty("compileSucceeded", compileSucceeded);
			evidence.addProperty("goldenClaimed", false);
			evidence.addProperty("catalogStatus", decision.status().name());
			evidence.addProperty("catalogReasonCode", decision.reasonCode());
			evidence.addProperty("catalogGeneratable", decision.generatable());
			evidence.add("diagnostics", diagnostics(combinedLog.toString()));
			evidence.addProperty("logFile", "evidence/stage-8/" + LocalDate.now() + "/neoforge-1201-compile.log");
			evidence.addProperty("completedAt", Instant.now().toString());
			evidence.addProperty("durationSeconds",
					Math.round(Duration.between(started, Instant.now()).toMillis() / 10.0) / 100.0);
			Files.writeString(logFile, combinedLog.toString(), StandardCharsets.UTF_8);
			Files.writeString(evidenceJson, JSON.toJson(evidence), StandardCharsets.UTF_8);
		}

		assertTrue(Files.isRegularFile(evidenceJson));
		assertTrue(generateSucceeded);
		assertEquals(SupportStatus.SUPPORTED, VersionTrackCatalog.official().decision("neoforge-1.20.1").status());
		assertEquals("TRACK_SUPPORTED", VersionTrackCatalog.official().decision("neoforge-1.20.1").reasonCode());
		assertTrue(VersionTrackCatalog.official().decision("neoforge-1.20.1").generatable());
	}

	private static JsonObject baseEvidence(Path repository) {
		JsonObject evidence = new JsonObject();
		evidence.addProperty("schemaVersion", "1.0");
		evidence.addProperty("kind", "preview-track-compile-probe");
		evidence.addProperty("generatorId", NeoForge1211Generator.Profile.NEOFORGE_1201.generatorId());
		evidence.addProperty("minecraftVersion", NeoForge1211Generator.Profile.NEOFORGE_1201.minecraftVersion());
		evidence.addProperty("neoForgeVersion", NeoForge1211Generator.Profile.NEOFORGE_1201.neoForgeVersion());
		evidence.addProperty("moddevVersion", NeoForge1211Generator.Profile.NEOFORGE_1201.moddevVersion());
		evidence.addProperty("javaRelease", NeoForge1211Generator.Profile.NEOFORGE_1201.javaRelease());
		evidence.addProperty("jdk", repository.resolve("jdk/jdk21_win_64").toString());
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
					|| lower.contains("execution failed") || lower.contains("incompatible")
					|| lower.contains("minimum supported gradle")) {
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
