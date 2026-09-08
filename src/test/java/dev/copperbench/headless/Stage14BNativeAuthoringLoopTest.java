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
import net.mcreator.workspace.WorkspaceFolderManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage14BNativeAuthoringLoopTest {

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test
	@Timeout(value = 25, unit = TimeUnit.MINUTES)
	void emptyDirectoryToNativeMultiFileCompileFailureRepairAndReopenUsesOnlyProductEntryPointsAndFiles()
			throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String modId = "stage14b_native_" + suffix;
		Path suggestedRoot = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath()
				.toAbsolutePath().normalize();
		Path workspaceRoot = suggestedRoot.resolve(modId);
		Path workspaceFile = workspaceRoot.resolve(modId + ".mcreator");
		try {
			RunResult discovery = bootstrap(new String[] { "list-generators" }, request -> false);
			assertEquals(HeadlessExitCode.SUCCESS.code(), discovery.exitCode());
			assertTrue(discovery.json().getAsJsonObject("data").getAsJsonArray("generators").toString()
					.contains("fabric-1.21.1"));

			RunResult created = bootstrap(new String[] { "create-workspace", "--generator-id", "fabric-1.21.1",
					"--mod-name", "Stage 14B Native Loop", "--mod-id", modId, "--workspace-folder",
					workspaceRoot.toString(), "--version", "1.0.0" }, request -> true);
			assertEquals(HeadlessExitCode.SUCCESS.code(), created.exitCode(), created.json().toString());
			assertEquals(workspaceFile.toAbsolutePath().normalize(), Path.of(created.json().getAsJsonObject("data")
					.get("workspaceFile").getAsString()).toAbsolutePath().normalize());

			RunResult environment = product(workspaceFile, "environment");
			assertEquals(HeadlessExitCode.SUCCESS.code(), environment.exitCode(), environment.json().toString());
			JsonObject execution = environment.json().getAsJsonObject("data").getAsJsonObject("execution");
			assertEquals("fabric-1.21.1", execution.get("generatorId").getAsString());
			assertEquals(21, execution.getAsJsonObject("java").get("requiredRelease").getAsInt());
			assertEquals(workspaceRoot.resolve("src/main/java").toAbsolutePath().normalize().toString(),
					Path.of(execution.get("sourceRoot").getAsString()).toAbsolutePath().normalize().toString());

			RunResult baselineBuild = product(workspaceFile, "build");
			assertEquals(HeadlessExitCode.SUCCESS.code(), baselineBuild.exitCode(), baselineBuild.json().toString());
			assertEquals("succeeded", baselineBuild.json().get("status").getAsString());

			Path sourceRoot = Path.of(execution.get("sourceRoot").getAsString());
			Path mainClass = findSingleJavaContaining(sourceRoot, "// Start of user code block mod init");
			String originalMain = Files.readString(mainClass, StandardCharsets.UTF_8);
			String hook = "\t\tnet.mcreator." + modId + ".nativecode.ColdStartHooks.init();";
			String startMarker = "\t\t// Start of user code block mod init";
			String lineSeparator = originalMain.contains("\r\n") ? "\r\n" : "\n";
			String hookedMain = originalMain.replace(startMarker, startMarker + lineSeparator + hook);
			assertFalse(originalMain.equals(hookedMain), "Generated mod entry did not expose the expected mod init block");
			Files.writeString(mainClass, hookedMain, StandardCharsets.UTF_8);

			Path nativeDir = sourceRoot.resolve(Path.of("net", "mcreator", modId, "nativecode"));
			Files.createDirectories(nativeDir);
			Path state = nativeDir.resolve("ColdStartState.java");
			Path hooks = nativeDir.resolve("ColdStartHooks.java");
			String stateSource = """
					package net.mcreator.%s.nativecode;

					public final class ColdStartState {
					    private static boolean initialized;

					    private ColdStartState() {}

					    public static void markInitialized() {
					        initialized = true;
					    }

					    public static boolean initialized() {
					        return initialized;
					    }
					}
					""".formatted(modId);
			String brokenHooks = """
					package net.mcreator.%s.nativecode;

					public final class ColdStartHooks {
					    private ColdStartHooks() {}

					    public static void init() {
					        ColdStartState.missingMethod();
					    }
					}
					""".formatted(modId);
			Files.writeString(state, stateSource, StandardCharsets.UTF_8);
			Files.writeString(hooks, brokenHooks, StandardCharsets.UTF_8);

			RunResult brokenBuild = product(workspaceFile, "build");
			assertEquals(HeadlessExitCode.INTERNAL_ERROR.code(), brokenBuild.exitCode(), brokenBuild.json().toString());
			assertEquals("failed", brokenBuild.json().get("status").getAsString());
			assertTrue(brokenBuild.json().getAsJsonArray("diagnostics").toString().contains("JAVA_COMPILE_ERROR"),
					brokenBuild.json().toString());
			assertTrue(brokenBuild.json().getAsJsonArray("diagnostics").toString().contains("ColdStartHooks.java"),
					brokenBuild.json().toString());
			assertTrue(Files.readString(mainClass, StandardCharsets.UTF_8).contains(hook),
					"Regeneration before the failed build must preserve the native hook");
			assertEquals(stateSource, Files.readString(state, StandardCharsets.UTF_8),
					"Independent native source must remain byte-exact through regeneration");

			String repairedHooks = brokenHooks.replace("ColdStartState.missingMethod();",
					"ColdStartState.markInitialized();");
			Files.writeString(hooks, repairedHooks, StandardCharsets.UTF_8);
			RunResult repairedBuild = product(workspaceFile, "build");
			assertEquals(HeadlessExitCode.SUCCESS.code(), repairedBuild.exitCode(), repairedBuild.json().toString());
			assertEquals("succeeded", repairedBuild.json().get("status").getAsString());
			assertTrue(Files.readString(mainClass, StandardCharsets.UTF_8).contains(hook));
			assertEquals(repairedHooks, Files.readString(hooks, StandardCharsets.UTF_8));
			assertEquals(stateSource, Files.readString(state, StandardCharsets.UTF_8));
			assertTrue(Files.isDirectory(workspaceRoot.resolve("build/libs")));

			// A fresh product invocation reopens the workspace and regenerates before build again.
			RunResult reopenedEnvironment = product(workspaceFile, "environment");
			assertEquals(HeadlessExitCode.SUCCESS.code(), reopenedEnvironment.exitCode(), reopenedEnvironment.json().toString());
			RunResult reopenedBuild = product(workspaceFile, "build");
			assertEquals(HeadlessExitCode.SUCCESS.code(), reopenedBuild.exitCode(), reopenedBuild.json().toString());
			assertTrue(Files.readString(mainClass, StandardCharsets.UTF_8).contains(hook));
			assertEquals(repairedHooks, Files.readString(hooks, StandardCharsets.UTF_8));
			assertEquals(stateSource, Files.readString(state, StandardCharsets.UTF_8));
		} finally {
			deleteRecursively(workspaceRoot);
		}
	}

	private static Path findSingleJavaContaining(Path root, String marker) throws IOException {
		try (var files = Files.walk(root)) {
			List<Path> matches = files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".java"))
					.filter(path -> !path.getFileName().toString().endsWith("ModClient.java"))
					.filter(path -> {
						try {
							return Files.readString(path, StandardCharsets.UTF_8).contains(marker);
						} catch (IOException exception) {
							throw new UncheckedIOException(exception);
						}
					}).toList();
			assertEquals(1, matches.size(), matches.toString());
			return matches.getFirst();
		}
	}

	private static RunResult bootstrap(String[] args, BootstrapProductLauncher.ApprovalPrompt approvalPrompt) {
		StringWriter buffer = new StringWriter();
		int exitCode = BootstrapProductLauncher.run(args, new PrintWriter(buffer, true),
				new WorkspaceCreationService(), approvalPrompt);
		return parse(exitCode, buffer);
	}

	private static RunResult product(Path workspace, String... command) {
		String[] args = new String[command.length + 2];
		args[0] = "--workspace";
		args[1] = workspace.toString();
		System.arraycopy(command, 0, args, 2, command.length);
		StringWriter buffer = new StringWriter();
		int exitCode = HeadlessProductLauncher.run(args, new PrintWriter(buffer, true));
		return parse(exitCode, buffer);
	}

	private static RunResult parse(int exitCode, StringWriter buffer) {
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

