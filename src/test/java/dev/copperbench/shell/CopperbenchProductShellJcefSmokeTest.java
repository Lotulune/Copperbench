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
import dev.copperbench.bridge.JcefLegacyPluginBridgeTransport;
import dev.copperbench.bridge.JcefWorkspaceOpenBridgeTransport;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import net.mcreator.Launcher;
import net.mcreator.io.LoggingSystem;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.chromium.CefUtils;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.util.MCreatorVersionNumber;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "copperbench.stage4.jcefSmoke", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CopperbenchProductShellJcefSmokeTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111141");
	private static final Gson JSON = new Gson();

	@BeforeAll static void initializePreferences() throws Exception {
		LoggingSystem.init();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		Properties configuration = new Properties();
		configuration.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
		Launcher.version = new MCreatorVersionNumber(configuration);
		PreferencesManager.init();
		PluginLoader.initInstance();
		ThemeManager.loadThemes();
		L10N.initTranslations();
	}

	@AfterAll static void closeCef() {
		CefUtils.close();
	}

	@Order(2)
	@Test void loadsOfflineReactShellAndCompletesNativeHandshake() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		Clock clock = Clock.systemUTC();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
		WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));

		CountDownLatch loaded = new CountDownLatch(1);
		CountDownLatch legacyWindowRequested = new CountDownLatch(1);
		try (WebView webView = new WebView(CopperbenchProductShell.UI_URL);
				JcefCoreBridgeTransport ignored = webView.attachCoreBridge(WORKSPACE_ID, adapter);
				JcefLegacyPluginBridgeTransport ignoredLegacy = JcefLegacyPluginBridgeTransport.attach(webView,
						legacyWindowRequested::countDown)) {
			webView.addLoadListener(loaded::countDown);
			webView.forceLoad();
			assertTrue(loaded.await(30, TimeUnit.SECONDS), "Packaged product shell did not finish loading");

			String ready = "false";
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (!"true".equals(ready) && System.nanoTime() < deadline) {
				ready = webView.executeScript("document.querySelector('[data-testid=app-shell]') !== null"
						+ " && document.body.textContent.includes('JCEF 原生桥接')",
						WebView.JSExecutionType.RETURN_VALUE);
				if (!"true".equals(ready))
					Thread.sleep(100);
			}
			assertEquals("true", ready, "React shell did not bind the native UI-Core host");

			String legacyHostReady = webView.executeScript(
					"window.__COPPERBENCH_LEGACY_PLUGIN_HOST__?.available === true",
					WebView.JSExecutionType.RETURN_VALUE);
			assertEquals("true", legacyHostReady, "React shell did not bind the legacy plugin host");
			webView.executeScriptAsync("document.querySelector('[data-testid=nav-plugins]').click()");
			String legacyButtonEnabled = "false";
			deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (!"true".equals(legacyButtonEnabled) && System.nanoTime() < deadline) {
				legacyButtonEnabled = webView.executeScript(
						"document.querySelector('[data-testid=open-legacy-plugin-window]')?.disabled === false",
						WebView.JSExecutionType.RETURN_VALUE);
				if (!"true".equals(legacyButtonEnabled))
					Thread.sleep(100);
			}
			assertEquals("true", legacyButtonEnabled, "React plugin view did not select the native legacy host");
			webView.executeScriptAsync(
					"document.querySelector('[data-testid=open-legacy-plugin-window]').click()");
			assertTrue(legacyWindowRequested.await(5, TimeUnit.SECONDS),
					"Legacy plugin action did not reach the Java host");
		}
	}

	@Order(3)
	@Test void workspaceOpenBridgeRejectsMissingFilesAndAcceptsPersistedMcreatorFiles() throws Exception {
		Path temporaryRoot = Files.createTempDirectory("copperbench-workspace-open-");
		Path existingWorkspace = temporaryRoot.resolve("created.mcreator");
		Files.writeString(existingWorkspace, "{}");
		Path missingWorkspace = temporaryRoot.resolve("missing.mcreator");
		AtomicReference<Path> openedWorkspace = new AtomicReference<>();
		CountDownLatch opened = new CountDownLatch(1);
		try (WebView webView = new WebView("about:blank");
				JcefWorkspaceOpenBridgeTransport ignored = JcefWorkspaceOpenBridgeTransport.attach(webView, file -> {
					openedWorkspace.set(file.toPath());
					opened.countDown();
				})) {
			CountDownLatch loaded = new CountDownLatch(1);
			webView.addLoadListener(loaded::countDown);
			webView.forceLoad();
			assertTrue(loaded.await(30, TimeUnit.SECONDS), "Workspace open bridge page did not load");
			await(() -> "true".equals(webView.executeScript(
					"window.__COPPERBENCH_WORKSPACE_OPEN_HOST__?.available === true",
					WebView.JSExecutionType.RETURN_VALUE)), 30,
					"Workspace open bridge did not install its native host");

			webView.executeScriptAsync("""
					window.__COPPERBENCH_WORKSPACE_OPEN_RESULT__ = 'pending';
					window.__COPPERBENCH_WORKSPACE_OPEN_HOST__.open(%s)
						.then(function() { window.__COPPERBENCH_WORKSPACE_OPEN_RESULT__ = 'accepted'; })
						.catch(function() { window.__COPPERBENCH_WORKSPACE_OPEN_RESULT__ = 'rejected'; });
					""".formatted(JSON.toJson(missingWorkspace.toString())));
			await(() -> "rejected".equals(webView.executeScript(
					"window.__COPPERBENCH_WORKSPACE_OPEN_RESULT__",
					WebView.JSExecutionType.RETURN_VALUE)), 10,
					"Workspace open bridge accepted a missing workspace file");

			webView.executeScriptAsync("""
					window.__COPPERBENCH_WORKSPACE_OPEN_RESULT__ = 'pending';
					window.__COPPERBENCH_WORKSPACE_OPEN_HOST__.open(%s)
						.then(function() { window.__COPPERBENCH_WORKSPACE_OPEN_RESULT__ = 'accepted'; })
						.catch(function() { window.__COPPERBENCH_WORKSPACE_OPEN_RESULT__ = 'rejected'; });
					""".formatted(JSON.toJson(existingWorkspace.toString())));
			assertTrue(opened.await(10, TimeUnit.SECONDS), "Workspace open bridge did not invoke the host action");
			assertEquals(existingWorkspace.toAbsolutePath(), openedWorkspace.get().toAbsolutePath());
		} finally {
			Files.deleteIfExists(existingWorkspace);
			Files.deleteIfExists(temporaryRoot);
		}
	}

	@Order(1)
	@Test void recoversFromARealRendererCrashWithoutReplacingCommittedWorkspaceState() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		Clock clock = Clock.systemUTC();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
		WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));

		AtomicInteger browserGeneration = new AtomicInteger();
		AtomicReference<WebView> currentWebView = new AtomicReference<>();
		Instant testStartedAt = Instant.now().minusSeconds(1);
		RecoverableBrowserHost[] hostRef = new RecoverableBrowserHost[1];
		SwingUtilities.invokeAndWait(() -> hostRef[0] = new RecoverableBrowserHost(
				() -> new RealJcefBrowserHandle(adapter, browserGeneration, currentWebView), () -> {
				}));
		RecoverableBrowserHost host = hostRef[0];
		try {
			SwingUtilities.invokeAndWait(host::forceLoad);
			await(() -> isReactShellReady(currentWebView.get()), 30,
					"Initial React shell did not finish loading before renderer crash");
			assertEquals(1, browserGeneration.get());

			terminateCurrentTestHelpersUntilRendererExits(host, testStartedAt);
			await(host::isRecovering, 20, "Real renderer crash did not show the recovery surface");
			assertTrue(host.recoveryStatus().contains("Java 服务保留"));

			SwingUtilities.invokeAndWait(host::retryRecovery);
			await(() -> browserGeneration.get() == 2 && !host.isRecovering()
					&& isReactShellReady(currentWebView.get()), 30,
					"Replacement browser did not recover the React shell");

			WorkspaceState retained = store.read(WORKSPACE_ID).orElseThrow();
			assertEquals(WORKSPACE_ID, retained.id());
			assertEquals(0, retained.revision());
		} finally {
			SwingUtilities.invokeAndWait(host::close);
		}
	}

	private static boolean isReactShellReady(WebView webView) {
		if (webView == null)
			return false;
		return "true".equals(webView.executeScript(
				"document.querySelector('[data-testid=app-shell]') !== null"
						+ " && document.body.textContent.includes('JCEF 原生桥接')",
				WebView.JSExecutionType.RETURN_VALUE));
	}

	private static void await(BooleanSupplier condition, int timeoutSeconds, String failureMessage) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline)
			Thread.sleep(100);
		assertTrue(condition.getAsBoolean(), failureMessage);
	}

	private static void terminateCurrentTestHelpersUntilRendererExits(RecoverableBrowserHost host,
			Instant testStartedAt) throws Exception {
		AtomicReference<List<ProcessHandle>> rendererProcesses = new AtomicReference<>(List.of());
		await(() -> {
			List<ProcessHandle> current = findRendererProcesses(testStartedAt);
			rendererProcesses.set(current);
			return !current.isEmpty();
		}, 10, "No renderer JCEF helper process started by the smoke test");

		for (ProcessHandle renderer : rendererProcesses.get()) {
			if (!renderer.isAlive() || !renderer.destroyForcibly())
				continue;
			renderer.onExit().get(10, TimeUnit.SECONDS);
			long callbackDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (!host.isRecovering() && System.nanoTime() < callbackDeadline)
				Thread.sleep(100);
			if (host.isRecovering())
				return;
		}
		assertTrue(host.isRecovering(), "Terminating the renderer process did not reach the renderer callback");
	}

	private static List<ProcessHandle> findRendererProcesses(Instant testStartedAt) throws Exception {
		String helperPath = Path.of(System.getProperty("java.home"), "bin", "jcef_helper.exe")
				.toAbsolutePath().normalize().toString();
		String quotedPath = helperPath.replace("'", "''");
		String quotedCutoff = testStartedAt.toString().replace("'", "''");
		String script = "$cutoff=[DateTimeOffset]::Parse('" + quotedCutoff + "'); "
				+ "Get-CimInstance Win32_Process | Where-Object { "
				+ "$_.Name -eq 'jcef_helper.exe' -and $_.ExecutablePath -eq '" + quotedPath + "' "
				+ "-and $_.CommandLine -match '(?:^|\\s)--type=renderer(?:\\s|$)' "
				+ "-and $_.CreationDate.ToUniversalTime() -ge $cutoff.UtcDateTime } | "
				+ "ForEach-Object { $_.ProcessId }";
		Process process = new ProcessBuilder("pwsh", "-NoProfile", "-NonInteractive", "-Command", script)
				.redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (!process.waitFor(10, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IllegalStateException("Timed out while locating the JCEF renderer process");
		}
		if (process.exitValue() != 0)
			throw new IllegalStateException("Could not locate the JCEF renderer process: " + output.strip());
		return output.lines().map(String::strip).filter(line -> line.matches("\\d+"))
				.map(Long::parseLong).map(ProcessHandle::of).flatMap(java.util.Optional::stream).toList();
	}

	private static WorkspaceState workspace() {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, "JCEF Smoke Workspace", "mod", 0, false, generator,
				new JsonObject(), List.of());
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean() throws Exception;
	}

	private static final class RealJcefBrowserHandle implements RecoverableBrowserHost.BrowserHandle {
		private final WebView webView = new WebView(CopperbenchProductShell.UI_URL);
		private final JcefCoreBridgeTransport coreTransport;

		private RealJcefBrowserHandle(WorkspaceEntryAdapter adapter, AtomicInteger generation,
				AtomicReference<WebView> currentWebView) {
			this.coreTransport = webView.attachCoreBridge(WORKSPACE_ID, adapter);
			generation.incrementAndGet();
			currentWebView.set(webView);
		}

		@Override public Component component() {
			return webView;
		}

		@Override public void addLoadListener(Runnable listener) {
			webView.addLoadListener(listener::run);
		}

		@Override public void addRendererTerminationListener(Consumer<String> listener) {
			webView.addRendererTerminationListener((status, errorCode, errorString) ->
					listener.accept(status.name() + " (" + errorCode + ": " + errorString + ")"));
		}

		@Override public void forceLoad() {
			webView.forceLoad();
		}

		@Override public void close() {
			coreTransport.close();
			webView.close();
		}
	}
}
