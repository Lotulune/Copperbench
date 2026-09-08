package dev.copperbench.assets;

import dev.copperbench.assets.AssetMoveService.AssetMoveException;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetMoveServiceTest {
	@TempDir Path temp;

	@Test void previewsExactReferenceRewritesAndMovesWithRecovery() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path model = workspace.resolve("assets/copperbench/models/block/lamp.json");
		Path texture = workspace.resolve("assets/copperbench/textures/block/lamp.png");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:block/lamp\","
				+ "\"a/b\":\"copperbench:block/lamp\"}}");
		Files.write(texture, new byte[] { 1, 2, 3 });
		Clock clock = Clock.fixed(Instant.parse("2026-09-05T06:00:00Z"), ZoneOffset.UTC);

		try (var history = JGitLocalHistoryService.open(workspace, clock)) {
			AssetWorkspaceService assets = new AssetWorkspaceService(workspace);
			AssetMoveService service = new AssetMoveService(assets, history);
			AssetDescriptor source = assets.findByRelativePath("assets/copperbench/textures/block/lamp.png").orElseThrow();
			AssetMovePlan plan = service.preview(source.id(), "assets/copperbench/textures/block/renamed_lamp.png");

			assertTrue(plan.canApply());
			assertEquals(2, plan.rewrites().size());
			assertTrue(plan.rewrites().stream().anyMatch(rewrite -> rewrite.sourcePointer().equals("/textures/a~1b")));
			assertTrue(plan.rewrites().stream().allMatch(rewrite ->
					rewrite.newRawValue().equals("copperbench:block/renamed_lamp")));
			assertEquals(AssetDescriptor.stableIdForPath("assets/copperbench/textures/block/renamed_lamp.png"),
					plan.targetAssetId());

			var applied = service.apply(plan, Actor.UI, "asset-move-test");
			assertEquals(2, applied.rewrittenReferences());
			assertFalse(Files.exists(texture));
			assertTrue(Files.isRegularFile(workspace.resolve("assets/copperbench/textures/block/renamed_lamp.png")));
			String rewritten = Files.readString(model);
			assertTrue(rewritten.contains("copperbench:block/renamed_lamp"));
			assertFalse(rewritten.contains("copperbench:block/lamp"));
			AssetReferenceGraph refreshed = assets.referenceGraph();
			assertEquals(2, refreshed.incoming("assets/copperbench/textures/block/renamed_lamp.png").size());
			assertTrue(refreshed.diagnostics().stream().noneMatch(diagnostic ->
					"MISSING_ASSET_REFERENCE".equals(diagnostic.code())));

			history.restore(applied.recoveryPoint().id());
			assertTrue(Files.isRegularFile(texture));
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/renamed_lamp.png")));
			assertTrue(Files.readString(model).contains("copperbench:block/lamp"));
		}
	}

	@Test void rejectsStaleReferenceSourcesBeforeCreatingRecoveryOrWriting() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path model = workspace.resolve("assets/copperbench/models/block/lamp.json");
		Path texture = workspace.resolve("assets/copperbench/textures/block/lamp.png");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:block/lamp\"}}");
		Files.write(texture, new byte[] { 1 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetWorkspaceService assets = new AssetWorkspaceService(workspace);
			AssetMoveService service = new AssetMoveService(assets, history);
			AssetMovePlan plan = service.preview(assets.findByRelativePath(
					"assets/copperbench/textures/block/lamp.png").orElseThrow().id(),
					"assets/copperbench/textures/block/renamed.png");
			Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:block/lamp\"},\"note\":1}");

			AssetMoveException error = assertThrows(AssetMoveException.class,
					() -> service.apply(plan, Actor.UI, "stale"));
			assertEquals("ASSET_MOVE_PLAN_STALE", error.code());
			assertTrue(history.listRecoveryPoints().isEmpty());
			assertTrue(Files.isRegularFile(texture));
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/renamed.png")));
		}
	}

	@Test void blocksTargetConflictsCategoryChangesAndResourceRootChanges() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path texture = workspace.resolve("assets/copperbench/textures/block/lamp.png");
		Path existing = workspace.resolve("assets/copperbench/textures/block/existing.png");
		Files.createDirectories(texture.getParent());
		Files.write(texture, new byte[] { 1 });
		Files.write(existing, new byte[] { 2 });

		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			AssetWorkspaceService assets = new AssetWorkspaceService(workspace);
			AssetMoveService service = new AssetMoveService(assets, history);
			String sourceId = assets.findByRelativePath("assets/copperbench/textures/block/lamp.png").orElseThrow().id();

			AssetMovePlan conflict = service.preview(sourceId, "assets/copperbench/textures/block/existing.png");
			assertFalse(conflict.canApply());
			assertTrue(conflict.issueCodes().contains("ASSET_MOVE_TARGET_EXISTS"));

			AssetMovePlan category = service.preview(sourceId, "assets/copperbench/models/block/lamp.png");
			assertFalse(category.canApply());
			assertTrue(category.issueCodes().contains("ASSET_MOVE_CATEGORY_MISMATCH"));

			AssetMovePlan root = service.preview(sourceId,
					"src/main/resources/assets/copperbench/textures/block/lamp.png");
			assertFalse(root.canApply());
			assertTrue(root.issueCodes().contains("ASSET_MOVE_RESOURCE_ROOT_MISMATCH"));
		}
	}
}
