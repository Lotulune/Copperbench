package dev.copperbench.assets;

import dev.copperbench.assets.AssetImportService.AssetImportException;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetImportServiceTest {
	@TempDir Path temp;

	@Test void previewsCreateReplaceIdenticalAndExactDuplicatePeersWithoutLeakingSourcePath() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace.resolve("assets/copperbench/textures/block"));
		Path existing = Files.write(workspace.resolve("assets/copperbench/textures/block/existing.png"),
				new byte[] { 1, 2, 3 });
		Files.write(workspace.resolve("assets/copperbench/textures/block/peer.png"), new byte[] { 9, 8, 7 });
		Path createSource = Files.write(temp.resolve("new.png"), new byte[] { 4, 5, 6 });
		Path duplicateSource = Files.write(temp.resolve("duplicate.png"), new byte[] { 9, 8, 7 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportService service = new AssetImportService(new AssetWorkspaceService(workspace), history);
			AssetImportPlan create = service.preview(createSource,
					"assets/copperbench/textures/block/new.png");
			assertEquals(AssetImportPlan.Conflict.CREATE, create.conflict());
			assertTrue(create.canApply());
			assertFalse(create.toJson().toString().contains(temp.toString()));

			AssetImportPlan replace = service.preview(createSource,
					"assets/copperbench/textures/block/existing.png");
			assertEquals(AssetImportPlan.Conflict.REPLACE, replace.conflict());
			assertTrue(replace.issueCodes().contains("ASSET_IMPORT_TARGET_WILL_REPLACE"));

			AssetImportPlan identical = service.preview(existing,
					"assets/copperbench/textures/block/existing.png");
			assertEquals(AssetImportPlan.Conflict.IDENTICAL, identical.conflict());
			assertFalse(identical.canApply());

			AssetImportPlan duplicate = service.preview(duplicateSource,
					"assets/copperbench/textures/block/imported.png");
			assertEquals(java.util.List.of("assets/copperbench/textures/block/peer.png"), duplicate.duplicatePaths());
			assertTrue(duplicate.issueCodes().contains("ASSET_IMPORT_DUPLICATE_CONTENT"));
		}
	}

	@Test void appliesFromAStablePreviewAndRecoveryPointRestoresThePreImportWorkspace() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path source = Files.write(temp.resolve("lamp.png"), new byte[] { 7, 7, 7 });
		Clock clock = Clock.fixed(Instant.parse("2026-09-05T04:00:00Z"), ZoneOffset.UTC);

		try (var history = JGitLocalHistoryService.open(workspace, clock)) {
			AssetImportService service = new AssetImportService(new AssetWorkspaceService(workspace), history);
			AssetImportPlan plan = service.preview(source, "assets/copperbench/textures/block/lamp.png");
			var result = service.apply(plan, Actor.UI, "asset-import-test");

			Path imported = workspace.resolve("assets/copperbench/textures/block/lamp.png");
			assertTrue(Files.isRegularFile(imported));
			assertEquals(plan.sourceSha256(), result.asset().sha256());
			assertEquals(1, history.listRecoveryPoints().size());
			assertTrue(result.recoveryPoint().label().startsWith("Before asset import:"));

			history.restore(result.recoveryPoint().id());
			assertFalse(Files.exists(imported));
		}
	}

	@Test void rejectsAStalePreviewBeforeCreatingARecoveryPointOrWriting() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path source = Files.write(temp.resolve("lamp.png"), new byte[] { 1 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportService service = new AssetImportService(new AssetWorkspaceService(workspace), history);
			AssetImportPlan plan = service.preview(source, "assets/copperbench/textures/block/lamp.png");
			Files.write(source, new byte[] { 2 });

			AssetImportException error = assertThrows(AssetImportException.class,
					() -> service.apply(plan, Actor.UI, "stale"));
			assertEquals("ASSET_IMPORT_PLAN_STALE", error.code());
			assertTrue(history.listRecoveryPoints().isEmpty());
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/lamp.png")));
		}
	}

	@Test void rejectsTargetsOutsideKnownAssetRootsAndUnsupportedTypes() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path png = Files.write(temp.resolve("lamp.png"), new byte[] { 1 });
		Path exe = Files.write(temp.resolve("lamp.exe"), new byte[] { 2 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportService service = new AssetImportService(new AssetWorkspaceService(workspace), history);
			assertEquals("ASSET_IMPORT_TARGET_INVALID", assertThrows(AssetImportException.class,
					() -> service.preview(png, "src/main/java/lamp.png")).code());
			assertEquals("ASSET_IMPORT_TARGET_INVALID", assertThrows(AssetImportException.class,
					() -> service.preview(png, "../lamp.png")).code());
			assertEquals("ASSET_IMPORT_TYPE_UNSUPPORTED", assertThrows(AssetImportException.class,
					() -> service.preview(exe, "assets/copperbench/models/lamp.exe")).code());
			assertEquals("ASSET_IMPORT_EXTENSION_MISMATCH", assertThrows(AssetImportException.class,
					() -> service.preview(png, "assets/copperbench/models/lamp.json")).code());
		}
	}

	@Test void rejectsTargetWhoseExistingAncestorResolvesOutsideWorkspace() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path outside = temp.resolve("outside");
		Files.createDirectories(workspace);
		Files.createDirectories(outside);
		Path linkedAssets = workspace.resolve("assets");
		try {
			Files.createSymbolicLink(linkedAssets, outside);
		} catch (Exception exception) {
			Assumptions.abort("Symbolic links are unavailable in this test environment: " + exception.getMessage());
		}
		Path source = Files.write(temp.resolve("lamp.png"), new byte[] { 3 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportService service = new AssetImportService(new AssetWorkspaceService(workspace), history);
			AssetImportException error = assertThrows(AssetImportException.class,
					() -> service.preview(source, "assets/copperbench/textures/block/lamp.png"));
			assertEquals("ASSET_IMPORT_TARGET_INVALID", error.code());
		}
	}
}
