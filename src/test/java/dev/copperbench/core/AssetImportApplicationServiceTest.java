package dev.copperbench.core;

import com.google.gson.JsonObject;
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

class AssetImportApplicationServiceTest {
	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
	@TempDir Path temp;

	@Test void nativeGrantPreviewAndImportAdvanceRevisionWithoutExposingTheExternalPath() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path external = Files.write(temp.resolve("selected-lamp.png"), new byte[] { 4, 2, 4, 2 });

		try (JGitLocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = service(workspace, history);
			var grant = service.grantAssetImportSource(external);
			assertEquals("selected-lamp.png", grant.fileName());
			assertFalse(grant.toString().contains(temp.toString()));

			JsonObject previewPayload = new JsonObject();
			previewPayload.addProperty("sourceGrantId", grant.id());
			previewPayload.addProperty("targetRelativePath", "assets/copperbench/textures/block/lamp.png");
			var preview = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.PREVIEW_ASSET_IMPORT, previewPayload), UI);
			assertEquals("succeeded", preview.status());
			JsonObject plan = preview.data().getAsJsonObject();
			assertEquals("CREATE", plan.get("conflict").getAsString());
			assertTrue(plan.get("canApply").getAsBoolean());
			assertFalse(plan.toString().contains(temp.toString()));
			assertNotNull(plan.get("planToken").getAsString());

			JsonObject commandPayload = new JsonObject();
			commandPayload.addProperty("clientMutationId", uuid(2).toString());
			commandPayload.addProperty("planToken", plan.get("planToken").getAsString());
			var outcome = service.execute(Command.of(uuid(3), WORKSPACE_ID, 0, Operation.IMPORT_ASSET, commandPayload), UI);
			assertEquals("committed", outcome.result().status());
			assertEquals(1, outcome.result().newRevision());
			assertNotNull(outcome.result().recoveryPointId());
			assertTrue(Files.isRegularFile(workspace.resolve("assets/copperbench/textures/block/lamp.png")));
			assertEquals("asset_imported", outcome.events().getFirst().event());

			var reused = service.execute(Command.of(uuid(4), WORKSPACE_ID, 1, Operation.IMPORT_ASSET, commandPayload), UI);
			assertEquals("rejected", reused.result().status());
			assertEquals("ASSET_IMPORT_PLAN_INVALID", reused.result().diagnostics().getFirst().code());
		}
	}

	@Test void previewTokenCannotBeReplayedAgainstAnotherWorkspace() throws Exception {
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(workspace);
		Path external = Files.write(temp.resolve("selected-lamp.png"), new byte[] { 6, 6, 6 });

		try (JGitLocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = service(workspace, history);
			var grant = service.grantAssetImportSource(external);
			JsonObject previewPayload = new JsonObject();
			previewPayload.addProperty("sourceGrantId", grant.id());
			previewPayload.addProperty("targetRelativePath", "assets/copperbench/textures/block/lamp.png");
			JsonObject plan = service.query(Query.of(uuid(20), WORKSPACE_ID, Operation.PREVIEW_ASSET_IMPORT,
					previewPayload), UI).data().getAsJsonObject();

			JsonObject commandPayload = new JsonObject();
			commandPayload.addProperty("clientMutationId", uuid(21).toString());
			commandPayload.addProperty("planToken", plan.get("planToken").getAsString());
			var rejected = service.execute(Command.of(uuid(22), OTHER_WORKSPACE_ID, 0, Operation.IMPORT_ASSET,
					commandPayload), UI);

			assertEquals("rejected", rejected.result().status());
			assertEquals("ASSET_IMPORT_PLAN_WORKSPACE_MISMATCH", rejected.result().diagnostics().getFirst().code());
			assertFalse(Files.exists(workspace.resolve("assets/copperbench/textures/block/lamp.png")));
		}
	}

	@Test void replacementRequiresPostPreviewConfirmationAndLeavesTheTargetUntouchedWhenRejected() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path target = workspace.resolve("assets/copperbench/textures/block/lamp.png");
		Files.createDirectories(target.getParent());
		Files.write(target, new byte[] { 1, 1, 1 });
		Path external = Files.write(temp.resolve("replacement.png"), new byte[] { 9, 9, 9 });

		try (JGitLocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			WorkspaceApplicationService service = service(workspace, history);
			var grant = service.grantAssetImportSource(external);
			JsonObject previewPayload = new JsonObject();
			previewPayload.addProperty("sourceGrantId", grant.id());
			previewPayload.addProperty("targetRelativePath", "assets/copperbench/textures/block/lamp.png");
			JsonObject plan = service.query(Query.of(uuid(10), WORKSPACE_ID, Operation.PREVIEW_ASSET_IMPORT,
					previewPayload), UI).data().getAsJsonObject();
			assertEquals("REPLACE", plan.get("conflict").getAsString());
			assertTrue(plan.get("requiresReplacementConfirmation").getAsBoolean());

			JsonObject commandPayload = new JsonObject();
			commandPayload.addProperty("clientMutationId", uuid(11).toString());
			commandPayload.addProperty("planToken", plan.get("planToken").getAsString());
			var rejected = service.execute(Command.of(uuid(12), WORKSPACE_ID, 0, Operation.IMPORT_ASSET,
					commandPayload), UI);
			assertEquals("rejected", rejected.result().status());
			assertEquals("ASSET_IMPORT_REPLACE_CONFIRMATION_REQUIRED",
					rejected.result().diagnostics().getFirst().code());
			assertEquals(List.of((byte) 1, (byte) 1, (byte) 1),
					toList(Files.readAllBytes(target)));

			commandPayload.addProperty("confirmReplace", true);
			var committed = service.execute(Command.of(uuid(13), WORKSPACE_ID, 0, Operation.IMPORT_ASSET,
					commandPayload), UI);
			assertEquals("committed", committed.result().status());
			assertEquals(List.of((byte) 9, (byte) 9, (byte) 9), toList(Files.readAllBytes(target)));
		}
	}

	private static List<Byte> toList(byte[] bytes) {
		java.util.ArrayList<Byte> result = new java.util.ArrayList<>();
		for (byte value : bytes) result.add(value);
		return result;
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
		AtomicLong ids = new AtomicLong(500);
		return new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())),
				WorkspaceMutationGateway.noOp(), history, null, ignored -> workspace, CLOCK,
				() -> uuid(ids.incrementAndGet()));
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
