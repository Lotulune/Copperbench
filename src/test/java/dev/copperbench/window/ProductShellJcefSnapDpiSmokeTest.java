/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.window;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.copperbench.bridge.JcefWindowBridgeTransport;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.shell.CopperbenchProductShell;
import net.mcreator.Launcher;
import net.mcreator.io.LoggingSystem;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.chromium.CefUtils;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real JCEF product-shell window chrome: React reports regions over the window
 * bridge, Win32 hit-testing exposes Snap's HTMAXBUTTON, and WM_DPICHANGED is applied.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "copperbench.stage8.jcefSnapDpi", matches = "true")
class ProductShellJcefSnapDpiSmokeTest {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111148");
	private static final int HTCLIENT = 1;
	private static final int HTCAPTION = 2;
	private static final int HTMAXBUTTON = 9;
	private static final int HTTOPLEFT = 13;

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

	@Test
	@Timeout(value = 4, unit = TimeUnit.MINUTES)
	void realJcefShellReportsChromeRegionsAndExposesSnapAndDpi() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		Clock clock = Clock.systemUTC();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
		WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));

		JFrame[] windowRef = new JFrame[1];
		WindowsWindowChromeController[] chromeRef = new WindowsWindowChromeController[1];
		WebView[] webViewRef = new WebView[1];
		SwingUtilities.invokeAndWait(() -> {
			JFrame window = new JFrame("Copperbench JCEF Snap/DPI");
			window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			WindowsWindowChromeController chrome = WindowsWindowChromeController.prepare(window);
			assertNotNull(chrome, "Native chrome must be prepared on Windows");
			window.setSize(1100, 760);
			window.setLocationRelativeTo(null);
			WebView webView = new WebView(CopperbenchProductShell.UI_URL);
			webView.attachCoreBridge(WORKSPACE_ID, adapter);
			JcefWindowBridgeTransport.attach(webView, window, window::dispose, chrome::accept,
					chrome::isUsingCustomFrame);
			window.setContentPane(webView);
			window.setVisible(true);
			assertTrue(chrome.install(), "Native chrome must install on the JCEF product window");
			webView.forceLoad();
			windowRef[0] = window;
			chromeRef[0] = chrome;
			webViewRef[0] = webView;
		});

		JFrame window = windowRef[0];
		WindowsWindowChromeController chrome = chromeRef[0];
		WebView webView = webViewRef[0];
		JsonObject evidence = new JsonObject();
		evidence.addProperty("schemaVersion", "1.0");
		evidence.addProperty("kind", "jcef-snap-dpi-smoke");
		try {
			await(() -> "true".equals(webView.executeScript(
					"document.querySelector('[data-testid=app-shell]') !== null",
					WebView.JSExecutionType.RETURN_VALUE)), 30, "React shell did not load in JCEF");
			await(() -> chrome.chromeSnapshotForTesting() != null, 20,
					"React title bar did not report chrome regions to the JCEF window bridge");

			WindowChromeSnapshot snapshot = chrome.chromeSnapshotForTesting();
			assertNotNull(snapshot);
			assertEquals("1.0", snapshot.schemaVersion());
			assertTrue(snapshot.regions().stream().anyMatch(region -> region.kind() == WindowChromeSnapshot.Kind.MAXIMIZE),
					"Reported regions must include the maximize/Snap target");

			WindowChromeHitTest.WindowBounds bounds = chrome.nativeBoundsForTesting();
			double dpr = chrome.devicePixelRatioForTesting();
			int topLeft = chrome.nativeHitTestForTesting(bounds.left() + 2, bounds.top() + 2);
			WindowChromeSnapshot.Region maximize = snapshot.regions().stream()
					.filter(region -> region.kind() == WindowChromeSnapshot.Kind.MAXIMIZE).findFirst().orElseThrow();
			int maxX = bounds.left() + (int) ((maximize.bounds().x() + maximize.bounds().width() / 2) * dpr);
			int maxY = bounds.top() + (int) ((maximize.bounds().y() + maximize.bounds().height() / 2) * dpr);
			int maxHit = chrome.nativeHitTestForTesting(maxX, maxY);
			WindowChromeSnapshot.Region caption = snapshot.regions().stream()
					.filter(region -> region.kind() == WindowChromeSnapshot.Kind.CAPTION).findFirst().orElseThrow();
			int capX = bounds.left() + (int) ((caption.bounds().x() + Math.min(48, caption.bounds().width() / 2)) * dpr);
			int capY = bounds.top() + (int) ((caption.bounds().y() + caption.bounds().height() / 2) * dpr);
			int capHit = chrome.nativeHitTestForTesting(capX, capY);

			assertEquals(HTTOPLEFT, topLeft, "JCEF window must expose HTTOPLEFT for resize");
			assertEquals(HTMAXBUTTON, maxHit, "JCEF-reported maximize region must expose HTMAXBUTTON for Snap Layout");
			assertTrue(capHit == HTCAPTION || capHit == HTCLIENT, "Caption/client hit test must stay in the title bar");

			WindowChromeHitTest.WindowBounds dpiBounds = new WindowChromeHitTest.WindowBounds(bounds.left() + 16,
					bounds.top() + 16, bounds.right() + 116, bounds.bottom() + 66);
			chrome.applyDpiChangeForTesting(144, dpiBounds);
			assertEquals(dpiBounds, chrome.nativeBoundsForTesting());
			assertEquals(1.5, chrome.devicePixelRatioForTesting());

			evidence.addProperty("reactShellLoaded", true);
			evidence.addProperty("chromeRegionsReported", true);
			evidence.addProperty("regionCount", snapshot.regions().size());
			evidence.addProperty("topLeftHit", topLeft);
			evidence.addProperty("maximizeHit", maxHit);
			evidence.addProperty("captionHit", capHit);
			evidence.addProperty("nativeChildHookCount", chrome.childHookCountForTesting());
			assertTrue(chrome.childHookCountForTesting() > 0,
					"Real JCEF must expose at least one same-process child HWND for native titlebar input proxying");
			evidence.addProperty("dpiAfterChange", chrome.devicePixelRatioForTesting());
			evidence.addProperty("customFrame", chrome.isUsingCustomFrame());
			evidence.addProperty("passed", true);
		} finally {
			writeEvidence(evidence);
			SwingUtilities.invokeAndWait(() -> {
				chrome.close();
				window.dispose();
			});
		}
	}

	private static void writeEvidence(JsonObject evidence) {
		try {
			Path repository = Path.of(".").toAbsolutePath().normalize();
			Path dir = repository.resolve("evidence/stage-8/" + LocalDate.now());
			Files.createDirectories(dir);
			evidence.addProperty("completedAt", Instant.now().toString());
			Files.writeString(dir.resolve("jcef-snap-dpi.json"), JSON.toJson(evidence), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	private static void await(BooleanSupplier condition, int timeoutSeconds, String failure) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline)
			Thread.sleep(100);
		assertTrue(condition.getAsBoolean(), failure);
	}

	private static WorkspaceState workspace() {
		com.google.gson.JsonObject generator = new com.google.gson.JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, "JCEF Snap DPI", "mod", 0, false, generator,
				new com.google.gson.JsonObject(), java.util.List.of());
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean() throws Exception;
	}
}
