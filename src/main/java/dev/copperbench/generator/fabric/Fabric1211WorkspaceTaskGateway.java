/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.GradleWorkspaceBackend;
import dev.copperbench.generator.GradleWorkspaceTaskGateway;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Fabric 1.21.1 adapter for the shared Gradle workspace task gateway. */
public final class Fabric1211WorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {

	private final GradleWorkspaceTaskGateway delegate;

	public Fabric1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids) {
		this(store, workspaceRoots, distributionRoot, clock, ids, Fabric1211Generator.Profile.FABRIC_1211,
				Fabric1211ProcessRunner.system());
	}

	public Fabric1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, Fabric1211Generator.Profile profile) {
		this(store, workspaceRoots, distributionRoot, clock, ids, profile,
				Fabric1211ProcessRunner.system(profile.readyMarker()));
	}

	public Fabric1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, Fabric1211ProcessRunner processes) {
		this(store, workspaceRoots, distributionRoot, clock, ids, Fabric1211Generator.Profile.FABRIC_1211, processes);
	}

	public Fabric1211WorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, Fabric1211Generator.Profile profile,
			Fabric1211ProcessRunner processes) {
		Fabric1211Generator generator = new Fabric1211Generator(distributionRoot, profile);
		GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
			@Override public String displayName() {
				return "Fabric " + profile.minecraftVersion();
			}

			@Override public String diagnosticPrefix() {
				return "FABRIC";
			}

			@Override public List<ValidationIssue> validate(dev.copperbench.core.workspace.WorkspaceState workspace) {
				return generator.validate(workspace).stream().map(issue -> new ValidationIssue(issue.code(),
						issue.message(), issue.path(), issue.elementId())).toList();
			}

			@Override public GenerationResult generate(Path targetRoot,
					dev.copperbench.core.workspace.WorkspaceState workspace) throws Exception {
				var result = generator.generate(targetRoot, workspace);
				return new GenerationResult(result.generatorId(), result.modId(), result.generatedPaths());
			}
		};
		GradleProcessRunner processAdapter = (root, arguments, timeout, output) -> {
			var result = processes.run(root, arguments, timeout, output);
			return new GradleProcessRunner.ProcessResult(result.exitCode(), result.readinessMarkerSeen());
		};
		this.delegate = new GradleWorkspaceTaskGateway(store, workspaceRoots, backend, clock, ids, processAdapter);
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

	@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		return delegate.logs(workspaceId, taskId);
	}

	@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		return delegate.diagnostics(workspaceId, taskId);
	}

	@Override public void close() {
		delegate.close();
	}
}
