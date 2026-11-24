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
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Sound events are a registry like tags and variables, backed by MCreator's sound elements. */
class SoundRegistryTest {
	@TempDir Path root;

	@BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

	@Test void soundEventsAreCreatedUpdatedAndUnregisteredWithoutDeletingTheirFiles() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("trail");
		settings.setModName("Trail"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
		try (Workspace workspace = Workspace.createWorkspace(root.resolve("trail.mcreator").toFile(), settings);
			 var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
					 new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
			Path sound = workspace.getFolderManager().getSoundsDir().toPath().resolve("lamp_click.ogg");
			Files.createDirectories(sound.getParent());
			Files.write(sound, new byte[] { 'O', 'g', 'g', 'S' });

			var missingFiles = session.uiEntry().execute(create(session.workspaceId(), 0, "lamp.click", new JsonArray(), "block"));
			assertEquals("REGISTRY_SOUND_FILES_INVALID", missingFiles.result().diagnostics().getFirst().code(), missingFiles.result().toString());
			var badCategory = session.uiEntry().execute(create(session.workspaceId(), 0, "lamp.click", files("lamp_click"), "loud"));
			assertEquals("REGISTRY_SOUND_CATEGORY_INVALID", badCategory.result().diagnostics().getFirst().code());
			assertTrue(workspace.getSoundElements().isEmpty());

			var created = session.uiEntry().execute(create(session.workspaceId(), 0, "lamp.click", files("lamp_click"), "block"));
			assertEquals("committed", created.result().status(), created.result().toString());
			SoundElement registered = workspace.getSoundElements().iterator().next();
			assertEquals("lamp.click", registered.getName());
			assertEquals(List.of("lamp_click"), registered.getFiles());
			assertEquals("block", registered.getCategory());
			String entryId = created.result().data().getAsJsonObject().getAsJsonObject("entry").get("id").getAsString();
			JsonObject saved = JsonParser.parseString(Files.readString(root.resolve("trail.mcreator"))).getAsJsonObject();
			assertTrue(saved.toString().contains("lamp.click"), "the sound element is saved in the workspace file");

			JsonObject listed = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(),
					Operation.LIST_WORKSPACE_REGISTRIES, registryPayload())).data().getAsJsonObject();
			assertEquals("sound", listed.getAsJsonArray("sounds").get(0).getAsJsonObject().get("kind").getAsString());

			JsonObject update = new JsonObject();
			update.addProperty("clientMutationId", UUID.randomUUID().toString());
			update.addProperty("entryId", entryId);
			JsonArray changes = new JsonArray();
			JsonObject change = new JsonObject(); change.addProperty("path", "/subtitle"); change.addProperty("value", "subtitles.trail.lamp_click");
			changes.add(change);
			update.add("changes", changes);
			var updated = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), created.result().newRevision(),
					Operation.UPDATE_REGISTRY_ENTRY, update));
			assertEquals("committed", updated.result().status(), updated.result().toString());
			assertEquals("subtitles.trail.lamp_click", workspace.getSoundElements().iterator().next().getSubtitle());

			JsonObject delete = new JsonObject();
			delete.addProperty("clientMutationId", UUID.randomUUID().toString());
			delete.addProperty("entryId", entryId);
			delete.addProperty("force", true);
			var deleted = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), updated.result().newRevision(),
					Operation.DELETE_REGISTRY_ENTRY, delete));
			assertEquals("committed", deleted.result().status(), deleted.result().toString());
			assertTrue(workspace.getSoundElements().isEmpty());
			assertTrue(Files.isRegularFile(sound), "unregistering a sound event keeps its audio file");
		}
	}

	private static JsonObject registryPayload() {
		JsonObject payload = new JsonObject();
		payload.addProperty("registry", "sounds");
		return payload;
	}

	private static JsonArray files(String... names) {
		JsonArray files = new JsonArray();
		for (String name : names) files.add(name);
		return files;
	}

	private static Command create(UUID workspaceId, long revision, String name, JsonArray files, String category) {
		JsonObject entry = new JsonObject();
		entry.addProperty("name", name);
		entry.add("files", files);
		entry.addProperty("category", category);
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", UUID.randomUUID().toString());
		payload.addProperty("registry", "sounds");
		payload.add("entry", entry);
		return Command.of(UUID.randomUUID(), workspaceId, revision, Operation.CREATE_REGISTRY_ENTRY, payload);
	}
}
