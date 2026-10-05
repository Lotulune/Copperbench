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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JcefWindowBridgeTransportTest {

	@Test void unsavedSourceCapabilityIsScopedAndRejectsInvalidCounts() {
		assertFalse(JcefWindowBridgeTransport.generateBootstrapScript(true).contains("reportUnsavedChanges"));
		String bootstrap = JcefWindowBridgeTransport.generateBootstrapScript(true, false, true);
		assertTrue(bootstrap.contains("unsavedChangesSchemaVersion: '1.0'"));
		assertTrue(bootstrap.contains("reportUnsavedChanges: function(count)"));
		assertTrue(bootstrap.contains(JcefWindowBridgeTransport.UNSAVED_SOURCE_QUERY_PREFIX));
		assertEquals(0, JcefWindowBridgeTransport.parseUnsavedSourceCount("{\"schemaVersion\":\"1.0\",\"count\":0}"));
		assertEquals(3, JcefWindowBridgeTransport.parseUnsavedSourceCount("{\"schemaVersion\":\"1.0\",\"count\":3}"));
		for (String count : new String[]{"-1", "1.5", "1000001", "null", "true", "\"1\"", "1e100"})
			assertThrows(RuntimeException.class, () -> JcefWindowBridgeTransport.parseUnsavedSourceCount(
					"{\"schemaVersion\":\"1.0\",\"count\":" + count + "}"), count);
		assertThrows(RuntimeException.class, () -> JcefWindowBridgeTransport.parseUnsavedSourceCount("{\"schemaVersion\":\"2.0\",\"count\":1}"));
		assertThrows(RuntimeException.class, () -> JcefWindowBridgeTransport.parseUnsavedSourceCount("{\"schemaVersion\":\"1.0\",\"count\":1,\"extra\":true}"));
		assertThrows(RuntimeException.class, () -> JcefWindowBridgeTransport.parseUnsavedSourceCount(" ".repeat(257)));
	}

	@Test void bootstrapExposesOnlyTheScopedWindowActionTransport() {
		String bootstrap = JcefWindowBridgeTransport.generateBootstrapScript(true);

		assertTrue(bootstrap.contains("window.__COPPERBENCH_WINDOW_HOST__"));
		assertTrue(bootstrap.contains(JcefWindowBridgeTransport.QUERY_PREFIX));
		assertTrue(bootstrap.contains("systemFrame: true"));
		assertTrue(bootstrap.contains("window.cefQuery"));
		assertTrue(bootstrap.contains("preferencesSchemaVersion: '1.0'"));
		assertTrue(bootstrap.contains("getPreferences: function()"));
		assertTrue(bootstrap.contains("savePreferences: function(patch)"));
		assertTrue(bootstrap.contains(JcefWindowBridgeTransport.PREFERENCES_QUERY_PREFIX));
		assertTrue(bootstrap.contains("resolve(JSON.parse(value))"));
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
