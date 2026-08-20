package dev.copperbench.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import dev.copperbench.assets.BlockbenchBridgeException;
import dev.copperbench.assets.BlockbenchProcessService;
import net.mcreator.ui.chromium.WebView;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.Closeable;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Narrow JCEF transport for the path-scoped Blockbench process service. */
public final class JcefBlockbenchBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {
	public static final String SCHEMA_VERSION = "1.0";
	public static final String QUERY_PREFIX = "copperbench:blockbench:";
	private static final Gson JSON = new GsonBuilder().serializeNulls().create();

	private final WebView webView;
	private final BlockbenchProcessService service;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JcefBlockbenchBridgeTransport(WebView webView, BlockbenchProcessService service) {
		this.webView = Objects.requireNonNull(webView);
		this.service = Objects.requireNonNull(service);
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefBlockbenchBridgeTransport attach(WebView webView, BlockbenchProcessService service) {
		return new JcefBlockbenchBridgeTransport(webView, service);
	}

	private void installHost() {
		if (!closed.get()) webView.executeScriptAsync(generateBootstrapScript());
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX)) return false;
		if (closed.get()) {
			callback.failure(503, "Blockbench bridge is closed");
			return true;
		}
		if (browser != expectedBrowser) return false;
		if (frame != null && !frame.isMain()) {
			callback.failure(403, "Blockbench bridge is only available to the main frame");
			return true;
		}
		try {
			JsonObject payload = JsonParser.parseString(request.substring(QUERY_PREFIX.length())).getAsJsonObject();
			String operation = payload.has("operation") ? payload.get("operation").getAsString() : "";
			BlockbenchProcessService.Snapshot snapshot = switch (operation) {
				case "status" -> service.status();
				case "open_asset" -> service.openAsset(requiredString(payload, "assetId"));
				default -> throw new IllegalArgumentException("Unsupported Blockbench bridge operation");
			};
			callback.success(toWireJson(snapshot));
		} catch (BlockbenchBridgeException exception) {
			callback.failure(exception.code().equals("BLOCKBENCH_ALREADY_RUNNING") ? 409 : 400,
					exception.code() + ": " + exception.getMessage());
		} catch (RuntimeException exception) {
			callback.failure(400, exception.getMessage());
		}
		return true;
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
	}

	public static String toWireJson(BlockbenchProcessService.Snapshot snapshot) {
		JsonObject result = new JsonObject();
		result.addProperty("schemaVersion", SCHEMA_VERSION);
		result.addProperty("state", snapshot.state().name().toLowerCase(Locale.ROOT));
		add(result, "assetId", snapshot.assetId());
		add(result, "relativePath", snapshot.relativePath());
		if (snapshot.processId() == null) result.add("processId", JsonNull.INSTANCE);
		else result.addProperty("processId", snapshot.processId());
		if (snapshot.exitCode() == null) result.add("exitCode", JsonNull.INSTANCE);
		else result.addProperty("exitCode", snapshot.exitCode());
		add(result, "openedSha256", snapshot.openedSha256());
		add(result, "currentSha256", snapshot.currentSha256());
		add(result, "blockbenchVersion", snapshot.blockbenchVersion());
		add(result, "diagnosticCode", snapshot.diagnosticCode());
		return JSON.toJson(result);
	}

	private static void add(JsonObject target, String name, String value) {
		if (value == null) target.add(name, JsonNull.INSTANCE);
		else target.addProperty(name, value);
	}

	private static String requiredString(JsonObject payload, String name) {
		if (!payload.has(name) || !payload.get(name).isJsonPrimitive())
			throw new IllegalArgumentException("Missing Blockbench bridge property: " + name);
		return payload.get(name).getAsString();
	}

	public static String generateBootstrapScript() {
		return """
				(function() {
				    function invoke(payload) {
				        return new Promise(function(resolve, reject) {
				            if (typeof window.cefQuery !== 'function') {
				                reject(new Error('JCEF Blockbench transport is not available'));
				                return;
				            }
				            window.cefQuery({
				                request: %s + JSON.stringify(payload),
				                persistent: false,
				                onSuccess: function(response) { resolve(JSON.parse(response)); },
				                onFailure: function(code, message) {
				                    reject(new Error('Blockbench bridge failed [' + code + ']: ' + message));
				                }
				            });
				        });
				    }
				    window.__COPPERBENCH_BLOCKBENCH_HOST__ = {
				        schemaVersion: %s,
				        status: function() { return invoke({ operation: 'status' }); },
				        openAsset: function(assetId) { return invoke({ operation: 'open_asset', assetId: assetId }); }
				    };
				})();
				""".formatted(JSON.toJson(QUERY_PREFIX), JSON.toJson(SCHEMA_VERSION));
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		webView.removeLoadStartListener(loadStartListener);
		webView.removeCloseListener(closeListener);
		try { router.removeHandler(this); } catch (Exception ignored) { }
		service.close();
	}
}
