package dev.copperbench.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JcefAssetImportBridgeTransportTest {
	@Test void bootstrapExposesOnlyGrantBasedNativeSelectionAndNeverAPathArgument() {
		String bootstrap = JcefAssetImportBridgeTransport.generateBootstrapScript();
		assertTrue(bootstrap.contains("window.__COPPERBENCH_ASSET_IMPORT_HOST__"));
		assertTrue(bootstrap.contains("selectSource"));
		assertTrue(bootstrap.contains(JcefAssetImportBridgeTransport.QUERY_PREFIX));
		assertTrue(!bootstrap.contains("sourcePath"));
	}
}
