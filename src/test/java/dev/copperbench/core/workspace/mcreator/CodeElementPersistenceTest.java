/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeElementPersistenceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-02T07:20:00Z"), ZoneOffset.UTC);
	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void codeElementWritesAgentSuppliedJavaIntoTheUpstreamAssociatedSourceAndLocksIt() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("code_agent");
		settings.setModName("Code Agent");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("code_agent.mcreator");
		AtomicLong ids = new AtomicLong(200);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting custom code");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("22222222-2222-4222-8222-222222222222"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				String sourceCode = "package net.mcreator.code_agent;\n"
						+ "public final class AgentBehavior { public static int answer() { return 42; } }\n";
				JsonObject values = new JsonObject();
				values.addProperty("code", sourceCode);
				JsonObject payload = new JsonObject();
				payload.addProperty("clientMutationId", uuid(10).toString());
				payload.addProperty("elementType", "code");
				payload.addProperty("name", "agent_behavior");
				payload.add("initialValues", values);

				var outcome = session.uiEntry().execute(Command.of(uuid(20), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, payload));
				assertEquals("committed", outcome.result().status(), outcome.result().diagnostics().toString());

				var stored = workspace.getModElementByName("agent_behavior");
				assertTrue(stored.isCodeLocked());
				Path source = stored.getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath)
						.findFirst().orElseThrow();
				assertEquals(sourceCode, Files.readString(source, StandardCharsets.UTF_8));
			}
		}
	}

	@Test void codeElementPersistsUpdatesAndTracksMultiFileBundle() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("code_bundle_agent");
		settings.setModName("Code Bundle Agent");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("code_bundle_agent.mcreator");
		AtomicLong ids = new AtomicLong(260);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting custom code");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("22222222-2222-4222-8222-222222222223"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				JsonObject values = new JsonObject();
				values.addProperty("code", "package net.mcreator.code_bundle_agent;\npublic final class RuntimeRoot {}\n");
				com.google.gson.JsonArray files = new com.google.gson.JsonArray();
				JsonObject stateFile = new JsonObject();
				stateFile.addProperty("path", "runtime/SwordState.java");
				stateFile.addProperty("code", "package net.mcreator.code_bundle_agent.runtime;\npublic final class SwordState {}\n");
				files.add(stateFile);
				JsonObject controllerFile = new JsonObject();
				controllerFile.addProperty("path", "runtime/SwordController.java");
				controllerFile.addProperty("code", "package net.mcreator.code_bundle_agent.runtime;\npublic final class SwordController {}\n");
				files.add(controllerFile);
				values.add("codeFiles", files);
				JsonObject create = new JsonObject();
				create.addProperty("clientMutationId", uuid(40).toString());
				create.addProperty("elementType", "code");
				create.addProperty("name", "runtime_root");
				create.add("initialValues", values);

				var created = session.uiEntry().execute(Command.of(uuid(41), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, create));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				var stored = workspace.getModElementByName("runtime_root");
				Path primary = stored.getAssociatedFiles().stream().filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath).findFirst().orElseThrow();
				Path stateSource = primary.getParent().resolve("runtime/SwordState.java");
				Path controllerSource = primary.getParent().resolve("runtime/SwordController.java");
				assertTrue(Files.isRegularFile(stateSource));
				assertTrue(Files.isRegularFile(controllerSource));
				assertTrue(stored.getAssociatedFiles().stream().anyMatch(file -> file.toPath().equals(stateSource)));

				com.google.gson.JsonArray replacementFiles = new com.google.gson.JsonArray();
				JsonObject replacement = new JsonObject();
				replacement.addProperty("path", "runtime/SwordRuntime.java");
				replacement.addProperty("code", "package net.mcreator.code_bundle_agent.runtime;\npublic final class SwordRuntime {}\n");
				replacementFiles.add(replacement);
				JsonObject change = new JsonObject();
				change.addProperty("path", "/codeFiles");
				change.add("value", replacementFiles);
				com.google.gson.JsonArray changes = new com.google.gson.JsonArray();
				changes.add(change);
				JsonObject update = new JsonObject();
				update.addProperty("clientMutationId", uuid(42).toString());
				update.addProperty("elementId", elementId);
				update.add("changes", changes);
				var updated = session.uiEntry().execute(Command.of(uuid(43), session.workspaceId(), 1,
						Operation.UPDATE_MOD_ELEMENT, update));

				assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
				assertFalse(Files.exists(stateSource));
				assertFalse(Files.exists(controllerSource));
				Path runtimeSource = primary.getParent().resolve("runtime/SwordRuntime.java");
				assertTrue(Files.isRegularFile(runtimeSource));
				assertTrue(workspace.getModElementByName("runtime_root").getAssociatedFiles().stream()
						.anyMatch(file -> file.toPath().equals(runtimeSource)));
			}
		}
	}

	@Test void metadataOnlyCodeUpdatePreservesExternallyEditedBundleSource() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("code_external_edit");
		settings.setModName("Code External Edit");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("code_external_edit.mcreator");
		AtomicLong ids = new AtomicLong(280);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting custom code");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("22222222-2222-4222-8222-222222222224"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				JsonObject values = new JsonObject();
				values.addProperty("code", "package net.mcreator.code_external_edit;\npublic final class RuntimeRoot {}\n");
				com.google.gson.JsonArray files = new com.google.gson.JsonArray();
				JsonObject helper = new JsonObject();
				helper.addProperty("path", "runtime/ExternalHelper.java");
				helper.addProperty("code", "package net.mcreator.code_external_edit.runtime;\npublic final class ExternalHelper {}\n");
				files.add(helper);
				values.add("codeFiles", files);
				JsonObject create = new JsonObject();
				create.addProperty("clientMutationId", uuid(50).toString());
				create.addProperty("elementType", "code");
				create.addProperty("name", "runtime_root");
				create.add("initialValues", values);

				var created = session.uiEntry().execute(Command.of(uuid(51), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, create));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				Path primary = workspace.getModElementByName("runtime_root").getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath).findFirst().orElseThrow();
				Path helperSource = primary.getParent().resolve("runtime/ExternalHelper.java");
				String primaryExternalEdit = "package net.mcreator.code_external_edit;\n"
						+ "public final class RuntimeRoot { public static final int IDE_EDIT = 3; }\n";
				String externalEdit = "package net.mcreator.code_external_edit.runtime;\n"
						+ "public final class ExternalHelper { public static final int IDE_EDIT = 7; }\n";
				Files.writeString(primary, primaryExternalEdit, StandardCharsets.UTF_8);
				Files.writeString(helperSource, externalEdit, StandardCharsets.UTF_8);
				var mapped = new MCreatorWorkspaceStateMapper().map(workspace,
						new ProductMetadataManager.Metadata(1, session.workspaceId(), 1));
				JsonObject mappedValues = mapped.element(UUID.fromString(elementId)).values();
				assertEquals(primaryExternalEdit, mappedValues.get("code").getAsString(),
						"Workspace projection must use the live primary source after an IDE edit");
				assertEquals(externalEdit, mappedValues.getAsJsonArray("codeFiles").get(0).getAsJsonObject()
						.get("code").getAsString(),
						"Workspace projection must use the live bundle source after an IDE edit");
				assertTrue(workspace.getGenerator().generateElement(
						workspace.getModElementByName("runtime_root").getGeneratableElement()),
						"Regenerating a code-locked custom element should remain a successful no-op");
				assertEquals(primaryExternalEdit, Files.readString(primary, StandardCharsets.UTF_8),
						"Regeneration must not reclaim an externally edited code-locked primary source");
				assertEquals(externalEdit, Files.readString(helperSource, StandardCharsets.UTF_8),
						"Regeneration must not reclaim an externally edited code-locked bundle source");

				JsonObject change = new JsonObject();
				change.addProperty("path", "/displayName");
				change.addProperty("value", "Runtime Root Renamed");
				com.google.gson.JsonArray changes = new com.google.gson.JsonArray();
				changes.add(change);
				JsonObject update = new JsonObject();
				update.addProperty("clientMutationId", uuid(52).toString());
				update.addProperty("elementId", elementId);
				update.add("changes", changes);

				var updated = session.uiEntry().execute(Command.of(uuid(53), session.workspaceId(), 1,
						Operation.UPDATE_MOD_ELEMENT, update));
				assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
				assertEquals(primaryExternalEdit, Files.readString(primary, StandardCharsets.UTF_8),
						"Metadata-only changes must not replay stale primary-source metadata over IDE edits");
				assertEquals(externalEdit, Files.readString(helperSource, StandardCharsets.UTF_8),
						"Metadata-only changes must not replay stale codeFiles metadata over IDE edits");
			}
		}
	}

	@Test void recoveryPointRestoresLiveExternallyEditedPrimaryAndBundleBytes() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("code_recovery_bytes");
		settings.setModName("Code Recovery Bytes");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("code_recovery_bytes.mcreator");
		AtomicLong ids = new AtomicLong(320);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting custom code");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					store -> new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				JsonObject values = new JsonObject();
				values.addProperty("code", "package net.mcreator.code_recovery_bytes;\npublic final class RuntimeRoot {}\n");
				com.google.gson.JsonArray files = new com.google.gson.JsonArray();
				JsonObject helper = new JsonObject();
				helper.addProperty("path", "runtime/RecoveryHelper.java");
				helper.addProperty("code", "package net.mcreator.code_recovery_bytes.runtime;\npublic final class RecoveryHelper {}\n");
				files.add(helper);
				values.add("codeFiles", files);
				JsonObject create = new JsonObject();
				create.addProperty("clientMutationId", uuid(70).toString());
				create.addProperty("elementType", "code");
				create.addProperty("name", "runtime_root");
				create.add("initialValues", values);

				var created = session.uiEntry().execute(Command.of(uuid(71), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, create));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				Path primary = workspace.getModElementByName("runtime_root").getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath).findFirst().orElseThrow();
				Path helperSource = primary.getParent().resolve("runtime/RecoveryHelper.java");
				String recoveryPrimary = "package net.mcreator.code_recovery_bytes;\n"
						+ "public final class RuntimeRoot { public static final int IDE_BASELINE = 11; }\n";
				String recoveryHelper = "package net.mcreator.code_recovery_bytes.runtime;\n"
						+ "public final class RecoveryHelper { public static final int IDE_BASELINE = 13; }\n";
				Files.writeString(primary, recoveryPrimary, StandardCharsets.UTF_8);
				Files.writeString(helperSource, recoveryHelper, StandardCharsets.UTF_8);

				JsonObject pointPayload = new JsonObject();
				pointPayload.addProperty("label", "External IDE baseline");
				var point = session.uiEntry().execute(Command.of(uuid(72), session.workspaceId(), 1,
						Operation.CREATE_RECOVERY_POINT, pointPayload));
				assertEquals("committed", point.result().status(), point.result().diagnostics().toString());
				String recoveryPointId = point.result().recoveryPointId();
				assertTrue(recoveryPointId != null && !recoveryPointId.isBlank());

				Files.writeString(primary, recoveryPrimary.replace("11", "21"), StandardCharsets.UTF_8);
				Files.writeString(helperSource, recoveryHelper.replace("13", "23"), StandardCharsets.UTF_8);
				Path addedAfterPoint = primary.getParent().resolve("runtime/AddedAfterPoint.java");
				Files.writeString(addedAfterPoint,
						"package net.mcreator.code_recovery_bytes.runtime;\npublic final class AddedAfterPoint {}\n",
						StandardCharsets.UTF_8);

				JsonObject restorePayload = new JsonObject();
				restorePayload.addProperty("recoveryPointId", recoveryPointId);
				restorePayload.addProperty("userApproved", true);
				var restored = session.uiEntry().execute(Command.of(uuid(73), session.workspaceId(), 1,
						Operation.RESTORE_RECOVERY_POINT, restorePayload));
				assertEquals("committed", restored.result().status(), restored.result().diagnostics().toString());
				assertEquals(2, restored.result().newRevision());
				assertEquals(recoveryPrimary, Files.readString(primary, StandardCharsets.UTF_8),
						"restore must replay the live external primary source bytes captured by the recovery point");
				assertEquals(recoveryHelper, Files.readString(helperSource, StandardCharsets.UTF_8),
						"restore must replay the live external helper source bytes captured by the recovery point");
				assertFalse(Files.exists(addedAfterPoint),
						"restore must remove a source file introduced after the target recovery point");
			}
		}
	}

	@Test void stalePrimarySourceWriteIsRejectedUntilCallerRefreshesItsFingerprint() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("code_stale_source");
		settings.setModName("Code Stale Source");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("code_stale_source.mcreator");
		AtomicLong ids = new AtomicLong(360);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting custom code");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("22222222-2222-4222-8222-222222222226"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				String initial = "package net.mcreator.code_stale_source;\npublic final class RuntimeRoot {}\n";
				JsonObject values = new JsonObject();
				values.addProperty("code", initial);
				JsonObject create = new JsonObject();
				create.addProperty("clientMutationId", uuid(80).toString());
				create.addProperty("elementType", "code");
				create.addProperty("name", "runtime_root");
				create.add("initialValues", values);
				var created = session.uiEntry().execute(Command.of(uuid(81), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, create));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				Path primary = workspace.getModElementByName("runtime_root").getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath).findFirst().orElseThrow();

				String external = "package net.mcreator.code_stale_source;\n"
						+ "public final class RuntimeRoot { public static final int IDE_EDIT = 17; }\n";
				Files.writeString(primary, external, StandardCharsets.UTF_8);
				String agent = "package net.mcreator.code_stale_source;\n"
						+ "public final class RuntimeRoot { public static final int AGENT_EDIT = 19; }\n";
				JsonObject codeChange = new JsonObject();
				codeChange.addProperty("path", "/code");
				codeChange.addProperty("value", agent);
				com.google.gson.JsonArray staleChanges = new com.google.gson.JsonArray();
				staleChanges.add(codeChange);
				JsonObject staleUpdate = new JsonObject();
				staleUpdate.addProperty("clientMutationId", uuid(82).toString());
				staleUpdate.addProperty("elementId", elementId);
				staleUpdate.add("changes", staleChanges);
				var stale = session.uiEntry().execute(Command.of(uuid(83), session.workspaceId(), 1,
						Operation.UPDATE_MOD_ELEMENT, staleUpdate));

				assertEquals("rejected", stale.result().status(), stale.result().diagnostics().toString());
				assertEquals("SOURCE_CONTENT_CONFLICT", stale.result().diagnostics().getFirst().code());
				assertTrue(stale.result().diagnostics().getFirst().path().endsWith("/code"));
				assertEquals(external, Files.readString(primary, StandardCharsets.UTF_8),
						"A stale Agent write must not overwrite the IDE edit");

				var live = new MCreatorWorkspaceStateMapper().map(workspace,
						new ProductMetadataManager.Metadata(1, session.workspaceId(), 1));
				String liveFingerprint = live.element(UUID.fromString(elementId)).values()
						.getAsJsonObject("sourceFingerprints").get("$primary").getAsString();
				JsonObject fingerprintChange = new JsonObject();
				fingerprintChange.addProperty("path", "/sourceFingerprints/$primary");
				fingerprintChange.addProperty("value", liveFingerprint);
				com.google.gson.JsonArray retryChanges = new com.google.gson.JsonArray();
				retryChanges.add(fingerprintChange);
				retryChanges.add(codeChange.deepCopy());
				JsonObject retryUpdate = new JsonObject();
				retryUpdate.addProperty("clientMutationId", uuid(84).toString());
				retryUpdate.addProperty("elementId", elementId);
				retryUpdate.add("changes", retryChanges);
				var retried = session.uiEntry().execute(Command.of(uuid(85), session.workspaceId(), 1,
						Operation.UPDATE_MOD_ELEMENT, retryUpdate));

				assertEquals("committed", retried.result().status(), retried.result().diagnostics().toString());
				assertEquals(2, retried.result().newRevision());
				assertEquals(agent, Files.readString(primary, StandardCharsets.UTF_8));
			}
		}
	}

	@Test void unrelatedExternalHelperEditDoesNotBlockAnotherHelperButStaleTargetIsRejected() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("code_helper_fingerprints");
		settings.setModName("Code Helper Fingerprints");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("code_helper_fingerprints.mcreator");
		AtomicLong ids = new AtomicLong(380);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting custom code");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("22222222-2222-4222-8222-222222222227"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				String helperA = "package net.mcreator.code_helper_fingerprints.runtime;\npublic final class HelperA {}\n";
				String helperB = "package net.mcreator.code_helper_fingerprints.runtime;\npublic final class HelperB {}\n";
				JsonObject values = new JsonObject();
				values.addProperty("code", "package net.mcreator.code_helper_fingerprints;\npublic final class RuntimeRoot {}\n");
				com.google.gson.JsonArray files = new com.google.gson.JsonArray();
				JsonObject a = new JsonObject();
				a.addProperty("path", "runtime/HelperA.java");
				a.addProperty("code", helperA);
				files.add(a);
				JsonObject b = new JsonObject();
				b.addProperty("path", "runtime/HelperB.java");
				b.addProperty("code", helperB);
				files.add(b);
				values.add("codeFiles", files);
				JsonObject create = new JsonObject();
				create.addProperty("clientMutationId", uuid(90).toString());
				create.addProperty("elementType", "code");
				create.addProperty("name", "runtime_root");
				create.add("initialValues", values);
				var created = session.uiEntry().execute(Command.of(uuid(91), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, create));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				Path primary = workspace.getModElementByName("runtime_root").getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath).findFirst().orElseThrow();
				Path helperASource = primary.getParent().resolve("runtime/HelperA.java");
				Path helperBSource = primary.getParent().resolve("runtime/HelperB.java");

				String externalA = helperA.replace("{}", "{ public static final int IDE_EDIT = 23; }");
				Files.writeString(helperASource, externalA, StandardCharsets.UTF_8);
				String agentB = helperB.replace("{}", "{ public static final int AGENT_EDIT = 29; }");
				com.google.gson.JsonArray changeOnlyB = new com.google.gson.JsonArray();
				changeOnlyB.add(a.deepCopy());
				JsonObject changedB = b.deepCopy();
				changedB.addProperty("code", agentB);
				changeOnlyB.add(changedB);
				JsonObject bundleChange = new JsonObject();
				bundleChange.addProperty("path", "/codeFiles");
				bundleChange.add("value", changeOnlyB);
				com.google.gson.JsonArray changes = new com.google.gson.JsonArray();
				changes.add(bundleChange);
				JsonObject update = new JsonObject();
				update.addProperty("clientMutationId", uuid(92).toString());
				update.addProperty("elementId", elementId);
				update.add("changes", changes);
				var updated = session.uiEntry().execute(Command.of(uuid(93), session.workspaceId(), 1,
						Operation.UPDATE_MOD_ELEMENT, update));

				assertEquals("committed", updated.result().status(), updated.result().diagnostics().toString());
				assertEquals(externalA, Files.readString(helperASource, StandardCharsets.UTF_8),
						"An unrelated externally edited helper must not be rewritten or block another helper's update");
				assertEquals(agentB, Files.readString(helperBSource, StandardCharsets.UTF_8));

				String externalB = agentB.replace("29", "31");
				Files.writeString(helperBSource, externalB, StandardCharsets.UTF_8);
				com.google.gson.JsonArray staleBundle = new com.google.gson.JsonArray();
				JsonObject liveA = a.deepCopy();
				liveA.addProperty("code", externalA);
				staleBundle.add(liveA);
				JsonObject staleB = changedB.deepCopy();
				staleB.addProperty("code", agentB.replace("29", "37"));
				staleBundle.add(staleB);
				JsonObject staleBundleChange = new JsonObject();
				staleBundleChange.addProperty("path", "/codeFiles");
				staleBundleChange.add("value", staleBundle);
				com.google.gson.JsonArray staleChanges = new com.google.gson.JsonArray();
				staleChanges.add(staleBundleChange);
				JsonObject staleUpdate = new JsonObject();
				staleUpdate.addProperty("clientMutationId", uuid(94).toString());
				staleUpdate.addProperty("elementId", elementId);
				staleUpdate.add("changes", staleChanges);
				var stale = session.uiEntry().execute(Command.of(uuid(95), session.workspaceId(), 2,
						Operation.UPDATE_MOD_ELEMENT, staleUpdate));

				assertEquals("rejected", stale.result().status(), stale.result().diagnostics().toString());
				assertEquals("SOURCE_CONTENT_CONFLICT", stale.result().diagnostics().getFirst().code());
				assertTrue(stale.result().diagnostics().getFirst().path().endsWith("/codeFiles"));
				assertEquals(externalB, Files.readString(helperBSource, StandardCharsets.UTF_8),
						"A stale helper write must preserve the newer IDE edit");
			}
		}
	}

	@Test void codeBundleCannotClaimAnotherElementsPrimarySource() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("code_owner_collision");
		settings.setModName("Code Owner Collision");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("code_owner_collision.mcreator");
		AtomicLong ids = new AtomicLong(340);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting custom code");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("22222222-2222-4222-8222-222222222225"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				String ownerASource = "package net.mcreator.code_owner_collision;\npublic final class OwnerA {}\n";
				JsonObject ownerAValues = new JsonObject();
				ownerAValues.addProperty("code", ownerASource);
				JsonObject createA = new JsonObject();
				createA.addProperty("clientMutationId", uuid(60).toString());
				createA.addProperty("elementType", "code");
				createA.addProperty("name", "owner_a");
				createA.add("initialValues", ownerAValues);
				var createdA = session.uiEntry().execute(Command.of(uuid(61), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, createA));
				assertEquals("committed", createdA.result().status(), createdA.result().diagnostics().toString());
				Path ownerAPrimary = workspace.getModElementByName("owner_a").getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath).findFirst().orElseThrow();

				JsonObject ownerBValues = new JsonObject();
				ownerBValues.addProperty("code", "package net.mcreator.code_owner_collision;\npublic final class OwnerB {}\n");
				com.google.gson.JsonArray files = new com.google.gson.JsonArray();
				JsonObject collision = new JsonObject();
				collision.addProperty("path", ownerAPrimary.getFileName().toString());
				collision.addProperty("code", "// must never overwrite owner A\n");
				files.add(collision);
				ownerBValues.add("codeFiles", files);
				JsonObject createB = new JsonObject();
				createB.addProperty("clientMutationId", uuid(62).toString());
				createB.addProperty("elementType", "code");
				createB.addProperty("name", "owner_b");
				createB.add("initialValues", ownerBValues);

				var beforePlan = new MCreatorWorkspaceStateMapper().map(workspace,
						new ProductMetadataManager.Metadata(1, session.workspaceId(), 1));
				var afterPlan = beforePlan.copy();
				afterPlan.addElement(new WorkspaceState.Element(uuid(64), "code", "owner_b", "Owner B", "valid",
						"generated", CLOCK.instant(), ownerBValues));
				var preflightFailure = assertThrows(IllegalStateException.class,
						() -> new MCreatorWorkspaceMutationGateway(workspace, session.workspaceId())
								.validateWorkspacePlan(beforePlan, afterPlan));
				assertTrue(preflightFailure.getMessage().contains("owner_a"), preflightFailure.getMessage());

				var createdB = session.uiEntry().execute(Command.of(uuid(63), session.workspaceId(), 1,
						Operation.CREATE_MOD_ELEMENT, createB));
				assertEquals("rejected", createdB.result().status(),
						"A second managed element must not claim another element's physical source file");
				assertEquals(ownerASource, Files.readString(ownerAPrimary, StandardCharsets.UTF_8));
				assertNull(workspace.getModElementByName("owner_b"),
						"A rejected ownership conflict must roll the new element out of the upstream workspace");
				assertFalse(Files.exists(ownerAPrimary.getParent().resolve("owner_b.java")),
						"A rejected ownership conflict must not leave an orphan primary source behind");
			}
		}
	}

	@Test void deletingGeneratedElementRefreshesSharedGeneratorRegistrations() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("delete_refresh");
		settings.setModName("Delete Refresh");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = root.resolve("delete_refresh.mcreator");
		AtomicLong ids = new AtomicLong(300);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase(), "Generator base must exist before persisting elements");
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
					UUID.fromString("33333333-3333-4333-8333-333333333333"),
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				JsonObject values = new JsonObject();
				values.addProperty("texture", "minecraft:barrier");
				JsonObject create = new JsonObject();
				create.addProperty("clientMutationId", uuid(30).toString());
				create.addProperty("elementType", "item");
				create.addProperty("name", "temporary_blade");
				create.add("initialValues", values);

				var created = session.uiEntry().execute(Command.of(uuid(31), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, create));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				String elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
				var stored = workspace.getModElementByName("temporary_blade");
				Path elementSource = stored.getAssociatedFiles().stream()
						.filter(file -> file.getName().endsWith(".java"))
						.map(java.io.File::toPath)
						.findFirst().orElseThrow();
				String generatedType = elementSource.getFileName().toString().replaceFirst("\\.java$", "");
				List<Path> sharedReferencesBefore = javaFilesContaining(root.resolve("src/main/java"), generatedType,
						elementSource);
				assertFalse(sharedReferencesBefore.isEmpty(),
						"The generated item must be referenced by at least one shared base/registry source before delete");

				JsonObject delete = new JsonObject();
				delete.addProperty("clientMutationId", uuid(32).toString());
				delete.addProperty("elementId", elementId);
				var deleted = session.uiEntry().execute(Command.of(uuid(33), session.workspaceId(), 1,
						Operation.DELETE_MOD_ELEMENT, delete));

				assertEquals("committed", deleted.result().status(), deleted.result().diagnostics().toString());
				assertFalse(Files.exists(elementSource));
				assertTrue(javaFilesContaining(root.resolve("src/main/java"), generatedType, null).isEmpty(),
						"Deleting an element must regenerate shared sources so no generator-owned reference survives");
			}
		}
	}

	private static List<Path> javaFilesContaining(Path sourceRoot, String needle, Path excluded) throws Exception {
		if (!Files.isDirectory(sourceRoot)) return List.of();
		Path normalizedExcluded = excluded == null ? null : excluded.toAbsolutePath().normalize();
		try (var paths = Files.walk(sourceRoot)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".java"))
					.filter(path -> normalizedExcluded == null || !path.toAbsolutePath().normalize().equals(normalizedExcluded))
					.filter(path -> {
						try {
							return Files.readString(path, StandardCharsets.UTF_8).contains(needle);
						} catch (Exception exception) {
							throw new IllegalStateException(exception);
						}
					}).toList();
		}
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
