/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static dev.copperbench.shell.UiLocalePreferences.text;

/** Keeps the Java workspace alive while a failed browser surface is replaced. */
final class RecoverableBrowserHost extends JPanel implements AutoCloseable {

	private static final Logger LOG = LogManager.getLogger(RecoverableBrowserHost.class);
	private static final String BROWSER_CARD = "browser";
	private static final String RECOVERY_CARD = "recovery";

	private final CardLayout cards = new CardLayout();
	private final BrowserFactory browserFactory;
	private final Runnable closeAction;
	private final JButton retryButton = new JButton();
	private final JLabel recoveryStatus = new JLabel("", SwingConstants.CENTER);
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private BrowserHandle currentBrowser;
	private boolean recovering;

	RecoverableBrowserHost(BrowserFactory browserFactory, Runnable closeAction) {
		this.browserFactory = Objects.requireNonNull(browserFactory, "browserFactory must not be null");
		this.closeAction = Objects.requireNonNull(closeAction, "closeAction must not be null");
		setLayout(cards);
		add(createRecoveryPanel(), RECOVERY_CARD);

		BrowserHandle initialBrowser = browserFactory.open();
		installBrowser(initialBrowser);
		cards.show(this, BROWSER_CARD);
	}

	void forceLoad() {
		BrowserHandle browser = currentBrowser;
		if (browser != null && !closed.get())
			browser.forceLoad();
	}

	void executeScriptAsync(String javaScript) {
		BrowserHandle browser = currentBrowser;
		if (browser != null && !closed.get())
			browser.executeScriptAsync(javaScript);
	}

	boolean isRecovering() {
		return recovering;
	}

	boolean isRetryEnabled() {
		return retryButton.isEnabled();
	}

	String recoveryStatus() {
		return recoveryStatus.getText();
	}

	void retryRecovery() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::retryRecovery);
			return;
		}
		if (closed.get() || !recovering || !retryButton.isEnabled())
			return;

		retryButton.setEnabled(false);
		recoveryStatus.setText(text("正在重新加载界面…", "Reloading the interface…"));
		BrowserHandle failedBrowser = currentBrowser;
		currentBrowser = null;
		if (failedBrowser != null) {
			remove(failedBrowser.component());
			closeBrowser(failedBrowser);
		}

		BrowserHandle replacement = null;
		try {
			replacement = browserFactory.open();
			installBrowser(replacement);
			replacement.forceLoad();
		} catch (RuntimeException exception) {
			if (replacement != null) {
				remove(replacement.component());
				if (currentBrowser == replacement)
					currentBrowser = null;
				closeBrowser(replacement);
			}
			LOG.error("Failed to recreate JCEF browser after renderer termination", exception);
			recovering = true;
			recoveryStatus.setText(text("界面重新加载失败，请重试。", "Could not reload the interface. Please try again."));
			retryButton.setEnabled(true);
			cards.show(this, RECOVERY_CARD);
			retryButton.requestFocusInWindow();
		}
		revalidate();
		repaint();
	}

	private void installBrowser(BrowserHandle browser) {
		Objects.requireNonNull(browser, "browser must not be null");
		currentBrowser = browser;
		add(browser.component(), BROWSER_CARD);
		browser.addLoadListener(() -> runOnEdt(() -> showBrowserWhenReady(browser)));
		browser.addRendererTerminationListener(reason -> runOnEdt(() -> showRecovery(browser, reason)));
	}

	private void showRecovery(BrowserHandle browser, String reason) {
		if (closed.get() || browser != currentBrowser)
			return;
		LOG.error("Showing renderer recovery state after {}", reason);
		recovering = true;
		// Rebuild labels here because the language may have changed since this host opened.
		for (Component card : getComponents())
			if (card != browser.component()) remove(card);
		add(createRecoveryPanel(), RECOVERY_CARD);
		recoveryStatus.setText(text("工作区仍保持打开。", "Your workspace is still open."));
		retryButton.setEnabled(true);
		cards.show(this, RECOVERY_CARD);
		revalidate();
		repaint();
		SwingUtilities.invokeLater(() -> retryButton.requestFocusInWindow());
	}

	private void showBrowserWhenReady(BrowserHandle browser) {
		if (closed.get() || browser != currentBrowser)
			return;
		if (recovering) {
			recovering = false;
			retryButton.setEnabled(true);
			cards.show(this, BROWSER_CARD);
			revalidate();
			repaint();
			browser.requestFocus();
		}
	}

	private JPanel createRecoveryPanel() {
		JPanel root = new JPanel(new GridBagLayout());
		root.setBorder(new EmptyBorder(32, 32, 32, 32));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(new EmptyBorder(24, 28, 24, 28));
		content.setPreferredSize(new Dimension(560, 280));

		JLabel icon = new JLabel(UIManager.getIcon("OptionPane.errorIcon"));
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);
		JLabel title = new JLabel(text("界面暂时无法显示", "The interface stopped responding"), SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		recoveryStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

		JTextArea explanation = new JTextArea(text(
				"重新加载后会恢复已保存的内容，尚未保存的输入可能丢失。",
				"Reload to restore saved content. Unsaved input may have been lost."));
		explanation.setEditable(false);
		explanation.setFocusable(false);
		explanation.setOpaque(false);
		explanation.setLineWrap(true);
		explanation.setWrapStyleWord(true);
		explanation.setFont(UIManager.getFont("Label.font"));
		explanation.setAlignmentX(Component.CENTER_ALIGNMENT);
		explanation.setMaximumSize(new Dimension(500, 64));

		retryButton.setText(text("重新加载界面", "Reload interface"));
		retryButton.setName("renderer-recovery-retry");
		if (retryButton.getActionListeners().length == 0)
			retryButton.addActionListener(ignored -> retryRecovery());
		retryButton.getAccessibleContext().setAccessibleDescription(text("重新连接当前工作区", "Reconnect to the current workspace"));
		JButton closeButton = new JButton(text("关闭工作区", "Close workspace"));
		closeButton.setName("renderer-recovery-close");
		closeButton.addActionListener(ignored -> closeAction.run());
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		actions.add(retryButton);
		actions.add(closeButton);
		actions.setAlignmentX(Component.CENTER_ALIGNMENT);

		content.add(icon);
		content.add(Box.createVerticalStrut(14));
		content.add(title);
		content.add(Box.createVerticalStrut(10));
		content.add(recoveryStatus);
		content.add(Box.createVerticalStrut(14));
		content.add(explanation);
		content.add(Box.createVerticalGlue());
		content.add(actions);
		root.add(content);
		return root;
	}

	private static void runOnEdt(Runnable action) {
		if (SwingUtilities.isEventDispatchThread())
			action.run();
		else
			SwingUtilities.invokeLater(action);
	}

	private static void closeBrowser(BrowserHandle browser) {
		try {
			browser.close();
		} catch (RuntimeException exception) {
			LOG.warn("Failed to close replaced JCEF browser", exception);
		}
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		BrowserHandle browser = currentBrowser;
		currentBrowser = null;
		if (browser != null)
			closeBrowser(browser);
	}

	@FunctionalInterface
	interface BrowserFactory {
		BrowserHandle open();
	}

	interface BrowserHandle extends AutoCloseable {
		Component component();

		void addLoadListener(Runnable listener);

		void addRendererTerminationListener(Consumer<String> listener);

		void forceLoad();

		default void executeScriptAsync(String javaScript) {
		}

		default void requestFocus() {
			component().requestFocusInWindow();
		}

		@Override void close();
	}
}
