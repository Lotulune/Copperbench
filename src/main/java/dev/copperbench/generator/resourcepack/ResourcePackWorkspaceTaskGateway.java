/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.resourcepack;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.GradleWorkspaceBackend;
import dev.copperbench.generator.GradleWorkspaceTaskGateway;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Task adapter for the bundled Java Edition resource-pack workspace generator. */
public final class ResourcePackWorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {

	public static final String GENERATOR_ID = "resourcepack-1.21.1";

	private final GradleWorkspaceTaskGateway delegate;

	public ResourcePackWorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids) {
		this(store, workspaceRoots, distributionRoot, clock, ids, systemProcesses());
	}

	public ResourcePackWorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, GradleProcessRunner processes) {
		GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
			@Override public String displayName() {
				return "Resource Pack 1.21.1";
			}

			@Override public String diagnosticPrefix() {
				return "RESOURCE_PACK";
			}

			@Override public List<ValidationIssue> validate(WorkspaceState workspace) {
				JsonObject generator = workspace.generator();
				String id = generator.has("id") && generator.get("id").isJsonPrimitive()
						? generator.get("id").getAsString() : "";
				if (!GENERATOR_ID.equals(id))
					return List.of(new ValidationIssue("RESOURCE_PACK_WORKSPACE_INVALID",
							"Workspace generator must be " + GENERATOR_ID, "/generator", null));
				return List.of();
			}

			@Override public GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws Exception {
				Path source = targetRoot.resolve("src/main").normalize();
				if (!Files.isRegularFile(source.resolve("pack.mcmeta")))
					throw new IllegalArgumentException("Resource pack requires src/main/pack.mcmeta");
				return new GenerationResult(GENERATOR_ID, workspace.name(),
						List.of("src/main/pack.mcmeta", "src/main/pack.png"));
			}

			@Override public boolean buildOutputAvailable(Path targetRoot) {
				return Files.isRegularFile(targetRoot.resolve("build/export/export.zip"));
			}

			@Override public List<String> gradleArguments(Operation operation) {
				if (operation == Operation.RUN_CLIENT)
					return List.of(":packloader:runClient");
				return List.of("build");
			}

			@Override public Path export(Path targetRoot, JsonObject payload) throws Exception {
				if (!payload.has("output") || !payload.get("output").isJsonPrimitive()
						|| payload.get("output").getAsString().isBlank())
					throw new IllegalArgumentException("Export output is required");
				Path root = targetRoot.toAbsolutePath().normalize();
				Path output = root.resolve(payload.get("output").getAsString()).normalize();
				if (!output.startsWith(root) || output.getFileName() == null
						|| !output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
					throw new IllegalArgumentException("Resource pack export output must be a workspace-relative .zip");
				Path artifact = root.resolve("build/export/export.zip");
				if (!Files.isRegularFile(artifact))
					throw new IllegalStateException("Resource pack build did not produce build/export/export.zip");
				if (!output.equals(artifact)) {
					if (output.getParent() != null)
						Files.createDirectories(output.getParent());
					Files.copy(artifact, output, StandardCopyOption.REPLACE_EXISTING);
				}
				return output;
			}
		};
		this.delegate = new GradleWorkspaceTaskGateway(store, workspaceRoots, backend, clock, ids, processes);
	}

	private static GradleProcessRunner systemProcesses() {
		Fabric1211ProcessRunner delegate = Fabric1211ProcessRunner.system("COPPERBENCH_RESOURCE_PACK_READY");
		return (root, arguments, timeout, output) -> {
			Fabric1211ProcessRunner.ProcessResult result = delegate.run(root, arguments, timeout, output);
			return new GradleProcessRunner.ProcessResult(result.exitCode(), result.readinessMarkerSeen());
		};
	}

	@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
		if (operation == Operation.RUN_SERVER || operation == Operation.RUN_DATAGEN
				|| operation == Operation.RUN_GAMETEST)
			throw new IllegalArgumentException("Resource-pack workspaces do not support " + operation);
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

	@Override public void close() {
		delegate.close();
	}
}
