/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFolderManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapProductLauncherTest {

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void generatorDiscoveryWorksWithoutAnExistingWorkspace() {
		RunResult result = run(new String[] { "list-generators" }, request -> {
			throw new AssertionError("Discovery must never request workspace-creation approval");
		});

		assertEquals(HeadlessExitCode.SUCCESS.code(), result.exitCode());
		assertEquals("list_new_workspace_generators", result.json().get("operation").getAsString());
		assertEquals("succeeded", result.json().get("status").getAsString());
		assertEquals(9, result.json().getAsJsonObject("data").getAsJsonArray("generators").size());
		assertTrue(result.json().getAsJsonObject("data").get("suggestedWorkspaceFoldersRoot").getAsString()
				.contains("MCreatorWorkspaces"));
	}

	@Test void invalidCreationIsRejectedBeforeTrustedApprovalPrompt() throws Exception {
		Path root = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath().normalize();
		Path workspaceFolder = root.resolve("stage14b-invalid-" + UUID.randomUUID());
		AtomicInteger prompts = new AtomicInteger();
		try {
			RunResult result = run(new String[] { "create-workspace", "--generator-id", "fabric-1.21.1",
					"--mod-name", "Cold Start", "--mod-id", "Invalid ID!", "--workspace-folder",
					workspaceFolder.toString() }, request -> {
				prompts.incrementAndGet();
				return true;
			});

			assertEquals(HeadlessExitCode.VALIDATION_FAILED.code(), result.exitCode());
			assertEquals("rejected", result.json().get("status").getAsString());
			assertTrue(result.json().getAsJsonArray("diagnostics").toString().contains("MOD_ID_INVALID"));
			assertEquals(0, prompts.get());
			assertFalse(Files.exists(workspaceFolder));
		} finally {
			deleteRecursively(workspaceFolder);
		}
	}

	@Test void bootstrapArgumentsDoNotOfferAnAgentSelfApprovalFlag() {
		assertThrows(IllegalArgumentException.class, () -> BootstrapProductLauncher.parse(new String[] {
				"create-workspace", "--generator-id", "fabric-1.21.1", "--mod-name", "Cold Start", "--mod-id",
				"cold_start", "--workspace-folder", "C:\\Users\\example\\MCreatorWorkspaces\\cold_start",
				"--approve", "true" }));
	}

	@Test void localApprovalCreatesARealWorkspaceAndReturnsTheNewMcreatorPath() throws Exception {
		Path root = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath().normalize();
		String modId = "stage14b_cold_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		Path workspaceFolder = root.resolve(modId);
		AtomicBoolean prompted = new AtomicBoolean();
		try {
			RunResult result = run(new String[] { "create-workspace", "--generator-id", "fabric-1.21.1",
					"--mod-name", "Stage 14B Cold Start", "--mod-id", modId, "--workspace-folder",
					workspaceFolder.toString(), "--version", "1.0.0" }, request -> {
				prompted.set(true);
				assertEquals("fabric-1.21.1", request.generatorId());
				assertEquals(workspaceFolder.toString(), request.workspaceFolderPath());
				return true;
			});

			assertTrue(prompted.get());
			assertEquals(HeadlessExitCode.SUCCESS.code(), result.exitCode());
			assertEquals("create_workspace", result.json().get("operation").getAsString());
			assertEquals("committed", result.json().get("status").getAsString());
			Path workspaceFile = Path.of(result.json().getAsJsonObject("data").get("workspaceFile").getAsString());
			assertEquals(workspaceFolder.resolve(modId + ".mcreator").toAbsolutePath().normalize(),
					workspaceFile.toAbsolutePath().normalize());
			assertTrue(Files.isRegularFile(workspaceFile));
			try (Workspace workspace = Workspace.readFromFS(workspaceFile.toFile(), null)) {
				assertEquals("fabric-1.21.1", workspace.getWorkspaceSettings().getCurrentGenerator());
				assertEquals(modId, workspace.getWorkspaceSettings().getModID());
			}
		} finally {
			deleteRecursively(workspaceFolder);
		}
	}

	@Test void localApprovalCreatesResourcePackWorkspaceForGraphicalFixture() throws Exception {
		Path root = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath().normalize();
		String modId = "stage15_graphical_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		Path workspaceFolder = root.resolve(modId);
		AtomicBoolean prompted = new AtomicBoolean();
		try {
			RunResult result = run(new String[] { "create-workspace", "--generator-id", "resourcepack-1.21.1",
					"--mod-name", "Stage15 Graphical Fixture", "--mod-id", modId, "--workspace-folder",
					workspaceFolder.toString(), "--version", "1.0.0" }, request -> {
				prompted.set(true);
				assertEquals("resourcepack-1.21.1", request.generatorId());
				assertEquals(workspaceFolder.toString(), request.workspaceFolderPath());
				return true;
			});

			assertTrue(prompted.get());
			assertEquals(HeadlessExitCode.SUCCESS.code(), result.exitCode());
			assertEquals("committed", result.json().get("status").getAsString());
			assertEquals("resourcepack-1.21.1",
					result.json().getAsJsonObject("data").get("generatorId").getAsString());
			Path workspaceFile = Path.of(result.json().getAsJsonObject("data").get("workspaceFile").getAsString());
			assertTrue(Files.isRegularFile(workspaceFile));
			assertEquals(workspaceFolder.resolve(modId + ".mcreator").toAbsolutePath().normalize(),
					workspaceFile.toAbsolutePath().normalize());
		} finally {
			deleteRecursively(workspaceFolder);
		}
	}

	@Test void decliningLocalApprovalLeavesTheTargetUntouched() throws Exception {
		Path root = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath().normalize();
		Path workspaceFolder = root.resolve("stage14b-denied-" + UUID.randomUUID());
		try {
			RunResult result = run(new String[] { "create-workspace", "--generator-id", "fabric-1.21.1",
					"--mod-name", "Stage 14B Denied", "--mod-id", "stage14b_denied", "--workspace-folder",
					workspaceFolder.toString() }, request -> false);

			assertEquals(HeadlessExitCode.PERMISSION_DENIED.code(), result.exitCode());
			assertEquals("rejected", result.json().get("status").getAsString());
			assertEquals("USER_APPROVAL_REQUIRED", result.json().get("code").getAsString());
			assertTrue(result.json().getAsJsonObject("denial").get("approvalRequired").getAsBoolean());
			assertFalse(Files.exists(workspaceFolder));
		} finally {
			deleteRecursively(workspaceFolder);
		}
	}

	private static RunResult run(String[] arguments, BootstrapProductLauncher.ApprovalPrompt approvalPrompt) {
		StringWriter buffer = new StringWriter();
		int exitCode = BootstrapProductLauncher.run(arguments, new PrintWriter(buffer, true),
				new WorkspaceCreationService(), approvalPrompt);
		String payload = buffer.toString().trim();
		assertEquals(1, payload.lines().count(), payload);
		return new RunResult(exitCode, JsonParser.parseString(payload).getAsJsonObject());
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (var paths = Files.walk(root)) {
			try {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException exception) {
						throw new UncheckedIOException(exception);
					}
				});
			} catch (UncheckedIOException exception) {
				throw exception.getCause();
			}
		}
	}

	private record RunResult(int exitCode, JsonObject json) {
	}
}
