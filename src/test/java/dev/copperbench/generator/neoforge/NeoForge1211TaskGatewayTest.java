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
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForge1211TaskGatewayTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-18T02:10:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

	@TempDir Path generatedWorkspace;

	@Test void generationBuildRunAndExportUseTheSharedApplicationService() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(NeoForge1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(900);
		Supplier<UUID> ids = ids(sequence);
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			if (arguments.equals(List.of("build"))) {
				output.accept("NEOFORGE_BUILD_OK copper_trails-1.0.0.jar");
				Path jar = root.resolve("build/libs/copper_trails-1.0.0.jar");
				Files.createDirectories(jar.getParent());
				Files.write(jar, new byte[] { 0x50, 0x4b, 0x03, 0x04 });
				return new Fabric1211ProcessRunner.ProcessResult(0, false);
			}
			output.accept("COPPERBENCH_STAGE5_NEOFORGE_READY");
			return new Fabric1211ProcessRunner.ProcessResult(0, true);
		};
		try (var tasks = new NeoForge1211WorkspaceTaskGateway(store, ignored -> generatedWorkspace,
				Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject generated = startAndAwait(service, ids, Operation.GENERATE_WORKSPACE, null);
			assertEquals("succeeded", generated.getAsJsonObject("task").get("state").getAsString());
			assertTrue(generated.getAsJsonArray("logs").toString().contains("NeoForge 1.21.1"));
			assertTrue(Files.isRegularFile(generatedWorkspace.resolve("src/main/resources/META-INF/neoforge.mods.toml")));

			JsonObject built = startAndAwait(service, ids, Operation.BUILD_WORKSPACE, null);
			assertEquals("succeeded", built.getAsJsonObject("task").get("state").getAsString());
			assertTrue(built.getAsJsonArray("logs").toString().contains("NEOFORGE_BUILD_OK"));

			JsonObject run = startAndAwait(service, ids, Operation.RUN_CLIENT, null);
			assertEquals("succeeded", run.getAsJsonObject("task").get("state").getAsString());

			JsonObject exported = startAndAwait(service, ids, Operation.EXPORT_WORKSPACE,
					"exports/copper-trails-neoforge.zip");
			assertEquals("succeeded", exported.getAsJsonObject("task").get("state").getAsString());
			assertTrue(Files.isRegularFile(generatedWorkspace.resolve("exports/copper-trails-neoforge.zip")));
		}
	}

	@Test void validationUsesNeoForgeDiagnosticsAndDoesNotWriteFiles() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		WorkspaceState valid = NeoForge1211GoldenWorkspace.create();
		var elements = new ArrayList<>(valid.elements());
		var item = elements.get(1);
		JsonObject values = item.values();
		values.getAsJsonObject("fields").addProperty("maxStackSize", 0);
		elements.set(1, new WorkspaceState.Element(item.id(), item.type(), item.name(), item.displayName(), item.state(),
				item.ownership(), item.updatedAt(), values));
		store.register(new WorkspaceState(valid.id(), valid.name(), valid.kind(), valid.revision(), valid.dirty(),
				valid.generator(), valid.upstreamDocument(), elements));
		AtomicLong sequence = new AtomicLong(950);
		Supplier<UUID> ids = ids(sequence);
		try (var tasks = new NeoForge1211WorkspaceTaskGateway(store, ignored -> generatedWorkspace,
				Path.of(".").toAbsolutePath().normalize(), CLOCK, ids)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject projection = startAndAwait(service, ids, Operation.VALIDATE_WORKSPACE, null);
			assertEquals("failed", projection.getAsJsonObject("task").get("state").getAsString());
			assertTrue(projection.getAsJsonArray("diagnostics").toString().contains("NEOFORGE_ITEM_STACK_INVALID"));
			assertFalse(Files.exists(generatedWorkspace.resolve("build.gradle")));
		}
	}

	private static Supplier<UUID> ids(AtomicLong sequence) {
		return () -> UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", sequence.getAndIncrement()));
	}

	private static JsonObject startAndAwait(WorkspaceApplicationService service, Supplier<UUID> ids,
			Operation operation, String output) throws Exception {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", ids.get().toString());
		payload.addProperty("scope", "workspace");
		if (output != null) payload.addProperty("output", output);
		var accepted = service.execute(Command.of(ids.get(), NeoForge1211GoldenWorkspace.WORKSPACE_ID, 4, operation,
				payload), UI);
		assertEquals("accepted", accepted.result().status());
		UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			JsonObject query = new JsonObject();
			query.addProperty("taskId", taskId.toString());
			var result = service.query(Query.of(UUID.randomUUID(), NeoForge1211GoldenWorkspace.WORKSPACE_ID,
					Operation.GET_TASK, query), UI);
			JsonObject projection = result.data().getAsJsonObject();
			String state = projection.getAsJsonObject("task").get("state").getAsString();
			if (!state.equals("queued") && !state.equals("running")) return projection;
			Thread.sleep(25);
		}
		throw new AssertionError("NeoForge task did not finish");
	}
}
