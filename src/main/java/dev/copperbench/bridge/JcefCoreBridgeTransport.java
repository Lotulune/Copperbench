/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore;
import net.mcreator.ui.chromium.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Narrowly-scoped JCEF message router transport that connects CEF browser JavaScript queries
 * to {@link JcefBridgeEndpoint}, returning versioned JSON outcomes or failing promises,
 * and dispatching asynchronous domain events safely to the browser.
 */
public final class JcefCoreBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Logger LOG = LogManager.getLogger(JcefCoreBridgeTransport.class);

	public static final String DEFAULT_QUERY_PREFIX = "copperbench:bridge:";

	private final UUID workspaceId;
	private final JcefBridgeEndpoint endpoint;
	private final String queryPrefix;
	private final Consumer<String> scriptEvaluator;
	@Nullable private final CefMessageRouter router;
	@Nullable private final CefBrowser expectedBrowser;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	@Nullable private WebView lifecycleWebView;
	@Nullable private WebView.PageLoadListener loadStartListener;
	@Nullable private Runnable closeListener;
	@Nullable private AutoCloseable eventSubscription;

	public JcefCoreBridgeTransport(UUID workspaceId, JcefBridgeEndpoint endpoint, Consumer<String> scriptEvaluator) {
		this(workspaceId, endpoint, DEFAULT_QUERY_PREFIX, scriptEvaluator, null);
	}

	public JcefCoreBridgeTransport(UUID workspaceId, JcefBridgeEndpoint endpoint, String queryPrefix,
			Consumer<String> scriptEvaluator, @Nullable CefMessageRouter router) {
		this(workspaceId, endpoint, queryPrefix, scriptEvaluator, router, null);
	}

	private JcefCoreBridgeTransport(UUID workspaceId, JcefBridgeEndpoint endpoint, String queryPrefix,
			Consumer<String> scriptEvaluator, @Nullable CefMessageRouter router, @Nullable CefBrowser expectedBrowser) {
		this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
		this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
		this.queryPrefix = Objects.requireNonNull(queryPrefix, "queryPrefix must not be null");
		this.scriptEvaluator = Objects.requireNonNull(scriptEvaluator, "scriptEvaluator must not be null");
		this.router = router;
		this.expectedBrowser = expectedBrowser;
	}

	private static JcefCoreBridgeTransport attach(WebView webView, UUID workspaceId, JcefBridgeEndpoint endpoint,
			String queryPrefix) {
		Objects.requireNonNull(webView, "webView must not be null");
		Objects.requireNonNull(workspaceId, "workspaceId must not be null");
		Objects.requireNonNull(endpoint, "endpoint must not be null");
		Objects.requireNonNull(queryPrefix, "queryPrefix must not be null");

		JcefCoreBridgeTransport transport = new JcefCoreBridgeTransport(workspaceId, endpoint, queryPrefix,
				webView::executeScriptAsync, webView.getRouter(), webView.getBrowser());
		transport.bindLifecycle(webView);
		return transport;
	}

	public static JcefCoreBridgeTransport attach(WebView webView, UUID workspaceId, WorkspaceEntryAdapter adapter) {
		return attach(webView, workspaceId, adapter, DEFAULT_QUERY_PREFIX);
	}

	public static JcefCoreBridgeTransport attach(WebView webView, UUID workspaceId, WorkspaceEntryAdapter adapter,
			String queryPrefix) {
		Objects.requireNonNull(webView, "webView must not be null");
		Objects.requireNonNull(workspaceId, "workspaceId must not be null");
		Objects.requireNonNull(adapter, "adapter must not be null");
		Objects.requireNonNull(queryPrefix, "queryPrefix must not be null");

		AtomicReference<JcefCoreBridgeTransport> transportRef = new AtomicReference<>();
		JcefBridgeEndpoint endpoint = new JcefBridgeEndpoint(adapter, eventJson -> {
			JcefCoreBridgeTransport current = transportRef.get();
			if (current != null)
				current.dispatchEvent(eventJson);
		});
		JcefCoreBridgeTransport transport = attach(webView, workspaceId, endpoint, queryPrefix);
		transportRef.set(transport);
		return transport;
	}

	private void bindLifecycle(WebView webView) {
		this.lifecycleWebView = webView;
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		router.addHandler(this, false);
		webView.addLoadStartListener(loadStartListener);
		webView.addCloseListener(closeListener);
		installHost();
	}

	private void installHost() {
		if (closed.get())
			return;
		scriptEvaluator.accept(generateBootstrapScript(workspaceId, queryPrefix));
		if (eventSubscription != null) {
			try {
				eventSubscription.close();
			} catch (Exception ignored) {
			}
		}
		eventSubscription = endpoint.subscribeEvents(workspaceId, 0, this::dispatchEvent);
	}

	public void dispatchEvent(String eventJson) {
		if (closed.get()) {
			return;
		}
		String safePayload = UiCore.wireGson().toJson(Objects.requireNonNull(eventJson, "eventJson must not be null"));
		String script = "(function() { var raw = " + safePayload +
				"; if (window.__COPPERBENCH_EMIT_EVENT__) { window.__COPPERBENCH_EMIT_EVENT__(raw); }" +
				" else { window.dispatchEvent(new CustomEvent('copperbench:event', { detail: raw })); } })();";
		try {
			scriptEvaluator.accept(script);
		} catch (Exception e) {
			LOG.warn("Failed to dispatch UI-Core bridge event to browser: {}", e.getMessage(), e);
		}
	}

	@Override
	public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent,
			CefQueryCallback callback) {
		if (request == null || !request.startsWith(queryPrefix)) {
			return false;
		}
		if (closed.get()) {
			callback.failure(503, "UI-Core bridge transport is closed");
			return true;
		}
		if (expectedBrowser != null && browser != expectedBrowser)
			return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "UI-Core bridge is only available to the main frame");
			return true;
		}

		String payload = request.substring(queryPrefix.length());
		try {
			requireWorkspaceScope(payload);
			String resultJson = endpoint.handle(payload);
			callback.success(resultJson != null ? resultJson : "{}");
		} catch (IllegalArgumentException | IllegalStateException | JsonParseException e) {
			LOG.warn("UI-Core bridge query failed: {}", e.getMessage(), e);
			callback.failure(400, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		} catch (RuntimeException e) {
			LOG.error("Unexpected UI-Core bridge failure", e);
			callback.failure(500, "UI-Core bridge request failed");
		}
		return true;
	}

	private void requireWorkspaceScope(String payload) {
		JsonObject envelope = JsonParser.parseString(payload).getAsJsonObject();
		if (!envelope.has("messageType") || !envelope.get("messageType").isJsonPrimitive())
			return;
		String messageType = envelope.get("messageType").getAsString();
		if (!"command".equals(messageType) && !"query".equals(messageType))
			return;
		if (!envelope.has("workspaceId") || !envelope.get("workspaceId").isJsonPrimitive())
			throw new IllegalArgumentException("Missing bridge property: workspaceId");
		if (!workspaceId.toString().equals(envelope.get("workspaceId").getAsString()))
			throw new IllegalArgumentException("Bridge request targets a different workspace");
	}

	@Override
	public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
		// Queries in UI-Core bridge are transactional; no background cancellation is required.
	}

	public static String generateBootstrapScript(UUID workspaceId, String queryPrefix) {
		String workspaceJson = UiCore.wireGson().toJson(workspaceId.toString());
		String prefixJson = UiCore.wireGson().toJson(queryPrefix);
		return """
				(function() {
				    window.__COPPERBENCH_WORKSPACE_ID__ = %s;
				    window.__COPPERBENCH_QUERY_PREFIX__ = %s;
				    var listeners = new Set();
				    window.__COPPERBENCH_EMIT_EVENT__ = function(eventJson) {
				        listeners.forEach(function(l) {
				            try {
				                l(eventJson);
				            } catch (e) {
				                console.error('[Copperbench Bridge] Event error:', e);
				            }
				        });
				        try {
				            window.dispatchEvent(new CustomEvent('copperbench:event', { detail: eventJson }));
				        } catch (e) {}
				    };
				    window.copperbenchHost = {
				        workspaceId: %s,
				        invoke: function(envelopeJson) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF native query transport (window.cefQuery) is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + envelopeJson,
				                    persistent: false,
				                    onSuccess: function(response) {
				                        resolve(response);
				                    },
				                    onFailure: function(code, msg) {
				                        reject(new Error('Native bridge query failed [' + code + ']: ' + msg));
				                    }
				                });
				            });
				        },
				        onEvent: function(listener) {
				            listeners.add(listener);
				            return function() {
				                listeners.delete(listener);
				            };
				        }
				    };
				    window.__COPPERBENCH_HOST__ = window.copperbenchHost;
				})();
				""".formatted(workspaceJson, prefixJson, workspaceJson, prefixJson);
	}

	public UUID workspaceId() {
		return workspaceId;
	}

	public JcefBridgeEndpoint endpoint() {
		return endpoint;
	}

	public String queryPrefix() {
		return queryPrefix;
	}

	public boolean isClosed() {
		return closed.get();
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			if (eventSubscription != null) {
				try {
					eventSubscription.close();
				} catch (Exception ignored) {
				}
				eventSubscription = null;
			}
			if (lifecycleWebView != null) {
				if (loadStartListener != null)
					lifecycleWebView.removeLoadStartListener(loadStartListener);
				if (closeListener != null)
					lifecycleWebView.removeCloseListener(closeListener);
			}
			if (router != null) {
				try {
					router.removeHandler(this);
				} catch (Exception ignored) {
				}
			}
		}
	}
}
