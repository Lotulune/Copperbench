/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicalCiModeTest {

	@Test void graphicalCiOptInDisablesOnlyTheChromiumHeadlessDecision() {
		assertTrue(GraphicalCiMode.chromiumHeadlessRequired(true, false));
		assertFalse(GraphicalCiMode.chromiumHeadlessRequired(true, true));
		assertFalse(GraphicalCiMode.chromiumHeadlessRequired(false, false));
	}
}
