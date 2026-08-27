package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211WorkspaceTaskGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract fixture for datagen staging and GameTest task handling across all Java tracks. */
class Stage9ManagedTaskMatrixTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-27T08:30:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

	@Test void datagenAndGameTestRemainIsolatedAcrossEightTracks(@TempDir Path root) throws Exception {
		List<Track> tracks = tracks();
		List<String> datagenPassed = new ArrayList<>();
		List<String> gameTestPassed = new ArrayList<>();
		for (Track track : tracks) {
			UUID workspaceId = UUID.nameUUIDFromBytes((track.generatorId + "-managed-workspace").getBytes());
			RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
			store.register(workspace(workspaceId, track.generatorId));
			AtomicLong ids = new AtomicLong(2000);
			Fabric1211ProcessRunner runner = (workingDirectory, arguments, timeout, output) -> {
				assertTrue(workingDirectory.toString().replace('\\', '/').contains(".copperbench/task-runs/"));
				if (arguments.equals(List.of("runDatagen"))) {
					Path generated = workingDirectory.resolve("src/generated/resources/data/copper_trails/generated.json");
					Files.createDirectories(generated.getParent());
					Files.writeString(generated, "{}\n");
					output.accept("COPPERBENCH_STAGE9_DATAGEN_DONE " + track.generatorId);
				} else {
					assertEquals(List.of("runGameTest"), arguments);
					output.accept("COPPERBENCH_STAGE9_GAMETEST_DONE " + track.generatorId);
				}
				return new Fabric1211ProcessRunner.ProcessResult(0, false);
			};
			Path target = root.resolve(track.generatorId);
			WorkspaceTaskGateway tasks = track.factory.create(store,
					() -> UUID.nameUUIDFromBytes((track.generatorId + "-" + ids.getAndIncrement()).getBytes()), runner, target);
			try {
				WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK,
					() -> UUID.nameUUIDFromBytes((track.generatorId + "-service-" + ids.getAndIncrement()).getBytes()));
				JsonObject datagen = start(service, workspaceId, Operation.RUN_DATAGEN);
				assertEquals("succeeded", datagen.getAsJsonObject("task").get("state").getAsString());
				assertTrue(datagen.getAsJsonArray("logs").toString().contains("COPPERBENCH_STAGE9_DATAGEN_DONE"));
				UUID datagenTask = UUID.fromString(datagen.getAsJsonObject("task").get("id").getAsString());
				JsonObject previewPayload = new JsonObject();
				previewPayload.addProperty("taskId", datagenTask.toString());
				JsonObject preview = service.query(Query.of(UUID.randomUUID(), workspaceId,
						Operation.PREVIEW_DATAGEN_OUTPUT, previewPayload), UI).data().getAsJsonObject();
				assertEquals(1, preview.get("changeCount").getAsInt());
				assertTrue(preview.get("canPublish").getAsBoolean());
				assertFalse(Files.exists(target.resolve("src/generated")));
				datagenPassed.add(track.generatorId);

				JsonObject gameTest = start(service, workspaceId, Operation.RUN_GAMETEST);
				assertEquals("succeeded", gameTest.getAsJsonObject("task").get("state").getAsString());
				assertTrue(gameTest.getAsJsonArray("logs").toString().contains("COPPERBENCH_STAGE9_GAMETEST_DONE"));
				gameTestPassed.add(track.generatorId);
			} finally {
				((AutoCloseable) tasks).close();
			}
		}
		assertEquals(8, datagenPassed.size());
		assertEquals(8, gameTestPassed.size());
	}

	private static JsonObject start(WorkspaceApplicationService service, UUID workspaceId, Operation operation)
			throws Exception {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", UUID.randomUUID().toString());
		payload.addProperty("scope", "workspace");
		var accepted = service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, operation, payload), UI);
		assertEquals("accepted", accepted.result().status());
		UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			JsonObject queryPayload = new JsonObject();
			queryPayload.addProperty("taskId", taskId.toString());
			JsonObject projection = service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_TASK,
					queryPayload), UI).data().getAsJsonObject();
			String state = projection.getAsJsonObject("task").get("state").getAsString();
			if (!state.equals("queued") && !state.equals("running")) return projection;
			Thread.sleep(20);
		}
		throw new AssertionError("Managed task did not finish: " + operation);
	}

	private static List<Track> tracks() {
		return List.of(
				new Track("fabric-26.2", (s, i, r, t) -> new Fabric1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, Fabric1211Generator.Profile.FABRIC_262, r)),
				new Track("neoforge-26.2", (s, i, r, t) -> new NeoForge1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, NeoForge1211Generator.Profile.NEOFORGE_262, r)),
				new Track("fabric-26.1.2", (s, i, r, t) -> new Fabric1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, Fabric1211Generator.Profile.FABRIC_261, r)),
				new Track("neoforge-26.1.2", (s, i, r, t) -> new NeoForge1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, NeoForge1211Generator.Profile.NEOFORGE_261, r)),
				new Track("fabric-1.21.1", (s, i, r, t) -> new Fabric1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, Fabric1211Generator.Profile.FABRIC_1211, r)),
				new Track("neoforge-1.21.1", (s, i, r, t) -> new NeoForge1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, NeoForge1211Generator.Profile.NEOFORGE_1211, r)),
				new Track("fabric-1.20.1", (s, i, r, t) -> new Fabric1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, Fabric1211Generator.Profile.FABRIC_1201, r)),
				new Track("neoforge-1.20.1", (s, i, r, t) -> new NeoForge1211WorkspaceTaskGateway(s, ignored -> t,
						Path.of("."), CLOCK, i, NeoForge1211Generator.Profile.NEOFORGE_1201, r)));
	}

	private static WorkspaceState workspace(UUID id, String generatorId) {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", generatorId);
		generator.addProperty("loader", generatorId.startsWith("fabric") ? "fabric" : "neoforge");
		generator.addProperty("minecraftVersion", generatorId.substring(generatorId.indexOf('-') + 1));
		generator.addProperty("displayName", generatorId);
		generator.addProperty("state", "ready");
		return new WorkspaceState(id, "Stage9 Managed Matrix", "mod", 0, false, generator, new JsonObject(), List.of());
	}

	private record Track(String generatorId, Factory factory) { }

	@FunctionalInterface
	private interface Factory {
		WorkspaceTaskGateway create(RevisionedWorkspaceStore store, Supplier<UUID> ids,
				Fabric1211ProcessRunner runner, Path target);
	}
}
