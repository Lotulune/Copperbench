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
import net.mcreator.ui.chromium.WebView;
import net.mcreator.util.DesktopUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opens the host application log for a structured diagnostic failure ID. */
public final class JcefDiagnosticsBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:diagnostics:";

	private final WebView webView;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefDiagnosticsBridgeTransport(WebView webView) {
		this.webView = Objects.requireNonNull(webView, "webView must not be null");
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefDiagnosticsBridgeTransport attach(WebView webView) {
		return new JcefDiagnosticsBridgeTransport(webView);
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

		try {
			JsonObject payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
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
				                    request: %s + JSON.stringify({ failureId: failureId }),
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Open logs failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        }
				    };
				})();
				""".formatted(JSON.toJson(QUERY_PREFIX));
	}

	private static String requiredString(JsonObject payload, String name) {
		if (!payload.has(name) || !payload.get(name).isJsonPrimitive())
			throw new IllegalArgumentException("Missing diagnostics property");
		return payload.get(name).getAsString();
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		webView.removeLoadStartListener(loadStartListener);
		webView.removeCloseListener(closeListener);
		try {
			router.removeHandler(this);
		} catch (Exception ignored) {
		}
	}
}
