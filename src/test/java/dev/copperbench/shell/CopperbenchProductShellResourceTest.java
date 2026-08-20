/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopperbenchProductShellResourceTest {

	private static final Pattern LOCAL_ASSET = Pattern.compile("(?:src|href)=\"\\./([^\"]+)\"");

	@Test void packagedShellUsesOnlyResolvableRelativeAssets() throws Exception {
		ClassLoader loader = CopperbenchProductShell.class.getClassLoader();
		URL index = loader.getResource("copperbench/ui/index.html");
		assertNotNull(index, "React product shell index must be included in processResources output");
		String html;
		try (InputStream stream = index.openStream()) {
			html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertFalse(html.contains("src=\"/assets/"));
		assertFalse(html.contains("href=\"/assets/"));
		var assets = LOCAL_ASSET.matcher(html).results().map(result -> result.group(1)).toList();
		assertFalse(assets.isEmpty(), "The production index should reference its bundled assets");
		for (String asset : assets)
			assertNotNull(loader.getResource("copperbench/ui/" + asset), "Missing packaged UI asset: " + asset);
		assertTrue(CopperbenchProductShell.UI_URL.endsWith("/copperbench/ui/index.html"));
	}
}
