package dev.copperbench.assets;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetMutationHistoryBoundaryTest {
	@TempDir Path temp;

	@Test void importAndReplaceCreateRestorableHistoryPoints() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path source = Files.writeString(temp.resolve("source.bbmodel"), "one");
		var assets = new AssetWorkspaceService(workspace);
		try (var history = JGitLocalHistoryService.open(workspace, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))) {
			var boundary = new AssetMutationHistoryBoundary(assets, history);
			var first = boundary.importOrReplace(source, "assets/copperbench/models/lamp.bbmodel", Actor.UI, "task-1");
			Files.writeString(source, "two");
			var second = boundary.importOrReplace(source, "assets/copperbench/models/lamp.bbmodel", Actor.UI, "task-2");
			assertEquals("two", Files.readString(workspace.resolve("assets/copperbench/models/lamp.bbmodel")));
			boundary.restore(first.id());
			assertEquals("one", Files.readString(workspace.resolve("assets/copperbench/models/lamp.bbmodel")));
			assertEquals(2, history.listRecoveryPoints().size());
		}
	}

	@Test void rejectsWorkspaceEscape() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		try (var history = JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
			var boundary = new AssetMutationHistoryBoundary(new AssetWorkspaceService(workspace), history);
			assertThrows(AssetPathViolationException.class, () -> boundary.importOrReplace(temp.resolve("source"), "../escape", Actor.UI, "task"));
		}
	}
}
