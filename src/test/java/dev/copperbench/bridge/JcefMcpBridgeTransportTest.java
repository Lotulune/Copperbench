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
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.PermissionProfile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
		assertTrue(script.contains("setPermissionProfile"));
		assertTrue(script.contains("set_permission_profile"));
	}

	@Test void permissionRequestsAcceptOnlyKnownProfilesAndProperties() {
		for (String value : new String[] {"read_only", "workspace", "full_access"}) {
			var payload = JsonParser.parseString("{\"operation\":\"set_permission_profile\",\"profile\":\"" + value + "\"}").getAsJsonObject();
			assertEquals(PermissionProfile.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
					JcefMcpBridgeTransport.requestedPermissionProfile(payload));
		}
		for (String payload : new String[] {"{}", "{\"profile\":null}", "{\"profile\":true}", "{\"profile\":[]}",
				"{\"profile\":\"FULL_ACCESS\"}", "{\"profile\":\"admin\"}",
				"{\"profile\":\"full_access\",\"workspaceId\":\"other\"}"}) {
			assertThrows(IllegalArgumentException.class, () -> JcefMcpBridgeTransport.requestedPermissionProfile(
					JsonParser.parseString(payload).getAsJsonObject()), payload);
		}
	}
}
