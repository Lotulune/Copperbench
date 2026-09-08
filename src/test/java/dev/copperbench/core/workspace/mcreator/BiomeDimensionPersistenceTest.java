package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Biome;
import net.mcreator.element.types.Dimension;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeDimensionPersistenceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T02:00:00Z"), ZoneOffset.UTC);
	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void persistsCanonicalBiomeAndDimensionReferencesThroughUpstreamTypes() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("worldgen_depth");
		settings.setModName("Worldgen Depth");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("worldgen_depth.mcreator");
		AtomicLong ids = new AtomicLong(300);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings);
				MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
						UUID.fromString("11111111-1111-4111-8111-111111111111"),
						new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
						() -> uuid(ids.incrementAndGet()))) {
			var biomeCreated = session.uiEntry().execute(create(session.workspaceId(), 0, "biome", "copper_grove",
					new JsonObject()));
			assertEquals("committed", biomeCreated.result().status());

			JsonObject dimensionValues = new JsonObject();
			JsonArray biomes = new JsonArray();
			biomes.add("CUSTOM:copper_grove");
			dimensionValues.add("biomesInDimension", biomes);
			var dimensionCreated = session.uiEntry().execute(create(session.workspaceId(), 1, "dimension",
					"copper_realm", dimensionValues));
			assertEquals("committed", dimensionCreated.result().status());
			UUID dimensionId = UUID.fromString(dimensionCreated.result().data().getAsJsonObject()
					.getAsJsonObject("element").get("id").getAsString());

			Biome storedBiome = assertInstanceOf(Biome.class,
					workspace.getModElementByName("copper_grove").getGeneratableElement());
			assertEquals("Blocks.GRASS", storedBiome.groundBlock.getUnmappedValue());
			assertEquals("Blocks.DIRT#0", storedBiome.undergroundBlock.getUnmappedValue());
			assertFalse(storedBiome.spawnParticles);

			Dimension storedDimension = assertInstanceOf(Dimension.class,
					workspace.getModElementByName("copper_realm").getGeneratableElement());
			assertEquals("Normal world gen", storedDimension.worldGenType);
			assertEquals("Blocks.STONE#0", storedDimension.mainFillerBlock.getUnmappedValue());
			assertEquals("Blocks.WATER", storedDimension.fluidBlock.getUnmappedValue());
			assertEquals(63, storedDimension.seaLevel);
			assertFalse(storedDimension.enablePortal);
			assertFalse(storedDimension.enableIgniter);
			assertFalse(storedDimension.enableCustomSkyboxTextures);
			assertFalse(storedDimension.enableCustomSunMoonTextures);
			assertEquals(1, storedDimension.biomesInDimension.size());
			assertEquals("CUSTOM:copper_grove", storedDimension.biomesInDimension.getFirst().getUnmappedValue());

			JsonArray updatedBiomes = new JsonArray();
			updatedBiomes.add("CUSTOM:copper_grove");
			updatedBiomes.add("#is_overworld");
			JsonObject change = new JsonObject();
			change.addProperty("path", "/biomesInDimension");
			change.add("value", updatedBiomes);
			JsonArray changes = new JsonArray();
			changes.add(change);
			JsonObject updatePayload = new JsonObject();
			updatePayload.addProperty("clientMutationId", uuid(80).toString());
			updatePayload.addProperty("elementId", dimensionId.toString());
			updatePayload.add("changes", changes);
			Command update = Command.of(uuid(81), session.workspaceId(), 2,
					Operation.UPDATE_MOD_ELEMENT, updatePayload);
			assertEquals("committed", session.uiEntry().execute(update).result().status());

			Dimension updated = assertInstanceOf(Dimension.class,
					workspace.getModElementByName("copper_realm").getGeneratableElement());
			assertEquals(2, updated.biomesInDimension.size());
			assertEquals("CUSTOM:copper_grove", updated.biomesInDimension.getFirst().getUnmappedValue());
			assertEquals("#is_overworld", updated.biomesInDimension.get(1).getUnmappedValue());
			assertTrue(Files.isRegularFile(root.resolve("elements/copper_grove.mod.json")));
			assertTrue(Files.isRegularFile(root.resolve("elements/copper_realm.mod.json")));
		}
	}

	private static Command create(UUID workspaceId, long revision, String type, String name, JsonObject values) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(20 + revision).toString());
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", values);
		return Command.of(uuid(30 + revision), workspaceId, revision, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
