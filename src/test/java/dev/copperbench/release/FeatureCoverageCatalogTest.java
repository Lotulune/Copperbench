/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureCoverageCatalogTest {

	@Test void everyFirstReleaseSurfaceIsClassified() {
		assertEquals(20, FeatureCoverageCatalog.ITEMS.size());
		assertTrue(FeatureCoverageCatalog.ITEMS.stream()
				.anyMatch(item -> item.id().equals("slice_elements")
						&& item.surface() == FeatureCoverageCatalog.Surface.NEW_UI));
		assertTrue(FeatureCoverageCatalog.ITEMS.stream()
				.anyMatch(item -> item.id().equals("other_mod_elements")
						&& item.surface() == FeatureCoverageCatalog.Surface.UNSUPPORTED));
		assertTrue(FeatureCoverageCatalog.ITEMS.stream()
				.anyMatch(item -> item.id().equals("code_signing")
						&& item.surface() == FeatureCoverageCatalog.Surface.NOT_APPLICABLE));
	}
}
