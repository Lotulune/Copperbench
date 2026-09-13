package dev.copperbench.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceModIdentity;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceModIdentityTest {
	@TempDir Path root;

	@ParameterizedTest @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
			"neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
	void metadataAndEveryTestHostUsePersistedIdInsteadOfDisplayName(String generatorId) throws Exception {
		var workspace = workspace(generatorId, "{\"workspaceSettings\":{\"modid\":\"copper_signal\"},"
				+ "\"copperbench\":{\"modId\":\"stale_projection\"}}");
		JsonObject environment;
		List<String> generated;
		if (generatorId.startsWith("fabric")) {
			var profile = List.of(Fabric1211Generator.Profile.FABRIC_1201, Fabric1211Generator.Profile.FABRIC_1211,
					Fabric1211Generator.Profile.FABRIC_261, Fabric1211Generator.Profile.FABRIC_262).stream()
					.filter(value -> value.generatorId().equals(generatorId)).findFirst().orElseThrow();
			var generator = new Fabric1211Generator(Path.of("."), profile);
			var result = generator.generate(root, workspace);
			assertEquals("copper_signal", result.modId()); generated = result.generatedPaths(); environment = generator.gameTestEnvironment();
			assertEquals("copper_signal", JsonParser.parseString(Files.readString(root.resolve("src/main/resources/fabric.mod.json")))
					.getAsJsonObject().get("id").getAsString());
		} else {
			var profile = List.of(NeoForge1211Generator.Profile.NEOFORGE_1201, NeoForge1211Generator.Profile.NEOFORGE_1211,
					NeoForge1211Generator.Profile.NEOFORGE_261, NeoForge1211Generator.Profile.NEOFORGE_262).stream()
					.filter(value -> value.generatorId().equals(generatorId)).findFirst().orElseThrow();
			var generator = new NeoForge1211Generator(Path.of("."), profile);
			var result = generator.generate(root, workspace);
			assertEquals("copper_signal", result.modId()); generated = result.generatedPaths(); environment = generator.gameTestEnvironment();
			String metadata = Files.readString(root.resolve(generated.stream().filter(path -> path.endsWith("mods.toml")).findFirst().orElseThrow()));
			assertTrue(metadata.contains("modId=\"copper_signal\"")); assertFalse(metadata.contains("stale_projection"));
		}
		GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
			public String displayName() { return "test"; }
			public String diagnosticPrefix() { return "TEST"; }
			public List<ValidationIssue> validate(WorkspaceState state) { return List.of(); }
			public GenerationResult generate(Path path, WorkspaceState state) { throw new UnsupportedOperationException(); }
		};
		GameTestSupport.prepare(root, environment, backend.gameTestModId(workspace));
		String test = Files.readString(root.resolve("src/gametest/java/copperbench/acceptance/AcceptanceTests.java"));
		assertTrue(test.contains("\"copper_signal\""));
		assertFalse(test.contains("copper_signal_lantern")); assertFalse(test.contains("stale_projection"));
	}

	@Test void prepareOperationUsesNativeSettingsWithoutAnyProductOverride() throws Exception {
		var state = workspace("fabric-1.21.1", "{\"workspaceSettings\":{\"modid\":\"copper_signal\"}}");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore(); store.register(state);
		try (var gateway = new Fabric1211WorkspaceTaskGateway(store, ignored -> root, Path.of("."), Clock.systemUTC(), UUID::randomUUID,
				(working, args, timeout, output) -> { throw new AssertionError("Preparation must not run Gradle"); })) {
			UUID id = UUID.fromString(gateway.start(state.id(), Operation.PREPARE_GAME_TESTS, new JsonObject()).get("id").getAsString());
			Instant deadline = Instant.now().plusSeconds(15);
			JsonObject task;
			do {
				task = gateway.find(state.id(), id).orElseThrow();
				if (!List.of("running", "queued").contains(task.get("state").getAsString())) break;
				Thread.sleep(20);
			} while (Instant.now().isBefore(deadline));
			assertEquals("succeeded", task.get("state").getAsString(), task.toString());
			assertEquals("copper_signal", task.getAsJsonObject("gameTestSetup").get("modId").getAsString());
			assertTrue(Files.readString(root.resolve("src/gametest/java/copperbench/acceptance/AcceptanceTests.java")).contains("\"copper_signal\""));
		}
	}

	@ParameterizedTest @ValueSource(strings = {"null", "{}", "{\"modid\":null}", "{\"modid\":\"\"}", "{\"modid\":123}", "{\"modid\":\"Bad ID\"}"})
	void malformedNativeSettingsNeverFallBackToTheDisplayName(String settings) {
		assertThrows(IllegalArgumentException.class, () -> WorkspaceModIdentity.resolve(workspace("fabric-1.21.1",
				"{\"workspaceSettings\":" + settings + ",\"copperbench\":{\"modId\":\"stale_projection\"}}")));
	}

	@Test void legacyProductOnlyWorkspacesRetainTheirExplicitId() {
		assertEquals("copper_signal", WorkspaceModIdentity.resolve(workspace("fabric-1.21.1", "{\"copperbench\":{\"modId\":\"copper_signal\"}}")));
	}

	private static WorkspaceState workspace(String generatorId, String document) {
		JsonObject generator = new JsonObject(); generator.addProperty("id", generatorId);
		return new WorkspaceState(UUID.randomUUID(), "Copper Signal Lantern", "mod", 0, false, generator,
				JsonParser.parseString(document).getAsJsonObject(), List.of());
	}
}
