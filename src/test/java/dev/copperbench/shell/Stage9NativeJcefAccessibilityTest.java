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
		JFrame[] windowRef = new JFrame[1];

		try (WebView webView = new WebView(CopperbenchProductShell.UI_URL);
				JcefCoreBridgeTransport ignored = webView.attachCoreBridge(WORKSPACE_ID, adapter)) {
			SwingUtilities.invokeAndWait(() -> {
				JFrame window = new JFrame("Copperbench native accessibility");
				window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				window.setContentPane(webView);
				window.setSize(1280, 800);
				window.setLocationRelativeTo(null);
				window.setVisible(true);
				windowRef[0] = window;
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

			webView.executeScriptAsync("document.querySelector('[data-testid=titlebar-build-btn]')?.click()");
			await(() -> "true".equals(js(webView,
					"document.querySelector('[data-testid=task-log-stream]') !== null")), 10,
					"Build command did not expose the task log in native JCEF");
			assertEquals("true", js(webView, """
					(function() {
					    var log = document.querySelector('[data-testid=task-log-stream]');
					    return log?.getAttribute('role') === 'log'
					        && log.getAttribute('aria-live') === 'polite'
					        && getComputedStyle(log).userSelect === 'text';
					})()
					"""), "Task logs must be announced and selectable in native JCEF");
			webView.executeScriptAsync("document.querySelector('[data-testid=task-drawer-close]')?.click()");
			await(() -> "true".equals(js(webView,
					"document.querySelector('[data-testid=task-log-stream]') === null")), 10,
					"Task log drawer did not close before the Procedure audit");

			webView.executeScriptAsync("""
					(function() {
					    document.querySelector('[data-testid=empty-primary-action]')?.click();
					})()
					""");
			await(() -> "true".equals(js(webView,
					"document.querySelector('[data-testid=create-element-modal]') !== null")), 10,
					"Create-element dialog did not reopen for the Procedure audit");
			webView.executeScriptAsync("""
					(function() {
					    var modal = document.querySelector('[data-testid=create-element-modal]');
					    var typeButton = Array.from(modal?.querySelectorAll('button') || [])
					        .find(function(button) { return button.textContent?.trim() === '过程'; });
					    typeButton?.click();
					    var input = modal?.querySelector('[data-testid=create-element-name-input]');
					    if (input) {
					        var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
					        setter?.call(input, 'native_accessible_flow');
					        input.dispatchEvent(new Event('input', { bubbles: true }));
					    }
					    modal?.querySelector('[data-testid=create-element-submit-btn]')?.click();
					})()
					""");
			await(() -> "true".equals(js(webView,
					"document.querySelector('[data-testid=procedure-workbench]') !== null")), 20,
					"Procedure workbench did not open through the real JCEF/Core path");
			webView.executeScriptAsync("""
					(function() {
					    var sourceTab = document.querySelector('#procedure-tab-source');
					    sourceTab?.focus();
					    sourceTab?.dispatchEvent(new KeyboardEvent('keydown', {
					        key: 'End', bubbles: true, cancelable: true
					    }));
					})()
					""");
			await(() -> "true".equals(js(webView, """
					(function() {
					    var tab = document.querySelector('#procedure-tab-outline');
					    return tab?.getAttribute('aria-selected') === 'true' && document.activeElement === tab;
					})()
					""")), 10, "Procedure tabs did not support End-key navigation in native JCEF");

			assertEquals("true", js(webView, """
					(function() {
					    var canvas = document.querySelector('.procedure-canvas');
					    var outline = document.querySelector('[data-testid=procedure-node-outline]');
					    var nodes = Array.from(outline?.querySelectorAll('button') || []);
					    return canvas?.getAttribute('aria-label') === 'Procedure 可视化画布'
					        && outline?.getAttribute('aria-label') === 'Procedure 节点与端口'
					        && nodes.length >= 1
					        && nodes.every(function(node) {
					            var name = node.getAttribute('aria-label') || '';
					            return name.includes('入口触发器') && name.includes('event_trigger') && name.includes('下一个');
					        });
					})()
					"""), "Procedure nodes and ports were not readable through native JCEF semantics");

			String procedureMetrics = js(webView, """
					(function() {
					    var root = document.querySelector('[data-testid=procedure-workbench]');
					    var controls = Array.from(root?.querySelectorAll('button, input') || []).filter(function(control) {
					        var rect = control.getBoundingClientRect();
					        var style = getComputedStyle(control);
					        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
					    });
					    var widths = controls.map(function(control) { return control.getBoundingClientRect().width; });
					    var heights = controls.map(function(control) { return control.getBoundingClientRect().height; });
					    var named = controls.every(function(control) {
					        return Boolean((control.getAttribute('aria-label') || control.textContent || control.getAttribute('title')
					            || control.getAttribute('placeholder') || '').trim());
					    });
					    return [controls.length, Math.min.apply(Math, widths), Math.min.apply(Math, heights), named].join(',');
					})()
					""");
			String[] procedureParts = procedureMetrics.split(",");
			assertEquals(4, procedureParts.length, "Native JCEF Procedure metrics were malformed: " + procedureMetrics);
			int procedureTargetCount = Integer.parseInt(procedureParts[0]);
			double procedureMinTargetWidth = Double.parseDouble(procedureParts[1]);
			double procedureMinTargetHeight = Double.parseDouble(procedureParts[2]);
			assertTrue(procedureTargetCount >= 10, "Too few Procedure controls were audited: " + procedureMetrics);
			assertTrue(procedureMinTargetWidth >= 32 && procedureMinTargetHeight >= 32,
					"A Procedure control is below the 32x32 CSS-pixel target: " + procedureMetrics);
			assertEquals("true", procedureParts[3], "A visible Procedure control has no accessible name");

			writeEvidence(viewportWidth, viewportHeight, devicePixelRatio, targetCount, minTargetWidth,
					minTargetHeight, procedureTargetCount, procedureMinTargetWidth, procedureMinTargetHeight);
		} finally {
			if (windowRef[0] != null)
				SwingUtilities.invokeAndWait(windowRef[0]::dispose);
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
			int targetCount, double minTargetWidth, double minTargetHeight, int procedureTargetCount,
			double procedureMinTargetWidth, double procedureMinTargetHeight) throws Exception {
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
		evidence.addProperty("rendererAccessibilityMode", "complete");
		evidence.addProperty("selectableTaskLogContract", "passed");
		evidence.addProperty("procedureKeyboardTabs", "passed");
		evidence.addProperty("procedureNodeAndPortSemantics", "passed");
		evidence.addProperty("procedureAccessibleNames", "passed");
		evidence.addProperty("procedureAuditedVisibleTargetCount", procedureTargetCount);
		evidence.addProperty("procedureMinimumTargetWidthCssPx", procedureMinTargetWidth);
		evidence.addProperty("procedureMinimumTargetHeightCssPx", procedureMinTargetHeight);
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
