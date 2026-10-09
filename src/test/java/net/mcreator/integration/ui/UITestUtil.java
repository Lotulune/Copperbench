/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2021, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.integration.ui;

import net.mcreator.element.GeneratableElement;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.blockly.BlocklyPanel;
import net.mcreator.ui.modgui.IBlocklyPanelHolder;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.validation.AggregatedValidationResult;
import net.mcreator.ui.validation.ValidationResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class UITestUtil {
	private static final Duration WINDOW_OPEN_TIMEOUT = Duration.ofSeconds(
			Math.max(1, Long.getLong("copperbench.uiWindowTimeoutSeconds", 6L)));

	public static ModElementGUI<?> openModElementGUIFor(MCreator mcreator, GeneratableElement generatableElement)
			throws Exception {
		// Store GeneratableElement to workspace so ModElementGUI can load GE from ME correctly
		mcreator.getWorkspace().getModElementManager().storeModElement(generatableElement);

		ModElementGUI<?> modElementGUI = generatableElement.getModElement().getType()
				.getModElementGUI(mcreator, generatableElement.getModElement(), true);

		// Verify that BlocklyPanels are fully loaded
		if (modElementGUI instanceof IBlocklyPanelHolder panelHolder) {
			CountDownLatch latch = new CountDownLatch(1);

			// Prepare a listener to detect if BlocklyPanel(s) are responding
			Set<BlocklyPanel> blocklyPanels = new HashSet<>();
			panelHolder.addBlocklyChangedListener((blocklyPanel, jsEventTriggeredChange) -> {
				if (jsEventTriggeredChange) {
					blocklyPanels.add(blocklyPanel);
					if (blocklyPanels.equals(panelHolder.getBlocklyPanels()))
						latch.countDown();
				}
			});

			panelHolder.forceLoadPanels();

			// Give it time for BlocklyPanel(s) to load and propagate the event
			assertTrue(latch.await(10, TimeUnit.SECONDS));
		}

		return modElementGUI;
	}

	public static void testIfValidationPasses(ModElementGUI<?> modElementGUI,
			boolean skipInitialXMLValidationIfAllowed) {
		AggregatedValidationResult validationResult = modElementGUI.validateAllPages();

		boolean hasErrors = false;
		for (ValidationResult result : validationResult.getGroupedValidationResults()) {
			if (result.type() == ValidationResult.Type.ERROR) {
				if (modElementGUI instanceof IBlocklyPanelHolder panelHolder) {
					if (result.isBlocklyResult()) {
						// skip Blockly validation in case it is marked that initial XML in the editor is not valid
						// and skipInitialXMLValidationIfAllowed flag is set to true
						if (skipInitialXMLValidationIfAllowed && !panelHolder.isInitialXMLValid())
							continue;
					}
				}

				hasErrors = true;
				break;
			}
		}

		if (hasErrors)
			fail(String.join(",", validationResult.getValidationProblemMessages()));
	}

	public static void waitUntilWindowIsOpen(Window master, Runnable openTask) throws Throwable {
		// Retain identities: disposed windows may be collected while a new one opens.
		Set<Window> existing = new HashSet<>(Arrays.asList(Window.getWindows()));
		CountDownLatch opened = new CountDownLatch(1);
		CountDownLatch openerFinished = new CountDownLatch(1);
		AtomicReference<Throwable> throwableAtomic = new AtomicReference<>(null);
		AWTEventListener listener = event -> {
			if (event instanceof WindowEvent windowEvent && windowEvent.getID() == WindowEvent.WINDOW_OPENED
					&& !existing.contains(windowEvent.getWindow()) && windowEvent.getWindow() != master)
				opened.countDown();
		};
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);

		SwingUtilities.invokeLater(() -> {
			try {
				openTask.run();
			} catch (Throwable t) {
				throwableAtomic.set(t);
				opened.countDown();
			} finally {
				openerFinished.countDown();
			}
		});

		try {
			if (!opened.await(WINDOW_OPEN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
				throw new TimeoutException("No new window was shown within " + WINDOW_OPEN_TIMEOUT);
		} finally {
			toolkit.removeAWTEventListener(listener);
			// Modal openers need an EDT close before their setVisible call can return.
			FutureTask<Void> cleanup = new FutureTask<>(() -> {
				Arrays.stream(Window.getWindows()).filter(w -> w != master && !existing.contains(w))
						.forEach(Window::dispose);
				return null;
			});
			SwingUtilities.invokeLater(cleanup);
			cleanup.get(WINDOW_OPEN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}

		if (!openerFinished.await(WINDOW_OPEN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
			throw new TimeoutException("Window opener did not finish after closing its windows");
		if (throwableAtomic.get() != null)
			throw throwableAtomic.get();
	}
}
