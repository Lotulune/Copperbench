/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.diagnostics.DiagnosticBundleService;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.util.DesktopUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Opens logs and exports bounded, local-only diagnostic bundles. */
public final class JcefDiagnosticsBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Logger LOG = LogManager.getLogger(JcefDiagnosticsBridgeTransport.class);
	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:diagnostics:";

	private final WebView webView;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final DiagnosticBundleService bundleService;
	private final Consumer<File> openBundleAction;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final AtomicBoolean exportInProgress = new AtomicBoolean(false);
	private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Copperbench-Diagnostic-Export");
		thread.setDaemon(true);
		return thread;
	});

	private JcefDiagnosticsBridgeTransport(WebView webView, DiagnosticBundleService bundleService,
			Consumer<File> openBundleAction) {
		this.webView = Objects.requireNonNull(webView, "webView must not be null");
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.bundleService = bundleService;
		this.openBundleAction = openBundleAction;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefDiagnosticsBridgeTransport attach(WebView webView) {
		return new JcefDiagnosticsBridgeTransport(webView, null, null);
	}

	public static JcefDiagnosticsBridgeTransport attach(WebView webView, DiagnosticBundleService bundleService) {
		return new JcefDiagnosticsBridgeTransport(webView, Objects.requireNonNull(bundleService),
				file -> DesktopUtils.openSafe(file, true));
	}

	public static JcefDiagnosticsBridgeTransport attach(WebView webView, DiagnosticBundleService bundleService,
			Consumer<File> openBundleAction) {
		return new JcefDiagnosticsBridgeTransport(webView, Objects.requireNonNull(bundleService),
				Objects.requireNonNull(openBundleAction));
	}

	private void installHost() {
		if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript());
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
		if (closed.get()) {
			callback.failure(503, "Diagnostics bridge is closed");
			return true;
		}
		if (browser != expectedBrowser) return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "Diagnostics bridge is only available to the main frame");
			return true;
		}

		JsonObject payload;
		try {
			payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
			String operation = payload.has("operation") ? requiredString(payload, "operation") : "open_logs";
			if (operation.equals("export_bundle")) {
				requireOnly(payload, Set.of("operation", "includeWorkspaceFiles", "failureId"));
				exportBundle(payload, callback);
				return true;
			}
			if (!operation.equals("open_logs")) throw new IllegalArgumentException("Unknown diagnostics operation");
			requireOnly(payload, Set.of("operation", "failureId"));
			UUID.fromString(requiredString(payload, "failureId"));
			String logDirectory = System.getProperty("log_directory");
			if (logDirectory == null || logDirectory.isBlank()) {
				callback.failure(503, "Application log directory is not configured");
				return true;
			}
			Path logs = Path.of(logDirectory).toAbsolutePath().normalize().resolve("logs");
			File target = logs.resolve("mcreator.log").toFile();
			if (!target.isFile()) target = logs.toFile();
			if (!target.exists()) {
				callback.failure(404, "Application log is not available");
				return true;
			}
			DesktopUtils.openSafe(target, target.isFile());
			callback.success("{\"status\":\"opened\"}");
		} catch (RuntimeException exception) {
			callback.failure(400, "Invalid diagnostics request");
		}
		return true;
	}

	private void exportBundle(JsonObject payload, CefQueryCallback callback) {
		if (bundleService == null) {
			callback.failure(503, "Diagnostic bundle export is not configured");
			return;
		}
		UUID failureId = payload.has("failureId") && !payload.get("failureId").getAsString().isBlank()
				? UUID.fromString(payload.get("failureId").getAsString()) : null;
		boolean includeWorkspaceFiles = payload.has("includeWorkspaceFiles")
				&& payload.get("includeWorkspaceFiles").isJsonPrimitive()
				&& payload.get("includeWorkspaceFiles").getAsBoolean();
		if (!exportInProgress.compareAndSet(false, true)) {
			callback.failure(409, "A diagnostic bundle export is already running");
			return;
		}
		exportExecutor.execute(() -> {
			try {
				DiagnosticBundleService.Result result = bundleService.export(failureId, includeWorkspaceFiles);
				JsonObject response = new JsonObject();
				response.addProperty("status", "exported");
				response.addProperty("path", result.path().toString());
				response.addProperty("fileName", result.path().getFileName().toString());
				response.addProperty("includedWorkspaceFiles", result.includedWorkspaceFiles());
				response.addProperty("reproductionFileCount", result.reproductionFileCount());
				openBundleAction.accept(result.path().toFile());
				if (!closed.get()) callback.success(JSON.toJson(response));
			} catch (Exception exception) {
				LOG.error("Failed to export Copperbench diagnostic bundle", exception);
				if (!closed.get()) callback.failure(500, "Diagnostic bundle export failed");
			} finally {
				exportInProgress.set(false);
			}
		});
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
	}

	public static String generateBootstrapScript() {
		return """
				(function() {
				    window.__COPPERBENCH_DIAGNOSTICS_HOST__ = {
				        available: true,
				        openLogs: function(failureId) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF diagnostics transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({ operation: 'open_logs', failureId: failureId }),
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Open logs failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        },
				        exportBundle: function(includeWorkspaceFiles, failureId) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF diagnostics transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({
				                        operation: 'export_bundle',
				                        includeWorkspaceFiles: includeWorkspaceFiles === true,
				                        failureId: failureId || ''
				                    }),
				                    persistent: false,
				                    onSuccess: function(response) { resolve(JSON.parse(response)); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Export diagnostics failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        }
				    };
				})();
				""".formatted(JSON.toJson(QUERY_PREFIX), JSON.toJson(QUERY_PREFIX));
	}

	private static String requiredString(JsonObject payload, String name) {
		if (!payload.has(name) || !payload.get(name).isJsonPrimitive())
			throw new IllegalArgumentException("Missing diagnostics property");
		return payload.get(name).getAsString();
	}

	private static void requireOnly(JsonObject payload, Set<String> allowed) {
		if (payload.keySet().stream().anyMatch(key -> !allowed.contains(key)))
			throw new IllegalArgumentException("Unknown diagnostics property");
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		exportExecutor.shutdownNow();
		webView.removeLoadStartListener(loadStartListener);
		webView.removeCloseListener(closeListener);
		try {
			router.removeHandler(this);
		} catch (Exception ignored) {
		}
	}
}
