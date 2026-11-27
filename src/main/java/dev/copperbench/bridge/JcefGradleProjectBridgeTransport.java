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
import dev.copperbench.core.workspace.project.GradleProjectSettings;
import dev.copperbench.core.workspace.project.GradleProjectWorkspaceSession;
import net.mcreator.ui.chromium.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The adopted project's desktop choices (ADR-0020 decisions 7 and 8): whether {@code .copperbench/} goes into the
 * project's {@code .gitignore}, and which Gradle user home its builds use. Nothing changes until the user chooses.
 */
public final class JcefGradleProjectBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Logger LOG = LogManager.getLogger(JcefGradleProjectBridgeTransport.class);
	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:gradle-project:";
	private static final int MAX_REQUEST_CHARS = 4096;

	private final WebView webView;
	private final GradleProjectWorkspaceSession session;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefGradleProjectBridgeTransport(WebView webView, GradleProjectWorkspaceSession session) {
		this.webView = Objects.requireNonNull(webView, "webView must not be null");
		this.session = Objects.requireNonNull(session, "session must not be null");
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefGradleProjectBridgeTransport attach(WebView webView, GradleProjectWorkspaceSession session) {
		return new JcefGradleProjectBridgeTransport(webView, session);
	}

	private void installHost() {
		if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript());
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
		if (closed.get()) {
			callback.failure(503, "Gradle project bridge is closed");
			return true;
		}
		if (browser != expectedBrowser || frame == null || !frame.isMain()
				|| !JcefWorkspaceOpenBridgeTransport.trustedUrl(browser.getURL()) || !JcefWorkspaceOpenBridgeTransport.trustedUrl(frame.getURL())) {
			callback.failure(403, "Project settings are only available to the trusted workbench main frame");
			return true;
		}
		if (persistent || request.length() > MAX_REQUEST_CHARS) {
			callback.failure(400, "Invalid project settings request");
			return true;
		}
		Thread.ofVirtual().name("Copperbench-Gradle-Project-Settings").start(() -> {
			try {
				String result = handle(session, JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject()).toString();
				if (!closed.get()) callback.success(result);
			} catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException invalid) {
				if (!closed.get()) callback.failure(400, invalid.getMessage() == null ? "Invalid project settings request" : invalid.getMessage());
			} catch (IOException | RuntimeException failure) {
				LOG.warn("Could not apply the project setting", failure);
				if (!closed.get()) callback.failure(500, failure.getMessage() == null ? "Could not apply the project setting" : failure.getMessage());
			}
		});
		return true;
	}

	/** Applies one request and returns the resulting state, so the page never guesses what was written. */
	static JsonObject handle(GradleProjectWorkspaceSession session, JsonObject payload) throws IOException {
		switch (string(payload, "operation")) {
			case "status" -> requireKeys(payload, Set.of("operation"));
			case "git_ignore" -> {
				requireKeys(payload, Set.of("operation", "add"));
				var add = payload.get("add");
				if (!add.isJsonPrimitive() || !add.getAsJsonPrimitive().isBoolean())
					throw new IllegalArgumentException("add must be true or false");
				session.decideGitIgnore(add.getAsBoolean());
			}
			case "gradle_user_home" -> {
				requireKeys(payload, Set.of("operation", "mode"));
				String mode = string(payload, "mode");
				if (!Set.of("product", "environment").contains(mode)) throw new IllegalArgumentException("Unknown Gradle user home choice");
				session.useEnvironmentGradleHome(mode.equals("environment"));
			}
			default -> throw new IllegalArgumentException("Unknown project settings operation");
		}
		return status(session);
	}

	private static String string(JsonObject payload, String key) {
		var value = payload.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
	}

	static JsonObject status(GradleProjectWorkspaceSession session) throws IOException {
		GradleProjectSettings settings = session.settings();
		JsonObject gitIgnore = new JsonObject();
		gitIgnore.addProperty("repository", GradleProjectSettings.inGitRepository(session.root()));
		gitIgnore.addProperty("ignored", GradleProjectSettings.ignoredByGit(session.root()));
		if (settings.gitIgnore() == null) gitIgnore.add("decision", com.google.gson.JsonNull.INSTANCE);
		else gitIgnore.addProperty("decision", settings.gitIgnore());
		JsonObject gradle = new JsonObject();
		gradle.addProperty("mode", settings.environmentGradleHome() ? "environment" : "product");
		gradle.addProperty("product", net.mcreator.io.UserFolderManager.getGradleHome().toPath().toAbsolutePath().normalize().toString());
		gradle.addProperty("environment", GradleProjectSettings.environmentGradleUserHome().toString());
		JsonObject result = new JsonObject();
		result.addProperty("projectRoot", session.root().toString());
		result.add("gitIgnore", gitIgnore);
		result.add("gradleUserHome", gradle);
		return result;
	}

	private static void requireKeys(JsonObject payload, Set<String> allowed) {
		if (!allowed.containsAll(payload.keySet()) || !payload.keySet().containsAll(allowed))
			throw new IllegalArgumentException("Unexpected project settings properties");
	}

	public static String generateBootstrapScript() {
		return """
				(function() {
				    window.__COPPERBENCH_GRADLE_PROJECT_HOST__ = {
				        available: true,
				        request: function(payload) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') { reject(new Error('JCEF project settings transport is not available')); return; }
				                window.cefQuery({ request: %s + JSON.stringify(payload), persistent: false,
				                    onSuccess: function(result) { try { resolve(JSON.parse(result)); } catch (error) { reject(error); } },
				                    onFailure: function(code, message) { var error = new Error(message); error.code = code; reject(error); } });
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
