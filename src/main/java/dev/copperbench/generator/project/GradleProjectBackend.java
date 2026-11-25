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
import dev.copperbench.core.workspace.WorkspaceFiles;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.project.GradleProjectDetector;
import dev.copperbench.core.workspace.project.GradleProjectIdentity.Problem;
import dev.copperbench.core.workspace.project.GradleProjectOperations;
import dev.copperbench.generator.GradleWorkspaceBackend;
import dev.copperbench.generator.JavaGeneratorProfile;
import dev.copperbench.generator.PluginWorkspaceLayout;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * One version track's tasks for adopted Gradle projects (ADR-0020). The project builds with its own scripts, wrapper
 * and dependencies and no file is generated; only the isolated acceptance host uses the track's pinned coordinates.
 */
final class GradleProjectBackend implements GradleWorkspaceBackend {

	/** A source set name no template uses, so the project's own build never compiles or runs these tests. */
	static final String TEST_DIRECTORY = "src/copperbenchTest";
	static final String TARGET_CHANGED = "GRADLE_PROJECT_TARGET_CHANGED";

	private final Path distribution;
	private final JavaGeneratorProfile profile;
	private final JsonObject gameTestEnvironment;

	GradleProjectBackend(Path distribution, JavaGeneratorProfile profile) {
		this.distribution = Objects.requireNonNull(distribution).toAbsolutePath().normalize();
		this.profile = Objects.requireNonNull(profile);
		this.gameTestEnvironment = switch (profile) {
			case Fabric1211Generator.Profile fabric -> new Fabric1211Generator(distribution, fabric).gameTestEnvironment();
			case NeoForge1211Generator.Profile neoForge -> new NeoForge1211Generator(distribution, neoForge).gameTestEnvironment();
			default -> throw new IllegalArgumentException("Unsupported execution profile: " + profile.generatorId());
		};
	}

	@Override public String displayName() {
		return (fabric() ? "Fabric " : "NeoForge ") + profile.minecraftVersion();
	}

	@Override public String diagnosticPrefix() {
		return fabric() ? "FABRIC" : "NEOFORGE";
	}

	@Override public boolean generatesSources() {
		return false;
	}

	@Override public List<ValidationIssue> validate(WorkspaceState workspace) {
		List<Problem> problems = GradleProjectOperations.problems(workspace);
		if (!problems.isEmpty()) return issues(problems);
		var id = workspace.generator().get("id");
		return id != null && profile.generatorId().equals(id.getAsString()) ? List.of() : List.of(targetChanged());
	}

	/** Reads the files this task will actually build, which an editor may have changed since the project was opened. */
	@Override public List<ValidationIssue> validateContent(Path targetRoot, WorkspaceState workspace, Consumer<String> log)
			throws IOException {
		try {
			var identity = GradleProjectDetector.inspect(targetRoot);
			if (!identity.problems().isEmpty()) return issues(identity.problems());
			return profile.generatorId().equals(identity.generatorId()) ? List.of() : List.of(targetChanged());
		} catch (GradleProjectDetector.NotAProjectException missing) {
			return List.of(new ValidationIssue(missing.code(), missing.getMessage(), null, null));
		}
	}

	private ValidationIssue targetChanged() {
		return new ValidationIssue(TARGET_CHANGED, "The project's build files no longer describe " + displayName()
				+ ". Reopen the project.", null, null);
	}

	private static List<ValidationIssue> issues(List<Problem> problems) {
		return problems.stream().map(problem -> new ValidationIssue(problem.code(), problem.message(), null, null)).toList();
	}

	@Override public GenerationResult generate(Path targetRoot, WorkspaceState workspace) {
		throw new UnsupportedOperationException("Adopted Gradle projects are not generated");
	}

	@Override public JsonObject gameTestEnvironment() {
		return gameTestEnvironment.deepCopy();
	}

	@Override public String gameTestDirectory() {
		return TEST_DIRECTORY;
	}

	/** The pinned host plugins need the track's Gradle version, which the project's own wrapper may not provide. */
	@Override public void installHostWrapper(Path snapshot, Path host) throws IOException {
		PluginWorkspaceLayout.ensureGradleRuntime(host, distribution, pinnedGradle());
	}

	@Override public boolean hostSharesProjectCache(Path snapshot) throws IOException {
		return profile instanceof Fabric1211Generator.Profile fabric
				&& LoomVersion.declared(snapshot).filter(fabric.loomVersion()::equals).isPresent();
	}

	@Override public List<String> archiveTaskNames() {
		return profile instanceof Fabric1211Generator.Profile fabric && !fabric.unobfuscated()
				? List.of("remapJar", "jar") : List.of("jar");
	}

	/**
	 * Loom and ModDevGradle run the server in {@code run} and NeoGradle in {@code runs/server}. The accepted EULA goes
	 * into both in this isolated copy, and the test server stays on a local ephemeral port.
	 */
	@Override public void prepareServerRun(Path targetRoot) throws IOException {
		for (String directory : List.of("run", "runs/server")) {
			Path run = WorkspaceFiles.canonicalPath(targetRoot.resolve(directory));
			Files.createDirectories(run);
			Files.writeString(run.resolve("eula.txt"), "eula=true\n", StandardCharsets.UTF_8);
			Files.writeString(run.resolve("server.properties"), "server-ip=127.0.0.1\nserver-port=0\n", StandardCharsets.UTF_8);
		}
	}

	private boolean fabric() {
		return profile instanceof Fabric1211Generator.Profile;
	}

	private String pinnedGradle() {
		return switch (profile) {
			case Fabric1211Generator.Profile fabric -> fabric.gradleWrapperZip();
			case NeoForge1211Generator.Profile neoForge -> neoForge.fabricProfile().gradleWrapperZip();
			default -> throw new IllegalStateException("Unsupported execution profile: " + profile.generatorId());
		};
	}

	/** Reads the Loom version a build script declares; anything but a literal or a gradle.properties value is unknown. */
	static final class LoomVersion {
		private static final Pattern PLUGIN = Pattern.compile(
				"id\\s*\\(?\\s*['\"](?:net\\.fabricmc\\.)?fabric-loom(?:-remap)?['\"]\\s*\\)?\\s*version\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
		private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{?(?:project\\.)?([A-Za-z0-9_]+)}?");

		private LoomVersion() {
		}

		static Optional<String> declared(Path root) throws IOException {
			for (String name : List.of("build.gradle", "build.gradle.kts")) {
				Path script = root.resolve(name);
				if (!Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS) || Files.size(script) > 1024 * 1024) continue;
				// A commented-out or second declaration makes the effective version unknown.
				List<String> declared = PLUGIN.matcher(Files.readString(script)).results().map(match -> match.group(1)).distinct().toList();
				if (declared.size() != 1) return Optional.empty();
				String version = declared.getFirst();
				var placeholder = PLACEHOLDER.matcher(version);
				if (!placeholder.matches()) return Optional.of(version);
				Path file = root.resolve("gradle.properties");
				if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1024 * 1024) return Optional.empty();
				var properties = new Properties();
				try (var reader = Files.newBufferedReader(file)) {
					properties.load(reader);
				} catch (IllegalArgumentException invalid) {
					return Optional.empty();
				}
				return Optional.ofNullable(properties.getProperty(placeholder.group(1))).map(String::strip);
			}
			return Optional.empty();
		}
	}
}
