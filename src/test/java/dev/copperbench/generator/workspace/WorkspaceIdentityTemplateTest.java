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
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceIdentityTemplateTest {

	private static final List<String> GENERATOR_IDS = List.of(
			"fabric-26.2", "neoforge-26.2", "fabric-26.1.2", "neoforge-26.1.2",
			"fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1");

	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@TestFactory Stream<DynamicTest> generatedWorkspaceUsesTheUserModIdForGradleIdentity() {
		return GENERATOR_IDS.stream().map(generatorId -> DynamicTest.dynamicTest(generatorId, () -> {
			String modId = "identity_probe";
			Path workspaceRoot = root.resolve(generatorId.replace('.', '_'));
			Files.createDirectories(workspaceRoot);
			WorkspaceSettings settings = new WorkspaceSettings(modId);
			settings.setModName("Identity Probe");
			settings.setVersion("1.0.0");
			settings.setCurrentGenerator(generatorId);
			try (Workspace workspace = Workspace.createWorkspace(workspaceRoot.resolve(modId + ".mcreator").toFile(), settings)) {
				WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
				String buildGradle = Files.readString(workspaceRoot.resolve("build.gradle"));
				assertTrue(buildGradle.contains("base.archivesName = \"" + modId + "\""), buildGradle);
				assertFalse(buildGradle.contains("base.archivesName = \"modid\""), buildGradle);
				if (generatorId.startsWith("fabric-")) {
					String gradleProperties = Files.readString(workspaceRoot.resolve("gradle.properties"));
					assertTrue(gradleProperties.contains("actualmodid=" + modId), gradleProperties);
					assertFalse(gradleProperties.contains("potatoesAreBetterThanEggs"), gradleProperties);
				}
			}
		}));
	}
}
