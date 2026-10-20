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

import static org.junit.jupiter.api.Assertions.*;

class JcefAgentBridgeTransportTest {

	@Test void onlyThePackagedWorkbenchUrlCanRequestAgentOperations() {
		assertTrue(JcefAgentBridgeTransport.trustedUrl("http://mcreator/copperbench/ui/index.html"));
		assertTrue(JcefAgentBridgeTransport.trustedUrl("http://mcreator/copperbench/ui/index.html?mode=simple#chat"));
		for (String url : new String[]{"http://mcreator.evil/copperbench/ui/index.html",
				"http://evil@mcreator/copperbench/ui/index.html", "http://mcreator:80/copperbench/ui/index.html",
				"http://mcreator/other.html", "https://mcreator/copperbench/ui/index.html", "file:///index.html",
				"http://localhost:5173/", "http://mcreator/copperbench/ui/%69ndex.html", "about:blank", ""})
			assertFalse(JcefAgentBridgeTransport.trustedUrl(url), url);
		assertFalse(JcefAgentBridgeTransport.trustedUrl(null));
	}

	@Test void requestEnvelopeRejectsUnsupportedOperationsAndNonObjects() {
		for (String payload : new String[]{"null", "[]", "true", "\"status\"", "{", "{\"operation\":null}",
				"{\"operation\":12}", "{\"operation\":true}", "{\"operation\":{}}",
				"{\"operation\":\"execute_shell\"}"})
			assertThrows(RuntimeException.class, () -> JcefAgentBridgeTransport.parseRequest(
					JcefAgentBridgeTransport.QUERY_PREFIX + payload), payload);
		assertThrows(IllegalArgumentException.class,
				() -> JcefAgentBridgeTransport.parseRequest("copperbench:python:{}"));
		assertThrows(IllegalArgumentException.class, () -> JcefAgentBridgeTransport.parseRequest(null));
	}

	@Test void requestsPreserveTheWorkspaceServiceContractWithinABoundedEnvelope() {
		assertTrue(JcefAgentBridgeTransport.parseRequest(JcefAgentBridgeTransport.QUERY_PREFIX + "{}").isEmpty());
		for (String operation : new String[]{"status", "detect", "new", "select", "send", "stop", "approve", "login", "openSetup"})
			assertEquals(operation, JcefAgentBridgeTransport.parseRequest(JcefAgentBridgeTransport.QUERY_PREFIX
					+ "{\"operation\":\"" + operation + "\"}").get("operation").getAsString());
		String prefix = JcefAgentBridgeTransport.QUERY_PREFIX + "{\"operation\":\"send\",\"message\":\"";
		String suffix = "\"}";
		String request = prefix + "x".repeat(JcefAgentBridgeTransport.MAX_REQUEST_CHARS - prefix.length() - suffix.length()) + suffix;
		assertEquals(JcefAgentBridgeTransport.MAX_REQUEST_CHARS, request.length());
		assertEquals("send", JcefAgentBridgeTransport.parseRequest(request).get("operation").getAsString());
		assertThrows(IllegalArgumentException.class, () -> JcefAgentBridgeTransport.parseRequest(request + " "));
	}

	@Test void bootstrapExposesTheScopedPromiseTransport() {
		String script = JcefAgentBridgeTransport.generateBootstrapScript();
		assertTrue(script.contains("__COPPERBENCH_AGENT_HOST__"));
		assertTrue(script.contains("copperbench:agent:"));
		assertTrue(script.contains("persistent: false"));
		assertFalse(script.contains("token"));
	}
}
