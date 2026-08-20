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
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RecoverableBrowserHostTest {

	@Test void recreatesBrowserAndWaitsForLoadBeforeLeavingRecovery() throws Exception {
		FakeBrowser first = new FakeBrowser();
		FakeBrowser replacement = new FakeBrowser();
		AtomicInteger created = new AtomicInteger();
		RecoverableBrowserHost host = onEdt(() -> new RecoverableBrowserHost(
				() -> created.getAndIncrement() == 0 ? first : replacement, () -> {
				}));

		try {
			onEdt(host::forceLoad);
			assertEquals(1, first.forceLoads);

			first.terminate("TS_PROCESS_CRASHED");
			flushEdt();
			assertTrue(host.isRecovering());
			assertTrue(host.isRetryEnabled());
			assertTrue(host.recoveryStatus().contains("Java 服务保留"));

			onEdt(host::retryRecovery);
			assertTrue(first.closed);
			assertEquals(1, replacement.forceLoads);
			assertTrue(host.isRecovering(), "Recovery stays visible until the replacement page loads");

			replacement.loaded();
			flushEdt();
			assertFalse(host.isRecovering());
		} finally {
			onEdt(host::close);
		}
		assertTrue(replacement.closed);
	}

	@Test void failedRecreationKeepsRetryPathAvailable() throws Exception {
		FakeBrowser first = new FakeBrowser();
		AtomicInteger created = new AtomicInteger();
		RecoverableBrowserHost host = onEdt(() -> new RecoverableBrowserHost(() -> {
			if (created.getAndIncrement() == 0)
				return first;
			throw new IllegalStateException("synthetic browser creation failure");
		}, () -> {
		}));

		try {
			first.terminate("TS_PROCESS_OOM");
			flushEdt();
			onEdt(host::retryRecovery);
			assertTrue(host.isRecovering());
			assertTrue(host.isRetryEnabled());
			assertTrue(host.recoveryStatus().contains("重新加载失败"));
		} finally {
			onEdt(host::close);
		}
	}

	@Test void failedReplacementLoadClosesPartialBrowserAndKeepsRetryAvailable() throws Exception {
		FakeBrowser first = new FakeBrowser();
		FakeBrowser brokenReplacement = new FakeBrowser();
		brokenReplacement.loadBeforeFailure = true;
		brokenReplacement.forceLoadFailure = new IllegalStateException("synthetic load failure");
		AtomicInteger created = new AtomicInteger();
		RecoverableBrowserHost host = onEdt(() -> new RecoverableBrowserHost(
				() -> created.getAndIncrement() == 0 ? first : brokenReplacement, () -> {
				}));

		try {
			first.terminate("TS_ABNORMAL_TERMINATION");
			flushEdt();
			onEdt(host::retryRecovery);
			assertTrue(brokenReplacement.closed);
			assertTrue(host.isRecovering());
			assertTrue(host.isRetryEnabled());
		} finally {
			onEdt(host::close);
		}
	}

	private static void flushEdt() throws InvocationTargetException, InterruptedException {
		SwingUtilities.invokeAndWait(() -> {
		});
	}

	private static void onEdt(Runnable action) throws InvocationTargetException, InterruptedException {
		SwingUtilities.invokeAndWait(action);
	}

	private static <T> T onEdt(ValueSupplier<T> supplier) throws InvocationTargetException, InterruptedException {
		Object[] value = new Object[1];
		SwingUtilities.invokeAndWait(() -> value[0] = supplier.get());
		@SuppressWarnings("unchecked") T result = (T) value[0];
		return result;
	}

	@FunctionalInterface
	private interface ValueSupplier<T> {
		T get();
	}

	private static final class FakeBrowser implements RecoverableBrowserHost.BrowserHandle {
		private final JPanel component = new JPanel();
		private final List<Runnable> loadListeners = new CopyOnWriteArrayList<>();
		private final List<Consumer<String>> terminationListeners = new CopyOnWriteArrayList<>();
		private int forceLoads;
		private boolean closed;
		private boolean loadBeforeFailure;
		private RuntimeException forceLoadFailure;

		@Override public Component component() {
			return component;
		}

		@Override public void addLoadListener(Runnable listener) {
			loadListeners.add(listener);
		}

		@Override public void addRendererTerminationListener(Consumer<String> listener) {
			terminationListeners.add(listener);
		}

		@Override public void forceLoad() {
			forceLoads++;
			if (loadBeforeFailure)
				loaded();
			if (forceLoadFailure != null)
				throw forceLoadFailure;
		}

		@Override public void close() {
			closed = true;
		}

		private void terminate(String reason) {
			terminationListeners.forEach(listener -> listener.accept(reason));
		}

		private void loaded() {
			loadListeners.forEach(Runnable::run);
		}
	}
}
