/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

import com.google.gson.JsonObject;
import dev.copperbench.bridge.JcefCoreBridgeTransport;
import dev.copperbench.bridge.JcefDiagnosticsBridgeTransport;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.diagnostics.DiagnosticBundleService;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Windows JCEF acceptance for the local diagnostic bundle flow. */
@EnabledOnOs(OS.WINDOWS)
class Stage10NativeJcefDiagnosticsBundleTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111143");

	@BeforeAll static void initializePreferences() throws Exception {
		LoggingSystem.init();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		PropertiesLoader.initialize();
	}

	@Test @Timeout(value = 3, unit = TimeUnit.MINUTES)
	void exportsRedactedBundleFromTheNativeHelpView() throws Exception {
		Path root = Files.createTempDirectory("copperbench-native-diagnostics-");
		Path logs = Files.createDirectories(root.resolve("logs"));
		Path workspaceRoot = Files.createDirectories(root.resolve("workspace"));
		Files.writeString(logs.resolve("mcreator.log"),
				"password=hunter2 Authorization: Bearer native-token C:\\Users\\alice\\private\\mod.java");
		Files.writeString(workspaceRoot.resolve("sample.mcreator"), "private workspace");
		AtomicReference<java.io.File> lastOpened = new AtomicReference<>();
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		Clock clock = Clock.fixed(Instant.parse("2026-08-29T01:00:00Z"), ZoneOffset.UTC);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
		WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
		DiagnosticBundleService bundles = new DiagnosticBundleService(root.resolve("output"), logs, workspaceRoot,
				() -> new JsonObject(), clock);
		JFrame[] windowRef = new JFrame[1];

		try (WebView webView = new WebView(CopperbenchProductShell.UI_URL);
				JcefCoreBridgeTransport core = webView.attachCoreBridge(WORKSPACE_ID, adapter);
				JcefDiagnosticsBridgeTransport diagnostics = JcefDiagnosticsBridgeTransport.attach(webView, bundles,
						lastOpened::set)) {
				SwingUtilities.invokeAndWait(() -> {
					JFrame window = new JFrame("Copperbench diagnostics acceptance");
					window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
					window.setContentPane(webView);
					window.setSize(new Dimension(1280, 800));
					window.setVisible(true);
					windowRef[0] = window;
				});
				CountDownLatch loaded = new CountDownLatch(1);
				webView.addLoadListener(loaded::countDown);
				webView.forceLoad();
				assertTrue(loaded.await(30, TimeUnit.SECONDS), "Native diagnostics shell did not load");
				await(webView, "document.querySelector('[data-testid=app-shell]') !== null", 30);
				webView.executeScriptAsync("document.querySelector('[data-testid=nav-help]')?.click()");
				await(webView, "document.querySelector('[data-testid=diagnostic-support-panel]') !== null", 15);
				webView.executeScriptAsync("""
					window.__CB_DIAGNOSTIC_RESULT__ = 'pending';
					window.__COPPERBENCH_DIAGNOSTICS_HOST__.exportBundle(false)
					  .then(function(result) { window.__CB_DIAGNOSTIC_RESULT__ = result.fileName; })
					  .catch(function() { window.__CB_DIAGNOSTIC_RESULT__ = 'failed'; });
					""");
				await(webView, "window.__CB_DIAGNOSTIC_RESULT__ !== 'pending'", 20);
				String fileName = webView.executeScript("window.__CB_DIAGNOSTIC_RESULT__",
						WebView.JSExecutionType.RETURN_VALUE);
				assertTrue(fileName.endsWith(".zip"));
				Path exported = lastOpened.get() == null ? null : lastOpened.get().toPath();
				assertTrue(exported != null && Files.isRegularFile(exported));
				try (ZipFile zip = new ZipFile(exported.toFile())) {
					assertTrue(zip.getEntry("environment.json") != null);
					assertTrue(zip.getEntry("manifest.json") != null);
					assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith("reproduction/")));
					String log = new String(zip.getInputStream(zip.getEntry("logs/mcreator.log")).readAllBytes(), StandardCharsets.UTF_8);
					assertFalse(log.contains("hunter2"));
					assertFalse(log.contains("native-token"));
					assertFalse(log.contains("alice"));
				}
			} finally {
				if (windowRef[0] != null) SwingUtilities.invokeAndWait(windowRef[0]::dispose);
			}
		}

	private static void await(WebView webView, String expression, int timeoutSeconds) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (!"true".equals(webView.executeScript(expression, WebView.JSExecutionType.RETURN_VALUE))
				&& System.nanoTime() < deadline) Thread.sleep(100);
		assertTrue("true".equals(webView.executeScript(expression, WebView.JSExecutionType.RETURN_VALUE)));
	}

	private static WorkspaceState workspace() {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, "Native diagnostics", "mod", 0, false, generator,
				new JsonObject(), List.of());
	}

	private static final class PropertiesLoader {
		private static void initialize() throws Exception {
			if (Launcher.version == null) {
				java.util.Properties configuration = new java.util.Properties();
				configuration.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
				Launcher.version = new MCreatorVersionNumber(configuration);
			}
			if (PreferencesManager.PREFERENCES == null) PreferencesManager.init();
			if (PluginLoader.INSTANCE == null) PluginLoader.initInstance();
			if (Theme.current() == null) ThemeManager.loadThemes();
			L10N.initTranslations();
		}
	}

}
