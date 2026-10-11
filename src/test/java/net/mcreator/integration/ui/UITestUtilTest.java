package net.mcreator.integration.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UITestUtilTest {

	@Test void waitsForTheWindowToBeShownAfterConstruction() throws Throwable {
		AtomicReference<JFrame> window = new AtomicReference<>();
		AtomicReference<Timer> timer = new AtomicReference<>();
		AtomicBoolean shown = new AtomicBoolean();
		try {
			UITestUtil.waitUntilWindowIsOpen(null, () -> {
				JFrame frame = new JFrame("Delayed visibility");
				window.set(frame);
				frame.setSize(160, 100);
				Timer show = new Timer(400, _ -> {
					frame.setVisible(true);
					shown.set(true);
				});
				show.setRepeats(false);
				timer.set(show);
				show.start();
			});
			assertTrue(shown.get(), "Construction alone must not pass a window-opening check");
			assertFalse(window.get().isDisplayable());
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (timer.get() != null) timer.get().stop();
				if (window.get() != null) window.get().dispose();
			});
		}
	}

	@Test void closesAModalWindowWithoutDisposingPreexistingWindows() throws Throwable {
		AtomicReference<JFrame> existing = new AtomicReference<>();
		AtomicReference<JDialog> dialog = new AtomicReference<>();
		AtomicBoolean returnedFromModal = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> {
			JFrame frame = new JFrame("Existing window");
			frame.setSize(160, 100);
			frame.setVisible(true);
			existing.set(frame);
		});
		try {
			UITestUtil.waitUntilWindowIsOpen(null, () -> {
				JDialog modal = new JDialog(existing.get(), "Modal test", Dialog.ModalityType.APPLICATION_MODAL);
				dialog.set(modal);
				modal.setSize(160, 100);
				modal.setVisible(true);
				returnedFromModal.set(true);
			});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(existing.get().isShowing());
				assertFalse(dialog.get().isDisplayable());
				assertTrue(returnedFromModal.get());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (dialog.get() != null) dialog.get().dispose();
				existing.get().dispose();
			});
		}
	}

	@Test void propagatesTheOriginalOpenerFailure() {
		IllegalStateException expected = new IllegalStateException("opening failed");
		assertSame(expected, assertThrows(IllegalStateException.class,
				() -> UITestUtil.waitUntilWindowIsOpen(null, () -> { throw expected; })));
	}
}
