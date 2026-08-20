/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.tracks.VersionTrackCatalog;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric262RunClientProbeTest {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String MARKER = Fabric1211Generator.Profile.FABRIC_262.readyMarker();

	@TempDir Path workspace;

	@Test
	@Timeout(value = 50, unit = TimeUnit.MINUTES)
	@EnabledIfSystemProperty(named = "copperbench.fabric262RunClient", matches = "true")
	void recordsHonestFabric262RunClientOutcome() throws Exception {
		recordRunClient(workspace, Fabric1211Generator.Profile.FABRIC_262,
				Fabric1211GoldenWorkspace.create262(), "fabric-262-runclient");
	}

	static void recordRunClient(Path workspace, Fabric1211Generator.Profile profile,
			dev.copperbench.core.workspace.WorkspaceState state, String evidenceStem) throws Exception {
		Instant started = Instant.now();
		Path repository = Path.of(".").toAbsolutePath().normalize();
		Path evidenceDir = repository.resolve("evidence/stage-8/" + LocalDate.now());
		Files.createDirectories(evidenceDir);
		Path evidenceJson = evidenceDir.resolve(evidenceStem + ".json");
		Path logFile = evidenceDir.resolve(evidenceStem + ".log");
		JsonObject evidence = new JsonObject();
		evidence.addProperty("schemaVersion", "1.0");
		evidence.addProperty("kind", "preview-track-runclient-probe");
		evidence.addProperty("generatorId", profile.generatorId());
		evidence.addProperty("minecraftVersion", profile.minecraftVersion());
		evidence.addProperty("readyMarker", profile.readyMarker());
		evidence.addProperty("jdk", repository.resolve("jdk/jbr25_win_64").toString());
		evidence.addProperty("osNetworkDisconnected", false);
		StringBuilder log = new StringBuilder();
		boolean generateSucceeded = false;
		boolean markerSeen = false;
		boolean timedOut = false;
		int exitCode = -1;
		try {
			new Fabric1211Generator(repository, profile).generate(workspace, state);
			generateSucceeded = true;
			Path jbr25 = repository.resolve("jdk/jbr25_win_64");
			assertTrue(Files.isDirectory(jbr25.resolve("bin")));
			ClientRun run = runClient(workspace, jbr25, profile.readyMarker());
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
			VersionTrackCatalog.CapabilityDecision decision = VersionTrackCatalog.official().decision(profile.generatorId());
			evidence.addProperty("generateSucceeded", generateSucceeded);
			evidence.addProperty("runClientSucceeded", markerSeen && !timedOut);
			evidence.addProperty("goldenClaimed", false);
			evidence.addProperty("catalogStatus", decision.status().name());
			evidence.addProperty("catalogReasonCode", decision.reasonCode());
			evidence.addProperty("catalogGeneratable", decision.generatable());
			evidence.add("diagnostics", diagnostics(log.toString()));
			evidence.addProperty("logFile", "evidence/stage-8/" + LocalDate.now() + "/" + evidenceStem + ".log");
			evidence.addProperty("completedAt", Instant.now().toString());
			evidence.addProperty("durationSeconds",
					Math.round(Duration.between(started, Instant.now()).toMillis() / 10.0) / 100.0);
			Files.writeString(logFile, log.toString(), StandardCharsets.UTF_8);
			Files.writeString(evidenceJson, JSON.toJson(evidence), StandardCharsets.UTF_8);
		}
		assertTrue(Files.isRegularFile(evidenceJson));
		assertTrue(generateSucceeded);
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
					|| lower.contains("mod resolution") || lower.contains("failed to load")) {
				items.add(trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed);
				if (items.size() >= 20)
					break;
			}
		}
		return items;
	}

	private static ClientRun runClient(Path workspace, Path jdk, String marker) throws Exception {
		List<String> command = List.of("cmd.exe", "/c", "gradlew.bat", "--no-daemon", "runClient", "--stacktrace");
		ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true);
		builder.environment().put("JAVA_HOME", jdk.toString());
		builder.environment().put("GRADLE_USER_HOME", Path.of(System.getProperty("user.home"), ".gradle").toString());
		Process process = builder.start();
		StringBuilder log = new StringBuilder();
		AtomicBoolean seen = new AtomicBoolean();
		Thread reader = Thread.startVirtualThread(() -> {
			try (BufferedReader lines = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = lines.readLine()) != null) {
					log.append(line).append('\n');
					if (line.contains(marker))
						seen.set(true);
				}
			} catch (Exception ignored) {
			}
		});
		Instant deadline = Instant.now().plus(Duration.ofMinutes(20));
		while (process.isAlive() && Instant.now().isBefore(deadline)) {
			if (seen.get()) {
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
		return new ClientRun(!timedOut, timedOut, exit, seen.get(), log.toString());
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
