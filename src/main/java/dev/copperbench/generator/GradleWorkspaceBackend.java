/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Loader-specific generation boundary used by the shared Gradle task gateway. */
public interface GradleWorkspaceBackend {

	String displayName();

	String diagnosticPrefix();

	List<ValidationIssue> validate(WorkspaceState workspace);

	GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws Exception;

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

	/** Copies the generator artifact to the requested output path. */
	default Path export(Path targetRoot, JsonObject payload) throws Exception {
		return GradleWorkspaceTaskGateway.exportJar(targetRoot, payload);
	}

	record GenerationResult(String generatorId, String modId, List<String> generatedPaths) {
		public GenerationResult {
			generatedPaths = List.copyOf(generatedPaths);
		}
	}

	record ValidationIssue(String code, String message, String path, UUID elementId) {
	}
}
