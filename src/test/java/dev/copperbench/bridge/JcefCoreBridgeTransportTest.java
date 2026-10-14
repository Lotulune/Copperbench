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
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Handshake;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class JcefCoreBridgeTransportTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T05:00:00Z"), ZoneOffset.UTC);

	private JcefBridgeEndpoint endpoint;
	private List<String> dispatchedScripts;
	private JcefCoreBridgeTransport transport;
	private Supplier<UUID> idSupplier;

	@BeforeAll
	static void configureLogDirectory() {
		System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
	}

	@BeforeEach
	void setUp() {
		AtomicLong sequence = new AtomicLong(1_000);
		idSupplier = () -> UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", sequence.getAndIncrement()));

		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(sampleWorkspace());
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, idSupplier), CLOCK, idSupplier);

		dispatchedScripts = new ArrayList<>();
		endpoint = new JcefBridgeEndpoint(
				new WorkspaceEntryAdapter(service, new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)),
				eventJson -> transport.dispatchEvent(eventJson)
		);
		transport = new JcefCoreBridgeTransport(WORKSPACE_ID, endpoint, dispatchedScripts::add);
	}

	@AfterEach void closeTransport() { transport.close(); }

	@Test
	void handlesQueriesWithConfiguredPrefixAndDelegatesToEndpoint() {
		TestQueryCallback handshakeCallback = new TestQueryCallback();
		JsonObject client = new JsonObject();
		client.addProperty("id", "product_shell");
		client.addProperty("version", "0.1.0");
		String handshakeEnvelope = UiCore.wireGson().toJson(
				new Handshake("handshake", idSupplier.get(), List.of("1.0"), client));

		boolean handledHandshake = transport.onQuery(null, null, 1,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + handshakeEnvelope, false, handshakeCallback);

		assertTrue(handledHandshake);
		handshakeCallback.await();
		assertNotNull(handshakeCallback.successResponse);
		assertEquals("1.0", JsonParser.parseString(handshakeCallback.successResponse)
				.getAsJsonObject().get("selectedSchemaVersion").getAsString());

		TestQueryCallback queryCallback = new TestQueryCallback();
		String queryEnvelope = UiCore.wireGson().toJson(
				Query.of(idSupplier.get(), WORKSPACE_ID, Operation.GET_WORKBENCH, new JsonObject()));

		boolean handledQuery = transport.onQuery(null, null, 2,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + queryEnvelope, false, queryCallback);

		assertTrue(handledQuery);
		queryCallback.await();
		assertNotNull(queryCallback.successResponse);
		assertEquals("1.0", JsonParser.parseString(queryCallback.successResponse)
				.getAsJsonObject().get("schemaVersion").getAsString());
	}

	@Test
	void ignoresUnrelatedQueries() {
		TestQueryCallback callback = new TestQueryCallback();

		assertFalse(transport.onQuery(null, null, 1, "@jsResult:test", false, callback));
		assertFalse(transport.onQuery(null, null, 2, "other:channel:data", false, callback));
		assertFalse(transport.onQuery(null, null, 3, null, false, callback));
		assertNull(callback.successResponse);
		assertEquals(-1, callback.failureCode);
	}

	@Test void blockbenchDiscoveryCompletesOffTheBrowserCallbackThread() throws Exception {
		Thread caller = Thread.currentThread();
		var completed = new java.util.concurrent.CompletableFuture<String>();
		CefQueryCallback callback = new CefQueryCallback() {
			@Override public void success(String response) {
				if (Thread.currentThread() == caller) completed.completeExceptionally(new AssertionError("Browser callback thread was used"));
				else completed.complete(response);
			}
			@Override public void failure(int code, String message) { completed.completeExceptionally(new AssertionError(message)); }
		};
		String envelope = UiCore.wireGson().toJson(Query.of(idSupplier.get(), WORKSPACE_ID,
				Operation.GET_BLOCKBENCH_ENVIRONMENT, new JsonObject()));
		assertTrue(transport.onQuery(null, null, 99, JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + envelope, false, callback));
		JsonObject response = JsonParser.parseString(completed.get(5, java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject();
		assertEquals("succeeded", response.get("status").getAsString());
		assertEquals("not_checked", response.getAsJsonObject("data").getAsJsonObject("mcp").get("state").getAsString());
		transport.close();
	}

	@Test
	void mapsExceptionsToQueryFailures() {
		TestQueryCallback callback = new TestQueryCallback();
		String invalidSchemaQuery = "{\"messageType\":\"query\",\"schemaVersion\":\"0.1\"," +
				"\"requestId\":\"00000000-0000-4000-8000-000000001101\"," +
				"\"workspaceId\":\"11111111-1111-4111-8111-111111111111\"," +
				"\"operation\":\"get_workbench\",\"payload\":{}}";

		boolean handled = transport.onQuery(null, null, 1,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + invalidSchemaQuery, false, callback);

		assertTrue(handled);
		callback.await();
		assertNull(callback.successResponse);
		assertEquals(400, callback.failureCode);
		assertTrue(callback.failureMessage.contains("Unsupported UI-Core schema version"));

		TestQueryCallback malformedCallback = new TestQueryCallback();
		boolean handledMalformed = transport.onQuery(null, null, 2,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + "invalid-json-content", false, malformedCallback);

		assertTrue(handledMalformed);
		malformedCallback.await();
		assertNull(malformedCallback.successResponse);
		assertEquals(400, malformedCallback.failureCode);
	}

	@Test void coreRequestsLeaveTheBrowserThreadFreeAndKeepTheirOrder() throws Exception {
		var entered = new java.util.concurrent.CountDownLatch(1);
		var release = new java.util.concurrent.CountDownLatch(1);
		var second = new TestQueryCallback();
		String envelope = JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + UiCore.wireGson().toJson(
				Query.of(idSupplier.get(), WORKSPACE_ID, Operation.GET_WORKBENCH, new JsonObject()));
		CefQueryCallback first = new CefQueryCallback() {
			@Override public void success(String response) {
				entered.countDown();
				try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
			}
			@Override public void failure(int code, String message) { fail(message); }
		};
		try {
			assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
					() -> assertTrue(transport.onQuery(null, null, 1, envelope, false, first)));
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			assertTrue(transport.onQuery(null, null, 2, envelope, false, second));
			assertFalse(second.completed.isDone(), "The second request must not overtake an active request");
		} finally { release.countDown(); }
		second.await();
		assertNotNull(second.successResponse);
	}

	@Test void closeDiscardsQueuedCoreRequests() throws Exception {
		var entered = new java.util.concurrent.CountDownLatch(1);
		var interrupted = new java.util.concurrent.CountDownLatch(1);
		String envelope = JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + UiCore.wireGson().toJson(
				Query.of(idSupplier.get(), WORKSPACE_ID, Operation.GET_WORKBENCH, new JsonObject()));
		CefQueryCallback first = new CefQueryCallback() {
			@Override public void success(String response) {
				entered.countDown();
				try { new java.util.concurrent.CountDownLatch(1).await(); }
				catch (InterruptedException exception) { interrupted.countDown(); Thread.currentThread().interrupt(); }
			}
			@Override public void failure(int code, String message) { fail(message); }
		};
		assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
				() -> assertTrue(transport.onQuery(null, null, 1, envelope, false, first)));
		assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
		var queued = new TestQueryCallback();
		assertTrue(transport.onQuery(null, null, 2, envelope, false, queued));
		transport.close();
		assertTrue(interrupted.await(5, java.util.concurrent.TimeUnit.SECONDS));
		assertFalse(queued.completed.isDone());
	}

	@Test
	void rejectsRequestsForAnotherWorkspace() {
		TestQueryCallback callback = new TestQueryCallback();
		String queryEnvelope = UiCore.wireGson().toJson(Query.of(idSupplier.get(),
				UUID.fromString("22222222-2222-4222-8222-222222222222"), Operation.GET_WORKBENCH,
				new JsonObject()));

		assertTrue(transport.onQuery(null, null, 1,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + queryEnvelope, false, callback));
		assertEquals(400, callback.failureCode);
		assertTrue(callback.failureMessage.contains("different workspace"));
	}

	@Test void acceptanceExtensionUsesTheSameWorkspaceBoundary() {
		for (String target : List.of(WORKSPACE_ID.toString(), "22222222-2222-4222-8222-222222222222")) {
			JsonObject envelope = new JsonObject();
			envelope.addProperty("messageType", "acceptance");
			envelope.addProperty("requestId", idSupplier.get().toString());
			envelope.addProperty("workspaceId", target);
			envelope.add("request", JsonParser.parseString("{\"supportedVersions\":[\"1\"],\"operation\":\"catalog\"}"));
			TestQueryCallback callback = new TestQueryCallback();
			assertTrue(transport.onQuery(null, null, 1, JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + envelope, false, callback));
			callback.await();
			if (target.equals(WORKSPACE_ID.toString()))
				assertEquals("1", JsonParser.parseString(callback.successResponse).getAsJsonObject().get("selectedVersion").getAsString());
			else assertEquals(400, callback.failureCode);
		}
	}

	@Test
	void rejectsQueriesWhenTransportIsClosed() {
		transport.close();
		assertTrue(transport.isClosed());
		assertFalse(transport.onQuery(null, null, 0, "other:channel:data", false, new TestQueryCallback()));

		TestQueryCallback callback = new TestQueryCallback();
		boolean handled = transport.onQuery(null, null, 1,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + "{}", false, callback);

		assertTrue(handled);
		assertNull(callback.successResponse);
		assertEquals(503, callback.failureCode);
		assertTrue(callback.failureMessage.contains("closed"));
	}

	@Test
	void dispatchesEventsSafelyToScriptEvaluator() {
		String testEvent = "{\"messageType\":\"event\",\"schemaVersion\":\"1.0\",\"sequence\":1001,\"event\":\"diagnostics_changed\",\"payload\":{\"diagnostics\":[]}}";
		transport.dispatchEvent(testEvent);

		assertEquals(1, dispatchedScripts.size());
		String script = dispatchedScripts.getFirst();
		assertTrue(script.contains("__COPPERBENCH_EMIT_EVENT__"));
		assertTrue(script.contains("diagnostics_changed"));

		transport.close();
		transport.dispatchEvent(testEvent);
		assertEquals(1, dispatchedScripts.size()); // no further dispatch after close
	}

	@Test
	void dispatchesEventsDuringCommandExecution() {
		TestQueryCallback callback = new TestQueryCallback();
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", idSupplier.get().toString());
		payload.addProperty("elementType", "item");
		payload.addProperty("name", "ruby_sword");
		payload.add("initialValues", new JsonObject());

		String commandEnvelope = UiCore.wireGson().toJson(
				Command.of(idSupplier.get(), WORKSPACE_ID, 0, Operation.CREATE_MOD_ELEMENT, payload));

		boolean handled = transport.onQuery(null, null, 1,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + commandEnvelope, false, callback);

		assertTrue(handled);
		callback.await();
		assertNotNull(callback.successResponse);
		assertEquals("committed", JsonParser.parseString(callback.successResponse)
				.getAsJsonObject().get("status").getAsString());

		assertEquals(1, dispatchedScripts.size());
		assertTrue(dispatchedScripts.getFirst().contains("element_created"));
	}

	@Test
	void bootstrapScriptContainsWorkspaceIdAndPrefix() {
		String bootstrap = JcefCoreBridgeTransport.generateBootstrapScript(WORKSPACE_ID,
				JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX);

		assertTrue(bootstrap.contains(WORKSPACE_ID.toString()));
		assertTrue(bootstrap.contains(JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX));
		assertTrue(bootstrap.contains("window.copperbenchHost"));
		assertTrue(bootstrap.contains("window.__COPPERBENCH_EMIT_EVENT__"));
		assertTrue(bootstrap.contains("window.cefQuery"));

		String escaped = JcefCoreBridgeTransport.generateBootstrapScript(WORKSPACE_ID, "bridge:'\\line:");
		assertTrue(escaped.contains("window.__COPPERBENCH_QUERY_PREFIX__ = \"bridge:"));
		assertFalse(escaped.contains("window.__COPPERBENCH_QUERY_PREFIX__ = 'bridge:"));
	}

	@Test void previewExtensionRetainsWorkspaceBinding() {
		JsonObject envelope = new JsonObject(); envelope.addProperty("messageType", "preview");
		envelope.addProperty("workspaceId", WORKSPACE_ID.toString()); envelope.addProperty("requestId", UUID.randomUUID().toString());
		envelope.add("request", JsonParser.parseString("{\"operation\":\"catalog\",\"supportedVersions\":[\"1\"]}"));
		TestQueryCallback matching = new TestQueryCallback();
		transport.onQuery(null, null, 1, JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + envelope, false, matching);
		matching.await();
		assertNotNull(matching.successResponse);
		assertEquals(10, JsonParser.parseString(matching.successResponse).getAsJsonObject().getAsJsonArray("targets").size());
		envelope.addProperty("workspaceId", UUID.randomUUID().toString());
		TestQueryCallback wrong = new TestQueryCallback();
		transport.onQuery(null, null, 2, JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + envelope, false, wrong);
		assertNull(wrong.successResponse); assertEquals(400, wrong.failureCode);
	}

	@Test void versionCopyUpgradeExtensionCannotReadAnotherWorkspace() {
		JsonObject envelope = new JsonObject(); envelope.addProperty("messageType", "version_copy_upgrade");
		envelope.addProperty("workspaceId", WORKSPACE_ID.toString()); envelope.addProperty("requestId", UUID.randomUUID().toString());
		envelope.add("request", JsonParser.parseString("{\"operation\":\"negotiate\",\"supportedVersions\":[\"1\"]}"));
		TestQueryCallback matching = new TestQueryCallback();
		transport.onQuery(null, null, 1, JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + envelope, false, matching);
		matching.await();
		assertNotNull(matching.successResponse); assertTrue(matching.successResponse.contains("version_copy_upgrade"));
		envelope.addProperty("workspaceId", UUID.randomUUID().toString());
		TestQueryCallback wrong = new TestQueryCallback();
		transport.onQuery(null, null, 2, JcefCoreBridgeTransport.DEFAULT_QUERY_PREFIX + envelope, false, wrong);
		assertNull(wrong.successResponse); assertEquals(400, wrong.failureCode);
	}

	private static WorkspaceState sampleWorkspace() {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of());
	}

	private static final class TestQueryCallback implements CefQueryCallback {
		private final java.util.concurrent.CompletableFuture<Void> completed = new java.util.concurrent.CompletableFuture<>();
		private String successResponse;
		private int failureCode = -1;
		private String failureMessage;

		@Override
		public void success(String response) {
			this.successResponse = response;
			completed.complete(null);
		}

		@Override
		public void failure(int error_code, String error_message) {
			this.failureCode = error_code;
			this.failureMessage = error_message;
			completed.complete(null);
		}

		void await() {
			try { completed.get(5, java.util.concurrent.TimeUnit.SECONDS); }
			catch (Exception exception) { throw new AssertionError("Bridge request did not complete", exception); }
		}
	}
}
