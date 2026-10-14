package dev.copperbench.tracks;

import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceStateMapper;
import dev.copperbench.generator.WorkspaceExecutionEnvironment;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactGeneratorWorkspaceTest {
	@TempDir Path root;

	@BeforeAll static void initialize() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@ParameterizedTest
	@ValueSource(strings = { "fabric-26.2", "neoforge-26.2", "fabric-26.1.2", "neoforge-26.1.2",
			"fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1" })
	void persistedWorkspaceReopensWithExactIdentityOutsideDisplayTracks(String generatorId) throws Exception {
		var official = VersionTrackCatalog.official();
		var rotated = official.withTracks(official.tracks().stream()
				.filter(track -> track.id() != VersionTrackCatalog.TrackId.PREVIOUS_STABLE).toList());
		var settings = new WorkspaceSettings("registry_fixture");
		settings.setModName("Registry Fixture");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator(generatorId);
		settings.setModElementsPackage("dev.copperbench.registryfixture");
		Path file = root.resolve("registry_fixture.mcreator");
		try (Workspace created = Workspace.createWorkspace(file.toFile(), settings)) {
			assertEquals(generatorId, created.getGenerator().getGeneratorName());
		}
		assertTrue(Files.isRegularFile(file));
		try (Workspace reopened = Workspace.readFromFS(file.toFile(), null)) {
			var state = new MCreatorWorkspaceStateMapper().map(reopened,
					new ProductMetadataManager.Metadata(1, UUID.randomUUID(), 0));
			assertEquals(generatorId, reopened.getWorkspaceSettings().getCurrentGenerator());
			assertEquals(generatorId, state.generator().get("id").getAsString());
			assertTrue(rotated.decision(generatorId).generatable());
			var environment = WorkspaceExecutionEnvironment.describe(root, Path.of("."), generatorId, rotated);
			assertEquals(WorkspaceExecutionEnvironment.describe(root, Path.of("."), generatorId), environment);
			assertEquals(generatorId, environment.get("generatorId").getAsString());
			assertEquals(generatorId.substring(generatorId.indexOf('-') + 1),
					environment.get("minecraftVersion").getAsString());
			assertEquals(generatorId.endsWith("1.20.1") ? 17 : generatorId.endsWith("1.21.1") ? 21 : 25,
					environment.getAsJsonObject("java").get("requiredRelease").getAsInt());
		}
	}

	@Test void unknownEnvironmentLookupDoesNotCreateAWorkspace() {
		Path absent = root.resolve("absent");
		assertTrue(WorkspaceExecutionEnvironment.describe(absent, Path.of("."), "fabric-26.4").isEmpty());
		assertFalse(Files.exists(absent));
	}
}
