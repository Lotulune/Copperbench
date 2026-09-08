package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Projectile;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileLifecyclePersistenceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T06:00:00Z"), ZoneOffset.UTC);
	private static final String EMPTY_PROCEDURE_XML =
			"<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\"><field name=\"trigger\">no_ext_trigger</field></block></xml>";

	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void projectileLifecycleProcedureReferencesPersistIntoGeneratedSourcesAndCanBeCleared() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("projectile_hooks");
		settings.setModName("Projectile Hooks");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		AtomicLong ids = new AtomicLong(500);
		try (Workspace workspace = Workspace.createWorkspace(root.resolve("projectile_hooks.mcreator").toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase());
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("44444444-4444-4444-8444-444444444444"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				long revision = 0;
				for (String name : new String[] { "hit_block", "hit_player", "hit_entity", "flying_tick" }) {
					JsonObject values = new JsonObject();
					values.addProperty("procedurexml", EMPTY_PROCEDURE_XML);
					var created = session.uiEntry().execute(create(session.workspaceId(), revision++, "procedure", name, values));
					assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				}

				JsonObject values = new JsonObject();
				values.addProperty("projectileItem", "Items.ARROW");
				values.addProperty("entityModel", "Default");
				values.addProperty("onHitsBlock", "hit_block");
				values.addProperty("onHitsPlayer", "hit_player");
				values.addProperty("onHitsEntity", "hit_entity");
				values.addProperty("onFlyingTick", "flying_tick");
				var created = session.uiEntry().execute(create(session.workspaceId(), revision, "projectile", "guided_bolt", values));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String projectileId = created.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();

				Projectile projectile = (Projectile) workspace.getModElementByName("guided_bolt").getGeneratableElement();
				assertEquals("hit_block", projectile.onHitsBlock.getName());
				assertEquals("hit_player", projectile.onHitsPlayer.getName());
				assertEquals("hit_entity", projectile.onHitsEntity.getName());
				assertEquals("flying_tick", projectile.onFlyingTick.getName());

				Path source = workspace.getModElementByName("guided_bolt").getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java")).map(java.io.File::toPath).findFirst().orElseThrow();
				String generated = Files.readString(source);
				assertTrue(generated.contains("hit_blockProcedure.execute"));
				assertTrue(generated.contains("hit_playerProcedure.execute"));
				assertTrue(generated.contains("hit_entityProcedure.execute"));
				assertTrue(generated.contains("flying_tickProcedure.execute"));

				JsonObject clear = new JsonObject();
				clear.addProperty("path", "/onHitsBlock");
				clear.add("value", JsonNull.INSTANCE);
				JsonArray changes = new JsonArray();
				changes.add(clear);
				JsonObject update = new JsonObject();
				update.addProperty("clientMutationId", uuid(90).toString());
				update.addProperty("elementId", projectileId);
				update.add("changes", changes);
				var updated = session.uiEntry().execute(Command.of(uuid(91), session.workspaceId(), revision + 1,
						Operation.UPDATE_MOD_ELEMENT, update));
				assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());

				Projectile refreshed = (Projectile) workspace.getModElementByName("guided_bolt").getGeneratableElement();
				assertNull(refreshed.onHitsBlock);
				assertEquals("hit_entity", refreshed.onHitsEntity.getName());
				String regenerated = Files.readString(source);
				assertFalse(regenerated.contains("hit_blockProcedure.execute"));
				assertTrue(regenerated.contains("hit_entityProcedure.execute"));
			}
		}
	}

	private static Command create(UUID workspaceId, long revision, String type, String name, JsonObject values) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(20 + revision).toString());
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", values);
		return Command.of(uuid(40 + revision), workspaceId, revision, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
