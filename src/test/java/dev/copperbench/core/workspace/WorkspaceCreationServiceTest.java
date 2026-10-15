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

	@Test void failureDetailsRetainCausesButRedactCredentialsAndStayBounded() {
		String detail = WorkspaceCreationService.failureDetail(new IllegalStateException("Workspace setup failed",
				new java.io.IOException("Cannot connect to https://user:password@example.invalid/; token=secret-value")));
		assertTrue(detail.contains("Cannot connect"));
		assertFalse(detail.contains("user:password"));
		assertFalse(detail.contains("secret-value"));
		assertTrue(WorkspaceCreationService.failureDetail(new RuntimeException("x".repeat(9000))).length() <= 4001);
	}

	@Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
	void failedCreationCleanupRemovesReadOnlyGitObjectsAndPreservesOutsideFiles() throws Exception {
		Path outside = temporaryFolder.resolve("outside.txt");
		Files.writeString(outside, "outside workspace");
		var outsideAttributes = Files.getFileAttributeView(outside, java.nio.file.attribute.DosFileAttributeView.class);
		outsideAttributes.setReadOnly(true);
		try {
			for (boolean preserve : List.of(false, true)) {
				Path workspace = temporaryFolder.resolve("failed-" + preserve);
				Path gitObject = workspace.resolve(".mcreator/localHistory/objects/ab/cdef");
				Files.createDirectories(gitObject.getParent());
				Files.writeString(gitObject, "created history object");
				Files.getFileAttributeView(gitObject, java.nio.file.attribute.DosFileAttributeView.class).setReadOnly(true);
				Files.writeString(workspace.resolve("build.gradle"), "created build file");
				WorkspaceCreationService.cleanupPartialWorkspace(workspace, preserve);
				assertEquals(preserve, Files.exists(workspace));
				if (preserve) {
					try (var children = Files.list(workspace)) { assertEquals(0, children.count()); }
				}
				assertEquals("outside workspace", Files.readString(outside));
				assertTrue(outsideAttributes.readAttributes().isReadOnly());
			}
		} finally {
			outsideAttributes.setReadOnly(false);
		}
	}

	private final WorkspaceCreationService service = new WorkspaceCreationService();

	@BeforeAll static void initializeUpstreamRuntimeForPersistenceTest() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void legacyCreationCatalogListsExecutableDisplayTargetsOnly() {
		List<JsonObject> generators = service.listGenerators();
		assertEquals(7, generators.size());
		for (JsonObject generator : generators) {
			assertNotNull(generator.get("generatorId").getAsString());
			assertNotNull(generator.get("loader").getAsString());
			assertNotNull(generator.get("minecraftVersion").getAsString());
			assertNotNull(generator.get("trackId").getAsString());
			assertNotNull(generator.get("displayName").getAsString());
			assertNotNull(generator.get("workspaceGeneratorName").getAsString());
		}
		// 每个轨道 × 加载器组合都出现在目录里
		for (String expected : new String[] { "fabric-26.2", "neoforge-26.2", "fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1" })
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
		assertEquals(7, projection.getAsJsonArray("generators").size());
		assertTrue(projection.get("suggestedWorkspaceFoldersRoot").getAsString().contains("MCreatorWorkspaces"));
	}

	@Test void createResourcePackPersistsSkeletonAndPackMetadata() throws Exception {
		Path suggestedRoot = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath()
				.normalize();
		Path workspaceFolder = suggestedRoot.resolve("copperbench-resource-pack-" + UUID.randomUUID()).normalize();
		try {
			WorkspaceCreationService.CreationResult result = service.create("resourcepack-1.21.1", "Copper Pack",
					"copper_pack", null, workspaceFolder.toString(), "1.0.0");

			assertTrue(result.complete(), () -> "Creation failed: " + result.diagnostics() + "\n" + result.detail());
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

			assertTrue(result.complete(), () -> "Creation failed: " + result.diagnostics() + "\n" + result.detail());
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

	@Test void nonemptyOrFileWorkspaceTargetsAreRejectedWithoutChangingTheirBytes() throws Exception {
		Path existing = Files.createDirectories(temporaryFolder.resolve("existing-workspace"));
		Path sentinel = Files.writeString(existing.resolve("existing.mcreator"), "existing user workspace", java.nio.charset.StandardCharsets.UTF_8);
		Path fileTarget = Files.writeString(temporaryFolder.resolve("existing-file"), "existing user file", java.nio.charset.StandardCharsets.UTF_8);
		for (Path target : List.of(existing, fileTarget)) {
			WorkspaceCreationService.CreationResult result = service.create("fabric-1.21.1", "First Creation", "first_creation",
					"com.example.firstcreation", target.toString(), "1.0.0");
			assertFalse(result.complete()); assertTrue(result.diagnostics().contains("WORKSPACE_FOLDER_NOT_EMPTY"));
		}
		assertEquals("existing user workspace", Files.readString(sentinel));
		assertEquals("existing user file", Files.readString(fileTarget));
		try (var children = Files.list(existing)) { assertEquals(List.of(sentinel), children.toList()); }
	}

	@Test void createRejectsInvalidModIdWithoutTouchingTheFileSystem() {
		WorkspaceCreationService.CreationResult result = service.create("fabric-1.21.1", "Test Mod", "Invalid ID!",
				"net.mcreator.test", temporaryFolder.resolve("ws").toString(), "1.0.0");
		assertFalse(result.complete());
		assertTrue(result.diagnostics().contains("MOD_ID_INVALID"));
		assertFalse(Files.exists(temporaryFolder.resolve("ws")));
	}

	@Test void createRejectsUnsupportedGenerator() {
		WorkspaceCreationService.CreationResult result = service.create("fabric-26.9", "Test Mod", "test_mod",
				"net.mcreator.test", temporaryFolder.resolve("ws").toString(), "1.0.0");
		assertFalse(result.complete());
		assertTrue(result.diagnostics().contains("UNSUPPORTED_GENERATOR"));
	}

	@Test void absoluteWorkspaceFolderMayBeOutsideSuggestedRootButCannotBeARelativePathOrFilesystemRoot() {
		assertEquals(List.of(), service.validateCreation("fabric-1.21.1", "Test Mod", "test_mod",
				"net.mcreator.test", temporaryFolder.resolve("elsewhere").toString()));
		for (String path : List.of("relative/path", temporaryFolder.toAbsolutePath().getRoot().toString()))
			assertEquals(List.of("WORKSPACE_FOLDER_OUTSIDE_ROOT"), service.validateCreation("fabric-1.21.1", "Test Mod", "test_mod",
					"net.mcreator.test", path));
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
