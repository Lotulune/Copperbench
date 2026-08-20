package dev.copperbench.core;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage67ApplicationServiceTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);

	@TempDir Path temp;

	@Test void versionTrackQueryAndMigrationPreviewUseTheSharedCatalog() {
		WorkspaceApplicationService service = service(null);
		var tracks = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.GET_VERSION_TRACKS, new JsonObject()),
				uiWorkspace());
		assertEquals("succeeded", tracks.status());
		assertEquals(4, tracks.data().getAsJsonObject().getAsJsonArray("tracks").size());
		JsonObject payload = new JsonObject();
		payload.addProperty("targetGeneratorId", "neoforge-1.21.1");
		var preview = service.query(Query.of(uuid(2), WORKSPACE_ID, Operation.PREVIEW_LOADER_MIGRATION, payload),
				uiWorkspace());
		assertEquals("succeeded", preview.status());
		assertTrue(preview.data().getAsJsonObject().get("complete").getAsBoolean());
	}

	@Test void loaderMigrationRequiresApprovalAndDoesNotMutateTheSourceWorkspace() throws Exception {
		Path source = temp.resolve("source");
		Files.createDirectories(source);
		Files.writeString(source.resolve("workspace.mcreator"),
				"{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}");
		WorkspaceApplicationService service = service(id -> source);
		JsonObject denied = new JsonObject();
		denied.addProperty("clientMutationId", uuid(3).toString());
		denied.addProperty("targetGeneratorId", "neoforge-1.21.1");
		denied.addProperty("outputName", "copied-neoforge");
		denied.addProperty("userApproved", false);
		var rejected = service.execute(Command.of(uuid(4), WORKSPACE_ID, 0, Operation.EXECUTE_LOADER_MIGRATION, denied),
				uiWorkspace());
		assertEquals("rejected", rejected.result().status());
		assertEquals("USER_APPROVAL_REQUIRED", rejected.result().diagnostics().getFirst().code());

		JsonObject approved = denied.deepCopy();
		approved.addProperty("userApproved", true);
		var committed = service.execute(Command.of(uuid(5), WORKSPACE_ID, 0, Operation.EXECUTE_LOADER_MIGRATION,
				approved), uiWorkspace());
		assertEquals("committed", committed.result().status());
		assertEquals(0, service.query(Query.of(uuid(6), WORKSPACE_ID, Operation.GET_WORKBENCH, new JsonObject()),
				uiWorkspace()).revision());
		assertTrue(Files.isRegularFile(temp.resolve("copied-neoforge/workspace.mcreator")));
		assertTrue(Files.readString(source.resolve("workspace.mcreator")).contains("fabric-1.21.1"));
		assertEquals("generated", committed.result().data().getAsJsonObject().getAsJsonObject("rebuild")
				.get("status").getAsString());
		assertTrue(Files.isRegularFile(temp.resolve("copied-neoforge/src/main/java/dev/copperbench/generated/copper_trails/CopperTrailsMod.java")));
	}

	@Test void upstreamImportRequiresFullAccess() {
		WorkspaceApplicationService service = service(id -> temp.resolve("source"));
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(7).toString());
		payload.addProperty("sourceWorkspacePath", temp.toString());
		payload.addProperty("outputName", "imported");
		payload.addProperty("userApproved", true);
		var denied = service.execute(Command.of(uuid(8), WORKSPACE_ID, 0, Operation.IMPORT_UPSTREAM_WORKSPACE, payload),
				uiWorkspace());
		assertEquals("rejected", denied.result().status());
		assertEquals("PERMISSION_DENIED", denied.result().diagnostics().getFirst().code());
	}

	@Test void publishBatchAndResourcePackPrepareShareTheWorkspaceRoot() throws Exception {
		Path source = temp.resolve("source");
		Files.createDirectories(source.resolve("resource-pack/assets"));
		Files.writeString(source.resolve("resource-pack/pack.mcmeta"),
				"{\"pack\":{\"pack_format\":34,\"description\":\"Copper\"}}");
		Files.write(source.resolve("resource-pack/assets/a.png"), new byte[] { 9 });
		WorkspaceApplicationService service = service(id -> source);
		JsonObject batch = new JsonObject();
		batch.addProperty("clientMutationId", uuid(9).toString());
		batch.addProperty("name", "copper-pack");
		batch.addProperty("sourceDirectory", "resource-pack");
		batch.addProperty("output", "exports/pack.zip");
		var created = service.execute(Command.of(uuid(10), WORKSPACE_ID, 0, Operation.CREATE_PUBLISH_BATCH, batch),
				uiWorkspace());
		assertEquals("committed", created.result().status());
		JsonObject prepare = new JsonObject();
		prepare.addProperty("clientMutationId", uuid(11).toString());
		prepare.addProperty("sourceDirectory", "resource-pack");
		prepare.addProperty("zipFileName", "copper.zip");
		var ready = service.execute(Command.of(uuid(12), WORKSPACE_ID, 0, Operation.PREPARE_RESOURCE_PACK_CLIENT,
				prepare), uiWorkspace());
		assertEquals("committed", ready.result().status());
		assertFalse(ready.result().data().getAsJsonObject().get("clientLaunched").getAsBoolean());
		assertTrue(Files.isRegularFile(source.resolve("run/resourcepacks/copper.zip")));
	}

	private WorkspaceApplicationService service(java.util.function.Function<UUID, Path> roots) {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator, new JsonObject(),
				List.of()));
		AtomicLong sequence = new AtomicLong(200);
		return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())),
				WorkspaceMutationGateway.noOp(), null, null, roots, CLOCK, () -> uuid(sequence.getAndIncrement()));
	}

	private static RequestContext uiWorkspace() {
		return new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
