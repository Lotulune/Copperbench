package dev.copperbench.bridge;

import org.junit.jupiter.api.Test;

import dev.copperbench.core.application.WorkspaceApplicationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JcefAssetImportBridgeTransportTest {
	@Test void bootstrapExposesOnlyGrantBasedNativeSelectionAndNeverAPathArgument() {
		String bootstrap = JcefAssetImportBridgeTransport.generateBootstrapScript();
		assertTrue(bootstrap.contains("window.__COPPERBENCH_ASSET_IMPORT_HOST__"));
		assertTrue(bootstrap.contains("selectSource"));
		assertTrue(bootstrap.contains("selectSources"));
		assertTrue(bootstrap.contains("operation: multiple ? 'selectSources' : 'selectSource'"));
		assertTrue(bootstrap.contains(JcefAssetImportBridgeTransport.QUERY_PREFIX));
		assertTrue(!bootstrap.contains("sourcePath"));
	}

	@Test void droppedSourceEventContainsOnlyGrantMetadataAndNeverAnExternalPath() {
		String script = JcefAssetImportBridgeTransport.generateDroppedSourcesScript(List.of(
				new WorkspaceApplicationService.AssetImportSelectionGrant(
						"grant-1", "lamp.png", 12, "2026-09-05T10:00:00Z")));
		assertTrue(script.contains(JcefAssetImportBridgeTransport.DROP_EVENT));
		assertTrue(script.contains("grant-1"));
		assertTrue(script.contains("lamp.png"));
		assertTrue(!script.contains("sourcePath"));
		assertTrue(!script.contains("C:\\"));
	}
}
