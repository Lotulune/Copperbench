/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceCreationServiceTest {

	@TempDir Path temporaryFolder;

	private final WorkspaceCreationService service = new WorkspaceCreationService();

	@Test void listGeneratorsCoversAllFourTracksAndBothLoaders() {
		List<JsonObject> generators = service.listGenerators();
		assertEquals(8, generators.size());
		for (JsonObject generator : generators) {
			assertNotNull(generator.get("generatorId").getAsString());
			assertNotNull(generator.get("loader").getAsString());
			assertNotNull(generator.get("minecraftVersion").getAsString());
			assertNotNull(generator.get("trackId").getAsString());
			assertNotNull(generator.get("displayName").getAsString());
			assertNotNull(generator.get("workspaceGeneratorName").getAsString());
		}
		// 每个轨道 × 加载器组合都出现在目录里
		for (String expected : new String[] { "fabric-26.2", "neoforge-26.2", "fabric-26.1.2",
				"neoforge-26.1.2", "fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1" })
			assertTrue(generators.stream().anyMatch(g -> expected.equals(g.get("generatorId").getAsString())),
					"Missing generator " + expected);
	}

	@Test void projectionExposesSuggestedWorkspaceFoldersRoot() {
		JsonObject projection = service.toProjection();
		assertEquals("1.0", projection.get("schemaVersion").getAsString());
		assertEquals(8, projection.getAsJsonArray("generators").size());
		assertTrue(projection.get("suggestedWorkspaceFoldersRoot").getAsString().contains("MCreatorWorkspaces"));
	}

	@Test void createRejectsInvalidModIdWithoutTouchingTheFileSystem() {
		WorkspaceCreationService.CreationResult result = service.create("fabric-1.21.1", "Test Mod", "Invalid ID!",
				"net.mcreator.test", temporaryFolder.resolve("ws").toString(), "1.0.0");
		assertFalse(result.complete());
		// 域校验报告全部命中的诊断（临时目录同时在建议根目录之外）
		assertTrue(result.diagnostics().contains("MOD_ID_INVALID"));
		assertTrue(result.diagnostics().contains("WORKSPACE_FOLDER_OUTSIDE_ROOT"));
	}

	@Test void createRejectsUnsupportedGenerator() {
		WorkspaceCreationService.CreationResult result = service.create("fabric-26.9", "Test Mod", "test_mod",
				"net.mcreator.test", temporaryFolder.resolve("ws").toString(), "1.0.0");
		assertFalse(result.complete());
		assertTrue(result.diagnostics().contains("UNSUPPORTED_GENERATOR"));
	}

	@Test void createRejectsWorkspaceFolderOutsideSuggestedRoot() {
		WorkspaceCreationService.CreationResult result = service.create("fabric-1.21.1", "Test Mod", "test_mod",
				"net.mcreator.test", temporaryFolder.resolve("elsewhere").toString(), "1.0.0");
		assertFalse(result.complete());
		assertEquals(List.of("WORKSPACE_FOLDER_OUTSIDE_ROOT"), result.diagnostics());
	}

	@Test void createRejectsBlankModNameAndMissingPackage() {
		WorkspaceCreationService.CreationResult result = service.create("fabric-1.21.1", "", "test_mod", null,
				temporaryFolder.resolve("ws").toString(), null);
		assertFalse(result.complete());
		assertTrue(result.diagnostics().contains("MOD_NAME_INVALID"));
		assertTrue(result.diagnostics().contains("PACKAGE_NAME_INVALID"));
	}

	@Test void createRejectsBlankWorkspaceFolder() {
		WorkspaceCreationService.CreationResult result = service.create("fabric-1.21.1", "Test Mod", "test_mod",
				"net.mcreator.test", " ", null);
		assertFalse(result.complete());
		// 空白路径只报告 WORKSPACE_FOLDER_REQUIRED（不做根目录比较）
		assertEquals(List.of("WORKSPACE_FOLDER_REQUIRED"), result.diagnostics());
	}
}
