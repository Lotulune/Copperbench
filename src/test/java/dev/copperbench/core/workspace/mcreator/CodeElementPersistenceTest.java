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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
