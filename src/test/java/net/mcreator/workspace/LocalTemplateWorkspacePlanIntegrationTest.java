package net.mcreator.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceMutationGateway;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTemplateWorkspacePlanIntegrationTest {

	@TempDir Path temporaryDirectory;

	@BeforeAll static void initializeUpstreamRuntimeMinimum() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test
	@ResourceLock(Resources.SYSTEM_PROPERTIES)
	void procedureAndAssetRoundTripThroughSignedWorkspacePlan() throws Exception {
		String previousTemplateDirectory = System.getProperty("copperbench.templates.dir");
		Path templateDirectory = temporaryDirectory.resolve("local-templates");
		System.setProperty("copperbench.templates.dir", templateDirectory.toString());
		try {
			Path sourceDirectory = temporaryDirectory.resolve("source");
			Path targetDirectory = temporaryDirectory.resolve("target");
			Files.createDirectories(sourceDirectory);
			Files.createDirectories(targetDirectory);
			String assetPath = "src/main/resources/assets/template_test/textures/item/reusable.bin";
			byte[] assetBytes = "template-asset".getBytes(StandardCharsets.UTF_8);
			String sourceElementId;

			AtomicLong sequence = new AtomicLong(9000);
			Supplier<UUID> ids = () -> UUID.nameUUIDFromBytes(
					("local-template-" + sequence.incrementAndGet()).getBytes(StandardCharsets.UTF_8));
			Clock clock = Clock.systemUTC();

			try (Workspace source = Workspace.createWorkspace(sourceDirectory.resolve("source.mcreator").toFile(),
					settings("template_source", "Template Source"));
					MCreatorWorkspaceSession sourceSession = MCreatorWorkspaceSession.attach(source,
							store -> new InMemoryWorkspaceTaskGateway(clock, ids), clock, ids)) {
				var sourceEntry = sourceSession.mcpEntry(PermissionProfile.WORKSPACE);
				JsonObject createProcedure = new JsonObject();
				createProcedure.addProperty("clientMutationId", ids.get().toString());
				createProcedure.addProperty("elementType", "procedure");
				createProcedure.addProperty("name", "reusable_procedure");
				createProcedure.add("initialValues", new JsonObject());
				var created = sourceEntry.execute(Command.of(ids.get(), sourceSession.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, createProcedure));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				sourceElementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();

				Path sourceAsset = sourceDirectory.resolve(assetPath);
				Files.createDirectories(sourceAsset.getParent());
				Files.write(sourceAsset, assetBytes);

				JsonObject createTemplate = new JsonObject();
				createTemplate.addProperty("clientMutationId", ids.get().toString());
				createTemplate.addProperty("templateName", "reusable_bundle");
				createTemplate.addProperty("description", "Reusable Procedure plus one local asset");
				JsonArray elementIds = new JsonArray();
				elementIds.add(sourceElementId);
				createTemplate.add("elementIds", elementIds);
				JsonArray assetPaths = new JsonArray();
				assetPaths.add(assetPath);
				createTemplate.add("assetPaths", assetPaths);
				var templated = sourceEntry.execute(Command.of(ids.get(), sourceSession.workspaceId(), 1,
						Operation.CREATE_LOCAL_TEMPLATE, createTemplate));
				assertEquals("committed", templated.result().status(), templated.result().diagnostics().toString());
				assertEquals(1, templated.result().newRevision());
				assertEquals(1, templated.result().data().getAsJsonObject().get("elementCount").getAsInt());
				assertEquals(1, templated.result().data().getAsJsonObject().get("assetCount").getAsInt());
				assertTrue(Files.isRegularFile(templateDirectory.resolve("reusable_bundle.json")));
			}

			try (Workspace target = Workspace.createWorkspace(targetDirectory.resolve("target.mcreator").toFile(),
					settings("template_target", "Template Target"));
					MCreatorWorkspaceSession targetSession = MCreatorWorkspaceSession.attach(target,
							store -> new InMemoryWorkspaceTaskGateway(clock, ids), clock, ids)) {
				var targetEntry = targetSession.mcpEntry(PermissionProfile.WORKSPACE);
				var readOnlyEntry = targetSession.mcpEntry(PermissionProfile.READ_ONLY);
				var deniedTemplateWrite = readOnlyEntry.execute(Command.of(ids.get(), targetSession.workspaceId(), 0,
						Operation.CREATE_LOCAL_TEMPLATE, new JsonObject()));
				assertEquals("rejected", deniedTemplateWrite.result().status());
				assertEquals("PERMISSION_DENIED", deniedTemplateWrite.result().diagnostics().getFirst().code());
				assertFalse(Files.exists(templateDirectory.resolve("read_only_write.json")));
				var listed = targetEntry.query(Query.of(ids.get(), targetSession.workspaceId(),
						Operation.LIST_LOCAL_TEMPLATES, new JsonObject()));
				assertEquals("succeeded", listed.status(), listed.diagnostics().toString());
				assertEquals(1, listed.data().getAsJsonObject().get("templateCount").getAsInt());

				Path targetAsset = targetDirectory.resolve(assetPath);
				Files.createDirectories(targetAsset.getParent());
				Files.writeString(targetAsset, "pre-existing", StandardCharsets.UTF_8);
				JsonObject conflictPreviewPayload = previewPayload("reusable_bundle", "template-conflict");
				var conflictPreview = targetEntry.query(Query.of(ids.get(), targetSession.workspaceId(),
						Operation.PREVIEW_LOCAL_TEMPLATE_INSTANTIATION, conflictPreviewPayload));
				assertEquals("failed", conflictPreview.status(), conflictPreview.diagnostics().toString());
				assertEquals("WORKSPACE_PLAN_SOURCE_CONFLICT", conflictPreview.diagnostics().getFirst().code());
				assertFalse(target.containsModElement("reusable_procedure"));
				Files.delete(targetAsset);

				JsonObject previewPayload = previewPayload("reusable_bundle", "template-apply");
				var preview = targetEntry.query(Query.of(ids.get(), targetSession.workspaceId(),
						Operation.PREVIEW_LOCAL_TEMPLATE_INSTANTIATION, previewPayload));
				assertEquals("succeeded", preview.status(), preview.diagnostics().toString());
				JsonObject plan = preview.data().getAsJsonObject();
				assertEquals(1, plan.get("operationCount").getAsInt());
				assertEquals(1, plan.getAsJsonArray("templateAssets").size());
				assertEquals("reusable_bundle", plan.getAsJsonObject("template").get("name").getAsString());
				assertTrue(plan.get("requireRecoveryPoint").getAsBoolean());
				assertTrue(plan.getAsJsonArray("changedPaths").asList().stream()
						.anyMatch(path -> path.getAsString().equals("/files/" + assetPath)));

				JsonObject tamperedPlan = plan.deepCopy();
				tamperedPlan.getAsJsonArray("templateAssets").get(0).getAsJsonObject()
						.addProperty("contentBase64", "AA==");
				JsonObject tamperedApplyPayload = new JsonObject();
				tamperedApplyPayload.addProperty("clientMutationId", ids.get().toString());
				tamperedApplyPayload.add("plan", tamperedPlan);
				var tampered = targetEntry.execute(Command.of(ids.get(), targetSession.workspaceId(), 0,
						Operation.APPLY_WORKSPACE_PLAN, tamperedApplyPayload));
				assertEquals("rejected", tampered.result().status());
				assertEquals("WORKSPACE_PLAN_INVALID", tampered.result().diagnostics().getFirst().code());
				assertFalse(target.containsModElement("reusable_procedure"));
				assertFalse(Files.exists(targetAsset));

				JsonObject applyPayload = new JsonObject();
				applyPayload.addProperty("clientMutationId", ids.get().toString());
				applyPayload.add("plan", plan.deepCopy());
				var applied = targetEntry.execute(Command.of(ids.get(), targetSession.workspaceId(), 0,
						Operation.APPLY_WORKSPACE_PLAN, applyPayload));
				assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
				assertEquals(1, applied.result().newRevision());
				assertNotNull(applied.result().recoveryPointId());
				assertArrayEquals(assetBytes, Files.readAllBytes(targetAsset));

				ModElement reused = target.getModElementByName("reusable_procedure");
				assertNotNull(reused);
				assertNotEquals(sourceElementId, String.valueOf(
						reused.getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA)));
				target.reloadFromFileSystem();
				assertNotNull(target.getModElementByName("reusable_procedure"));
				assertArrayEquals(assetBytes, Files.readAllBytes(targetAsset));
			}
		} finally {
			if (previousTemplateDirectory == null) System.clearProperty("copperbench.templates.dir");
			else System.setProperty("copperbench.templates.dir", previousTemplateDirectory);
		}
	}

	private static JsonObject previewPayload(String templateName, String idempotencyKey) {
		JsonObject payload = new JsonObject();
		payload.addProperty("templateName", templateName);
		payload.addProperty("expectedRevision", 0);
		payload.addProperty("idempotencyKey", idempotencyKey);
		return payload;
	}

	private static WorkspaceSettings settings(String modId, String modName) {
		WorkspaceSettings settings = new WorkspaceSettings(modId);
		settings.setModName(modName);
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("neoforge-1.21.8");
		return settings;
	}
}
