/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loader-specific generation boundary used by the shared Gradle task gateway. */
public interface GradleWorkspaceBackend {

	String displayName();

	String diagnosticPrefix();

	List<ValidationIssue> validate(WorkspaceState workspace);

	GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws Exception;

	/** Pinned loader coordinates used to create an independent acceptance-test host. */
	default JsonObject gameTestEnvironment() { return new JsonObject(); }

	default String gameTestModId(WorkspaceState workspace) {
		return dev.copperbench.core.workspace.WorkspaceModIdentity.resolve(workspace);
	}

	/** Gradle arguments for the task operation; resource-pack generators use a nested client task. */
	default List<String> gradleArguments(Operation operation) {
		return switch (operation) {
			case RUN_CLIENT -> List.of("runClient");
			case RUN_SERVER -> List.of("runServer");
			case RUN_DATAGEN -> List.of("runDatagen");
			case RUN_GAMETEST -> List.of("runGameTest");
			default -> List.of("build");
		};
	}

	/** Directory used as the Minecraft dedicated-server game directory for this backend. */
	default Path serverRunDirectory(Path targetRoot) {
		return targetRoot.resolve("run");
	}

	default void prepareServerRun(Path targetRoot) throws Exception {
	}

	/** Checks the artifact shape produced by the generator's build task. */
	default boolean buildOutputAvailable(Path targetRoot) {
		return Files.isDirectory(targetRoot.resolve("build/libs"));
	}

	/** Declared Gradle archive tasks, in loader priority order. Pack backends use their fixed ZIP contract. */
	default List<String> archiveTaskNames() { return List.of("remapJar", "jar"); }

	/** The exact non-JAR output declared by a pack backend's build. */
	default Path buildArtifact(Path targetRoot) {
		throw new IllegalStateException("A Gradle archive-task receipt is required for mod export");
	}

	record GenerationResult(String generatorId, String modId, List<String> generatedPaths,
			Map<String, UUID> sourceElementOwners) {
		public GenerationResult(String generatorId, String modId, List<String> generatedPaths) {
			this(generatorId, modId, generatedPaths, Map.of());
		}
		public GenerationResult {
			generatedPaths = List.copyOf(generatedPaths);
			sourceElementOwners = Map.copyOf(sourceElementOwners);
		}
	}

	record ValidationIssue(String code, String message, String path, UUID elementId, JsonElement repairValue) {
		public ValidationIssue(String code, String message, String path, UUID elementId) {
			this(code, message, path, elementId, null);
		}

		public ValidationIssue {
			repairValue = repairValue == null ? null : repairValue.deepCopy();
		}
	}
}
