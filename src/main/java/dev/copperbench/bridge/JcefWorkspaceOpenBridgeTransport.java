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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.*;
import java.io.Closeable;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Scoped transport for opening a newly created workspace in a new host window.
 * The browser side only supplies the workspace file path produced by the
 * create_workspace command; the host owns the actual open flow, so the UI never
 * re-implements workspace loading or domain validation.
 */
public final class JcefWorkspaceOpenBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Logger LOG = LogManager.getLogger(JcefWorkspaceOpenBridgeTransport.class);
	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:workspace-open:";

	private final WebView webView;
	private final Consumer<File> openAction;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefWorkspaceOpenBridgeTransport(WebView webView, Consumer<File> openAction) {
		this.webView = Objects.requireNonNull(webView, "webView must not be null");
		this.openAction = Objects.requireNonNull(openAction, "openAction must not be null");
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefWorkspaceOpenBridgeTransport attach(WebView webView, Consumer<File> openAction) {
		return new JcefWorkspaceOpenBridgeTransport(webView, openAction);
	}

	private void installHost() {
		if (!closed.get())
			webView.executeScriptAsync(generateBootstrapScript());
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX))
			return false;
		if (closed.get()) {
			callback.failure(503, "Workspace open bridge is closed");
			return true;
		}
		if (browser != expectedBrowser)
			return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "Workspace open bridge is only available to the main frame");
			return true;
		}

		File workspaceFile;
		try {
			JsonObject payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
			String workspaceFilePath = requiredString(payload, "workspaceFile");
			workspaceFile = new File(workspaceFilePath);
		} catch (RuntimeException exception) {
			callback.failure(400, "Invalid workspace open request: " + exception.getMessage());
			return true;
		}
		if (!workspaceFile.isFile() || !workspaceFile.getName().endsWith(".mcreator")) {
			callback.failure(404, "Workspace file does not exist: " + workspaceFile.getName());
			return true;
		}

		callback.success("{\"status\":\"accepted\"}");
		SwingUtilities.invokeLater(() -> {
			try {
				openAction.accept(workspaceFile);
			} catch (RuntimeException exception) {
				LOG.error("Failed to open newly created workspace {}", workspaceFile, exception);
			}
		});
		return true;
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
	}

	public static String generateBootstrapScript() {
		return """
				(function() {
				    window.__COPPERBENCH_WORKSPACE_OPEN_HOST__ = {
				        available: true,
				        open: function(workspaceFile) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF workspace open transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({ workspaceFile: workspaceFile }),
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Workspace open failed [' + code + ']: ' + message));
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
			throw new IllegalArgumentException("Missing workspace open property: " + name);
		return payload.get(name).getAsString();
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		webView.removeLoadStartListener(loadStartListener);
		webView.removeCloseListener(closeListener);
		try {
			router.removeHandler(this);
		} catch (Exception ignored) {
		}
	}

	public boolean isClosed() {
		return closed.get();
	}
}
