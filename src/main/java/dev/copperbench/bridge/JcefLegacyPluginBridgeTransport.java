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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Scoped transport for opening the isolated upstream Swing plugin window. */
public final class JcefLegacyPluginBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Logger LOG = LogManager.getLogger(JcefLegacyPluginBridgeTransport.class);
	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:legacy-plugin:";

	private final WebView webView;
	private final Runnable openAction;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefLegacyPluginBridgeTransport(WebView webView, Runnable openAction) {
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

	public static JcefLegacyPluginBridgeTransport attach(WebView webView, Runnable openAction) {
		return new JcefLegacyPluginBridgeTransport(webView, openAction);
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
			callback.failure(503, "Legacy plugin bridge is closed");
			return true;
		}
		if (browser != expectedBrowser)
			return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "Legacy plugin bridge is only available to the main frame");
			return true;
		}
		if (!"open".equals(request.substring(QUERY_PREFIX.length()))) {
			callback.failure(400, "Unsupported legacy plugin action");
			return true;
		}

		callback.success("{}");
		SwingUtilities.invokeLater(() -> {
			try {
				openAction.run();
			} catch (RuntimeException exception) {
				LOG.error("Failed to perform legacy plugin window action", exception);
			}
		});
		return true;
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
	}

	public static String generateBootstrapScript() {
		return """
				(function() {
				    window.__COPPERBENCH_LEGACY_PLUGIN_HOST__ = {
				        available: true,
				        invoke: function(action) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF legacy plugin transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + action,
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Legacy plugin action failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        }
				    };
				})();
				""".formatted(JSON.toJson(QUERY_PREFIX));
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
