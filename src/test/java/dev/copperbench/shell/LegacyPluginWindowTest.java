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

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPluginWindowTest {

	@Test void contentHostsUpstreamSwingExtensionPointsWithoutCopyingThem() throws Exception {
		JPanel legacyWorkspace = new JPanel();
		legacyWorkspace.setName("representative-c-plugin-panel");
		JToolBar legacyToolBar = new JToolBar();
		legacyToolBar.setVisible(false);
		JPanel[] result = new JPanel[1];

		SwingUtilities.invokeAndWait(() -> result[0] = LegacyPluginWindow.createContent(legacyWorkspace, legacyToolBar));

		assertEquals("legacy-plugin-window-content", result[0].getName());
		assertTrue(SwingUtilities.isDescendingFrom(legacyWorkspace, result[0]));
		assertTrue(SwingUtilities.isDescendingFrom(legacyToolBar, result[0]));
		assertTrue(legacyToolBar.isVisible());
	}
}
