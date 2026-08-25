package dev.copperbench.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JcefDiagnosticsBridgeTransportTest {

	@Test void bootstrapExposesScopedOpenLogsHost() {
		String bootstrap = JcefDiagnosticsBridgeTransport.generateBootstrapScript();
		assertTrue(bootstrap.contains("__COPPERBENCH_DIAGNOSTICS_HOST__"));
		assertTrue(bootstrap.contains(JcefDiagnosticsBridgeTransport.QUERY_PREFIX));
		assertTrue(bootstrap.contains("JSON.stringify({ failureId: failureId })"));
	}
}
