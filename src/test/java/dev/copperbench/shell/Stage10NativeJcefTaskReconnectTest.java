/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.copperbench.bridge.JcefCoreBridgeTransport;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.GradleWorkspaceBackend;
import dev.copperbench.generator.GradleWorkspaceTaskGateway;
import net.mcreator.Launcher;
import net.mcreator.io.LoggingSystem;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stage 10 Windows-native JCEF proof for retained long-task replay after browser reconnect. */
@EnabledOnOs(OS.WINDOWS)
class Stage10NativeJcefTaskReconnectTest {
	// Do not call CefUtils.close() here. Nightly executes this inside the shared
	// test JVM, so closing the process-wide CEF runtime would break later native
	// JCEF tests even though this test's WebView instances are closed normally.

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111142");
	private static final Gson JSON = new Gson();

	@BeforeAll static void initializeJcefPrerequisites() throws Exception {
		LoggingSystem.init();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		if (Launcher.version == null) {
			Properties configuration = new Properties();
			configuration.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
			Launcher.version = new MCreatorVersionNumber(configuration);
		}
		if (PreferencesManager.PREFERENCES == null)
			PreferencesManager.init();
		if (PluginLoader.INSTANCE == null)
			PluginLoader.initInstance();
		if (Theme.current() == null)
			ThemeManager.loadThemes();
		L10N.initTranslations();
	}

	@Test void replaysLongRunningTaskEventsAcrossRealNativeJcefReconnect() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		Clock clock = Clock.systemUTC();
		Path workspaceRoot = Files.createTempDirectory("copperbench-native-task-reconnect-");
		CountDownLatch processStarted = new CountDownLatch(1);
		AtomicReference<Consumer<String>> processOutput = new AtomicReference<>();
		GradleProcessRunner processRunner = (root, arguments, timeout, output) -> {
			processOutput.set(output);
			output.accept("native online task log");
			processStarted.countDown();
			try {
				new CountDownLatch(1).await();
				return new GradleProcessRunner.ProcessResult(0, false);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return new GradleProcessRunner.ProcessResult(130, false);
			}
		};
		GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
			@Override public String displayName() {
				return "Native JCEF fixture";
			}

			@Override public String diagnosticPrefix() {
				return "NATIVE_JCEF_FIXTURE";
			}

			@Override public List<ValidationIssue> validate(WorkspaceState workspace) {
				return List.of();
			}

			@Override public GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws Exception {
				Files.createDirectories(targetRoot);
				return new GenerationResult("fabric-1.21.1", "jcef_smoke", List.of("src/generated.txt"));
			}

			@Override public boolean buildOutputAvailable(Path targetRoot) {
				return true;
			}
		};

		try (GradleWorkspaceTaskGateway tasks = new GradleWorkspaceTaskGateway(store, ignored -> workspaceRoot,
				backend, clock, UUID::randomUUID, processRunner)) {
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks, clock, UUID::randomUUID);
			RequestContext context = new RequestContext(Actor.UI, PermissionProfile.WORKSPACE);
			WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service, context);

			UUID taskId;
			try (WebView firstBrowser = new WebView("about:blank")) {
				prepareNativeEventCapture(firstBrowser);
				try (JcefCoreBridgeTransport ignored = firstBrowser.attachCoreBridge(WORKSPACE_ID, adapter)) {
					JsonObject payload = new JsonObject();
					payload.addProperty("clientMutationId", UUID.randomUUID().toString());
					payload.addProperty("scope", "workspace");
					var started = service.execute(Command.of(UUID.randomUUID(), WORKSPACE_ID, 0,
							Operation.BUILD_WORKSPACE, payload), context);
					taskId = UUID.fromString(started.result().task().getAsJsonObject().get("id").getAsString());
					assertTrue(processStarted.await(10, TimeUnit.SECONDS), "Managed task process did not start");
					await(() -> capturedTaskLog(firstBrowser, taskId, "native online task log"), 10,
							"Live task log did not cross the native JCEF transport");
				}
			}

			Consumer<String> disconnectedOutput = processOutput.get();
			assertNotNull(disconnectedOutput, "Managed task did not expose its output sink");
			disconnectedOutput.accept("native disconnected task log");

			try (WebView secondBrowser = new WebView("about:blank")) {
				prepareNativeEventCapture(secondBrowser);
				try (JcefCoreBridgeTransport ignored = secondBrowser.attachCoreBridge(WORKSPACE_ID, adapter)) {
					await(() -> capturedTaskLog(secondBrowser, taskId, "native disconnected task log"), 10,
							"Retained task log was not replayed after native JCEF reconnect");

					JsonObject cancelPayload = new JsonObject();
					cancelPayload.addProperty("clientMutationId", UUID.randomUUID().toString());
					cancelPayload.addProperty("taskId", taskId.toString());
					var cancelled = service.execute(Command.of(UUID.randomUUID(), WORKSPACE_ID, 0,
							Operation.CANCEL_TASK, cancelPayload), context);
					assertEquals("cancelled", cancelled.result().status());
					await(() -> capturedCancelledTask(secondBrowser, taskId), 10,
							"Cancelled task completion did not reach the reconnected native JCEF host");
				}
			}
		}
	}

	private static void prepareNativeEventCapture(WebView webView) throws Exception {
		CountDownLatch loaded = new CountDownLatch(1);
		webView.addLoadListener(loaded::countDown);
		webView.forceLoad();
		assertTrue(loaded.await(30, TimeUnit.SECONDS), "Native JCEF event capture page did not load");
		webView.executeScriptAsync("""
				window.__COPPERBENCH_NATIVE_TASK_EVENTS__ = [];
				window.addEventListener('copperbench:event', function(event) {
				    window.__COPPERBENCH_NATIVE_TASK_EVENTS__.push(event.detail);
				});
				window.__COPPERBENCH_NATIVE_TASK_CAPTURE_READY__ = true;
				""");
		await(() -> "true".equals(webView.executeScript(
				"window.__COPPERBENCH_NATIVE_TASK_CAPTURE_READY__ === true",
				WebView.JSExecutionType.RETURN_VALUE)), 10, "Native JCEF event capture hook was not installed");
	}

	private static boolean capturedTaskLog(WebView webView, UUID taskId, String text) {
		return "true".equals(webView.executeScript("""
				(window.__COPPERBENCH_NATIVE_TASK_EVENTS__ || []).some(function(raw) {
				    try {
				        var event = JSON.parse(raw);
				        return event.event === 'task_log_appended'
				            && event.payload.taskId === %s
				            && (event.payload.entries || []).some(function(entry) { return entry.text === %s; });
				    } catch (e) { return false; }
				})
				""".formatted(JSON.toJson(taskId.toString()), JSON.toJson(text)), WebView.JSExecutionType.RETURN_VALUE));
	}

	private static boolean capturedCancelledTask(WebView webView, UUID taskId) {
		return "true".equals(webView.executeScript("""
				(window.__COPPERBENCH_NATIVE_TASK_EVENTS__ || []).some(function(raw) {
				    try {
				        var event = JSON.parse(raw);
				        return event.event === 'task_completed'
				            && event.payload.task.id === %s
				            && event.payload.task.state === 'cancelled';
				    } catch (e) { return false; }
				})
				""".formatted(JSON.toJson(taskId.toString())), WebView.JSExecutionType.RETURN_VALUE));
	}

	private static void await(BooleanSupplier condition, int timeoutSeconds, String failureMessage) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline)
			Thread.sleep(100);
		assertTrue(condition.getAsBoolean(), failureMessage);
	}

	private static WorkspaceState workspace() {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, "Native JCEF Task Replay", "mod", 0, false, generator,
				new JsonObject(), List.of());
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean() throws Exception;
	}
}
