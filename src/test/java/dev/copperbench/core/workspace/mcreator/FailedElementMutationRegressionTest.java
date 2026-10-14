package dev.copperbench.core.workspace.mcreator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.ModElementType;
import net.mcreator.element.types.SpecialEntity;
import net.mcreator.generator.GeneratorGradleCache;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FailedElementMutationRegressionTest {
	@TempDir Path root;

	@BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

	@ParameterizedTest
	@CsvSource({"specialentity,false", "specialentity,true", "tool,false", "tool,true", "livingentity,false"})
	void failedCreateRestoresConditionalFilesAndRevision(String type, boolean plan) throws Exception {
		try (Workspace workspace = newWorkspace("fabric-1.21.1");
				var session = MCreatorWorkspaceSession.attach(workspace,
						store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
						Clock.systemUTC(), UUID::randomUUID)) {
			assertTrue(workspace.getGenerator().generateBase());
			Path source = workspace.getGenerator().getSourceRoot().toPath();
			Files.writeString(source.resolve("Unowned.java"), "// Keep unrelated user source\nclass Unowned {}\n");
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			var before = sourceBytes(root.resolve("src"));
			byte[] document = Files.readAllBytes(root.resolve("mutation_probe.mcreator"));
			var payload = probe(type);
			var operation = Operation.CREATE_MOD_ELEMENT;
			if (plan) {
				JsonObject action = new JsonObject();
				action.addProperty("operation", "create_mod_element");
				action.add("payload", payload);
				JsonArray actions = new JsonArray(); actions.add(action);
				JsonObject request = new JsonObject();
				request.addProperty("expectedRevision", 0);
				request.addProperty("idempotencyKey", "probe-plan");
				request.add("operations", actions);
				var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(),
						Operation.PLAN_WORKSPACE_CHANGES, request));
				assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
				payload = new JsonObject(); payload.add("plan", planned.data().deepCopy());
				operation = Operation.APPLY_WORKSPACE_PLAN;
			}
			var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
					operation, payload)).result();
			assertEquals("rejected", result.status(), result.diagnostics().toString());
			assertEquals(plan ? "WORKSPACE_PLAN_PERSISTENCE_FAILED" : "WORKSPACE_PERSISTENCE_FAILED",
					result.diagnostics().getFirst().code(), result.diagnostics().toString());
			assertEquals(0, result.newRevision());
			assertTrue(workspace.getModElements().isEmpty());
			assertFalse(Files.exists(root.resolve("elements/probe_" + type + ".mod.json")));
			assertArrayEquals(document, Files.readAllBytes(root.resolve("mutation_probe.mcreator")));
			var after = sourceBytes(root.resolve("src"));
			assertEquals(before.keySet(), after.keySet(), "Rejected creation must leave no generated stubs");
			for (Path path : before.keySet()) assertArrayEquals(before.get(path), after.get(path), path.toString());
			assertTrue(workspace.getGenerator().generateBase());
			assertEquals(before.keySet(), sourceBytes(root.resolve("src")).keySet(), "Regeneration must stay clean");
		}
		try (Workspace reopened = Workspace.readFromFS(root.resolve("mutation_probe.mcreator").toFile(), null)) {
			assertTrue(reopened.getModElements().isEmpty());
		}
	}

	@ParameterizedTest @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1"})
	void modelRegistrationRemainsUnambiguousWithBothFabricApiVersions(String generator) throws Exception {
		try (Workspace workspace = newWorkspace(generator)) {
			workspace.getGenerator().setGradleCache(new Gson().fromJson("""
					{"classpath":[],"importTree":{"EntityModelLayerRegistry":[
					"net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry",
					"net.fabricmc.fabric.api.client.rendereregistry.v1.EntityModelLayerRegistry"]}}
					""", GeneratorGradleCache.class));
			ModElement element = new ModElement(workspace, "ModelProbe", ModElementType.SPECIALENTITY);
			SpecialEntity boat = new SpecialEntity(element);
			boat.entityType = "Boat"; boat.name = "Model Probe";
			workspace.addModElement(element);
			workspace.getModElementManager().storeModElement(boat);
			assertTrue(workspace.getGenerator().generateBase());
			Path models = workspace.getGenerator().getPotentialBaseGeneratorFiles().stream()
					.map(java.io.File::toPath).filter(path -> path.getFileName().toString().endsWith("Models.java"))
					.findFirst().orElseThrow();
			String generated = Files.readString(models);
			assertTrue(generated.contains("net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer("), generated);
			assertFalse(generated.contains("import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityModelLayerRegistry;"), generated);
		}
	}

	private Workspace newWorkspace(String generator) throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("mutation_probe");
		settings.setModName("Mutation Probe"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
		return Workspace.createWorkspace(root.resolve("mutation_probe.mcreator").toFile(), settings);
	}

	private static JsonObject probe(String type) {
		JsonObject payload = new JsonObject();
		payload.addProperty("elementType", type); payload.addProperty("name", "probe_" + type);
		// Missing textures fail after conditional base files have been written. Shears also enables a global template.
		payload.add("initialValues", JsonParser.parseString(switch (type) {
			case "specialentity" -> "{\"entityType\":\"Boat\"}";
			case "tool" -> "{\"toolType\":\"Shears\",\"enchantability\":2,\"customModelName\":\"Normal\"}";
			default -> "{}";
		}));
		return payload;
	}

	private static Map<Path, byte[]> sourceBytes(Path source) throws Exception {
		Map<Path, byte[]> result = new LinkedHashMap<>();
		try (var paths = Files.walk(source)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) result.put(path, Files.readAllBytes(path));
		}
		return result;
	}
}
