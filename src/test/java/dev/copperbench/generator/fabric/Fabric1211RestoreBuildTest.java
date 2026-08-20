/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Fabric1211RestoreBuildTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T04:00:00Z"), ZoneOffset.UTC);
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);

	@TempDir Path workspace;

	@Test void restoredGoldenWorkspaceRegeneratesAndBuildsIdentically() throws Exception {
		var golden = Fabric1211GoldenWorkspace.create();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(golden);
		AtomicLong sequence = new AtomicLong(900);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			Path jar = root.resolve("build/libs/copper_trails-1.0.0.jar");
			Files.createDirectories(jar.getParent());
			Files.write(jar, new byte[] { 0x50, 0x4b, 0x03, 0x04 });
			return new Fabric1211ProcessRunner.ProcessResult(0, false);
		};

		try (JGitLocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK);
				Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store, ignored -> workspace,
						Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
					WorkspaceMutationGateway.noOp(), history, ignored -> golden.copy(), CLOCK, ids);

			await(service, start(service, ids, Operation.GENERATE_WORKSPACE, 4, new JsonObject()));
			Path itemSource = workspace.resolve("src/main/java/dev/coppertrails/init/ModItems.java");
			String original = Files.readString(itemSource);
			String point = createRecoveryPoint(service, ids, 4);

			JsonObject change = new JsonObject();
			change.addProperty("path", "/fields/maxStackSize");
			change.add("value", new JsonPrimitive(32));
			JsonArray changes = new JsonArray();
			changes.add(change);
			JsonObject update = new JsonObject();
			update.addProperty("elementId", golden.elements().get(1).id().toString());
			update.add("changes", changes);
			assertEquals("committed", service.execute(Command.of(ids.get(), golden.id(), 4,
					Operation.UPDATE_MOD_ELEMENT, update), UI).result().status());
			await(service, start(service, ids, Operation.GENERATE_WORKSPACE, 5, new JsonObject()));
			assertNotEquals(original, Files.readString(itemSource));

			JsonObject restore = new JsonObject();
			restore.addProperty("recoveryPointId", point);
			restore.addProperty("userApproved", true);
			var restored = service.execute(Command.of(ids.get(), golden.id(), 5,
					Operation.RESTORE_RECOVERY_POINT, restore), UI);
			assertEquals("committed", restored.result().status());
			assertEquals(6, restored.result().newRevision());

			await(service, start(service, ids, Operation.BUILD_WORKSPACE, 6, new JsonObject()));
			assertEquals(original, Files.readString(itemSource));
		}
	}

	private static String createRecoveryPoint(WorkspaceApplicationService service, Supplier<UUID> ids, long revision) {
		JsonObject payload = new JsonObject();
		payload.addProperty("label", "Golden Fabric 1.21.1");
		return service.execute(Command.of(ids.get(), Fabric1211GoldenWorkspace.create().id(), revision,
				Operation.CREATE_RECOVERY_POINT, payload), UI).result().recoveryPointId();
	}

	private static UUID start(WorkspaceApplicationService service, Supplier<UUID> ids, Operation operation,
			long revision, JsonObject payload) {
		payload.addProperty("clientMutationId", ids.get().toString());
		payload.addProperty("scope", "workspace");
		var result = service.execute(Command.of(ids.get(), Fabric1211GoldenWorkspace.create().id(), revision,
				operation, payload), UI).result();
		assertEquals("accepted", result.status());
		return UUID.fromString(result.task().getAsJsonObject().get("id").getAsString());
	}

	private static void await(WorkspaceApplicationService service, UUID taskId) throws Exception {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			JsonObject payload = new JsonObject();
			payload.addProperty("taskId", taskId.toString());
			var result = service.query(Query.of(UUID.randomUUID(), Fabric1211GoldenWorkspace.create().id(),
					Operation.GET_TASK, payload), UI);
			String state = result.data().getAsJsonObject().getAsJsonObject("task").get("state").getAsString();
			if (state.equals("succeeded")) return;
			if (!state.equals("running") && !state.equals("queued"))
				throw new AssertionError("Task failed: " + result.data());
			Thread.sleep(25);
		}
		throw new AssertionError("Task did not finish");
	}
}
