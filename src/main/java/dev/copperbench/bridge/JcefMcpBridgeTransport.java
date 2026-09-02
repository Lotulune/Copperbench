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
import dev.copperbench.mcp.DesktopMcpRuntime;
import net.mcreator.ui.chromium.WebView;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.Closeable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exposes desktop MCP runtime state and one-time token reveal to the trusted React shell. */
public final class JcefMcpBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:mcp-runtime:";

	private final WebView webView;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final DesktopMcpRuntime runtime;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefMcpBridgeTransport(WebView webView, DesktopMcpRuntime runtime) {
		this.webView = Objects.requireNonNull(webView);
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.runtime = Objects.requireNonNull(runtime);
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefMcpBridgeTransport attach(WebView webView, DesktopMcpRuntime runtime) {
		return new JcefMcpBridgeTransport(webView, runtime);
	}

	private void installHost() {
		if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript());
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
		if (closed.get()) {
			callback.failure(503, "MCP runtime bridge is closed");
			return true;
		}
		if (browser != expectedBrowser) return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "MCP runtime bridge is only available to the main frame");
			return true;
		}
		try {
			JsonObject payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
			requireOnly(payload, Set.of("operation"));
			String operation = payload.get("operation").getAsString();
			if (operation.equals("get_state")) {
				callback.success(JSON.toJson(runtime.state().toJson()));
				return true;
			}
			if (operation.equals("reveal_token_once")) {
				var token = runtime.revealTokenOnce();
				if (token.isEmpty()) {
					callback.failure(410, "MCP token is unavailable or was already revealed");
					return true;
				}
				JsonObject response = new JsonObject();
				response.addProperty("token", token.get());
				callback.success(JSON.toJson(response));
				return true;
			}
			callback.failure(400, "Unknown MCP runtime operation");
		} catch (RuntimeException exception) {
			callback.failure(400, "Invalid MCP runtime request");
		}
		return true;
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
	}

	public static String generateBootstrapScript() {
		return """
				(function() {
				    window.__COPPERBENCH_MCP_HOST__ = {
				        available: true,
				        getState: function() {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF MCP runtime transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({ operation: 'get_state' }),
				                    persistent: false,
				                    onSuccess: function(response) { resolve(JSON.parse(response)); },
				                    onFailure: function(code, message) {
				                        reject(new Error('MCP runtime state failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        },
				        revealTokenOnce: function() {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF MCP runtime transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({ operation: 'reveal_token_once' }),
				                    persistent: false,
				                    onSuccess: function(response) { resolve(JSON.parse(response)); },
				                    onFailure: function(code, message) {
				                        reject(new Error('MCP token reveal failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        }
				    };
				})();
				""".formatted(JSON.toJson(QUERY_PREFIX), JSON.toJson(QUERY_PREFIX));
	}

	private static void requireOnly(JsonObject payload, Set<String> allowed) {
		if (!payload.has("operation") || !payload.get("operation").isJsonPrimitive())
			throw new IllegalArgumentException("Missing MCP runtime operation");
		if (payload.keySet().stream().anyMatch(key -> !allowed.contains(key)))
			throw new IllegalArgumentException("Unknown MCP runtime property");
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
