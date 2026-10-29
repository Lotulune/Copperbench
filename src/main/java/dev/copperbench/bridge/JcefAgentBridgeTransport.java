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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.agent.AgentChatService;
import net.mcreator.ui.chromium.WebView;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.Closeable;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exposes the workspace's CLI sessions only to its trusted workbench main frame. */
public final class JcefAgentBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	public static final String QUERY_PREFIX = "copperbench:agent:";
	static final int MAX_REQUEST_CHARS = 2 * 1024 * 1024;
	private static final Set<String> OPERATIONS = Set.of("status", "detect", "new", "select", "send", "stop",
			"approve", "login", "openSetup", "selectRun", "verify", "stopVerification", "refreshRun", "exportRun", "models", "updateSettings");

	private final WebView webView;
	private final AgentChatService service;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final ExecutorService requests = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
	private final AtomicBoolean closed = new AtomicBoolean();
	private final WebView.PageLoadListener loadListener = this::install;
	private final Runnable closeListener = this::close;

	private JcefAgentBridgeTransport(WebView webView, AgentChatService service) {
		this.webView = Objects.requireNonNull(webView);
		this.service = Objects.requireNonNull(service);
		expectedBrowser = webView.getBrowser();
		router = webView.getRouter();
		router.addHandler(this, false);
		webView.addLoadStartListener(loadListener);
		webView.addCloseListener(closeListener);
		install();
	}

	public static JcefAgentBridgeTransport attach(WebView webView, AgentChatService service) {
		return new JcefAgentBridgeTransport(webView, service);
	}

	private void install() {
		if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript());
	}

	public static boolean trustedUrl(String url) {
		try {
			URI uri = URI.create(url);
			return "http".equals(uri.getScheme()) && "mcreator".equals(uri.getHost())
					&& uri.getPort() == -1 && uri.getUserInfo() == null
					&& "/copperbench/ui/index.html".equals(uri.getRawPath());
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
		if (browser != expectedBrowser || frame == null || !frame.isMain()
				|| !trustedUrl(browser.getURL()) || !trustedUrl(frame.getURL())) {
			callback.failure(403, "Agent access is available only in the trusted workbench main frame");
			return true;
		}
		if (closed.get()) {
			callback.failure(503, "Agent bridge is closed");
			return true;
		}
		if (persistent) {
			callback.failure(400, "Agent bridge requests must not be persistent");
			return true;
		}
		JsonObject payload;
		try {
			payload = parseRequest(request);
		} catch (RuntimeException exception) {
			callback.failure(400, "Invalid Agent request");
			return true;
		}
		try {
			requests.submit(() -> {
				if (closed.get()) return;
				try {
					JsonObject result = service.handle(payload);
					if (!closed.get()) callback.success(result.toString());
				} catch (RuntimeException exception) {
					if (!closed.get()) callback.failure(400,
							exception.getMessage() == null ? "Agent request failed" : exception.getMessage());
				}
			});
		} catch (RejectedExecutionException exception) {
			if (!closed.get()) callback.failure(503, "Agent bridge is closed");
		}
		return true;
	}

	static JsonObject parseRequest(String request) {
		if (request == null || !request.startsWith(QUERY_PREFIX) || request.length() > MAX_REQUEST_CHARS)
			throw new IllegalArgumentException("Invalid Agent request size or prefix");
		JsonElement parsed = JsonParser.parseString(request.substring(QUERY_PREFIX.length()));
		if (!parsed.isJsonObject()) throw new IllegalArgumentException("Agent request must be an object");
		JsonObject payload = parsed.getAsJsonObject();
		if (payload.has("operation")) {
			JsonElement operation = payload.get("operation");
			if (!operation.isJsonPrimitive() || !operation.getAsJsonPrimitive().isString()
					|| !OPERATIONS.contains(operation.getAsString()))
				throw new IllegalArgumentException("Unknown Agent operation");
		}
		return payload;
	}

	public static String generateBootstrapScript() {
		return """
				(() => {
				  window.__COPPERBENCH_AGENT_HOST__ = { invoke: payload => new Promise((resolve, reject) => {
				    if (typeof window.cefQuery !== 'function') {
				      reject(new Error('Agent bridge is not available'));
				      return;
				    }
				    window.cefQuery({request: %s + JSON.stringify(payload), persistent: false,
				      onSuccess: result => {
				        try { resolve(JSON.parse(result)); } catch (error) { reject(error); }
				      },
				      onFailure: (code, message) => reject(new Error(message || String(code)))});
				  })};
				})();
				""".formatted(new Gson().toJson(QUERY_PREFIX));
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		webView.removeLoadStartListener(loadListener);
		webView.removeCloseListener(closeListener);
		// A browser reload must neither interrupt accepted requests nor stop CLI sessions.
		requests.shutdown();
		try { router.removeHandler(this); } catch (RuntimeException ignored) { }
	}
}
