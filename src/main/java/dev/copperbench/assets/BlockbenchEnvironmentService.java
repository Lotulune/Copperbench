package dev.copperbench.assets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpHttpClientTransportAuthorizationErrorHandler;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.ConnectException;
import java.net.URI;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Read-only, opt-in MCP discovery. It never launches an editor or invokes a modeling tool. */
public final class BlockbenchEnvironmentService {

	public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:3000/bb-mcp";
	private static final Gson JSON = new Gson();
	private static final Semaphore PROBES = new Semaphore(2);
	private final Supplier<BlockbenchInstallationDetector.Installation> installation;
	private final Duration timeout;

	public BlockbenchEnvironmentService() {
		this(() -> new BlockbenchInstallationDetector().detect(BlockbenchExecutableLocator.locate()), Duration.ofSeconds(3));
	}

	BlockbenchEnvironmentService(Supplier<BlockbenchInstallationDetector.Installation> installation, Duration timeout) {
		this.installation = installation;
		this.timeout = timeout;
	}

	public JsonObject inspect(JsonObject payload) {
		if (!Set.of("probeMcp", "endpoint").containsAll(payload.keySet()))
			throw new IllegalArgumentException("Unsupported Blockbench environment property");
		boolean probe = false;
		if (payload.has("probeMcp")) {
			if (!payload.get("probeMcp").isJsonPrimitive() || !payload.getAsJsonPrimitive("probeMcp").isBoolean())
				throw new IllegalArgumentException("probeMcp must be a boolean");
			probe = payload.get("probeMcp").getAsBoolean();
		}
		String address = DEFAULT_ENDPOINT;
		if (payload.has("endpoint")) {
			if (!payload.get("endpoint").isJsonPrimitive() || !payload.getAsJsonPrimitive("endpoint").isString())
				throw new IllegalArgumentException("endpoint must be a local HTTP URL");
			address = payload.get("endpoint").getAsString();
		}
		URI endpoint = localEndpoint(address);
		var detected = installation.get();
		JsonObject result = new JsonObject();
		result.addProperty("checkedAt", Instant.now().toString());
		JsonObject editor = new JsonObject();
		editor.addProperty("state", detected.state().name().toLowerCase(Locale.ROOT));
		editor.addProperty("version", detected.version());
		editor.addProperty("diagnosticCode", detected.diagnosticCode());
		// No absolute installation/workspace paths are sent to external clients.
		editor.addProperty("available", detected.state() == BlockbenchInstallationDetector.State.READY
				|| detected.state() == BlockbenchInstallationDetector.State.READY_UNVERIFIED);
		result.add("editor", editor);
		if (!probe) result.add("mcp", report(endpoint, "not_checked", null));
		else if (!PROBES.tryAcquire()) result.add("mcp", report(endpoint, "busy", "BLOCKBENCH_MCP_BUSY"));
		else {
			try { result.add("mcp", probe(endpoint)); }
			finally { PROBES.release(); }
		}
		result.addProperty("managedModelingTasksAvailable", false);
		result.addProperty("workflow", "external_agent_two_servers");
		result.addProperty("onboardingDismissed", BlockbenchConfiguration.productDefault().onboardingDismissed());
		return result;
	}

	/** Pin localhost to loopback and disable redirects/proxies in the client as well. */
	static URI localEndpoint(String address) {
		try {
			if (address == null || address.length() > 512) throw new IllegalArgumentException();
			URI uri = URI.create(address);
			String host = uri.getHost();
			if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null
					|| !Set.of("localhost", "127.0.0.1", "[::1]").contains(host.toLowerCase(Locale.ROOT))
					|| uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
					|| uri.getPort() < 1 || uri.getPort() > 65535 || uri.getRawPath() == null
					|| !uri.getRawPath().startsWith("/") || uri.getRawPath().contains("%"))
				throw new IllegalArgumentException();
			return new URI("http", null, host.equalsIgnoreCase("localhost") ? "127.0.0.1" : host,
					uri.getPort(), uri.getPath(), null, null);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Use an HTTP loopback endpoint with an explicit port and path, such as "
					+ DEFAULT_ENDPOINT);
		}
	}

	private JsonObject probe(URI endpoint) {
		AtomicBoolean authenticationRequired = new AtomicBoolean();
		var transport = HttpClientStreamableHttpTransport.builder(endpoint.resolve("/").toString())
				.endpoint(endpoint.getRawPath()).openConnectionOnStartup(false).resumableStreams(false)
				.connectTimeout(timeout)
				.clientBuilder(HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
						.proxy(ProxySelector.of(null)))
				.authorizationErrorHandler(McpHttpClientTransportAuthorizationErrorHandler.fromSync((request, response, context) -> {
					if (response.statusCode() == 401 || response.statusCode() == 403) authenticationRequired.set(true);
					return false;
				}))
				.build();
		try (var client = McpClient.sync(transport).initializationTimeout(timeout).requestTimeout(timeout)
				.clientInfo(new McpSchema.Implementation("copperbench-blockbench-discovery", "1.0"))
				.capabilities(McpSchema.ClientCapabilities.builder().build()).build()) {
			var initialized = client.initialize();
			if (initialized.capabilities() == null || initialized.capabilities().tools() == null)
				return report(endpoint, "no_tools", "BLOCKBENCH_MCP_NO_TOOLS");
			var listed = client.listTools();
			var tools = listed.tools();
			JsonObject result = report(endpoint, tools.isEmpty() ? "no_tools" : "tools_available",
					tools.isEmpty() ? "BLOCKBENCH_MCP_NO_TOOLS" : null);
			result.addProperty("protocolVersion", initialized.protocolVersion());
			result.addProperty("toolCount", tools.size());
			result.addProperty("hasMoreTools", listed.nextCursor() != null);
			// Server-provided descriptions/instructions are intentionally not relayed.
			List<String> names = tools.stream().map(McpSchema.Tool::name)
					.filter(name -> name != null && name.matches("[A-Za-z0-9_.:/-]{1,128}"))
					.limit(10).toList();
			result.add("sampleToolNames", JSON.toJsonTree(names));
			return result;
		} catch (RuntimeException exception) {
			String state = "protocol_error";
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConnectException) { state = "unreachable"; break; }
				if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) state = "timeout";
			}
			if (authenticationRequired.get()) state = "authentication_required";
			return report(endpoint, state, "BLOCKBENCH_MCP_" + state.toUpperCase(Locale.ROOT));
		}
	}

	private static JsonObject report(URI endpoint, String state, String diagnostic) {
		JsonObject result = new JsonObject();
		result.addProperty("endpoint", endpoint.toString());
		result.addProperty("state", state);
		result.addProperty("diagnosticCode", diagnostic);
		result.addProperty("checkedAt", state.equals("not_checked") ? null : Instant.now().toString());
		return result;
	}
}
