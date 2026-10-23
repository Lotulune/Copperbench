package dev.copperbench.assets;

import dev.copperbench.history.*;
import dev.copperbench.core.contract.UiCore.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssetImportSnapshotTest {
	@TempDir Path temp;

	@Test void sourceChangeDuringRecoveryIsRejectedBeforeReplacingTheDestination() throws Exception {
		Path root = Files.createDirectory(temp.resolve("workspace"));
		Path source = Files.write(temp.resolve("source.png"), new byte[]{1, 2, 3});
		Path destination = root.resolve("assets/probe/textures/block/test.png");
		Files.createDirectories(destination.getParent());
		Files.write(destination, new byte[]{9, 9});
		try (var durable = JGitLocalHistoryService.open(root, Clock.systemUTC())) {
			var history = afterRecovery(durable, () -> {
				Files.write(source, new byte[]{4, 5, 6});
				Files.writeString(root.resolve("independent.txt"), "keep the concurrent edit");
			});
			var importer = new AssetImportService(new AssetWorkspaceService(root), history);
			var preview = importer.preview(source, "assets/probe/textures/block/test.png");
			var failure = assertThrows(AssetImportService.AssetImportException.class,
					() -> importer.apply(preview, Actor.UI, "source-change"));
			assertEquals("ASSET_IMPORT_PLAN_STALE", failure.code());
			assertArrayEquals(new byte[]{9, 9}, Files.readAllBytes(destination));
			assertEquals("keep the concurrent edit", Files.readString(root.resolve("independent.txt")));
		}
	}

	@Test void batchImportsTheReviewedSnapshotWhenTheExternalSourceChangesLater() throws Exception {
		Path root = Files.createDirectory(temp.resolve("workspace"));
		Path source = Files.write(temp.resolve("source.png"), new byte[]{1, 2, 3});
		try (var durable = JGitLocalHistoryService.open(root, Clock.systemUTC())) {
			var history = afterRecovery(durable, () -> Files.write(source, new byte[]{4, 5, 6}));
			var importer = new AssetImportBatchService(new AssetWorkspaceService(root), history);
			var preview = importer.preview(List.of(new AssetImportBatchService.Request(source, "assets/probe/textures/block/test.png")));
			var applied = importer.apply(preview, Actor.UI, "batch-source-change");
			assertEquals(preview.items().getFirst().sourceSha256(), applied.assets().getFirst().sha256());
			assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("assets/probe/textures/block/test.png")));
		}
	}

	private static LocalHistoryService afterRecovery(LocalHistoryService delegate, Change change) {
		return new LocalHistoryService() {
			public RecoveryPoint createRecoveryPoint(RecoveryPointRequest request) throws LocalHistoryException {
				var point = delegate.createRecoveryPoint(request);
				try { change.run(); } catch (Exception failure) { throw new RuntimeException(failure); }
				return point;
			}
			public List<RecoveryPoint> listRecoveryPoints() throws LocalHistoryException { return delegate.listRecoveryPoints(); }
			public String currentRecoveryPointId() throws LocalHistoryException { return delegate.currentRecoveryPointId(); }
			public List<WorkspaceChange> compare(String a, String b) throws LocalHistoryException { return delegate.compare(a, b); }
			public List<WorkspaceChange> previewRestore(String id) throws LocalHistoryException { return delegate.previewRestore(id); }
			public RestoreResult restore(String id) throws LocalHistoryException { return delegate.restore(id); }
			public void close() {}
		};
	}
	@FunctionalInterface private interface Change { void run() throws Exception; }
}
