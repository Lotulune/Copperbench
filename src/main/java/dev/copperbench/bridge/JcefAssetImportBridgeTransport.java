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
import dev.copperbench.core.application.WorkspaceApplicationService;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.ui.dialogs.file.FileDialogs;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.SwingUtilities;
import java.awt.Window;
import java.io.Closeable;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native file-selection boundary for grant-scoped Asset Center imports. */
public final class JcefAssetImportBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:asset-import:";
	private static final String[] EXTENSIONS = { ".png", ".jpg", ".jpeg", ".json", ".bbmodel", ".ogg",
			".wav", ".mcmeta", ".zip", ".lang" };

	private final WebView webView;
	private final Window owner;
	private final WorkspaceApplicationService service;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefAssetImportBridgeTransport(WebView webView, Window owner, WorkspaceApplicationService service) {
		this.webView = Objects.requireNonNull(webView, "webView");
		this.owner = Objects.requireNonNull(owner, "owner");
		this.service = Objects.requireNonNull(service, "service");
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefAssetImportBridgeTransport attach(WebView webView, Window owner,
			WorkspaceApplicationService service) {
		return new JcefAssetImportBridgeTransport(webView, owner, service);
	}

	private void installHost() {
		if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript());
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
		if (closed.get()) {
			callback.failure(503, "Asset import bridge is closed");
			return true;
		}
		if (browser != expectedBrowser) return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "Asset import bridge is only available to the main frame");
			return true;
		}
		try {
			JsonObject payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
			if (!payload.has("operation") || !payload.get("operation").isJsonPrimitive()
					|| !payload.get("operation").getAsString().equals("selectSource"))
				throw new IllegalArgumentException("Unsupported asset import bridge operation");
		} catch (RuntimeException exception) {
			callback.failure(400, "Invalid asset import request: " + exception.getMessage());
			return true;
		}

		SwingUtilities.invokeLater(() -> {
			if (closed.get()) {
				callback.failure(503, "Asset import bridge is closed");
				return;
			}
			try {
				File source = FileDialogs.getOpenDialog(owner, EXTENSIONS);
				if (source == null) {
					callback.success("{\"cancelled\":true}");
					return;
				}
				WorkspaceApplicationService.AssetImportSelectionGrant grant = service
						.grantAssetImportSource(source.toPath());
				JsonObject response = JSON.toJsonTree(grant).getAsJsonObject();
				response.addProperty("cancelled", false);
				callback.success(JSON.toJson(response));
			} catch (RuntimeException exception) {
				callback.failure(400, "Asset source selection failed: " + exception.getMessage());
			}
		});
		return true;
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
	}

	public static String generateBootstrapScript() {
		return """
				(function() {
				    window.__COPPERBENCH_ASSET_IMPORT_HOST__ = {
				        schemaVersion: '1.0',
				        selectSource: function() {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF asset import transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({ operation: 'selectSource' }),
				                    persistent: false,
				                    onSuccess: function(response) { resolve(JSON.parse(response)); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Asset source selection failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        }
				    };
				})();
				""".formatted(JSON.toJson(QUERY_PREFIX));
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
