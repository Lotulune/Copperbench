/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import dev.copperbench.core.plugin.PluginCompatibilityClassifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledPluginInventoryTest {

	private final PluginCompatibilityClassifier classifier = new PluginCompatibilityClassifier();

	@Test void firstPartyPluginsMatchSourceTreesAndClassifier() throws Exception {
		assertEquals(8, BundledPluginInventory.FIRST_PARTY.size());
		for (var plugin : BundledPluginInventory.FIRST_PARTY) {
			Path root = Path.of("plugins", plugin.packageName());
			assertTrue(Files.isDirectory(root), plugin.packageName());
			var assessment = classifier.assess(root, 2026002L, 2026002L);
			assertEquals(plugin.pluginId(), assessment.pluginId());
			assertEquals(plugin.level(), assessment.level().name());
			assertEquals(plugin.route(), assessment.route().name());
		}
	}
}
