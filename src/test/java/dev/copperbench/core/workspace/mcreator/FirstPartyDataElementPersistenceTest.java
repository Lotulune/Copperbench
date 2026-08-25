package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Achievement;
import net.mcreator.element.types.Function;
import net.mcreator.element.types.LootTable;
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

class FirstPartyDataElementPersistenceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);
	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void createsFunctionLootTableAndAdvancementAsRealUpstreamDefinitions() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("data_slice");
		settings.setModName("Data Slice");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("data_slice.mcreator");
		AtomicLong ids = new AtomicLong(100);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings);
				MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
						UUID.fromString("11111111-1111-4111-8111-111111111111"),
						new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
						() -> uuid(ids.incrementAndGet()))) {
			JsonObject function = new JsonObject();
			function.addProperty("namespace", "mod");
			JsonArray commands = new JsonArray();
			commands.add("say Copperbench ready");
			commands.add("time set day");
			function.add("commands", commands);
			assertEquals("committed", session.uiEntry().execute(create(session.workspaceId(), 0, "function",
					"bootstrap", function)).result().status());

			JsonObject loot = new JsonObject();
			loot.addProperty("type", "Generic");
			JsonObject entry = new JsonObject();
			entry.addProperty("item", "Blocks.STONE");
			entry.addProperty("weight", 2);
			entry.addProperty("minCount", 1);
			entry.addProperty("maxCount", 3);
			JsonArray entries = new JsonArray();
			entries.add(entry);
			JsonObject pool = new JsonObject();
			pool.addProperty("minRolls", 1);
			pool.addProperty("maxRolls", 2);
			pool.add("entries", entries);
			JsonArray pools = new JsonArray();
			pools.add(pool);
			loot.add("pools", pools);
			var lootCreated = session.uiEntry().execute(create(session.workspaceId(), 1, "loottable",
					"trail_cache", loot));
			assertEquals("committed", lootCreated.result().status());
			UUID lootId = UUID.fromString(lootCreated.result().data().getAsJsonObject()
					.getAsJsonObject("element").get("id").getAsString());

			JsonObject advancement = new JsonObject();
			advancement.addProperty("title", "Trail Ready");
			advancement.addProperty("description", "Run the bootstrap function");
			advancement.addProperty("rewardFunction", "bootstrap");
			assertEquals("committed", session.uiEntry().execute(create(session.workspaceId(), 2, "achievement",
					"trail_ready", advancement)).result().status());

			Function storedFunction = assertInstanceOf(Function.class,
					workspace.getModElementByName("bootstrap").getGeneratableElement());
			assertEquals("say Copperbench ready\ntime set day\n", storedFunction.code);
			LootTable storedLoot = assertInstanceOf(LootTable.class,
					workspace.getModElementByName("trail_cache").getGeneratableElement());
			assertEquals(1, storedLoot.pools.size());
			assertEquals(3, storedLoot.pools.getFirst().entries.getFirst().maxCount);
			Achievement storedAdvancement = assertInstanceOf(Achievement.class,
					workspace.getModElementByName("trail_ready").getGeneratableElement());
			assertEquals("Trail Ready", storedAdvancement.achievementName);
			assertEquals("bootstrap", storedAdvancement.rewardFunction);
			assertTrue(storedAdvancement.triggerxml.contains("advancement_trigger"));
			assertTrue(Files.isRegularFile(root.resolve("elements/bootstrap.mod.json")));
			assertTrue(Files.isRegularFile(root.resolve("elements/trail_cache.mod.json")));
			assertTrue(Files.isRegularFile(root.resolve("elements/trail_ready.mod.json")));

			JsonObject editorPayload = new JsonObject();
			editorPayload.addProperty("elementId", lootId.toString());
			var editor = session.uiEntry().query(Query.of(uuid(40), session.workspaceId(),
					Operation.GET_MOD_ELEMENT_EDITOR, editorPayload));
			JsonArray fields = editor.data().getAsJsonObject().getAsJsonArray("sections")
					.get(0).getAsJsonObject().getAsJsonArray("fields");
			boolean poolsJsonEditor = false;
			for (var raw : fields) {
				JsonObject field = raw.getAsJsonObject();
				if (field.get("path").getAsString().equals("/pools")) {
					assertEquals("json", field.get("control").getAsString());
					assertTrue(field.get("value").isJsonArray());
					poolsJsonEditor = true;
				}
			}
			assertTrue(poolsJsonEditor, "Loot Table pools must remain a structured JSON array in the new UI contract");
		}
	}

	private static Command create(UUID workspaceId, long revision, String type, String name, JsonObject values) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(10 + revision).toString());
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", values);
		return Command.of(uuid(20 + revision), workspaceId, revision, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
