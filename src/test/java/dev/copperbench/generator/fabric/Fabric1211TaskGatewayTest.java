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
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
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

	@Test void runClientRemainsRunningAfterMarkerUntilTheClientProcessExits() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(625);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		CountDownLatch markerSeen = new CountDownLatch(1);
		CountDownLatch closeClient = new CountDownLatch(1);
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			assertEquals(List.of("runClient"), arguments);
			assertTrue(timeout.isZero(), "interactive runClient must not use the CI smoke timeout");
			output.accept("[Render thread/INFO] COPPERBENCH_STAGE3_READY");
			markerSeen.countDown();
			closeClient.await();
			return new Fabric1211ProcessRunner.ProcessResult(0, true);
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
			JsonObject payload = new JsonObject();
			payload.addProperty("clientMutationId", ids.get().toString());
			payload.addProperty("scope", "workspace");
			var accepted = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4, Operation.RUN_CLIENT, payload), UI);
			UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());

			assertTrue(markerSeen.await(5, TimeUnit.SECONDS));
			assertEquals("running", task(service, taskId).getAsJsonObject("task").get("state").getAsString());
			closeClient.countDown();
			assertEquals("succeeded", awaitTask(service, taskId).getAsJsonObject("task").get("state").getAsString());
		}
	}

	@Test
	@ResourceLock(Resources.SYSTEM_PROPERTIES)
	void missingBundledJdkBecomesStructuredTaskDiagnostic() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(640);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Path distribution = generatedWorkspace.resolve("distribution");
		Path workspace = generatedWorkspace.resolve("workspace");
		Files.createDirectories(distribution.resolve("gradle/wrapper"));
		Files.writeString(distribution.resolve("gradlew"), "placeholder");
		Files.writeString(distribution.resolve("gradlew.bat"), "placeholder");
		Files.write(distribution.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[] { 0 });
		Path unusableFallback = generatedWorkspace.resolve("not-a-java-home");
		String previousJavaHome = System.getProperty("java.home");
		System.setProperty("java.home", unusableFallback.toString());
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> workspace, distribution, CLOCK, ids)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
			JsonObject projection = startAndAwait(service, ids, Operation.RUN_CLIENT);

			assertEquals("failed", projection.getAsJsonObject("task").get("state").getAsString());
			String diagnostics = projection.getAsJsonArray("diagnostics").toString();
			assertTrue(diagnostics.contains("BUNDLED_JDK_MISSING"));
			String expectedJdkPath = distribution.resolve("jdk").toString().replace("\\", "\\\\");
			assertTrue(diagnostics.contains(expectedJdkPath));
			assertTrue(diagnostics.contains("jdk21_win_64"));
			assertTrue(diagnostics.contains("not-a-java-home"));
			assertTrue(projection.getAsJsonArray("logs").toString().contains("No usable Java home found"));
		} finally {
			if (previousJavaHome == null)
				System.clearProperty("java.home");
			else
				System.setProperty("java.home", previousJavaHome);
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

	@Test void failedBuildExtractsJavaCompilerErrorsIntoStructuredDiagnostics() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(620);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			assertEquals(List.of("build"), arguments);
			Path source = root.resolve("src/main/java/net/example/BrokenBehavior.java");
			output.accept(source + ":42: 错误: 找不到符号");
			output.accept("  " + source + ":42: 错误: 找不到符号");
			return new Fabric1211ProcessRunner.ProcessResult(1, false);
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject build = startAndAwait(service, ids, Operation.BUILD_WORKSPACE);
			assertEquals("failed", build.getAsJsonObject("task").get("state").getAsString());
			String diagnostics = build.getAsJsonArray("diagnostics").toString();
			assertTrue(diagnostics.contains("JAVA_COMPILE_ERROR"), diagnostics);
			assertEquals(diagnostics.indexOf("JAVA_COMPILE_ERROR"), diagnostics.lastIndexOf("JAVA_COMPILE_ERROR"), diagnostics);
			assertTrue(diagnostics.contains("/src/main/java/net/example/BrokenBehavior.java"), diagnostics);
			assertTrue(diagnostics.contains("Line 42: 找不到符号"), diagnostics);
			assertTrue(diagnostics.contains("FABRIC_BUILD_FAILED"), diagnostics);
		}
	}

	@Test void serverRequiresDesktopEulaApprovalAndDatagenStaysInTaskStaging() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(650);
		AtomicInteger invocations = new AtomicInteger();
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			invocations.incrementAndGet();
			assertTrue(root.toString().replace('\\', '/').contains(".copperbench/task-runs/"));
			if (arguments.equals(List.of("runServer"))) {
				assertEquals("eula=true\n", Files.readString(root.resolve("run/eula.txt")));
				output.accept("COPPERBENCH_STAGE3_READY dedicated server");
				return new Fabric1211ProcessRunner.ProcessResult(0, true);
			}
			assertEquals(List.of("runDatagen"), arguments);
			Path generated = root.resolve("src/generated/resources/data/copper_trails/generated.json");
			Files.createDirectories(generated.getParent());
			Files.writeString(generated, "{}");
			return new Fabric1211ProcessRunner.ProcessResult(0, false);
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
			JsonObject unapprovedPayload = new JsonObject();
			unapprovedPayload.addProperty("clientMutationId", ids.get().toString());
			unapprovedPayload.addProperty("scope", "workspace");
			unapprovedPayload.addProperty("userApproved", false);
			var unapproved = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4, Operation.RUN_SERVER,
					unapprovedPayload), UI);
			assertEquals("rejected", unapproved.result().status());
			assertEquals(0, invocations.get());

			JsonObject approvedPayload = unapprovedPayload.deepCopy();
			approvedPayload.addProperty("userApproved", true);
			var approved = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4, Operation.RUN_SERVER,
					approvedPayload), UI);
			assertEquals("accepted", approved.result().status());
			UUID serverTask = UUID.fromString(approved.result().task().getAsJsonObject().get("id").getAsString());
			assertEquals("succeeded", awaitTask(service, serverTask).getAsJsonObject("task").get("state").getAsString());

			JsonObject datagen = startAndAwait(service, ids, Operation.RUN_DATAGEN);
			assertEquals("succeeded", datagen.getAsJsonObject("task").get("state").getAsString());
			UUID datagenTask = UUID.fromString(datagen.getAsJsonObject("task").get("id").getAsString());
			assertEquals(2, invocations.get());
			assertFalse(Files.exists(generatedWorkspace.resolve("run/eula.txt")));
			assertFalse(Files.exists(generatedWorkspace.resolve("src/generated")));
			try (var manifests = Files.walk(generatedWorkspace.resolve(".copperbench/task-runs/run_datagen"))) {
				assertTrue(manifests.anyMatch(path -> path.getFileName().toString().equals("datagen-manifest.json")));
			}
			assertEquals(4, store.read(WORKSPACE_ID).orElseThrow().revision());

			JsonObject previewPayload = new JsonObject();
			previewPayload.addProperty("taskId", datagenTask.toString());
			JsonObject preview = service.query(Query.of(ids.get(), WORKSPACE_ID,
					Operation.PREVIEW_DATAGEN_OUTPUT, previewPayload), UI).data().getAsJsonObject();
			assertEquals(1, preview.get("changeCount").getAsInt());
			assertTrue(preview.get("canPublish").getAsBoolean());
			assertEquals("add", preview.getAsJsonArray("files").get(0).getAsJsonObject().get("status").getAsString());

			JsonObject stalePublish = new JsonObject();
			stalePublish.addProperty("clientMutationId", ids.get().toString());
			stalePublish.addProperty("taskId", datagenTask.toString());
			stalePublish.addProperty("manifestHash", "0".repeat(64));
			var rejected = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4,
					Operation.PUBLISH_DATAGEN_OUTPUT, stalePublish), UI);
			assertEquals("rejected", rejected.result().status());
			assertFalse(Files.exists(generatedWorkspace.resolve("src/generated")));
			assertEquals(4, store.read(WORKSPACE_ID).orElseThrow().revision());

			JsonObject publish = stalePublish.deepCopy();
			publish.addProperty("clientMutationId", ids.get().toString());
			publish.addProperty("manifestHash", preview.get("manifestHash").getAsString());
			var committed = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4,
					Operation.PUBLISH_DATAGEN_OUTPUT, publish), UI);
			assertEquals("committed", committed.result().status());
			assertEquals(5, committed.result().newRevision());
			assertEquals(5, store.read(WORKSPACE_ID).orElseThrow().revision());
			assertTrue(Files.isRegularFile(generatedWorkspace.resolve(
					"src/generated/resources/data/copper_trails/generated.json")));
			assertEquals(1, committed.result().data().getAsJsonObject()
					.getAsJsonArray("changedPaths").size());
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

	@Test void cancellationCannotBeOverwrittenByTheInterruptedWorker() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(750);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch exited = new CountDownLatch(1);
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			started.countDown();
			try {
				new CountDownLatch(1).await();
				return new Fabric1211ProcessRunner.ProcessResult(0, false);
			} finally {
				exited.countDown();
			}
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
			JsonObject payload = new JsonObject();
			payload.addProperty("clientMutationId", ids.get().toString());
			payload.addProperty("scope", "workspace");
			var accepted = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4, Operation.BUILD_WORKSPACE, payload), UI);
			UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());
			assertTrue(started.await(10, TimeUnit.SECONDS));

			JsonObject cancelPayload = new JsonObject();
			cancelPayload.addProperty("clientMutationId", ids.get().toString());
			cancelPayload.addProperty("taskId", taskId.toString());
			var cancelled = service.execute(
					Command.of(ids.get(), WORKSPACE_ID, 4, Operation.CANCEL_TASK, cancelPayload), UI);

			assertEquals("cancelled", cancelled.result().status());
			assertTrue(exited.await(2, TimeUnit.SECONDS));
			JsonObject projection = task(service, taskId);
			assertEquals("cancelled", projection.getAsJsonObject("task").get("state").getAsString());
			assertTrue(projection.getAsJsonArray("diagnostics").isEmpty());
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

	private static JsonObject task(WorkspaceApplicationService service, UUID taskId) {
		JsonObject payload = new JsonObject();
		payload.addProperty("taskId", taskId.toString());
		return service.query(Query.of(UUID.randomUUID(), WORKSPACE_ID, Operation.GET_TASK, payload), UI)
				.data().getAsJsonObject();
	}
}
