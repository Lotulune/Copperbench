package dev.copperbench.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchEnvironmentServiceTest {

	private BlockbenchEnvironmentService service() {
		return new BlockbenchEnvironmentService(() -> new BlockbenchInstallationDetector.Installation(
				BlockbenchInstallationDetector.State.UNAVAILABLE, null, null, "BLOCKBENCH_NOT_CONFIGURED"), Duration.ofSeconds(3));
	}

	@Test void defaultInspectionDoesNotContactServerAndReportsNoManagedWorkflow() throws Exception {
		try (Fixture fixture = new Fixture("valid")) {
			JsonObject payload = new JsonObject();
			payload.addProperty("endpoint", fixture.endpoint());
			JsonObject result = service().inspect(payload);
			assertEquals("not_checked", result.getAsJsonObject("mcp").get("state").getAsString());
			assertFalse(result.getAsJsonObject("editor").get("available").getAsBoolean());
			assertFalse(result.get("managedModelingTasksAvailable").getAsBoolean());
			assertTrue(fixture.methods.isEmpty());
		}
	}

	@Test void realHandshakeAndToolsDiscoveryNeverCallModelingToolsOrSendWorkspaceCredentials() throws Exception {
		try (Fixture fixture = new Fixture("valid")) {
			JsonObject result = inspect(fixture);
			assertEquals("tools_available", result.get("state").getAsString(), result::toString);
			assertEquals(1, result.get("toolCount").getAsInt());
			assertEquals(List.of("initialize", "notifications/initialized", "tools/list"), fixture.methods);
			assertFalse(fixture.authorizationSeen);
			assertFalse(fixture.bodies.toString().contains("workspace"));
			assertFalse(result.toString().contains("untrusted-server-instructions"));
			assertEquals("create_cube", result.getAsJsonArray("sampleToolNames").get(0).getAsString());
		}
	}

	@Test void supportsStreamableHttpSseResponses() throws Exception {
		try (Fixture fixture = new Fixture("sse")) {
			assertEquals("tools_available", inspect(fixture).get("state").getAsString());
		}
	}

	@Test void reachableHttpServiceIsNotEnoughToReportMcpReady() throws Exception {
		for (String mode : List.of("html", "bad_protocol", "http_error", "empty", "redirect", "denied", "forbidden", "slow")) {
			try (Fixture fixture = new Fixture(mode)) {
				JsonObject result = inspect(fixture);
				assertNotEquals("tools_available", result.get("state").getAsString(), mode + result);
				assertTrue(result.has("diagnosticCode"));
				if (mode.equals("denied") || mode.equals("forbidden")) assertEquals("authentication_required", result.get("state").getAsString());
				if (mode.equals("empty")) assertEquals("no_tools", result.get("state").getAsString());
				if (mode.equals("slow")) assertEquals("timeout", result.get("state").getAsString());
				if (List.of("html", "bad_protocol", "http_error", "redirect").contains(mode))
					assertEquals("protocol_error", result.get("state").getAsString(), mode + result);
				assertFalse(result.toString().contains("private-error-details"));
				assertFalse(fixture.methods.contains("tools/call"));
			}
		}
	}

	@Test void reportsAClosedPortWithoutClaimingTheEditorIsMissing() throws Exception {
		String endpoint;
		try (Fixture fixture = new Fixture("valid")) { endpoint = fixture.endpoint(); }
		JsonObject payload = new JsonObject();
		payload.addProperty("probeMcp", true);
		payload.addProperty("endpoint", endpoint);
		var installed = new BlockbenchEnvironmentService(() -> new BlockbenchInstallationDetector.Installation(
				BlockbenchInstallationDetector.State.READY, java.nio.file.Path.of("private-install-path"), "5.1.6", null), Duration.ofMillis(700));
		JsonObject result = installed.inspect(payload);
		assertTrue(result.getAsJsonObject("editor").get("available").getAsBoolean());
		assertEquals("unreachable", result.getAsJsonObject("mcp").get("state").getAsString(), result::toString);
		assertFalse(result.toString().contains("private-install-path"));
	}

	@Test void rejectsNonLoopbackAndCredentialUrlsBeforeAnyProbe() {
		for (String endpoint : List.of("http://example.com:3000/bb-mcp", "http://192.168.1.1:3000/bb-mcp",
				"http://127.0.0.1.example.com:3000/bb-mcp", "http://user:secret@localhost:3000/bb-mcp",
				"http://localhost:3000/bb-mcp?token=secret", "http://localhost:3000/bb-mcp#fragment",
				"https://localhost:3000/bb-mcp", "file:///tmp/mcp", "http://localhost:0/bb-mcp")) {
			var exception = assertThrows(IllegalArgumentException.class, () -> BlockbenchEnvironmentService.localEndpoint(endpoint));
			assertFalse(exception.getMessage().contains("secret"));
		}
		assertEquals("http://127.0.0.1:3000/bb-mcp", BlockbenchEnvironmentService.localEndpoint("http://localhost:3000/bb-mcp").toString());
		assertEquals("http://[::1]:3000/bb-mcp", BlockbenchEnvironmentService.localEndpoint("http://[::1]:3000/bb-mcp").toString());
	}

	@Test void validatesPayloadTypesAndDoesNotAcceptExecutionOptions() {
		for (String payload : List.of("{\"probeMcp\":\"true\"}", "{\"endpoint\":null}", "{\"launch\":true}"))
			assertThrows(IllegalArgumentException.class, () -> service().inspect(JsonParser.parseString(payload).getAsJsonObject()));
	}

	private static BlockbenchInstallationDetector.Installation installed() {
		return new BlockbenchInstallationDetector.Installation(BlockbenchInstallationDetector.State.READY,
				java.nio.file.Path.of("private-install-path"), "5.1.6", null);
	}

	private static JsonObject probePayload() {
		return JsonParser.parseString("{\"probeMcp\":true}").getAsJsonObject();
	}

	private static McpClientTransport transport(AtomicInteger closed) {
		return (McpClientTransport) java.lang.reflect.Proxy.newProxyInstance(McpClientTransport.class.getClassLoader(),
				new Class<?>[]{McpClientTransport.class}, (proxy, method, args) -> {
					if (method.getName().equals("closeGracefully")) {
						closed.incrementAndGet(); return Mono.empty();
					}
					throw new AssertionError("Unexpected transport call: " + method.getName());
				});
	}

	@Test void constructorInitializationDiscoveryAndCloseFaultsPreserveEditorAndReleaseResources() {
		for (String failure : List.of("transport_construction", "client_construction", "initialization", "tool_discovery", "close")) {
			AtomicInteger transportClosed = new AtomicInteger(), clientClosed = new AtomicInteger();
			var service = new BlockbenchEnvironmentService(BlockbenchEnvironmentServiceTest::installed,
					Duration.ofSeconds(1), Duration.ofSeconds(2), (endpoint, auth) -> {
						if (failure.equals("transport_construction")) throw new LinkageError("secret-constructor-detail");
						return transport(transportClosed);
					}, transport -> {
						if (failure.equals("client_construction")) throw new IllegalStateException("secret-client-detail");
						return new BlockbenchEnvironmentService.DiscoveryClient() {
							public McpSchema.InitializeResult initialize() {
								if (failure.equals("initialization")) throw new IllegalStateException("secret-init-detail");
								return new com.google.gson.Gson().fromJson("{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}", McpSchema.InitializeResult.class);
							}
							public McpSchema.ListToolsResult listTools() {
								if (failure.equals("tool_discovery")) throw new IllegalStateException("secret-discovery-detail");
								return new com.google.gson.Gson().fromJson("{\"tools\":[]}", McpSchema.ListToolsResult.class);
							}
							public void close() {
								clientClosed.incrementAndGet();
								if (failure.equals("close")) throw new IllegalStateException("secret-close-detail");
							}
						};
					});
			JsonObject result = service.inspect(probePayload());
			JsonObject mcp = result.getAsJsonObject("mcp");
			assertEquals(failure, mcp.get("failurePhase").getAsString());
			assertEquals(failure.endsWith("construction") ? "initialization_error" : "protocol_error", mcp.get("state").getAsString());
			assertTrue(result.getAsJsonObject("editor").get("available").getAsBoolean());
			assertFalse(result.toString().contains("secret"));
			assertEquals(failure.equals("client_construction") ? 1 : 0, transportClosed.get());
			assertEquals(failure.endsWith("construction") ? 0 : 1, clientClosed.get());
		}
	}

	@Test void totalBudgetIncludesDetectionAndPreventsLateNetworkAfterCancellation() throws Exception {
		CountDownLatch release = new CountDownLatch(1), exited = new CountDownLatch(1);
		AtomicReference<Thread> worker = new AtomicReference<>();
		AtomicInteger network = new AtomicInteger();
		var service = new BlockbenchEnvironmentService(() -> {
			worker.set(Thread.currentThread());
			try { release.await(); } catch (InterruptedException ignored) { /* Simulate native detection clearing interruption. */ }
			exited.countDown(); return installed();
		}, Duration.ofSeconds(3), Duration.ofMillis(150), (endpoint, auth) -> {
			network.incrementAndGet(); throw new AssertionError("Late network access");
		}, null);
		long started = System.nanoTime();
		JsonObject result = service.inspect(probePayload());
		assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1000);
		assertEquals("timeout", result.getAsJsonObject("mcp").get("state").getAsString());
		assertEquals("unverified", result.getAsJsonObject("editor").get("state").getAsString());
		assertTrue(exited.await(2, TimeUnit.SECONDS));
		release.countDown();
		worker.get().join(2000);
		assertFalse(worker.get().isAlive());
		assertEquals(0, network.get());
		assertEquals("unverified", result.getAsJsonObject("editor").get("state").getAsString());
	}

	@Test void busyRequestsDoNotQueueAndInterruptingCallersReleasesTheWorkers() throws Exception {
		CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
		List<Thread> workers = new CopyOnWriteArrayList<>();
		AtomicInteger cancelled = new AtomicInteger();
		var service = new BlockbenchEnvironmentService(() -> {
			workers.add(Thread.currentThread()); entered.countDown();
			try { release.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
			return installed();
		}, Duration.ofSeconds(3));
		Runnable request = () -> {
			JsonObject result = service.inspect(probePayload());
			if (result.getAsJsonObject("mcp").get("state").getAsString().equals("cancelled")
					&& Thread.currentThread().isInterrupted()) cancelled.incrementAndGet();
		};
		Thread first = Thread.ofVirtual().start(request), second = Thread.ofVirtual().start(request);
		try {
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			long started = System.nanoTime();
			assertEquals("busy", service.inspect(probePayload()).getAsJsonObject("mcp").get("state").getAsString());
			assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 500);
			first.interrupt(); second.interrupt();
			first.join(2000); second.join(2000);
			assertEquals(2, cancelled.get());
		} finally {
			release.countDown(); first.interrupt(); second.interrupt();
			for (Thread worker : workers) worker.join(2000);
		}
		assertEquals("not_checked", service().inspect(new JsonObject()).getAsJsonObject("mcp").get("state").getAsString());
	}

	@Test void defaultVisibleBudgetBoundsAStalledTransportAndPreservesInstalledState() throws Exception {
		AtomicBoolean interrupted = new AtomicBoolean();
		AtomicReference<Thread> worker = new AtomicReference<>();
		var service = new BlockbenchEnvironmentService(BlockbenchEnvironmentServiceTest::installed,
				Duration.ofSeconds(3), Duration.ofSeconds(9), (endpoint, auth) -> {
			worker.set(Thread.currentThread());
			try { new CountDownLatch(1).await(); }
			catch (InterruptedException exception) { interrupted.set(true); Thread.currentThread().interrupt(); }
			throw new IllegalStateException("cancelled construction");
		}, null);
		long started = System.nanoTime();
		JsonObject result = service.inspect(probePayload());
		assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 10_000);
		assertEquals("timeout", result.getAsJsonObject("mcp").get("state").getAsString());
		assertTrue(result.getAsJsonObject("editor").get("available").getAsBoolean());
		worker.get().join(2000);
		assertTrue(interrupted.get());
		assertFalse(worker.get().isAlive());
	}

	@Test void installationFailureIsAnEnvironmentResultAndDefaultQueryStillDoesNotProbe() {
		var service = new BlockbenchEnvironmentService(() -> { throw new IllegalStateException("secret path"); }, Duration.ofSeconds(1));
		JsonObject result = service.inspect(new JsonObject());
		assertEquals("initialization_error", result.get("inspectionState").getAsString());
		assertEquals("not_checked", result.getAsJsonObject("mcp").get("state").getAsString());
		assertEquals("unverified", result.getAsJsonObject("editor").get("state").getAsString());
		assertFalse(result.toString().contains("secret"));
	}

	@Test void timedOutConstructionCannotInitializeALateSessionAndStillClosesItsTransport() throws Exception {
		AtomicReference<Thread> worker = new AtomicReference<>();
		AtomicInteger closed = new AtomicInteger(), clients = new AtomicInteger();
		var service = new BlockbenchEnvironmentService(BlockbenchEnvironmentServiceTest::installed,
				Duration.ofSeconds(1), Duration.ofMillis(200), (endpoint, auth) -> {
			worker.set(Thread.currentThread());
			try { new CountDownLatch(1).await(); } catch (InterruptedException ignored) { }
			return transport(closed);
		}, transport -> { clients.incrementAndGet(); throw new AssertionError("Late client initialization"); });
		JsonObject result = service.inspect(probePayload());
		assertEquals("timeout", result.getAsJsonObject("mcp").get("state").getAsString());
		worker.get().join(2000);
		assertFalse(worker.get().isAlive());
		assertEquals(1, closed.get());
		assertEquals(0, clients.get());
	}

	@Test void stalledCloseIsBoundedAndInterruptedWithoutLosingEditorState() throws Exception {
		AtomicReference<Thread> worker = new AtomicReference<>();
		AtomicBoolean closed = new AtomicBoolean();
		var service = new BlockbenchEnvironmentService(BlockbenchEnvironmentServiceTest::installed,
				Duration.ofSeconds(1), Duration.ofMillis(200), (endpoint, auth) -> transport(new AtomicInteger()),
				transport -> new BlockbenchEnvironmentService.DiscoveryClient() {
			public McpSchema.InitializeResult initialize() {
				return new com.google.gson.Gson().fromJson("{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}", McpSchema.InitializeResult.class);
			}
			public McpSchema.ListToolsResult listTools() { throw new AssertionError("No tools capability"); }
			public void close() {
				worker.set(Thread.currentThread());
				try { new CountDownLatch(1).await(); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
				finally { closed.set(true); }
			}
		});
		long started = System.nanoTime();
		JsonObject result = service.inspect(probePayload());
		assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1000);
		assertEquals("timeout", result.getAsJsonObject("mcp").get("state").getAsString());
		assertTrue(result.getAsJsonObject("editor").get("available").getAsBoolean());
		worker.get().join(2000);
		assertTrue(closed.get());
		assertFalse(worker.get().isAlive());
	}

	@Test void sanitizedTraceRetainsInternalFramesAndCauseTypesWithoutCredentialMessages() {
		var error = new IllegalStateException("Bearer secret", new java.net.ConnectException("password=secret"));
		error.addSuppressed(new IllegalArgumentException("token=secret"));
		String trace = BlockbenchEnvironmentService.safeTrace(error);
		assertFalse(trace.contains("secret"));
		assertTrue(trace.contains("java.net.ConnectException"));
		assertTrue(trace.contains("sanitizedTraceRetainsInternalFrames"));
		assertTrue(trace.contains("Suppressed: java.lang.IllegalArgumentException"));
	}

	private JsonObject inspect(Fixture fixture) {
		JsonObject payload = new JsonObject();
		payload.addProperty("endpoint", fixture.endpoint());
		payload.addProperty("probeMcp", true);
		return service().inspect(payload).getAsJsonObject("mcp");
	}

	private static final class Fixture implements AutoCloseable {
		final HttpServer server;
		final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		final List<String> methods = new CopyOnWriteArrayList<>();
		final List<String> bodies = new CopyOnWriteArrayList<>();
		volatile boolean authorizationSeen;
		Fixture(String mode) throws Exception {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.setExecutor(executor);
			server.createContext("/bb-mcp", exchange -> {
				try (exchange) {
					if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405, -1); return; }
					authorizationSeen |= exchange.getRequestHeaders().containsKey("Authorization");
					String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
					bodies.add(body);
					JsonObject request = JsonParser.parseString(body).getAsJsonObject();
					String method = request.get("method").getAsString();
					methods.add(method);
					if (mode.equals("slow")) { try { Thread.sleep(4000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } }
					if (mode.equals("redirect")) { exchange.getResponseHeaders().add("Location", "http://localhost:1/leak"); exchange.sendResponseHeaders(302, -1); return; }
					if (mode.equals("denied")) { exchange.sendResponseHeaders(401, -1); return; }
					if (mode.equals("forbidden")) { exchange.sendResponseHeaders(403, -1); return; }
					if (mode.equals("http_error")) { exchange.sendResponseHeaders(503, -1); return; }
					if (method.equals("notifications/initialized")) { exchange.sendResponseHeaders(202, -1); return; }
					String result = method.equals("initialize")
							? "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"},\"instructions\":\"untrusted-server-instructions\"}"
							: mode.equals("empty") ? "{\"tools\":[]}"
							: "{\"tools\":[{\"name\":\"create_cube\",\"inputSchema\":{\"type\":\"object\"}}]}";
					String response = "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id") + ",\"result\":" + result + "}";
					if (mode.equals("html")) response = "<html>private-error-details</html>";
					if (mode.equals("bad_protocol")) response = "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id") + ",\"error\":{\"code\":-32600,\"message\":\"private-error-details\"}}";
					if (mode.equals("sse")) response = "event: message\ndata: " + response + "\n\n";
					exchange.getResponseHeaders().add("Content-Type", mode.equals("sse") ? "text/event-stream" : "application/json");
					byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
					exchange.sendResponseHeaders(200, bytes.length);
					exchange.getResponseBody().write(bytes);
				}
			});
			server.start();
		}
		String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/bb-mcp"; }
		@Override public void close() { server.stop(0); executor.shutdownNow(); }
	}
}
