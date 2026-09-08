package dev.copperbench.core;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetMoveApplicationServiceTest {
	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T07:00:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
	@TempDir Path temp;

	@Test void previewAndMoveRewriteReferencesAdvanceOneRevisionAndReturnRecovery() throws Exception {
		Path workspace = fixture();
		try (var history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = service(workspace, history);
			String sourceId = new AssetWorkspaceService(workspace).findByRelativePath(
					"assets/copperbench/textures/block/lamp.png").orElseThrow().id();
			JsonObject previewPayload = new JsonObject();
			previewPayload.addProperty("sourceAssetId", sourceId);
			previewPayload.addProperty("targetRelativePath", "assets/copperbench/textures/block/renamed.png");
			var preview = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.PREVIEW_ASSET_MOVE, previewPayload), UI);
			assertEquals("succeeded", preview.status());
			JsonObject plan = preview.data().getAsJsonObject();
			assertTrue(plan.get("canApply").getAsBoolean());
			assertEquals(1, plan.get("referenceCount").getAsInt());
			assertEquals("/textures/all", plan.getAsJsonArray("rewrites").get(0).getAsJsonObject()
					.get("sourcePointer").getAsString());
			assertNotNull(plan.get("planToken").getAsString());

			JsonObject commandPayload = new JsonObject();
			commandPayload.addProperty("clientMutationId", uuid(2).toString());
			commandPayload.addProperty("planToken", plan.get("planToken").getAsString());
			var outcome = service.execute(Command.of(uuid(3), WORKSPACE_ID, 0, Operation.MOVE_ASSET, commandPayload), UI);
			assertEquals("committed", outcome.result().status());
			assertEquals(1, outcome.result().newRevision());
			assertNotNull(outcome.result().recoveryPointId());
			assertEquals("asset_moved", outcome.events().getFirst().event());
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/lamp.png")));
			assertTrue(Files.isRegularFile(workspace.resolve("assets/copperbench/textures/block/renamed.png")));
			assertTrue(Files.readString(workspace.resolve("assets/copperbench/models/block/lamp.json"))
					.contains("copperbench:block/renamed"));

			var reused = service.execute(Command.of(uuid(4), WORKSPACE_ID, 1, Operation.MOVE_ASSET, commandPayload), UI);
			assertEquals("rejected", reused.result().status());
			assertEquals("ASSET_MOVE_PLAN_INVALID", reused.result().diagnostics().getFirst().code());
		}
	}

	@Test void movePlanTokenCannotCrossWorkspaceBoundary() throws Exception {
		Path workspace = fixture();
		try (var history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = service(workspace, history);
			String sourceId = new AssetWorkspaceService(workspace).findByRelativePath(
					"assets/copperbench/textures/block/lamp.png").orElseThrow().id();
			JsonObject previewPayload = new JsonObject();
			previewPayload.addProperty("sourceAssetId", sourceId);
			previewPayload.addProperty("targetRelativePath", "assets/copperbench/textures/block/renamed.png");
			JsonObject plan = service.query(Query.of(uuid(10), WORKSPACE_ID, Operation.PREVIEW_ASSET_MOVE,
					previewPayload), UI).data().getAsJsonObject();

			JsonObject commandPayload = new JsonObject();
			commandPayload.addProperty("clientMutationId", uuid(11).toString());
			commandPayload.addProperty("planToken", plan.get("planToken").getAsString());
			var rejected = service.execute(Command.of(uuid(12), OTHER_WORKSPACE_ID, 0, Operation.MOVE_ASSET,
					commandPayload), UI);
			assertEquals("rejected", rejected.result().status());
			assertEquals("ASSET_MOVE_PLAN_WORKSPACE_MISMATCH", rejected.result().diagnostics().getFirst().code());
			assertTrue(Files.isRegularFile(workspace.resolve("assets/copperbench/textures/block/lamp.png")));
		}
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
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copperbench", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		store.register(new WorkspaceState(OTHER_WORKSPACE_ID, "Copperbench Other", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		AtomicLong ids = new AtomicLong(700);
		return new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())),
				WorkspaceMutationGateway.noOp(), history, null, ignored -> workspace, CLOCK,
				() -> uuid(ids.incrementAndGet()));
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
