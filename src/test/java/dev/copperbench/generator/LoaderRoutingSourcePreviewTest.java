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
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.fabric.Fabric1211WorkspaceTaskGateway;
import dev.copperbench.generator.neoforge.NeoForge1211GoldenWorkspace;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211WorkspaceTaskGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderRoutingSourcePreviewTest {
	private static final RequestContext UI = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
	private static final String MANUAL_PATH = "src/main/java/com/example/acceptance/WorkflowNote.java";
	private static final String GENERATED_PATH = "src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java";
	private static final String BROKEN_SOURCE = "package com.example.acceptance;\r\n\r\n"
			+ "public final class WorkflowNote {\r\n"
			+ "    public static final String NOTE = ;\r\n}\r\n";
	@TempDir Path roots;

	@ParameterizedTest
	@CsvSource({ "fabric,manual", "fabric,manual_same_name", "fabric,generated", "neoforge,manual", "neoforge,manual_same_name", "neoforge,generated" })
	void routesCapturedDiagnosticSourceWithoutReadingTheCurrentFile(String loader, String ownership) throws Exception {
		WorkspaceState workspace = loader.equals("fabric") ? Fabric1211GoldenWorkspace.create()
				: NeoForge1211GoldenWorkspace.create();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace);
		Function<UUID, Path> workspaceRoots = id -> roots.resolve(id.toString());
		boolean manual = ownership.startsWith("manual");
		String relativePath = ownership.equals("manual_same_name")
				? "src/main/java/com/example/manual/AnnounceTrailProcedure.java" : manual ? MANUAL_PATH : GENERATED_PATH;
		Path sourceFile = workspaceRoots.apply(workspace.id()).resolve(relativePath);
		if (manual) {
			Files.createDirectories(sourceFile.getParent());
			Files.writeString(sourceFile, BROKEN_SOURCE, StandardCharsets.UTF_8);
		}
		AtomicReference<String> captured = new AtomicReference<>();
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			assertEquals(List.of("build"), arguments);
			Path source = root.resolve(relativePath);
			assertTrue(Files.isRegularFile(source), source.toString());
			captured.set(Files.readString(source, StandardCharsets.UTF_8));
			// Preserve the actual C3 javac message and its indented duplicate; only the temporary root differs.
			output.accept(source + ":4: 错误: 非法的表达式开始");
			output.accept("    public static final String NOTE = ;");
			output.accept("  " + source + ":4: 错误: 非法的表达式开始");
			return new Fabric1211ProcessRunner.ProcessResult(1, false);
		};
		Path distribution = Path.of(".").toAbsolutePath().normalize();
		Clock clock = Clock.systemUTC();
		var fabric = new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID, runner);
		var neoForge = new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID, runner);
		CountDownLatch completed = new CountDownLatch(1);
		try (var tasks = new LoaderRoutingWorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID, fabric, neoForge);
				var subscription = tasks.subscribeTaskEvents(event -> {
					if (event.workspaceId().equals(workspace.id()) && event.event().equals("task_completed")) completed.countDown();
				})) {
			var service = new WorkspaceApplicationService(store, tasks, clock, UUID::randomUUID);
			JsonObject finished = startAndAwaitCompilerFailure(service, workspace);
			assertTrue(completed.await(10, TimeUnit.SECONDS), "The terminal task observation was not persisted");
			UUID taskId = UUID.fromString(finished.getAsJsonObject("task").get("id").getAsString());
			assertEquals("failed", finished.getAsJsonObject("task").get("state").getAsString());
			List<JsonObject> compilerErrors = finished.getAsJsonArray("diagnostics").asList().stream()
					.map(raw -> raw.getAsJsonObject()).filter(d -> d.get("code").getAsString().equals("JAVA_COMPILE_ERROR")).toList();
			assertEquals(1, compilerErrors.size(), compilerErrors.toString());
			JsonObject diagnostic = compilerErrors.getFirst();
			assertEquals("/" + relativePath, diagnostic.get("path").getAsString());
			assertEquals("Line 4: 非法的表达式开始", diagnostic.getAsJsonObject("message")
					.getAsJsonObject("args").get("message").getAsString());
			JsonObject sourceAction = diagnostic.getAsJsonArray("actions").asList().stream()
					.map(raw -> raw.getAsJsonObject()).filter(action -> action.get("kind").getAsString().equals("open_source"))
					.findFirst().orElseThrow();
			assertEquals("open_generated_source", sourceAction.get("id").getAsString());
			assertEquals("/" + relativePath, sourceAction.get("target").getAsString());
			assertEquals("View source", sourceAction.getAsJsonObject("label").get("fallback").getAsString());
			assertEquals(4, sourceAction.getAsJsonObject("payload").get("line").getAsInt());
			if (manual) {
				assertEquals(BROKEN_SOURCE, captured.get());
				assertTrue(!diagnostic.has("elementId") || diagnostic.get("elementId").isJsonNull(), diagnostic.toString());
			} else {
				assertTrue(captured.get().contains("AnnounceTrailProcedure"), captured.get());
				assertEquals(workspace.elements().stream().filter(element -> element.type().equals("procedure")).findFirst().orElseThrow()
						.id().toString(), diagnostic.get("elementId").getAsString());
			}
			if (loader.equals("neoforge")) {
				// Model a persisted Fabric record colliding with the live NeoForge task ID.
				// Wait for the terminal publication above so it cannot overwrite this history fixture.
				JsonObject fabricHistory = finished.getAsJsonObject("task").deepCopy();
				fabricHistory.addProperty("backendIdentity", "Fabric 1.21.1");
				WorkspaceTaskRecords.save(workspaceRoots.apply(workspace.id()), workspace.id(), fabricHistory,
						finished.getAsJsonArray("logs").asList().stream().map(raw -> raw.getAsJsonObject()).toList(),
						finished.getAsJsonArray("diagnostics").asList().stream().map(raw -> raw.getAsJsonObject()).toList());
				JsonObject restoredFabric = fabric.find(workspace.id(), taskId).orElseThrow();
				assertEquals("Fabric 1.21.1", restoredFabric.get("backendIdentity").getAsString());
				assertTrue(restoredFabric.has("restoredFromHistory") && restoredFabric.get("restoredFromHistory").getAsBoolean(),
						restoredFabric.toString());
				assertEquals(restoredFabric, tasks.find(workspace.id(), taskId).orElseThrow());
				assertTrue(fabric.sourcePreview(workspace.id(), taskId, "/" + relativePath).isEmpty());
				assertEquals("NeoForge 1.21.1", neoForge.find(workspace.id(), taskId).orElseThrow()
						.get("backendIdentity").getAsString());
			}

			Files.writeString(sourceFile, "final class RewrittenAfterFailure {}\n", StandardCharsets.UTF_8);
			var result = sourceQuery(service, workspace.id(), taskId, "/" + relativePath);
			assertEquals("succeeded", result.status(), result.diagnostics().toString());
			JsonObject preview = result.data().getAsJsonObject().getAsJsonObject("source");
			assertEquals("/" + relativePath, preview.get("path").getAsString());
			assertEquals("java", preview.get("language").getAsString());
			assertEquals(4, preview.get("line").getAsInt());
			assertEquals(captured.get(), preview.get("content").getAsString());
			assertEquals(captured.get().getBytes(StandardCharsets.UTF_8).length, preview.get("size").getAsLong());
			assertFalse(preview.get("content").getAsString().contains("RewrittenAfterFailure"));

			for (String unrelated : List.of("/src/main/java/Unrelated.java", "/src/main/java/../../../../outside.java")) {
				var rejected = sourceQuery(service, workspace.id(), taskId, unrelated);
				assertEquals("rejected", rejected.status());
				assertTrue(rejected.diagnostics().toString().contains("COMMAND_PAYLOAD_INVALID"), rejected.diagnostics().toString());
			}
			assertTrue(tasks.sourcePreview(UUID.randomUUID(), taskId, "/" + relativePath).isEmpty());
			var missingTask = sourceQuery(service, workspace.id(), UUID.randomUUID(), "/" + relativePath);
			assertEquals("rejected", missingTask.status());
			assertTrue(missingTask.diagnostics().toString().contains("TASK_NOT_FOUND"), missingTask.diagnostics().toString());
		}
	}

	@ParameterizedTest @CsvSource({ "fabric", "neoforge" })
	void errorsAtDifferentLinesKeepTheirOwnActionLineAndOneCapturedSource(String loader) throws Exception {
		WorkspaceState workspace = loader.equals("fabric") ? Fabric1211GoldenWorkspace.create() : NeoForge1211GoldenWorkspace.create();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore(); store.register(workspace);
		Function<UUID, Path> workspaceRoots = id -> roots.resolve(id.toString());
		Path source = workspaceRoots.apply(workspace.id()).resolve(MANUAL_PATH);
		Files.createDirectories(source.getParent()); Files.writeString(source, BROKEN_SOURCE, StandardCharsets.UTF_8);
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			output.accept(root.resolve(MANUAL_PATH) + ":4: error: first failure");
			output.accept(root.resolve(MANUAL_PATH) + ":8: error: second failure");
			return new Fabric1211ProcessRunner.ProcessResult(1, false);
		};
		try (var tasks = router(store, workspaceRoots, runner)) {
			var service = new WorkspaceApplicationService(store, tasks, Clock.systemUTC(), UUID::randomUUID);
			JsonObject finished = startAndAwaitCompilerFailure(service, workspace);
			var compiler = finished.getAsJsonArray("diagnostics").asList().stream().map(raw -> raw.getAsJsonObject())
					.filter(diagnostic -> diagnostic.get("code").getAsString().equals("JAVA_COMPILE_ERROR")).toList();
			assertEquals(2, compiler.size());
			assertEquals(List.of(4, 8), compiler.stream().map(diagnostic -> diagnostic.getAsJsonArray("actions").asList().stream()
					.map(raw -> raw.getAsJsonObject()).filter(action -> action.get("kind").getAsString().equals("open_source"))
					.findFirst().orElseThrow().getAsJsonObject("payload").get("line").getAsInt()).toList());
			Files.writeString(source, "class ChangedAfterFailure {}", StandardCharsets.UTF_8);
			UUID taskId = UUID.fromString(finished.getAsJsonObject("task").get("id").getAsString());
			JsonObject preview = sourceQuery(service, workspace.id(), taskId, "/" + MANUAL_PATH).data().getAsJsonObject().getAsJsonObject("source");
			assertEquals(BROKEN_SOURCE, preview.get("content").getAsString());
			assertEquals(4, preview.get("line").getAsInt(), "Path-only legacy reads remain deterministic; action payload owns each diagnostic line");
		}
	}

	@Test void externalCompilerPathNeverFallsBackToAnInternalFileWithTheSameRelativePath() throws Exception {
		WorkspaceState workspace = Fabric1211GoldenWorkspace.create();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace);
		Function<UUID, Path> workspaceRoots = id -> roots.resolve("workspaces").resolve(id.toString());
		Path internal = workspaceRoots.apply(workspace.id()).resolve(MANUAL_PATH);
		Path external = roots.resolve("external").resolve(MANUAL_PATH);
		for (Path file : List.of(internal, external)) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, BROKEN_SOURCE, StandardCharsets.UTF_8);
		}
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			output.accept(external + ":4: 错误: 非法的表达式开始");
			return new Fabric1211ProcessRunner.ProcessResult(1, false);
		};
		try (var tasks = router(store, workspaceRoots, runner)) {
			var service = new WorkspaceApplicationService(store, tasks, Clock.systemUTC(), UUID::randomUUID);
			JsonObject finished = startAndAwaitCompilerFailure(service, workspace);
			UUID taskId = UUID.fromString(finished.getAsJsonObject("task").get("id").getAsString());
			var result = sourceQuery(service, workspace.id(), taskId, "/" + MANUAL_PATH);
			assertEquals("rejected", result.status());
			assertTrue(result.diagnostics().toString().contains("TASK_SOURCE_NOT_FOUND"), result.diagnostics().toString());
			assertTrue(Files.isRegularFile(internal));
		}
	}

	@Test void restoredTaskNeverReconstructsAPreviewFromTheCurrentWorkspace() throws Exception {
		WorkspaceState workspace = Fabric1211GoldenWorkspace.create();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace);
		Function<UUID, Path> workspaceRoots = id -> roots.resolve(id.toString());
		Path file = workspaceRoots.apply(workspace.id()).resolve(MANUAL_PATH);
		Files.createDirectories(file.getParent());
		Files.writeString(file, BROKEN_SOURCE, StandardCharsets.UTF_8);
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			output.accept(root.resolve(MANUAL_PATH) + ":4: 错误: 非法的表达式开始");
			return new Fabric1211ProcessRunner.ProcessResult(1, false);
		};
		UUID taskId;
		try (var tasks = router(store, workspaceRoots, runner)) {
			var service = new WorkspaceApplicationService(store, tasks, Clock.systemUTC(), UUID::randomUUID);
			JsonObject finished = startAndAwaitCompilerFailure(service, workspace);
			taskId = UUID.fromString(finished.getAsJsonObject("task").get("id").getAsString());
			assertEquals("succeeded", sourceQuery(service, workspace.id(), taskId, "/" + MANUAL_PATH).status());
		}
		Files.writeString(file, "final class RewrittenAfterFailure {}\n", StandardCharsets.UTF_8);
		try (var tasks = router(store, workspaceRoots, runner)) {
			var service = new WorkspaceApplicationService(store, tasks, Clock.systemUTC(), UUID::randomUUID);
			var result = sourceQuery(service, workspace.id(), taskId, "/" + MANUAL_PATH);
			assertEquals("rejected", result.status());
			assertTrue(result.diagnostics().toString().contains("TASK_SOURCE_NOT_FOUND"), result.diagnostics().toString());
		}
	}

	@ParameterizedTest @CsvSource({ "fabric", "neoforge" })
	void routesCapturedSourceFromThe263TaskOwner(String loader) throws Exception {
		WorkspaceState original = Fabric1211GoldenWorkspace.create();
		JsonObject generator = original.generator();
		generator.addProperty("id", loader + "-26.3");
		generator.addProperty("loader", loader);
		generator.addProperty("minecraftVersion", "26.3");
		WorkspaceState workspace = new WorkspaceState(original.id(), original.name(), original.kind(), original.revision(),
				original.dirty(), generator, original.upstreamDocument(), List.of());
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore(); store.register(workspace);
		Function<UUID, Path> workspaceRoots = id -> roots.resolve(id.toString());
		Path file = workspaceRoots.apply(workspace.id()).resolve(MANUAL_PATH);
		Files.createDirectories(file.getParent()); Files.writeString(file, BROKEN_SOURCE, StandardCharsets.UTF_8);
		Fabric1211ProcessRunner runner = (root, arguments, timeout, output) -> {
			assertEquals(List.of("build"), arguments);
			output.accept(root.resolve(MANUAL_PATH) + ":4: error: isolated 26.3 fixture failure");
			return new Fabric1211ProcessRunner.ProcessResult(1, false);
		};
		Path distribution = Path.of(".").toAbsolutePath().normalize(); Clock clock = Clock.systemUTC();
		CountDownLatch completed = new CountDownLatch(1);
		try (var tasks = new LoaderRoutingWorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID,
				new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID, runner),
				new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID, runner),
				new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID,
						Fabric1211Generator.Profile.FABRIC_263, runner),
				new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID,
						NeoForge1211Generator.Profile.NEOFORGE_263, runner));
				var subscription = tasks.subscribeTaskEvents(event -> {
					if (event.workspaceId().equals(workspace.id()) && event.event().equals("task_completed")) completed.countDown();
				})) {
			JsonObject started = tasks.start(workspace.id(), Operation.BUILD_WORKSPACE, new JsonObject());
			UUID taskId = UUID.fromString(started.get("id").getAsString());
			assertTrue(completed.await(10, TimeUnit.SECONDS), "The 26.3 fixture did not reach its terminal state");
			assertEquals("failed", tasks.find(workspace.id(), taskId).orElseThrow().get("state").getAsString());
			Files.writeString(file, "class ChangedAfter263Failure {}", StandardCharsets.UTF_8);
			JsonObject captured = tasks.sourcePreview(workspace.id(), taskId, "/" + MANUAL_PATH).orElseThrow();
			assertEquals(BROKEN_SOURCE, captured.get("content").getAsString());
			assertEquals(4, captured.get("line").getAsInt());
			assertTrue(tasks.sourcePreview(UUID.randomUUID(), taskId, "/" + MANUAL_PATH).isEmpty());
		}
	}

	private LoaderRoutingWorkspaceTaskGateway router(RevisionedWorkspaceStore store,
			Function<UUID, Path> workspaceRoots, Fabric1211ProcessRunner runner) {
		Path distribution = Path.of(".").toAbsolutePath().normalize();
		Clock clock = Clock.systemUTC();
		return new LoaderRoutingWorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID,
				new Fabric1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID, runner),
				new NeoForge1211WorkspaceTaskGateway(store, workspaceRoots, distribution, clock, UUID::randomUUID, runner));
	}

	private static JsonObject startAndAwaitCompilerFailure(WorkspaceApplicationService service, WorkspaceState workspace) throws Exception {
		JsonObject payload = new JsonObject();
		payload.addProperty("scope", "workspace");
		payload.addProperty("clientMutationId", UUID.randomUUID().toString());
		var accepted = service.execute(Command.of(UUID.randomUUID(), workspace.id(), workspace.revision(), Operation.BUILD_WORKSPACE, payload), UI);
		assertEquals("accepted", accepted.result().status(), accepted.result().diagnostics().toString());
		UUID taskId = UUID.fromString(accepted.result().task().getAsJsonObject().get("id").getAsString());
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			JsonObject queryPayload = new JsonObject();
			queryPayload.addProperty("taskId", taskId.toString());
			var result = service.query(Query.of(UUID.randomUUID(), workspace.id(), Operation.GET_TASK, queryPayload), UI);
			assertEquals("succeeded", result.status(), result.diagnostics().toString());
			JsonObject projection = result.data().getAsJsonObject();
			if (projection.getAsJsonObject("task").get("state").getAsString().equals("failed")
					&& projection.getAsJsonArray("diagnostics").asList().stream()
					.anyMatch(raw -> raw.getAsJsonObject().get("code").getAsString().equals("JAVA_COMPILE_ERROR")))
				return projection;
			Thread.sleep(25);
		}
		throw new AssertionError("Routed compiler failure was not observed");
	}

	private static QueryResult sourceQuery(WorkspaceApplicationService service, UUID workspaceId, UUID taskId, String sourcePath) {
		JsonObject payload = new JsonObject();
		payload.addProperty("taskId", taskId.toString());
		payload.addProperty("afterLogSequence", 0);
		payload.addProperty("sourcePath", sourcePath);
		return service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_TASK, payload), UI);
	}
}
