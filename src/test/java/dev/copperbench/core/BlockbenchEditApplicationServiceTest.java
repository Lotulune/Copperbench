package dev.copperbench.core;

import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.assets.BlockbenchBridgeException;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Event;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RestoreResult;
import dev.copperbench.history.WorkspaceChange;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

	@Test void slowRecoveryKeepsWorkspaceReadableAndRetriesIfTheRevisionChanges() throws Exception {
		Path model = temp.resolve("models/lamp.bbmodel");
		Files.createDirectories(model.getParent());
		Files.writeString(model, "{}");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(new WorkspaceState(WORKSPACE_ID, "Slow recovery", "mod", 0, false,
				new JsonObject(), new JsonObject(), List.of()));
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		AtomicInteger attempts = new AtomicInteger();
		try (var history = new ControlledHistory(JGitLocalHistoryService.open(temp, CLOCK), () -> {
			if (attempts.incrementAndGet() == 1) {
				entered.countDown();
				try {
					assertTrue(release.await(10, TimeUnit.SECONDS));
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			}
		}); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var service = service(store, history);
			var descriptor = new AssetWorkspaceService(temp).list().getFirst();
			var prepared = executor.submit(() -> service.blockbenchEditLifecycle(WORKSPACE_ID).prepare(descriptor));
			try {
				assertTrue(entered.await(2, TimeUnit.SECONDS));
				assertEquals(0, executor.submit(() -> store.read(WORKSPACE_ID).orElseThrow().revision())
						.get(2, TimeUnit.SECONDS));
				assertEquals(1, executor.submit(() -> store.transact(WORKSPACE_ID, 0, candidate ->
						RevisionedWorkspaceStore.Decision.commit(null, List.of("/metadata"))).revision())
						.get(2, TimeUnit.SECONDS));
			} finally {
				release.countDown();
			}
			assertEquals(1, prepared.get(5, TimeUnit.SECONDS).openedRevision());
			assertEquals(2, attempts.get(), "A backup taken across a revision change cannot authorize launch");
		}
	}

	@Test void rejectsAModelChangedWhileRecoveryWasBeingCreated() throws Exception {
		Path model = temp.resolve("models/lamp.bbmodel");
		Files.createDirectories(model.getParent());
		Files.writeString(model, "{}");
		var descriptor = new AssetWorkspaceService(temp).list().getFirst();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(new WorkspaceState(WORKSPACE_ID, "Changed model", "mod", 0, false,
				new JsonObject(), new JsonObject(), List.of()));
		try (var history = new ControlledHistory(JGitLocalHistoryService.open(temp, CLOCK), () -> {
			try { Files.writeString(model, "{\"changed\":true}"); }
			catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
		})) {
			var lifecycle = service(store, history).blockbenchEditLifecycle(WORKSPACE_ID);
			assertEquals("BLOCKBENCH_ASSET_STALE", assertThrows(BlockbenchBridgeException.class,
					() -> lifecycle.prepare(descriptor)).code());
			assertEquals(0, store.read(WORKSPACE_ID).orElseThrow().revision());
		}
	}

	private WorkspaceApplicationService service(RevisionedWorkspaceStore store, LocalHistoryService history) {
		return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(CLOCK, UUID::randomUUID),
				WorkspaceMutationGateway.noOp(), history, null, ignored -> temp, CLOCK, UUID::randomUUID);
	}

	private record ControlledHistory(LocalHistoryService delegate, Runnable beforeCreate) implements LocalHistoryService {
		@Override public RecoveryPoint createRecoveryPoint(RecoveryPointRequest request) throws LocalHistoryException {
			beforeCreate.run();
			return delegate.createRecoveryPoint(request);
		}
		@Override public List<RecoveryPoint> listRecoveryPoints() throws LocalHistoryException { return delegate.listRecoveryPoints(); }
		@Override public String currentRecoveryPointId() throws LocalHistoryException { return delegate.currentRecoveryPointId(); }
		@Override public List<WorkspaceChange> compare(String from, String to) throws LocalHistoryException { return delegate.compare(from, to); }
		@Override public List<WorkspaceChange> previewRestore(String id) throws LocalHistoryException { return delegate.previewRestore(id); }
		@Override public RestoreResult restore(String id) throws LocalHistoryException { return delegate.restore(id); }
		@Override public void close() { delegate.close(); }
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
