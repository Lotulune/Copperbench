package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSourceApplicationTest {
	@TempDir Path root;
	private static final UUID ID = UUID.randomUUID();
	private static final String FILE = "src/main/java/example/Entry.java";
	private static final RequestContext CONTEXT = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

	@Test void successfulEditUsesHistoryRevisionReloadAndEventAndNoOpDoesNotAdvance() throws Exception {
		try (Fixture fixture = fixture(false)) {
			JsonObject original = read(fixture.service);
			CommandOutcome saved = save(fixture.service, original.get("sha256").getAsString(), "class Entry { int value; }", 0);
			assertEquals("committed", saved.result().status(), saved.result().diagnostics().toString());
			assertEquals(1, saved.result().newRevision());
			assertEquals(1, fixture.durableRevision.get());
			assertNotNull(saved.result().recoveryPointId());
			assertEquals(1, fixture.history.listRecoveryPoints().size());
			assertEquals("workspace_revision_advanced", saved.events().getFirst().event());
			assertEquals("/" + FILE, saved.events().getFirst().payload().getAsJsonArray("changedPaths").get(0).getAsString());
			assertEquals("class Entry { int value; }", fixture.store.read(ID).orElseThrow().upstreamDocument().get("loadedSource").getAsString());
			assertEquals("class Entry { int value; }", Files.readString(root.resolve(FILE)));
			assertEquals(0, fixture.store.read(ID).orElseThrow().elements().size(), "native Java remains source, not a Mod Element");
			JsonObject after = read(fixture.service);
			CommandOutcome unchanged = save(fixture.service, after.get("sha256").getAsString(), after.get("content").getAsString(), 1);
			assertEquals("completed", unchanged.result().status());
			assertEquals(1, unchanged.result().newRevision());
			assertFalse(unchanged.result().data().getAsJsonObject().get("changed").getAsBoolean());
			assertTrue(unchanged.events().isEmpty());
			assertEquals(1, fixture.history.listRecoveryPoints().size());
			fixture.history.restore(saved.result().recoveryPointId());
			assertEquals("class Entry {}", Files.readString(root.resolve(FILE)));
		}
	}

	@Test void externalHashAndRevisionConflictsPreserveDiskAndDoNotCreateHistory() throws Exception {
		try (Fixture fixture = fixture(false)) {
			JsonObject original = read(fixture.service);
			Files.writeString(root.resolve(FILE), "external");
			CommandOutcome external = save(fixture.service, original.get("sha256").getAsString(), "draft", 0);
			assertEquals("WORKSPACE_SOURCE_CONFLICT", external.result().diagnostics().getFirst().code());
			assertEquals("external", Files.readString(root.resolve(FILE)));
			assertTrue(fixture.history.listRecoveryPoints().isEmpty());
			JsonObject current = read(fixture.service);
			CommandOutcome stale = save(fixture.service, current.get("sha256").getAsString(), "draft", 4);
			assertEquals("rejected", stale.result().status());
			assertEquals("WORKSPACE_REVISION_CONFLICT", stale.result().diagnostics().getFirst().code());
			assertFalse(stale.result().conflict().isJsonNull());
			assertEquals("external", Files.readString(root.resolve(FILE)));
			assertTrue(fixture.history.listRecoveryPoints().isEmpty());
			JsonObject payload = new JsonObject(); payload.addProperty("relativePath", FILE);
			assertFalse(fixture.service.query(Query.of(UUID.randomUUID(), ID, Operation.READ_WORKSPACE_FILE, payload),
					new RequestContext(Actor.UI, PermissionProfile.READ_ONLY)).data().getAsJsonObject().get("editable").getAsBoolean());
		}
	}

	@Test void revisionPersistenceFailureRollsBackFileWithoutPublishingRevision() throws Exception {
		try (Fixture fixture = fixture(true)) {
			JsonObject original = read(fixture.service);
			CommandOutcome saved = save(fixture.service, original.get("sha256").getAsString(), "new content", 0);
			assertEquals("rejected", saved.result().status());
			assertEquals("WORKSPACE_SOURCE_IO", saved.result().diagnostics().getFirst().code());
			assertEquals(0, fixture.store.read(ID).orElseThrow().revision());
			assertEquals(0, fixture.durableRevision.get());
			assertEquals("class Entry {}", Files.readString(root.resolve(FILE)));
			assertTrue(saved.events().isEmpty());
			assertEquals(1, fixture.history.listRecoveryPoints().size());
		}
	}

	@Test void assetSourceCapabilityMatchesListingReadingAndGuardedSaving() throws Exception {
		try (Fixture fixture = fixture(false)) {
			for (String path : List.of("models/custom/lamp.json", "assets/example/lang/legacy.lang", "assets/example/private.mcreator")) {
				Files.createDirectories(root.resolve(path).getParent());
				Files.writeString(root.resolve(path), path.endsWith(".lang") ? "item.example=Example" : "{}");
			}
			var assets = fixture.service.query(Query.of(UUID.randomUUID(), ID, Operation.LIST_ASSETS, new JsonObject()), CONTEXT);
			assertEquals("succeeded", assets.status());
			for (var raw : assets.data().getAsJsonObject().getAsJsonArray("assets")) {
				var asset = raw.getAsJsonObject();
				String path = asset.get("relativePath").getAsString();
				boolean supported = !path.endsWith(".mcreator");
				assertEquals(supported, asset.get("sourceAvailable").getAsBoolean(), path);
				var query = new JsonObject(); query.addProperty("relativePath", path);
				var read = fixture.service.query(Query.of(UUID.randomUUID(), ID, Operation.READ_WORKSPACE_FILE, query), CONTEXT);
				assertEquals(supported ? "succeeded" : "rejected", read.status(), path);
			}
			var source = new WorkspaceSourceService(root, java.util.Map.of(), true);
			assertEquals(1, source.list("models/custom/lamp.json", 0, 200).getAsJsonArray("files").size());
			for (String path : List.of("models/custom/lamp.json", "assets/example/lang/legacy.lang")) {
				var before = source.read(path);
				var edit = source.prepare(path, before.get("content").getAsString() + "\n", before.get("sha256").getAsString());
				source.apply(edit, () -> {});
				assertTrue(Files.readString(root.resolve(path)).endsWith("\n"));
			}
			assertFalse(WorkspaceSourceService.supports("models/../private.json", 2));
			assertFalse(WorkspaceSourceService.supports("models/big.json", WorkspaceSourceService.MAX_FILE_BYTES + 1L));
		}
	}

	private Fixture fixture(boolean failPersist) throws Exception {
		Files.createDirectories(root.resolve(FILE).getParent()); Files.writeString(root.resolve(FILE), "class Entry {}");
		Files.writeString(root.resolve("workspace.mcreator"), "{}");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		WorkspaceState state = new WorkspaceState(ID, "Source project", "mod", 0, false, new JsonObject(), new JsonObject(), List.of());
		store.register(state);
		var history = JGitLocalHistoryService.open(root, Clock.systemUTC());
		AtomicLong durableRevision = new AtomicLong();
		WorkspaceMutationGateway gateway = new WorkspaceMutationGateway() {
			@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation, WorkspaceState.Element affected) {}
			@Override public void persistRestoredRevision(WorkspaceState restored, long revision) throws Exception {
				durableRevision.set(revision);
				if (failPersist && revision > 0) throw new IOException("metadata unavailable");
			}
		};
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), gateway,
				history, id -> { JsonObject data = new JsonObject(); data.addProperty("loadedSource", Files.readString(root.resolve(FILE)));
					return new WorkspaceState(ID, "Source project", "mod", 0, false, new JsonObject(), data, List.of()); },
				id -> root, Clock.systemUTC(), UUID::randomUUID);
		return new Fixture(service, store, history, durableRevision);
	}
	private JsonObject read(WorkspaceApplicationService service) {
		JsonObject payload = new JsonObject(); payload.addProperty("relativePath", FILE);
		var result = service.query(Query.of(UUID.randomUUID(), ID, Operation.READ_WORKSPACE_FILE, payload), CONTEXT);
		assertEquals("succeeded", result.status(), result.diagnostics().toString()); return result.data().getAsJsonObject();
	}
	private CommandOutcome save(WorkspaceApplicationService service, String sha, String text, long revision) {
		JsonObject payload = new JsonObject(); payload.addProperty("relativePath", FILE); payload.addProperty("content", text);
		payload.addProperty("expectedSha256", sha); payload.addProperty("clientMutationId", UUID.randomUUID().toString());
		return service.execute(Command.of(UUID.randomUUID(), ID, revision, Operation.UPDATE_WORKSPACE_FILE, payload), CONTEXT);
	}
	private record Fixture(WorkspaceApplicationService service, RevisionedWorkspaceStore store, JGitLocalHistoryService history,
			AtomicLong durableRevision) implements AutoCloseable {
		@Override public void close() { history.close(); }
	}
}
