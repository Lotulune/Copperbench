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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.procedure.ProcedureIr;
import dev.copperbench.procedure.ProcedureIr.Node;
import dev.copperbench.procedure.ProcedureIrCodec;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixed-hardware Windows JCEF performance gate for the Stage 9 creator paths. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "copperbench.stage9.scale", matches = "true")
class Stage9NativeJcefScaleGateTest {

	private static final int ELEMENT_COUNT = 2_000;
	private static final int REFERENCES_PER_ELEMENT = 5;
	private static final int REFERENCE_COUNT = ELEMENT_COUNT * REFERENCES_PER_ELEMENT;
	private static final int PROCEDURE_NODE_COUNT = 500;
	private static final int SAMPLE_COUNT = 20;
	private static final double P95_TARGET_MILLIS = 300.0;
	private static final double LONG_ACTION_CEILING_MILLIS = 10_000.0;
	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111119500");
	private static final UUID PROCEDURE_ID = UUID.fromString("22222222-2222-4222-8222-222222229500");
	private static final UUID FILTER_ALPHA_ID = UUID.fromString("33333333-3333-4333-8333-333333339501");
	private static final UUID FILTER_BETA_ID = UUID.fromString("33333333-3333-4333-8333-333333339502");
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String WORKSPACE_NAME = "Stage 9 Native Scale Gate";
	// Verify the native transport and the exact fixture projection rendered by React,
	// independently of localized status copy or a merely mounted application shell.
	private static final String NATIVE_WORKSPACE_READY = """
			typeof window.cefQuery === 'function'
			    && window.copperbenchHost?.workspaceId === %s
			    && typeof window.copperbenchHost.invoke === 'function'
			    && document.querySelector('[data-testid=workbench-main] h1')?.textContent?.trim() === %s
			    && document.querySelector('[data-testid=workbench-loading]') === null
			""".formatted(JSON.toJson(WORKSPACE_ID.toString()), JSON.toJson(WORKSPACE_NAME));

	// Use the existing native locale event without persisting a user preference. This fixture
	// asserts Chinese refresh/save/back copy; runner navigator.language may be en-US.
	private static final String FIXTURE_LOCALE_SETUP = """
			window.dispatchEvent(new CustomEvent('copperbench:locale-changed', { detail: 'zh' }))
			""";
	private static final String REFERENCE_REFRESH_BUTTON = """
			Array.from(document.querySelectorAll('[data-testid=creator-data-view] button'))
			    .find(candidate => candidate.textContent?.includes('刷新'))
			""";

	@BeforeAll static void initializeJcefPrerequisites() throws Exception {
		LoggingSystem.init();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		if (Launcher.version == null) {
			Properties configuration = new Properties();
			configuration.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
			Launcher.version = new MCreatorVersionNumber(configuration);
		}
		if (PreferencesManager.PREFERENCES == null) PreferencesManager.init();
		if (PluginLoader.INSTANCE == null) PluginLoader.initInstance();
		if (Theme.current() == null) ThemeManager.loadThemes();
		L10N.initTranslations();
	}

	@Test void fixedHardwareProductShellMeetsStage9ScaleTargets() throws Exception {
		Stage9ScaleEvidence evidence = new Stage9ScaleEvidence(
				Path.of("build", "nightly-results", "stage9-native-jcef-scale.json"));
		evidence.data.addProperty("elements", ELEMENT_COUNT);
		evidence.data.addProperty("references", REFERENCE_COUNT);
		evidence.data.addProperty("procedureNodes", PROCEDURE_NODE_COUNT);
		evidence.data.addProperty("sampleCount", SAMPLE_COUNT);
		evidence.data.addProperty("p95TargetMillis", P95_TARGET_MILLIS);
		evidence.data.addProperty("longActionCeilingMillis", LONG_ACTION_CEILING_MILLIS);
		evidence.data.addProperty("scope", "Real Windows JCEF: element filter, reference refresh, Procedure open/search/edit/save/reopen");
		List<Double> elementFilterSamples = evidence.samples("elementFilterSamplesMillis");
		List<Double> referenceRefreshSamples = evidence.samples("referenceRefreshSamplesMillis");
		List<Double> procedureSearchSamples = evidence.samples("procedureSearchSamplesMillis");
		evidence.capture(() -> {
			evidence.phase("setup");
			RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
			store.register(scaleWorkspace());
			Clock clock = Clock.systemUTC();
			WorkspaceApplicationService service = new WorkspaceApplicationService(store,
					new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
			WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service,
					new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
			JFrame[] windowRef = new JFrame[1];

			try (WebView webView = new WebView(CopperbenchProductShell.UI_URL);
					JcefCoreBridgeTransport ignored = webView.attachCoreBridge(WORKSPACE_ID, adapter)) {
				SwingUtilities.invokeAndWait(() -> {
					JFrame window = new JFrame("Copperbench native scale gate");
					window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
					window.setContentPane(webView);
					window.setSize(1440, 900);
					window.setLocationRelativeTo(null);
					window.setVisible(true);
					windowRef[0] = window;
				});

				CountDownLatch loaded = new CountDownLatch(1);
				webView.addLoadListener(loaded::countDown);
				webView.forceLoad();
				assertTrue(loaded.await(30, TimeUnit.SECONDS), "Production React shell did not finish loading");
				await(() -> "true".equals(js(webView,
						NATIVE_WORKSPACE_READY)), 30,
						"Production shell did not bind the native JCEF UI-Core host");

				js(webView, FIXTURE_LOCALE_SETUP);
				awaitWithDiagnostics(webView, () -> "zh-CN".equals(js(webView, "document.documentElement.lang")),
						10, "Scale fixture did not apply its Chinese UI locale");

				// A fresh profile now defaults to Simple mode. Do not depend on a preceding
				// native accessibility test to leave this shared CEF profile in Professional mode.
				clickTestId(webView, "mode-professional");
				await(() -> "true".equals(js(webView,
						"document.querySelector('[data-testid=mode-professional]')?.getAttribute('aria-pressed') === 'true'")),
						10, "Professional workbench did not become visible in native JCEF");
				evidence.data.add("browser", com.google.gson.JsonParser.parseString(js(webView, """
						JSON.stringify({ userAgent: navigator.userAgent, viewportCssWidth: innerWidth,
						    viewportCssHeight: innerHeight, devicePixelRatio, visibilityState: document.visibilityState,
						    hasFocus: document.hasFocus(), mode: document.querySelector('[data-testid=app-shell]')?.dataset.workbenchMode })
						""")));
				for (String key : List.of("viewportCssWidth", "viewportCssHeight", "devicePixelRatio"))
					evidence.data.add(key, evidence.data.getAsJsonObject("browser").get(key));
				clickTestId(webView, "nav-elements");
				await(() -> "true".equals(js(webView,
						"document.querySelector('[data-testid=elements-pagination]')?.textContent.includes('2000') === true")),
						30, "The real JCEF element list did not load all 2,000 elements");

				// Warm the React filter path once before collecting the fixed-hardware samples.
				evidence.phase("element-filter-warmup");
				evidence.data.addProperty("elementFilterWarmupMillis", measureElementFilter(webView, "scale_filter_alpha_unique", FILTER_ALPHA_ID));
				evidence.phase("element-filter-samples");
				for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
					evidence.sample(sample);
					boolean alpha = sample % 2 == 0;
					elementFilterSamples.add(measureElementFilter(webView,
							alpha ? "scale_filter_beta_unique" : "scale_filter_alpha_unique",
							alpha ? FILTER_BETA_ID : FILTER_ALPHA_ID));
				}
				double elementFilterP95 = p95(elementFilterSamples);
				evidence.data.addProperty("elementFilterP95Millis", elementFilterP95);
				assertTrue(elementFilterP95 < P95_TARGET_MILLIS,
						"2,000-element real-JCEF filter P95 exceeded 300 ms: " + elementFilterP95);

				evidence.phase("reference-view-setup");
				clickTestId(webView, "nav-data");
				await(() -> "10000".equals(js(webView,
						"document.querySelector('[data-testid=tab-references] .badge')?.textContent.trim() ?? ''")), 30,
						"The real JCEF reference view did not load 10,000 edges");
				evidence.phase("reference-refresh-warmup");
				evidence.data.addProperty("referenceRefreshWarmupMillis", measureReferenceRefresh(webView));
				evidence.phase("reference-refresh-samples");
				for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
					evidence.sample(sample);
					referenceRefreshSamples.add(measureReferenceRefresh(webView));
				}
				double referenceRefreshP95 = p95(referenceRefreshSamples);
				evidence.data.addProperty("referenceRefreshP95Millis", referenceRefreshP95);
				assertTrue(referenceRefreshP95 < P95_TARGET_MILLIS,
						"10,000-reference real-JCEF query/refresh P95 exceeded 300 ms: " + referenceRefreshP95);

				evidence.phase("procedure-setup");
				clickTestId(webView, "nav-elements");
				measureElementFilter(webView, "scale_procedure_gate", PROCEDURE_ID);
				evidence.phase("procedure-open");
				double procedureOpenMillis = measureProcedureOpen(webView, PROCEDURE_NODE_COUNT);
				evidence.data.addProperty("procedureOpenMillis", procedureOpenMillis);
				assertTrue(procedureOpenMillis < LONG_ACTION_CEILING_MILLIS,
						"500-node Procedure open exceeded the 10 s usability ceiling: " + procedureOpenMillis);

				evidence.phase("procedure-search-warmup");
				evidence.data.addProperty("procedureSearchWarmupMillis", measureProcedureSearch(webView, "math_number"));
				evidence.phase("procedure-search-samples");
				for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
					evidence.sample(sample);
					procedureSearchSamples.add(measureProcedureSearch(webView, sample % 2 == 0 ? "text" : "math"));
				}
				double procedureSearchP95 = p95(procedureSearchSamples);
				evidence.data.addProperty("procedureSearchP95Millis", procedureSearchP95);
				assertTrue(procedureSearchP95 < P95_TARGET_MILLIS,
						"500-node Procedure palette-search P95 exceeded 300 ms: " + procedureSearchP95);

				evidence.phase("procedure-edit-setup");
				measureProcedureSearch(webView, "math_number");
				evidence.phase("procedure-edit");
				double procedureEditMillis = measureProcedureAddBlock(webView, PROCEDURE_NODE_COUNT + 1);
				evidence.data.addProperty("procedureEditMillis", procedureEditMillis);
				assertTrue(procedureEditMillis < P95_TARGET_MILLIS,
						"500-node Procedure edit interaction exceeded 300 ms: " + procedureEditMillis);

				evidence.phase("procedure-save");
				double procedureSaveMillis = measureProcedureSave(webView);
				evidence.data.addProperty("procedureSaveMillis", procedureSaveMillis);
				assertTrue(procedureSaveMillis < LONG_ACTION_CEILING_MILLIS,
						"500-node Procedure save exceeded the 10 s usability ceiling: " + procedureSaveMillis);

				evidence.phase("procedure-reopen-setup");
				clickAriaLabel(webView, "返回元素列表");
				await(() -> "true".equals(js(webView,
						"document.querySelector('[data-element-id=\"" + PROCEDURE_ID + "\"]') !== null")), 10,
						"Procedure list did not return after save");
				evidence.phase("procedure-reopen");
				double procedureReopenMillis = measureProcedureOpen(webView, PROCEDURE_NODE_COUNT + 1);
				evidence.data.addProperty("procedureReopenMillis", procedureReopenMillis);
				assertTrue(procedureReopenMillis < LONG_ACTION_CEILING_MILLIS,
						"501-node Procedure reopen exceeded the 10 s usability ceiling: " + procedureReopenMillis);

			} finally {
				if (windowRef[0] != null)
					SwingUtilities.invokeAndWait(windowRef[0]::dispose);
			}
		});
	}

	private static double measureElementFilter(WebView webView, String query, UUID expectedId) throws Exception {
		assertEquals("true", js(webView, """
				(function(query, expectedId) {
				    var input = document.querySelector('[data-testid=elements-search-input]');
				    var root = document.querySelector('[data-testid=elements-workbench]');
				    if (!input || !root) return false;
				    window.__cbScaleElementFilter = null;
				    var finished = false;
				    var started = performance.now();
				    var observer = new MutationObserver(function() {
				        if (finished || !root.querySelector('[data-element-id="' + expectedId + '"]')) return;
				        finished = true;
				        requestAnimationFrame(function() { requestAnimationFrame(function() {
				            window.__cbScaleElementFilter = performance.now() - started;
				            observer.disconnect();
				        }); });
				    });
				    observer.observe(root, { childList: true, subtree: true, characterData: true });
				    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
				    setter.call(input, query);
				    input.dispatchEvent(new Event('input', { bubbles: true }));
				    return true;
				})(%s, %s)
				""".formatted(JSON.toJson(query), JSON.toJson(expectedId.toString()))));
		return awaitMetric(webView, "__cbScaleElementFilter", 10);
	}

	private static double measureReferenceRefresh(WebView webView) throws Exception {
		awaitButton(webView, REFERENCE_REFRESH_BUTTON, "Reference refresh");
		assertEquals("true", js(webView, """
				(function() {
				    var root = document.querySelector('[data-testid=creator-data-view]');
				    if (!root) return false;
				    var button = %s;
				    if (!button || button.disabled) return false;
				    window.__cbScaleReferenceRefresh = null;
				    var sawDisabled = false;
				    var finished = false;
				    var started = performance.now();
				    var observer = new MutationObserver(function() {
				        if (button.disabled) sawDisabled = true;
				        var badge = document.querySelector('[data-testid=tab-references] .badge');
				        if (finished || !sawDisabled || button.disabled || !badge || badge.textContent.trim() !== '10000') return;
				        finished = true;
				        requestAnimationFrame(function() { requestAnimationFrame(function() {
				            window.__cbScaleReferenceRefresh = performance.now() - started;
				            observer.disconnect();
				        }); });
				    });
				    observer.observe(button, { attributes: true, attributeFilter: ['disabled'] });
				    button.click();
				    return true;
				})()
				""".formatted(REFERENCE_REFRESH_BUTTON)), () -> "Reference refresh setup failed: " + diagnostics(webView));
		return awaitMetric(webView, "__cbScaleReferenceRefresh", 15);
	}

	private static double measureProcedureOpen(WebView webView, int expectedNodes) throws Exception {
		assertEquals("true", js(webView, """
				(function(expectedId, expectedNodes) {
				    var card = document.querySelector('[data-element-id="' + expectedId + '"]');
				    var root = document.querySelector('[data-testid=app-shell]');
				    if (!card || !root) return false;
				    window.__cbScaleProcedureOpen = null;
				    var finished = false;
				    var started = performance.now();
				    var observer = new MutationObserver(function() {
				        if (finished || !document.querySelector('[data-testid=procedure-workbench]')) return;
				        if (document.querySelectorAll('.blocklyDraggable').length < expectedNodes) return;
				        finished = true;
				        requestAnimationFrame(function() { requestAnimationFrame(function() {
				            window.__cbScaleProcedureOpen = performance.now() - started;
				            observer.disconnect();
				        }); });
				    });
				    observer.observe(root, { childList: true, subtree: true, attributes: true });
				    card.click();
				    return true;
				})(%s, %d)
				""".formatted(JSON.toJson(PROCEDURE_ID.toString()), expectedNodes)));
		return awaitMetric(webView, "__cbScaleProcedureOpen", 30);
	}

	private static double measureProcedureSearch(WebView webView, String query) throws Exception {
		assertEquals("true", js(webView, """
				(function(query) {
				    var input = document.querySelector('[data-testid=procedure-palette-search]');
				    var root = document.querySelector('.procedure-node-list');
				    if (!input || !root) return false;
				    window.__cbScaleProcedureSearch = null;
				    var finished = false;
				    var started = performance.now();
				    var observer = new MutationObserver(function() {
				        if (finished || root.querySelectorAll('button').length === 0) return;
				        finished = true;
				        requestAnimationFrame(function() { requestAnimationFrame(function() {
				            window.__cbScaleProcedureSearch = performance.now() - started;
				            observer.disconnect();
				        }); });
				    });
				    observer.observe(root, { childList: true, subtree: true, characterData: true });
				    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
				    setter.call(input, query);
				    input.dispatchEvent(new Event('input', { bubbles: true }));
				    return true;
				})(%s)
				""".formatted(JSON.toJson(query))), "Procedure palette search input or node list is missing");
		return awaitMetric(webView, "__cbScaleProcedureSearch", 10);
	}

	private static double measureProcedureAddBlock(WebView webView, int expectedNodes) throws Exception {
		assertEquals("true", js(webView, """
				(function(expectedNodes) {
				    var button = document.querySelector('.procedure-node-list button:not([disabled])');
				    var root = document.querySelector('[data-testid=procedure-workbench]');
				    if (!button || !root) return false;
				    window.__cbScaleProcedureEdit = null;
				    var finished = false;
				    var started = performance.now();
				    var observer = new MutationObserver(function() {
				        var save = document.querySelector('button.procedure-save');
				        if (finished || !save || save.disabled || document.querySelectorAll('.blocklyDraggable').length < expectedNodes) return;
				        finished = true;
				        requestAnimationFrame(function() { requestAnimationFrame(function() {
				            window.__cbScaleProcedureEdit = performance.now() - started;
				            observer.disconnect();
				        }); });
				    });
				    observer.observe(root, { childList: true, subtree: true, attributes: true });
				    button.click();
				    return true;
				})(%d)
				""".formatted(expectedNodes)));
		return awaitMetric(webView, "__cbScaleProcedureEdit", 10);
	}

	private static double measureProcedureSave(WebView webView) throws Exception {
		awaitButton(webView, "document.querySelector('button.procedure-save')", "Procedure save");
		assertEquals("true", js(webView, """
				(function() {
				    var save = document.querySelector('button.procedure-save');
				    var root = document.querySelector('[data-testid=procedure-workbench]');
				    if (!save || save.disabled || !root) return false;
				    window.__cbScaleProcedureSave = null;
				    var finished = false;
				    var started = performance.now();
				    var observer = new MutationObserver(function() {
				        var status = document.querySelector('.procedure-message');
				        if (finished || !status || !status.textContent.includes('已保存')) return;
				        finished = true;
				        requestAnimationFrame(function() { requestAnimationFrame(function() {
				            window.__cbScaleProcedureSave = performance.now() - started;
				            observer.disconnect();
				        }); });
				    });
				    observer.observe(root, { childList: true, subtree: true, characterData: true });
				    save.click();
				    return true;
				})()
				"""), () -> "Procedure save setup failed: " + diagnostics(webView));
		return awaitMetric(webView, "__cbScaleProcedureSave", 20);
	}

	private static void clickTestId(WebView webView, String testId) {
		assertEquals("true", js(webView, """
				(function() {
				    var button = document.querySelector('[data-testid=%s]');
				    if (!button) return false;
				    button.click();
				    return true;
				})()
				""".formatted(testId)));
	}

	private static void clickAriaLabel(WebView webView, String label) throws Exception {
		awaitButton(webView, "Array.from(document.querySelectorAll('button')).find(button => button.getAttribute('aria-label') === "
				+ JSON.toJson(label) + ")", "Procedure back");
		assertEquals("true", js(webView, """
				(function(label) {
				    var button = Array.from(document.querySelectorAll('button')).find(function(candidate) {
				        return candidate.getAttribute('aria-label') === label;
				    });
				    if (!button) return false;
				    button.click();
				    return true;
				})(%s)
				""".formatted(JSON.toJson(label))));
	}

	private static double awaitMetric(WebView webView, String property, int timeoutSeconds) throws Exception {
		awaitWithDiagnostics(webView, () -> !js(webView, "String(window." + property + " ?? '')").isBlank(), timeoutSeconds,
				"Timed out waiting for native JCEF performance metric " + property);
		return Double.parseDouble(js(webView, "String(window." + property + ")"));
	}

	private static void awaitButton(WebView webView, String expression, String action) throws Exception {
		// Readiness is outside the timed action; disabled/loading time is not a sample.
		awaitWithDiagnostics(webView, () -> "true".equals(js(webView, """
				(function() {
				    var button = %s;
				    return !!button && !button.disabled && button.getClientRects().length > 0;
				})()
				""".formatted(expression))), 10, action + " button did not become available");
	}

	private static void awaitWithDiagnostics(WebView webView, BooleanSupplier condition, int timeoutSeconds,
			String failureMessage) throws Exception {
		try {
			await(condition, timeoutSeconds, failureMessage);
		} catch (AssertionError failure) {
			throw new AssertionError(failureMessage + ": " + diagnostics(webView), failure);
		}
	}

	private static String diagnostics(WebView webView) {
		try {
			return js(webView, """
					JSON.stringify({
					    systemLanguage: navigator.language, uiLanguage: document.documentElement.lang,
					    dataView: !!document.querySelector('[data-testid=creator-data-view]'),
					    procedureView: !!document.querySelector('[data-testid=procedure-workbench]'),
					    referenceBadge: document.querySelector('[data-testid=tab-references] .badge')?.textContent,
					    messages: Array.from(document.querySelectorAll('.procedure-message')).map(node => node.textContent),
					    buttons: Array.from(document.querySelectorAll(
					        '[data-testid=creator-data-view] button, [data-testid=procedure-workbench] button'))
					        .map(button => ({ text: button.textContent, label: button.getAttribute('aria-label'),
					            disabled: button.disabled, visible: button.getClientRects().length > 0 }))
					})
					""");
		} catch (RuntimeException failure) {
			return "DOM diagnostics unavailable: " + failure;
		}
	}

	private static double p95(List<Double> samples) {
		List<Double> sorted = new ArrayList<>(samples);
		Collections.sort(sorted);
		return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
	}

	private static WorkspaceState scaleWorkspace() {
		List<UUID> ids = new ArrayList<>(ELEMENT_COUNT);
		ids.add(PROCEDURE_ID);
		ids.add(FILTER_ALPHA_ID);
		ids.add(FILTER_BETA_ID);
		for (int index = ids.size(); index < ELEMENT_COUNT; index++)
			ids.add(UUID.nameUUIDFromBytes(("stage9-native-scale-element-" + index).getBytes(StandardCharsets.UTF_8)));

		List<Element> elements = new ArrayList<>(ELEMENT_COUNT);
		for (int index = 0; index < ELEMENT_COUNT; index++) {
			JsonObject values = referenceValues(ids, index);
			UUID id = ids.get(index);
			String type = index == 0 ? "procedure" : "function";
			String name;
			String displayName;
			if (index == 0) {
				name = "scale_procedure_gate";
				displayName = "Scale Procedure Gate";
				values.add("procedureIr", new ProcedureIrCodec().toJson(scaleProcedure()));
			} else if (index == 1) {
				name = "scale_filter_alpha_unique";
				displayName = "Scale Filter Alpha Unique";
			} else if (index == 2) {
				name = "scale_filter_beta_unique";
				displayName = "Scale Filter Beta Unique";
			} else {
				name = "scale_function_" + index;
				displayName = "Scale Function " + index;
			}
			elements.add(new Element(id, type, name, displayName, "valid", "owned",
					Instant.EPOCH.plusSeconds(index), values));
		}

		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, WORKSPACE_NAME, "mod", 0, false, generator,
				new JsonObject(), elements);
	}

	private static JsonObject referenceValues(List<UUID> ids, int index) {
		JsonArray references = new JsonArray();
		for (int offset = 1; offset <= REFERENCES_PER_ELEMENT; offset++) {
			JsonObject reference = new JsonObject();
			reference.addProperty("target", ids.get((index + offset) % ELEMENT_COUNT).toString());
			references.add(reference);
		}
		JsonObject values = new JsonObject();
		values.add("references", references);
		return values;
	}

	private static ProcedureIr scaleProcedure() {
		List<Node> nodes = new ArrayList<>(PROCEDURE_NODE_COUNT);
		for (int index = 0; index < PROCEDURE_NODE_COUNT; index++) {
			UUID id = UUID.nameUUIDFromBytes(("stage9-native-procedure-node-" + index)
					.getBytes(StandardCharsets.UTF_8));
			JsonObject fields = new JsonObject();
			if (index > 0) fields.addProperty("value", index);
			nodes.add(new Node(id, index == 0 ? "event_trigger" : "math_number",
					index == 0 ? "statement" : "value", (index % 25) * 150, (index / 25) * 72,
					fields, Map.of(), null, false, ""));
		}
		return new ProcedureIr(ProcedureIr.SCHEMA_VERSION, "no_ext_trigger", nodes, List.of(), new JsonObject());
	}


	private static String js(WebView webView, String expression) {
		return webView.executeScript(expression, WebView.JSExecutionType.RETURN_VALUE);
	}

	private static void await(BooleanSupplier condition, int timeoutSeconds, String failureMessage) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
		assertTrue(condition.getAsBoolean(), failureMessage);
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean() throws Exception;
	}
}
