/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prepares a resource pack into a Fabric 1.21.1 workspace and launches the
 * real client. Success requires the client log to mention the enabled pack file.
 */
class ResourcePack1211ClientLoadProbeTest {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String PACK_ZIP = "copper_ready_pack.zip";
	private static final String PACK_MARKER = "file/" + PACK_ZIP;

	@TempDir Path workspace;

	@Test
	@Timeout(value = 40, unit = TimeUnit.MINUTES)
	@EnabledIfSystemProperty(named = "copperbench.resourcePack1211Client", matches = "true")
	void preparedResourcePackLoadsInTheFabric1211Client() throws Exception {
		Instant started = Instant.now();
		Path repository = Path.of(".").toAbsolutePath().normalize();
		Path evidenceDir = repository.resolve("evidence/stage-8/" + LocalDate.now());
		Files.createDirectories(evidenceDir);
		JsonObject evidence = new JsonObject();
		evidence.addProperty("schemaVersion", "1.0");
		evidence.addProperty("kind", "resource-pack-client-load-probe");
		evidence.addProperty("minecraftVersion", "1.21.1");
		evidence.addProperty("packZip", PACK_ZIP);
		StringBuilder log = new StringBuilder();
		boolean generateSucceeded = false;
		boolean prepareSucceeded = false;
		boolean packMentioned = false;
		boolean markerSeen = false;
		try {
			new Fabric1211Generator(repository).generate(workspace, Fabric1211GoldenWorkspace.create());
			generateSucceeded = true;
			Path packRoot = workspace.resolve("resource-pack");
			Files.createDirectories(packRoot.resolve("assets/minecraft/lang"));
			Files.writeString(packRoot.resolve("pack.mcmeta"), """
					{"pack":{"pack_format":34,"description":"Copperbench resource pack client probe"}}
					""", StandardCharsets.UTF_8);
			Files.writeString(packRoot.resolve("assets/minecraft/lang/en_us.json"), """
					{"language.name":"Copperbench Probe English"}
					""", StandardCharsets.UTF_8);
			AssetWorkspaceService assets = new AssetWorkspaceService(workspace);
			var prepared = new ResourcePackClientLoadService(assets, new ResourcePackExportService(assets))
					.prepare("resource-pack", PACK_ZIP);
			prepareSucceeded = prepared.readyForClient();
			assertTrue(Files.isRegularFile(workspace.resolve("run/resourcepacks/" + PACK_ZIP)));
			assertTrue(Files.readString(workspace.resolve("run/options.txt")).contains(PACK_MARKER));

			List<String> lines = new ArrayList<>();
			ClientRun run = runClientUntilReady(workspace, Duration.ofMinutes(20), lines, log);
			markerSeen = run.markerSeen();
			String combined = String.join("\n", lines);
			Path latestLog = workspace.resolve("run/logs/latest.log");
			Path debugLog = workspace.resolve("run/logs/debug.log");
			String clientLog = Files.isRegularFile(latestLog) ? Files.readString(latestLog) : "";
			String debug = Files.isRegularFile(debugLog) ? Files.readString(debugLog) : "";
			String optionsAfter = Files.readString(workspace.resolve("run/options.txt"));
			boolean optionsKeptPack = optionsAfter.contains(PACK_MARKER) || optionsAfter.contains(PACK_ZIP);
			boolean resourceManagerListedPack = resourceManagerListsPack(lines, clientLog, debug);
			packMentioned = resourceManagerListedPack;
			evidence.addProperty("readyForClient", prepared.readyForClient());
			evidence.addProperty("packFormat", prepared.packFormat());
			evidence.addProperty("optionsRelativePath", prepared.optionsRelativePath());
			evidence.addProperty("zipRelativePath", prepared.zipRelativePath());
			evidence.addProperty("clientExitCode", run.exitCode());
			evidence.addProperty("readinessMarkerSeen", markerSeen);
			evidence.addProperty("optionsKeptPackAfterLaunch", optionsKeptPack);
			evidence.addProperty("resourceManagerListedPack", resourceManagerListedPack);
			evidence.addProperty("packMentionedInClientLog", resourceManagerListedPack
					|| combined.contains(PACK_MARKER) || clientLog.contains(PACK_MARKER));
		} catch (Exception exception) {
			evidence.addProperty("error", String.valueOf(exception.getMessage()));
			throw exception;
		} finally {
			evidence.addProperty("generateSucceeded", generateSucceeded);
			evidence.addProperty("prepareSucceeded", prepareSucceeded);
			evidence.addProperty("clientLaunched", markerSeen);
			evidence.addProperty("packLoaded", packMentioned && markerSeen);
			evidence.add("diagnostics", diagnostics(log.toString()));
			evidence.addProperty("logFile", "evidence/stage-8/" + LocalDate.now() + "/resource-pack-1211-client.log");
			evidence.addProperty("completedAt", Instant.now().toString());
			evidence.addProperty("durationSeconds",
					Math.round(Duration.between(started, Instant.now()).toMillis() / 10.0) / 100.0);
			Files.writeString(evidenceDir.resolve("resource-pack-1211-client.log"), log.toString(),
					StandardCharsets.UTF_8);
			Files.writeString(evidenceDir.resolve("resource-pack-1211-client.json"), JSON.toJson(evidence),
					StandardCharsets.UTF_8);
		}
		assertTrue(generateSucceeded);
		assertTrue(prepareSucceeded);
		assertTrue(markerSeen, "Fabric 1.21.1 client must reach the readiness marker");
		assertTrue(packMentioned, "ResourceManager must list file/" + PACK_ZIP);
	}

	private static boolean resourceManagerListsPack(List<String> lines, String clientLog, String debugLog) {
		if (listsPack(lines))
			return true;
		if (listsPack(clientLog.split("\\R")))
			return true;
		return listsPack(debugLog.split("\\R"));
	}

	private static boolean listsPack(String[] lines) {
		return listsPack(List.of(lines));
	}

	private static boolean listsPack(List<String> lines) {
		for (String line : lines) {
			if (line != null && line.contains("Reloading ResourceManager:") && line.contains(PACK_MARKER))
				return true;
		}
		return false;
	}

	private static ClientRun runClientUntilReady(Path workspace, Duration timeout, List<String> lines,
			StringBuilder log) throws Exception {
		ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "--no-daemon", "runClient",
				"--stacktrace").directory(workspace.toFile()).redirectErrorStream(true);
		builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
		Process process = builder.start();
		AtomicBoolean marker = new AtomicBoolean();
		Thread reader = Thread.startVirtualThread(() -> {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					lines.add(line);
					log.append(line).append('\n');
					if (line.contains("COPPERBENCH_STAGE3_READY"))
						marker.set(true);
				}
			} catch (Exception ignored) {
			}
		});
		Instant deadline = Instant.now().plus(timeout);
		Instant keepAliveUntil = null;
		while (process.isAlive() && Instant.now().isBefore(deadline)) {
			if (marker.get() && keepAliveUntil == null)
				keepAliveUntil = Instant.now().plusSeconds(8);
			if (keepAliveUntil != null && Instant.now().isAfter(keepAliveUntil))
				break;
			process.waitFor(200, TimeUnit.MILLISECONDS);
		}
		process.descendants().forEach(ProcessHandle::destroy);
		process.destroy();
		if (!process.waitFor(8, TimeUnit.SECONDS))
			process.destroyForcibly();
		reader.join(Duration.ofSeconds(10));
		return new ClientRun(process.exitValue(), marker.get());
	}

	private record ClientRun(int exitCode, boolean markerSeen) {
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
					|| lower.contains("failed to load") || lower.contains("resourcepack")
					|| lower.startsWith("error:")) {
				items.add(trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed);
				if (items.size() >= 20)
					break;
			}
		}
		return items;
	}
}
