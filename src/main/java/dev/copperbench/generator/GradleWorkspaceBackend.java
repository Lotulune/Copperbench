/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import dev.copperbench.core.workspace.WorkspaceState;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Loader-specific generation boundary used by the shared Gradle task gateway. */
public interface GradleWorkspaceBackend {

	String displayName();

	String diagnosticPrefix();

	List<ValidationIssue> validate(WorkspaceState workspace);

	GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws Exception;

	record GenerationResult(String generatorId, String modId, List<String> generatedPaths) {
		public GenerationResult {
			generatedPaths = List.copyOf(generatedPaths);
		}
	}

	record ValidationIssue(String code, String message, String path, UUID elementId) {
	}
}
