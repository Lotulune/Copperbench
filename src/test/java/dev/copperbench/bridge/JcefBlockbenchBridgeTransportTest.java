package dev.copperbench.bridge;

import dev.copperbench.assets.BlockbenchProcessService;
import dev.copperbench.assets.AssetWorkspaceService;
import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class JcefBlockbenchBridgeTransportTest {
	@TempDir Path workspace;

	@Test void requestsDoNotBlockChromiumAndCompleteInOrder() throws Exception {
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		try (var transport = new JcefBlockbenchBridgeTransport(null,
				new BlockbenchProcessService(new AssetWorkspaceService(workspace), null))) {
			CefQueryCallback first = new CefQueryCallback() {
				@Override public void success(String response) {
					entered.countDown();
					try { release.await(5, TimeUnit.SECONDS); }
					catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
				}
				@Override public void failure(int code, String message) { throw new AssertionError(message); }
			};
			var second = new QueryResult();
			try {
				assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
						assertTrue(transport.onQuery(null, null, 1, statusRequest(), false, first)));
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				assertTrue(transport.onQuery(null, null, 2, statusRequest(), false, second));
				assertFalse(second.result.isDone());
			} finally { release.countDown(); }
			assertTrue(second.result.get(5, TimeUnit.SECONDS).contains("\"state\":\"unavailable\""));
		}
	}

	@Test void closeInterruptsActiveWorkAndDiscardsQueuedRequests() throws Exception {
		var entered = new CountDownLatch(1);
		var interrupted = new CountDownLatch(1);
		try (var transport = new JcefBlockbenchBridgeTransport(null,
				new BlockbenchProcessService(new AssetWorkspaceService(workspace), null))) {
			CefQueryCallback first = new CefQueryCallback() {
				@Override public void success(String response) {
					entered.countDown();
					try { new CountDownLatch(1).await(); }
					catch (InterruptedException exception) { interrupted.countDown(); Thread.currentThread().interrupt(); }
				}
				@Override public void failure(int code, String message) { throw new AssertionError(message); }
			};
			assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
					assertTrue(transport.onQuery(null, null, 1, statusRequest(), false, first)));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var queued = new QueryResult();
			assertTrue(transport.onQuery(null, null, 2, statusRequest(), false, queued));
			transport.close();
			assertTrue(interrupted.await(5, TimeUnit.SECONDS));
			assertFalse(queued.result.isDone());
			var rejected = new QueryResult();
			assertTrue(transport.onQuery(null, null, 3, statusRequest(), false, rejected));
			assertTrue(rejected.result.isCompletedExceptionally());
		}
	}

	private static String statusRequest() {
		return JcefBlockbenchBridgeTransport.QUERY_PREFIX + "{\"operation\":\"status\"}";
	}

	private static final class QueryResult implements CefQueryCallback {
		private final CompletableFuture<String> result = new CompletableFuture<>();
		@Override public void success(String response) { result.complete(response); }
		@Override public void failure(int code, String message) { result.completeExceptionally(new AssertionError(code + ": " + message)); }
	}

	@Test void bootstrapExposesNativePickerButNoCallerSuppliedExecutableOrCommand() {
		String bootstrap = JcefBlockbenchBridgeTransport.generateBootstrapScript();
		assertTrue(bootstrap.contains("window.__COPPERBENCH_BLOCKBENCH_HOST__"));
		assertTrue(bootstrap.contains("schemaVersion: \"1.0\""));
		assertTrue(bootstrap.contains("openAsset"));
		assertTrue(bootstrap.contains(JcefBlockbenchBridgeTransport.QUERY_PREFIX));
		assertTrue(bootstrap.contains("selectExecutable: function()"));
		assertFalse(bootstrap.contains("executable:"));
		assertFalse(bootstrap.contains("setExecutable"));
		assertFalse(bootstrap.contains("command"));
		assertFalse(bootstrap.contains("filesystem"));
	}

	@Test void wireSnapshotUsesLowercaseStateAndExplicitNulls() {
		String json = JcefBlockbenchBridgeTransport.toWireJson(new BlockbenchProcessService.Snapshot(
				BlockbenchProcessService.State.UNAVAILABLE, null, null, null, null,
				null, null, "5.1.6", "BLOCKBENCH_NOT_CONFIGURED", null, null, false));
		assertTrue(json.contains("\"state\":\"unavailable\""));
		assertTrue(json.contains("\"assetId\":null"));
		assertTrue(json.contains("\"blockbenchVersion\":\"5.1.6\""));
		assertTrue(json.contains("\"diagnosticCode\":\"BLOCKBENCH_NOT_CONFIGURED\""));
		assertTrue(json.contains("\"recoveryPointId\":null"));
		assertTrue(json.contains("\"workspaceRevision\":null"));
		assertTrue(json.contains("\"changeCommitted\":false"));
	}
}
