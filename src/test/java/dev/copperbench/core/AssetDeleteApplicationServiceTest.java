package dev.copperbench.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AssetDeleteApplicationServiceTest {
	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T07:00:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
	@TempDir Path temp;

	@Test void deletingAReferencedTextureNeedsConfirmationAndIsRestorable() throws Exception {
		Path workspace = fixture();
		try (var history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = service(workspace, history);
			String textureId = new AssetWorkspaceService(workspace).findByRelativePath(
					"assets/copperbench/textures/block/lamp.png").orElseThrow().id();
			JsonObject plan = preview(service, textureId);
			assertTrue(plan.get("canApply").getAsBoolean());
			assertTrue(plan.get("requiresReferenceConfirmation").getAsBoolean());
			JsonObject affected = plan.getAsJsonArray("affectedReferences").get(0).getAsJsonObject();
			assertEquals("assets/copperbench/models/block/lamp.json", affected.get("sourcePath").getAsString());
			assertEquals("/textures/all", affected.get("sourcePointer").getAsString());

			JsonObject payload = new JsonObject();
			payload.addProperty("clientMutationId", uuid(2).toString());
			payload.addProperty("planToken", plan.get("planToken").getAsString());
			var unconfirmed = service.execute(Command.of(uuid(3), WORKSPACE_ID, 0, Operation.DELETE_ASSET, payload), UI);
			assertEquals("rejected", unconfirmed.result().status());
			assertEquals("ASSET_DELETE_CONFIRMATION_REQUIRED", unconfirmed.result().diagnostics().getFirst().code());
			assertTrue(Files.isRegularFile(workspace.resolve("assets/copperbench/textures/block/lamp.png")));

			payload.addProperty("confirmAffectedReferences", true);
			var deleted = service.execute(Command.of(uuid(4), WORKSPACE_ID, 0, Operation.DELETE_ASSET, payload), UI);
			assertEquals("committed", deleted.result().status(), deleted.result().diagnostics().toString());
			assertEquals(1, deleted.result().newRevision());
			assertNotNull(deleted.result().recoveryPointId());
			assertEquals("asset_deleted", deleted.events().getFirst().event());
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/lamp.png")));

			history.restore(deleted.result().recoveryPointId());
			assertTrue(Files.isRegularFile(workspace.resolve("assets/copperbench/textures/block/lamp.png")));
		}
	}

	@Test void deletingTheModelAndItsTextureTogetherBreaksNothing() throws Exception {
		Path workspace = fixture();
		try (var history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = service(workspace, history);
			var assets = new AssetWorkspaceService(workspace);
			JsonObject plan = preview(service, assets.findByRelativePath("assets/copperbench/models/block/lamp.json").orElseThrow().id(),
					assets.findByRelativePath("assets/copperbench/textures/block/lamp.png").orElseThrow().id());
			assertEquals(0, plan.getAsJsonArray("affectedReferences").size());
			assertEquals(2, plan.get("deleteCount").getAsInt());
		}
	}

	private static JsonObject preview(WorkspaceApplicationService service, String... assetIds) {
		JsonObject payload = new JsonObject();
		JsonArray ids = new JsonArray();
		for (String id : assetIds) ids.add(id);
		payload.add("assetIds", ids);
		var result = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.PREVIEW_ASSET_DELETE, payload), UI);
		assertEquals("succeeded", result.status(), result.toString());
		return result.data().getAsJsonObject();
	}

	private Path fixture() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path model = workspace.resolve("assets/copperbench/models/block/lamp.json");
		Path texture = workspace.resolve("assets/copperbench/textures/block/lamp.png");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:block/lamp\"}}");
		Files.write(texture, new byte[] { 1, 2, 3 });
		return workspace;
	}

	private static WorkspaceApplicationService service(Path workspace, JGitLocalHistoryService history) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copperbench", "mod", 0, false, generator, new JsonObject(), List.of()));
		AtomicLong ids = new AtomicLong(700);
		return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())),
				WorkspaceMutationGateway.noOp(), history, null, ignored -> workspace, CLOCK, () -> uuid(ids.incrementAndGet()));
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
