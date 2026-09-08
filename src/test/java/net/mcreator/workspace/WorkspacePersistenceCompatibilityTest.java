package net.mcreator.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
import dev.copperbench.release.ElementCoverageCatalog;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.types.Function;
import net.mcreator.element.types.Item;
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

	@Test void documentedItemMaxStackSizeMaterializesIntoUpstreamDefinitionAndGeneratedSource() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("item_stack_contract");
		settings.setModName("Item Stack Contract");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("item_stack_contract.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111186");
		AtomicLong sequence = new AtomicLong(5200);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
				new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			Path sourceRoot = workspace.getGenerator().getSourceRoot().toPath();
			Files.createDirectories(sourceRoot);
			workspace.getGenerator().setGradleCache(new com.google.gson.Gson().fromJson(
					"{\"classpath\":[],\"importTree\":{}}", net.mcreator.generator.GeneratorGradleCache.class));
			var entry = session.mcpEntry(PermissionProfile.WORKSPACE);

			JsonObject fields = new JsonObject();
			fields.addProperty("maxStackSize", 1);
			JsonObject values = new JsonObject();
			values.addProperty("displayName", "Contract Item");
			values.add("fields", fields);
			JsonObject createPayload = new JsonObject();
			createPayload.addProperty("clientMutationId", ids.get().toString());
			createPayload.addProperty("elementType", "item");
			createPayload.addProperty("name", "contract_item");
			createPayload.add("initialValues", values);
			var created = entry.execute(Command.of(ids.get(), workspaceId, 0,
					Operation.CREATE_MOD_ELEMENT, createPayload));
			assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
			String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
					.get("id").getAsString();

			ModElement modElement = workspace.getModElementByName("contract_item");
			assertNotNull(modElement);
			assertEquals(1, ((Item) modElement.getGeneratableElement()).stackSize,
					"documented create field must reach the persisted upstream Item definition");
			Path generatedItem = modElement.getAssociatedFiles().stream()
					.map(java.io.File::toPath)
					.filter(path -> path.getFileName().toString().endsWith(".java"))
					.findFirst().orElseThrow();
			assertTrue(Files.readString(generatedItem, StandardCharsets.UTF_8).contains(".stacksTo(1)"),
					generatedItem.toString());

			JsonObject change = new JsonObject();
			change.addProperty("path", "/fields/maxStackSize");
			change.addProperty("value", 7);
			JsonArray changes = new JsonArray();
			changes.add(change);
			JsonObject updatePayload = new JsonObject();
			updatePayload.addProperty("clientMutationId", ids.get().toString());
			updatePayload.addProperty("elementId", elementId);
			updatePayload.add("changes", changes);
			var updated = entry.execute(Command.of(ids.get(), workspaceId, 1,
					Operation.UPDATE_MOD_ELEMENT, updatePayload));
			assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());

			modElement = workspace.getModElementByName("contract_item");
			assertEquals(7, ((Item) modElement.getGeneratableElement()).stackSize,
					"documented update field must reach the persisted upstream Item definition");
			generatedItem = modElement.getAssociatedFiles().stream()
					.map(java.io.File::toPath)
					.filter(path -> path.getFileName().toString().endsWith(".java"))
					.findFirst().orElseThrow();
			assertTrue(Files.readString(generatedItem, StandardCharsets.UTF_8).contains(".stacksTo(7)"),
					generatedItem.toString());

			workspace.reloadFromFileSystem();
			assertEquals(7, ((Item) workspace.getModElementByName("contract_item").getGeneratableElement()).stackSize,
					"reopen must preserve the materialized upstream value");
		} finally {
			workspace.close();
		}
	}

	@Test void generatedSourceTakeoverSurvivesRegenerationAndExplicitReattachRestoresGeneratorOwnership()
			throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("source_takeover_lifecycle");
		settings.setModName("Source Takeover Lifecycle");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("source_takeover_lifecycle.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111184");
		AtomicLong sequence = new AtomicLong(698);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			assertTrue(workspace.getGenerator().generateBase());
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
					new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
				var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
				JsonObject createPayload = new JsonObject();
				createPayload.addProperty("clientMutationId", ids.get().toString());
				createPayload.addProperty("elementType", "item");
				createPayload.addProperty("name", "takeover_item");
				createPayload.add("initialValues", new JsonObject());
				var created = entry.execute(Command.of(ids.get(), workspaceId, 0,
						Operation.CREATE_MOD_ELEMENT, createPayload));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				ModElement modElement = workspace.getModElementByName("takeover_item");
				Path generatedSource = modElement.getAssociatedFiles().stream().map(java.io.File::toPath)
						.filter(path -> path.getFileName().toString().endsWith(".java"))
						.findFirst().orElseThrow();
				String generatorBaseline = Files.readString(generatedSource, StandardCharsets.UTF_8);
				assertFalse(modElement.isCodeLocked());
				JsonObject generatedNoOp = new JsonObject();
				generatedNoOp.addProperty("clientMutationId", ids.get().toString());
				generatedNoOp.addProperty("elementId", elementId);
				generatedNoOp.addProperty("mode", "generated");
				var noOp = entry.execute(Command.of(ids.get(), workspaceId, 1,
						Operation.SET_MOD_ELEMENT_SOURCE_MANAGEMENT, generatedNoOp));
				assertEquals("committed", noOp.result().status(), noOp.result().diagnostics().toString());
				assertEquals(1, noOp.result().newRevision(), "same-mode source management should be revision-neutral");
				assertFalse(noOp.result().data().getAsJsonObject().get("changed").getAsBoolean());

				JsonObject takeover = new JsonObject();
				takeover.addProperty("clientMutationId", ids.get().toString());
				takeover.addProperty("elementId", elementId);
				takeover.addProperty("mode", "manual");
				var takenOver = entry.execute(Command.of(ids.get(), workspaceId, 1,
						Operation.SET_MOD_ELEMENT_SOURCE_MANAGEMENT, takeover));
				assertEquals("committed", takenOver.result().status(), takenOver.result().diagnostics().toString());
				assertEquals(2, takenOver.result().newRevision());
				assertTrue(workspace.getModElementByName("takeover_item").isCodeLocked());

				JsonObject editorPayload = new JsonObject();
				editorPayload.addProperty("elementId", elementId);
				var editor = entry.query(Query.of(ids.get(), workspaceId, Operation.GET_MOD_ELEMENT_EDITOR, editorPayload));
				assertEquals("succeeded", editor.status(), editor.diagnostics().toString());
				assertEquals("manual", editor.data().getAsJsonObject().getAsJsonObject("element")
						.get("ownership").getAsString());
				assertTrue(editor.data().getAsJsonObject().getAsJsonObject("sourceManagement")
						.get("canReattach").getAsBoolean());
				for (var rawSection : editor.data().getAsJsonObject().getAsJsonArray("sections"))
					for (var rawField : rawSection.getAsJsonObject().getAsJsonArray("fields"))
						assertTrue(rawField.getAsJsonObject().get("readOnly").getAsBoolean(), rawField.toString());

				String externalSource = generatorBaseline + "\n// EXTERNAL_TAKEOVER_SURVIVES\n";
				Files.writeString(generatedSource, externalSource, StandardCharsets.UTF_8);
				workspace.reloadFromFileSystem();
				modElement = workspace.getModElementByName("takeover_item");
				assertTrue(modElement.isCodeLocked(), "takeover must survive a workspace reopen/reload");
				assertTrue(workspace.getGenerator().generateElement(modElement.getGeneratableElement()));
				assertEquals(externalSource, Files.readString(generatedSource, StandardCharsets.UTF_8),
						"regeneration must not reclaim manually managed source");

				JsonObject change = new JsonObject();
				change.addProperty("path", "/displayName");
				change.addProperty("value", "Must Not Apply While Detached");
				JsonArray changes = new JsonArray();
				changes.add(change);
				JsonObject update = new JsonObject();
				update.addProperty("clientMutationId", ids.get().toString());
				update.addProperty("elementId", elementId);
				update.add("changes", changes);
				var detachedUpdate = entry.execute(Command.of(ids.get(), workspaceId, 2,
						Operation.UPDATE_MOD_ELEMENT, update));
				assertEquals("rejected", detachedUpdate.result().status());
				assertEquals("SOURCE_MANAGEMENT_DETACHED", detachedUpdate.result().diagnostics().getFirst().code());

				JsonObject reattach = new JsonObject();
				reattach.addProperty("clientMutationId", ids.get().toString());
				reattach.addProperty("elementId", elementId);
				reattach.addProperty("mode", "generated");
				var unapproved = entry.execute(Command.of(ids.get(), workspaceId, 2,
						Operation.SET_MOD_ELEMENT_SOURCE_MANAGEMENT, reattach));
				assertEquals("rejected", unapproved.result().status());
				assertEquals("SOURCE_REATTACH_APPROVAL_REQUIRED", unapproved.result().diagnostics().getFirst().code());
				assertEquals(externalSource, Files.readString(generatedSource, StandardCharsets.UTF_8));

				reattach.addProperty("userApproved", true);
				var reattached = entry.execute(Command.of(ids.get(), workspaceId, 2,
						Operation.SET_MOD_ELEMENT_SOURCE_MANAGEMENT, reattach));
				assertEquals("committed", reattached.result().status(), reattached.result().diagnostics().toString());
				assertEquals(3, reattached.result().newRevision());
				assertFalse(workspace.getModElementByName("takeover_item").isCodeLocked());
				assertFalse(Files.readString(generatedSource, StandardCharsets.UTF_8)
						.contains("EXTERNAL_TAKEOVER_SURVIVES"),
						"approved reattach must make the generator authoritative again");

				var reattachedEditor = entry.query(Query.of(ids.get(), workspaceId,
						Operation.GET_MOD_ELEMENT_EDITOR, editorPayload));
				assertEquals("generated", reattachedEditor.data().getAsJsonObject().getAsJsonObject("element")
						.get("ownership").getAsString());
				assertTrue(reattachedEditor.data().getAsJsonObject().getAsJsonObject("sourceManagement")
						.get("canTakeOver").getAsBoolean());
			}
		} finally {
			workspace.close();
		}
	}

	@Test void sourceIntegrityLifecycleSemanticsHoldAcrossAllEightTracks() throws Exception {
		List<String> generators = List.of("fabric-1.20.1", "neoforge-1.20.1", "fabric-1.21.1", "neoforge-1.21.1",
				"fabric-26.1.2", "neoforge-26.1.2", "fabric-26.2", "neoforge-26.2");
		AtomicLong sequence = new AtomicLong(820);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		for (String generator : generators) {
			String safe = generator.replace('-', '_').replace('.', '_');
			WorkspaceSettings settings = new WorkspaceSettings("takeover_" + safe);
			settings.setModName("Takeover " + generator);
			settings.setVersion("1.0.0");
			settings.setCurrentGenerator(generator);
			Path root = temporaryDirectory.resolve("takeover_" + safe);
			Files.createDirectories(root);
			Workspace workspace = Workspace.createWorkspace(root.resolve("takeover_" + safe + ".mcreator").toFile(), settings);
			try {
				assertTrue(workspace.getGenerator().generateBase(), generator);
				try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
						store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
					var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
					JsonObject create = new JsonObject();
					create.addProperty("clientMutationId", ids.get().toString());
					create.addProperty("elementType", "item");
					create.addProperty("name", "track_item");
					create.add("initialValues", new JsonObject());
					var created = entry.execute(Command.of(ids.get(), session.workspaceId(), 0,
							Operation.CREATE_MOD_ELEMENT, create));
					assertEquals("committed", created.result().status(), generator + ": " + created.result().diagnostics());
					String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
							.get("id").getAsString();
					ModElement modElement = workspace.getModElementByName("track_item");
					Path source = modElement.getAssociatedFiles().stream().map(java.io.File::toPath)
							.filter(path -> path.getFileName().toString().endsWith(".java"))
							.findFirst().orElseThrow();

					JsonObject takeover = new JsonObject();
					takeover.addProperty("clientMutationId", ids.get().toString());
					takeover.addProperty("elementId", elementId);
					takeover.addProperty("mode", "manual");
					var detached = entry.execute(Command.of(ids.get(), session.workspaceId(), 1,
							Operation.SET_MOD_ELEMENT_SOURCE_MANAGEMENT, takeover));
					assertEquals("committed", detached.result().status(), generator + ": " + detached.result().diagnostics());
					assertTrue(workspace.getModElementByName("track_item").isCodeLocked(), generator);

					String external = Files.readString(source, StandardCharsets.UTF_8) + "\n// TRACK_MANUAL_" + safe + "\n";
					Files.writeString(source, external, StandardCharsets.UTF_8);
					workspace.reloadFromFileSystem();
					modElement = workspace.getModElementByName("track_item");
					assertTrue(modElement.isCodeLocked(), generator + " reopen");
					assertTrue(workspace.getGenerator().generateElement(modElement.getGeneratableElement()), generator);
					assertEquals(external, Files.readString(source, StandardCharsets.UTF_8), generator + " regenerate");

					JsonObject reattach = new JsonObject();
					reattach.addProperty("clientMutationId", ids.get().toString());
					reattach.addProperty("elementId", elementId);
					reattach.addProperty("mode", "generated");
					reattach.addProperty("userApproved", true);
					var attached = entry.execute(Command.of(ids.get(), session.workspaceId(), 2,
							Operation.SET_MOD_ELEMENT_SOURCE_MANAGEMENT, reattach));
					assertEquals("committed", attached.result().status(), generator + ": " + attached.result().diagnostics());
					assertFalse(workspace.getModElementByName("track_item").isCodeLocked(), generator);
					assertFalse(Files.readString(source, StandardCharsets.UTF_8).contains("TRACK_MANUAL_"), generator);

					long revision = attached.result().newRevision();
					String packageName = workspace.getWorkspaceSettings().getModElementsPackage();

					// Primary + helper external edits must survive metadata-only save, reopen and regeneration.
					JsonObject codeValues = new JsonObject();
					codeValues.addProperty("code", "package " + packageName
							+ ";\npublic final class integrity_root {}\n");
					JsonArray codeFiles = new JsonArray();
					JsonObject helper = new JsonObject();
					helper.addProperty("path", "runtime/IntegrityHelper.java");
					helper.addProperty("code", "package " + packageName
							+ ".runtime;\npublic final class IntegrityHelper {}\n");
					codeFiles.add(helper);
					codeValues.add("codeFiles", codeFiles);
					JsonObject codeCreate = new JsonObject();
					codeCreate.addProperty("clientMutationId", ids.get().toString());
					codeCreate.addProperty("elementType", "code");
					codeCreate.addProperty("name", "integrity_root");
					codeCreate.add("initialValues", codeValues);
					var codeCreated = entry.execute(Command.of(ids.get(), session.workspaceId(), revision,
							Operation.CREATE_MOD_ELEMENT, codeCreate));
					assertEquals("committed", codeCreated.result().status(), generator + ": " + codeCreated.result().diagnostics());
					revision = codeCreated.result().newRevision();
					String codeElementId = codeCreated.result().data().getAsJsonObject().getAsJsonObject("element")
							.get("id").getAsString();
					Path primary = workspace.getModElementByName("integrity_root").getAssociatedFiles().stream()
							.map(java.io.File::toPath).filter(path -> path.getFileName().toString().endsWith(".java"))
							.findFirst().orElseThrow();
					Path helperSource = primary.getParent().resolve("runtime/IntegrityHelper.java");
					String externalPrimary = "package " + packageName
							+ ";\npublic final class integrity_root { public static final int IDE_BASELINE = 11; }\n";
					String externalHelper = "package " + packageName
							+ ".runtime;\npublic final class IntegrityHelper { public static final int IDE_BASELINE = 13; }\n";
					Files.writeString(primary, externalPrimary, StandardCharsets.UTF_8);
					Files.writeString(helperSource, externalHelper, StandardCharsets.UTF_8);

					JsonObject displayChange = new JsonObject();
					displayChange.addProperty("path", "/displayName");
					displayChange.addProperty("value", "Integrity " + generator);
					JsonArray metadataChanges = new JsonArray();
					metadataChanges.add(displayChange);
					JsonObject metadataUpdate = new JsonObject();
					metadataUpdate.addProperty("clientMutationId", ids.get().toString());
					metadataUpdate.addProperty("elementId", codeElementId);
					metadataUpdate.add("changes", metadataChanges);
					var metadataOnly = entry.execute(Command.of(ids.get(), session.workspaceId(), revision,
							Operation.UPDATE_MOD_ELEMENT, metadataUpdate));
					assertEquals("committed", metadataOnly.result().status(), generator + ": " + metadataOnly.result().diagnostics());
					revision = metadataOnly.result().newRevision();
					assertEquals(externalPrimary, Files.readString(primary, StandardCharsets.UTF_8), generator + " metadata primary");
					assertEquals(externalHelper, Files.readString(helperSource, StandardCharsets.UTF_8), generator + " metadata helper");

					workspace.reloadFromFileSystem();
					ModElement codeElement = workspace.getModElementByName("integrity_root");
					assertTrue(codeElement.isCodeLocked(), generator + " code reopen ownership");
					assertTrue(workspace.getGenerator().generateElement(codeElement.getGeneratableElement()), generator + " code regenerate");
					assertEquals(externalPrimary, Files.readString(primary, StandardCharsets.UTF_8), generator + " reopen primary");
					assertEquals(externalHelper, Files.readString(helperSource, StandardCharsets.UTF_8), generator + " reopen helper");

					// An explicit write based on the pre-IDE fingerprint must fail without advancing revision.
					String stalePrimary = externalPrimary.replace("IDE_BASELINE = 11", "IDE_STALE = 21");
					Files.writeString(primary, stalePrimary, StandardCharsets.UTF_8);
					JsonObject codeChange = new JsonObject();
					codeChange.addProperty("path", "/code");
					codeChange.addProperty("value", externalPrimary.replace("IDE_BASELINE = 11", "AGENT_STALE = 29"));
					JsonArray staleChanges = new JsonArray();
					staleChanges.add(codeChange);
					JsonObject staleUpdate = new JsonObject();
					staleUpdate.addProperty("clientMutationId", ids.get().toString());
					staleUpdate.addProperty("elementId", codeElementId);
					staleUpdate.add("changes", staleChanges);
					var stale = entry.execute(Command.of(ids.get(), session.workspaceId(), revision,
							Operation.UPDATE_MOD_ELEMENT, staleUpdate));
					assertEquals("rejected", stale.result().status(), generator + ": " + stale.result().diagnostics());
					assertTrue(stale.result().diagnostics().stream().anyMatch(diagnostic ->
							"SOURCE_CONTENT_CONFLICT".equals(diagnostic.code())), generator + ": " + stale.result().diagnostics());
					assertEquals(stalePrimary, Files.readString(primary, StandardCharsets.UTF_8), generator + " stale primary");
					assertEquals(revision, stale.result().newRevision(), generator + " stale revision");

					// Recovery points must capture the live external primary/helper bytes and remove later files.
					String recoveryHelper = externalHelper.replace("IDE_BASELINE = 13", "IDE_RECOVERY = 23");
					Files.writeString(helperSource, recoveryHelper, StandardCharsets.UTF_8);
					JsonObject pointPayload = new JsonObject();
					pointPayload.addProperty("label", "Eight-track source baseline " + generator);
					var point = entry.execute(Command.of(ids.get(), session.workspaceId(), revision,
							Operation.CREATE_RECOVERY_POINT, pointPayload));
					assertEquals("committed", point.result().status(), generator + ": " + point.result().diagnostics());
					String recoveryPointId = point.result().recoveryPointId();
					assertTrue(recoveryPointId != null && !recoveryPointId.isBlank(), generator);
					Files.writeString(primary, stalePrimary.replace("IDE_STALE = 21", "POST_POINT = 31"), StandardCharsets.UTF_8);
					Files.writeString(helperSource, recoveryHelper.replace("IDE_RECOVERY = 23", "POST_POINT = 33"), StandardCharsets.UTF_8);
					Path addedAfterPoint = primary.getParent().resolve("runtime/AddedAfterPoint.java");
					Files.writeString(addedAfterPoint, "package " + packageName
							+ ".runtime;\npublic final class AddedAfterPoint {}\n", StandardCharsets.UTF_8);
					JsonObject restorePayload = new JsonObject();
					restorePayload.addProperty("recoveryPointId", recoveryPointId);
					restorePayload.addProperty("userApproved", true);
					var restored = session.uiEntry().execute(Command.of(ids.get(), session.workspaceId(), revision,
							Operation.RESTORE_RECOVERY_POINT, restorePayload));
					assertEquals("committed", restored.result().status(), generator + ": " + restored.result().diagnostics());
					revision = restored.result().newRevision();
					assertEquals(stalePrimary, Files.readString(primary, StandardCharsets.UTF_8), generator + " recovery primary");
					assertEquals(recoveryHelper, Files.readString(helperSource, StandardCharsets.UTF_8), generator + " recovery helper");
					assertFalse(Files.exists(addedAfterPoint), generator + " recovery cleanup");

					// A second code element cannot claim another element's primary; failure must leave no orphan primary.
					String ownerACode = "package " + packageName + ";\npublic final class owner_a {}\n";
					JsonObject ownerAValues = new JsonObject();
					ownerAValues.addProperty("code", ownerACode);
					JsonObject ownerACreate = new JsonObject();
					ownerACreate.addProperty("clientMutationId", ids.get().toString());
					ownerACreate.addProperty("elementType", "code");
					ownerACreate.addProperty("name", "owner_a");
					ownerACreate.add("initialValues", ownerAValues);
					var ownerA = entry.execute(Command.of(ids.get(), session.workspaceId(), revision,
							Operation.CREATE_MOD_ELEMENT, ownerACreate));
					assertEquals("committed", ownerA.result().status(), generator + ": " + ownerA.result().diagnostics());
					revision = ownerA.result().newRevision();
					Path ownerAPrimary = workspace.getModElementByName("owner_a").getAssociatedFiles().stream()
							.map(java.io.File::toPath).filter(path -> path.getFileName().toString().endsWith(".java"))
							.findFirst().orElseThrow();
					String ownerABytes = Files.readString(ownerAPrimary, StandardCharsets.UTF_8);
					JsonObject ownerBValues = new JsonObject();
					ownerBValues.addProperty("code", "package " + packageName + ";\npublic final class owner_b {}\n");
					JsonArray ownerBFiles = new JsonArray();
					JsonObject collision = new JsonObject();
					collision.addProperty("path", ownerAPrimary.getFileName().toString());
					collision.addProperty("code", "// must never overwrite owner_a\n");
					ownerBFiles.add(collision);
					ownerBValues.add("codeFiles", ownerBFiles);
					JsonObject ownerBCreate = new JsonObject();
					ownerBCreate.addProperty("clientMutationId", ids.get().toString());
					ownerBCreate.addProperty("elementType", "code");
					ownerBCreate.addProperty("name", "owner_b");
					ownerBCreate.add("initialValues", ownerBValues);
					var ownerB = entry.execute(Command.of(ids.get(), session.workspaceId(), revision,
							Operation.CREATE_MOD_ELEMENT, ownerBCreate));
					assertEquals("rejected", ownerB.result().status(), generator + ": " + ownerB.result().diagnostics());
					assertEquals(ownerABytes, Files.readString(ownerAPrimary, StandardCharsets.UTF_8), generator + " collision owner");
					assertFalse(workspace.containsModElement("owner_b"), generator + " collision element cleanup");
					assertFalse(Files.exists(ownerAPrimary.getParent().resolve("owner_b.java")), generator + " collision primary cleanup");
				}
			} finally {
				workspace.close();
			}
		}
	}

	@Test void unmanagedExistingJavaSourceCannotBeClaimedByDirectCreateOrWorkspacePlan() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("workspace_unmanaged_source");
		settings.setModName("Workspace Unmanaged Source");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("workspace_unmanaged_source.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(695);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			assertTrue(workspace.getGenerator().generateBase());
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
				var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
				Path packageRoot = workspace.getGenerator().getGeneratorPackageRoot().toPath();
				Files.createDirectories(packageRoot);

				String directManual = "package " + workspace.getWorkspaceSettings().getModElementsPackage()
						+ ";\npublic final class manual_direct {}\n";
				Path directPath = packageRoot.resolve("manual_direct.java");
				Files.writeString(directPath, directManual, StandardCharsets.UTF_8);
				JsonObject directValues = new JsonObject();
				directValues.addProperty("code", directManual.replace("{}", "{ public static final int AGENT = 1; }"));
				JsonObject directPayload = new JsonObject();
				directPayload.addProperty("clientMutationId", ids.get().toString());
				directPayload.addProperty("elementType", "code");
				directPayload.addProperty("name", "manual_direct");
				directPayload.add("initialValues", directValues);
				var direct = entry.execute(Command.of(ids.get(), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, directPayload));
				assertEquals("rejected", direct.result().status(), direct.result().diagnostics().toString());
				assertEquals(directManual, Files.readString(directPath, StandardCharsets.UTF_8));
				assertFalse(workspace.containsModElement("manual_direct"));

				String planManual = directManual.replace("manual_direct", "manual_plan");
				Path planPath = packageRoot.resolve("manual_plan.java");
				Files.writeString(planPath, planManual, StandardCharsets.UTF_8);
				JsonObject planValues = new JsonObject();
				planValues.addProperty("code", planManual.replace("{}", "{ public static final int AGENT = 2; }"));
				var planned = entry.query(Query.of(ids.get(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES,
						workspacePlanPayload(0, "unmanaged-source-plan",
								workspacePlanCreate("code", "manual_plan", planValues))));
				assertEquals("failed", planned.status(), planned.diagnostics().toString());
				assertTrue(planned.diagnostics().stream().anyMatch(diagnostic ->
						"WORKSPACE_PLAN_SOURCE_CONFLICT".equals(diagnostic.code())), planned.diagnostics().toString());
				assertEquals(planManual, Files.readString(planPath, StandardCharsets.UTF_8));
				assertFalse(workspace.containsModElement("manual_plan"));
			}
		} finally {
			workspace.close();
		}
	}

	@Test void unmanagedGeneratedItemPathCannotBeOverwrittenByDirectCreate() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("unmanaged_generated_item");
		settings.setModName("Unmanaged Generated Item");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("unmanaged_generated_item.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(710);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			assertTrue(workspace.getGenerator().generateBase());
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
				Path target = workspace.getGenerator().getGeneratorPackageRoot().toPath()
						.resolve("item/unmanaged_itemItem.java");
				Files.createDirectories(target.getParent());
				String manual = "// external unmanaged item source\n";
				Files.writeString(target, manual, StandardCharsets.UTF_8);

				JsonObject payload = new JsonObject();
				payload.addProperty("clientMutationId", ids.get().toString());
				payload.addProperty("elementType", "item");
				payload.addProperty("name", "unmanaged_item");
				payload.add("initialValues", new JsonObject());
				var outcome = session.mcpEntry(PermissionProfile.WORKSPACE).execute(
						Command.of(ids.get(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload));

				assertEquals("rejected", outcome.result().status(), outcome.result().diagnostics().toString());
				assertEquals("WORKSPACE_PERSISTENCE_FAILED", outcome.result().diagnostics().getFirst().code());
				assertEquals(manual, Files.readString(target, StandardCharsets.UTF_8));
				assertFalse(workspace.containsModElement("unmanaged_item"));
			}
		} finally {
			workspace.close();
		}
	}

	@Test void workspacePlanRejectsCodeHelperCollisionWithPredictedItemSource() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("cross_type_generated_owner");
		settings.setModName("Cross Type Generated Owner");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("cross_type_generated_owner.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(720);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			assertTrue(workspace.getGenerator().generateBase());
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
				JsonObject helper = new JsonObject();
				helper.addProperty("path", "item/planned_itemItem.java");
				helper.addProperty("code", "package net.mcreator.cross_type_generated_owner.item; class planned_itemItem {}\n");
				JsonArray helpers = new JsonArray();
				helpers.add(helper);
				JsonObject codeValues = new JsonObject();
				codeValues.addProperty("code",
						"package net.mcreator.cross_type_generated_owner; public final class native_root {}\n");
				codeValues.add("codeFiles", helpers);

				var planned = session.mcpEntry(PermissionProfile.WORKSPACE).query(Query.of(ids.get(), session.workspaceId(),
						Operation.PLAN_WORKSPACE_CHANGES, workspacePlanPayload(0, "cross-type-generated-owner",
								workspacePlanCreate("code", "native_root", codeValues),
								workspacePlanCreate("item", "planned_item"))));

				assertEquals("failed", planned.status(), planned.diagnostics().toString());
				assertTrue(planned.diagnostics().stream().anyMatch(diagnostic ->
						"WORKSPACE_PLAN_SOURCE_CONFLICT".equals(diagnostic.code())), planned.diagnostics().toString());
				assertFalse(workspace.containsModElement("native_root"));
				assertFalse(workspace.containsModElement("planned_item"));
				assertFalse(Files.exists(workspace.getGenerator().getGeneratorPackageRoot().toPath().resolve("native_root.java")));
				assertFalse(Files.exists(workspace.getGenerator().getGeneratorPackageRoot().toPath()
						.resolve("item/planned_itemItem.java")));
			}
		} finally {
			workspace.close();
		}
	}

	@Test void generatedPathOwnershipPredictionRejectsUnmanagedItemSourceAcrossAllEightTracks() throws Exception {
		List<String> generators = List.of("fabric-1.20.1", "neoforge-1.20.1", "fabric-1.21.1", "neoforge-1.21.1",
				"fabric-26.1.2", "neoforge-26.1.2", "fabric-26.2", "neoforge-26.2");
		AtomicLong sequence = new AtomicLong(730);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		for (String generator : generators) {
			String safe = generator.replace('-', '_').replace('.', '_');
			WorkspaceSettings settings = new WorkspaceSettings("owner_" + safe);
			settings.setModName("Ownership " + generator);
			settings.setVersion("1.0.0");
			settings.setCurrentGenerator(generator);
			Path root = temporaryDirectory.resolve(safe);
			Files.createDirectories(root);
			Workspace workspace = Workspace.createWorkspace(root.resolve("owner_" + safe + ".mcreator").toFile(), settings);
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
				Path target = workspace.getGenerator().getGeneratorPackageRoot().toPath().resolve("item/track_itemItem.java");
				Files.createDirectories(target.getParent());
				String manual = "// unmanaged " + generator + "\n";
				Files.writeString(target, manual, StandardCharsets.UTF_8);

				var planned = session.mcpEntry(PermissionProfile.WORKSPACE).query(Query.of(ids.get(), session.workspaceId(),
						Operation.PLAN_WORKSPACE_CHANGES, workspacePlanPayload(0, "owner-" + safe,
								workspacePlanCreate("item", "track_item"))));

				assertEquals("failed", planned.status(), generator + ": " + planned.diagnostics());
				assertTrue(planned.diagnostics().stream().anyMatch(diagnostic ->
						"WORKSPACE_PLAN_SOURCE_CONFLICT".equals(diagnostic.code())),
						generator + ": " + planned.diagnostics());
				assertEquals(manual, Files.readString(target, StandardCharsets.UTF_8), generator);
				assertFalse(workspace.containsModElement("track_item"), generator);
			} finally {
				workspace.close();
			}
		}
	}

	@Test void issuedWorkspacePlanBecomesSourceStaleBeforePreviewOrApply() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("workspace_plan_stale_source");
		settings.setModName("Workspace Plan Stale Source");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("workspace_plan_stale_source.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(690);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			assertTrue(workspace.getGenerator().generateBase());
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
				var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
				String initial = "package net.mcreator.workspace_plan_stale_source;\npublic final class runtime_root {}\n";
				JsonObject initialValues = new JsonObject();
				initialValues.addProperty("code", initial);
				JsonObject createPayload = new JsonObject();
				createPayload.addProperty("clientMutationId", ids.get().toString());
				createPayload.addProperty("elementType", "code");
				createPayload.addProperty("name", "runtime_root");
				createPayload.add("initialValues", initialValues);
				var created = entry.execute(Command.of(ids.get(), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, createPayload));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				Path primary = workspace.getModElementByName("runtime_root").getAssociatedFiles().stream()
						.map(java.io.File::toPath)
						.filter(path -> path.getFileName().toString().endsWith(".java"))
						.findFirst().orElseThrow();

				String plannedCode = "package net.mcreator.workspace_plan_stale_source;\n"
						+ "public final class runtime_root { public static final int PLAN_EDIT = 41; }\n";
				var planned = entry.query(Query.of(ids.get(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES,
						workspacePlanPayload(1, "stale-source-plan",
								workspacePlanUpdate(elementId, "/code", new JsonPrimitive(plannedCode)))));
				assertEquals("succeeded", planned.status(), planned.diagnostics().toString());

				String external = plannedCode.replace("PLAN_EDIT = 41", "IDE_EDIT = 43");
				Files.writeString(primary, external, StandardCharsets.UTF_8);
				JsonObject previewPayload = new JsonObject();
				previewPayload.add("plan", planned.data().deepCopy());
				var preview = entry.query(Query.of(ids.get(), session.workspaceId(), Operation.PREVIEW_WORKSPACE_PLAN,
						previewPayload));
				assertEquals("failed", preview.status(), preview.diagnostics().toString());
				assertTrue(preview.diagnostics().stream().anyMatch(diagnostic ->
						"WORKSPACE_PLAN_SOURCE_CONFLICT".equals(diagnostic.code())), preview.diagnostics().toString());

				JsonObject applyPayload = new JsonObject();
				applyPayload.addProperty("clientMutationId", ids.get().toString());
				applyPayload.add("plan", planned.data().deepCopy());
				var applied = entry.execute(Command.of(ids.get(), session.workspaceId(), 1,
						Operation.APPLY_WORKSPACE_PLAN, applyPayload));
				assertEquals("rejected", applied.result().status(), applied.result().diagnostics().toString());
				assertTrue(applied.result().diagnostics().stream().anyMatch(diagnostic ->
						"WORKSPACE_PLAN_SOURCE_CONFLICT".equals(diagnostic.code())),
						applied.result().diagnostics().toString());
				assertEquals(external, Files.readString(primary, StandardCharsets.UTF_8),
						"A plan that became source-stale after issuance must not overwrite the IDE edit");
			}
		} finally {
			workspace.close();
		}
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
		return workspacePlanCreate(type, name, new JsonObject());
	}

	private static JsonObject workspacePlanCreate(String type, String name, JsonObject initialValues) {
		JsonObject payload = new JsonObject();
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", initialValues);
		JsonObject step = new JsonObject();
		step.addProperty("operation", "create_mod_element");
		step.add("payload", payload);
		return step;
	}

	private static JsonObject workspacePlanUpdate(String elementId, String path, JsonElement value) {
		JsonObject change = new JsonObject();
		change.addProperty("path", path);
		change.add("value", value.deepCopy());
		JsonArray changes = new JsonArray();
		changes.add(change);
		JsonObject payload = new JsonObject();
		payload.addProperty("elementId", elementId);
		payload.add("changes", changes);
		JsonObject step = new JsonObject();
		step.addProperty("operation", "update_mod_element");
		step.add("payload", payload);
		return step;
	}

	@Test void workspacePlanRejectsCodeOwnershipCollisionBeforeWriting() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("workspace_plan_code_owner");
		settings.setModName("Workspace Plan Code Owner");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("workspace_plan_code_owner.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(680);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
				store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			JsonObject ownerA = new JsonObject();
			ownerA.addProperty("code", "package net.mcreator.workspace_plan_code_owner;\npublic final class owner_a {}\n");
			JsonObject ownerB = new JsonObject();
			ownerB.addProperty("code", "package net.mcreator.workspace_plan_code_owner;\npublic final class owner_b {}\n");
			JsonArray files = new JsonArray();
			JsonObject collision = new JsonObject();
			collision.addProperty("path", "owner_a.java");
			collision.addProperty("code", "// must never overwrite the first plan element\n");
			files.add(collision);
			ownerB.add("codeFiles", files);

			var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
			var planned = entry.query(Query.of(ids.get(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES,
					workspacePlanPayload(0, "code-owner-conflict",
							workspacePlanCreate("code", "owner_a", ownerA),
							workspacePlanCreate("code", "owner_b", ownerB))));

			assertEquals("failed", planned.status(), planned.diagnostics().toString());
			assertTrue(planned.diagnostics().stream().anyMatch(diagnostic ->
					"WORKSPACE_PLAN_SOURCE_CONFLICT".equals(diagnostic.code())), planned.diagnostics().toString());
			assertFalse(workspace.containsModElement("owner_a"));
			assertFalse(workspace.containsModElement("owner_b"));
			Path packageRoot = workspace.getGenerator().getGeneratorPackageRoot().toPath();
			assertFalse(Files.exists(packageRoot.resolve("owner_a.java")));
			assertFalse(Files.exists(packageRoot.resolve("owner_b.java")));
		} finally {
			workspace.close();
		}
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
		settings.setCurrentGenerator("neoforge-1.21.1");
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

	@Test void workspacePlanRollbackRemovesNewGeneratedSourcesAndRestoresSharedBaseFiles() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("rollback_generated_sources");
		settings.setModName("Rollback Generated Sources");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("rollback_generated_sources.mcreator").toFile(), settings);
		AtomicLong sequence = new AtomicLong(780);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			assertTrue(workspace.getGenerator().generateBase());
			Path packageRoot = workspace.getGenerator().getGeneratorPackageRoot().toPath();
			Path stableBase = workspace.getGenerator().getModBaseGeneratorTemplatesList().stream()
					.map(net.mcreator.generator.GeneratorTemplate::getFile)
					.map(java.io.File::toPath)
					.filter(Files::isRegularFile)
					.findFirst().orElseThrow();
			byte[] stableBefore = Files.readAllBytes(stableBase);
			Path itemSource = packageRoot.resolve("item/rollback_itemItem.java");
			assertFalse(Files.exists(itemSource));
			try (var sources = Files.walk(packageRoot)) {
				assertTrue(sources.noneMatch(path -> path.getFileName().toString().endsWith("ModItems.java")));
			}

			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
				var entry = session.mcpEntry(PermissionProfile.WORKSPACE);
				var planned = entry.query(Query.of(ids.get(), session.workspaceId(), Operation.PLAN_WORKSPACE_CHANGES,
						workspacePlanPayload(0, "rollback-generated-sources",
								workspacePlanCreate("item", "rollback_item"))));
				assertEquals("succeeded", planned.status(), planned.diagnostics().toString());

				workspace.getFileManager().advanceProductRevision(session.workspaceId(), 0);
				JsonObject applyPayload = new JsonObject();
				applyPayload.addProperty("clientMutationId", ids.get().toString());
				applyPayload.add("plan", planned.data().deepCopy());
				var applied = entry.execute(Command.of(ids.get(), session.workspaceId(), 0,
						Operation.APPLY_WORKSPACE_PLAN, applyPayload));

				assertEquals("rejected", applied.result().status(), applied.result().diagnostics().toString());
				assertEquals("WORKSPACE_PLAN_PERSISTENCE_FAILED", applied.result().diagnostics().getFirst().code());
				assertFalse(workspace.containsModElement("rollback_item"));
				assertFalse(Files.exists(itemSource), "new element source must be deleted by rollback");
				try (var sources = Files.walk(packageRoot)) {
					assertTrue(sources.noneMatch(path -> path.getFileName().toString().endsWith("ModItems.java")),
							"new shared item registry must be deleted by rollback");
				}
				assertTrue(java.util.Arrays.equals(stableBefore, Files.readAllBytes(stableBase)),
						"pre-existing generated base file must be restored byte-for-byte");
			}
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
		settings.setCurrentGenerator("neoforge-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("first_party_slice_test.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111180");
		AtomicLong sequence = new AtomicLong(1000);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try {
			MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
					new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids);
			var entry = session.headlessEntry(PermissionProfile.WORKSPACE);
			List<String> types = ElementCoverageCatalog.FIRST_PARTY_SLICE;
			List<ModElementType<?>> upstreamTypes = types.stream()
				.map(ModElementTypeLoader::getModElementType).toList();
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
				if (!type.equals("code"))
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
				assertTrue(Files.readString(workspace.getFileManager().getWorkspaceFile().toPath())
						.contains("session_" + type), type);
			}
			assertEquals(types.size() * 2L, revision);

			String savedWorkspace = Files.readString(workspace.getFileManager().getWorkspaceFile().toPath(),
					StandardCharsets.UTF_8);
			workspace.reloadFromFileSystem();
			List<String> reloadedNames = workspace.getModElements().stream().map(ModElement::getName).sorted()
					.toList();
			for (int index = 0; index < types.size(); index++) {
				String type = types.get(index);
				if (type.equals("code"))
					continue;
				assertTrue(savedWorkspace.contains("session_" + type), type);
				ModElement reloaded = workspace.getModElementByName("session_" + type);
				assertNotNull(reloaded, () -> type + " missing after reload; present=" + reloadedNames);
				assertEquals(upstreamTypes.get(index), reloaded.getType());
				assertTrue(Files.isRegularFile(temporaryDirectory.resolve("elements/session_" + type + ".mod.json")),
						type);
			}
		} finally {
			workspace.close();
		}
	}

	@Test void minimalAgentItemAndProjectilePersistThroughRealFabricGeneration() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("agent_generation_defaults");
		settings.setModName("Agent Generation Defaults");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-26.1.2");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("agent_generation_defaults.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111183");
		AtomicLong sequence = new AtomicLong(4000);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
				new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			Files.createDirectories(workspace.getGenerator().getSourceRoot().toPath());
			var entry = session.headlessEntry(PermissionProfile.WORKSPACE);
			long revision = 0;

			for (String type : List.of("item", "projectile")) {
				JsonObject payload = new JsonObject();
				payload.addProperty("clientMutationId", ids.get().toString());
				payload.addProperty("elementType", type);
				payload.addProperty("name", "agent_" + type);
				JsonObject values = new JsonObject();
				if (type.equals("item")) values.addProperty("displayName", "Agent Item");
				payload.add("initialValues", values);

				var created = entry.execute(Command.of(ids.get(), workspaceId, revision,
						Operation.CREATE_MOD_ELEMENT, payload));
				assertEquals("committed", created.result().status(),
						() -> type + ": " + created.result().diagnostics());
				revision++;
				assertNotNull(workspace.getModElementByName("agent_" + type));
			}
		} finally {
			workspace.close();
		}
	}

	@Test void workspacePlanRefreshesRealFabricBaseImportsAfterCreatingJavaElement() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("agent_plan_imports");
		settings.setModName("Agent Plan Imports");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-26.1.2");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("agent_plan_imports.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111184");
		AtomicLong sequence = new AtomicLong(5000);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
				new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			Path sourceRoot = workspace.getGenerator().getSourceRoot().toPath();
			Files.createDirectories(sourceRoot);
			workspace.getGenerator().setGradleCache(new com.google.gson.Gson().fromJson(
					"{\"classpath\":[],\"importTree\":{}}", net.mcreator.generator.GeneratorGradleCache.class));
			var entry = session.mcpEntry(PermissionProfile.WORKSPACE);

			JsonObject directPayload = new JsonObject();
			directPayload.addProperty("clientMutationId", ids.get().toString());
			directPayload.addProperty("elementType", "item");
			directPayload.addProperty("name", "direct_item");
			directPayload.add("initialValues", new JsonObject());
			var direct = entry.execute(Command.of(ids.get(), workspaceId, 0,
					Operation.CREATE_MOD_ELEMENT, directPayload));
			assertEquals("committed", direct.result().status(), direct.result().diagnostics().toString());

			JsonObject planPayload = workspacePlanPayload(1, "real-fabric-plan-import",
					workspacePlanCreate("item", "planned_item"));
			var planned = entry.query(Query.of(ids.get(), workspaceId, Operation.PLAN_WORKSPACE_CHANGES,
					planPayload));
			assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
			JsonObject applyPayload = new JsonObject();
			applyPayload.addProperty("clientMutationId", ids.get().toString());
			applyPayload.add("plan", planned.data().deepCopy());
			var applied = entry.execute(Command.of(ids.get(), workspaceId, 1,
					Operation.APPLY_WORKSPACE_PLAN, applyPayload));
			assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());

			Path itemRegistry;
			try (var sources = Files.walk(sourceRoot)) {
				itemRegistry = sources.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().endsWith("ModItems.java"))
						.findFirst().orElseThrow();
			}
			String generated = Files.readString(itemRegistry, StandardCharsets.UTF_8);
			assertTrue(generated.contains("import net.mcreator.agent_plan_imports.item.direct_itemItem;"), generated);
			assertTrue(generated.contains("import net.mcreator.agent_plan_imports.item.planned_itemItem;"), generated);
			assertTrue(generated.contains("planned_itemItem::new"), generated);
		} finally {
			workspace.close();
		}
	}

	@Test void invalidGeneratedElementIsRejectedAndRolledBackInsteadOfSilentlyPersisted() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("invalid_generation_rollback");
		settings.setModName("Invalid Generation Rollback");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("invalid_generation_rollback.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111182");
		AtomicLong sequence = new AtomicLong(3000);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
				new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			Files.createDirectories(workspace.getGenerator().getSourceRoot().toPath());
			JsonObject payload = new JsonObject();
			payload.addProperty("clientMutationId", ids.get().toString());
			payload.addProperty("elementType", "armor");
			payload.addProperty("name", "invalid_armor");
			JsonObject values = new JsonObject();
			values.addProperty("enableHelmet", true);
			values.addProperty("enableBody", true);
			values.addProperty("enableLeggings", true);
			values.addProperty("enableBoots", true);
			payload.add("initialValues", values);

			var outcome = session.headlessEntry(PermissionProfile.WORKSPACE).execute(
					Command.of(ids.get(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload));

			assertEquals("rejected", outcome.result().status());
			assertEquals("WORKSPACE_PERSISTENCE_FAILED", outcome.result().diagnostics().getFirst().code());
			assertFalse(workspace.containsModElement("invalid_armor"));
			assertFalse(Files.exists(temporaryDirectory.resolve("elements/invalid_armor.mod.json")));
		}
	}

	@Test void copperbenchSavePreservesUnknownFieldsForEveryFirstPartyType() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("stage11_roundtrip");
		settings.setModName("Stage 11 Round Trip");
		settings.setCurrentGenerator("fabric-1.21.1");
		Workspace workspace = Workspace.createWorkspace(
				temporaryDirectory.resolve("stage11_roundtrip.mcreator").toFile(), settings);
		UUID workspaceId = UUID.fromString("11111111-1111-4111-8111-111111111181");
		AtomicLong sequence = new AtomicLong(2000);
		java.util.function.Supplier<UUID> ids = () -> uuid(sequence.incrementAndGet());
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
				new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), ids), Clock.systemUTC(), ids)) {
			var entry = session.headlessEntry(PermissionProfile.WORKSPACE);
			long revision = 0;
			for (String type : ElementCoverageCatalog.FIRST_PARTY_SLICE) {
				String name = "roundtrip_" + type;
				JsonObject createPayload = new JsonObject();
				createPayload.addProperty("clientMutationId", ids.get().toString());
				createPayload.addProperty("elementType", type);
				createPayload.addProperty("name", name);
				JsonObject values = new JsonObject();
				values.addProperty("pluginFutureField", "keep-" + type);
				if (type.equals("procedure"))
					values.addProperty("procedurexml", emptyProcedureXml("no_ext_trigger"));
				createPayload.add("initialValues", values);
				var created = entry.execute(Command.of(ids.get(), workspaceId, revision,
						Operation.CREATE_MOD_ELEMENT, createPayload));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				revision++;
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				Path definitionFile = temporaryDirectory.resolve("elements/" + name + ".mod.json");
				if (!type.equals("code")) {
					assertTrue(Files.isRegularFile(definitionFile));
					JsonObject definition = JsonParser.parseString(Files.readString(definitionFile)).getAsJsonObject();
					definition.addProperty("pluginOpaque", "keep-disk-" + type);
					Files.writeString(definitionFile, WorkspaceFileManager.gson.toJson(definition),
							StandardCharsets.UTF_8);
				}

				JsonObject updatePayload = new JsonObject();
				updatePayload.addProperty("clientMutationId", ids.get().toString());
				updatePayload.addProperty("elementId", elementId);
				JsonObject change = new JsonObject();
				change.addProperty("path", "/displayName");
				change.addProperty("value", "Updated " + type);
				com.google.gson.JsonArray changes = new com.google.gson.JsonArray();
				changes.add(change);
				updatePayload.add("changes", changes);
				var updated = entry.execute(Command.of(ids.get(), workspaceId, revision,
						Operation.UPDATE_MOD_ELEMENT, updatePayload));
				assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
				revision++;
				workspace.reloadFromFileSystem();

				ModElement upstream = workspace.getModElementByName(name);
				assertNotNull(upstream);
				assertTrue(WorkspaceFileManager.gson.toJson(upstream.getMetadata(
						dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceMutationGateway
								.ELEMENT_VALUES_METADATA)).contains("keep-" + type));
				if (!type.equals("code")) {
					JsonObject saved = JsonParser.parseString(Files.readString(definitionFile)).getAsJsonObject();
					assertEquals("keep-disk-" + type, saved.get("pluginOpaque").getAsString());
				}
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
