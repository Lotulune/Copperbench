/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.sdk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentalExtensionManifestTest {

	@Test void minimalFixtureDeclaresAnExplicitExperimentalCompatibilityBoundary() throws Exception {
		Path root = Path.of("sdk/experimental-extension");
		JsonObject schema = JsonParser.parseString(Files.readString(root.resolve("capability-manifest.schema.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject fixture = JsonParser.parseString(Files.readString(
				root.resolve("fixtures/minimal-generator-capabilities.json"), StandardCharsets.UTF_8)).getAsJsonObject();

		assertEquals("experimental", fixture.get("compatibilityLevel").getAsString());
		assertTrue(fixture.get("extensionApiVersion").getAsString().startsWith("experimental-"));
		assertEquals(UiCore.SCHEMA_VERSION, fixture.getAsJsonObject("coreSchema").get("minimum").getAsString());
		assertEquals(UiCore.SCHEMA_VERSION,
				fixture.getAsJsonObject("coreSchema").get("maximumTested").getAsString());
		assertEquals("plugin_loader",
				fixture.getAsJsonObject("diagnosticBoundary").get("failureIsolation").getAsString());
		assertEquals("reject_unless_explicit_dev_override",
				fixture.getAsJsonObject("diagnosticBoundary").get("incompatibleBehavior").getAsString());
		assertTrue(fixture.getAsJsonArray("capabilities").size() >= 1);
		assertEquals("experimental",
				schema.getAsJsonObject("properties").getAsJsonObject("compatibilityLevel").get("const").getAsString());

		String readme = Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8).toLowerCase();
		assertTrue(readme.contains("experimental"));
		assertTrue(readme.contains("not described as a stable"));
		assertFalse(readme.contains("stable third-party sdk is available"));
	}
}
