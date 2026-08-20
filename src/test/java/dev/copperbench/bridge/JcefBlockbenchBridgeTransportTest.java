package dev.copperbench.bridge;

import dev.copperbench.assets.BlockbenchProcessService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcefBlockbenchBridgeTransportTest {
	@Test void bootstrapExposesOnlyStatusAndAssetIdOpenOperations() {
		String bootstrap = JcefBlockbenchBridgeTransport.generateBootstrapScript();
		assertTrue(bootstrap.contains("window.__COPPERBENCH_BLOCKBENCH_HOST__"));
		assertTrue(bootstrap.contains("schemaVersion: \"1.0\""));
		assertTrue(bootstrap.contains("openAsset"));
		assertTrue(bootstrap.contains(JcefBlockbenchBridgeTransport.QUERY_PREFIX));
		assertFalse(bootstrap.contains("executable"));
		assertFalse(bootstrap.contains("command"));
		assertFalse(bootstrap.contains("filesystem"));
	}

	@Test void wireSnapshotUsesLowercaseStateAndExplicitNulls() {
		String json = JcefBlockbenchBridgeTransport.toWireJson(new BlockbenchProcessService.Snapshot(
				BlockbenchProcessService.State.UNAVAILABLE, null, null, null, null,
				null, null, "5.1.6", "BLOCKBENCH_NOT_CONFIGURED"));
		assertTrue(json.contains("\"state\":\"unavailable\""));
		assertTrue(json.contains("\"assetId\":null"));
		assertTrue(json.contains("\"blockbenchVersion\":\"5.1.6\""));
		assertTrue(json.contains("\"diagnosticCode\":\"BLOCKBENCH_NOT_CONFIGURED\""));
	}
}
