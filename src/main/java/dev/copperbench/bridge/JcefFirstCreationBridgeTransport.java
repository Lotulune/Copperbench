/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.shell.FirstCreationPreferences;
import net.mcreator.ui.chromium.WebView;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** A fixed-workspace bridge for first-creation references. It cannot execute product operations. */
public final class JcefFirstCreationBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {
    public static final String QUERY_PREFIX = "copperbench:first-creation:";
    private static final Gson JSON = new Gson();
    private final WebView webView;
    private final Path workspaceFile;
    private final UUID workspaceId;
    private final Function<List<UUID>, JsonObject> observeInputs;
    private final CefBrowser expectedBrowser;
    private final CefMessageRouter router;
    private final WebView.PageLoadListener loadStartListener;
    private final Runnable closeListener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService reads = Executors.newVirtualThreadPerTaskExecutor();

    private JcefFirstCreationBridgeTransport(WebView webView, Path workspaceFile, UUID workspaceId,
            Function<List<UUID>, JsonObject> observeInputs) {
        this.webView = webView;
        this.workspaceFile = workspaceFile.toAbsolutePath().normalize();
        this.workspaceId = workspaceId;
        this.observeInputs = observeInputs;
        expectedBrowser = webView.getBrowser(); router = webView.getRouter();
        loadStartListener = this::installHost; closeListener = this::close;
        router.addHandler(this, false); webView.addLoadStartListener(loadStartListener); webView.addCloseListener(closeListener);
        installHost();
    }

    public static JcefFirstCreationBridgeTransport attach(WebView webView, Path workspaceFile, UUID workspaceId,
            Function<List<UUID>, JsonObject> observeInputs) {
        return new JcefFirstCreationBridgeTransport(webView, workspaceFile, workspaceId, observeInputs);
    }

    private void installHost() {
        if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript(workspaceFile, workspaceId));
    }

    @Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
            boolean persistent, CefQueryCallback callback) {
        if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
        if (browser != expectedBrowser) return false;
        if (closed.get() || frame != null && !frame.isMain()) { callback.failure(403, "First-creation bridge is unavailable"); return true; }
        if (request.length() > FirstCreationPreferences.MAX_BYTES || request.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > FirstCreationPreferences.MAX_BYTES) {
            callback.failure(413, "First-creation references exceed 64 KiB"); return true;
        }
        try {
            JsonObject payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
            String operation = payload.get("operation").getAsString();
            if ("read".equals(operation) && payload.size() == 1) {
                var binding = FirstCreationPreferences.read(workspaceFile);
                callback.success(binding.isPresent() ? binding.get().toString() : "null");
            } else if ("save".equals(operation) && payload.size() == 2 && payload.has("binding")) {
                JsonObject binding = payload.getAsJsonObject("binding");
                if (!binding.has("workspaceId") || !workspaceId.toString().equals(binding.get("workspaceId").getAsString()))
                    throw new IllegalArgumentException("The references belong to another workspace session");
                FirstCreationPreferences.save(workspaceFile, binding);
                callback.success("{\"status\":\"saved\"}");
            } else if ("observeInputs".equals(operation) && payload.size() == 2 && payload.has("taskIds")) {
                var requested = payload.getAsJsonArray("taskIds");
                if (requested.size() > 8) throw new IllegalArgumentException("Select at most eight task references");
                List<UUID> ids = new ArrayList<>();
                for (var raw : requested) {
                    String value = raw.getAsString(); UUID id = UUID.fromString(value);
                    if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Select standard task UUIDs");
                    ids.add(id);
                }
                // The callback is bound to this session. No caller-supplied workspace or path is accepted.
                List<UUID> selected = List.copyOf(ids);
                reads.submit(() -> {
                    try {
                        JsonObject observation = observeInputs.apply(selected);
                        if (!closed.get()) callback.success(observation.toString());
                    } catch (RuntimeException failure) {
                        if (!closed.get()) callback.failure(400, "Task inputs could not be observed: " + failure.getMessage());
                    }
                });
            } else throw new IllegalArgumentException("Unknown first-creation host operation");
        } catch (Exception exception) {
            callback.failure(400, "First-creation references could not be read or saved: " + exception.getMessage());
        }
        return true;
    }

    public static String generateBootstrapScript(Path workspaceFile, UUID workspaceId) {
        return """
                (function() {
                    function invoke(payload) {
                        return new Promise(function(resolve, reject) {
                            window.cefQuery({ request: %s + JSON.stringify(payload), persistent: false,
                                onSuccess: function(raw) { try { resolve(JSON.parse(raw)); } catch (error) { reject(error); } },
                                onFailure: function(code, message) { reject(new Error(message)); } });
                        });
                    }
                    window.__COPPERBENCH_FIRST_CREATION_HOST__ = {
                        workspaceFile: %s, workspaceId: %s,
                        read: function() { return invoke({ operation: 'read' }); },
                        save: function(binding) { return invoke({ operation: 'save', binding: binding }); },
                        observeInputs: function(taskIds) { return invoke({ operation: 'observeInputs', taskIds: taskIds }); }
                    };
                })();
                """.formatted(JSON.toJson(QUERY_PREFIX), JSON.toJson(workspaceFile.toAbsolutePath().normalize().toString()), JSON.toJson(workspaceId.toString()));
    }

    @Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {}
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            reads.shutdownNow();
            webView.removeLoadStartListener(loadStartListener); webView.removeCloseListener(closeListener); router.removeHandler(this);
        }
    }
}
