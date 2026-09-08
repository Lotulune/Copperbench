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
