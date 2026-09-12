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
import com.google.gson.JsonElement;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs loader-specific generation and Gradle tasks outside the workspace revision lock. */
public final class GradleWorkspaceTaskGateway implements WorkspaceTaskGateway, AutoCloseable {
	private static final Logger LOG = LogManager.getLogger(GradleWorkspaceTaskGateway.class);
	private static final long MAX_SOURCE_PREVIEW_BYTES = 256L * 1024L;
	private static final Pattern JAVA_COMPILE_ERROR = Pattern.compile(
			"^(.+\\.java):(\\d+):\\s*(?:error|错误|錯誤|エラー|오류|fehler|erreur|errore|ошибка|erro):\\s*(.+)$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

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
		job.future = executor.submit(() -> {
			job.workerStarted();
			try {
				execute(workspaceId, state, operation, taskPayload, job);
			} finally {
				job.workerFinished();
			}
		});
		return job.task();
	}

	private void execute(UUID workspaceId, WorkspaceState state, Operation operation, JsonObject payload, Job job) {
		try {
			Path root = workspaceRoots.apply(workspaceId).toAbsolutePath().normalize();
			Path executionRoot = operation == Operation.RUN_GAMETEST ? GameTestRunDirectory.select(root, job.id()) : isolated(operation)
					? root.resolve(".copperbench/task-runs").resolve(taskKind(operation))
							.resolve(job.id().toString()).resolve("workspace").normalize()
					: root;
			job.executionRoot = executionRoot;
			job.sourceRevision = state.revision();
			job.sourceState = state;
			if (isolated(operation)) {
				if (operation != Operation.RUN_GAMETEST && !executionRoot.startsWith(root.toAbsolutePath().normalize()))
					throw new IllegalStateException("Isolated task path escaped the workspace");
				var snapshot = WorkspaceExecutionSnapshot.capture(root, executionRoot, workspaceId,
						state.revision(), clock, job::cancellationRequested);
				job.record("sourceSnapshot", snapshot.projection());
				if (operation == Operation.RUN_GAMETEST) GameTestRunDirectory.record(root, job.id(), snapshot);
				job.log("info", "Using isolated task directory "
						+ executionRoot.toString().replace('\\', '/'));
				if (!executionRoot.startsWith(root))
					job.log("info", "GAMETEST_SHORT_PATH: using the Copperbench task cache to avoid the Windows wrapper path limit; execution-location.json remains in the workspace task directory.");
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
			if (operation == Operation.PREPARE_GAME_TESTS) {
				job.record("gameTestSetup", GameTestSupport.prepare(root, backend.gameTestEnvironment(), backend.gameTestModId(state)));
				job.succeed("task.prepare_game_tests.completed", "GameTest starter prepared; add behavior assertions before acceptance");
				return;
			}
			job.progress(0.35, "task." + taskKind(operation) + ".generating", "Generating workspace sources");
			var result = backend.generate(executionRoot, state);
			job.log("info", backend.displayName() + " generation completed: " + result.generatedPaths().size()
					+ " files");
			if (operation == Operation.BUILD_WORKSPACE || operation == Operation.EXPORT_WORKSPACE) {
				job.progress(0.55, "task." + taskKind(operation) + ".building", "Running Gradle build");
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ofMinutes(15),
						line -> {
							job.log("info", line);
							job.captureJavaCompileDiagnostic(executionRoot, line);
						});
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
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ZERO,
						line -> job.log("info", line));
				// Minecraft can catch WindowInitFailed and return zero before opening a window.
				// A mod's earlier readiness marker does not establish that OpenGL initialized.
				boolean graphicsInitializationFailed = "WINDOWS_OPENGL_INITIALIZATION_FAILED"
						.equals(process.runtimeFailureCode());
				if (process.exitCode() != 0 || graphicsInitializationFailed) {
					JsonObject args = new JsonObject();
					args.addProperty("exitCode", process.exitCode());
					String runtimeFailureCode = process.readinessMarkerSeen() && !graphicsInitializationFailed
							? null : process.runtimeFailureCode();
					if (runtimeFailureCode != null && !runtimeFailureCode.isBlank())
						args.addProperty("runtimeFailureCode", runtimeFailureCode);
					String diagnosticCode = runtimeFailureCode == null || runtimeFailureCode.isBlank()
							? backend.diagnosticPrefix() + "_RUN_CLIENT_EXITED"
							: backend.diagnosticPrefix() + "_RUN_CLIENT_" + runtimeFailureCode;
					failKnownTask(workspaceId, operation, job,
							diagnosticCode, graphicsInitializationFailed
									? "diagnostic.task_client_opengl_initialization_failed" : "diagnostic.task_process_exited",
							graphicsInitializationFailed
									? "The {backend} client could not initialize OpenGL. Check graphics support and drivers."
									: "The {backend} {task} task exited with code {exitCode}.", args);
					return;
				}
			} else if (operation == Operation.RUN_SERVER) {
				job.progress(0.55, "task.run_server.starting", "Starting dedicated server");
				if (!payload.has("eulaAccepted") || !payload.get("eulaAccepted").getAsBoolean())
					throw new IllegalArgumentException("Dedicated server EULA confirmation is required");
				Path serverRunDirectory = backend.serverRunDirectory(executionRoot).toAbsolutePath().normalize();
				if (!serverRunDirectory.startsWith(executionRoot.toAbsolutePath().normalize()))
					throw new IllegalStateException("Server run directory escaped the workspace");
				Path eula = serverRunDirectory.resolve("eula.txt");
				Files.createDirectories(eula.getParent());
				Files.writeString(eula, "eula=true\n", StandardCharsets.UTF_8);
				backend.prepareServerRun(executionRoot);
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ofMinutes(20),
						line -> job.log("info", line));
				if (process.exitCode() != 0) {
					JsonObject args = new JsonObject();
					args.addProperty("exitCode", process.exitCode());
					failKnownTask(workspaceId, operation, job,
							backend.diagnosticPrefix() + "_RUN_SERVER_EXITED", "diagnostic.task_process_exited",
							"The {backend} {task} task exited with code {exitCode}.", args);
					return;
				}
				if (!process.readinessMarkerSeen()) {
					JsonObject args = new JsonObject();
					args.addProperty("exitCode", process.exitCode());
					failKnownTask(workspaceId, operation, job,
							backend.diagnosticPrefix() + "_RUN_SERVER_NOT_READY",
							"diagnostic.task_readiness_not_reached",
							"The {backend} {task} task did not reach the readiness marker.", args);
					return;
				}
			} else if (operation == Operation.RUN_GAMETEST) {
				if (!runGameTests(executionRoot, state, payload, job)) return;
			} else if (operation == Operation.RUN_DATAGEN) {
				job.progress(0.55, "task." + taskKind(operation) + ".running", "Running managed task");
				var process = processes.run(executionRoot, backend.gradleArguments(operation), Duration.ofMinutes(20),
						line -> job.log("info", line));
				if (process.exitCode() != 0) {
					JsonObject args = new JsonObject();
					args.addProperty("exitCode", process.exitCode());
					failKnownTask(workspaceId, operation, job,
							backend.diagnosticPrefix() + "_" + taskKind(operation).toUpperCase(Locale.ROOT) + "_EXITED",
							"diagnostic.task_process_exited",
							"The {backend} {task} task exited with code {exitCode}.", args);
					return;
				}
				if (operation == Operation.RUN_DATAGEN) writeDatagenManifest(executionRoot, state, result, job);
			}
			job.succeed("task." + taskKind(operation) + ".completed",
					backend.displayName() + " " + taskKind(operation) + " completed");
		} catch (GameTestSupport.TestSetupException exception) {
			if (job.isCancelled() || job.cancellationRequested()) return;
			job.log("error", exception.getMessage());
			job.fail(exception.code(), UUID.randomUUID().toString(), taskKind(operation),
					"diagnostic.gametest_setup_failed", exception.getMessage(), null);
		} catch (WorkspaceExecutionSnapshot.SnapshotException exception) {
			if (job.isCancelled() || job.cancellationRequested()) return;
			job.fail(exception.code(), UUID.randomUUID().toString(), taskKind(operation),
					"diagnostic.workspace_snapshot_failed", exception.getMessage(), null);
		} catch (BundledJdkLocator.MissingJdkException exception) {
			if (job.isCancelled() || job.cancellationRequested()) return;
			String failureId = UUID.randomUUID().toString();
			LOG.error("Workspace task failure {} (backend={}, operation={}, workspaceId={})", failureId,
					backend.displayName(), operation, workspaceId, exception);
			job.log("error", exception.getMessage());
			job.fail(exception.diagnosticCode(), failureId, taskKind(operation), exception.getMessage());
		} catch (dev.copperbench.gradle.GradleRuntimeCompatibility.LoopbackUnavailableException exception) {
			if (job.isCancelled() || job.cancellationRequested()) return;
			job.log("error", exception.getMessage());
			job.fail("GRADLE_LOOPBACK_UNAVAILABLE", UUID.randomUUID().toString(), taskKind(operation),
					"diagnostic.gradle_loopback_unavailable", exception.getMessage(), null);
		} catch (GradleProcessRunner.ProcessStartException exception) {
			if (job.isCancelled() || job.cancellationRequested()) return;
			String failureId = UUID.randomUUID().toString();
			String code = backend.diagnosticPrefix() + "_" + taskKind(operation).toUpperCase(Locale.ROOT)
					+ "_PROCESS_START_FAILED";
			LOG.error("Workspace task failure {} (backend={}, operation={}, workspaceId={}, code={})", failureId,
					backend.displayName(), operation, workspaceId, code, exception);
			job.log("error", exception.getMessage());
			JsonObject args = new JsonObject();
			args.addProperty("executable", exception.executable());
			args.addProperty("workspaceRoot", exception.workspaceRoot().toString());
			args.addProperty("reason", exception.getCause() == null ? exception.getMessage()
					: exception.getCause().getMessage());
			job.fail(code, failureId, taskKind(operation), "diagnostic.workspace_task_failed",
					"The {backend} {task} task could not start its Gradle process.", args);
		} catch (Exception exception) {
			if (job.isCancelled() || job.cancellationRequested()) return;
			String failureId = UUID.randomUUID().toString();
			LOG.error("Workspace task failure {} (backend={}, operation={}, workspaceId={})", failureId,
					backend.displayName(), operation, workspaceId, exception);
			job.fail(backend.diagnosticPrefix() + "_" + taskKind(operation).toUpperCase(Locale.ROOT) + "_FAILED",
					failureId, taskKind(operation));
		}
	}

	private boolean runGameTests(Path snapshot, WorkspaceState state, JsonObject payload, Job job) throws Exception {
		var configuration = GameTestSupport.configuration(snapshot);
		JsonObject verification = GameTestReport.empty("GAMETEST_NOT_COMPLETED");
		verification.addProperty("mode", configuration.mode());
		verification.addProperty("status", "pending");
		verification.addProperty("minimumTests", configuration.minimumTests());
		verification.add("environment", backend.gameTestEnvironment());
		job.record("verification", verification);
		Path runRoot = snapshot, report = GameTestSupport.safePath(snapshot, configuration.reportPath());
		List<String> arguments = List.of(configuration.task());
		if (configuration.mode().equals("packaged_jar")) {
			boolean serverAuthorized = payload.has("serverEulaAuthorized") && payload.get("serverEulaAuthorized").getAsBoolean();
			GameTestSupport.requireServerAuthorization(backend.gameTestEnvironment(), serverAuthorized);
			job.progress(0.45, "task.run_gametest.building", "Building the frozen workspace for acceptance");
			var build = processes.run(snapshot, backend.gradleArguments(Operation.BUILD_WORKSPACE), Duration.ofMinutes(20), line -> {
				job.log("info", line); job.captureJavaCompileDiagnostic(snapshot, line);
			});
			if (build.exitCode() != 0) throw new GameTestSupport.TestSetupException("GAMETEST_BUILD_FAILED", "Tested mod build exited " + build.exitCode());
			var host = GameTestSupport.host(snapshot, snapshot.resolveSibling("host"), configuration, backend.gameTestEnvironment());
			if (GameTestSupport.requiresServerEula(backend.gameTestEnvironment()))
				Files.writeString(host.root().resolve("run/eula.txt"), "eula=true\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
			runRoot = host.root(); report = host.report(); arguments = List.of("runGameTest");
			verification.addProperty("artifactPath", host.jar().toString());
			verification.addProperty("artifactSha256", host.jarSha256());
			job.record("verification", verification);
			job.log("info", "Testing packaged JAR SHA-256 " + host.jarSha256());
		}
		// A report present before this invocation cannot be acceptance evidence.
		if (Files.exists(report)) throw new GameTestSupport.TestSetupException("GAMETEST_REPORT_STALE", "Report already exists before test execution");
		job.progress(0.65, "task.run_gametest.running", "Running acceptance tests");
		var startedAt = clock.instant();
		var process = processes.run(runRoot, arguments, Duration.ofMinutes(20), line -> job.log("info", line));
		JsonObject parsed = GameTestReport.read(report, startedAt, configuration.minimumTests());
		for (var entry : parsed.entrySet()) verification.add(entry.getKey(), entry.getValue());
		verification.addProperty("startedAt", startedAt.toString());
		verification.addProperty("completedAt", clock.instant().toString());
		verification.addProperty("processExitCode", process.exitCode());
		if (process.exitCode() != 0) GameTestReport.reason(verification, "GAMETEST_PROCESS_EXITED");
		if (configuration.mode().equals("packaged_jar") && !verification.get("artifactSha256").getAsString()
				.equals(WorkspaceExecutionSnapshot.sha256(Path.of(verification.get("artifactPath").getAsString()))))
			GameTestReport.reason(verification, "GAMETEST_ARTIFACT_CHANGED");
		boolean sourceCurrent = store.read(state.id()).map(current -> current.revision() == state.revision()).orElse(false)
				&& job.task().getAsJsonObject("sourceSnapshot").get("sha256").getAsString().equals(
				WorkspaceExecutionSnapshot.fingerprint(workspaceRoots.apply(state.id()), job::cancellationRequested));
		verification.addProperty("sourceCurrentAtCompletion", sourceCurrent);
		if (!sourceCurrent) GameTestReport.reason(verification, "GAMETEST_SOURCE_CHANGED");
		job.record("verification", verification);
		job.persistVerification();
		verification = job.task().getAsJsonObject("verification");
		if (!verification.get("status").getAsString().equals("passed")) {
			job.fail(verification.get("reasonCode").getAsString(), UUID.randomUUID().toString(), "run_gametest",
					"diagnostic.gametest_not_verified", "GameTest acceptance failed; inspect the structured test report.", null);
			return false;
		}
		job.log("info", "GameTest: " + verification.get("passed") + " passed, " + verification.get("failed")
				+ " failed, " + verification.get("skipped") + " skipped");
		return true;
	}

	private void failKnownTask(UUID workspaceId, Operation operation, Job job, String code, String messageKey,
			String fallback, JsonObject args) {
		if (job.isCancelled()) return;
		String failureId = UUID.randomUUID().toString();
		LOG.error("Workspace task failure {} (backend={}, operation={}, workspaceId={}, code={}, args={})", failureId,
				backend.displayName(), operation, workspaceId, code, args);
		job.fail(code, failureId, taskKind(operation), messageKey, fallback, args);
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
		if (!job.cancelAndAwait())
			throw new IllegalStateException("Task cancellation did not finish process cleanup before the timeout");
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

	@Override public Optional<JsonObject> sourcePreview(UUID workspaceId, UUID taskId, String sourcePath) {
		Job job = job(workspaceId, taskId);
		if (job == null) return Optional.empty();
		synchronized (job) {
			String relative = diagnosticSourcePath(sourcePath);
			String diagnosticPath = "/" + relative;
			boolean owned = job.diagnostics().stream()
					.anyMatch(diagnostic -> diagnostic.has("path") && !diagnostic.get("path").isJsonNull()
							&& diagnosticPath.equals(diagnostic.get("path").getAsString()));
			if (!owned)
				throw new IllegalArgumentException("Source preview path is not referenced by this task diagnostic");
			JsonObject preview = job.sourcePreviews.get(diagnosticPath);
			return preview == null ? Optional.empty() : Optional.of(preview.deepCopy());
		}
	}

	private static String diagnosticSourcePath(String sourcePath) {
		if (sourcePath == null || sourcePath.isBlank() || sourcePath.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Diagnostic source path is required");
		String candidate = sourcePath.replace('\\', '/');
		while (candidate.startsWith("/")) candidate = candidate.substring(1);
		Path relative = Path.of(candidate).normalize();
		if (relative.isAbsolute() || relative.startsWith(".."))
			throw new IllegalArgumentException("Diagnostic source path escaped task staging");
		String normalized = relative.toString().replace('\\', '/');
		if (!normalized.startsWith("src/main/java/") || !normalized.toLowerCase(Locale.ROOT).endsWith(".java"))
			throw new IllegalArgumentException("Only generated Java diagnostic sources can be previewed");
		return normalized;
	}

	private static JsonObject captureSourcePreview(Path executionRoot, String compilerSource, String diagnosticPath,
			String lineNumber) {
		try {
			String relative = diagnosticSourcePath(diagnosticPath);
			Path normalizedRoot = executionRoot.toAbsolutePath().normalize();
			Path rawSource = Path.of(compilerSource);
			Path source = rawSource.isAbsolute() ? rawSource.toAbsolutePath().normalize()
					: normalizedRoot.resolve(rawSource).normalize();
			if (!source.equals(resolveInside(normalizedRoot, relative)) || !Files.isRegularFile(source)) return null;
			Path realRoot = normalizedRoot.toRealPath();
			Path realSource = source.toRealPath();
			if (!realSource.startsWith(realRoot)) return null;
			long size = Files.size(realSource);
			if (size > MAX_SOURCE_PREVIEW_BYTES) return null;
			JsonObject preview = new JsonObject();
			preview.addProperty("path", "/" + relative);
			preview.addProperty("language", "java");
			preview.addProperty("content", Files.readString(realSource, StandardCharsets.UTF_8));
			preview.addProperty("size", size);
			preview.addProperty("line", Integer.parseInt(lineNumber));
			return preview;
		} catch (java.io.IOException | RuntimeException exception) {
			return null;
		}
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
		boolean sourceChanged = job.summary.has("sourceSnapshot") && !job.summary.getAsJsonObject("sourceSnapshot").get("sha256").getAsString()
				.equals(WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
		JsonObject preview = new JsonObject();
		preview.addProperty("taskId", job.id().toString());
		preview.addProperty("sourceRevision", job.sourceRevision);
		preview.addProperty("currentRevision", currentRevision);
		preview.addProperty("manifestHash", HexFormat.of().formatHex(manifestDigest.digest()));
		preview.add("files", files);
		preview.addProperty("changeCount", changes);
		preview.addProperty("stale", currentRevision != job.sourceRevision || sourceChanged);
		preview.addProperty("published", job.published);
		preview.addProperty("canPublish", changes > 0 && currentRevision == job.sourceRevision && !sourceChanged && !job.published);
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
				|| operation == Operation.RUN_DATAGEN || operation == Operation.RUN_GAMETEST || operation == Operation.PREPARE_GAME_TESTS;
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
			case PREPARE_GAME_TESTS -> "prepare_game_tests";
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
		private final Map<String, JsonObject> sourcePreviews = new HashMap<>();
		private final Set<String> javaCompileDiagnosticKeys = new HashSet<>();
		private Future<?> future;
		private Path executionRoot;
		private long sourceRevision;
		private WorkspaceState sourceState;
		private PublishSession publishSession;
		private boolean published;
		private final CountDownLatch workerFinished = new CountDownLatch(1);
		private boolean workerStarted;
		private boolean cancellationRequested;

		private Job(UUID workspaceId, JsonObject summary) {
			this.workspaceId = workspaceId;
			this.summary = summary;
		}

		private synchronized JsonObject task() {
			return summary.deepCopy();
		}

		private synchronized void record(String key, JsonObject value) {
			summary.add(key, value.deepCopy());
		}

		private UUID id() {
			return UUID.fromString(summary.get("id").getAsString());
		}

		private Operation operation() {
			return switch (summary.get("kind").getAsString()) {
				case "run_datagen" -> Operation.RUN_DATAGEN;
				case "run_server" -> Operation.RUN_SERVER;
				case "run_gametest" -> Operation.RUN_GAMETEST;
				case "prepare_game_tests" -> Operation.PREPARE_GAME_TESTS;
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

		private synchronized void persistVerification() {
			if (operation() != Operation.RUN_GAMETEST || !summary.has("verification")) return;
			JsonObject verification = summary.getAsJsonObject("verification");
			verification.addProperty("taskId", id().toString());
			verification.addProperty("workspaceId", workspaceId.toString());
			verification.addProperty("workspaceRevision", sourceRevision);
			if (summary.has("sourceSnapshot")) verification.add("sourceSnapshot", summary.get("sourceSnapshot").deepCopy());
			if (executionRoot == null || !Files.isDirectory(executionRoot)) return;
			Path path = executionRoot.resolveSibling("verification.json");
			try {
				WorkspaceExecutionSnapshot.rejectLinks(path);
				verification.addProperty("verificationPath", path.toString());
				Files.writeString(path, verification.toString() + "\n", StandardCharsets.UTF_8);
			} catch (java.io.IOException exception) {
				verification.remove("verificationPath");
				GameTestReport.reason(verification, "GAMETEST_EVIDENCE_WRITE_FAILED");
			}
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
			fail(code, failureId, taskKind, "diagnostic.workspace_task_failed",
					"The {backend} {task} task failed.", null);
		}

		private void fail(String code, String failureId, String taskKind, String detail) {
			fail(code, failureId, taskKind, "diagnostic.bundled_jdk_missing", detail, null);
		}

		private void fail(String code, String failureId, String taskKind, String messageKey, String fallback,
				JsonObject extraArgs) {
			synchronized (this) {
				if (!isRunning()) return;
				failVerification(code);
			}
			log("error", "Task failed. Error ID: " + failureId);
			WorkspaceTaskGateway.TaskEvent diagnosticsEvent;
			WorkspaceTaskGateway.TaskEvent completedEvent;
			synchronized (this) {
				if (!isRunning()) return;
				addFailureDiagnostic(code, failureId, taskKind, messageKey, fallback, extraArgs);
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

		private synchronized void failVerification(String code) {
			if (operation() != Operation.RUN_GAMETEST) return;
			if (!summary.has("verification")) summary.add("verification", GameTestReport.empty(code));
			else GameTestReport.reason(summary.getAsJsonObject("verification"), code);
			persistVerification();
		}

		private void addFailureDiagnostic(String code, String failureId, String taskKind, String messageKey,
				String fallback, JsonObject extraArgs) {
			JsonObject args = new JsonObject();
			args.addProperty("backend", backend.displayName());
			args.addProperty("task", taskKind);
			args.addProperty("failureId", failureId);
			if (extraArgs != null)
				extraArgs.entrySet().forEach(entry -> args.add(entry.getKey(), entry.getValue().deepCopy()));
			JsonObject diagnostic = new JsonObject();
			diagnostic.addProperty("code", code);
			diagnostic.addProperty("severity", "error");
			diagnostic.add("message", localized(messageKey, fallback, args));
			diagnostic.add("path", JsonNull.INSTANCE);
			diagnostic.add("elementId", JsonNull.INSTANCE);
			diagnostic.addProperty("recoverable", true);
			JsonObject action = new JsonObject();
			action.addProperty("id", "open_logs");
			action.add("label", localized("action.open_logs", "View logs"));
			action.addProperty("kind", "open_logs");
			action.addProperty("target", failureId);
			JsonObject actionPayload = new JsonObject();
			actionPayload.addProperty("taskId", id().toString());
			action.add("payload", actionPayload);
			JsonArray actions = new JsonArray();
			actions.add(action);
			diagnostic.add("actions", actions);
			diagnosticEntries.add(diagnostic);
		}

		private JsonObject procedureDiagnosticTarget(String path, UUID elementId) {
			if (path == null) return null;
			String prefix = "/elements/" + elementId + "/procedureIr/nodes/";
			if (!path.startsWith(prefix)) return null;
			String tail = path.substring(prefix.length());
			int portSeparator = tail.indexOf("/ports/");
			String nodeId = portSeparator >= 0 ? tail.substring(0, portSeparator) : tail;
			try {
				UUID.fromString(nodeId);
			} catch (IllegalArgumentException exception) {
				return null;
			}
			JsonObject target = new JsonObject();
			target.addProperty("nodeId", nodeId);
			if (portSeparator >= 0) {
				String port = tail.substring(portSeparator + "/ports/".length());
				if (!port.isBlank() && !port.contains("/")) target.addProperty("port", port);
			}
			return target;
		}

		private void captureJavaCompileDiagnostic(Path executionRoot, String line) {
			Matcher matcher = JAVA_COMPILE_ERROR.matcher(line == null ? "" : line.trim());
			if (!matcher.matches())
				return;
			String source = matcher.group(1);
			String lineNumber = matcher.group(2);
			String compilerMessage = matcher.group(3).trim();
			String path = diagnosticPath(executionRoot, source);
			String message = "Line " + lineNumber + ": " + compilerMessage;
			String key = path + "\n" + message;
			UUID elementId = resolveGeneratedElement(path);
			JsonObject sourcePreview = captureSourcePreview(executionRoot, source, path, lineNumber);
			synchronized (this) {
				if (isRunning() && javaCompileDiagnosticKeys.add(key)) {
					addDiagnostic("JAVA_COMPILE_ERROR", message, path, elementId);
					if (sourcePreview != null) sourcePreviews.put(path, sourcePreview);
				}
			}
		}

		private UUID resolveGeneratedElement(String path) {
			WorkspaceState snapshot = sourceState;
			if (snapshot == null || path == null || path.isBlank()) return null;
			String fileName;
			try {
				fileName = Path.of(path.replace('/', java.io.File.separatorChar)).getFileName().toString();
			} catch (RuntimeException exception) {
				return null;
			}
			String stem = fileName.toLowerCase(Locale.ROOT).endsWith(".java")
					? fileName.substring(0, fileName.length() - 5) : fileName;
			String normalizedStem = normalizeGeneratedName(stem);
			List<WorkspaceState.Element> matches = snapshot.elements().stream()
					.filter(element -> generatedSourceMatches(element, normalizedStem)).toList();
			return matches.size() == 1 ? matches.getFirst().id() : null;
		}

		private static boolean generatedSourceMatches(WorkspaceState.Element element, String normalizedStem) {
			String name = normalizeGeneratedName(element.name());
			return switch (element.type()) {
				case "procedure" -> normalizedStem.equals(name + "procedure");
				case "block", "item" -> false;
				case "code" -> normalizedStem.equals(name) || normalizedStem.equals(name + "element");
				default -> normalizedStem.equals(name + "element");
			};
		}

		private static String normalizeGeneratedName(String value) {
			return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		}

		private static String diagnosticPath(Path executionRoot, String source) {
			String normalizedSource = source.replace('\\', '/');
			String normalizedRoot = executionRoot.toAbsolutePath().normalize().toString().replace('\\', '/');
			if (normalizedSource.regionMatches(true, 0, normalizedRoot, 0, normalizedRoot.length())) {
				String relative = normalizedSource.substring(normalizedRoot.length());
				return relative.startsWith("/") ? relative : "/" + relative;
			}
			int src = normalizedSource.indexOf("/src/");
			if (src >= 0)
				return normalizedSource.substring(src);
			int slash = normalizedSource.lastIndexOf('/');
			return "/" + (slash >= 0 ? normalizedSource.substring(slash + 1) : normalizedSource);
		}

		private void failValidation(List<GradleWorkspaceBackend.ValidationIssue> issues) {
			failVerification("GAMETEST_WORKSPACE_INVALID");
			for (var issue : issues) {
				log("error", issue.message());
				synchronized (this) {
					if (isRunning()) addDiagnostic(issue.code(), issue.message(), issue.path(), issue.elementId(),
							issue.repairValue());
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
			addDiagnostic(code, message, path, elementId, null);
		}

		private void addDiagnostic(String code, String message, String path, UUID elementId, JsonElement repairValue) {
			JsonObject diagnostic = new JsonObject();
			diagnostic.addProperty("code", code);
			diagnostic.addProperty("severity", "error");
			JsonObject messageArgs = new JsonObject();
			messageArgs.addProperty("message", message);
			diagnostic.add("message", localized("diagnostic." + code.toLowerCase(Locale.ROOT), message, messageArgs));
			if (path == null) diagnostic.add("path", JsonNull.INSTANCE); else diagnostic.addProperty("path", path);
			if (elementId == null) diagnostic.add("elementId", JsonNull.INSTANCE);
			else diagnostic.addProperty("elementId", elementId.toString());
			diagnostic.addProperty("recoverable", true);
			JsonArray actions = new JsonArray();
			if (elementId != null) {
				String elementPath = "/elements/" + elementId;
				JsonObject procedureTarget = procedureDiagnosticTarget(path, elementId);
				String fieldTarget = procedureTarget == null && path != null && path.startsWith(elementPath + "/")
						? path.substring(elementPath.length()) : null;
				if (fieldTarget != null && fieldTarget.startsWith("/values/"))
					fieldTarget = fieldTarget.substring("/values".length());
				JsonObject locate = new JsonObject();
				if (procedureTarget != null) {
					locate.addProperty("id", "open_procedure_node");
					locate.add("label", localized("action.open_procedure_node", "Locate node"));
					locate.addProperty("kind", "open_procedure_node");
					locate.addProperty("target", procedureTarget.get("nodeId").getAsString());
					locate.add("payload", procedureTarget);
				} else {
					locate.addProperty("id", fieldTarget == null ? "locate_element" : "locate_generator_field");
					locate.add("label", fieldTarget == null
							? localized("action.open_element", "Open element")
							: localized("action.open_field", "Locate invalid field"));
					locate.addProperty("kind", "open_field");
					if (fieldTarget == null) locate.add("target", JsonNull.INSTANCE);
					else locate.addProperty("target", fieldTarget);
				}
				actions.add(locate);
				if (procedureTarget == null && fieldTarget != null && repairValue != null) {
					JsonObject change = new JsonObject();
					change.addProperty("path", fieldTarget);
					change.add("value", repairValue.deepCopy());
					JsonArray changes = new JsonArray();
					changes.add(change);
					JsonObject updatePayload = new JsonObject();
					updatePayload.addProperty("elementId", elementId.toString());
					updatePayload.add("changes", changes);
					JsonObject step = new JsonObject();
					step.addProperty("operation", "update_mod_element");
					step.add("payload", updatePayload);
					JsonArray operations = new JsonArray();
					operations.add(step);
					JsonObject repairPayload = new JsonObject();
					repairPayload.addProperty("expectedRevision", sourceRevision);
					repairPayload.addProperty("requireRecoveryPoint", true);
					repairPayload.add("operations", operations);
					JsonObject repair = new JsonObject();
					repair.addProperty("id", "preview_generator_repair");
					repair.add("label", localized("action.preview_repair", "Preview safe repair"));
					repair.addProperty("kind", "preview_repair");
					repair.add("target", JsonNull.INSTANCE);
					repair.add("payload", repairPayload);
					actions.add(repair);
				}
			}
			if (path != null) {
				if (path.startsWith("/src/main/java/") && path.toLowerCase(Locale.ROOT).endsWith(".java")) {
					JsonObject source = new JsonObject();
					source.addProperty("id", "open_generated_source");
					source.add("label", localized("action.open_source", "View generated source"));
					source.addProperty("kind", "open_source");
					source.addProperty("target", path);
					actions.add(source);
				}
				JsonObject logs = new JsonObject();
				logs.addProperty("id", "open_task_logs");
				logs.add("label", localized("action.open_logs", "View task logs"));
				logs.addProperty("kind", "open_logs");
				logs.addProperty("target", id().toString());
				actions.add(logs);
			}
			diagnostic.add("actions", actions);
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

		private synchronized void workerStarted() {
			workerStarted = true;
		}

		private void workerFinished() {
			WorkspaceTaskGateway.TaskEvent event = null;
			synchronized (this) {
				if (cancellationRequested && isRunning()) event = completeCancelled();
			}
			workerFinished.countDown();
			if (event != null) {
				log("warning", backend.displayName() + " task cancelled");
				publishTaskEvent(event);
			}
		}

		private boolean cancelAndAwait() {
			Future<?> currentFuture;
			boolean cancelledBeforeStart = false;
			synchronized (this) {
				if (!isRunning()) return isCancelled();
				cancellationRequested = true;
				currentFuture = future;
				if (currentFuture == null) {
					WorkspaceTaskGateway.TaskEvent event = completeCancelled();
					workerFinished.countDown();
					publishTaskEvent(event);
					return true;
				}
				if (!workerStarted) cancelledBeforeStart = currentFuture.cancel(false);
				if (!cancelledBeforeStart) currentFuture.cancel(true);
			}
			if (cancelledBeforeStart) {
				WorkspaceTaskGateway.TaskEvent event;
				synchronized (this) {
					event = isRunning() ? completeCancelled() : null;
				}
				workerFinished.countDown();
				if (event != null) {
					log("warning", backend.displayName() + " task cancelled");
					publishTaskEvent(event);
				}
				return true;
			}
			try {
				return workerFinished.await(15, TimeUnit.SECONDS) && isCancelled();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		private WorkspaceTaskGateway.TaskEvent completeCancelled() {
			failVerification("GAMETEST_CANCELLED");
			summary.addProperty("state", "cancelled");
			summary.addProperty("cancellable", false);
			summary.addProperty("progress", 1);
			summary.add("stage", localized("task.cancelled", "Task cancelled"));
			summary.addProperty("completedAt", clock.instant().toString());
			return new WorkspaceTaskGateway.TaskEvent(workspaceId, id(), "task_completed", summary,
					List.of(), List.of());
		}

		private boolean isRunning() {
			return summary.get("state").getAsString().equals("running");
		}

		private synchronized boolean isCancelled() {
			return summary.get("state").getAsString().equals("cancelled");
		}

		private synchronized boolean cancellationRequested() {
			return cancellationRequested;
		}
	}
}
