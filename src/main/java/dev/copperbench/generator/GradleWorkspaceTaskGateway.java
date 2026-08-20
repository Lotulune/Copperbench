/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs loader-specific generation and Gradle tasks outside the workspace revision lock. */
public final class GradleWorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {

	private final RevisionedWorkspaceStore store;
	private final Function<UUID, Path> workspaceRoots;
	private final GradleWorkspaceBackend backend;
	private final Clock clock;
	private final Supplier<UUID> ids;
	private final GradleProcessRunner processes;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final Map<UUID, Map<UUID, Job>> jobs = new ConcurrentHashMap<>();

	public GradleWorkspaceTaskGateway(RevisionedWorkspaceStore store, Function<UUID, Path> workspaceRoots,
			GradleWorkspaceBackend backend, Clock clock, Supplier<UUID> ids, GradleProcessRunner processes) {
		this.store = store;
		this.workspaceRoots = workspaceRoots;
		this.backend = backend;
		this.clock = clock;
		this.ids = ids;
		this.processes = processes;
	}

	@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
		WorkspaceState state = store.read(workspaceId)
				.orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
		if (!isTask(operation))
			throw new IllegalArgumentException(backend.displayName() + " task is not implemented yet: " + operation);
		UUID taskId = ids.get();
		Job job = new Job(task(taskId, operation));
		jobs.computeIfAbsent(workspaceId, ignored -> new ConcurrentHashMap<>()).put(taskId, job);
		job.log("info", "Starting " + backend.displayName() + " " + taskKind(operation)
				+ " from revision " + state.revision());
		JsonObject taskPayload = payload == null ? new JsonObject() : payload.deepCopy();
		job.future = executor.submit(() -> execute(workspaceId, state, operation, taskPayload, job));
		return job.task();
	}

	private void execute(UUID workspaceId, WorkspaceState state, Operation operation, JsonObject payload, Job job) {
		try {
			Path root = workspaceRoots.apply(workspaceId);
			var validation = backend.validate(state);
			if (!validation.isEmpty()) {
				job.failValidation(validation);
				return;
			}
			if (operation == Operation.VALIDATE_WORKSPACE) {
				job.log("info", backend.displayName() + " validation completed without errors");
				job.succeed("task.validate.completed", backend.displayName() + " validation completed");
				return;
			}
			var result = backend.generate(root, state);
			job.log("info", backend.displayName() + " generation completed: " + result.generatedPaths().size()
					+ " files");
			if (operation == Operation.BUILD_WORKSPACE || operation == Operation.EXPORT_WORKSPACE) {
				var process = processes.run(root, List.of("build"), Duration.ofMinutes(15),
						line -> job.log("info", line));
				if (process.exitCode() != 0)
					throw new IllegalStateException(backend.displayName() + " build exited " + process.exitCode());
				if (!Files.isDirectory(root.resolve("build/libs")))
					throw new IllegalStateException(backend.displayName() + " build did not produce build/libs");
				if (operation == Operation.EXPORT_WORKSPACE) {
					Path exported = export(root, payload);
					job.log("info", "Exported " + backend.displayName() + " artifact to "
							+ root.relativize(exported).toString().replace('\\', '/'));
				}
			} else if (operation == Operation.RUN_CLIENT) {
				var process = processes.run(root, List.of("runClient"), Duration.ofMinutes(20),
						line -> job.log("info", line));
				if (process.exitCode() != 0 || !process.readinessMarkerSeen())
					throw new IllegalStateException(backend.displayName() + " client did not reach the readiness marker");
			}
			job.succeed("task." + taskKind(operation) + ".completed",
					backend.displayName() + " " + taskKind(operation) + " completed");
		} catch (Exception exception) {
			job.fail(backend.diagnosticPrefix() + "_" + taskKind(operation).toUpperCase(Locale.ROOT) + "_FAILED",
					exception.getMessage() == null ? exception.toString() : exception.getMessage());
		}
	}

	private static Path export(Path root, JsonObject payload) throws Exception {
		if (!payload.has("output") || !payload.get("output").isJsonPrimitive()
				|| payload.get("output").getAsString().isBlank())
			throw new IllegalArgumentException("Export output is required");
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path output = normalizedRoot.resolve(payload.get("output").getAsString()).normalize();
		if (!output.startsWith(normalizedRoot)) throw new IllegalArgumentException("Export path escapes the workspace");
		Path jar;
		try (var files = Files.list(normalizedRoot.resolve("build/libs"))) {
			jar = files.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
					.sorted().findFirst().orElseThrow(() -> new IllegalStateException("Build did not produce a mod JAR"));
		}
		if (output.getParent() != null) Files.createDirectories(output.getParent());
		if (output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
			try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(output))) {
				archive.putNextEntry(new ZipEntry(jar.getFileName().toString()));
				Files.copy(jar, archive);
				archive.closeEntry();
				Path lock = normalizedRoot.resolve(".copperbench/generator-lock.json");
				if (Files.isRegularFile(lock)) {
					archive.putNextEntry(new ZipEntry("generator-lock.json"));
					Files.copy(lock, archive);
					archive.closeEntry();
				}
			}
		} else {
			Files.copy(jar, output, StandardCopyOption.REPLACE_EXISTING);
		}
		return output;
	}

	@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		return Optional.ofNullable(job == null ? null : job.task());
	}

	@Override public List<JsonObject> active(UUID workspaceId) {
		List<JsonObject> result = new ArrayList<>();
		for (Job job : jobs.getOrDefault(workspaceId, Map.of()).values()) {
			String state = job.task().get("state").getAsString();
			if (state.equals("queued") || state.equals("running")) result.add(job.task());
		}
		return List.copyOf(result);
	}

	@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		if (job == null) return Optional.empty();
		job.cancel();
		return Optional.of(job.task());
	}

	@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		return job == null ? List.of() : job.logs();
	}

	@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		return job == null ? List.of() : job.diagnostics();
	}

	private Job job(UUID workspaceId, UUID taskId) {
		return jobs.getOrDefault(workspaceId, Map.of()).get(taskId);
	}

	private JsonObject task(UUID taskId, Operation operation) {
		JsonObject task = new JsonObject();
		task.addProperty("id", taskId.toString());
		task.addProperty("kind", taskKind(operation));
		task.addProperty("state", "running");
		task.addProperty("cancellable", true);
		task.addProperty("progress", 0);
		task.add("stage", localized("task." + taskKind(operation) + ".started",
				"Starting " + backend.displayName() + " " + taskKind(operation)));
		task.addProperty("startedAt", clock.instant().toString());
		task.add("completedAt", JsonNull.INSTANCE);
		task.add("diagnostics", counts(0));
		return task;
	}

	private static boolean isTask(Operation operation) {
		return operation == Operation.VALIDATE_WORKSPACE || operation == Operation.GENERATE_WORKSPACE
				|| operation == Operation.BUILD_WORKSPACE || operation == Operation.EXPORT_WORKSPACE
				|| operation == Operation.RUN_CLIENT;
	}

	private static String taskKind(Operation operation) {
		return switch (operation) {
			case VALIDATE_WORKSPACE -> "validate";
			case GENERATE_WORKSPACE -> "generate";
			case BUILD_WORKSPACE -> "build";
			case EXPORT_WORKSPACE -> "export";
			case RUN_CLIENT -> "run_client";
			default -> throw new IllegalArgumentException("Operation is not a Gradle task: " + operation);
		};
	}

	private static JsonObject localized(String key, String fallback) {
		JsonObject value = new JsonObject();
		value.addProperty("key", key);
		value.addProperty("fallback", fallback);
		value.add("args", new JsonObject());
		return value;
	}

	private static JsonObject counts(int errors) {
		JsonObject counts = new JsonObject();
		counts.addProperty("error", errors);
		counts.addProperty("warning", 0);
		counts.addProperty("info", 0);
		return counts;
	}

	@Override public void close() {
		executor.shutdownNow();
	}

	private final class Job {
		private final JsonObject summary;
		private final List<JsonObject> logEntries = new ArrayList<>();
		private final List<JsonObject> diagnosticEntries = new ArrayList<>();
		private Future<?> future;

		private Job(JsonObject summary) {
			this.summary = summary;
		}

		private synchronized JsonObject task() {
			return summary.deepCopy();
		}

		private synchronized List<JsonObject> logs() {
			return logEntries.stream().map(JsonObject::deepCopy).toList();
		}

		private synchronized List<JsonObject> diagnostics() {
			return diagnosticEntries.stream().map(JsonObject::deepCopy).toList();
		}

		private synchronized void log(String level, String text) {
			JsonObject entry = new JsonObject();
			entry.addProperty("sequence", logEntries.size() + 1L);
			entry.addProperty("timestamp", clock.instant().toString());
			entry.addProperty("level", level);
			entry.addProperty("text", text);
			logEntries.add(entry);
		}

		private synchronized void succeed(String key, String stage) {
			summary.addProperty("state", "succeeded");
			summary.addProperty("cancellable", false);
			summary.addProperty("progress", 1);
			summary.add("stage", localized(key, stage));
			summary.addProperty("completedAt", clock.instant().toString());
		}

		private synchronized void fail(String code, String message) {
			log("error", message);
			addDiagnostic(code, message, null, null);
			completeFailure();
		}

		private synchronized void failValidation(List<GradleWorkspaceBackend.ValidationIssue> issues) {
			for (var issue : issues) {
				log("error", issue.message());
				addDiagnostic(issue.code(), issue.message(), issue.path(), issue.elementId());
			}
			completeFailure();
		}

		private void addDiagnostic(String code, String message, String path, UUID elementId) {
			JsonObject diagnostic = new JsonObject();
			diagnostic.addProperty("code", code);
			diagnostic.addProperty("severity", "error");
			diagnostic.add("message", localized("diagnostic." + code.toLowerCase(Locale.ROOT), message));
			if (path == null) diagnostic.add("path", JsonNull.INSTANCE); else diagnostic.addProperty("path", path);
			if (elementId == null) diagnostic.add("elementId", JsonNull.INSTANCE);
			else diagnostic.addProperty("elementId", elementId.toString());
			diagnostic.addProperty("recoverable", true);
			diagnostic.add("actions", new JsonArray());
			diagnosticEntries.add(diagnostic);
		}

		private void completeFailure() {
			summary.addProperty("state", "failed");
			summary.addProperty("cancellable", false);
			summary.addProperty("progress", 1);
			summary.add("stage", localized("task.failed", backend.displayName() + " task failed"));
			summary.addProperty("completedAt", clock.instant().toString());
			summary.add("diagnostics", counts(diagnosticEntries.size()));
		}

		private synchronized void cancel() {
			if (!summary.get("state").getAsString().equals("running")) return;
			if (future != null) future.cancel(true);
			summary.addProperty("state", "cancelled");
			summary.addProperty("cancellable", false);
			summary.addProperty("progress", 1);
			summary.add("stage", localized("task.cancelled", "Task cancelled"));
			summary.addProperty("completedAt", clock.instant().toString());
			log("warning", backend.displayName() + " task cancelled");
		}
	}
}
