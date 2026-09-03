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

import static org.junit.jupiter.api.Assertions.assertTrue;

class JcefMcpBridgeTransportTest {

	@Test void bootstrapExposesRuntimeStateTokenAndNativeClipboardOperations() {
		String script = JcefMcpBridgeTransport.generateBootstrapScript();

		assertTrue(script.contains("__COPPERBENCH_MCP_HOST__"));
		assertTrue(script.contains("getState"));
		assertTrue(script.contains("revealTokenOnce"));
		assertTrue(script.contains("copyText"));
		assertTrue(script.contains("copperbench:mcp-runtime:"));
		assertTrue(script.contains("get_state"));
		assertTrue(script.contains("reveal_token_once"));
		assertTrue(script.contains("copy_text"));
	}
}
