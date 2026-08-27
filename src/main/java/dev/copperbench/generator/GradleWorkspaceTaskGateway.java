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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs loader-specific generation and Gradle tasks outside the workspace revision lock. */
public final class GradleWorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {
	private static final Logger LOG = LogManager.getLogger(GradleWorkspaceTaskGateway.class);

	private final RevisionedWorkspaceStore store;
	private final Function<UUID, Path> workspaceRoots;
	private final GradleWorkspaceBackend backend;
	private final Clock clock;
	private final Supplier<UUID> ids;
	private final GradleProcessRunner processes;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final Map<UUID, Map<UUID, Job>> jobs = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<Consumer<WorkspaceTaskGateway.TaskEvent>> taskEventListeners = new CopyOnWriteArrayList<>();

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
		Job job = new Job(workspaceId, task(taskId, operation));
		jobs.computeIfAbsent(workspaceId, ignored -> new ConcurrentHashMap<>()).put(taskId, job);
		job.log("info", "Starting " + backend.displayName() + " " + taskKind(operation)
				+ " from revision " + state.revision());
		JsonObject taskPayload = payload == null ? new JsonObject() : payload.deepCopy();
		job.future = executor.submit(() -> execute(workspaceId, state, operation, taskPayload, job));
		return job.task();
	}

	private void execute(UUID workspaceId, WorkspaceState state, Operation operation, JsonObject payload, Job job) {
		try {
			Path root = workspaceRoots.apply(workspaceId).toAbsolutePath().normalize();
			Path executionRoot = isolated(operation)
					? root.resolve(".copperbench/task-runs").resolve(taskKind(operation))
							.resolve(job.id().toString()).resolve("workspace").normalize()
					: root;
			job.executionRoot = executionRoot;
			job.sourceRevision = state.revision();
			if (isolated(operation)) {
				if (!executionRoot.startsWith(root.toAbsolutePath().normalize()))
					throw new IllegalStateException("Isolated task path escaped the workspace");
				Files.createDirectories(executionRoot);
				job.log("info", "Using isolated task directory "
						+ root.relativize(executionRoot).toString().replace('\\', '/'));
			}
			job.progress(0.15, "task." + taskKind(operation) + ".validating", "Validating workspace");
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
			job.progress(0.35, "task." + taskKind(operation) + ".generating", "Generating workspace sources");
			var result = backend.generate(executionRoot, state);
			job.log("info", backend.displayName() + " generation completed: " + result.generatedPaths().size()
					+ " files");
			if (operation == Operation.BUILD_WORKSPACE || operation == Operation.EXPORT_WORKSPACE) {
				job.progress(0.55, "task." + taskKind(operation) + ".building", "Running Gradle build");
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ofMinutes(15),
						line -> job.log("info", line));
				if (process.exitCode() != 0)
					throw new IllegalStateException(backend.displayName() + " build exited " + process.exitCode());
				if (!backend.buildOutputAvailable(executionRoot))
					throw new IllegalStateException(backend.displayName() + " build did not produce its export artifact");
				if (operation == Operation.EXPORT_WORKSPACE) {
					Path exported = backend.export(executionRoot, payload);
					job.log("info", "Exported " + backend.displayName() + " artifact to "
							+ executionRoot.relativize(exported).toString().replace('\\', '/'));
				}
			} else if (operation == Operation.RUN_CLIENT) {
				job.progress(0.55, "task.run_client.starting", "Starting Minecraft client");
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ofMinutes(20),
						line -> job.log("info", line));
				if (process.exitCode() != 0 || !process.readinessMarkerSeen())
					throw new IllegalStateException(backend.displayName() + " client did not reach the readiness marker");
			} else if (operation == Operation.RUN_SERVER) {
				job.progress(0.55, "task.run_server.starting", "Starting dedicated server");
				if (!payload.has("eulaAccepted") || !payload.get("eulaAccepted").getAsBoolean())
					throw new IllegalArgumentException("Dedicated server EULA confirmation is required");
				Path eula = executionRoot.resolve("run/eula.txt");
				Files.createDirectories(eula.getParent());
				Files.writeString(eula, "eula=true\n", StandardCharsets.UTF_8);
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ofMinutes(20),
						line -> job.log("info", line));
				if (process.exitCode() != 0 || !process.readinessMarkerSeen())
					throw new IllegalStateException(backend.displayName() + " server did not reach the readiness marker");
			} else if (operation == Operation.RUN_DATAGEN || operation == Operation.RUN_GAMETEST) {
				job.progress(0.55, "task." + taskKind(operation) + ".running", "Running managed task");
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ofMinutes(20),
						line -> job.log("info", line));
				if (process.exitCode() != 0)
					throw new IllegalStateException(backend.displayName() + " " + taskKind(operation)
							+ " exited " + process.exitCode());
				if (operation == Operation.RUN_DATAGEN) writeDatagenManifest(executionRoot, state, result, job);
			}
			job.succeed("task." + taskKind(operation) + ".completed",
					backend.displayName() + " " + taskKind(operation) + " completed");
		} catch (Exception exception) {
			if (job.isCancelled()) return;
			String failureId = UUID.randomUUID().toString();
			LOG.error("Workspace task failure {} (backend={}, operation={}, workspaceId={})", failureId,
					backend.displayName(), operation, workspaceId, exception);
			job.fail(backend.diagnosticPrefix() + "_" + taskKind(operation).toUpperCase(Locale.ROOT) + "_FAILED",
					failureId, taskKind(operation));
		}
	}

	static Path exportJar(Path root, JsonObject payload) throws Exception {
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

	@Override public AutoCloseable subscribeTaskEvents(Consumer<WorkspaceTaskGateway.TaskEvent> listener) {
		taskEventListeners.add(listener);
		return () -> taskEventListeners.remove(listener);
	}

	private void publishTaskEvent(WorkspaceTaskGateway.TaskEvent event) {
		for (Consumer<WorkspaceTaskGateway.TaskEvent> listener : taskEventListeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException exception) {
				LOG.debug("Task event listener disconnected", exception);
			}
		}
	}

	@Override public List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		return job == null ? List.of() : job.logs();
	}

	@Override public List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		return job == null ? List.of() : job.diagnostics();
	}

	@Override public Optional<JsonObject> previewDatagen(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		if (job == null || job.operation() != Operation.RUN_DATAGEN) return Optional.empty();
		synchronized (job) {
			try {
				return Optional.of(datagenPreview(workspaceId, job));
			} catch (Exception exception) {
				throw new IllegalStateException("Could not preview staged datagen output", exception);
			}
		}
	}

	@Override public JsonObject publishDatagen(UUID workspaceId, UUID taskId, JsonObject payload) {
		Job job = job(workspaceId, taskId);
		if (job == null || job.operation() != Operation.RUN_DATAGEN)
			throw new IllegalArgumentException("Datagen task not found: " + taskId);
		synchronized (job) {
			try {
				JsonObject preview = datagenPreview(workspaceId, job);
				String expectedHash = payload.has("manifestHash") ? payload.get("manifestHash").getAsString() : "";
				if (!preview.get("manifestHash").getAsString().equals(expectedHash))
					throw new IllegalArgumentException("Datagen preview hash is stale");
				if (!preview.get("canPublish").getAsBoolean())
					throw new IllegalStateException("Staged datagen output cannot be published");
				if (job.publishSession != null || job.published)
					throw new IllegalStateException("Datagen output was already published");
				Path root = workspaceRoots.apply(workspaceId).toAbsolutePath().normalize();
				Path backupRoot = job.executionRoot.getParent().resolve("publish-backup").normalize();
				if (!backupRoot.startsWith(root.resolve(".copperbench/task-runs").normalize()))
					throw new IllegalStateException("Datagen backup path escaped task storage");
				Files.createDirectories(backupRoot);
				List<FileBackup> backups = new ArrayList<>();
				job.publishSession = new PublishSession(backupRoot, backups);
				JsonArray changedPaths = new JsonArray();
				for (var raw : preview.getAsJsonArray("files")) {
					JsonObject item = raw.getAsJsonObject();
					if (item.get("status").getAsString().equals("unchanged")) continue;
					String relative = item.get("path").getAsString();
					Path source = resolveInside(job.executionRoot, relative);
					Path destination = resolveInside(root, relative);
					Path backup = resolveInside(backupRoot, relative);
					boolean existed = Files.isRegularFile(destination);
					if (existed) {
						Files.createDirectories(backup.getParent());
						Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
					}
					backups.add(new FileBackup(destination, backup, existed));
					Files.createDirectories(destination.getParent());
					Path prepared = destination.resolveSibling(destination.getFileName() + ".copperbench-" + taskId + ".tmp");
					try {
						Files.copy(source, prepared, StandardCopyOption.REPLACE_EXISTING);
						try {
							Files.move(prepared, destination, StandardCopyOption.ATOMIC_MOVE,
									StandardCopyOption.REPLACE_EXISTING);
						} catch (AtomicMoveNotSupportedException ignored) {
							Files.move(prepared, destination, StandardCopyOption.REPLACE_EXISTING);
						}
					} finally {
						Files.deleteIfExists(prepared);
					}
					changedPaths.add(relative);
				}
				job.published = true;
				JsonObject result = preview.deepCopy();
				result.add("changedPaths", changedPaths);
				result.addProperty("published", true);
				return result;
			} catch (Exception exception) {
				rollbackPublish(job);
				throw new IllegalStateException("Could not publish staged datagen output", exception);
			}
		}
	}

	@Override public void completeDatagenPublish(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		if (job == null) return;
		synchronized (job) {
			if (job.publishSession == null) return;
			deleteTree(job.publishSession.backupRoot(), job.executionRoot.getParent());
			job.publishSession = null;
		}
	}

	@Override public void rollbackDatagenPublish(UUID workspaceId, UUID taskId) {
		Job job = job(workspaceId, taskId);
		if (job == null) return;
		synchronized (job) {
			rollbackPublish(job);
		}
	}

	private JsonObject datagenPreview(UUID workspaceId, Job job) throws Exception {
		if (job.executionRoot == null || !job.task().get("state").getAsString().equals("succeeded"))
			throw new IllegalStateException("Datagen task has not completed successfully");
		Path manifestPath = job.executionRoot.getParent().resolve("datagen-manifest.json");
		if (!Files.isRegularFile(manifestPath)) throw new IllegalStateException("Datagen manifest is missing");
		JsonObject manifest = com.google.gson.JsonParser.parseString(Files.readString(manifestPath)).getAsJsonObject();
		Path root = workspaceRoots.apply(workspaceId).toAbsolutePath().normalize();
		MessageDigest manifestDigest = MessageDigest.getInstance("SHA-256");
		JsonArray files = new JsonArray();
		int changes = 0;
		for (var raw : manifest.getAsJsonArray("files")) {
			String relative = raw.getAsString().replace('\\', '/');
			if (!publishableDatagenPath(relative)) continue;
			Path source = resolveInside(job.executionRoot, relative);
			if (!Files.isRegularFile(source)) continue;
			Path destination = resolveInside(root, relative);
			byte[] bytes = Files.readAllBytes(source);
			manifestDigest.update(relative.getBytes(StandardCharsets.UTF_8));
			manifestDigest.update((byte) 0);
			manifestDigest.update(MessageDigest.getInstance("SHA-256").digest(bytes));
			String status = !Files.isRegularFile(destination) ? "add"
					: Files.mismatch(source, destination) < 0 ? "unchanged" : "modify";
			if (!status.equals("unchanged")) changes++;
			JsonObject item = new JsonObject();
			item.addProperty("path", relative);
			item.addProperty("status", status);
			item.addProperty("size", bytes.length);
			item.addProperty("sha256", sha256(bytes));
			files.add(item);
		}
		long currentRevision = store.read(workspaceId).map(WorkspaceState::revision).orElse(-1L);
		JsonObject preview = new JsonObject();
		preview.addProperty("taskId", job.id().toString());
		preview.addProperty("sourceRevision", job.sourceRevision);
		preview.addProperty("currentRevision", currentRevision);
		preview.addProperty("manifestHash", HexFormat.of().formatHex(manifestDigest.digest()));
		preview.add("files", files);
		preview.addProperty("changeCount", changes);
		preview.addProperty("stale", currentRevision != job.sourceRevision);
		preview.addProperty("published", job.published);
		preview.addProperty("canPublish", changes > 0 && currentRevision == job.sourceRevision && !job.published);
		return preview;
	}

	private static boolean publishableDatagenPath(String relative) {
		return relative.startsWith("src/generated/") || relative.startsWith("src/main/generated/");
	}

	private static Path resolveInside(Path root, String relative) {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path result = normalizedRoot.resolve(relative).normalize();
		if (!result.startsWith(normalizedRoot)) throw new IllegalArgumentException("Path escapes root: " + relative);
		return result;
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static void rollbackPublish(Job job) {
		PublishSession session = job.publishSession;
		if (session == null) return;
		for (int index = session.files().size() - 1; index >= 0; index--) {
			FileBackup backup = session.files().get(index);
			try {
				if (backup.existed()) {
					Files.createDirectories(backup.destination().getParent());
					Files.copy(backup.backup(), backup.destination(), StandardCopyOption.REPLACE_EXISTING);
				} else Files.deleteIfExists(backup.destination());
			} catch (Exception exception) {
				LOG.error("Could not roll back datagen output {}", backup.destination(), exception);
			}
		}
		deleteTree(session.backupRoot(), job.executionRoot.getParent());
		job.publishSession = null;
		job.published = false;
	}

	private static void deleteTree(Path target, Path boundary) {
		try {
			Path normalizedTarget = target.toAbsolutePath().normalize();
			Path normalizedBoundary = boundary.toAbsolutePath().normalize();
			if (!normalizedTarget.startsWith(normalizedBoundary) || normalizedTarget.equals(normalizedBoundary)) return;
			if (!Files.exists(normalizedTarget)) return;
			try (var paths = Files.walk(normalizedTarget)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception exception) {
						LOG.warn("Could not clean datagen publish backup {}", path, exception);
					}
				});
			}
		} catch (Exception exception) {
			LOG.warn("Could not clean datagen publish backup {}", target, exception);
		}
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
		JsonObject args = new JsonObject();
		args.addProperty("backend", backend.displayName());
		task.add("stage", localized("task." + taskKind(operation) + ".started",
				"Starting " + backend.displayName() + " " + taskKind(operation), args));
		task.addProperty("startedAt", clock.instant().toString());
		task.add("completedAt", JsonNull.INSTANCE);
		task.add("diagnostics", counts(0));
		return task;
	}

	private static boolean isTask(Operation operation) {
		return operation == Operation.VALIDATE_WORKSPACE || operation == Operation.GENERATE_WORKSPACE
				|| operation == Operation.BUILD_WORKSPACE || operation == Operation.EXPORT_WORKSPACE
				|| operation == Operation.RUN_CLIENT || operation == Operation.RUN_SERVER
				|| operation == Operation.RUN_DATAGEN || operation == Operation.RUN_GAMETEST;
	}

	private static boolean isolated(Operation operation) {
		return operation == Operation.RUN_SERVER || operation == Operation.RUN_DATAGEN
				|| operation == Operation.RUN_GAMETEST;
	}

	private static String taskKind(Operation operation) {
		return switch (operation) {
			case VALIDATE_WORKSPACE -> "validate";
			case GENERATE_WORKSPACE -> "generate";
			case BUILD_WORKSPACE -> "build";
			case EXPORT_WORKSPACE -> "export";
			case RUN_CLIENT -> "run_client";
			case RUN_SERVER -> "run_server";
			case RUN_DATAGEN -> "run_datagen";
			case RUN_GAMETEST -> "run_gametest";
			default -> throw new IllegalArgumentException("Operation is not a Gradle task: " + operation);
		};
	}

	private static void writeDatagenManifest(Path executionRoot, WorkspaceState state,
			GradleWorkspaceBackend.GenerationResult generation, Job job) throws Exception {
		JsonObject manifest = new JsonObject();
		manifest.addProperty("schemaVersion", "1.0");
		manifest.addProperty("workspaceId", state.id().toString());
		manifest.addProperty("workspaceRevision", state.revision());
		manifest.addProperty("generatorId", generation.generatorId());
		JsonArray files = new JsonArray();
		try (var paths = Files.walk(executionRoot)) {
			paths.filter(Files::isRegularFile).map(executionRoot::relativize)
					.map(path -> path.toString().replace('\\', '/'))
					.filter(path -> path.contains("generated") || path.startsWith("src/main/resources/data/"))
					.sorted().forEach(files::add);
		}
		manifest.add("files", files);
		Path target = executionRoot.getParent().resolve("datagen-manifest.json");
		Files.writeString(target, manifest.toString(), StandardCharsets.UTF_8);
		job.log("info", "Datagen staged " + files.size() + " files; manifest: " + target.getFileName());
	}

	private static JsonObject localized(String key, String fallback) {
		return localized(key, fallback, new JsonObject());
	}

	private static JsonObject localized(String key, String fallback, JsonObject args) {
		JsonObject value = new JsonObject();
		value.addProperty("key", key);
		value.addProperty("fallback", fallback);
		value.add("args", args == null ? new JsonObject() : args.deepCopy());
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

	private record FileBackup(Path destination, Path backup, boolean existed) {
	}

	private record PublishSession(Path backupRoot, List<FileBackup> files) {
	}

	private final class Job {
		private final UUID workspaceId;
		private final JsonObject summary;
		private final List<JsonObject> logEntries = new ArrayList<>();
		private final List<JsonObject> diagnosticEntries = new ArrayList<>();
		private Future<?> future;
		private Path executionRoot;
		private long sourceRevision;
		private PublishSession publishSession;
		private boolean published;

		private Job(UUID workspaceId, JsonObject summary) {
			this.workspaceId = workspaceId;
			this.summary = summary;
		}

		private synchronized JsonObject task() {
			return summary.deepCopy();
		}

		private UUID id() {
			return UUID.fromString(summary.get("id").getAsString());
		}

		private Operation operation() {
			return switch (summary.get("kind").getAsString()) {
				case "run_datagen" -> Operation.RUN_DATAGEN;
				case "run_server" -> Operation.RUN_SERVER;
				case "run_gametest" -> Operation.RUN_GAMETEST;
				case "run_client" -> Operation.RUN_CLIENT;
				case "validate" -> Operation.VALIDATE_WORKSPACE;
				case "generate" -> Operation.GENERATE_WORKSPACE;
				case "build" -> Operation.BUILD_WORKSPACE;
				case "export" -> Operation.EXPORT_WORKSPACE;
				default -> throw new IllegalStateException("Unknown task kind");
			};
		}

		private synchronized List<JsonObject> logs() {
			return logEntries.stream().map(JsonObject::deepCopy).toList();
		}

		private synchronized List<JsonObject> diagnostics() {
			return diagnosticEntries.stream().map(JsonObject::deepCopy).toList();
		}

		private void log(String level, String text) {
			JsonObject entry;
			WorkspaceTaskGateway.TaskEvent event;
			synchronized (this) {
				entry = new JsonObject();
				entry.addProperty("sequence", logEntries.size() + 1L);
				entry.addProperty("timestamp", clock.instant().toString());
				entry.addProperty("level", level);
				entry.addProperty("text", text);
				logEntries.add(entry);
				event = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "task_log_appended", summary,
						List.of(entry), List.of());
			}
			publishTaskEvent(event);
		}

		private void progress(double progress, String key, String stage) {
			WorkspaceTaskGateway.TaskEvent event = null;
			synchronized (this) {
				if (!isRunning()) return;
				JsonObject args = new JsonObject();
				args.addProperty("backend", backend.displayName());
				summary.addProperty("progress", Math.max(0, Math.min(1, progress)));
				summary.add("stage", localized(key, stage, args));
				event = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "task_progressed", summary,
						List.of(), List.of());
			}
			publishTaskEvent(event);
		}

		private void succeed(String key, String stage) {
			WorkspaceTaskGateway.TaskEvent event;
			synchronized (this) {
				if (!isRunning()) return;
				JsonObject args = new JsonObject();
				args.addProperty("backend", backend.displayName());
				summary.addProperty("state", "succeeded");
				summary.addProperty("cancellable", false);
				summary.addProperty("progress", 1);
				summary.add("stage", localized(key, stage, args));
				summary.addProperty("completedAt", clock.instant().toString());
				event = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "task_completed", summary,
						List.of(), List.of());
			}
			publishTaskEvent(event);
		}

		private void fail(String code, String failureId, String taskKind) {
			synchronized (this) {
				if (!isRunning()) return;
			}
			log("error", "Task failed. Error ID: " + failureId);
			WorkspaceTaskGateway.TaskEvent diagnosticsEvent;
			WorkspaceTaskGateway.TaskEvent completedEvent;
			synchronized (this) {
				if (!isRunning()) return;
				addFailureDiagnostic(code, failureId, taskKind);
				completeFailure();
				List<JsonObject> diagnostics = diagnostics();
				diagnosticsEvent = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "diagnostics_changed", summary,
						List.of(), diagnostics);
				completedEvent = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "task_completed", summary,
						List.of(), diagnostics);
			}
			publishTaskEvent(diagnosticsEvent);
			publishTaskEvent(completedEvent);
		}

		private void addFailureDiagnostic(String code, String failureId, String taskKind) {
			JsonObject args = new JsonObject();
			args.addProperty("backend", backend.displayName());
			args.addProperty("task", taskKind);
			args.addProperty("failureId", failureId);
			JsonObject diagnostic = new JsonObject();
			diagnostic.addProperty("code", code);
			diagnostic.addProperty("severity", "error");
			diagnostic.add("message", localized("diagnostic.workspace_task_failed",
					"The {backend} {task} task failed.", args));
			diagnostic.add("path", JsonNull.INSTANCE);
			diagnostic.add("elementId", JsonNull.INSTANCE);
			diagnostic.addProperty("recoverable", true);
			JsonObject action = new JsonObject();
			action.addProperty("id", "open_logs");
			action.add("label", localized("action.open_logs", "View logs"));
			action.addProperty("kind", "open_logs");
			action.addProperty("target", failureId);
			JsonArray actions = new JsonArray();
			actions.add(action);
			diagnostic.add("actions", actions);
			diagnosticEntries.add(diagnostic);
		}

		private void failValidation(List<GradleWorkspaceBackend.ValidationIssue> issues) {
			for (var issue : issues) {
				log("error", issue.message());
				synchronized (this) {
					if (isRunning()) addDiagnostic(issue.code(), issue.message(), issue.path(), issue.elementId());
				}
			}
			WorkspaceTaskGateway.TaskEvent diagnosticsEvent;
			WorkspaceTaskGateway.TaskEvent completedEvent;
			synchronized (this) {
				if (!isRunning()) return;
				completeFailure();
				List<JsonObject> diagnostics = diagnostics();
				diagnosticsEvent = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "diagnostics_changed", summary,
						List.of(), diagnostics);
				completedEvent = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "task_completed", summary,
						List.of(), diagnostics);
			}
			publishTaskEvent(diagnosticsEvent);
			publishTaskEvent(completedEvent);
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
			JsonObject args = new JsonObject();
			args.addProperty("backend", backend.displayName());
			summary.addProperty("state", "failed");
			summary.addProperty("cancellable", false);
			summary.addProperty("progress", 1);
			summary.add("stage", localized("task.failed", backend.displayName() + " task failed", args));
			summary.addProperty("completedAt", clock.instant().toString());
			summary.add("diagnostics", counts(diagnosticEntries.size()));
		}

		private void cancel() {
			WorkspaceTaskGateway.TaskEvent event;
			synchronized (this) {
				if (!isRunning()) return;
				if (future != null) future.cancel(true);
				summary.addProperty("state", "cancelled");
				summary.addProperty("cancellable", false);
				summary.addProperty("progress", 1);
				summary.add("stage", localized("task.cancelled", "Task cancelled"));
				summary.addProperty("completedAt", clock.instant().toString());
				event = new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "task_completed", summary,
						List.of(), List.of());
			}
			log("warning", backend.displayName() + " task cancelled");
			publishTaskEvent(event);
		}

		private boolean isRunning() {
			return summary.get("state").getAsString().equals("running");
		}

		private synchronized boolean isCancelled() {
			return summary.get("state").getAsString().equals("cancelled");
		}
	}
}
