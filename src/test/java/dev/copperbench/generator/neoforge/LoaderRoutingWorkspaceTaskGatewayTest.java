/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.LoaderRoutingWorkspaceTaskGateway;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderRoutingWorkspaceTaskGatewayTest {

	@TempDir Path roots;

	@Test void routesFabricAndNeoForgeWithoutChangingTheApplicationContract() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		WorkspaceState neoForge = NeoForge1211GoldenWorkspace.create();
		UUID fabricId = UUID.fromString("33333333-3333-4333-8333-333333333333");
		JsonObject fabricGenerator = neoForge.generator();
		fabricGenerator.addProperty("id", Fabric1211Generator.GENERATOR_ID);
		fabricGenerator.addProperty("loader", "fabric");
		WorkspaceState fabric = new WorkspaceState(fabricId, neoForge.name(), neoForge.kind(), neoForge.revision(),
				neoForge.dirty(), fabricGenerator, neoForge.upstreamDocument(), neoForge.elements());
		store.register(neoForge);
		store.register(fabric);
		Map<UUID, Path> workspaceRoots = new ConcurrentHashMap<>();
		workspaceRoots.put(neoForge.id(), roots.resolve("neoforge"));
		workspaceRoots.put(fabric.id(), roots.resolve("fabric"));

		try (var tasks = new LoaderRoutingWorkspaceTaskGateway(store, workspaceRoots::get,
				Path.of(".").toAbsolutePath().normalize(), Clock.systemUTC(), UUID::randomUUID)) {
			UUID neoTask = UUID.fromString(tasks.start(neoForge.id(), Operation.GENERATE_WORKSPACE, new JsonObject())
					.get("id").getAsString());
			UUID fabricTask = UUID.fromString(tasks.start(fabric.id(), Operation.GENERATE_WORKSPACE, new JsonObject())
					.get("id").getAsString());
			await(tasks, neoForge.id(), neoTask);
			await(tasks, fabric.id(), fabricTask);

			assertTrue(Files.isRegularFile(workspaceRoots.get(neoForge.id())
					.resolve("src/main/resources/META-INF/neoforge.mods.toml")));
			assertTrue(Files.isRegularFile(workspaceRoots.get(fabric.id())
					.resolve("src/main/resources/fabric.mod.json")));
		}
	}

	@Test void rejectsAnUnsupportedGeneratorBeforeAnyFilesAreWritten() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		WorkspaceState source = NeoForge1211GoldenWorkspace.create();
		JsonObject generator = source.generator();
		generator.addProperty("id", "unknown-1.21.1");
		WorkspaceState unsupported = new WorkspaceState(source.id(), source.name(), source.kind(), source.revision(),
				source.dirty(), generator, source.upstreamDocument(), source.elements());
		store.register(unsupported);
		try (var tasks = new LoaderRoutingWorkspaceTaskGateway(store, ignored -> roots,
				Path.of(".").toAbsolutePath().normalize(), Clock.systemUTC(), UUID::randomUUID)) {
			assertThrows(IllegalArgumentException.class,
					() -> tasks.start(source.id(), Operation.GENERATE_WORKSPACE, new JsonObject()));
		}
	}

	private static void await(LoaderRoutingWorkspaceTaskGateway tasks, UUID workspaceId, UUID taskId)
			throws Exception {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			JsonObject task = tasks.find(workspaceId, taskId).orElseThrow();
			String state = task.get("state").getAsString();
			if (!state.equals("queued") && !state.equals("running")) {
				assertTrue(state.equals("succeeded"), task.toString());
				return;
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Routed task did not finish");
	}
}
