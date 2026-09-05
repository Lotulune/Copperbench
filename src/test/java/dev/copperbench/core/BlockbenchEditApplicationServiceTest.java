package dev.copperbench.core;

import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Event;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockbenchEditApplicationServiceTest {
	private static final UUID WORKSPACE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T06:00:00Z"), ZoneOffset.UTC);
	@TempDir Path temp;

	@Test void blockbenchEditCreatesRecoveryBeforeLaunchAndRegistersExternalSaveAsOneRevision() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path modelFile = workspace.resolve("assets/copperbench/models/lamp.bbmodel");
		Files.createDirectories(modelFile.getParent());
		Files.writeString(modelFile, "{}");

		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copperbench", "mod", 0, false, generator,
				new JsonObject(), List.of()));

		try (JGitLocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			AtomicLong ids = new AtomicLong(700);
			WorkspaceApplicationService service = new WorkspaceApplicationService(store,
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())),
					WorkspaceMutationGateway.noOp(), history, null, ignored -> workspace, CLOCK,
					() -> uuid(ids.incrementAndGet()));
			List<Event> events = new ArrayList<>();
			try (AutoCloseable ignored = service.subscribeEvents(WORKSPACE_ID, 0, events::add)) {
				var descriptor = new AssetWorkspaceService(workspace).list().getFirst();
				var lifecycle = service.blockbenchEditLifecycle(WORKSPACE_ID);
				var prepared = lifecycle.prepare(descriptor);

				assertEquals(0, prepared.openedRevision());
				assertEquals(1, history.listRecoveryPoints().size());
				assertEquals(prepared.recoveryPointId(), history.listRecoveryPoints().getFirst().id());
				assertEquals("recovery_point_created", events.getFirst().event());

				Files.writeString(modelFile, "{\"edited\":true}");
				var current = new AssetWorkspaceService(workspace).findByRelativePath(
						"assets/copperbench/models/lamp.bbmodel").orElseThrow();
				assertNotEquals(prepared.openedSha256(), current.sha256());
				var completion = lifecycle.complete(prepared, current);

				assertEquals(1, completion.workspaceRevision());
				assertEquals(1, store.read(WORKSPACE_ID).orElseThrow().revision());
				Event committed = events.getLast();
				assertEquals("asset_external_edit_committed", committed.event());
				assertEquals(1, committed.revision());
				assertEquals(prepared.recoveryPointId(), committed.payload().get("recoveryPointId").getAsString());
				assertEquals(current.sha256(), committed.payload().get("currentSha256").getAsString());
				assertTrue(committed.sequence() > events.getFirst().sequence());

				history.restore(prepared.recoveryPointId());
				assertEquals("{}", Files.readString(modelFile));
			}
		}
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
