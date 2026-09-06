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
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RestoreResult;
import dev.copperbench.history.WorkspaceChange;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

	@Test void deterministicValidationRepairPreviewsAndAppliesThroughRecoveryProtectedWorkspacePlan() throws Exception {
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
		AtomicLong sequence = new AtomicLong(720);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		RecordingHistory history = new RecordingHistory();
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
					WorkspaceMutationGateway.noOp(), history, null, CLOCK, ids);

			JsonObject projection = startAndAwait(service, ids, Operation.VALIDATE_WORKSPACE);
			JsonObject diagnostic = projection.getAsJsonArray("diagnostics").asList().stream()
					.map(value -> value.getAsJsonObject())
					.filter(value -> value.get("code").getAsString().equals("FABRIC_ITEM_STACK_INVALID"))
					.findFirst().orElseThrow();
			JsonObject repair = diagnostic.getAsJsonArray("actions").asList().stream()
					.map(value -> value.getAsJsonObject())
					.filter(value -> value.get("kind").getAsString().equals("preview_repair"))
					.findFirst().orElseThrow();
			JsonObject repairPayload = repair.getAsJsonObject("payload");
			assertEquals(valid.revision(), repairPayload.get("expectedRevision").getAsLong());
			assertTrue(repairPayload.get("requireRecoveryPoint").getAsBoolean());
			JsonArray operations = repairPayload.getAsJsonArray("operations");
			assertEquals(1, operations.size());
			JsonObject change = operations.get(0).getAsJsonObject().getAsJsonObject("payload")
					.getAsJsonArray("changes").get(0).getAsJsonObject();
			assertEquals("/fields/maxStackSize", change.get("path").getAsString());
			assertEquals(1, change.get("value").getAsInt());

			JsonObject planPayload = repairPayload.deepCopy();
			planPayload.addProperty("idempotencyKey", "repair-item-stack");
			var planned = service.query(Query.of(ids.get(), WORKSPACE_ID, Operation.PLAN_WORKSPACE_CHANGES,
					planPayload), UI);
			assertEquals("succeeded", planned.status(), planned.diagnostics().toString());
			JsonObject plan = planned.data().getAsJsonObject();
			assertTrue(plan.get("requireRecoveryPoint").getAsBoolean());
			assertTrue(plan.getAsJsonObject("safety").get("ready").getAsBoolean());
			assertTrue(plan.getAsJsonArray("changedPaths").toString().contains(item.id().toString()));
			assertTrue(plan.getAsJsonArray("semanticDiff").toString().contains("/values/fields/maxStackSize"));

			JsonObject applyPayload = new JsonObject();
			applyPayload.add("plan", plan.deepCopy());
			var applied = service.execute(Command.of(ids.get(), WORKSPACE_ID, valid.revision(),
					Operation.APPLY_WORKSPACE_PLAN, applyPayload), UI);
			assertEquals("committed", applied.result().status(), applied.result().diagnostics().toString());
			assertNotNull(applied.result().recoveryPointId());
			assertEquals(1, history.created.size());
			var repaired = store.read(WORKSPACE_ID).orElseThrow();
			assertEquals(valid.revision() + 1, repaired.revision());
			assertEquals(1, repaired.element(item.id()).values().getAsJsonObject("fields")
					.get("maxStackSize").getAsInt());

			JsonObject manualChange = new JsonObject();
			manualChange.addProperty("path", "/fields/maxStackSize");
			manualChange.addProperty("value", 32);
			JsonArray manualChanges = new JsonArray();
			manualChanges.add(manualChange);
			JsonObject manualPayload = new JsonObject();
			manualPayload.addProperty("elementId", item.id().toString());
			manualPayload.add("changes", manualChanges);
			var manual = service.execute(Command.of(ids.get(), WORKSPACE_ID, repaired.revision(),
					Operation.UPDATE_MOD_ELEMENT, manualPayload), UI);
			assertEquals("committed", manual.result().status(), manual.result().diagnostics().toString());

			JsonObject stalePlanPayload = repairPayload.deepCopy();
			stalePlanPayload.addProperty("idempotencyKey", "stale-repair-item-stack");
			var stale = service.query(Query.of(ids.get(), WORKSPACE_ID, Operation.PLAN_WORKSPACE_CHANGES,
					stalePlanPayload), UI);
			assertEquals("failed", stale.status());
			assertTrue(stale.diagnostics().toString().contains("WORKSPACE_PLAN_STALE"));
			assertEquals(32, store.read(WORKSPACE_ID).orElseThrow().element(item.id()).values()
					.getAsJsonObject("fields").get("maxStackSize").getAsInt());
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
			UUID taskId = UUID.fromString(projection.getAsJsonObject("task").get("id").getAsString());
			JsonObject failureDiagnostic = projection.getAsJsonArray("diagnostics").get(0).getAsJsonObject();
			JsonObject openLogs = failureDiagnostic.getAsJsonArray("actions").get(0).getAsJsonObject();
			assertEquals("open_logs", openLogs.get("kind").getAsString());
			assertEquals(taskId.toString(), openLogs.getAsJsonObject("payload").get("taskId").getAsString());
			assertFalse(taskId.toString().equals(openLogs.get("target").getAsString()));
			assertEquals(failureDiagnostic.getAsJsonObject("message").getAsJsonObject("args").get("failureId").getAsString(),
					openLogs.get("target").getAsString());
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

	@Test void runtimeProcessFailuresExposeExitAndReadinessFactsWithoutGuessingElements() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(610);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			if (arguments.equals(List.of("runClient")))
				return new Fabric1211ProcessRunner.ProcessResult(7, false);
			if (arguments.equals(List.of("runServer")))
				return new Fabric1211ProcessRunner.ProcessResult(0, false);
			throw new AssertionError("Unexpected runtime task: " + arguments);
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject client = startAndAwait(service, ids, Operation.RUN_CLIENT);
			assertEquals("failed", client.getAsJsonObject("task").get("state").getAsString());
			JsonObject clientDiagnostic = client.getAsJsonArray("diagnostics").asList().stream()
					.map(value -> value.getAsJsonObject())
					.filter(value -> value.get("code").getAsString().equals("FABRIC_RUN_CLIENT_EXITED"))
					.findFirst().orElseThrow();
			JsonObject clientArgs = clientDiagnostic.getAsJsonObject("message").getAsJsonObject("args");
			assertEquals(7, clientArgs.get("exitCode").getAsInt());
			assertEquals("run_client", clientArgs.get("task").getAsString());
			assertTrue(!clientDiagnostic.has("path") || clientDiagnostic.get("path").isJsonNull());
			assertTrue(!clientDiagnostic.has("elementId") || clientDiagnostic.get("elementId").isJsonNull());
			JsonObject clientLogs = clientDiagnostic.getAsJsonArray("actions").get(0).getAsJsonObject();
			assertEquals(client.getAsJsonObject("task").get("id").getAsString(),
					clientLogs.getAsJsonObject("payload").get("taskId").getAsString());

			JsonObject serverPayload = new JsonObject();
			serverPayload.addProperty("clientMutationId", ids.get().toString());
			serverPayload.addProperty("scope", "workspace");
			serverPayload.addProperty("userApproved", true);
			var serverAccepted = service.execute(Command.of(ids.get(), WORKSPACE_ID, 4,
					Operation.RUN_SERVER, serverPayload), UI);
			assertEquals("accepted", serverAccepted.result().status());
			UUID serverTaskId = UUID.fromString(serverAccepted.result().task().getAsJsonObject().get("id").getAsString());
			JsonObject server = awaitTask(service, serverTaskId);
			assertEquals("failed", server.getAsJsonObject("task").get("state").getAsString());
			JsonObject readinessDiagnostic = server.getAsJsonArray("diagnostics").asList().stream()
					.map(value -> value.getAsJsonObject())
					.filter(value -> value.get("code").getAsString().equals("FABRIC_RUN_SERVER_NOT_READY"))
					.findFirst().orElseThrow();
			assertEquals("diagnostic.task_readiness_not_reached",
					readinessDiagnostic.getAsJsonObject("message").get("key").getAsString());
			assertEquals(0, readinessDiagnostic.getAsJsonObject("message").getAsJsonObject("args")
					.get("exitCode").getAsInt());
			assertEquals(serverTaskId.toString(), readinessDiagnostic.getAsJsonArray("actions").get(0).getAsJsonObject()
					.getAsJsonObject("payload").get("taskId").getAsString());
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
			Path source = root.resolve("src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java");
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
			assertTrue(diagnostics.contains("/src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java"), diagnostics);
			assertTrue(diagnostics.contains("Line 42: 找不到符号"), diagnostics);
			assertTrue(diagnostics.contains("\"message\":\"Line 42: 找不到符号\""), diagnostics);
			assertTrue(diagnostics.contains("FABRIC_BUILD_FAILED"), diagnostics);
			assertTrue(diagnostics.contains("00000000-0000-4000-8000-000000000004"), diagnostics);
			assertTrue(diagnostics.contains("locate_element"), diagnostics);
			assertTrue(diagnostics.contains("open_generated_source"), diagnostics);
			assertTrue(diagnostics.contains("open_task_logs"), diagnostics);

			Path generatedSource = generatedWorkspace.resolve(
					"src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java");
			Files.writeString(generatedSource,
					"package dev.coppertrails.procedure;\nfinal class RewrittenAfterFailure {}\n");

			UUID taskId = UUID.fromString(build.getAsJsonObject("task").get("id").getAsString());
			JsonObject sourcePayload = new JsonObject();
			sourcePayload.addProperty("taskId", taskId.toString());
			sourcePayload.addProperty("afterLogSequence", 0);
			sourcePayload.addProperty("sourcePath",
					"/src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java");
			var sourceResult = service.query(Query.of(ids.get(), WORKSPACE_ID, Operation.GET_TASK, sourcePayload), UI);
			assertEquals("succeeded", sourceResult.status(), sourceResult.diagnostics().toString());
			JsonObject source = sourceResult.data().getAsJsonObject().getAsJsonObject("source");
			assertEquals("/src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java",
					source.get("path").getAsString());
			assertEquals("java", source.get("language").getAsString());
			assertEquals(42, source.get("line").getAsInt());
			assertTrue(source.get("size").getAsLong() <= 256L * 1024L);
			assertTrue(source.get("content").getAsString().contains("AnnounceTrailProcedure"));
			assertFalse(source.get("content").getAsString().contains("RewrittenAfterFailure"));

			JsonObject unrelatedPayload = sourcePayload.deepCopy();
			unrelatedPayload.addProperty("sourcePath", "/src/main/java/dev/coppertrails/CopperTrailsMod.java");
			var unrelated = service.query(Query.of(ids.get(), WORKSPACE_ID, Operation.GET_TASK, unrelatedPayload), UI);
			assertEquals("rejected", unrelated.status());
			assertTrue(unrelated.diagnostics().toString().contains("COMMAND_PAYLOAD_INVALID"),
					unrelated.diagnostics().toString());
		}
	}

	@Test void unresolvedCompilerSourceKeepsFileAndLogsButNeverGuessesAnElement() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(Fabric1211GoldenWorkspace.create());
		AtomicLong sequence = new AtomicLong(640);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			assertEquals(List.of("build"), arguments);
			Path source = root.resolve("src/main/java/dev/coppertrails/registry/ModBlocks.java");
			output.accept(source + ":17: error: cannot find symbol");
			return new Fabric1211ProcessRunner.ProcessResult(1, false);
		};
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids, runner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);

			JsonObject build = startAndAwait(service, ids, Operation.BUILD_WORKSPACE);
			assertEquals("failed", build.getAsJsonObject("task").get("state").getAsString());
			var diagnostics = build.getAsJsonArray("diagnostics");
			JsonObject compile = null;
			for (var raw : diagnostics) {
				JsonObject diagnostic = raw.getAsJsonObject();
				if ("JAVA_COMPILE_ERROR".equals(diagnostic.get("code").getAsString())) {
					compile = diagnostic;
					break;
				}
			}
			assertNotNull(compile);
			assertTrue(!compile.has("elementId") || compile.get("elementId").isJsonNull(), compile.toString());
			assertTrue(compile.get("path").getAsString().endsWith("/src/main/java/dev/coppertrails/registry/ModBlocks.java"));
			assertTrue(compile.getAsJsonArray("actions").toString().contains("open_task_logs"), compile.toString());
			assertFalse(compile.getAsJsonArray("actions").toString().contains("locate_element"), compile.toString());
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
			JsonObject diagnostic = projection.getAsJsonArray("diagnostics").asList().stream()
					.map(value -> value.getAsJsonObject())
					.filter(value -> value.get("code").getAsString().equals("FABRIC_ITEM_STACK_INVALID"))
					.findFirst().orElseThrow();
			assertEquals("/fields/maxStackSize",
					diagnostic.getAsJsonArray("actions").get(0).getAsJsonObject().get("target").getAsString());
			assertEquals("locate_generator_field",
					diagnostic.getAsJsonArray("actions").get(0).getAsJsonObject().get("id").getAsString());
			assertFalse(Files.exists(generatedWorkspace.resolve("build.gradle")));
		}
	}

	@Test void modernProcedureValidationPreservesNodeAndPortThroughTaskDiagnostics() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		var valid = Fabric1211GoldenWorkspace.create();
		var elements = new ArrayList<>(valid.elements());
		int procedureIndex = -1;
		for (int index = 0; index < elements.size(); index++) {
			if (elements.get(index).type().equals("procedure")) {
				procedureIndex = index;
				break;
			}
		}
		assertTrue(procedureIndex >= 0);
		var procedure = elements.get(procedureIndex);
		JsonObject values = procedure.values();
		UUID triggerId = UUID.fromString("00000000-0000-4000-8000-000000000961");
		UUID callId = UUID.fromString("00000000-0000-4000-8000-000000000962");
		JsonObject ir = new JsonObject();
		ir.addProperty("schemaVersion", "1.0");
		ir.addProperty("trigger", "no_ext_trigger");
		JsonArray nodes = new JsonArray();
		JsonObject trigger = new JsonObject();
		trigger.addProperty("id", triggerId.toString());
		trigger.addProperty("type", "event_trigger");
		trigger.addProperty("kind", "statement");
		JsonObject triggerFields = new JsonObject();
		triggerFields.addProperty("trigger", "no_ext_trigger");
		trigger.add("fields", triggerFields);
		trigger.add("inputs", new JsonObject());
		nodes.add(trigger);
		JsonObject call = new JsonObject();
		call.addProperty("id", callId.toString());
		call.addProperty("type", "call_procedure");
		call.addProperty("kind", "statement");
		JsonObject callFields = new JsonObject();
		callFields.addProperty("procedureId", "");
		call.add("fields", callFields);
		call.add("inputs", new JsonObject());
		nodes.add(call);
		ir.add("nodes", nodes);
		ir.add("dependencies", new JsonArray());
		values.add("procedureIr", ir);
		elements.set(procedureIndex, new dev.copperbench.core.workspace.WorkspaceState.Element(procedure.id(),
				procedure.type(), procedure.name(), procedure.displayName(), procedure.state(), procedure.ownership(),
				procedure.updatedAt(), values));
		store.register(new dev.copperbench.core.workspace.WorkspaceState(valid.id(), valid.name(), valid.kind(),
				valid.revision(), valid.dirty(), valid.generator(), valid.upstreamDocument(), elements));
		AtomicLong sequence = new AtomicLong(960);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		try (Fabric1211WorkspaceTaskGateway tasks = new Fabric1211WorkspaceTaskGateway(store,
				ignored -> generatedWorkspace, Path.of(".").toAbsolutePath().normalize(), CLOCK, ids)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, CLOCK, ids);
			JsonObject projection = startAndAwait(service, ids, Operation.VALIDATE_WORKSPACE);
			assertEquals("failed", projection.getAsJsonObject("task").get("state").getAsString());
			JsonObject diagnostic = projection.getAsJsonArray("diagnostics").asList().stream()
					.map(raw -> raw.getAsJsonObject())
					.filter(item -> item.get("code").getAsString().equals("PROCEDURE_CALL_TARGET_REQUIRED"))
					.findFirst().orElseThrow();
			assertEquals("/elements/" + procedure.id() + "/procedureIr/nodes/" + callId + "/ports/procedureId",
					diagnostic.get("path").getAsString());
			JsonObject action = diagnostic.getAsJsonArray("actions").get(0).getAsJsonObject();
			assertEquals("open_procedure_node", action.get("kind").getAsString());
			assertEquals(callId.toString(), action.get("target").getAsString());
			assertEquals(callId.toString(), action.getAsJsonObject("payload").get("nodeId").getAsString());
			assertEquals("procedureId", action.getAsJsonObject("payload").get("port").getAsString());
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
		AtomicBoolean cleanedUp = new AtomicBoolean();
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			started.countDown();
			try {
				try {
					new CountDownLatch(1).await();
					return new Fabric1211ProcessRunner.ProcessResult(0, false);
				} catch (InterruptedException exception) {
					// Simulate real process-tree cleanup that takes a short but observable amount of time
					// after the Gradle worker receives cancellation.
					Thread.sleep(250);
					cleanedUp.set(true);
					throw exception;
				}
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
			assertTrue(cleanedUp.get(), "cancel_task must not report cancelled before external cleanup finishes");
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

	private static final class RecordingHistory implements LocalHistoryService {
		private final List<RecoveryPoint> created = new ArrayList<>();

		@Override public RecoveryPoint createRecoveryPoint(RecoveryPointRequest request) {
			RecoveryPoint point = new RecoveryPoint("repair-rp-" + (created.size() + 1), request.label(),
					request.actor(), request.taskId(), CLOCK.instant());
			created.add(point);
			return point;
		}

		@Override public List<RecoveryPoint> listRecoveryPoints() {
			return List.copyOf(created);
		}

		@Override public List<WorkspaceChange> compare(String fromRecoveryPointId, String toRecoveryPointId) {
			return List.of();
		}

		@Override public List<WorkspaceChange> previewRestore(String recoveryPointId) {
			return List.of();
		}

		@Override public RestoreResult restore(String recoveryPointId) {
			throw new UnsupportedOperationException("restore is not needed by this test");
		}

		@Override public void close() {
		}
	}
}
