/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.project;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.project.GradleProjectOperations;
import dev.copperbench.generator.BundledJdkLocator;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.GradleWorkspaceTaskGateway;
import dev.copperbench.generator.JavaGeneratorProfile;
import dev.copperbench.generator.WorkspaceExecutionEnvironment;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.platform.DesktopSessionCapabilities;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Builds, runs and acceptance for an adopted Gradle project, on the version track its files identify (ADR-0020). */
public final class GradleProjectTaskGateway implements WorkspaceTaskGateway, AutoCloseable {

	/** The mod's own log lines are unknown, so the dedicated server's start-up line shows it is ready. */
	private static final String SERVER_READY = "For help, type \"help\"";

	private final RevisionedWorkspaceStore store;
	private final Function<UUID, Path> workspaceRoots;
	private final Path distribution;
	private final Map<String, GradleWorkspaceTaskGateway> tracks = new LinkedHashMap<>();

	public GradleProjectTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots, Path distribution,
			Clock clock, Supplier<UUID> ids) {
		this(store, workspaceRoots, distribution, clock, ids, profile -> productProcesses(distribution, profile, () -> { }));
	}

	/** Runs each track's Gradle processes through {@code processes} instead of the project's wrapper. */
	public GradleProjectTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots, Path distribution,
			Clock clock, Supplier<UUID> ids, Function<JavaGeneratorProfile, GradleProcessRunner> processes) {
		this.store = store;
		this.workspaceRoots = workspaceRoots;
		this.distribution = distribution.toAbsolutePath().normalize();
		for (var registration : VersionTrackCatalog.official().registrations()) {
			JavaGeneratorProfile profile = registration.profile();
			if (!(profile instanceof Fabric1211Generator.Profile) && !(profile instanceof NeoForge1211Generator.Profile)) continue;
			tracks.put(profile.generatorId(), new GradleWorkspaceTaskGateway(store, workspaceRoots,
					new GradleProjectBackend(this.distribution, profile), clock, ids, processes.apply(profile)));
		}
	}

	/** The track's JDK with the project's or host's wrapper; {@code prepare} runs before each Gradle process. */
	public static GradleProcessRunner productProcesses(Path distribution, JavaGeneratorProfile profile, Runnable prepare) {
		var runner = Fabric1211ProcessRunner.system(SERVER_READY, () -> BundledJdkLocator.locate(distribution, profile.javaRelease()));
		boolean remapped = profile.equals(Fabric1211Generator.Profile.FABRIC_1211);
		return (root, arguments, timeout, output) -> {
			prepare.run();
			var result = runner.run(root, remapped ? Fabric1211WorkspaceTaskGateway.headlessBuildArgs(arguments) : arguments, timeout, output);
			return new GradleProcessRunner.ProcessResult(result.exitCode(), result.readinessMarkerSeen(), result.runtimeFailureCode());
		};
	}

	@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
		GradleWorkspaceTaskGateway track = tracks.get(generatorId(workspaceId));
		if (track == null) throw new IllegalArgumentException(GradleProjectOperations.UNSUPPORTED);
		return track.start(workspaceId, operation, payload);
	}

	@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
		return tracks.values().stream().map(track -> track.find(workspaceId, taskId)).flatMap(Optional::stream).findFirst();
	}

	@Override public List<JsonObject> active(UUID workspaceId) {
		return tracks.values().stream().flatMap(track -> track.active(workspaceId).stream()).toList();
	}

	@Override public List<JsonObject> recent(UUID workspaceId) {
		return tracks.values().stream().flatMap(track -> track.recent(workspaceId).stream())
				.sorted(Comparator.comparing((JsonObject task) -> task.has("startedAt") ? task.get("startedAt").getAsString() : "")
						.reversed()).limit(100).toList();
	}

	@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
		return owner(workspaceId, taskId).flatMap(track -> track.cancel(workspaceId, taskId));
	}

	@Override public AutoCloseable subscribeTaskEvents(Consumer<TaskEvent> listener) {
		List<AutoCloseable> subscriptions = new ArrayList<>();
		tracks.values().forEach(track -> subscriptions.add(track.subscribeTaskEvents(listener)));
		return () -> {
			Exception failure = null;
			for (AutoCloseable subscription : subscriptions) {
				try {
					subscription.close();
				} catch (Exception exception) {
					if (failure == null) failure = exception;
					else failure.addSuppressed(exception);
				}
			}
			if (failure != null) throw failure;
		};
	}

	@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		return owner(workspaceId, taskId).map(track -> track.logs(workspaceId, taskId)).orElse(List.of());
	}

	@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		return owner(workspaceId, taskId).map(track -> track.diagnostics(workspaceId, taskId)).orElse(List.of());
	}

	@Override public Optional<JsonObject> sourcePreview(UUID workspaceId, UUID taskId, String sourcePath) {
		// Persisted records are found by every track; only the track that ran the task holds its captured sources.
		return tracks.values().stream().map(track -> track.sourcePreview(workspaceId, taskId, sourcePath))
				.flatMap(Optional::stream).findFirst();
	}

	@Override public JsonObject environment(UUID workspaceId) {
		JsonObject environment = WorkspaceExecutionEnvironment.adoptedProject(workspaceRoots.apply(workspaceId), distribution,
				generatorId(workspaceId));
		environment.add("host", DesktopSessionCapabilities.current().toJson());
		return environment;
	}

	private String generatorId(UUID workspaceId) {
		return store.read(workspaceId).map(state -> state.generator().get("id"))
				.filter(value -> value != null && value.isJsonPrimitive()).map(value -> value.getAsString())
				.orElseThrow(() -> new IllegalArgumentException("Workspace target is missing"));
	}

	private Optional<GradleWorkspaceTaskGateway> owner(UUID workspaceId, UUID taskId) {
		return tracks.values().stream().filter(track -> track.find(workspaceId, taskId).isPresent()).findFirst();
	}

	@Override public void close() {
		tracks.values().forEach(GradleWorkspaceTaskGateway::close);
	}
}
