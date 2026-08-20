/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.window;

import net.mcreator.io.LoggingSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "copperbench.stage4.windowSmoke", matches = "true")
class WindowsWindowChromeControllerSmokeTest {

	@BeforeAll static void initializeLogging() {
		LoggingSystem.init();
	}

	@Test void installsNativeHitTestingAndCanRestoreTheSystemFrame() throws Exception {
		JFrame[] windowRef = new JFrame[1];
		WindowsWindowChromeController[] controllerRef = new WindowsWindowChromeController[1];
		SwingUtilities.invokeAndWait(() -> {
			JFrame window = new JFrame("Copperbench native chrome smoke");
			window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			window.setSize(900, 700);
			window.setLocationRelativeTo(null);
			WindowsWindowChromeController controller = WindowsWindowChromeController.prepare(window);
			assertNotNull(controller);
			window.setVisible(true);
			assertTrue(controller.install());
			windowRef[0] = window;
			controllerRef[0] = controller;
		});

		JFrame window = windowRef[0];
		WindowsWindowChromeController controller = controllerRef[0];
		try {
			double dpr = controller.devicePixelRatioForTesting();
			controller.accept(new WindowChromeSnapshot("1.0", 1, "css_viewport", dpr,
					new WindowChromeSnapshot.Viewport(900 / dpr, 700 / dpr), List.of(
					new WindowChromeSnapshot.Region("title", WindowChromeSnapshot.Kind.CAPTION,
							new WindowChromeSnapshot.Bounds(0, 0, 900 / dpr, 48)),
					new WindowChromeSnapshot.Region("client", WindowChromeSnapshot.Kind.CLIENT,
							new WindowChromeSnapshot.Bounds(100, 8, 100, 32)),
					new WindowChromeSnapshot.Region("maximize", WindowChromeSnapshot.Kind.MAXIMIZE,
							new WindowChromeSnapshot.Bounds(700 / dpr, 0, 46, 48)))));

			WindowChromeHitTest.WindowBounds bounds = controller.nativeBoundsForTesting();
			assertEquals(13, controller.nativeHitTestForTesting(bounds.left() + 2, bounds.top() + 2),
					"Top-left edge must expose HTTOPLEFT");
			assertEquals(2, controller.nativeHitTestForTesting(bounds.left() + (int) (50 * dpr),
					bounds.top() + (int) (20 * dpr)), "Caption must expose HTCAPTION");
			assertEquals(1, controller.nativeHitTestForTesting(bounds.left() + (int) (120 * dpr),
					bounds.top() + (int) (20 * dpr)), "Interactive overrides must expose HTCLIENT");
			assertEquals(9, controller.nativeHitTestForTesting(bounds.left() + 700 + (int) (20 * dpr),
					bounds.top() + (int) (20 * dpr)), "Maximize button must expose HTMAXBUTTON for Snap Layout");

			CompletableFuture<Void> systemMenu = CompletableFuture.runAsync(controller::openSystemMenuForTesting);
			Thread.sleep(300);
			controller.closeSystemMenuForTesting();
			systemMenu.get(5, TimeUnit.SECONDS);

			controller.doubleClickCaptionForTesting(bounds.left() + (int) (50 * dpr),
					bounds.top() + (int) (20 * dpr));
			assertTrue(controller.isMaximizedForTesting(), "Caption double-click must maximize the native window");
			controller.doubleClickCaptionForTesting(bounds.left() + (int) (50 * dpr),
					bounds.top() + (int) (20 * dpr));
			assertFalse(controller.isMaximizedForTesting(), "A second caption double-click must restore the window");
			SwingUtilities.invokeAndWait(() -> window.setState(Frame.ICONIFIED));
			assertEquals(Frame.ICONIFIED, window.getState());
			SwingUtilities.invokeAndWait(() -> window.setState(Frame.NORMAL));
			assertEquals(Frame.NORMAL, window.getState());

			WindowChromeHitTest.WindowBounds dpiBounds = new WindowChromeHitTest.WindowBounds(bounds.left() + 12,
					bounds.top() + 12, bounds.right() + 112, bounds.bottom() + 62);
			controller.applyDpiChangeForTesting(144, dpiBounds);
			assertEquals(dpiBounds, controller.nativeBoundsForTesting());
			assertEquals(1.5, controller.devicePixelRatioForTesting());

			GraphicsDevice[] displays = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
			if (displays.length > 1) {
				Rectangle secondDisplay = displays[1].getDefaultConfiguration().getBounds();
				SwingUtilities.invokeAndWait(() -> window.setLocation(secondDisplay.x + 32, secondDisplay.y + 32));
				assertTrue(secondDisplay.intersects(window.getBounds()), "Window must remain reachable on the second display");
			}

			SwingUtilities.invokeAndWait(controller::fallbackToSystemFrame);
			assertFalse(controller.isUsingCustomFrame());
			assertFalse(window.isUndecorated());
			assertTrue(window.isVisible());
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				controller.close();
				window.dispose();
			});
		}
	}
}
