/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.testing.McreatorTestRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceEnvironmentContextTest {

	@TempDir Path temporaryDirectory;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void allEightJavaTracksExposeContextFromTheirActualExecutionBackends() throws Exception {
		Map<String, Integer> javaRelease = Map.of(
				"fabric-1.20.1", 17, "neoforge-1.20.1", 17,
				"fabric-1.21.1", 21, "neoforge-1.21.1", 21,
				"fabric-26.1.2", 25, "neoforge-26.1.2", 25,
				"fabric-26.2", 25, "neoforge-26.2", 25);
		Map<String, String> loaderVersion = Map.of(
				"fabric-1.20.1", "0.15.11", "neoforge-1.20.1", "47.1.106",
				"fabric-1.21.1", "0.19.3", "neoforge-1.21.1", "21.1.232",
				"fabric-26.1.2", "0.19.3", "neoforge-26.1.2", "26.1.2.95",
				"fabric-26.2", "0.19.3", "neoforge-26.2", "26.2.0.63");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		Map<UUID, Path> roots = new LinkedHashMap<>();
		AtomicLong sequence = new AtomicLong(800);
		Supplier<UUID> ids = () -> new UUID(0x14b0000000000000L, sequence.incrementAndGet());
		Path distributionRoot = Path.of(".").toAbsolutePath().normalize();

		try (LoaderRoutingWorkspaceTaskGateway gateway = new LoaderRoutingWorkspaceTaskGateway(store,
				roots::get, distributionRoot, Clock.systemUTC(), ids)) {
			for (String generatorId : javaRelease.keySet()) {
				UUID workspaceId = ids.get();
				Path root = temporaryDirectory.resolve(generatorId.replace('.', '_'))
						.toAbsolutePath().normalize();
				roots.put(workspaceId, root);
				JsonObject generator = new JsonObject();
				generator.addProperty("id", generatorId);
				generator.addProperty("loader", generatorId.substring(0, generatorId.indexOf('-')));
				generator.addProperty("minecraftVersion", generatorId.substring(generatorId.indexOf('-') + 1));
				store.register(new WorkspaceState(workspaceId, "Environment " + generatorId, "mod", 0, false,
						generator, new JsonObject(), List.of()));

				JsonObject environment = gateway.environment(workspaceId);
				assertEquals(generatorId, environment.get("generatorId").getAsString(), generatorId);
				assertEquals(loaderVersion.get(generatorId), environment.get("loaderVersion").getAsString(), generatorId);
				assertEquals(javaRelease.get(generatorId),
						environment.getAsJsonObject("java").get("requiredRelease").getAsInt(), generatorId);
				assertFalse(environment.getAsJsonObject("java").get("home").getAsString().isBlank(), generatorId);
				assertEquals(root.resolve("src/main/java").toString(), environment.get("sourceRoot").getAsString(), generatorId);
				assertEquals(root.resolve("src/main/resources").toString(),
						environment.get("resourceRoot").getAsString(), generatorId);
				assertEquals(javaRelease.get(generatorId) == 17 ? "gradle-8.8-bin.zip" : "gradle-9.7.0-bin.zip",
						environment.getAsJsonObject("gradle").get("distribution").getAsString(), generatorId);
				assertEquals("build", environment.getAsJsonObject("gradle").getAsJsonObject("tasks")
						.get("build").getAsString(), generatorId);
				assertEquals("runClient", environment.getAsJsonObject("gradle").getAsJsonObject("tasks")
						.get("runClient").getAsString(), generatorId);
			}

			UUID queryWorkspaceId = roots.keySet().stream().filter(id ->
					"fabric-1.21.1".equals(store.read(id).orElseThrow().generator().get("id").getAsString()))
					.findFirst().orElseThrow();
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, gateway,
					WorkspaceMutationGateway.noOp(), null, null, roots::get, Clock.systemUTC(), ids);
			HeadlessWorkspaceEntryAdapter headless = new HeadlessWorkspaceEntryAdapter(service,
					PermissionProfile.WORKSPACE);
			var result = headless.query(Query.of(ids.get(), queryWorkspaceId, Operation.GET_WORKSPACE_ENVIRONMENT,
					new JsonObject()));
			assertEquals("succeeded", result.status(), result.diagnostics().toString());
			JsonObject data = result.data().getAsJsonObject();
			assertEquals("fabric-1.21.1", data.getAsJsonObject("execution").get("generatorId").getAsString());
			assertEquals(21, data.getAsJsonObject("execution").getAsJsonObject("java")
					.get("requiredRelease").getAsInt());
			assertTrue(data.getAsJsonObject("execution").has("host"));
			assertTrue(data.getAsJsonObject("execution").getAsJsonObject("host").has("desktop"));
			assertTrue(data.getAsJsonObject("execution").getAsJsonObject("host").getAsJsonObject("desktop")
					.has("certificationRole"));
			assertTrue(data.getAsJsonObject("agentWorkflow").get("nativeFilesAuthoritative").getAsBoolean());
			assertTrue(data.getAsJsonObject("agentWorkflow").get("structuredElementsOptional").getAsBoolean());
			assertEquals("build_workspace", data.getAsJsonObject("agentWorkflow").get("buildOperation").getAsString());
		}
	}
}
