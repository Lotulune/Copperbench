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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract fixture for server readiness behavior across all supported tracks. */
class Stage9ServerReadinessContractTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-27T08:00:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

	@Test void allEightJavaTracksRequireEulaAndReachReadiness(@TempDir Path root) throws Exception {
		List<Track> tracks = tracks();

		List<String> passed = new ArrayList<>();
		for (Track track : tracks) {
			UUID workspaceId = UUID.nameUUIDFromBytes((track.generatorId + "-workspace").getBytes());
			RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
			store.register(workspace(workspaceId, track.generatorId));
			AtomicLong ids = new AtomicLong(1000);
			Fabric1211ProcessRunner runner = (workingDirectory, arguments, timeout, output) -> {
				assertEquals(List.of("runServer"), arguments);
				assertEquals("eula=true\n", Files.readString(workingDirectory.resolve(track.eulaPath)));
				assertServerConfigPrepared(workingDirectory, track);
				output.accept("COPPERBENCH_STAGE9_SERVER_READY " + track.generatorId);
				return new Fabric1211ProcessRunner.ProcessResult(0, true);
			};
			Path target = root.resolve(track.generatorId);
			WorkspaceTaskGateway tasks = track.factory.create(store,
					() -> UUID.nameUUIDFromBytes((track.generatorId + "-" + ids.getAndIncrement()).getBytes()), runner, target);
			try {
				WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK,
					() -> UUID.nameUUIDFromBytes((track.generatorId + "-service-" + ids.getAndIncrement()).getBytes()));
				JsonObject projection = runServerAndAwait(service, workspaceId);
				assertEquals("succeeded", projection.getAsJsonObject("task").get("state").getAsString());
				assertTrue(projection.getAsJsonArray("logs").toString().contains("COPPERBENCH_STAGE9_SERVER_READY"));
				passed.add(track.generatorId);
			} finally {
				((AutoCloseable) tasks).close();
			}
		}
		assertEquals(8, passed.size());
	}

	@Test void allEightJavaTracksFailClosedOnServerTimeoutAndProcessFailure(@TempDir Path root) throws Exception {
		for (Track track : tracks()) {
			assertServerFailure(root.resolve("timeout"), track, new Fabric1211ProcessRunner.ProcessResult(124, false),
					"SERVER_TIMEOUT " + track.generatorId);
			assertServerFailure(root.resolve("exit"), track, new Fabric1211ProcessRunner.ProcessResult(1, true),
					"COPPERBENCH_STAGE9_SERVER_READY before fatal exit " + track.generatorId);
		}
	}

	private static void assertServerConfigPrepared(Path workingDirectory, Track track) throws Exception {
		if (track.serverConfigPath == null) return;
		assertTrue(Files.readString(workingDirectory.resolve(track.serverConfigPath))
				.contains("advertiseDedicatedServerToLan = false"));
	}

	private static List<Track> tracks() {
		return List.of(
				new Track("fabric-26.2", "run/eula.txt", null, (store, ids, runner, target) -> new Fabric1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, Fabric1211Generator.Profile.FABRIC_262, runner)),
				new Track("neoforge-26.2", "run/eula.txt", null, (store, ids, runner, target) -> new NeoForge1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, NeoForge1211Generator.Profile.NEOFORGE_262, runner)),
				new Track("fabric-26.1.2", "run/eula.txt", null, (store, ids, runner, target) -> new Fabric1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, Fabric1211Generator.Profile.FABRIC_261, runner)),
				new Track("neoforge-26.1.2", "run/eula.txt", null, (store, ids, runner, target) -> new NeoForge1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, NeoForge1211Generator.Profile.NEOFORGE_261, runner)),
				new Track("fabric-1.21.1", "run/eula.txt", null, (store, ids, runner, target) -> new Fabric1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, Fabric1211Generator.Profile.FABRIC_1211, runner)),
				new Track("neoforge-1.21.1", "run/eula.txt", null, (store, ids, runner, target) -> new NeoForge1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, NeoForge1211Generator.Profile.NEOFORGE_1211, runner)),
				new Track("fabric-1.20.1", "run/eula.txt", null, (store, ids, runner, target) -> new Fabric1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, Fabric1211Generator.Profile.FABRIC_1201, runner)),
				new Track("neoforge-1.20.1", "runs/server/eula.txt",
						"runs/server/world/serverconfig/forge-server.toml",
						(store, ids, runner, target) -> new NeoForge1211WorkspaceTaskGateway(store,
						ignored -> target, Path.of("."), CLOCK, ids, NeoForge1211Generator.Profile.NEOFORGE_1201, runner)));
	}

	private static void assertServerFailure(Path root, Track track, Fabric1211ProcessRunner.ProcessResult result,
			String emittedLog) throws Exception {
		String scenario = result.exitCode() == 124 ? "timeout" : "exit";
		UUID workspaceId = UUID.nameUUIDFromBytes((track.generatorId + "-" + scenario + "-workspace").getBytes());
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace(workspaceId, track.generatorId));
		AtomicLong ids = new AtomicLong(2000);
		Fabric1211ProcessRunner runner = (workingDirectory, arguments, timeout, output) -> {
			assertEquals(List.of("runServer"), arguments);
			assertEquals("eula=true\n", Files.readString(workingDirectory.resolve(track.eulaPath)));
			assertServerConfigPrepared(workingDirectory, track);
			output.accept(emittedLog);
			return result;
		};
		Path target = root.resolve(track.generatorId);
		WorkspaceTaskGateway tasks = track.factory.create(store,
				() -> UUID.nameUUIDFromBytes((track.generatorId + "-" + scenario + "-" + ids.getAndIncrement()).getBytes()),
				runner, target);
		try {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK,
					() -> UUID.nameUUIDFromBytes((track.generatorId + "-" + scenario + "-service-"
							+ ids.getAndIncrement()).getBytes()));
			JsonObject projection = runServerAndAwait(service, workspaceId);
			assertEquals("failed", projection.getAsJsonObject("task").get("state").getAsString(),
					track.generatorId + " must fail closed for " + scenario);
			assertTrue(projection.getAsJsonArray("logs").toString().contains(emittedLog));
		} finally {
			((AutoCloseable) tasks).close();
		}
	}

	private static JsonObject runServerAndAwait(WorkspaceApplicationService service, UUID workspaceId) throws Exception {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", UUID.randomUUID().toString());
		payload.addProperty("scope", "workspace");
		payload.addProperty("userApproved", true);
		var accepted = service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.RUN_SERVER, payload), UI);
		assertEquals("accepted", accepted.result().status());
		UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());
		return awaitTask(service, workspaceId, taskId);
	}

	private static WorkspaceState workspace(UUID id, String generatorId) {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", generatorId);
		generator.addProperty("loader", generatorId.startsWith("fabric") ? "fabric" : "neoforge");
		generator.addProperty("minecraftVersion", generatorId.substring(generatorId.indexOf('-') + 1));
		generator.addProperty("displayName", generatorId);
		generator.addProperty("state", "ready");
		return new WorkspaceState(id, "Stage9 Server Matrix", "mod", 0, false, generator, new JsonObject(), List.of());
	}

	private static JsonObject awaitTask(WorkspaceApplicationService service, UUID workspaceId, UUID taskId)
			throws Exception {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			JsonObject payload = new JsonObject();
			payload.addProperty("taskId", taskId.toString());
			var result = service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_TASK, payload), UI);
			JsonObject projection = result.data().getAsJsonObject();
			String state = projection.getAsJsonObject("task").get("state").getAsString();
			if (!state.equals("queued") && !state.equals("running")) return projection;
			Thread.sleep(20);
		}
		throw new AssertionError("Stage 9 server readiness task did not finish");
	}

	private record Track(String generatorId, String eulaPath, String serverConfigPath, Factory factory) { }

	@FunctionalInterface
	private interface Factory {
		WorkspaceTaskGateway create(RevisionedWorkspaceStore store, java.util.function.Supplier<UUID> ids,
				Fabric1211ProcessRunner runner, Path target);
	}
}
