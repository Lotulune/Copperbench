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

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Windows-native JCEF accessibility acceptance slice for the Stage 9 production shell. */
@EnabledOnOs(OS.WINDOWS)
class Stage9NativeJcefAccessibilityTest {
	// CEF is process-wide. Close this test's WebView, but do not call
	// CefUtils.close() because the Windows Nightly runs native JCEF tests in the
	// same Gradle test JVM.

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111139");
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

	@Test void productionShellPreservesDialogKeyboardAndTargetContractsInRealJcef() throws Exception {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		Clock clock = Clock.systemUTC();
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
		WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));

		try (WebView webView = new WebView(CopperbenchProductShell.UI_URL);
				JcefCoreBridgeTransport ignored = webView.attachCoreBridge(WORKSPACE_ID, adapter)) {
			SwingUtilities.invokeAndWait(() -> {
				webView.setPreferredSize(new Dimension(1280, 800));
				webView.setSize(1280, 800);
				webView.doLayout();
			});

			CountDownLatch loaded = new CountDownLatch(1);
			webView.addLoadListener(loaded::countDown);
			webView.forceLoad();
			assertTrue(loaded.await(30, TimeUnit.SECONDS), "Production React shell did not finish loading");
			await(() -> "true".equals(js(webView,
					"document.querySelector('[data-testid=app-shell]') !== null"
							+ " && document.body.textContent.includes('JCEF 原生桥接')")), 30,
					"Production shell did not bind the native JCEF UI-Core host");

			assertEquals("true", js(webView,
					"document.querySelector('[data-testid=global-announcer]')?.getAttribute('aria-live') === 'polite'"),
					"Production JCEF shell lost the polite live-region contract");

			webView.executeScriptAsync("""
					var openButton = document.querySelector('[data-testid=empty-primary-action]');
					if (openButton) { openButton.focus(); openButton.click(); }
					""");
			await(() -> "true".equals(js(webView,
					"document.querySelector('[data-testid=create-element-modal]') !== null")), 10,
					"Create-element dialog did not open in the native production shell");

			assertEquals("true", js(webView, """
					(function() {
					    var modal = document.querySelector('[data-testid=create-element-modal]');
					    return modal !== null
					        && modal.querySelector('[role=dialog][aria-modal=true]') !== null
					        && document.activeElement !== null
					        && modal.contains(document.activeElement);
					})()
					"""), "Opening the dialog did not move focus into its native JCEF DOM");

			assertEquals("true", js(webView, focusTrapScript(false)),
					"Forward Tab did not wrap from the last dialog control to the first control");
			assertEquals("true", js(webView, focusTrapScript(true)),
					"Shift+Tab did not wrap from the first dialog control to the last control");

			String metrics = js(webView, """
					(function() {
					    var selectors = [
					        '[data-testid=frameless-titlebar] button',
					        '[data-testid=nav-rail] button',
					        '[data-testid=create-element-modal] button',
					        '[data-testid=create-element-modal] input'
					    ];
					    var nodes = Array.from(document.querySelectorAll(selectors.join(','))).filter(function(node) {
					        var rect = node.getBoundingClientRect();
					        var style = window.getComputedStyle(node);
					        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
					    });
					    var widths = nodes.map(function(node) { return node.getBoundingClientRect().width; });
					    var heights = nodes.map(function(node) { return node.getBoundingClientRect().height; });
					    var minWidth = widths.length ? Math.min.apply(Math, widths) : 0;
					    var minHeight = heights.length ? Math.min.apply(Math, heights) : 0;
					    return [window.innerWidth, window.innerHeight, window.devicePixelRatio,
					        nodes.length, minWidth, minHeight].join(',');
					})()
					""");
			String[] parts = metrics.split(",");
			assertEquals(6, parts.length, "Native JCEF layout metrics were malformed: " + metrics);
			double viewportWidth = Double.parseDouble(parts[0]);
			double viewportHeight = Double.parseDouble(parts[1]);
			double devicePixelRatio = Double.parseDouble(parts[2]);
			int targetCount = Integer.parseInt(parts[3]);
			double minTargetWidth = Double.parseDouble(parts[4]);
			double minTargetHeight = Double.parseDouble(parts[5]);
			assertTrue(viewportWidth >= 500 && viewportHeight >= 500,
					"Native JCEF did not expose a usable product-shell viewport: " + metrics);
			assertTrue(targetCount >= 8, "Too few visible native-JCEF controls were audited: " + targetCount);
			assertTrue(minTargetWidth >= 32 && minTargetHeight >= 32,
					"A production-shell control is below the 32x32 CSS-pixel target: " + metrics);

			webView.executeScriptAsync("""
					document.dispatchEvent(new KeyboardEvent('keydown', {
					    key: 'Escape', bubbles: true, cancelable: true
					}));
					""");
			await(() -> "true".equals(js(webView,
					"document.querySelector('[data-testid=create-element-modal]') === null")), 10,
					"Escape did not close the non-blocking dialog in native JCEF");
			await(() -> "true".equals(js(webView,
					"document.activeElement?.matches('[data-testid=empty-primary-action]') === true")), 10,
					"Closing the dialog did not restore focus to the native-shell invoker");

			writeEvidence(viewportWidth, viewportHeight, devicePixelRatio, targetCount, minTargetWidth,
					minTargetHeight);
		}
	}

	private static String focusTrapScript(boolean backwards) {
		String activeIndex = backwards ? "0" : "items.length - 1";
		String expectedIndex = backwards ? "items.length - 1" : "0";
		String shift = Boolean.toString(backwards);
		return """
				(function() {
				    var dialog = document.querySelector('[data-testid=create-element-modal] [role=dialog]');
				    if (!dialog) return false;
				    var selector = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), '
				        + 'textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])';
				    var items = Array.from(dialog.querySelectorAll(selector)).filter(function(item) {
				        return item.offsetParent !== null;
				    });
				    if (items.length < 2) return false;
				    items[%s].focus();
				    document.dispatchEvent(new KeyboardEvent('keydown', {
				        key: 'Tab', shiftKey: %s, bubbles: true, cancelable: true
				    }));
				    return document.activeElement === items[%s];
				})()
				""".formatted(activeIndex, shift, expectedIndex);
	}

	private static void writeEvidence(double viewportWidth, double viewportHeight, double devicePixelRatio,
			int targetCount, double minTargetWidth, double minTargetHeight) throws Exception {
		JsonObject evidence = new JsonObject();
		evidence.addProperty("platform", "windows");
		evidence.addProperty("host", "real-jcef-production-shell");
		evidence.addProperty("viewportCssWidth", viewportWidth);
		evidence.addProperty("viewportCssHeight", viewportHeight);
		evidence.addProperty("devicePixelRatio", devicePixelRatio);
		evidence.addProperty("auditedVisibleTargetCount", targetCount);
		evidence.addProperty("minimumTargetWidthCssPx", minTargetWidth);
		evidence.addProperty("minimumTargetHeightCssPx", minTargetHeight);
		evidence.addProperty("dialogFocusPlacement", "passed");
		evidence.addProperty("dialogTabTrap", "passed");
		evidence.addProperty("dialogEscapeAndFocusRestore", "passed");
		evidence.addProperty("politeLiveRegionContract", "passed");
		evidence.addProperty("scope",
				"Real Windows JCEF automation; does not close physical high-DPI or screen-reader audit.");
		evidence.addProperty("generatedAt", Instant.now().toString());
		Path output = Path.of("build", "nightly-results", "stage9-native-jcef-accessibility.json");
		Files.createDirectories(output.getParent());
		Files.writeString(output, JSON.toJson(evidence));
	}

	private static String js(WebView webView, String expression) {
		return webView.executeScript(expression, WebView.JSExecutionType.RETURN_VALUE);
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
		return new WorkspaceState(WORKSPACE_ID, "Native JCEF Accessibility", "mod", 0, false, generator,
				new JsonObject(), List.of());
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean() throws Exception;
	}
}
