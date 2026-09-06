/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.chromium;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsDisplayAdapterProbeTest {

	@Test void fallsBackOnlyWhenEveryActiveAdapterIsKnownSoftwareDisplay() {
		assertTrue(WindowsDisplayAdapterProbe.requiresSoftwareRendering(List.of("Microsoft Hyper-V Video")));
		assertTrue(WindowsDisplayAdapterProbe.requiresSoftwareRendering(List.of("Microsoft Basic Display Adapter")));
		assertFalse(WindowsDisplayAdapterProbe.requiresSoftwareRendering(List.of()));
		assertFalse(WindowsDisplayAdapterProbe.requiresSoftwareRendering(
				List.of("Microsoft Hyper-V Video", "NVIDIA GeForce RTX 3060 Laptop GPU")));
		assertFalse(WindowsDisplayAdapterProbe.requiresSoftwareRendering(List.of("GameViewer Virtual Display Adapter")));
	}
}
