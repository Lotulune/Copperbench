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

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchEnvironmentServiceTest {

	private BlockbenchEnvironmentService service() {
		return new BlockbenchEnvironmentService(() -> new BlockbenchInstallationDetector.Installation(
				BlockbenchInstallationDetector.State.UNAVAILABLE, null, null, "BLOCKBENCH_NOT_CONFIGURED"), Duration.ofMillis(700));
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
		for (String mode : List.of("html", "empty", "redirect", "denied", "slow")) {
			try (Fixture fixture = new Fixture(mode)) {
				JsonObject result = inspect(fixture);
				assertNotEquals("tools_available", result.get("state").getAsString(), mode + result);
				assertTrue(result.has("diagnosticCode"));
				if (mode.equals("denied")) assertEquals("authentication_required", result.get("state").getAsString());
				if (mode.equals("empty")) assertEquals("no_tools", result.get("state").getAsString());
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
					if (mode.equals("slow")) { try { Thread.sleep(1800); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } }
					if (mode.equals("redirect")) { exchange.getResponseHeaders().add("Location", "http://localhost:1/leak"); exchange.sendResponseHeaders(302, -1); return; }
					if (mode.equals("denied")) { exchange.sendResponseHeaders(401, -1); return; }
					if (method.equals("notifications/initialized")) { exchange.sendResponseHeaders(202, -1); return; }
					String result = method.equals("initialize")
							? "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"},\"instructions\":\"untrusted-server-instructions\"}"
							: mode.equals("empty") ? "{\"tools\":[]}"
							: "{\"tools\":[{\"name\":\"create_cube\",\"inputSchema\":{\"type\":\"object\"}}]}";
					String response = "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id") + ",\"result\":" + result + "}";
					if (mode.equals("html")) response = "<html>private-error-details</html>";
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
