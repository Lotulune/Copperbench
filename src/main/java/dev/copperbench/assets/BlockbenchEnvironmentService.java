package dev.copperbench.assets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpHttpClientTransportAuthorizationErrorHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpClientTransport;

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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Read-only, opt-in MCP discovery. It never launches an editor or invokes a modeling tool. */
public final class BlockbenchEnvironmentService {

	public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:3000/bb-mcp";
	private static final Gson JSON = new Gson();
	private static final Semaphore PROBES = new Semaphore(2);
	private final Supplier<BlockbenchInstallationDetector.Installation> installation;
	private final Duration timeout;
	private final Duration totalBudget;
	private final BiFunction<URI, AtomicBoolean, McpClientTransport> transports;
	private final Function<McpClientTransport, DiscoveryClient> clients;

	public BlockbenchEnvironmentService() {
		this(() -> new BlockbenchInstallationDetector().detect(BlockbenchExecutableLocator.locate()), Duration.ofSeconds(3));
	}

	BlockbenchEnvironmentService(Supplier<BlockbenchInstallationDetector.Installation> installation, Duration timeout) {
		this(installation, timeout, Duration.ofSeconds(9), null, null);
	}

	BlockbenchEnvironmentService(Supplier<BlockbenchInstallationDetector.Installation> installation, Duration timeout,
			Duration totalBudget, BiFunction<URI, AtomicBoolean, McpClientTransport> transports,
			Function<McpClientTransport, DiscoveryClient> clients) {
		this.installation = installation;
		this.timeout = timeout;
		this.totalBudget = totalBudget;
		this.transports = transports == null ? this::createTransport : transports;
		this.clients = clients == null ? this::createClient : clients;
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
		// Bound all potentially blocking local I/O as well as transport construction and teardown.
		// Published snapshots are immutable: a cancelled worker must not mutate its returned response.
		AtomicReference<JsonObject> progress = new AtomicReference<>(baseResult());
		AtomicBoolean cancelled = new AtomicBoolean();
		if (!PROBES.tryAcquire()) return interruptedResult(progress.get(), endpoint, probe, "busy");
		boolean requested = probe;
		FutureTask<JsonObject> discovery = new FutureTask<>(() -> inspectWithinBudget(endpoint, requested, progress, cancelled)) {
			@Override public void run() {
				try { super.run(); }
				finally { PROBES.release(); }
			}
		};
		Thread.ofVirtual().name("blockbench-discovery").start(discovery);
		try { return discovery.get(totalBudget.toNanos(), TimeUnit.NANOSECONDS); }
		catch (TimeoutException exception) {
			cancelled.set(true);
			discovery.cancel(true);
			return interruptedResult(progress.get(), endpoint, probe, "timeout");
		} catch (InterruptedException exception) {
			cancelled.set(true);
			discovery.cancel(true);
			Thread.currentThread().interrupt();
			return interruptedResult(progress.get(), endpoint, probe, "cancelled");
		} catch (ExecutionException exception) {
			logFailure("environment", exception.getCause());
			return interruptedResult(progress.get(), endpoint, probe, "initialization_error");
		}
	}

	private static JsonObject baseResult() {
		JsonObject result = new JsonObject();
		result.addProperty("checkedAt", Instant.now().toString());
		JsonObject editor = new JsonObject();
		editor.addProperty("state", "not_checked");
		editor.addProperty("available", false);
		result.add("editor", editor);
		result.addProperty("managedModelingTasksAvailable", false);
		result.addProperty("workflow", "external_agent_two_servers");
		return result;
	}

	private JsonObject inspectWithinBudget(URI endpoint, boolean probe, AtomicReference<JsonObject> progress, AtomicBoolean cancelled) {
		JsonObject result = progress.get().deepCopy();
		var detected = installation.get();
		JsonObject editor = new JsonObject();
		editor.addProperty("state", detected.state().name().toLowerCase(Locale.ROOT));
		editor.addProperty("version", detected.version());
		editor.addProperty("diagnosticCode", detected.diagnosticCode());
		// No absolute installation/workspace paths are sent to external clients.
		editor.addProperty("available", detected.state() == BlockbenchInstallationDetector.State.READY
				|| detected.state() == BlockbenchInstallationDetector.State.READY_UNVERIFIED);
		result.add("editor", editor);
		progress.set(result.deepCopy());
		result.add("application", dev.copperbench.core.application.ApplicationBuildIdentity.inspect());
		result.addProperty("onboardingDismissed", BlockbenchConfiguration.productDefault().onboardingDismissed());
		progress.set(result.deepCopy());
		// Cancellation during slow detection must never start a late network session.
		if (cancelled.get() || Thread.currentThread().isInterrupted()) return interruptedResult(result, endpoint, probe, "cancelled");
		result.add("mcp", probe ? probe(endpoint, cancelled) : report(endpoint, "not_checked", null));
		result.addProperty("inspectionState", "completed");
		return result;
	}

	private static JsonObject interruptedResult(JsonObject progress, URI endpoint, boolean probe, String state) {
		JsonObject result = progress.deepCopy();
		result.addProperty("inspectionState", state);
		if (result.getAsJsonObject("editor").get("state").getAsString().equals("not_checked")) {
			result.getAsJsonObject("editor").addProperty("state", "unverified");
			result.getAsJsonObject("editor").addProperty("diagnosticCode", "BLOCKBENCH_DETECTION_" + state.toUpperCase(Locale.ROOT));
		}
		result.add("mcp", probe ? report(endpoint, state, "BLOCKBENCH_MCP_" + state.toUpperCase(Locale.ROOT))
				: report(endpoint, "not_checked", null));
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

	private McpClientTransport createTransport(URI endpoint, AtomicBoolean authenticationRequired) {
		return HttpClientStreamableHttpTransport.builder(endpoint.resolve("/").toString())
				.endpoint(endpoint.getRawPath()).openConnectionOnStartup(false).resumableStreams(false)
				.connectTimeout(timeout)
				.clientBuilder(HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
						.proxy(ProxySelector.of(null)))
				.authorizationErrorHandler(McpHttpClientTransportAuthorizationErrorHandler.fromSync((request, response, context) -> {
					if (response.statusCode() == 401 || response.statusCode() == 403) authenticationRequired.set(true);
					return false;
				}))
				.build();
	}

	private DiscoveryClient createClient(McpClientTransport transport) {
		var client = McpClient.sync(transport).initializationTimeout(timeout).requestTimeout(timeout)
				.clientInfo(new McpSchema.Implementation("copperbench-blockbench-discovery", "1.0"))
				.capabilities(McpSchema.ClientCapabilities.builder().build()).build();
		return new DiscoveryClient() {
			public McpSchema.InitializeResult initialize() { return client.initialize(); }
			public McpSchema.ListToolsResult listTools() { return client.listTools(); }
			public void close() { client.close(); }
		};
	}

	interface DiscoveryClient extends AutoCloseable {
		McpSchema.InitializeResult initialize();
		McpSchema.ListToolsResult listTools();
		@Override void close();
	}

	private JsonObject probe(URI endpoint, AtomicBoolean cancelled) {
		AtomicBoolean authenticationRequired = new AtomicBoolean();
		AtomicReference<Throwable> transportFailure = new AtomicReference<>();
		String phase = "transport_construction";
		McpClientTransport transport = null;
		DiscoveryClient client = null;
		JsonObject result;
		try {
			transport = transports.apply(endpoint, authenticationRequired);
			checkCancellation(cancelled);
			phase = "client_construction";
			client = clients.apply(observeTransport(transport, transportFailure));
			checkCancellation(cancelled);
			phase = "initialization";
			var initialized = client.initialize();
			checkCancellation(cancelled);
			if (initialized.capabilities() == null || initialized.capabilities().tools() == null) {
				result = report(endpoint, "no_tools", "BLOCKBENCH_MCP_NO_TOOLS");
			} else {
			phase = "tool_discovery";
			var listed = client.listTools();
			var tools = listed.tools();
			result = report(endpoint, tools.isEmpty() ? "no_tools" : "tools_available",
					tools.isEmpty() ? "BLOCKBENCH_MCP_NO_TOOLS" : null);
			result.addProperty("protocolVersion", initialized.protocolVersion());
			result.addProperty("toolCount", tools.size());
			result.addProperty("hasMoreTools", listed.nextCursor() != null);
			// Server-provided descriptions/instructions are intentionally not relayed.
			List<String> names = tools.stream().map(McpSchema.Tool::name)
					.filter(name -> name != null && name.matches("[A-Za-z0-9_.:/-]{1,128}"))
					.limit(10).toList();
			result.add("sampleToolNames", JSON.toJsonTree(names));
			}
		} catch (RuntimeException | LinkageError exception) {
			logFailure(phase, exception);
			String state = phase.endsWith("construction") ? "initialization_error" : "protocol_error";
			// The SDK can report an initialization timeout after an asynchronous decoder failure.
			// Classify the first observed transport failure instead of hiding it behind that timeout.
			Throwable observed = transportFailure.get();
			if (observed != null) logFailure("transport_response", observed);
			Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
			for (Throwable cause = observed == null ? exception : observed; cause != null && seen.add(cause); cause = cause.getCause()) {
				if (cause instanceof ConnectException) { state = "unreachable"; break; }
				if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) state = "timeout";
				if (cause instanceof java.util.concurrent.CancellationException) state = "cancelled";
			}
			if (authenticationRequired.get()) state = "authentication_required";
			result = report(endpoint, state, "BLOCKBENCH_MCP_" + state.toUpperCase(Locale.ROOT));
			result.addProperty("failurePhase", phase);
		}
		try {
			if (client != null) client.close();
			else if (transport != null) transport.closeGracefully().block(timeout);
		} catch (RuntimeException | LinkageError exception) {
			logFailure("close", exception);
			// Preserve an earlier failure, but never claim success if teardown failed.
			if (result.get("diagnosticCode").isJsonNull()
					|| result.get("state").getAsString().equals("no_tools")) {
				result = report(endpoint, "protocol_error", "BLOCKBENCH_MCP_CLOSE_ERROR");
				result.addProperty("failurePhase", "close");
			} else result.addProperty("cleanupDiagnosticCode", "BLOCKBENCH_MCP_CLOSE_ERROR");
		}
		return result;
	}

	private static void checkCancellation(AtomicBoolean cancelled) {
		if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
	}

	private static McpClientTransport observeTransport(McpClientTransport delegate, AtomicReference<Throwable> failure) {
		return new McpClientTransport() {
			public reactor.core.publisher.Mono<Void> connect(Function<reactor.core.publisher.Mono<McpSchema.JSONRPCMessage>,
					reactor.core.publisher.Mono<McpSchema.JSONRPCMessage>> handler) {
				return delegate.connect(handler).doOnError(error -> failure.compareAndSet(null, error));
			}
			public void setExceptionHandler(java.util.function.Consumer<Throwable> handler) {
				delegate.setExceptionHandler(error -> {
					failure.compareAndSet(null, error);
					handler.accept(error);
				});
			}
			public reactor.core.publisher.Mono<Void> closeGracefully() { return delegate.closeGracefully(); }
			public reactor.core.publisher.Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
				return delegate.sendMessage(message).doOnError(error -> failure.compareAndSet(null, error));
			}
			public <T> T unmarshalFrom(Object data, io.modelcontextprotocol.json.TypeRef<T> type) {
				return delegate.unmarshalFrom(data, type);
			}
			public List<String> protocolVersions() { return delegate.protocolVersions(); }
		};
	}

	private static void logFailure(String phase, Throwable exception) {
		org.apache.logging.log4j.LogManager.getLogger(BlockbenchEnvironmentService.class)
				.debug("Blockbench discovery failed during {}: {}", phase, safeTrace(exception));
	}

	/** Remote errors can contain credentials. Retain internal types/frames/causes, never arbitrary messages. */
	static String safeTrace(Throwable exception) {
		StringBuilder trace = new StringBuilder();
		appendTrace(trace, exception, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
		return trace.toString();
	}

	private static void appendTrace(StringBuilder trace, Throwable exception, Set<Throwable> seen) {
		if (exception == null || !seen.add(exception)) return;
		trace.append(exception.getClass().getName()).append('\n');
		for (StackTraceElement frame : exception.getStackTrace()) trace.append("\tat ").append(frame).append('\n');
		for (Throwable suppressed : exception.getSuppressed()) {
			trace.append("Suppressed: "); appendTrace(trace, suppressed, seen);
		}
		if (exception.getCause() != null) {
			trace.append("Caused by: "); appendTrace(trace, exception.getCause(), seen);
		}
	}

	private static JsonObject report(URI endpoint, String state, String diagnostic) {
		JsonObject result = new JsonObject();
		result.addProperty("endpoint", endpoint.toString());
		result.addProperty("state", state);
		result.addProperty("diagnosticCode", diagnostic);
		result.addProperty("checkedAt", state.equals("not_checked") ? null : Instant.now().toString());
		result.addProperty("nextAction", switch (state) {
			case "not_checked" -> "Request an explicit local MCP probe if needed.";
			case "unreachable" -> "Start the Blockbench MCP plugin and verify its local port and path.";
			case "timeout" -> "Check that Blockbench is responsive, then retry.";
			case "authentication_required" -> "Check the local plugin authentication settings.";
			case "no_tools" -> "Enable the plugin's modeling tools and retry discovery.";
			case "initialization_error" -> "Check the application runtime and SDK build details; inspect the local log.";
			case "busy" -> "Wait for the current discovery request to finish.";
			case "tools_available" -> "Discovery succeeded; no model has been created or imported.";
			case "cancelled" -> "Discovery was interrupted; retry when ready.";
			default -> "Verify that the endpoint serves the expected MCP protocol.";
		});
		return result;
	}
}
