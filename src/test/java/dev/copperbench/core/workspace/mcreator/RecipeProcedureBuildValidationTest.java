package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RecipeProcedureBuildValidationTest {
	@TempDir Path root;
	@BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

	@Test void namedProcedureAndDefinitionRecipeValidateInLiveAndReopenedStates() throws Exception {
		var settings = new WorkspaceSettings("recipe_procedure");
		settings.setModName("Recipe Procedure"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
		Path document = root.resolve("recipe_procedure.mcreator");
		UUID workspaceId;
		var generator = new Fabric1211Generator(Path.of("."));
		try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase());
			var store = new AtomicReference<RevisionedWorkspaceStore>();
			try (var session = MCreatorWorkspaceSession.attach(workspace, registered -> {
				store.set(registered);
				return new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID);
			}, Clock.systemUTC(), UUID::randomUUID)) {
				workspaceId = session.workspaceId();
				var create = JsonParser.parseString("""
						{"elementType":"procedure","name":"named_procedure","initialValues":{"displayName":"Named Procedure"}}
						""").getAsJsonObject();
				var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, create)).result();
				assertEquals("committed", result.status(), result.diagnostics().toString());
				create = JsonParser.parseString("""
						{"elementType":"recipe","name":"stored_recipe","initialValues":{"recipeType":"Crafting",
						"recipeSlots":["Blocks.COBBLESTONE","","","","","","","",""],"recipeReturnStack":"Blocks.STONE"}}
						""").getAsJsonObject();
				result = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.CREATE_MOD_ELEMENT, create)).result();
				assertEquals("committed", result.status(), result.diagnostics().toString());
				var state = store.get().read(workspaceId).orElseThrow();
				assertTrue(generator.validate(state).isEmpty(), generator.validate(state).toString());
				assertThrows(IllegalArgumentException.class, () -> generator.generate(root.resolve("projection-only"), state),
						"A definition recipe/body must never be replaced by the standalone projection generator");
				var recipe = state.elements().stream().filter(element -> element.type().equals("recipe")).findFirst().orElseThrow();
				var invalidValues = recipe.values(); invalidValues.getAsJsonArray("recipeSlots").set(0, com.google.gson.JsonNull.INSTANCE);
				state.replaceElement(new dev.copperbench.core.workspace.WorkspaceState.Element(recipe.id(), recipe.type(), recipe.name(),
						recipe.displayName(), recipe.state(), recipe.ownership(), recipe.updatedAt(), invalidValues));
				assertTrue(generator.validate(state).stream().anyMatch(issue -> issue.code().equals("FIELD_TYPE_INVALID")),
						"The definition path must still reject null slots");
			}
			// Reproduce 0.1.4 metadata: only the upstream definition contains the actual default XML.
			var procedure = workspace.getModElementByName("named_procedure");
			JsonObject old = new JsonObject(); old.addProperty("displayName", "Named Procedure");
			procedure.putMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_VALUES_METADATA, WorkspaceFileManager.gson.fromJson(old, Object.class));
			workspace.markDirty(); workspace.getFileManager().saveWorkspaceDirectlyAndWait();
		}
		try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
			var state = new MCreatorWorkspaceStateMapper().map(reopened, reopened.getFileManager().loadOrCreateProductMetadata(workspaceId));
			assertTrue(generator.validate(state).isEmpty(), generator.validate(state).toString());
			var body = state.elements().stream().filter(element -> element.type().equals("procedure")).findFirst().orElseThrow().values();
			assertTrue(body.get("procedurexml").getAsString().contains("no_ext_trigger"));
		}
	}
}
