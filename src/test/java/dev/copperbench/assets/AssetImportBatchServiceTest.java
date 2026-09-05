package dev.copperbench.assets;

import dev.copperbench.assets.AssetImportService.AssetImportException;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetImportBatchServiceTest {
	@TempDir Path temp;

	@Test void previewsAndAppliesCreateReplaceAndIdenticalAsOneRecovery() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path existing = workspace.resolve("assets/copperbench/textures/block/existing.png");
		Files.createDirectories(existing.getParent());
		Files.write(existing, new byte[] { 1, 1, 1 });
		Path createSource = Files.write(temp.resolve("new.png"), new byte[] { 2, 2, 2 });
		Path replaceSource = Files.write(temp.resolve("replacement.png"), new byte[] { 3, 3, 3 });
		Path identicalSource = Files.write(temp.resolve("same.png"), new byte[] { 1, 1, 1 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportBatchService service = new AssetImportBatchService(new AssetWorkspaceService(workspace), history);
			AssetImportBatchPlan plan = service.preview(List.of(
					new AssetImportBatchService.Request(createSource, "assets/copperbench/textures/block/new.png"),
					new AssetImportBatchService.Request(replaceSource, "assets/copperbench/textures/block/existing.png"),
					new AssetImportBatchService.Request(identicalSource, "assets/copperbench/textures/block/same_target.png")));
			// Make the third target identical before the preview that will actually be approved.
			Files.copy(identicalSource, workspace.resolve("assets/copperbench/textures/block/same_target.png"));
			plan = service.preview(List.of(
					new AssetImportBatchService.Request(createSource, "assets/copperbench/textures/block/new.png"),
					new AssetImportBatchService.Request(replaceSource, "assets/copperbench/textures/block/existing.png"),
					new AssetImportBatchService.Request(identicalSource, "assets/copperbench/textures/block/same_target.png")));

			assertTrue(plan.canApply());
			assertEquals(1, plan.createCount());
			assertEquals(1, plan.replaceCount());
			assertEquals(1, plan.identicalCount());

			var result = service.apply(plan, Actor.UI, "batch-test");
			assertEquals(2, result.importedCount());
			assertEquals(1, result.skippedIdenticalCount());
			assertEquals(1, history.listRecoveryPoints().size());
			assertTrue(Files.isRegularFile(workspace.resolve("assets/copperbench/textures/block/new.png")));
			assertEquals(List.of((byte) 3, (byte) 3, (byte) 3), bytes(existing));

			history.restore(result.recoveryPoint().id());
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/new.png")));
			assertEquals(List.of((byte) 1, (byte) 1, (byte) 1), bytes(existing));
		}
	}

	@Test void snapshotsAllSourcesBeforeAnyTargetCanOverwriteAnotherSource() throws Exception {
		Path workspace = temp.resolve("workspace-overlap");
		Path first = workspace.resolve("assets/copperbench/textures/block/first.png");
		Path second = workspace.resolve("assets/copperbench/textures/block/second.png");
		Files.createDirectories(first.getParent());
		Files.write(first, new byte[] { 1 });
		Files.write(second, new byte[] { 2 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportBatchService service = new AssetImportBatchService(new AssetWorkspaceService(workspace), history);
			AssetImportBatchPlan plan = service.preview(List.of(
					new AssetImportBatchService.Request(first, "assets/copperbench/textures/block/second.png"),
					new AssetImportBatchService.Request(second, "assets/copperbench/textures/block/third.png")));
			var result = service.apply(plan, Actor.UI, "overlap");

			assertEquals(List.of((byte) 1), bytes(second));
			assertEquals(List.of((byte) 2), bytes(workspace.resolve("assets/copperbench/textures/block/third.png")));
			assertEquals(1, history.listRecoveryPoints().size());
			assertEquals(2, result.importedCount());
		}
	}

	@Test void duplicateTargetsBlockBeforeRecoveryOrWrite() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path first = Files.write(temp.resolve("first.png"), new byte[] { 1 });
		Path second = Files.write(temp.resolve("second.png"), new byte[] { 2 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportBatchService service = new AssetImportBatchService(new AssetWorkspaceService(workspace), history);
			AssetImportBatchPlan plan = service.preview(List.of(
					new AssetImportBatchService.Request(first, "assets/copperbench/textures/block/shared.png"),
					new AssetImportBatchService.Request(second, "assets/copperbench/textures/block/SHARED.png")));
			assertFalse(plan.canApply());
			assertTrue(plan.issueCodes().contains("ASSET_IMPORT_BATCH_TARGET_CONFLICT"));
			AssetImportException failure = assertThrows(AssetImportException.class,
					() -> service.apply(plan, Actor.UI, "blocked"));
			assertEquals("ASSET_IMPORT_BATCH_NOT_APPLICABLE", failure.code());
			assertTrue(history.listRecoveryPoints().isEmpty());
		}
	}

	@Test void anyStaleSourceRejectsTheWholeBatchBeforeRecovery() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path first = Files.write(temp.resolve("first.png"), new byte[] { 1 });
		Path second = Files.write(temp.resolve("second.png"), new byte[] { 2 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetImportBatchService service = new AssetImportBatchService(new AssetWorkspaceService(workspace), history);
			AssetImportBatchPlan plan = service.preview(List.of(
					new AssetImportBatchService.Request(first, "assets/copperbench/textures/block/first.png"),
					new AssetImportBatchService.Request(second, "assets/copperbench/textures/block/second.png")));
			Files.write(second, new byte[] { 9 });

			AssetImportException failure = assertThrows(AssetImportException.class,
					() -> service.apply(plan, Actor.UI, "stale"));
			assertEquals("ASSET_IMPORT_BATCH_PLAN_STALE", failure.code());
			assertTrue(history.listRecoveryPoints().isEmpty());
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/first.png")));
		}
	}

	private static List<Byte> bytes(Path file) throws Exception {
		java.util.ArrayList<Byte> result = new java.util.ArrayList<>();
		for (byte value : Files.readAllBytes(file)) result.add(value);
		return result;
	}
}
