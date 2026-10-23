package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceAssetRevisionFailureTest {
	@TempDir Path temp;

	@ParameterizedTest @ValueSource(strings = {"single", "batch", "move"})
	void metadataFailureRollsBackFilesAndRevisionTogether(String kind) throws Exception {
		Path root = Files.createDirectory(temp.resolve("workspace"));
		Path source = Files.write(temp.resolve("source.png"), new byte[]{1, 2, 3});
		Files.writeString(root.resolve("revision.txt"), "0");
		Files.writeString(root.resolve("keep.txt"), "unrelated");
		UUID id = UUID.randomUUID();
		var store = new RevisionedWorkspaceStore();
		store.register(new WorkspaceState(id, "Asset revision", "mod", 0, false, new JsonObject(), new JsonObject(), List.of()));
		WorkspaceMutationGateway gateway = new WorkspaceMutationGateway() {
			public void persist(WorkspaceState before, WorkspaceState after, Operation operation, WorkspaceState.Element affected) {}
			public void persistRestoredRevision(WorkspaceState state, long revision) throws Exception {
				Files.writeString(root.resolve("revision.txt"), Long.toString(revision));
				throw new IOException("Injected metadata failure after write");
			}
		};
		var context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		try (var history = JGitLocalHistoryService.open(root, Clock.systemUTC())) {
			var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
					gateway, history, null, ignored -> root, Clock.systemUTC(), UUID::randomUUID);
			String destination = "assets/probe/textures/block/imported.png";
			String original = "assets/probe/textures/block/original.png";
			JsonObject previewPayload = new JsonObject(); previewPayload.addProperty("targetRelativePath", destination);
			Operation previewOperation = Operation.PREVIEW_ASSET_IMPORT, applyOperation = Operation.IMPORT_ASSET;
			if (kind.equals("move")) {
				Files.createDirectories(root.resolve(original).getParent()); Files.copy(source, root.resolve(original));
				previewPayload.addProperty("sourceAssetId", new AssetWorkspaceService(root).list().getFirst().id());
				previewOperation = Operation.PREVIEW_ASSET_MOVE; applyOperation = Operation.MOVE_ASSET;
			} else {
				previewPayload.addProperty("sourceGrantId", service.grantAssetImportSource(source).id());
				if (kind.equals("batch")) {
					JsonArray items = new JsonArray(); items.add(previewPayload); previewPayload = new JsonObject(); previewPayload.add("items", items);
					previewOperation = Operation.PREVIEW_ASSET_IMPORT_BATCH; applyOperation = Operation.IMPORT_ASSET_BATCH;
				}
			}
			var preview = service.query(Query.of(UUID.randomUUID(), id, previewOperation, previewPayload), context);
			assertEquals("succeeded", preview.status(), preview.diagnostics().toString());
			JsonObject apply = new JsonObject(); apply.add("planToken", preview.data().getAsJsonObject().get("planToken"));
			var outcome = service.execute(Command.of(UUID.randomUUID(), id, 0, applyOperation, apply), context);
			assertEquals("rejected", outcome.result().status());
			assertEquals("WORKSPACE_PERSISTENCE_FAILED", outcome.result().diagnostics().getFirst().code());
			assertEquals(0, store.read(id).orElseThrow().revision());
			assertEquals("0", Files.readString(root.resolve("revision.txt")));
			assertEquals("unrelated", Files.readString(root.resolve("keep.txt")));
			assertFalse(Files.exists(root.resolve(destination)));
			if (kind.equals("move")) assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve(original)));
			assertTrue(outcome.events().isEmpty());
		}
	}
}
