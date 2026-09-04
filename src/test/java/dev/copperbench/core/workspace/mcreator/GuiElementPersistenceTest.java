package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.types.GUI;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiElementPersistenceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T02:00:00Z"), ZoneOffset.UTC);
	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void createsAndUpdatesGuiComponentsThroughTheCanonicalComponentArray() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("gui_depth");
		settings.setModName("GUI Depth");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("gui_depth.mcreator");
		AtomicLong ids = new AtomicLong(100);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings);
				MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
						UUID.fromString("22222222-2222-4222-8222-222222222222"),
						new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
						() -> uuid(ids.incrementAndGet()))) {
			JsonObject values = new JsonObject();
			values.addProperty("type", 0);
			values.addProperty("width", 176);
			values.addProperty("height", 166);
			values.add("components", components(174));

			var created = session.uiEntry().execute(create(session.workspaceId(), values));
			assertEquals("committed", created.result().status());
			UUID elementId = UUID.fromString(created.result().data().getAsJsonObject()
					.getAsJsonObject("element").get("id").getAsString());

			GUI stored = assertInstanceOf(GUI.class,
					workspace.getModElementByName("control_panel").getGeneratableElement());
			assertEquals(176, stored.width);
			assertEquals(166, stored.height);
			assertEquals(1, stored.components.size());
			Button first = assertInstanceOf(Button.class, stored.components.getFirst());
			assertEquals("button_1", first.name);
			assertEquals("Button", first.text);
			assertEquals(174, first.x);
			assertEquals(110, first.y);

			JsonObject updatePayload = new JsonObject();
			updatePayload.addProperty("clientMutationId", uuid(31).toString());
			updatePayload.addProperty("elementId", elementId.toString());
			JsonObject componentChange = new JsonObject();
			componentChange.addProperty("path", "/components");
			componentChange.add("value", components(200));
			JsonArray changes = new JsonArray();
			changes.add(componentChange);
			updatePayload.add("changes", changes);
			var updated = session.uiEntry().execute(Command.of(uuid(32), session.workspaceId(), 1,
					Operation.UPDATE_MOD_ELEMENT, updatePayload));
			assertEquals("committed", updated.result().status());

			GUI updatedGui = assertInstanceOf(GUI.class,
					workspace.getModElementByName("control_panel").getGeneratableElement());
			Button moved = assertInstanceOf(Button.class, updatedGui.components.getFirst());
			assertEquals(200, moved.x);
			assertEquals("button_1", moved.name);
			assertTrue(Files.isRegularFile(root.resolve("elements/control_panel.mod.json")));
		}
	}

	private static JsonArray components(int x) {
		JsonObject data = new JsonObject();
		data.add("anchorPoint", JsonNull.INSTANCE);
		data.addProperty("x", x);
		data.addProperty("y", 110);
		data.addProperty("locked", false);
		data.addProperty("width", 80);
		data.addProperty("height", 20);
		data.addProperty("name", "button_1");
		data.addProperty("text", "Button");
		data.addProperty("isUndecorated", false);
		data.add("onClick", JsonNull.INSTANCE);
		data.add("displayCondition", JsonNull.INSTANCE);
		JsonObject button = new JsonObject();
		button.addProperty("type", "button");
		button.add("data", data);
		JsonArray components = new JsonArray();
		components.add(button);
		return components;
	}

	private static Command create(UUID workspaceId, JsonObject values) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(10).toString());
		payload.addProperty("elementType", "gui");
		payload.addProperty("name", "control_panel");
		payload.add("initialValues", values);
		return Command.of(uuid(20), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
