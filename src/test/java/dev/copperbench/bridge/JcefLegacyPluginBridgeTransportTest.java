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

class JcefLegacyPluginBridgeTransportTest {

	@Test void bootstrapExposesOnlyTheScopedLegacyWindowAction() {
		String bootstrap = JcefLegacyPluginBridgeTransport.generateBootstrapScript();

		assertTrue(bootstrap.contains("window.__COPPERBENCH_LEGACY_PLUGIN_HOST__"));
		assertTrue(bootstrap.contains(JcefLegacyPluginBridgeTransport.QUERY_PREFIX));
		assertTrue(bootstrap.contains("available: true"));
		assertTrue(bootstrap.contains("window.cefQuery"));
		assertFalse(bootstrap.contains("java.lang"));
		assertFalse(bootstrap.contains("getClass"));
		assertFalse(bootstrap.contains("filesystem"));
	}
}
