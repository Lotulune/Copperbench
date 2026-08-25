/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.workspace;

import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.gradle.GradleDistributionPool;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Builds empty workspaces from the eight visual workspace-generator plugins. */
@EnabledIfSystemProperty(named = "copperbench.stage8.workspaceGeneratorBuild", matches = "true")
class NewWorkspaceGeneratorGoldenBuildTest {

	private static final List<String> GENERATOR_IDS = List.of(
			"fabric-26.2", "neoforge-26.2", "fabric-26.1.2", "neoforge-26.1.2",
			"fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1");
	private static final String SELECTED_GENERATOR_PROPERTY = "copperbench.stage8.workspaceGeneratorId";

	@BeforeAll static void initializeMcreatorRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@TestFactory
	Stream<DynamicTest> emptyWorkspaceGeneratorsBuildIntoJars() {
		String selected = System.getProperty(SELECTED_GENERATOR_PROPERTY, "").trim();
		List<String> generators = selected.isEmpty() ? GENERATOR_IDS
				: GENERATOR_IDS.stream().filter(selected::equals).toList();
		if (generators.isEmpty())
			throw failure(selected, "catalog", "Unknown generator id");
		return generators.stream().map(generatorId -> DynamicTest.dynamicTest(
				generatorId + " - empty workspace Gradle build", () -> buildEmptyWorkspace(generatorId)));
	}

	private static void buildEmptyWorkspace(String generatorId) throws Exception {
		Path workspaceRoot = Files.createTempDirectory("copperbench-stage8-" + generatorId.replace('.', '_') + "-")
				.toAbsolutePath().normalize();
		File previousJavaHome = PreferencesManager.PREFERENCES.hidden.java_home.get();
		Path gradleJavaHome = javaHomeFor(generatorId);
		Path gradleHome = Path.of("build", "stage8-workspace-generator-gradle",
				generatorId.replace('.', '_')).toAbsolutePath().normalize();
		PreferencesManager.PREFERENCES.hidden.java_home.set(gradleJavaHome.resolve("bin/java.exe").toFile());
		try {
			seedGradleNetworkProperties(gradleHome);
			GeneratorConfiguration configuration = Generator.GENERATOR_CACHE.get(generatorId);
			if (configuration == null)
				throw failure(generatorId, "generator-cache", "Generator configuration is not loaded");

			WorkspaceSettings settings = new WorkspaceSettings("copperbench_empty");
			settings.setModName("Copperbench Empty Workspace");
			settings.setVersion("1.0.0");
			settings.setCurrentGenerator(generatorId);
			settings.setModElementsPackage("dev.copperbench.empty");

			try (Workspace workspace = Workspace.createWorkspace(
					workspaceRoot.resolve("copperbench_empty.mcreator").toFile(), settings)) {
				if (!workspace.getModElements().isEmpty())
					throw failure(generatorId, "empty-workspace", "Workspace unexpectedly contains mod elements");
				WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
				GradleUtils.updateMCreatorBuildFile(workspace);
				syncGradle(generatorId, workspace, configuration, gradleHome);
				workspace.getGenerator().reloadGradleCaches();
				try {
					workspace.getGenerator().generateBase(true);
				} catch (Exception exception) {
					throw failure(generatorId, "generate-base", exception.getMessage(), exception);
				}
				workspace.getGenerator().runResourceSetupTasks();
			}

			GradleRun gradle = runGradle(generatorId, workspaceRoot, gradleJavaHome, gradleHome);
			writeGradleLog(generatorId, gradle.output());
			if (!gradle.completed())
				throw failure(generatorId, "gradle-build", "Gradle build timed out\n"
						+ gradleDiagnostic(gradle.output()));
			if (gradle.exitCode() != 0)
				throw failure(generatorId, "gradle-build", "Gradle exited with " + gradle.exitCode() + "\n"
						+ gradleDiagnostic(gradle.output()));

			Path jar = findJar(workspaceRoot.resolve("build/libs"));
			if (jar == null)
				throw failure(generatorId, "jar-output", "Gradle build produced no non-sources JAR\n"
						+ gradleDiagnostic(gradle.output()));
		} catch (AssertionError error) {
			throw error;
		} catch (Exception exception) {
			throw failure(generatorId, "harness", exception.getMessage(), exception);
		} finally {
			PreferencesManager.PREFERENCES.hidden.java_home.set(previousJavaHome);
			deleteRecursively(workspaceRoot);
		}
	}

	private static Path javaHomeFor(String generatorId) {
		if (generatorId.endsWith("1.20.1"))
			return Path.of("jdk", "jdk21_win_64").toAbsolutePath().normalize();
		return Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
	}

	private static void seedGradleNetworkProperties(Path gradleHome) throws IOException {
		Path userProperties = Path.of(System.getProperty("user.home"), ".gradle", "gradle.properties");
		if (!Files.isRegularFile(userProperties))
			return;
		List<String> networkProperties = Files.readAllLines(userProperties, StandardCharsets.UTF_8).stream()
				.filter(line -> line.startsWith("systemProp.http.proxy") || line.startsWith("systemProp.https.proxy"))
				.toList();
		if (networkProperties.isEmpty())
			return;
		Files.createDirectories(gradleHome);
		Files.write(gradleHome.resolve("gradle.properties"), networkProperties, StandardCharsets.UTF_8);
	}

	private static void syncGradle(String generatorId, Workspace workspace,
			GeneratorConfiguration configuration, Path gradleHome) {
		try {
			GradleDistributionPool.seedForWorkspace(workspace.getWorkspaceFolder().toPath(), gradleHome);
			try (ProjectConnection connection = GradleConnector.newConnector()
					.forProjectDirectory(workspace.getWorkspaceFolder())
					.useGradleUserHomeDir(gradleHome.toFile()).connect()) {
			String syncTask = configuration.getGradleTaskFor("sync_task");
			if (syncTask == null)
				GradleUtils.getGradleTaskLauncher(configuration, connection, "help").run();
			else
				GradleUtils.getGradleTaskLauncher(configuration, connection, syncTask).run();
		}
		} catch (Exception exception) {
			throw failure(generatorId, "gradle-sync", exception.getMessage(), exception);
		}
	}

	private static GradleRun runGradle(String generatorId, Path workspaceRoot, Path javaHome, Path gradleHome)
			throws Exception {
		ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "--no-daemon", "build",
				"--stacktrace").directory(workspaceRoot.toFile()).redirectErrorStream(true);
		builder.environment().putAll(GradleUtils.getEnvironment(javaHome.toString()));
		builder.environment().put("GRADLE_USER_HOME", gradleHome.toString());
		Process process = builder.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		});
		boolean completed = process.waitFor(Duration.ofMinutes(20).toMillis(), TimeUnit.MILLISECONDS);
		if (!completed) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
		String log = output.get(30, TimeUnit.SECONDS);
		return new GradleRun(completed, completed ? process.exitValue() : -1, log);
	}

	private static Path findJar(Path libs) throws IOException {
		if (!Files.isDirectory(libs))
			return null;
		try (Stream<Path> paths = Files.list(libs)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
					.sorted().findFirst().orElse(null);
		}
	}

	private static AssertionError failure(String generatorId, String phase, String message) {
		return new AssertionError("WORKSPACE_GENERATOR_GOLDEN_FAILED generator=" + generatorId + " phase=" + phase
				+ " detail=" + (message == null || message.isBlank() ? "unknown" : message));
	}

	private static AssertionError failure(String generatorId, String phase, String message, Throwable cause) {
		AssertionError error = failure(generatorId, phase, message);
		error.initCause(cause);
		return error;
	}

	private static String tail(String output) {
		if (output == null)
			return "";
		return output.length() <= 4000 ? output : output.substring(output.length() - 4000);
	}

	private static String gradleDiagnostic(String output) {
		if (output == null || output.isBlank())
			return "No Gradle output";
		int marker = output.lastIndexOf("Caused by:");
		if (marker < 0)
			marker = output.lastIndexOf("* What went wrong:");
		if (marker < 0)
			return tail(output);
		int end = Math.min(output.length(), marker + 3000);
		return output.substring(marker, end).strip();
	}

	private static void writeGradleLog(String generatorId, String output) throws IOException {
		Path log = Path.of("build", "stage8-workspace-generator-logs", generatorId + ".log");
		Files.createDirectories(log.getParent());
		Files.writeString(log, output == null ? "" : output, StandardCharsets.UTF_8);
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root))
			return;
		IOException lastFailure = null;
		for (int attempt = 0; attempt < 40 && Files.exists(root); attempt++) {
			try (Stream<Path> paths = Files.walk(root)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException exception) {
						throw new UncheckedIOException(exception);
					}
				});
				return;
			} catch (UncheckedIOException exception) {
				lastFailure = exception.getCause();
				try {
					Thread.sleep(250);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw lastFailure;
				}
			} catch (IOException exception) {
				lastFailure = exception;
				try {
					Thread.sleep(250);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw lastFailure;
				}
			}
		}
		if (Files.exists(root) && lastFailure != null)
			throw lastFailure;
	}

	private record GradleRun(boolean completed, int exitCode, String output) {
	}
}
