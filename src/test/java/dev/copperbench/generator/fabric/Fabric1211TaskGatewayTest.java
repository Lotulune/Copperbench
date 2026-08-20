/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric1211TaskGatewayTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T03:10:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

	@TempDir Path generatedWorkspace;

	@Test void generateCommandCompletesAndExposesTaskLogsThroughTheApplicationService() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(500);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
			JsonObject payload = new JsonObject();
			payload.addProperty("clientMutationId", ids.get().toString());
			payload.addProperty("scope", "workspace");

			var accepted = service.execute(
					Command.of(ids.get(), WORKSPACE_ID, 4, Operation.GENERATE_WORKSPACE, payload), UI);
			assertEquals("accepted", accepted.result().status());
			UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());

			JsonObject taskProjection = awaitTask(service, taskId);
			assertEquals("succeeded", taskProjection.getAsJsonObject("task").get("state").getAsString());
			assertFalse(taskProjection.getAsJsonArray("logs").isEmpty());
			assertTrue(taskProjection.getAsJsonArray("logs").toString().contains("Fabric 1.21.1"));
			assertTrue(Files.isRegularFile(generatedWorkspace.resolve("src/main/resources/fabric.mod.json")));
		}
	}

	@Test void buildAndRunClientCommandsExposeGradleOutputAndReadiness() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(600);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			if (arguments.equals(List.of("build"))) {
				output.accept("BUILD_OK copper_trails-1.0.0.jar");
				Path jar = root.resolve("build/libs/copper_trails-1.0.0.jar");
				Files.createDirectories(jar.getParent());
				Files.write(jar, new byte[] { 0x50, 0x4b, 0x03, 0x04 });
				return new Fabric1211ProcessRunner.ProcessResult(0, false);
			}
			output.accept("[Render thread/INFO] COPPERBENCH_STAGE3_READY");
			return new Fabric1211ProcessRunner.ProcessResult(0, true);
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject build = startAndAwait(service, ids, Operation.BUILD_WORKSPACE);
			assertEquals("succeeded", build.getAsJsonObject("task").get("state").getAsString());
			assertTrue(build.getAsJsonArray("logs").toString().contains("BUILD_OK"));

			JsonObject runClient = startAndAwait(service, ids, Operation.RUN_CLIENT);
			assertEquals("succeeded", runClient.getAsJsonObject("task").get("state").getAsString());
			assertTrue(runClient.getAsJsonArray("logs").toString().contains("COPPERBENCH_STAGE3_READY"));
		}
	}

	@Test void validationReportsElementDiagnosticsWithoutGeneratingFiles() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		var valid = Fabric1211GoldenWorkspace.create();
		var brokenElements = new ArrayList<>(valid.elements());
		var item = brokenElements.get(1);
		JsonObject values = item.values();
		values.getAsJsonObject("fields").addProperty("maxStackSize", 0);
		brokenElements.set(1, new dev.copperbench.core.workspace.WorkspaceState.Element(item.id(), item.type(),
				item.name(), item.displayName(), item.state(), item.ownership(), item.updatedAt(), values));
		store.register(new dev.copperbench.core.workspace.WorkspaceState(valid.id(), valid.name(), valid.kind(),
				valid.revision(), valid.dirty(), valid.generator(), valid.upstreamDocument(), brokenElements));
		AtomicLong sequence = new AtomicLong(700);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject projection = startAndAwait(service, ids, Operation.VALIDATE_WORKSPACE);
			assertEquals("failed", projection.getAsJsonObject("task").get("state").getAsString());
			assertTrue(projection.getAsJsonArray("diagnostics").toString().contains("FABRIC_ITEM_STACK_INVALID"));
			assertFalse(Files.exists(generatedWorkspace.resolve("build.gradle")));
		}
	}

	@Test void exportBuildsArchiveAndRejectsPathsOutsideTheWorkspace() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(800);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			Path jar = root.resolve("build/libs/copper_trails-1.0.0.jar");
			Files.createDirectories(jar.getParent());
			Files.write(jar, new byte[] { 0x50, 0x4b, 0x03, 0x04 });
			return new Fabric1211ProcessRunner.ProcessResult(0, false);
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject exported = startAndAwait(service, ids, Operation.EXPORT_WORKSPACE,
					"exports/copper-trails.zip");
			assertEquals("succeeded", exported.getAsJsonObject("task").get("state").getAsString());
			assertTrue(Files.isRegularFile(generatedWorkspace.resolve("exports/copper-trails.zip")));

			JsonObject rejected = startAndAwait(service, ids, Operation.EXPORT_WORKSPACE, "../escaped.jar");
			assertEquals("failed", rejected.getAsJsonObject("task").get("state").getAsString());
			assertTrue(rejected.getAsJsonArray("diagnostics").toString().contains("FABRIC_EXPORT_FAILED"));
			assertFalse(Files.exists(generatedWorkspace.getParent().resolve("escaped.jar")));
		}
	}

	private static JsonObject startAndAwait(WorkspaceApplicationService service, Supplier<UUID> ids,
			Operation operation) throws Exception {
		return startAndAwait(service, ids, operation, null);
	}

	private static JsonObject startAndAwait(WorkspaceApplicationService service, Supplier<UUID> ids,
			Operation operation, String output) throws Exception {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", ids.get().toString());
		payload.addProperty("scope", "workspace");
		if (output != null) payload.addProperty("output", output);
		var accepted = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4, operation, payload), UI);
		assertEquals("accepted", accepted.result().status());
		UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());
		return awaitTask(service, taskId);
	}

	private static JsonObject awaitTask(WorkspaceApplicationService service, UUID taskId) throws Exception {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			JsonObject payload = new JsonObject();
			payload.addProperty("taskId", taskId.toString());
			var result = service.query(Query.of(UUID.randomUUID(), WORKSPACE_ID, Operation.GET_TASK, payload), UI);
			JsonObject projection = result.data().getAsJsonObject();
			String state = projection.getAsJsonObject("task").get("state").getAsString();
			if (!state.equals("queued") && !state.equals("running")) return projection;
			Thread.sleep(25);
		}
		throw new AssertionError("Fabric generation task did not finish");
	}
}
