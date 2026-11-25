package dev.copperbench.generator.project;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.workspace.project.GradleProjectWorkspaceSession;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Gradle and Minecraft processes on a copy of an existing project (ADR-0020's completion criterion).
 * {@code COPPERBENCH_ADOPTED_PROJECT} names the project, {@code COPPERBENCH_ADOPTED_TESTS} an optional replacement for
 * the generated AcceptanceTests.java, and {@code COPPERBENCH_ADOPTED_CLIENT=true} also opens the client until it renders.
 * No server is started, so no EULA is accepted.
 */
@EnabledIfEnvironmentVariable(named = "COPPERBENCH_ADOPTED_PROJECT", matches = ".+")
class GradleProjectRealTaskTest {

	private GradleProjectWorkspaceSession session;
	private final JsonObject evidence = new JsonObject();

	@Test void anExistingProjectBuildsAndPassesAcceptanceWithoutChangingItsFiles(@TempDir Path temp) throws Exception {
		Path source = Path.of(System.getenv("COPPERBENCH_ADOPTED_PROJECT")).toAbsolutePath().normalize();
		Path root = temp.resolve("p");
		copy(source, root);
		var before = files(root, path -> true);
		Path repository = Path.of("").toAbsolutePath();
		try (var opened = GradleProjectWorkspaceSession.open(root, (store, folder) -> new GradleProjectTaskGateway(store, ignored -> folder,
				repository, Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
			session = opened;
			succeeded(Operation.VALIDATE_WORKSPACE, new JsonObject());
			succeeded(Operation.BUILD_WORKSPACE, new JsonObject());
			succeeded(Operation.PREPARE_GAME_TESTS, new JsonObject());
			String tests = System.getenv("COPPERBENCH_ADOPTED_TESTS");
			if (tests != null && !tests.isBlank())
				Files.writeString(root.resolve("src/copperbenchTest/java/copperbench/acceptance/AcceptanceTests.java"), Files.readString(Path.of(tests)));
			JsonObject tested = succeeded(Operation.RUN_GAMETEST, new JsonObject());
			assertEquals("passed", tested.getAsJsonObject("verification").get("status").getAsString());

			JsonObject verified = new JsonObject();
			verified.addProperty("verifiedTaskId", tested.get("id").getAsString());
			JsonObject exported = succeeded(Operation.EXPORT_WORKSPACE, verified).getAsJsonObject("verifiedExport");
			assertEquals("passed_current_input", exported.get("status").getAsString());
			assertEquals(tested.getAsJsonObject("verification").get("artifactSha256").getAsString(), WorkspaceExecutionSnapshot.sha256(
					Path.of(exported.get("exportDirectory").getAsString()).resolve("verified-mod.jar")));
			JsonObject plain = new JsonObject();
			plain.addProperty("output", "dist/mod.jar");
			succeeded(Operation.EXPORT_WORKSPACE, plain);
			assertTrue(Files.size(root.resolve("dist/mod.jar")) > 0);
			if ("true".equals(System.getenv("COPPERBENCH_ADOPTED_CLIENT"))) client();
		}
		var after = files(root, path -> before.containsKey(path));
		assertEquals(before, after, "building, testing and exporting change none of the project's own files");
		Path results = repository.resolve("build/adopted-project-verification");
		Files.createDirectories(results);
		Files.writeString(results.resolve(source.getFileName() + ".json"), evidence + "\n", StandardCharsets.UTF_8);
	}

	/** Opens the game from the isolated copy and cancels it once the render thread created its first atlas. */
	private void client() throws Exception {
		UUID taskId = start(Operation.RUN_CLIENT, new JsonObject());
		Instant deadline = Instant.now().plus(Duration.ofMinutes(10));
		while (true) {
			JsonObject task = task(taskId);
			if (task.getAsJsonObject("stage").get("key").getAsString().equals("task.run_client.rendering")) break;
			assertEquals("running", task.get("state").getAsString(), task.toString());
			assertTrue(Instant.now().isBefore(deadline), "the client did not start rendering");
			Thread.sleep(500);
		}
		JsonObject cancel = new JsonObject();
		cancel.addProperty("taskId", taskId.toString());
		var cancelled = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision(), Operation.CANCEL_TASK, cancel));
		assertTrue(cancelled.result().diagnostics().isEmpty(), cancelled.result().diagnostics().toString());
		assertEquals("cancelled", task(taskId).get("state").getAsString());
		evidence.addProperty("run_client", "rendering, then cancelled");
	}

	private JsonObject succeeded(Operation operation, JsonObject payload) throws Exception {
		Instant started = Instant.now();
		UUID taskId = start(operation, payload);
		while (true) {
			JsonObject task = task(taskId);
			if (!List.of("running", "queued").contains(task.get("state").getAsString())) {
				JsonObject entry = new JsonObject();
				entry.addProperty("seconds", Duration.between(started, Instant.now()).toMillis() / 1000.0);
				entry.add("task", task);
				evidence.add(operation.name().toLowerCase(java.util.Locale.ROOT) + (payload.has("verifiedTaskId") ? "_verified" : ""), entry);
				assertEquals("succeeded", task.get("state").getAsString(), task + "\n" + logs(taskId));
				return task;
			}
			Thread.sleep(200);
		}
	}

	private UUID start(Operation operation, JsonObject payload) {
		payload.addProperty("clientMutationId", UUID.randomUUID().toString());
		payload.addProperty("scope", "workspace");
		var result = session.headlessEntry(PermissionProfile.WORKSPACE)
				.execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision(), operation, payload)).result();
		assertEquals("accepted", result.status(), result.diagnostics().toString());
		return UUID.fromString(result.task().getAsJsonObject().get("id").getAsString());
	}

	private long revision() {
		return session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_WORKBENCH, new JsonObject())).revision();
	}

	private JsonObject observed(UUID taskId) {
		JsonObject payload = new JsonObject();
		payload.addProperty("taskId", taskId.toString());
		payload.addProperty("afterLogSequence", 0);
		return session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.GET_TASK, payload)).data().getAsJsonObject();
	}

	private JsonObject task(UUID taskId) {
		return observed(taskId).getAsJsonObject("task");
	}

	private String logs(UUID taskId) {
		StringBuilder text = new StringBuilder();
		observed(taskId).getAsJsonArray("logs").forEach(entry -> text.append(entry.getAsJsonObject().get("text").getAsString()).append('\n'));
		return text.length() > 20_000 ? text.substring(text.length() - 20_000) : text.toString();
	}

	private static void copy(Path from, Path to) throws Exception {
		try (var paths = Files.walk(from)) {
			for (Path path : paths.toList()) {
				String relative = from.relativize(path).toString().replace('\\', '/');
				if (List.of(".git", ".gradle", ".copperbench", "build", "run", "runs").contains(relative.split("/")[0])) continue;
				Path target = to.resolve(relative);
				if (Files.isDirectory(path)) Files.createDirectories(target);
				else Files.copy(path, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
		assertFalse(Files.exists(to.resolve(".copperbench")));
	}

	private static TreeMap<String, String> files(Path root, Predicate<String> included) throws Exception {
		var result = new TreeMap<String, String>();
		try (var paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				String relative = root.relativize(path).toString().replace('\\', '/');
				if (included.test(relative)) result.put(relative, WorkspaceExecutionSnapshot.sha256(path));
			}
		}
		return result;
	}
}
