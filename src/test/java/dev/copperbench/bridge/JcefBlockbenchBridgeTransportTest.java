package dev.copperbench.bridge;

import dev.copperbench.assets.BlockbenchProcessService;
import dev.copperbench.assets.BlockbenchProcessFixture;
import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.assets.BlockbenchBridgeException;
import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JcefBlockbenchBridgeTransportTest {
	@TempDir Path root;

	@Test void finalizesSavedEditsEvenWhenTheAssetPageStopsPolling() throws Exception {
		Path model = root.resolve("models/lamp.bbmodel");
		Files.createDirectories(model.getParent());
		Files.writeString(model, "{}");
		var descriptor = new AssetWorkspaceService(root).list().getFirst();
		CountDownLatch committed = new CountDownLatch(1);
		var process = new BlockbenchProcessFixture();
		var service = process.service(root, new BlockbenchProcessService.EditLifecycle() {
			@Override public BlockbenchProcessService.PreparedEdit prepare(AssetDescriptor asset) {
				return new BlockbenchProcessService.PreparedEdit("recovery", 0, asset.id(), asset.relativePath(), asset.sha256());
			}
			@Override public BlockbenchProcessService.Completion complete(BlockbenchProcessService.PreparedEdit prepared,
					AssetDescriptor current) {
				assertNotEquals(prepared.openedSha256(), current.sha256());
				committed.countDown();
				return new BlockbenchProcessService.Completion(prepared.recoveryPointId(), 1);
			}
		});
		try (var transport = new JcefBlockbenchBridgeTransport(service)) {
			Callback opened = new Callback();
			assertTrue(transport.onQuery(null, null, 1, JcefBlockbenchBridgeTransport.QUERY_PREFIX
					+ "{\"operation\":\"open_asset\",\"assetId\":\"" + descriptor.id() + "\"}", false, opened));
			assertTrue(opened.result.get(2, TimeUnit.SECONDS).contains("\"state\":\"running\""));
			Files.writeString(model, "{\"saved\":true}");
			process.finish();
			assertTrue(committed.await(3, TimeUnit.SECONDS), "Completion must not depend on browser status polling");
			assertTrue(service.status().changeCommitted());
		}
	}

	@Test void slowAssetPreparationDoesNotBlockTheRouterOrCloseAndQueuedLaunchesAreDiscarded() throws Exception {
		Path model = root.resolve("models/lamp.bbmodel");
		Files.createDirectories(model.getParent());
		Files.writeString(model, "{}");
		String assetId = new AssetWorkspaceService(root).list().getFirst().id();
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		var process = new BlockbenchProcessFixture();
		var service = process.service(root, new BlockbenchProcessService.EditLifecycle() {
			@Override public BlockbenchProcessService.PreparedEdit prepare(AssetDescriptor asset) {
				entered.countDown();
				await(release);
				return null;
			}
			@Override public BlockbenchProcessService.Completion complete(BlockbenchProcessService.PreparedEdit prepared,
					AssetDescriptor current) { return null; }
		});
		try (var transport = new JcefBlockbenchBridgeTransport(service);
				var router = Executors.newSingleThreadExecutor()) {
			Callback opened = new Callback(), status = new Callback();
			try {
				assertTrue(router.submit(() -> transport.onQuery(null, null, 1,
						JcefBlockbenchBridgeTransport.QUERY_PREFIX + "{\"operation\":\"open_asset\",\"assetId\":\"" + assetId + "\"}",
						false, opened)).get(2, TimeUnit.SECONDS));
				assertTrue(entered.await(2, TimeUnit.SECONDS));
				assertTrue(router.submit(() -> transport.onQuery(null, null, 2,
						JcefBlockbenchBridgeTransport.QUERY_PREFIX + "{\"operation\":\"status\"}", false, status))
						.get(2, TimeUnit.SECONDS));
				assertFalse(opened.result.isDone());
				// Closing from the same simulated CEF thread must not wait for the service monitor either.
				router.submit(transport::close).get(2, TimeUnit.SECONDS);
			} finally {
				release.countDown();
			}
			router.submit(service::close).get(2, TimeUnit.SECONDS);
			assertEquals(0, process.starts.get(), "Closing during preparation must prevent a delayed editor launch");
			assertFalse(opened.result.isDone(), "A disposed browser must not receive the late launch callback");
			assertFalse(status.result.isDone(), "Queued requests must be discarded on close");
		}
	}

	@Test void slowTaskLaunchReturnsImmediatelyAndPreservesTheErrorCode() throws Exception {
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		var process = new BlockbenchProcessFixture();
		var service = process.service(root, new BlockbenchProcessService.EditLifecycle() {
			@Override public Path modelingEdit(UUID taskId) {
				entered.countDown();
				await(release);
				throw new BlockbenchBridgeException("BLOCKBENCH_ALREADY_RUNNING", "Already editing");
			}
			@Override public BlockbenchProcessService.PreparedEdit prepare(AssetDescriptor asset) { return null; }
			@Override public BlockbenchProcessService.Completion complete(BlockbenchProcessService.PreparedEdit prepared,
					AssetDescriptor current) { return null; }
		});
		try (var transport = new JcefBlockbenchBridgeTransport(service);
				var router = Executors.newSingleThreadExecutor()) {
			Callback callback = new Callback();
			try {
				assertTrue(router.submit(() -> transport.onQuery(null, null, 1,
						JcefBlockbenchBridgeTransport.QUERY_PREFIX + "{\"operation\":\"open_task\",\"taskId\":\"" + UUID.randomUUID() + "\"}",
						false, callback)).get(2, TimeUnit.SECONDS));
				assertTrue(entered.await(2, TimeUnit.SECONDS));
				assertFalse(callback.result.isDone());
			} finally {
				release.countDown();
			}
			assertEquals("409:BLOCKBENCH_ALREADY_RUNNING: Already editing", callback.result.get(2, TimeUnit.SECONDS));
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release preparation");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private static final class Callback implements CefQueryCallback {
		private final CompletableFuture<String> result = new CompletableFuture<>();
		@Override public void success(String response) { result.complete(response); }
		@Override public void failure(int code, String message) { result.complete(code + ":" + message); }
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
