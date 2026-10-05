/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.datapack;

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

/** Task adapter for the bundled Java Edition data-pack workspace generator. */
public final class DataPackWorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {

	public static final java.util.Set<String> GENERATOR_IDS = java.util.Set.of(
            "datapack-1.20.1", "datapack-1.21.1", "datapack-26.1.x");

	private final GradleWorkspaceTaskGateway delegate;

	public DataPackWorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids) {
		this(store, workspaceRoots, distributionRoot, clock, ids, systemProcesses());
	}

	public DataPackWorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			Path distributionRoot, Clock clock, Supplier<UUID> ids, GradleProcessRunner processes) {
		GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
			@Override public String displayName() {
				return "Data Pack";
			}

			@Override public String diagnosticPrefix() {
				return "DATA_PACK";
			}

			@Override public List<ValidationIssue> validate(WorkspaceState workspace) {
				JsonObject generator = workspace.generator();
				String id = generator.has("id") && generator.get("id").isJsonPrimitive()
						? generator.get("id").getAsString() : "";
				if (!GENERATOR_IDS.contains(id))
					return List.of(new ValidationIssue("DATA_PACK_WORKSPACE_INVALID",
							"Unsupported data-pack generator: " + id, "/generator", null));
				return List.of();
			}

			@Override public GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws Exception {
				Path source = targetRoot.resolve("src/main").normalize();
				if (!Files.isRegularFile(source.resolve("pack.mcmeta")))
					throw new IllegalArgumentException("Data pack requires src/main/pack.mcmeta");
				return new GenerationResult(workspace.generator().get("id").getAsString(), workspace.name(),
						List.of("src/main/pack.mcmeta"));
			}

			@Override public boolean buildOutputAvailable(Path targetRoot) {
				return Files.isRegularFile(targetRoot.resolve("build/export/export.zip"));
			}

			@Override public List<String> gradleArguments(Operation operation) {
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
					throw new IllegalArgumentException("Data pack export output must be a workspace-relative .zip");
				Path artifact = root.resolve("build/export/export.zip");
				if (!Files.isRegularFile(artifact))
					throw new IllegalStateException("Data pack build did not produce build/export/export.zip");
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
		Fabric1211ProcessRunner delegate = Fabric1211ProcessRunner.system("COPPERBENCH_DATA_PACK_READY");
		return (root, arguments, timeout, output) -> {
			Fabric1211ProcessRunner.ProcessResult result = delegate.run(root, arguments, timeout, output);
			return new GradleProcessRunner.ProcessResult(result.exitCode(), result.readinessMarkerSeen(),
					result.runtimeFailureCode());
		};
	}

    @Override public void setGenerationPreparation(GenerationPreparation preparation) {
        delegate.setGenerationPreparation(preparation);
    }

	@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
        if (!java.util.Set.of(Operation.VALIDATE_WORKSPACE, Operation.GENERATE_WORKSPACE,
                Operation.BUILD_WORKSPACE, Operation.EXPORT_WORKSPACE).contains(operation))
			throw new IllegalArgumentException("Data-pack workspaces do not support " + operation);
		return delegate.start(workspaceId, operation, payload);
	}

	@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
		return delegate.find(workspaceId, taskId);
	}

	@Override public List<JsonObject> active(UUID workspaceId) {
		return delegate.active(workspaceId);
	}
	@Override public List<JsonObject> recent(UUID workspaceId) { return delegate.recent(workspaceId); }

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

	@Override public Optional<JsonObject> sourcePreview(UUID workspaceId, UUID taskId, String sourcePath) {
		return delegate.sourcePreview(workspaceId, taskId, sourcePath);
	}

	@Override public void close() {
		delegate.close();
	}
}
