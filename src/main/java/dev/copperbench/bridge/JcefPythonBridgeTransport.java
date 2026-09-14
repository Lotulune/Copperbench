package dev.copperbench.bridge;

import com.google.gson.*;
import dev.copperbench.headless.PythonConsoleService;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.ui.dialogs.file.FileDialogs;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.SwingUtilities;
import java.io.Closeable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only the trusted workbench's main frame can execute user-authored Python. */
public final class JcefPythonBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {
    public static final String QUERY_PREFIX = "copperbench:python:";
    private final WebView webView;
    private final PythonConsoleService service;
    private final CefBrowser expectedBrowser;
    private final CefMessageRouter router;
    private final ExecutorService requests = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final WebView.PageLoadListener loadListener = this::install;
    private final Runnable closeListener = this::close;

    private JcefPythonBridgeTransport(WebView webView, PythonConsoleService service) {
        this.webView = webView;
        this.service = service;
        expectedBrowser = webView.getBrowser();
        router = webView.getRouter();
        router.addHandler(this, false);
        webView.addLoadStartListener(loadListener);
        webView.addCloseListener(closeListener);
        install();
    }

    public static JcefPythonBridgeTransport attach(WebView webView, PythonConsoleService service) {
        return new JcefPythonBridgeTransport(webView, service);
    }

    private void install() { if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript()); }

    public static boolean trustedUrl(String url) {
        try {
            URI uri = URI.create(url);
            return "http".equals(uri.getScheme()) && "mcreator".equals(uri.getHost())
                    && uri.getPort() == -1 && uri.getUserInfo() == null
                    && "/copperbench/ui/index.html".equals(uri.getPath());
        } catch (RuntimeException ignored) { return false; }
    }

    @Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
            boolean persistent, CefQueryCallback callback) {
        if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
        if (browser != expectedBrowser || frame == null || !frame.isMain() || !trustedUrl(browser.getURL())) {
            callback.failure(403, "Python execution is available only in the trusted workbench main frame");
            return true;
        }
        if (closed.get()) { callback.failure(503, "Python bridge is closed"); return true; }
        if (request.length() > 2 * 1024 * 1024) { callback.failure(400, "Python request is too large"); return true; }
        try {
            JsonObject payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
            String operation = payload.get("operation").getAsString();
            if (operation.equals("open_script") || operation.equals("save_script") || operation.equals("select_python")) {
                SwingUtilities.invokeLater(() -> chooseFile(operation, payload, callback));
            } else {
                requests.submit(() -> {
                    try {
                        if (closed.get()) throw new IllegalStateException("Python bridge is closed");
                        JsonObject result;
                        switch (operation) {
                            case "get_context" -> result = service.context().snapshot();
                            case "status" -> result = service.status(payload.has("afterSequence")
                                    ? payload.get("afterSequence").getAsLong() : 0);
                            case "sync_context" -> {
                                service.context().updateFromUi(payload.get("elementId").isJsonNull() ? null
                                        : UUID.fromString(payload.get("elementId").getAsString()), payload.get("view").getAsString());
                                result = service.context().snapshot();
                            }
                            case "stop" -> { service.stop(); result = service.status(0); }
                            case "execute", "complete", "start" -> result = service.execute(payload);
                            default -> throw new IllegalArgumentException("Unknown Python workbench operation");
                        }
                        if (!closed.get()) callback.success(result.toString());
                    } catch (Exception exception) {
                        if (!closed.get()) callback.failure(400, exception.getMessage());
                    }
                });
            }
        } catch (RuntimeException exception) { callback.failure(400, exception.getMessage()); }
        return true;
    }

    private void chooseFile(String operation, JsonObject payload, CefQueryCallback callback) {
        if (closed.get()) { callback.failure(503, "Python bridge is closed"); return; }
        try {
            var owner = SwingUtilities.getWindowAncestor(webView.getBrowser().getUIComponent());
            var file = operation.equals("save_script") ? FileDialogs.getSaveDialog(owner, "script.py", new String[]{"py"})
                    : FileDialogs.getOpenDialog(owner, operation.equals("select_python") ? new String[]{} : new String[]{"py"});
            JsonObject result = new JsonObject();
            result.addProperty("cancelled", file == null);
            if (file != null) {
                result.addProperty("path", file.getAbsolutePath());
                if (operation.equals("save_script")) {
                    String source = payload.get("source").getAsString();
                    if (source.length() > 262144) throw new IllegalArgumentException("Script is too large");
                    Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
                } else if (operation.equals("open_script")) {
                    if (Files.size(file.toPath()) > 1048576) throw new IllegalArgumentException("Script exceeds 1 MiB");
                    result.addProperty("source", Files.readString(file.toPath(), StandardCharsets.UTF_8));
                }
            }
            callback.success(result.toString());
        } catch (Exception exception) { callback.failure(400, exception.getMessage()); }
    }

    public static String generateBootstrapScript() {
        return """
                (() => {
                  window.__COPPERBENCH_PYTHON_HOST__ = { invoke: payload => new Promise((resolve, reject) => {
                    window.cefQuery({request: %s + JSON.stringify(payload), persistent: false,
                      onSuccess: result => resolve(JSON.parse(result)),
                      onFailure: (code, message) => reject(new Error(message || String(code)))});
                  })};
                })();
                """.formatted(new Gson().toJson(QUERY_PREFIX));
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        webView.removeLoadStartListener(loadListener);
        webView.removeCloseListener(closeListener);
        requests.shutdownNow();
        try { router.removeHandler(this); } catch (RuntimeException ignored) { }
        // Browser recovery must not discard the persistent interpreter.
    }
}
