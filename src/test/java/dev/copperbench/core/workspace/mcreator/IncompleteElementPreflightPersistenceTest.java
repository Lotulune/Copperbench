package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncompleteElementPreflightPersistenceTest {
	@TempDir Path root;

	@BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

	@ParameterizedTest
	@CsvSource({"specialentity,false", "specialentity,true", "tool,false", "tool,true", "livingentity,false", "livingentity,true"})
	void incompleteCreateIsRejectedBeforeGenerationWithoutChangingFilesOrRevision(String type, boolean plan) throws Exception {
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
			java.util.List<dev.copperbench.core.contract.UiCore.Diagnostic> diagnostics;
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
				assertEquals("failed", planned.status(), planned.diagnostics().toString());
				diagnostics = planned.diagnostics();
			} else {
				var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, payload)).result();
				assertEquals("rejected", result.status(), result.diagnostics().toString());
				assertEquals(0, result.newRevision());
				diagnostics = result.diagnostics();
			}
			assertEquals("FIELD_REQUIRED", diagnostics.getFirst().code(), diagnostics.toString());
			assertTrue(diagnostics.getFirst().path().startsWith("/initialValues/"));
			assertEquals("string", diagnostics.getFirst().message().args().get("expectedType").getAsString());
			assertTrue(diagnostics.getFirst().message().args().has("repairHint"));
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

	private Workspace newWorkspace(String generator) throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("mutation_probe");
		settings.setModName("Mutation Probe"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
		return Workspace.createWorkspace(root.resolve("mutation_probe.mcreator").toFile(), settings);
	}

	private static JsonObject probe(String type) {
		JsonObject payload = new JsonObject();
		payload.addProperty("elementType", type); payload.addProperty("name", "probe_" + type);
		// These formerly failed after conditional base files had been written; now preflight rejects them.
		payload.add("initialValues", JsonParser.parseString(switch (type) {
			case "specialentity" -> "{\"entityType\":\"Boat\"}";
			case "tool" -> "{\"texture\":null,\"toolType\":\"Shears\",\"enchantability\":2,\"customModelName\":\"Normal\"}";
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
