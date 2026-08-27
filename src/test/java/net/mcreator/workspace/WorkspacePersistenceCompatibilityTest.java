package net.mcreator.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceWriteLockedException;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.LegacyWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.workspace.UnknownFieldPreservingJsonStore;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.ModElementType;
import net.mcreator.element.types.Function;
import net.mcreator.generator.Generator;
import net.mcreator.io.zip.ZipIO;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePersistenceCompatibilityTest {

	@TempDir Path temporaryDirectory;

	@BeforeAll static void initializeUpstreamRuntimeMinimum() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	private static JsonObject workspacePlanPayload(long revision, String idempotencyKey, JsonObject... steps) {
		JsonObject payload = new JsonObject();
		payload.addProperty("expectedRevision", revision);
		payload.addProperty("idempotencyKey", idempotencyKey);
		JsonArray operations = new JsonArray();
		for (JsonObject step : steps) operations.add(step);
		payload.add("operations", operations);
		return payload;
	}

	private static JsonObject workspacePlanCreate(String type, String name) {
		JsonObject payload = new JsonObject();
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", new JsonObject());
		JsonObject step = new JsonObject();
		step.addProperty("operation", "create_mod_element");
		step.add("payload", payload);
		return step;
	}

	@Test void upstreamBackedWorkspacePlanPersistsMultipleElementsUnderOneRevision() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("workspace_plan_persistence");
		settings.setModName("Workspace Plan Persistence");
		settings.setCurrentGenerator("neoforge-1.21.8");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("workspace_plan_persistence.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(700);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
				store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
			JsonObject planPayload = workspacePlanPayload(0, "persist-two-elements",
					workspacePlanCreate("item", "atomic_item"), workspacePlanCreate("block", "atomic_block"));
			var planned = entry.query(Query.of(ids.get(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES,
					planPayload));
			assertEquals("succeeded", planned.status(), planned.diagnostics().toString());

			JsonObject applyPayload = new JsonObject();
			applyPayload.addProperty("clientMutationId", ids.get().toString());
			applyPayload.add("plan", planned.data().deepCopy());
			var applied = entry.execute(Command.of(ids.get(), session.workspaceId(), 0,
					Operation.APPLY_WORKSPACE_PLAN, applyPayload));

			assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
			assertEquals(1, applied.result().newRevision());
			assertTrue(workspace.containsModElement("atomic_item"));
			assertTrue(workspace.containsModElement("atomic_block"));
			assertTrue(Files.isRegularFile(temporaryDirectory.resolve("elements/atomic_item.mod.json")));
			assertTrue(Files.isRegularFile(temporaryDirectory.resolve("elements/atomic_block.mod.json")));
			JsonObject root = new UnknownFieldPreservingJsonStore().read(
					workspace.getFileManager().getWorkspaceFile().toPath());
			assertEquals(1, root.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE)
					.get("revision").getAsLong());
			workspace.reloadFromFileSystem();
			assertNotNull(workspace.getModElementByName("atomic_item"));
			assertNotNull(workspace.getModElementByName("atomic_block"));
		} finally {
			workspace.close();
		}
	}

	@Test void upstreamBackedWorkspacePlanRestoresAllFilesWhenFinalRevisionCommitConflicts() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("workspace_plan_rollback");
		settings.setModName("Workspace Plan Rollback");
		settings.setCurrentGenerator("neoforge-1.21.8");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("workspace_plan_rollback.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(750);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
				store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
			JsonObject planPayload = workspacePlanPayload(0, "rollback-two-elements",
					workspacePlanCreate("item", "rolled_back_item"),
					workspacePlanCreate("block", "rolled_back_block"));
			var planned = entry.query(Query.of(ids.get(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES,
					planPayload));
			assertEquals("succeeded", planned.status(), planned.diagnostics().toString());

			workspace.getFileManager().advanceProductRevision(session.workspaceId(), 0);
			JsonObject applyPayload = new JsonObject();
			applyPayload.addProperty("clientMutationId", ids.get().toString());
			applyPayload.add("plan", planned.data().deepCopy());
			var applied = entry.execute(Command.of(ids.get(), session.workspaceId(), 0,
					Operation.APPLY_WORKSPACE_PLAN, applyPayload));

			assertEquals("rejected", applied.result().status());
			assertEquals("WORKSPACE_PLAN_PERSISTENCE_FAILED", applied.result().diagnostics().getFirst().code());
			assertFalse(workspace.containsModElement("rolled_back_item"));
			assertFalse(workspace.containsModElement("rolled_back_block"));
			assertFalse(Files.exists(temporaryDirectory.resolve("elements/rolled_back_item.mod.json")));
			assertFalse(Files.exists(temporaryDirectory.resolve("elements/rolled_back_block.mod.json")));
			JsonObject root = new UnknownFieldPreservingJsonStore().read(
					workspace.getFileManager().getWorkspaceFile().toPath());
			assertEquals(1, root.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE)
					.get("revision").getAsLong());
		} finally {
			workspace.close();
		}
	}

	@Test void sessionTaskFactoryReceivesTheRegisteredWorkspaceStore() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("shared_store_test");
		settings.setModName("Shared Store Test");
		settings.setCurrentGenerator("neoforge-1.21.8");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("shared_store_test.mcreator").toFile(), settings);
		AtomicReference<RevisionedWorkspaceStore> taskStore = new AtomicReference<>();
		AtomicLong sequence = new AtomicLong(500);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, store -> {
				taskStore.set(store);
				return new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids);
			}, Clock.systemUTC(), ids)) {
				assertTrue(taskStore.get().read(session.workspaceId()).isPresent());
			}
		} finally {
			workspace.close();
		}
	}

	@Test void realWorkspaceManagerEnforcesOneWriterAndReleasesItOnAbort() {
		Path workspaceFile = temporaryDirectory.resolve("test_mod.mcreator");
		Workspace firstWorkspace = workspace();
		WorkspaceFileManager first = new WorkspaceFileManager(workspaceFile.toFile(), firstWorkspace);
		firstWorkspace.fileManager = first;
		try {
			assertThrows(WorkspaceWriteLockedException.class,
					() -> new WorkspaceFileManager(workspaceFile.toFile(), workspace()));
		} finally {
			first.abortOpen();
		}

		WorkspaceFileManager reopened = new WorkspaceFileManager(workspaceFile.toFile(), workspace());
		reopened.abortOpen();
	}

	@Test void realWorkspaceAndModElementSavesPreserveUnknownFields() throws Exception {
		Path workspaceFile = temporaryDirectory.resolve("test_mod.mcreator");
		Workspace workspace = workspace();
		JsonObject initialWorkspace = WorkspaceFileManager.gson.toJsonTree(workspace).getAsJsonObject();
		JsonObject futureWorkspaceData = new JsonObject();
		futureWorkspaceData.addProperty("opaque", "keep-workspace");
		initialWorkspace.add("plugin.future", futureWorkspaceData);
		Files.writeString(workspaceFile, WorkspaceFileManager.gson.toJson(initialWorkspace), StandardCharsets.UTF_8);

		WorkspaceFileManager manager = new WorkspaceFileManager(workspaceFile.toFile(), workspace);
		workspace.fileManager = manager;
		workspace.getWorkspaceSettings().setWorkspace(workspace);
		try {
			workspace.markDirty();
			manager.saveWorkspaceDirectlyAndWait();
			JsonObject savedWorkspace = JsonParser.parseString(Files.readString(workspaceFile)).getAsJsonObject();
			assertEquals("keep-workspace", savedWorkspace.getAsJsonObject("plugin.future")
					.get("opaque").getAsString());

			ModElement modElement = new ModElement(workspace, "CompatibilityFunction", ModElementType.FUNCTION);
			Function function = new Function(modElement);
			function.name = "compatibility_function";
			function.namespace = "mod";
			function.code = "first";
			manager.getModElementManager().storeModElement(function);
			Path definitionFile = temporaryDirectory.resolve("elements/CompatibilityFunction.mod.json");
			JsonObject definition = JsonParser.parseString(Files.readString(definitionFile)).getAsJsonObject();
			definition.addProperty("pluginOpaque", "keep-element");
			Files.writeString(definitionFile, WorkspaceFileManager.gson.toJson(definition), StandardCharsets.UTF_8);

			function.code = "second";
			manager.getModElementManager().storeModElement(function);
			JsonObject savedDefinition = JsonParser.parseString(Files.readString(definitionFile)).getAsJsonObject();
			assertEquals("keep-element", savedDefinition.get("pluginOpaque").getAsString());
		} finally {
			manager.abortOpen();
		}
	}

	@Test void upstreamGoldenWorkspaceRoundTripPreservesUnknownRootAndElementFields() throws Exception {
		Path archive = Path.of(getClass().getResource("/workspaces/test-2026.1.zip").toURI());
		ZipIO.unzip(archive.toString(), temporaryDirectory.toString());
		Path workspaceFile = temporaryDirectory.resolve("test.mcreator");
		JsonObject document = JsonParser.parseString(Files.readString(workspaceFile)).getAsJsonObject();
		document.addProperty("futureWorkspaceField", "keep-root");
		document.getAsJsonArray("mod_elements").get(0).getAsJsonObject()
				.addProperty("futureElementSummaryField", "keep-summary");
		Files.writeString(workspaceFile, WorkspaceFileManager.gson.toJson(document), StandardCharsets.UTF_8);

		Workspace workspace = WorkspaceFileManager.gson.fromJson(Files.readString(workspaceFile), Workspace.class);
		WorkspaceFileManager manager = new WorkspaceFileManager(workspaceFile.toFile(), workspace);
		workspace.fileManager = manager;
		workspace.getWorkspaceSettings().setWorkspace(workspace);
		try {
			workspace.markDirty();
			manager.saveWorkspaceDirectlyAndWait();
		} finally {
			manager.abortOpen();
		}

		JsonObject saved = JsonParser.parseString(Files.readString(workspaceFile)).getAsJsonObject();
		assertEquals("keep-root", saved.get("futureWorkspaceField").getAsString());
		assertEquals("keep-summary", saved.getAsJsonArray("mod_elements").get(0).getAsJsonObject()
				.get("futureElementSummaryField").getAsString());
	}

	@Test void upstreamBackedSessionPersistsProcedureCrudAcrossLegacyAndHeadlessEntries() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("session_test");
		settings.setModName("Session Test");
		settings.setCurrentGenerator("neoforge-1.21.8");
		Workspace workspace = Workspace.createWorkspace(temporaryDirectory.resolve("session_test.mcreator").toFile(),
				settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111177");
		AtomicLong sequence = new AtomicLong(200);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
					new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids);
			LegacyWorkspaceEntryAdapter legacy = session.legacyEntry();
			HeadlessWorkspaceEntryAdapter headless = session.headlessEntry(PermissionProfile.WORKSPACE);

			JsonObject createPayload = new JsonObject();
			createPayload.addProperty("clientMutationId", uuid(1).toString());
			createPayload.addProperty("elementType", "procedure");
			createPayload.addProperty("name", "session_procedure");
			JsonObject initialValues = new JsonObject();
			initialValues.addProperty("procedurexml", emptyProcedureXml("no_ext_trigger"));
			createPayload.add("initialValues", initialValues);
			var created = legacy.execute(Command.of(uuid(2), workspaceId, 0,
					Operation.CREATE_MOD_ELEMENT, createPayload));
			assertEquals("committed", created.result().status());
			String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
					.get("id").getAsString();
			assertTrue(workspace.containsModElement("session_procedure"));

			JsonObject updatePayload = new JsonObject();
			updatePayload.addProperty("clientMutationId", uuid(3).toString());
			updatePayload.addProperty("elementId", elementId);
			JsonObject change = new JsonObject();
			change.addProperty("path", "/procedurexml");
			change.addProperty("value", emptyProcedureXml("no_ext_trigger_updated"));
			com.google.gson.JsonArray changes = new com.google.gson.JsonArray();
			changes.add(change);
			updatePayload.add("changes", changes);
			var updated = headless.execute(Command.of(uuid(4), workspaceId, 1,
					Operation.UPDATE_MOD_ELEMENT, updatePayload));
			assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
			Path definition = temporaryDirectory.resolve("elements/session_procedure.mod.json");
			assertTrue(Files.readString(definition).contains("no_ext_trigger_updated"));

			JsonObject deletePayload = new JsonObject();
			deletePayload.addProperty("clientMutationId", uuid(5).toString());
			deletePayload.addProperty("elementId", elementId);
			Generator generator = workspace.generator;
			workspace.generator = null; // Exercise definition deletion without generator-side generated files.
			dev.copperbench.core.contract.UiCore.CommandOutcome deleted;
			try {
				deleted = legacy.execute(Command.of(uuid(6), workspaceId, 2,
						Operation.DELETE_MOD_ELEMENT, deletePayload));
			} finally {
				workspace.generator = generator;
			}
			assertEquals("committed", deleted.result().status());
			assertFalse(workspace.containsModElement("session_procedure"));
			assertFalse(Files.exists(definition));

			JsonObject root = new UnknownFieldPreservingJsonStore().read(
					workspace.getFileManager().getWorkspaceFile().toPath());
			assertEquals(3, root.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE)
					.get("revision").getAsLong());
		} finally {
			workspace.close();
		}
	}

	@Test void upstreamBackedSessionPersistsEveryFirstPartyElementType() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("first_party_slice_test");
		settings.setModName("First Party Slice Test");
		settings.setCurrentGenerator("neoforge-1.21.8");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("first_party_slice_test.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111180");
		AtomicLong sequence = new AtomicLong(1000);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
					new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids);
			var entry = session.headlessEntry(PermissionProfile.WORKSPACE);
			List<String> types = List.of("block", "item", "recipe", "procedure");
			List<ModElementType<?>> upstreamTypes = List.of(ModElementType.BLOCK, ModElementType.ITEM,
					ModElementType.RECIPE, ModElementType.PROCEDURE);
			long revision = 0;
			for (int index = 0; index < types.size(); index++) {
				String type = types.get(index);
				String name = "session_" + type;
				JsonObject createPayload = new JsonObject();
				createPayload.addProperty("clientMutationId", ids.get().toString());
				createPayload.addProperty("elementType", type);
				createPayload.addProperty("name", name);
				JsonObject values = new JsonObject();
				values.addProperty("source", "compatibility-test");
				if (type.equals("procedure")) values.addProperty("procedurexml", emptyProcedureXml("no_ext_trigger"));
				createPayload.add("initialValues", values);

				var created = entry.execute(Command.of(ids.get(), workspaceId, revision,
						Operation.CREATE_MOD_ELEMENT, createPayload));
				assertEquals("committed", created.result().status());
				revision++;
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				ModElement upstream = workspace.getModElementByName(name);
				assertNotNull(upstream);
				assertEquals(upstreamTypes.get(index), upstream.getType());
				assertNotNull(upstream.getGeneratableElement());
				assertTrue(Files.isRegularFile(temporaryDirectory.resolve("elements/" + name + ".mod.json")));

				JsonObject updatePayload = new JsonObject();
				updatePayload.addProperty("clientMutationId", ids.get().toString());
				updatePayload.addProperty("elementId", elementId);
				JsonObject change = new JsonObject();
				change.addProperty("path", type.equals("procedure") ? "/procedurexml" : "/customFlag");
				change.addProperty("value", type.equals("procedure")
						? emptyProcedureXml("no_ext_trigger_updated") : "updated");
				com.google.gson.JsonArray changes = new com.google.gson.JsonArray();
				changes.add(change);
				updatePayload.add("changes", changes);
				var updated = entry.execute(Command.of(ids.get(), workspaceId, revision,
						Operation.UPDATE_MOD_ELEMENT, updatePayload));
				assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
				revision++;
				assertTrue(WorkspaceFileManager.gson.toJson(
						upstream.getMetadata(dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceMutationGateway
								.ELEMENT_VALUES_METADATA)).contains(type.equals("procedure")
								? "no_ext_trigger_updated" : "customFlag"));
			}
			assertEquals(8, revision);

			workspace.reloadFromFileSystem();
			for (int index = 0; index < types.size(); index++) {
				ModElement reloaded = workspace.getModElementByName("session_" + types.get(index));
				assertNotNull(reloaded);
				assertEquals(upstreamTypes.get(index), reloaded.getType());
				assertNotNull(reloaded.getGeneratableElement());
			}
		} finally {
			workspace.close();
		}
	}

	@Test void upstreamBackedMutationRestoresFilesWhenMetadataCommitConflicts() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("rollback_test");
		settings.setModName("Rollback Test");
		settings.setCurrentGenerator("neoforge-1.21.8");
		Workspace workspace = Workspace.createWorkspace(temporaryDirectory.resolve("rollback_test.mcreator").toFile(),
				settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111178");
		AtomicLong sequence = new AtomicLong(300);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
					new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids);
			workspace.getFileManager().advanceProductRevision(workspaceId, 0);

			JsonObject payload = new JsonObject();
			payload.addProperty("clientMutationId", uuid(7).toString());
			payload.addProperty("elementType", "procedure");
			payload.addProperty("name", "rolled_back_procedure");
			payload.add("initialValues", new JsonObject());
			var outcome = session.headlessEntry(PermissionProfile.WORKSPACE).execute(
					Command.of(uuid(8), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload));

			assertEquals("rejected", outcome.result().status());
			assertEquals("WORKSPACE_PERSISTENCE_FAILED", outcome.result().diagnostics().getFirst().code());
			assertFalse(workspace.containsModElement("rolled_back_procedure"));
			assertFalse(Files.exists(temporaryDirectory.resolve("elements/rolled_back_procedure.mod.json")));
			JsonObject root = new UnknownFieldPreservingJsonStore().read(
					workspace.getFileManager().getWorkspaceFile().toPath());
			assertEquals(1, root.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE)
					.get("revision").getAsLong());
		} finally {
			workspace.close();
		}
	}

	@Test void failingJavaPluginObserverCannotLeaveAPartialWorkspaceMutation() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("plugin_rollback_test");
		settings.setModName("Plugin Rollback Test");
		settings.setCurrentGenerator("neoforge-1.21.8");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("plugin_rollback_test.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111179");
		AtomicLong sequence = new AtomicLong(400);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
					new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids,
					List.of((actual, _, _, _, _) -> {
						assertTrue(actual.containsModElement("plugin_failed_procedure"));
						throw new IllegalStateException("simulated B-level plugin failure");
					}));
			JsonObject payload = new JsonObject();
			payload.addProperty("clientMutationId", uuid(9).toString());
			payload.addProperty("elementType", "procedure");
			payload.addProperty("name", "plugin_failed_procedure");
			payload.add("initialValues", new JsonObject());

			var outcome = session.mcpEntry(PermissionProfile.WORKSPACE).execute(
					Command.of(uuid(10), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload));

			assertEquals("rejected", outcome.result().status());
			assertEquals("WORKSPACE_PERSISTENCE_FAILED", outcome.result().diagnostics().getFirst().code());
			assertFalse(workspace.containsModElement("plugin_failed_procedure"));
			assertFalse(Files.exists(temporaryDirectory.resolve("elements/plugin_failed_procedure.mod.json")));
		} finally {
			workspace.close();
		}
	}

	private Workspace workspace() {
		WorkspaceSettings settings = new WorkspaceSettings("test_mod");
		settings.setModName("Compatibility Test");
		settings.setCurrentGenerator("neoforge-1.21.8");
		return new Workspace(settings);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}

	private static String emptyProcedureXml(String trigger) {
		return "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\">"
				+ "<field name=\"trigger\">" + trigger + "</field></block></xml>";
	}
}
