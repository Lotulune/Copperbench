/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		assertTrue(bootstrap.contains("beginDrag"));
		assertTrue(bootstrap.contains(JcefWindowBridgeTransport.REGION_QUERY_PREFIX));
		assertTrue(bootstrap.contains("JSON.stringify(snapshot)"));
		assertFalse(bootstrap.contains("java.lang"));
		assertFalse(bootstrap.contains("getClass"));
	}

	@Test void acceptsBeginDragOnlyWhenNativeChromeIsActive() throws Exception {
		JFrame frame = new JFrame();
		frame.setUndecorated(true);
		AtomicBoolean dragged = new AtomicBoolean(false);
		AtomicInteger failureCode = new AtomicInteger();
		AtomicBoolean success = new AtomicBoolean(false);
		CefQueryCallback callback = new CefQueryCallback() {
			@Override public void success(String response) {
				success.set(true);
			}

			@Override public void failure(int errorCode, String errorMessage) {
				failureCode.set(errorCode);
			}
		};

		JcefWindowBridgeTransport inactiveTransport = new JcefWindowBridgeTransport(
				frame, () -> {}, snapshot -> {}, () -> false, () -> dragged.set(true));
		inactiveTransport.onQuery(null, null, 1L, JcefWindowBridgeTransport.QUERY_PREFIX + "begin_drag", false, callback);
		assertEquals(409, failureCode.get());
		assertFalse(dragged.get());

		JcefWindowBridgeTransport activeTransport = new JcefWindowBridgeTransport(
				frame, () -> {}, snapshot -> {}, () -> true, () -> dragged.set(true));
		activeTransport.onQuery(null, null, 2L, JcefWindowBridgeTransport.QUERY_PREFIX + "begin_drag", false, callback);
		assertTrue(success.get());
		SwingUtilities.invokeAndWait(() -> {});
		assertTrue(dragged.get());
	}
}
