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
import dev.copperbench.shell.UiLocalePreferences;
import dev.copperbench.window.WindowChromeSnapshot;
import dev.copperbench.window.WindowsWindowChromeController.PointerGesture;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.ui.dialogs.preferences.PreferencesDialog;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowStateListener;
import java.io.Closeable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Narrow JCEF transport for product-shell window controls. */
public final class JcefWindowBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	public static final String QUERY_PREFIX = "copperbench:window:";
	public static final String REGION_QUERY_PREFIX = "copperbench:window-regions:";
	public static final String GESTURE_QUERY_PREFIX = "copperbench:window-gesture:";
	private static final Set<String> ACTIONS = Set.of("minimize", "toggle_maximize", "close", "open_preferences", "set_locale_en", "set_locale_zh");
	private static final Gson JSON = new Gson();

	private final WebView webView;
	private final JFrame window;
	private final Runnable closeAction;
	private final Consumer<WindowChromeSnapshot> chromeRegionConsumer;
	private final BooleanSupplier nativeChromeAvailable;
	private final Consumer<PointerGesture> pointerGestureConsumer;
	private final WindowStateListener windowStateListener;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefWindowBridgeTransport(WebView webView, JFrame window, Runnable closeAction,
			Consumer<WindowChromeSnapshot> chromeRegionConsumer, BooleanSupplier nativeChromeAvailable,
			Consumer<PointerGesture> pointerGestureConsumer) {
		this.webView = Objects.requireNonNull(webView, "webView must not be null");
		this.window = Objects.requireNonNull(window, "window must not be null");
		this.closeAction = Objects.requireNonNull(closeAction, "closeAction must not be null");
		this.chromeRegionConsumer = chromeRegionConsumer;
		this.pointerGestureConsumer = pointerGestureConsumer;
		this.windowStateListener = event -> publishWindowState();
		window.addWindowStateListener(windowStateListener);
		this.nativeChromeAvailable = Objects.requireNonNull(nativeChromeAvailable,
				"nativeChromeAvailable must not be null");
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefWindowBridgeTransport attach(WebView webView, JFrame window, Runnable closeAction) {
		return new JcefWindowBridgeTransport(webView, window, closeAction, null, () -> false, null);
	}

	public static JcefWindowBridgeTransport attach(WebView webView, JFrame window, Runnable closeAction,
			Consumer<WindowChromeSnapshot> chromeRegionConsumer, BooleanSupplier nativeChromeAvailable) {
		return new JcefWindowBridgeTransport(webView, window, closeAction, chromeRegionConsumer,
				nativeChromeAvailable, null);
	}

	public static JcefWindowBridgeTransport attach(WebView webView, JFrame window, Runnable closeAction,
			Consumer<WindowChromeSnapshot> chromeRegionConsumer, BooleanSupplier nativeChromeAvailable,
			Consumer<PointerGesture> pointerGestureConsumer) {
		return new JcefWindowBridgeTransport(webView, window, closeAction, chromeRegionConsumer,
				nativeChromeAvailable, pointerGestureConsumer);
	}

	private void installHost() {
		if (!closed.get()) {
			boolean nativeChrome = nativeChromeActive();
			webView.executeScriptAsync(generateLocaleBootstrapScript(UiLocalePreferences.read()) + generateBootstrapScript(!nativeChrome, nativeChrome));
			publishWindowState();
		}
	}

	private void publishWindowState() {
		if (closed.get()) return;
		boolean maximized = (window.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
		webView.executeScriptAsync("if (window.__COPPERBENCH_WINDOW_HOST__) { "
				+ "window.__COPPERBENCH_WINDOW_HOST__.maximized = " + maximized + ";"
				+ "window.dispatchEvent(new CustomEvent('copperbench:window-state', {detail:{maximized:"
				+ maximized + "}})); }");
	}

	private boolean nativeChromeActive() {
		return window.isUndecorated() && chromeRegionConsumer != null && nativeChromeAvailable.getAsBoolean();
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		boolean actionRequest = request != null && request.startsWith(QUERY_PREFIX);
		boolean regionRequest = request != null && request.startsWith(REGION_QUERY_PREFIX);
		boolean gestureRequest = request != null && request.startsWith(GESTURE_QUERY_PREFIX);
		if (!actionRequest && !regionRequest && !gestureRequest)
			return false;
		if (closed.get()) {
			callback.failure(503, "Window bridge is closed");
			return true;
		}
		if (browser != expectedBrowser)
			return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "Window bridge is only available to the main frame");
			return true;
		}

		if (gestureRequest) {
			if (!nativeChromeActive() || pointerGestureConsumer == null) {
				callback.failure(409, "Native window gestures are not active");
				return true;
			}
			try {
				PointerGesture gesture = JSON.fromJson(request.substring(GESTURE_QUERY_PREFIX.length()), PointerGesture.class);
				if (gesture == null) throw new IllegalArgumentException("Missing window pointer position");
				pointerGestureConsumer.accept(gesture);
				callback.success("{}");
			} catch (RuntimeException exception) {
				callback.failure(400, "Invalid window pointer gesture");
			}
			return true;
		}

		if (regionRequest) {
			if (!nativeChromeActive()) {
				callback.failure(409, "Native window chrome is not active");
				return true;
			}
			try {
				chromeRegionConsumer.accept(WindowChromeSnapshot.parse(request.substring(REGION_QUERY_PREFIX.length())));
				callback.success("{}");
			} catch (IllegalArgumentException exception) {
				callback.failure(400, exception.getMessage());
			}
			return true;
		}

		String action = request.substring(QUERY_PREFIX.length());
		if (!ACTIONS.contains(action)) {
			callback.failure(400, "Unsupported window action");
			return true;
		}
		if (action.startsWith("set_locale_")) {
			try { UiLocalePreferences.save(action.substring("set_locale_".length())); callback.success("{}"); }
			catch (java.io.IOException | SecurityException exception) { callback.failure(500, "Could not save the UI language preference"); }
			return true;
		}
		callback.success("{}");
		SwingUtilities.invokeLater(() -> perform(action));
		return true;
	}

	private void perform(String action) {
		switch (action) {
			case "minimize" -> window.setState(Frame.ICONIFIED);
			case "toggle_maximize" -> {
				int state = window.getExtendedState();
				window.setExtendedState((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
						? state & ~Frame.MAXIMIZED_BOTH : state | Frame.MAXIMIZED_BOTH);
			}
			case "close" -> closeAction.run();
			case "open_preferences" -> new PreferencesDialog(window);
			default -> throw new IllegalArgumentException("Unsupported window action: " + action);
		}
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
	}

	public static String generateLocaleBootstrapScript(String locale) {
		return "window.__COPPERBENCH_UI_LOCALE__ = " + JSON.toJson("en".equals(locale) ? "en" : "zh") + ";"
				+ "window.__COPPERBENCH_SET_LOCALE__ = function(locale) { return new Promise(function(resolve, reject) {"
				+ "if (locale !== 'en' && locale !== 'zh') { reject(new Error('Unsupported locale')); return; }"
				+ "window.cefQuery({request:'copperbench:window:set_locale_' + locale,persistent:false,"
				+ "onSuccess:resolve,onFailure:function(code,message){reject(new Error(message));}}); }); };";
	}

	public static String generateBootstrapScript(boolean systemFrame) {
		return generateBootstrapScript(systemFrame, false);
	}

	public static String generateBootstrapScript(boolean systemFrame, boolean chromeRegions) {
		String chromeProperties = chromeRegions ? """
				        chromeRegionSchemaVersion: %s,
				        pointerGesture: function(gesture) {
				            window.cefQuery({ request: %s + JSON.stringify(gesture), persistent: false,
				                onSuccess: function() {}, onFailure: function(code, message) {
				                    console.warn('Native window gesture failed [' + code + ']: ' + message);
				                } });
				        },
				        reportChromeRegions: function(snapshot) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF window transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify(snapshot),
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Native chrome-region report failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        },
				""".formatted(JSON.toJson(WindowChromeSnapshot.SCHEMA_VERSION), JSON.toJson(GESTURE_QUERY_PREFIX),
				JSON.toJson(REGION_QUERY_PREFIX)) : "";
		return """
				(function() {
				    window.__COPPERBENCH_WINDOW_HOST__ = {
				        systemFrame: %s,
				        preferencesAvailable: true,
				%s
				        invoke: function(action) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF window transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + action,
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Native window action failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        }
				    };
				})();
				""".formatted(systemFrame, chromeProperties, JSON.toJson(QUERY_PREFIX));
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		webView.removeLoadStartListener(loadStartListener);
		window.removeWindowStateListener(windowStateListener);
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
