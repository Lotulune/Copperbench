package dev.copperbench.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real packaged-JAR loading check using the original review project, copied without its authored tests. */
@EnabledIfEnvironmentVariable(named = "COPPERBENCH_MOD_ID_SMOKE_SOURCE", matches = ".+")
class GameTestModIdentitySmokeTest {
	@Test void newlyPreparedTestLoadsTheOriginalCopperSignalJar(@TempDir Path root) throws Exception {
		Path source = Path.of(System.getenv("COPPERBENCH_MOD_ID_SMOKE_SOURCE"));
		for (String entry : List.of("src/main", "assets", "gradle", "gradlew", "gradlew.bat", "gradle.properties",
				"settings.gradle", "build.gradle", "mcreator.gradle", "copper_signal.mcreator")) {
			Path input = source.resolve(entry);
			try (var paths = Files.walk(input)) {
				for (Path file : paths.filter(Files::isRegularFile).toList()) {
					Path target = root.resolve(source.relativize(file));
					Files.createDirectories(target.getParent()); Files.copy(file, target);
				}
			}
		}
		JsonObject document = JsonParser.parseString(Files.readString(root.resolve("copper_signal.mcreator"))).getAsJsonObject();
		assertEquals("Copper Signal Lantern", document.getAsJsonObject("workspaceSettings").get("modName").getAsString());
		assertEquals("copper_signal", document.getAsJsonObject("workspaceSettings").get("modid").getAsString());
		assertFalse(document.has("copperbench"));
		JsonObject generator = new JsonObject(); generator.addProperty("id", "fabric-1.21.1");
		var state = new WorkspaceState(UUID.randomUUID(), "Copper Signal Lantern", "mod", 0, false, generator, document, List.of());
		var store = new RevisionedWorkspaceStore(); store.register(state);
		Path evidence = Path.of("build/mod-identity-smoke"); Files.createDirectories(evidence);
		try (var gateway = new Fabric1211WorkspaceTaskGateway(store, ignored -> root, Path.of("."), Clock.systemUTC(), UUID::randomUUID)) {
			var prepared = run(gateway, state.id(), Operation.PREPARE_GAME_TESTS, evidence);
			assertEquals("succeeded", prepared.get("state").getAsString(), prepared.toString());
			assertEquals("copper_signal", prepared.getAsJsonObject("gameTestSetup").get("modId").getAsString());
			Files.copy(root.resolve("src/gametest/java/copperbench/acceptance/AcceptanceTests.java"), evidence.resolve("AcceptanceTests.java"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			var result = run(gateway, state.id(), Operation.RUN_GAMETEST, evidence);
			assertEquals("succeeded", result.get("state").getAsString(), result.toString());
			var verification = result.getAsJsonObject("verification");
			assertEquals("passed", verification.get("status").getAsString());
			assertEquals(1, verification.get("acceptanceExecuted").getAsInt());
			assertTrue(verification.get("sourceCurrentAtCompletion").getAsBoolean());
			Files.copy(Path.of(verification.get("reportPath").getAsString()), evidence.resolve("gametest-results.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private JsonObject run(Fabric1211WorkspaceTaskGateway gateway, UUID workspaceId, Operation operation, Path evidence) throws Exception {
		UUID taskId = UUID.fromString(gateway.start(workspaceId, operation, new JsonObject()).get("id").getAsString());
		Instant deadline = Instant.now().plusSeconds(900);
		while (Instant.now().isBefore(deadline)) {
			var task = gateway.find(workspaceId, taskId).orElseThrow();
			Files.writeString(evidence.resolve(operation + ".json"), task.toString());
			Files.writeString(evidence.resolve(operation + ".log"), gateway.logs(workspaceId, taskId).stream()
					.map(log -> log.get("text").getAsString()).collect(java.util.stream.Collectors.joining("\n")));
			if (!List.of("queued", "running").contains(task.get("state").getAsString())) return task;
			Thread.sleep(1000);
		}
		gateway.cancel(workspaceId, taskId); throw new AssertionError("GameTest smoke timed out");
	}
}
