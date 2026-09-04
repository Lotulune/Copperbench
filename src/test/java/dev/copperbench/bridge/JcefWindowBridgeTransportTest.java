/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcefWindowBridgeTransportTest {

	@Test void bootstrapExposesOnlyTheScopedWindowActionTransport() {
		String bootstrap = JcefWindowBridgeTransport.generateBootstrapScript(true);

		assertTrue(bootstrap.contains("window.__COPPERBENCH_WINDOW_HOST__"));
		assertTrue(bootstrap.contains(JcefWindowBridgeTransport.QUERY_PREFIX));
		assertTrue(bootstrap.contains("systemFrame: true"));
		assertTrue(bootstrap.contains("window.cefQuery"));
		assertFalse(bootstrap.contains("java.lang"));
		assertFalse(bootstrap.contains("getClass"));
		assertFalse(bootstrap.contains("filesystem"));
	}

	@Test void nativeBootstrapExposesOnlyTheVersionedChromeRegionReporter() {
		String bootstrap = JcefWindowBridgeTransport.generateBootstrapScript(false, true);

		assertTrue(bootstrap.contains("systemFrame: false"));
		assertTrue(bootstrap.contains("chromeRegionSchemaVersion: \"1.0\""));
		assertTrue(bootstrap.contains("reportChromeRegions"));
		assertTrue(bootstrap.contains(JcefWindowBridgeTransport.REGION_QUERY_PREFIX));
		assertTrue(bootstrap.contains("JSON.stringify(snapshot)"));
		assertFalse(bootstrap.contains("java.lang"));
		assertFalse(bootstrap.contains("getClass"));
	}

}
