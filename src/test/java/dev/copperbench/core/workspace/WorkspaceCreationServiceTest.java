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
import com.google.gson.JsonParser;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.assets.ResourcePackExportService;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.WorkspaceFolderManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceCreationServiceTest {

	@TempDir Path temporaryFolder;

	private final WorkspaceCreationService service = new WorkspaceCreationService();

	@BeforeAll static void initializeUpstreamRuntimeForPersistenceTest() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void listGeneratorsCoversAllFourTracksAndBothLoaders() {
		List<JsonObject> generators = service.listGenerators();
		assertEquals(9, generators.size());
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
		JsonObject resourcePack = generators.stream()
				.filter(g -> "resourcepack-1.21.1".equals(g.get("generatorId").getAsString())).findFirst().orElseThrow();
		assertEquals("resource_pack", resourcePack.get("loader").getAsString());
		assertEquals("resource_pack", resourcePack.get("trackId").getAsString());
	}

	@Test void projectionExposesSuggestedWorkspaceFoldersRoot() {
		JsonObject projection = service.toProjection();
		assertEquals("1.0", projection.get("schemaVersion").getAsString());
		assertEquals(9, projection.getAsJsonArray("generators").size());
		assertTrue(projection.get("suggestedWorkspaceFoldersRoot").getAsString().contains("MCreatorWorkspaces"));
	}

	@Test void createResourcePackPersistsSkeletonAndPackMetadata() throws Exception {
		Path suggestedRoot = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath()
				.normalize();
		Path workspaceFolder = suggestedRoot.resolve("copperbench-resource-pack-" + UUID.randomUUID()).normalize();
		try {
			WorkspaceCreationService.CreationResult result = service.create("resourcepack-1.21.1", "Copper Pack",
					"copper_pack", null, workspaceFolder.toString(), "1.0.0");

			assertTrue(result.complete(), () -> "Creation failed: " + result.diagnostics());
			assertEquals("resourcepack-1.21.1", result.generatorId());
			assertTrue(Files.isRegularFile(workspaceFolder.resolve("copper_pack.mcreator")));
			assertTrue(Files.isRegularFile(workspaceFolder.resolve("src/main/pack.mcmeta")));
			assertTrue(Files.isRegularFile(workspaceFolder.resolve("src/main/pack.png")));
			assertTrue(Files.isRegularFile(workspaceFolder.resolve("build.gradle")));
			assertTrue(Files.isRegularFile(workspaceFolder.resolve("packloader/build.gradle")));
			var exported = new ResourcePackExportService(new AssetWorkspaceService(workspaceFolder))
					.export("src/main", "dist/copper_pack.zip");
			assertTrue(Files.isRegularFile(workspaceFolder.resolve(exported.relativePath())));
			try (ZipFile zip = new ZipFile(workspaceFolder.resolve(exported.relativePath()).toFile())) {
				assertTrue(zip.getEntry("pack.mcmeta") != null);
				assertTrue(zip.getEntry("pack.png") != null);
			}
		} finally {
			deleteRecursively(workspaceFolder);
		}
	}

	@Test void createPersistsMcreatorFileAndWorkspaceSettingsAfterValidation() throws Exception {
		Path suggestedRoot = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath()
				.normalize();
		Path workspaceFolder = suggestedRoot.resolve("copperbench-fr-close-02-" + UUID.randomUUID()).normalize();
		try {
			WorkspaceCreationService.CreationResult result = service.create("fabric-1.21.1", "Copper Trails",
					"copper_trails", "net.mcreator.copper_trails", workspaceFolder.toString(), "1.2.3");

			assertTrue(result.complete(), () -> "Creation failed: " + result.diagnostics());
			Path workspaceFile = workspaceFolder.resolve("copper_trails.mcreator");
			assertEquals(workspaceFile.toAbsolutePath().toString(), result.workspaceFile());
			assertTrue(Files.isRegularFile(workspaceFile));

			JsonObject persisted = JsonParser.parseString(Files.readString(workspaceFile)).getAsJsonObject();
			JsonObject settings = persisted.getAsJsonObject("workspaceSettings");
			assertEquals("copper_trails", settings.get("modid").getAsString());
			assertEquals("Copper Trails", settings.get("modName").getAsString());
			assertEquals("1.2.3", settings.get("version").getAsString());
			assertEquals("fabric-1.21.1", settings.get("currentGenerator").getAsString());
			assertEquals("net.mcreator.copper_trails", settings.get("modElementsPackage").getAsString());
		} finally {
			deleteRecursively(workspaceFolder);
		}
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

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root))
			return;
		try (var paths = Files.walk(root)) {
			try {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException exception) {
						throw new UncheckedIOException(exception);
					}
				});
			} catch (UncheckedIOException exception) {
				throw exception.getCause();
			}
		}
	}
}
