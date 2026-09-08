package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.parts.gui.EntityModel;
import net.mcreator.element.parts.gui.Image;
import net.mcreator.element.parts.gui.Label;
import net.mcreator.element.parts.gui.Sprite;
import net.mcreator.element.types.Overlay;
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

class OverlayElementPersistenceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZoneOffset.UTC);
	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void persistsAllUpstreamOverlayComponentTypesAndGridSettings() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("overlay_depth");
		settings.setModName("Overlay Depth");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("overlay_depth.mcreator");
		AtomicLong ids = new AtomicLong(100);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings);
				MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
						UUID.fromString("33333333-3333-4333-8333-333333333333"),
						new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
						() -> uuid(ids.incrementAndGet()))) {
			JsonObject values = new JsonObject();
			values.addProperty("priority", "HIGH");
			values.addProperty("baseTexture", "");
			values.addProperty("overlayTarget", "Ingame");
			values.add("displayCondition", JsonNull.INSTANCE);
			values.add("gridSettings", grid(18));
			values.add("components", components(120));

			var created = session.uiEntry().execute(create(session.workspaceId(), values));
			assertEquals("committed", created.result().status());
			UUID elementId = UUID.fromString(created.result().data().getAsJsonObject()
					.getAsJsonObject("element").get("id").getAsString());

			Overlay stored = assertInstanceOf(Overlay.class,
					workspace.getModElementByName("copper_hud").getGeneratableElement());
			assertEquals("HIGH", stored.priority);
			assertEquals("Ingame", stored.overlayTarget.getUnmappedValue());
			assertEquals(18, stored.gridSettings.sx);
			assertEquals(11, stored.gridSettings.ox);
			assertFalse(stored.gridSettings.snapOnGrid);
			assertEquals(4, stored.components.size());

			Label label = assertInstanceOf(Label.class, stored.components.get(0));
			assertEquals("overlay_label", label.name);
			assertEquals("Copper HUD", label.text.getFixedValue());
			assertTrue(label.hasShadow);
			Image image = assertInstanceOf(Image.class, stored.components.get(1));
			assertEquals("picture1", image.image);
			assertFalse(image.use1Xscale);
			Sprite sprite = assertInstanceOf(Sprite.class, stored.components.get(2));
			assertEquals("picture1", sprite.sprite);
			assertEquals(4, sprite.spritesCount);
			assertEquals(2.0, sprite.spriteIndex.getFixedValue());
			EntityModel entityModel = assertInstanceOf(EntityModel.class, stored.components.get(3));
			assertEquals("entity_provider", entityModel.entityModel.getName());
			assertEquals(30, entityModel.scale);
			assertTrue(entityModel.followMouseMovement);

			JsonObject updatePayload = new JsonObject();
			updatePayload.addProperty("clientMutationId", uuid(31).toString());
			updatePayload.addProperty("elementId", elementId.toString());
			JsonArray changes = new JsonArray();
			JsonObject componentChange = new JsonObject();
			componentChange.addProperty("path", "/components");
			componentChange.add("value", components(160));
			changes.add(componentChange);
			JsonObject gridChange = new JsonObject();
			gridChange.addProperty("path", "/gridSettings/sx");
			gridChange.addProperty("value", 20);
			changes.add(gridChange);
			JsonObject snapChange = new JsonObject();
			snapChange.addProperty("path", "/gridSettings/snapOnGrid");
			snapChange.addProperty("value", true);
			changes.add(snapChange);
			updatePayload.add("changes", changes);
			var updated = session.uiEntry().execute(Command.of(uuid(32), session.workspaceId(), 1,
					Operation.UPDATE_MOD_ELEMENT, updatePayload));
			assertEquals("committed", updated.result().status());

			Overlay updatedOverlay = assertInstanceOf(Overlay.class,
					workspace.getModElementByName("copper_hud").getGeneratableElement());
			assertEquals(160, updatedOverlay.components.getFirst().x);
			assertEquals(20, updatedOverlay.gridSettings.sx);
			assertTrue(updatedOverlay.gridSettings.snapOnGrid);
			assertTrue(Files.isRegularFile(root.resolve("elements/copper_hud.mod.json")));
		}
	}

	private static JsonObject grid(int sx) {
		JsonObject grid = new JsonObject();
		grid.addProperty("sx", sx);
		grid.addProperty("sy", 18);
		grid.addProperty("ox", 11);
		grid.addProperty("oy", 15);
		grid.addProperty("snapOnGrid", false);
		return grid;
	}

	private static JsonArray components(int x) {
		JsonArray components = new JsonArray();
		components.add(label(x));
		components.add(image());
		components.add(sprite());
		components.add(entityModel());
		return components;
	}

	private static JsonObject label(int x) {
		JsonObject text = new JsonObject();
		text.add("name", JsonNull.INSTANCE);
		text.addProperty("fixedValue", "Copper HUD");
		JsonObject color = new JsonObject();
		color.addProperty("value", -1);
		color.addProperty("falpha", 0.0f);
		JsonObject data = common(x, 70);
		data.addProperty("name", "overlay_label");
		data.add("text", text);
		data.add("color", color);
		data.addProperty("hasShadow", true);
		data.add("displayCondition", JsonNull.INSTANCE);
		return component("label", data);
	}

	private static JsonObject image() {
		JsonObject data = common(140, 80);
		data.addProperty("image", "picture1");
		data.addProperty("use1Xscale", false);
		data.add("displayCondition", JsonNull.INSTANCE);
		return component("image", data);
	}

	private static JsonObject sprite() {
		JsonObject index = new JsonObject();
		index.add("name", JsonNull.INSTANCE);
		index.addProperty("fixedValue", 2.0);
		JsonObject data = common(160, 90);
		data.addProperty("sprite", "picture1");
		data.addProperty("spritesCount", 4);
		data.add("displayCondition", JsonNull.INSTANCE);
		data.add("spriteIndex", index);
		return component("sprite", data);
	}

	private static JsonObject entityModel() {
		JsonObject data = common(180, 100);
		data.addProperty("entityModel", "entity_provider");
		data.add("displayCondition", JsonNull.INSTANCE);
		data.addProperty("scale", 30);
		data.addProperty("rotationX", 0);
		data.addProperty("followMouseMovement", true);
		return component("entitymodel", data);
	}

	private static JsonObject common(int x, int y) {
		JsonObject data = new JsonObject();
		data.add("anchorPoint", JsonNull.INSTANCE);
		data.addProperty("x", x);
		data.addProperty("y", y);
		data.addProperty("locked", false);
		return data;
	}

	private static JsonObject component(String type, JsonObject data) {
		JsonObject component = new JsonObject();
		component.addProperty("type", type);
		component.add("data", data);
		return component;
	}

	private static Command create(UUID workspaceId, JsonObject values) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(10).toString());
		payload.addProperty("elementType", "overlay");
		payload.addProperty("name", "copper_hud");
		payload.add("initialValues", values);
		return Command.of(uuid(20), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
