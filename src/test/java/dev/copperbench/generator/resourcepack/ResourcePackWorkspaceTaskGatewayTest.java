/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.resourcepack;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.GradleProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackWorkspaceTaskGatewayTest {

	@TempDir Path root;

	@Test void exportsTheBundledResourcePackArtifactWithoutUsingModJarRules() throws Exception {
		UUID workspaceId = UUID.fromString("44444444-4444-4444-8444-444444444444");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(resourcePackWorkspace(workspaceId));
		Path workspaceRoot = root.resolve("resource-pack");
		Files.createDirectories(workspaceRoot.resolve("src/main"));
		Files.writeString(workspaceRoot.resolve("src/main/pack.mcmeta"),
				"{\"pack\":{\"pack_format\":34,\"description\":\"Copper\"}}");
		Files.write(workspaceRoot.resolve("src/main/pack.png"), new byte[] { 1, 2, 3 });

		GradleProcessRunner process = (workingDirectory, arguments, timeout, output) -> {
			assertEquals(List.of("build"), arguments);
			Path artifact = workingDirectory.resolve("build/export/export.zip");
			Files.createDirectories(artifact.getParent());
			Files.writeString(artifact, "resource-pack-zip");
			return new GradleProcessRunner.ProcessResult(0, true);
		};
		try (var tasks = new ResourcePackWorkspaceTaskGateway(store, ignored -> workspaceRoot, root, Clock.systemUTC(),
				UUID::randomUUID, process)) {
			JsonObject payload = new JsonObject();
			payload.addProperty("output", "dist/copper_pack.zip");
			UUID taskId = UUID.fromString(tasks.start(workspaceId, Operation.EXPORT_WORKSPACE, payload)
					.get("id").getAsString());
			await(tasks, workspaceId, taskId);
			assertTrue(Files.isRegularFile(workspaceRoot.resolve("dist/copper_pack.zip")));
			assertEquals("resource-pack-zip", Files.readString(workspaceRoot.resolve("dist/copper_pack.zip")));
		}
	}

	@Test void runClientUsesTheQualifiedPackLoaderTaskAndRequiresReadiness() throws Exception {
		UUID workspaceId = UUID.fromString("44444444-4444-4444-8444-444444444445");
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(resourcePackWorkspace(workspaceId));
		Path workspaceRoot = root.resolve("resource-pack-client");
		Files.createDirectories(workspaceRoot.resolve("src/main"));
		Files.writeString(workspaceRoot.resolve("src/main/pack.mcmeta"),
				"{\"pack\":{\"pack_format\":34,\"description\":\"Copper\"}}");
		Files.write(workspaceRoot.resolve("src/main/pack.png"), new byte[] { 1, 2, 3 });
		GradleProcessRunner process = (workingDirectory, arguments, timeout, output) -> {
			assertEquals(List.of(":packloader:runClient"), arguments);
			output.accept("COPPERBENCH_RESOURCE_PACK_READY");
			return new GradleProcessRunner.ProcessResult(0, true);
		};
		try (var tasks = new ResourcePackWorkspaceTaskGateway(store, ignored -> workspaceRoot, root, Clock.systemUTC(),
				UUID::randomUUID, process)) {
			UUID taskId = UUID.fromString(tasks.start(workspaceId, Operation.RUN_CLIENT, new JsonObject())
					.get("id").getAsString());
			await(tasks, workspaceId, taskId);
			assertTrue(tasks.logs(workspaceId, taskId).toString().contains("COPPERBENCH_RESOURCE_PACK_READY"));
		}
	}

	private static WorkspaceState resourcePackWorkspace(UUID id) {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", ResourcePackWorkspaceTaskGateway.GENERATOR_ID);
		generator.addProperty("loader", "resource_pack");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Resource Pack 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(id, "Copper Pack", "resource_pack", 0, false, generator, new JsonObject(), List.of());
	}

	private static void await(ResourcePackWorkspaceTaskGateway tasks, UUID workspaceId, UUID taskId)
			throws Exception {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		while (Instant.now().isBefore(deadline)) {
			JsonObject task = tasks.find(workspaceId, taskId).orElseThrow();
			String state = task.get("state").getAsString();
			if (!state.equals("queued") && !state.equals("running")) {
				assertEquals("succeeded", state, task.toString());
				return;
			}
			Thread.sleep(10);
		}
		throw new AssertionError("Resource-pack task did not finish");
	}
}
