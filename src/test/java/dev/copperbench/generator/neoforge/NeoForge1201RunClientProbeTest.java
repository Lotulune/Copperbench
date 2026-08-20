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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Honest NeoForge 1.20.1 runClient probe. Generate must succeed. Catalog
 * promotion to SUPPORTED requires the readiness marker in evidence.
 */
class NeoForge1201RunClientProbeTest {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String MARKER = NeoForge1211Generator.Profile.NEOFORGE_1201.readyMarker();

	@TempDir Path workspace;

	@Test
	@Timeout(value = 70, unit = TimeUnit.MINUTES)
	@EnabledIfSystemProperty(named = "copperbench.neoforge1201RunClient", matches = "true")
	void recordsHonestNeoForge1201RunClientOutcome() throws Exception {
		Instant started = Instant.now();
		Path repository = Path.of(".").toAbsolutePath().normalize();
		Path evidenceDir = repository.resolve("evidence/stage-8/" + LocalDate.now());
		Files.createDirectories(evidenceDir);
		Path evidenceJson = evidenceDir.resolve("neoforge-1201-runclient.json");
		Path logFile = evidenceDir.resolve("neoforge-1201-runclient.log");

		JsonObject evidence = new JsonObject();
		evidence.addProperty("schemaVersion", "1.0");
		evidence.addProperty("kind", "preview-track-runclient-probe");
		evidence.addProperty("generatorId", "neoforge-1.20.1");
		evidence.addProperty("minecraftVersion", "1.20.1");
		evidence.addProperty("neoForgeVersion", NeoForge1211Generator.Profile.NEOFORGE_1201.neoForgeVersion());
		evidence.addProperty("readyMarker", MARKER);
		evidence.addProperty("jdk", repository.resolve("jdk/jdk21_win_64").toString());
		evidence.addProperty("osNetworkDisconnected", false);

		StringBuilder log = new StringBuilder();
		boolean generateSucceeded = false;
		boolean markerSeen = false;
		boolean timedOut = false;
		int exitCode = -1;
		try {
			new NeoForge1211Generator(repository, NeoForge1211Generator.Profile.NEOFORGE_1201)
					.generate(workspace, Fabric1211GoldenWorkspace.createNeoForge1201());
			generateSucceeded = true;
			assertTrue(Files.readString(workspace.resolve("src/main/resources/META-INF/mods.toml"))
					.contains("modId=\"forge\""));

			Path jdk21 = repository.resolve("jdk/jdk21_win_64");
			assertTrue(Files.isDirectory(jdk21.resolve("bin")), "Bundled JDK 21 is required for the 1.20.1 runClient probe");
			ClientRun run = runClient(workspace, jdk21, repository);
			markerSeen = run.markerSeen();
			timedOut = run.timedOut();
			exitCode = run.exitCode();
			log.append(run.log());
			JsonObject client = new JsonObject();
			client.addProperty("completed", run.completed());
			client.addProperty("timedOut", timedOut);
			client.addProperty("exitCode", exitCode);
			client.addProperty("readinessMarkerSeen", markerSeen);
			evidence.add("runClient", client);
		} catch (Exception exception) {
			evidence.addProperty("error", String.valueOf(exception.getMessage()));
			throw exception;
		} finally {
			VersionTrackCatalog.CapabilityDecision decision = VersionTrackCatalog.official().decision("neoforge-1.20.1");
			evidence.addProperty("generateSucceeded", generateSucceeded);
			evidence.addProperty("runClientSucceeded", markerSeen && !timedOut && exitCode == 0);
			evidence.addProperty("goldenClaimed", false);
			evidence.addProperty("catalogStatus", decision.status().name());
			evidence.addProperty("catalogReasonCode", decision.reasonCode());
			evidence.addProperty("catalogGeneratable", decision.generatable());
			evidence.add("diagnostics", diagnostics(log.toString()));
			evidence.addProperty("logFile", "evidence/stage-8/" + LocalDate.now() + "/neoforge-1201-runclient.log");
			evidence.addProperty("completedAt", Instant.now().toString());
			evidence.addProperty("durationSeconds",
					Math.round(Duration.between(started, Instant.now()).toMillis() / 10.0) / 100.0);
			Files.writeString(logFile, log.toString(), StandardCharsets.UTF_8);
			Files.writeString(evidenceJson, JSON.toJson(evidence), StandardCharsets.UTF_8);
		}

		assertTrue(Files.isRegularFile(evidenceJson));
		assertTrue(generateSucceeded);
		assertEquals(SupportStatus.SUPPORTED, VersionTrackCatalog.official().decision("neoforge-1.20.1").status());
		assertEquals("TRACK_SUPPORTED", VersionTrackCatalog.official().decision("neoforge-1.20.1").reasonCode());
		assertTrue(VersionTrackCatalog.official().decision("neoforge-1.20.1").generatable());
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
			if (lower.contains("failure:") || lower.contains("what went wrong") || lower.contains("crash")
					|| lower.contains("could not find") || lower.contains("incompatible")
					|| lower.contains("execution failed") || lower.startsWith("error:")
					|| lower.contains("mod resolution") || lower.contains("failed to load")
					|| lower.contains("missing or unsupported")) {
				items.add(trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed);
				if (items.size() >= 20)
					break;
			}
		}
		return items;
	}

	private static ClientRun runClient(Path workspace, Path jdk, Path repository) throws Exception {
		Path initScript = repository.resolve("scripts/neoforge-1201-assets-mirror.init.gradle");
		List<String> command = List.of("cmd.exe", "/c", "gradlew.bat", "--no-daemon",
				"-Pnet.neoforged.gradle.caching.enabled=false",
				"--init-script", initScript.toString(),
				"runClient", "--stacktrace");
		ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true);
		builder.environment().put("JAVA_HOME", jdk.toString());
		builder.environment().put("GRADLE_USER_HOME", Path.of(System.getProperty("user.home"), ".gradle").toString());
		Process process = builder.start();
		StringBuilder log = new StringBuilder();
		AtomicBoolean marker = new AtomicBoolean();
		Thread reader = Thread.startVirtualThread(() -> {
			try (BufferedReader lines = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = lines.readLine()) != null) {
					log.append(line).append('\n');
					if (line.contains(MARKER))
						marker.set(true);
				}
			} catch (Exception ignored) {
			}
		});
		Instant deadline = Instant.now().plus(Duration.ofMinutes(45));
		while (process.isAlive() && Instant.now().isBefore(deadline)) {
			if (marker.get()) {
				destroy(process);
				reader.join(Duration.ofSeconds(10));
				return new ClientRun(true, false, 0, true, log.toString());
			}
			process.waitFor(200, TimeUnit.MILLISECONDS);
		}
		boolean timedOut = process.isAlive();
		if (timedOut)
			destroy(process);
		reader.join(Duration.ofSeconds(10));
		int exit = timedOut ? 124 : process.exitValue();
		return new ClientRun(!timedOut, timedOut, exit, marker.get(), log.toString());
	}

	private static void destroy(Process process) {
		process.descendants().forEach(ProcessHandle::destroy);
		process.destroy();
		try {
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.descendants().forEach(ProcessHandle::destroyForcibly);
				process.destroyForcibly();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
	}

	private record ClientRun(boolean completed, boolean timedOut, int exitCode, boolean markerSeen, String log) {
	}
}
