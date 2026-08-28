/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211GoldenWorkspace;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in real Minecraft dedicated-server readiness probe for the Stage 9 support matrix.
 *
 * <p>The deterministic gateway contract is covered separately. This fixture crosses the
 * actual Gradle/Minecraft process boundary and only succeeds after both the generator's
 * Copperbench marker and the vanilla dedicated-server {@code Done (...)} line are observed.</p>
 */
class Stage9RealServerReadinessProbeTest {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String SELECTED_GENERATOR_PROPERTY = "copperbench.stage9.realServerGeneratorId";
	private static final String REUSE_WORKSPACE_PROPERTY = "copperbench.stage9.realServerReuseWorkspace";

	@TestFactory
	@EnabledIfSystemProperty(named = "copperbench.stage9.realServer", matches = "true")
	Stream<DynamicTest> allEightSupportedTracksReachRealDedicatedServerReadiness() {
		Path repository = Path.of(".").toAbsolutePath().normalize();
		List<Track> tracks = tracks(repository);
		String selected = System.getProperty(SELECTED_GENERATOR_PROPERTY, "").trim();
		if (!selected.isEmpty())
			tracks = tracks.stream().filter(track -> selected.equals(track.generatorId())).toList();
		if (tracks.isEmpty())
			throw new IllegalArgumentException("Unknown Stage 9 real-server generator id: " + selected);

		return tracks.stream().map(track -> DynamicTest.dynamicTest(
				track.generatorId() + " reaches real dedicated-server readiness", () -> runTrack(repository, track)));
	}

	private static List<Track> tracks(Path repository) {
		return List.of(
				fabric(repository, Fabric1211Generator.Profile.FABRIC_262, Fabric1211GoldenWorkspace::create262),
				neoForge(repository, NeoForge1211Generator.Profile.NEOFORGE_262,
						Fabric1211GoldenWorkspace::createNeoForge262),
				fabric(repository, Fabric1211Generator.Profile.FABRIC_261, Fabric1211GoldenWorkspace::create261),
				neoForge(repository, NeoForge1211Generator.Profile.NEOFORGE_261,
						Fabric1211GoldenWorkspace::createNeoForge261),
				fabric(repository, Fabric1211Generator.Profile.FABRIC_1211, Fabric1211GoldenWorkspace::create),
				neoForge(repository, NeoForge1211Generator.Profile.NEOFORGE_1211, NeoForge1211GoldenWorkspace::create),
				fabric(repository, Fabric1211Generator.Profile.FABRIC_1201, Fabric1211GoldenWorkspace::create1201),
				neoForge(repository, NeoForge1211Generator.Profile.NEOFORGE_1201,
						Fabric1211GoldenWorkspace::createNeoForge1201));
	}

	private static Track fabric(Path repository, Fabric1211Generator.Profile profile,
			Supplier<WorkspaceState> workspace) {
		return new Track(profile.generatorId(), "fabric", profile.minecraftVersion(), profile.readyMarker(),
				profile.jdkRelativePath(), "run", workspace,
				(target, state) -> new Fabric1211Generator(repository, profile).generate(target, state), target -> { });
	}

	private static Track neoForge(Path repository, NeoForge1211Generator.Profile profile,
			Supplier<WorkspaceState> workspace) {
		NeoForge1211Generator generator = new NeoForge1211Generator(repository, profile);
		return new Track(profile.generatorId(), "neoforge", profile.minecraftVersion(), profile.readyMarker(),
				profile.jdkRelativePath(), profile.modernDeferred() ? "run" : "runs/server", workspace,
				generator::generate, generator::prepareServerRun);
	}

	private static void runTrack(Path repository, Track track) throws Exception {
		Instant started = Instant.now();
		String safeId = track.generatorId().replace('.', '_');
		Path workspace = repository.resolve("build/stage9-real-server-workspaces").resolve(safeId);
		Path evidenceDir = repository.resolve("evidence/stage-9/" + LocalDate.now());
		Path evidenceJson = evidenceDir.resolve("server-readiness-" + safeId + ".json");
		Path logFile = evidenceDir.resolve("server-readiness-" + safeId + ".log");
		Path javaHome = repository.resolve(track.jdkRelativePath()).toAbsolutePath().normalize();
		boolean reuseWorkspace = Boolean.getBoolean(REUSE_WORKSPACE_PROPERTY);
		List<String> logs = new ArrayList<>();
		JsonObject evidence = new JsonObject();
		evidence.addProperty("schemaVersion", "1.0");
		evidence.addProperty("kind", "stage9-real-server-readiness");
		evidence.addProperty("generatorId", track.generatorId());
		evidence.addProperty("loader", track.loader());
		evidence.addProperty("minecraftVersion", track.minecraftVersion());
		evidence.addProperty("readyMarker", track.readyMarker());
		evidence.addProperty("javaHome", javaHome.toString());
		evidence.addProperty("workspaceReused", reuseWorkspace);
		evidence.addProperty("startedAt", started.toString());

		boolean generated = false;
		boolean eulaAccepted = false;
		Fabric1211ProcessRunner.ProcessResult processResult = null;
		try {
			if (!reuseWorkspace) deleteRecursively(workspace);
			Files.createDirectories(workspace);
			Files.createDirectories(evidenceDir);

			assertTrue(Files.isRegularFile(javaHome.resolve("bin/java.exe")),
					"Bundled generator JDK is missing: " + javaHome);
			WorkspaceState state = track.workspace().get();
			assertEquals(track.generatorId(), state.generator().get("id").getAsString());
			track.generator().generate(workspace, state);
			generated = true;

			Path eula = workspace.resolve(track.serverRunDirectory()).resolve("eula.txt");
			Files.createDirectories(eula.getParent());
			Files.writeString(eula, "eula=true\n", StandardCharsets.UTF_8);
			eulaAccepted = "eula=true\n".equals(Files.readString(eula, StandardCharsets.UTF_8));
			assertTrue(eulaAccepted);
			track.serverRunPreparer().prepare(workspace);

			Fabric1211ProcessRunner runner = Fabric1211ProcessRunner.system(track.readyMarker(), javaHome);
			processResult = runner.run(workspace, List.of("runServer"), Duration.ofMinutes(20), logs::add);
			assertEquals(0, processResult.exitCode(), diagnosticTail(logs));
			assertTrue(processResult.readinessMarkerSeen(), diagnosticTail(logs));
		} catch (Exception | AssertionError failure) {
			evidence.addProperty("error", failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
			throw failure;
		} finally {
			String joined = String.join("\n", logs);
			boolean generatorMarkerSeen = joined.contains(track.readyMarker());
			boolean minecraftReadySeen = logs.stream().anyMatch(Fabric1211ProcessRunner::isMinecraftServerReadyLine);
			boolean serverFatalSeen = logs.stream().anyMatch(Fabric1211ProcessRunner::isMinecraftServerFatalLine);
			VersionTrackCatalog.CapabilityDecision decision = VersionTrackCatalog.official().decision(track.generatorId());
			evidence.addProperty("generated", generated);
			evidence.addProperty("eulaAccepted", eulaAccepted);
			evidence.addProperty("generatorMarkerSeen", generatorMarkerSeen);
			evidence.addProperty("minecraftReadySeen", minecraftReadySeen);
			evidence.addProperty("serverFatalSeen", serverFatalSeen);
			evidence.addProperty("processExitCode", processResult == null ? -1 : processResult.exitCode());
			evidence.addProperty("readinessSatisfied", processResult != null && processResult.readinessMarkerSeen());
			evidence.addProperty("catalogStatus", decision.status().name());
			evidence.addProperty("catalogReasonCode", decision.reasonCode());
			evidence.addProperty("catalogGeneratable", decision.generatable());
			evidence.add("diagnostics", diagnostics(logs));
			evidence.addProperty("logFile", repository.relativize(logFile).toString().replace('\\', '/'));
			evidence.addProperty("completedAt", Instant.now().toString());
			evidence.addProperty("durationSeconds",
					Math.round(Duration.between(started, Instant.now()).toMillis() / 10.0) / 100.0);
			evidence.addProperty("passed", generated && eulaAccepted && generatorMarkerSeen && minecraftReadySeen
					&& !serverFatalSeen
					&& processResult != null && processResult.exitCode() == 0 && processResult.readinessMarkerSeen());
			Files.createDirectories(evidenceDir);
			Files.writeString(logFile, joined + (joined.isBlank() ? "" : "\n"), StandardCharsets.UTF_8);
			Files.writeString(evidenceJson, JSON.toJson(evidence), StandardCharsets.UTF_8);
			if (evidence.get("passed").getAsBoolean()) deleteRecursively(workspace);
		}
	}

	private static JsonArray diagnostics(List<String> logs) {
		JsonArray items = new JsonArray();
		for (String line : logs) {
			String lower = line.toLowerCase();
			if (lower.contains("failure:") || lower.contains("what went wrong") || lower.contains("crash")
					|| lower.contains("could not find") || lower.contains("incompatible")
					|| lower.contains("execution failed") || lower.startsWith("error:")
					|| lower.contains("mod resolution") || lower.contains("failed to load")) {
				items.add(line.length() > 400 ? line.substring(0, 400) : line);
				if (items.size() >= 20) break;
			}
		}
		return items;
	}

	private static String diagnosticTail(List<String> logs) {
		int start = Math.max(0, logs.size() - 40);
		return String.join("\n", logs.subList(start, logs.size()));
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
				Files.deleteIfExists(path);
		}
	}

	private record Track(String generatorId, String loader, String minecraftVersion, String readyMarker,
			String jdkRelativePath, String serverRunDirectory, Supplier<WorkspaceState> workspace,
			WorkspaceGenerator generator, ServerRunPreparer serverRunPreparer) {
	}

	@FunctionalInterface
	private interface WorkspaceGenerator {
		void generate(Path target, WorkspaceState state) throws Exception;
	}

	@FunctionalInterface
	private interface ServerRunPreparer {
		void prepare(Path target) throws Exception;
	}
}
