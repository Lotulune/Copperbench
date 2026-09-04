package dev.copperbench.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.LegacyWorkspaceEntryAdapter;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceApplicationServiceTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T06:50:00Z"), ZoneOffset.UTC);
	private static final Gson GSON = UiCore.wireGson();

	@Test void twoEntryAdaptersUseTheSameQueryRules() {
		Fixture fixture = fixture();
		Query query = Query.of(uuid(90), WORKSPACE_ID, Operation.GET_WORKBENCH, new JsonObject());
		LegacyWorkspaceEntryAdapter legacy = new LegacyWorkspaceEntryAdapter(fixture.service);
		HeadlessWorkspaceEntryAdapter headless = new HeadlessWorkspaceEntryAdapter(fixture.service,
				PermissionProfile.WORKSPACE);

		assertEquals(GSON.toJsonTree(legacy.query(query)), GSON.toJsonTree(headless.query(query)));
	}

	@Test void stage12BiomeAndDimensionExposeCanonicalElementReferencePickers() {
		Fixture fixture = fixture();
		assertEquals("committed", fixture.service.execute(
				createTypedCommand(uuid(30), 0, "block", "copper_stone", new JsonObject()),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)).result().status());
		CommandOutcome biomeCreated = fixture.service.execute(
				createTypedCommand(uuid(31), 1, "biome", "copper_grove", new JsonObject()),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		assertEquals("committed", biomeCreated.result().status());
		String biomeId = biomeCreated.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString();
		CommandOutcome dimensionCreated = fixture.service.execute(
				createTypedCommand(uuid(32), 2, "dimension", "copper_realm", new JsonObject()),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		assertEquals("committed", dimensionCreated.result().status());
		String dimensionId = dimensionCreated.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString();

		JsonObject biomePayload = new JsonObject();
		biomePayload.addProperty("elementId", biomeId);
		JsonObject biomeEditor = fixture.service.query(Query.of(uuid(33), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, biomePayload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)).data().getAsJsonObject();
		JsonObject ground = findEditorField(biomeEditor, "/groundBlock");
		assertNotNull(ground);
		assertEquals("element_reference", ground.get("control").getAsString());
		assertEquals("Blocks.GRASS", ground.get("value").getAsString());
		assertTrue(ground.get("required").getAsBoolean());
		assertTrue(editorFieldHasOption(ground, "CUSTOM:copper_stone"));

		JsonObject dimensionPayload = new JsonObject();
		dimensionPayload.addProperty("elementId", dimensionId);
		JsonObject dimensionEditor = fixture.service.query(Query.of(uuid(34), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, dimensionPayload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)).data().getAsJsonObject();
		JsonObject filler = findEditorField(dimensionEditor, "/mainFillerBlock");
		assertNotNull(filler);
		assertEquals("element_reference", filler.get("control").getAsString());
		assertEquals("Blocks.STONE#0", filler.get("value").getAsString());
		assertTrue(editorFieldHasOption(filler, "CUSTOM:copper_stone"));

		JsonObject biomes = findEditorField(dimensionEditor, "/biomesInDimension");
		assertNotNull(biomes);
		assertEquals("element_reference_list", biomes.get("control").getAsString());
		assertTrue(biomes.get("value").isJsonArray());
		assertTrue(biomes.getAsJsonArray("value").isEmpty());
		assertTrue(biomes.get("required").getAsBoolean());
		assertTrue(editorFieldHasOption(biomes, "CUSTOM:copper_grove"));
		assertTrue(editorFieldHasOption(biomes, "#is_overworld"));
	}

	@Test void stage12LivingEntityEditorProvidesReferenceCandidatesAndSemanticGenerationImpact() {
		Fixture fixture = fixture();
		RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		JsonObject entityValues = new JsonObject();
		entityValues.addProperty("mobName", "copper_guardian");
		entityValues.addProperty("mobLabel", "Copper Guardian");
		entityValues.addProperty("health", 20);
		entityValues.addProperty("mobModelTexture", "textures/entity/copper_guardian.png");
		entityValues.addProperty("whenMobDies", "guardian_cleanup");
		CommandOutcome created = fixture.service.execute(
				createTypedCommand(uuid(31), 0, "livingentity", "copper_guardian", entityValues), context);
		String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();

		JsonObject procedureValues = new JsonObject();
		procedureValues.addProperty("procedurexml", "<xml xmlns=\"https://developers.google.com/blockly/xml\"></xml>");
		CommandOutcome procedure = fixture.service.execute(
				createTypedCommand(uuid(32), 1, "procedure", "guardian_cleanup", procedureValues), context);
		assertEquals("committed", procedure.result().status());

		JsonObject editorPayload = new JsonObject();
		editorPayload.addProperty("elementId", elementId);
		var editorResult = fixture.service.query(Query.of(uuid(33), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, editorPayload), context);
		JsonObject editor = editorResult.data().getAsJsonObject();
		JsonObject deathProcedure = findEditorField(editor, "/whenMobDies");
		assertNotNull(deathProcedure);
		assertEquals("procedure_reference", deathProcedure.get("control").getAsString());
		assertTrue(deathProcedure.getAsJsonArray("options").asList().stream()
				.anyMatch(option -> option.getAsJsonObject().get("value").getAsString().equals("guardian_cleanup")));

		JsonArray changes = new JsonArray();
		JsonObject health = new JsonObject();
		health.addProperty("path", "/health");
		health.addProperty("value", 32);
		changes.add(health);
		JsonObject texture = new JsonObject();
		texture.addProperty("path", "/mobModelTexture");
		texture.addProperty("value", "textures/entity/copper_guardian_alt.png");
		changes.add(texture);
		JsonObject previewPayload = new JsonObject();
		previewPayload.addProperty("elementId", elementId);
		previewPayload.add("changes", changes);

		var previewResult = fixture.service.query(Query.of(uuid(34), WORKSPACE_ID,
				Operation.PREVIEW_MOD_ELEMENT_CHANGE, previewPayload), context);
		JsonObject preview = previewResult.data().getAsJsonObject();
		assertEquals(2, preview.getAsJsonObject("semanticSummary").get("changedFieldCount").getAsInt());
		JsonArray sections = preview.getAsJsonObject("semanticSummary").getAsJsonArray("sections");
		assertTrue(sections.asList().stream().anyMatch(section -> section.getAsString().equals("attributes")));
		assertTrue(sections.asList().stream().anyMatch(section -> section.getAsString().equals("resources")));
		JsonObject impact = preview.getAsJsonObject("generationImpact");
		assertTrue(impact.get("requiresRegeneration").getAsBoolean());
		assertEquals("fabric-1.21.1", impact.get("generatorId").getAsString());
		assertTrue(impact.getAsJsonArray("affectedDomains").asList().stream()
				.anyMatch(domain -> domain.getAsString().equals("entity_definition")));
		assertTrue(impact.getAsJsonArray("affectedDomains").asList().stream()
				.anyMatch(domain -> domain.getAsString().equals("client_resources")));
	}

	@Test void legacyMcpAndHeadlessEntriesProduceTheSameElementResultAndEvent() {
		Fixture legacyFixture = fixture();
		Fixture mcpFixture = fixture();
		Fixture headlessFixture = fixture();
		Command command = createCommand(uuid(91), "signal_lantern");
		LegacyWorkspaceEntryAdapter legacy = new LegacyWorkspaceEntryAdapter(legacyFixture.service);
		McpWorkspaceEntryAdapter mcp = new McpWorkspaceEntryAdapter(mcpFixture.service,
				PermissionProfile.WORKSPACE);
		HeadlessWorkspaceEntryAdapter headless = new HeadlessWorkspaceEntryAdapter(headlessFixture.service,
				PermissionProfile.WORKSPACE);

		var legacyOutcome = GSON.toJsonTree(legacy.execute(command));
		assertEquals(legacyOutcome, GSON.toJsonTree(mcp.execute(command)));
		assertEquals(legacyOutcome, GSON.toJsonTree(headless.execute(command)));
	}

	@Test void concurrentCommandsBasedOnOneRevisionCommitExactlyOnce() throws Exception {
		Fixture fixture = fixture();
		Command first = createCommand(uuid(1), "signal_lantern");
		Command second = createCommand(uuid(2), "trail_marker");
		RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<CommandOutcome>> calls = List.of(() -> fixture.service.execute(first, context),
					() -> fixture.service.execute(second, context));
			List<Future<CommandOutcome>> futures = executor.invokeAll(calls);
			List<String> statuses = futures.stream().map(future -> {
				try {
					return future.get().result().status();
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
			}).toList();
			assertEquals(1, statuses.stream().filter("committed"::equals).count());
			assertEquals(1, statuses.stream().filter("rejected"::equals).count());
			assertEquals(1, fixture.store.read(WORKSPACE_ID).orElseThrow().revision());
			assertEquals(1, fixture.store.read(WORKSPACE_ID).orElseThrow().elements().size());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test void persistenceFailureRejectsTheCandidateWithoutAdvancingRevision() {
		RevisionedWorkspaceStore store = registeredStore();
		SequentialIds ids = new SequentialIds();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, ids), (_, _, _, _) -> {
					throw new IllegalStateException("simulated persistence failure");
				}, CLOCK, ids);

		CommandOutcome outcome = service.execute(createCommand(uuid(15), "failed_element"),
				new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE));

		assertEquals("rejected", outcome.result().status());
		var diagnostic = outcome.result().diagnostics().getFirst();
		assertEquals("WORKSPACE_PERSISTENCE_FAILED", diagnostic.code());
		assertDoesNotThrow(() -> UUID.fromString(diagnostic.message().args().get("failureId").getAsString()));
		assertFalse(diagnostic.message().fallback().contains("simulated persistence failure"));
		assertEquals("open_logs", diagnostic.actions().getFirst().kind());
		assertEquals(diagnostic.message().args().get("failureId").getAsString(),
				diagnostic.actions().getFirst().target());
		assertEquals(0, store.read(WORKSPACE_ID).orElseThrow().revision());
		assertTrue(store.read(WORKSPACE_ID).orElseThrow().elements().isEmpty());
		assertTrue(outcome.events().isEmpty());
	}

	@Test void datagenPublicationRollsBackStagedFilesWhenWorkspaceMetadataPersistenceFails() {
		RevisionedWorkspaceStore store = registeredStore();
		SequentialIds ids = new SequentialIds();
		DatagenTaskGateway tasks = new DatagenTaskGateway();
		WorkspaceMutationGateway failingPersistence = new WorkspaceMutationGateway() {
			@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation,
					WorkspaceState.Element affectedElement) {
			}

			@Override public void persistWorkspaceData(WorkspaceState before, WorkspaceState after,
					Operation operation) {
				throw new IllegalStateException("simulated datagen metadata failure");
			}
		};
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, failingPersistence,
				CLOCK, ids);
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(18).toString());
		payload.addProperty("taskId", DatagenTaskGateway.TASK_ID.toString());
		payload.addProperty("manifestHash", "a".repeat(64));

		CommandOutcome outcome = service.execute(Command.of(uuid(19), WORKSPACE_ID, 0,
				Operation.PUBLISH_DATAGEN_OUTPUT, payload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));

		assertEquals("rejected", outcome.result().status());
		assertEquals("WORKSPACE_PERSISTENCE_FAILED", outcome.result().diagnostics().getFirst().code());
		assertTrue(tasks.published);
		assertTrue(tasks.rolledBack);
		assertFalse(tasks.completed);
		assertEquals(0, store.read(WORKSPACE_ID).orElseThrow().revision());
	}

	@Test void staleWorkspaceCreationDoesNotWriteToDisk(@TempDir Path temporaryDirectory) {
		Fixture fixture = fixture();
		Path target = temporaryDirectory.resolve("must-not-be-created");
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(16).toString());
		payload.addProperty("generatorId", "fabric-1.21.1");
		payload.addProperty("modName", "Stale Workspace");
		payload.addProperty("modId", "stale_workspace");
		payload.addProperty("packageName", "dev.copperbench.stale");
		payload.addProperty("workspaceFolderPath", target.toString());
		payload.addProperty("userApproved", true);

		CommandOutcome outcome = fixture.service.execute(
				Command.of(uuid(17), WORKSPACE_ID, 1, Operation.CREATE_WORKSPACE, payload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));

		assertEquals("rejected", outcome.result().status());
		assertEquals("WORKSPACE_REVISION_CONFLICT", outcome.result().diagnostics().getFirst().code());
		assertFalse(Files.exists(target));
	}

	@Test void rejectedFieldUpdateLeavesRevisionAndValuesUntouched() {
		Fixture fixture = fixture();
		CommandOutcome created = fixture.service.execute(createCommand(uuid(3), "copper_lamp"),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		UUID elementId = UUID.fromString(created.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString());
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(4).toString());
		payload.addProperty("elementId", elementId.toString());
		JsonObject change = new JsonObject();
		change.addProperty("path", "/fields/hardness");
		change.addProperty("value", 101);
		JsonArray changes = new JsonArray();
		changes.add(change);
		payload.add("changes", changes);

		CommandOutcome outcome = fixture.service.execute(
				Command.of(uuid(5), WORKSPACE_ID, 1, Operation.UPDATE_MOD_ELEMENT, payload),
				new RequestContext(Actor.MCP, PermissionProfile.WORKSPACE));

		assertEquals("rejected", outcome.result().status());
		var diagnostic = outcome.result().diagnostics().getFirst();
		assertEquals("FIELD_VALUE_OUT_OF_RANGE", diagnostic.code());
		assertEquals(0, diagnostic.message().args().get("min").getAsInt());
		assertEquals(100, diagnostic.message().args().get("max").getAsInt());
		WorkspaceState state = fixture.store.read(WORKSPACE_ID).orElseThrow();
		assertEquals(1, state.revision());
		assertTrue(!state.element(elementId).values().has("fields"));
	}

	@Test void taskAcceptanceDoesNotAdvanceContentRevisionAndEventsAreMonotonic() {
		Fixture fixture = fixture();
		RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		CommandOutcome created = fixture.service.execute(createCommand(uuid(6), "signal_lantern"), context);
		JsonObject taskPayload = new JsonObject();
		taskPayload.addProperty("clientMutationId", uuid(7).toString());
		taskPayload.addProperty("scope", "workspace");
		CommandOutcome build = fixture.service.execute(
				Command.of(uuid(8), WORKSPACE_ID, 1, Operation.BUILD_WORKSPACE, taskPayload), context);

		assertEquals("accepted", build.result().status());
		assertEquals(1, build.result().newRevision());
		assertEquals(1, fixture.store.read(WORKSPACE_ID).orElseThrow().revision());
		assertNotEquals(created.events().getFirst().sequence(), build.events().getFirst().sequence());
		assertTrue(created.events().getFirst().sequence() < build.events().getFirst().sequence());
	}

	@Test void readOnlyEditorMarksFieldsAndCapabilitiesUnavailable() {
		Fixture fixture = fixture();
		CommandOutcome created = fixture.service.execute(createCommand(uuid(10), "signal_lantern"),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString();
		JsonObject payload = new JsonObject();
		payload.addProperty("elementId", elementId);

		var result = fixture.service.query(Query.of(uuid(11), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, payload),
				new RequestContext(Actor.HEADLESS, PermissionProfile.READ_ONLY));
		JsonObject editor = result.data().getAsJsonObject();
		JsonArray fields = editor.getAsJsonArray("sections").get(0).getAsJsonObject().getAsJsonArray("fields");

		assertTrue(fields.get(0).getAsJsonObject().get("readOnly").getAsBoolean());
		for (var capability : editor.getAsJsonArray("capabilities"))
			assertEquals("unavailable", capability.getAsJsonObject().get("availability").getAsString());
	}

	@Test void stage12LivingEntityEditorUsesDomainSectionsAndPreservesUnknownFields() {
		Fixture fixture = fixture();
		JsonObject initialValues = new JsonObject();
		initialValues.addProperty("mobModelName", "Biped");
		initialValues.addProperty("mobModelTexture", "zombie.png");
		initialValues.addProperty("health", 40);
		initialValues.addProperty("movementSpeed", 0.35);
		initialValues.addProperty("hasAI", true);
		initialValues.addProperty("equipmentMainHand", "Items.IRON_SWORD");
		initialValues.addProperty("spawnThisMob", true);
		initialValues.addProperty("spawningProbability", 20);
		initialValues.addProperty("onMobTickUpdate", "tick_entity");
		JsonObject future = new JsonObject();
		future.addProperty("enabled", true);
		initialValues.add("futureEntityTuning", future);

		CommandOutcome created = fixture.service.execute(
				createElementCommand(uuid(20), "livingentity", "copper_golem", initialValues),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString();
		JsonObject payload = new JsonObject();
		payload.addProperty("elementId", elementId);

		var result = fixture.service.query(Query.of(uuid(21), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, payload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		JsonArray sections = result.data().getAsJsonObject().getAsJsonArray("sections");

		assertEquals(9, sections.size());
		assertEquals("identity", sections.get(0).getAsJsonObject().get("id").getAsString());
		assertEquals("appearance", sections.get(1).getAsJsonObject().get("id").getAsString());
		assertEquals("resources", sections.get(2).getAsJsonObject().get("id").getAsString());
		assertEquals("attributes", sections.get(3).getAsJsonObject().get("id").getAsString());
		assertEquals("behavior", sections.get(4).getAsJsonObject().get("id").getAsString());
		assertEquals("equipment", sections.get(5).getAsJsonObject().get("id").getAsString());
		assertEquals("spawning", sections.get(6).getAsJsonObject().get("id").getAsString());
		assertEquals("events", sections.get(7).getAsJsonObject().get("id").getAsString());
		assertEquals("advanced", sections.get(8).getAsJsonObject().get("id").getAsString());

		JsonObject texture = editorField(sections, "/mobModelTexture");
		assertEquals("resource_reference", texture.get("control").getAsString());
		assertTrue(texture.get("required").getAsBoolean());
		JsonObject event = editorField(sections, "/onMobTickUpdate");
		assertEquals("procedure_reference", event.get("control").getAsString());
		JsonObject health = editorField(sections, "/health");
		assertEquals("number", health.get("control").getAsString());
		assertEquals(0, health.getAsJsonObject("constraints").get("min").getAsInt());
		assertEquals(1024, health.getAsJsonObject("constraints").get("max").getAsInt());
		assertEquals("advanced", editorSectionId(sections, "/futureEntityTuning/enabled"));
	}

	@Test void stage12P0TypesUseDepthSectionsWhileLegacyTypesKeepGeneralProjection() {
		for (String type : List.of("biome", "dimension", "gui")) {
			Fixture fixture = fixture();
			JsonObject initialValues = new JsonObject();
			switch (type) {
				case "biome" -> {
					initialValues.addProperty("temperature", 0.5);
					initialValues.addProperty("treeType", 0);
					initialValues.addProperty("villageType", "plains");
				}
				case "dimension" -> {
					initialValues.addProperty("seaLevel", 63);
					initialValues.addProperty("worldGenType", "Normal world gen");
					initialValues.addProperty("skyType", "NORMAL");
				}
				case "gui" -> {
					initialValues.addProperty("type", 0);
					initialValues.addProperty("width", 176);
					initialValues.addProperty("height", 166);
				}
				default -> throw new AssertionError(type);
			}
			CommandOutcome created = fixture.service.execute(
					createElementCommand(uuid(22), type, "depth_sample", initialValues),
					new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
			String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
					.get("id").getAsString();
			JsonObject payload = new JsonObject();
			payload.addProperty("elementId", elementId);
			JsonArray sections = fixture.service.query(Query.of(uuid(23), WORKSPACE_ID,
					Operation.GET_MOD_ELEMENT_EDITOR, payload),
					new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)).data().getAsJsonObject()
					.getAsJsonArray("sections");
			assertTrue(sections.size() > 1, type + " should expose Stage 12 depth sections");
			assertEquals("identity", sections.get(0).getAsJsonObject().get("id").getAsString());
			if (type.equals("biome")) {
				JsonObject temperature = editorField(sections, "/temperature");
				assertEquals(-1, temperature.getAsJsonObject("constraints").get("min").getAsInt());
				assertEquals(2, temperature.getAsJsonObject("constraints").get("max").getAsInt());
				JsonObject treeType = editorField(sections, "/treeType");
				assertEquals("select", treeType.get("control").getAsString());
				assertEquals(0, treeType.getAsJsonArray("options").get(0).getAsJsonObject().get("value").getAsInt());
			} else if (type.equals("dimension")) {
				JsonObject seaLevel = editorField(sections, "/seaLevel");
				assertEquals(-1024, seaLevel.getAsJsonObject("constraints").get("min").getAsInt());
				assertEquals(1024, seaLevel.getAsJsonObject("constraints").get("max").getAsInt());
				assertEquals("select", editorField(sections, "/worldGenType").get("control").getAsString());
			} else {
				JsonObject guiType = editorField(sections, "/type");
				assertEquals("select", guiType.get("control").getAsString());
				assertEquals(0, guiType.getAsJsonArray("options").get(0).getAsJsonObject().get("value").getAsInt());
				JsonObject width = editorField(sections, "/width");
				assertEquals(512, width.getAsJsonObject("constraints").get("max").getAsInt());
			}
		}

		Fixture legacyFixture = fixture();
		CommandOutcome block = legacyFixture.service.execute(createCommand(uuid(24), "legacy_block"),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		JsonObject payload = new JsonObject();
		payload.addProperty("elementId", block.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString());
		JsonArray sections = legacyFixture.service.query(Query.of(uuid(25), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, payload),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)).data().getAsJsonObject()
				.getAsJsonArray("sections");
		assertEquals(1, sections.size());
		assertEquals("general", sections.get(0).getAsJsonObject().get("id").getAsString());
	}

	@Test void stage12GuiCreationUsesCanonicalCanvasDefaults() {
		Fixture fixture = fixture();
		RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
		CommandOutcome created = fixture.service.execute(
				createElementCommand(uuid(26), "gui", "control_panel", new JsonObject()), context);
		assertEquals("committed", created.result().status());
		UUID elementId = UUID.fromString(created.result().data().getAsJsonObject().getAsJsonObject("element")
				.get("id").getAsString());
		JsonObject values = fixture.store.read(WORKSPACE_ID).orElseThrow().element(elementId).values();
		assertEquals(0, values.get("type").getAsInt());
		assertEquals(176, values.get("width").getAsInt());
		assertEquals(166, values.get("height").getAsInt());
		assertEquals(0, values.get("inventoryOffsetX").getAsInt());
		assertEquals(0, values.get("inventoryOffsetY").getAsInt());
		assertTrue(values.get("renderBgLayer").getAsBoolean());
		assertFalse(values.get("doesPauseGame").getAsBoolean());
		assertTrue(values.getAsJsonArray("components").isEmpty());

		JsonObject payload = new JsonObject();
		payload.addProperty("elementId", elementId.toString());
		JsonArray sections = fixture.service.query(Query.of(uuid(27), WORKSPACE_ID,
				Operation.GET_MOD_ELEMENT_EDITOR, payload), context).data().getAsJsonObject().getAsJsonArray("sections");
		assertEquals("components", editorSectionId(sections, "/components"));
		assertEquals("select", editorField(sections, "/type").get("control").getAsString());
	}

	@Test void taskStartAndContentMutationAreOrderedByTheWorkspaceLock() throws Exception {
		RevisionedWorkspaceStore store = registeredStore();
		BlockingTaskGateway gateway = new BlockingTaskGateway();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, gateway, CLOCK,
				new SequentialIds());
		JsonObject taskPayload = new JsonObject();
		taskPayload.addProperty("clientMutationId", uuid(12).toString());
		taskPayload.addProperty("scope", "workspace");
		Command build = Command.of(uuid(13), WORKSPACE_ID, 0, Operation.BUILD_WORKSPACE, taskPayload);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<CommandOutcome> buildFuture = executor.submit(() -> service.execute(build,
					new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)));
			assertTrue(gateway.entered.await(2, TimeUnit.SECONDS));
			Future<CommandOutcome> createFuture = executor.submit(() -> service.execute(
					createCommand(uuid(14), "trail_marker"),
					new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE)));

			assertFalse(createFuture.isDone());
			gateway.release.countDown();

			CommandOutcome buildOutcome = buildFuture.get(2, TimeUnit.SECONDS);
			CommandOutcome createOutcome = createFuture.get(2, TimeUnit.SECONDS);
			assertEquals("accepted", buildOutcome.result().status());
			assertEquals("committed", createOutcome.result().status());
			assertTrue(buildOutcome.events().getFirst().sequence() < createOutcome.events().getFirst().sequence());
		} finally {
			gateway.release.countDown();
			executor.shutdownNow();
		}
	}

	@Test void commandEnvelopeSerializesToUiCoreWireNames() {
		Command command = createCommand(uuid(9), "signal_lantern");
		JsonObject json = GSON.toJsonTree(command).getAsJsonObject();
		assertEquals("command", json.get("messageType").getAsString());
		assertEquals(UiCore.SCHEMA_VERSION, json.get("schemaVersion").getAsString());
		assertEquals("create_mod_element", json.get("operation").getAsString());
	}

	@Test void wireSerializerKeepsRequiredNullResultProperties() {
		Fixture fixture = fixture();
		CommandOutcome outcome = fixture.service.execute(createCommand(uuid(92), "signal_lantern"),
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		JsonObject json = GSON.toJsonTree(outcome.result()).getAsJsonObject();

		assertTrue(json.has("recoveryPointId") && json.get("recoveryPointId").isJsonNull());
		assertTrue(json.has("task") && json.get("task").isJsonNull());
		assertTrue(json.has("conflict") && json.get("conflict").isJsonNull());
		assertTrue(json.has("denial") && json.get("denial").isJsonNull());
	}

	private static Command createCommand(UUID requestId, String name) {
		return createElementCommand(requestId, "block", name, new JsonObject());
	}

	private static Command createElementCommand(UUID requestId, String type, String name, JsonObject initialValues) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(requestId.variant() + 20).toString());
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", initialValues.deepCopy());
		return Command.of(requestId, WORKSPACE_ID, 0, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static Command createTypedCommand(UUID requestId, long expectedRevision, String type, String name,
			JsonObject initialValues) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(500 + expectedRevision).toString());
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", initialValues.deepCopy());
		return Command.of(requestId, WORKSPACE_ID, expectedRevision, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static JsonObject findEditorField(JsonObject editor, String path) {
		for (var sectionRaw : editor.getAsJsonArray("sections")) {
			for (var fieldRaw : sectionRaw.getAsJsonObject().getAsJsonArray("fields")) {
				JsonObject field = fieldRaw.getAsJsonObject();
				if (field.get("path").getAsString().equals(path)) return field;
			}
		}
		return null;
	}

	private static boolean editorFieldHasOption(JsonObject field, String value) {
		for (var raw : field.getAsJsonArray("options")) {
			if (value.equals(raw.getAsJsonObject().get("value").getAsString())) return true;
		}
		return false;
	}

	private static JsonObject editorField(JsonArray sections, String path) {
		for (var rawSection : sections) {
			for (var rawField : rawSection.getAsJsonObject().getAsJsonArray("fields")) {
				JsonObject field = rawField.getAsJsonObject();
				if (path.equals(field.get("path").getAsString())) return field;
			}
		}
		throw new AssertionError("Editor field not found: " + path);
	}

	private static String editorSectionId(JsonArray sections, String path) {
		for (var rawSection : sections) {
			JsonObject section = rawSection.getAsJsonObject();
			for (var rawField : section.getAsJsonArray("fields")) {
				if (path.equals(rawField.getAsJsonObject().get("path").getAsString()))
					return section.get("id").getAsString();
			}
		}
		throw new AssertionError("Editor section not found for field: " + path);
	}

	private static Fixture fixture() {
		RevisionedWorkspaceStore store = registeredStore();
		SequentialIds supplier = new SequentialIds();
		InMemoryWorkspaceTaskGateway tasks = new InMemoryWorkspaceTaskGateway(CLOCK, supplier);
		return new Fixture(store, new WorkspaceApplicationService(store, tasks, CLOCK, supplier));
	}

	private static RevisionedWorkspaceStore registeredStore() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of()));
		return store;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}

	private record Fixture(RevisionedWorkspaceStore store, WorkspaceApplicationService service) {
	}

	private static final class SequentialIds implements Supplier<UUID> {
		private final Queue<UUID> ids = new ArrayDeque<>();

		private SequentialIds() {
			for (int index = 100; index < 180; index++)
				ids.add(uuid(index));
		}

		@Override public synchronized UUID get() {
			return ids.remove();
		}
	}

	private static final class BlockingTaskGateway implements WorkspaceTaskGateway {
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final InMemoryWorkspaceTaskGateway delegate = new InMemoryWorkspaceTaskGateway(CLOCK,
				new SequentialIds());

		@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
			entered.countDown();
			try {
				if (!release.await(2, TimeUnit.SECONDS))
					throw new IllegalStateException("Timed out waiting to release task start");
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			return delegate.start(workspaceId, operation, payload);
		}

		@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
			return delegate.find(workspaceId, taskId);
		}

		@Override public List<JsonObject> active(UUID workspaceId) {
			return delegate.active(workspaceId);
		}

		@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
			return delegate.cancel(workspaceId, taskId);
		}
	}

	private static final class DatagenTaskGateway implements WorkspaceTaskGateway {
		private static final UUID TASK_ID = UUID.fromString("00000000-0000-4000-8000-000000000099");
		private boolean published;
		private boolean rolledBack;
		private boolean completed;

		@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
			throw new UnsupportedOperationException();
		}

		@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
			return Optional.empty();
		}

		@Override public List<JsonObject> active(UUID workspaceId) {
			return List.of();
		}

		@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
			return Optional.empty();
		}

		@Override public JsonObject publishDatagen(UUID workspaceId, UUID taskId, JsonObject payload) {
			assertEquals(TASK_ID, taskId);
			published = true;
			JsonObject data = new JsonObject();
			data.addProperty("taskId", taskId.toString());
			data.addProperty("manifestHash", "a".repeat(64));
			var changed = new JsonArray();
			changed.add("src/generated/resources/data/copper_trails/generated.json");
			data.add("changedPaths", changed);
			return data;
		}

		@Override public void completeDatagenPublish(UUID workspaceId, UUID taskId) {
			completed = true;
		}

		@Override public void rollbackDatagenPublish(UUID workspaceId, UUID taskId) {
			rolledBack = true;
		}
	}
}
