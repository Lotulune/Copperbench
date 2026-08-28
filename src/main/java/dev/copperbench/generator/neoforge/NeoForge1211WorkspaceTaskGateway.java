/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.GradleWorkspaceTaskGateway;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** NeoForge 1.21.1 adapter for the shared Gradle workspace task gateway. */
public final class NeoForge1211WorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {

	private final GradleWorkspaceTaskGateway delegate;

	public NeoForge1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids) {
		this(store, workspaceRoots, distributionRoot, clock, ids, NeoForge1211Generator.Profile.NEOFORGE_1211);
	}

	public NeoForge1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, NeoForge1211Generator.Profile profile) {
		this(store, workspaceRoots, distributionRoot, clock, ids, profile,
				Fabric1211ProcessRunner.system(profile.readyMarker(),
						distributionRoot.resolve(profile.jdkRelativePath())));
	}

	public NeoForge1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, Fabric1211ProcessRunner processes) {
		this(store, workspaceRoots, distributionRoot, clock, ids, NeoForge1211Generator.Profile.NEOFORGE_1211,
				processes);
	}

	public NeoForge1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, NeoForge1211Generator.Profile profile,
			Fabric1211ProcessRunner processes) {
		GradleProcessRunner processAdapter = (root, arguments, timeout, output) -> {
			var result = processes.run(root, arguments, timeout, output);
			return new GradleProcessRunner.ProcessResult(result.exitCode(), result.readinessMarkerSeen());
		};
		this.delegate = new GradleWorkspaceTaskGateway(store, workspaceRoots,
				new NeoForge1211Generator(distributionRoot, profile), clock, ids, processAdapter);
	}

	@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
		return delegate.start(workspaceId, operation, payload);
	}

	@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
		return delegate.find(workspaceId, taskId);
	}

	@Override public List<JsonObject> active(UUID workspaceId) {
		return delegate.active(workspaceId);
	}

	@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
		return delegate.cancel(workspaceId, taskId);
	}

	@Override public AutoCloseable subscribeTaskEvents(Consumer<TaskEvent> listener) {
		return delegate.subscribeTaskEvents(listener);
	}

	@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		return delegate.logs(workspaceId, taskId);
	}

	@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		return delegate.diagnostics(workspaceId, taskId);
	}

	@Override public Optional<JsonObject> previewDatagen(UUID workspaceId, UUID taskId) {
		return delegate.previewDatagen(workspaceId, taskId);
	}

	@Override public JsonObject publishDatagen(UUID workspaceId, UUID taskId, JsonObject payload) {
		return delegate.publishDatagen(workspaceId, taskId, payload);
	}

	@Override public void completeDatagenPublish(UUID workspaceId, UUID taskId) {
		delegate.completeDatagenPublish(workspaceId, taskId);
	}

	@Override public void rollbackDatagenPublish(UUID workspaceId, UUID taskId) {
		delegate.rollbackDatagenPublish(workspaceId, taskId);
	}

	@Override public void close() {
		delegate.close();
	}
}
