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

/** Hosts upstream Swing extension points outside the React product shell. */
public final class LegacyPluginWindow implements AutoCloseable {

	private static final Logger LOG = LogManager.getLogger(LegacyPluginWindow.class);

	private final JFrame owner;
	private final JComponent legacyWorkspaceContent;
	private final JToolBar legacyToolBar;
	private final JMenuBar legacyMenuBar;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private JFrame dialog;

	public LegacyPluginWindow(JFrame owner, JComponent legacyWorkspaceContent, JToolBar legacyToolBar,
			JMenuBar legacyMenuBar) {
		this.owner = Objects.requireNonNull(owner, "owner must not be null");
		this.legacyWorkspaceContent = Objects.requireNonNull(legacyWorkspaceContent,
				"legacyWorkspaceContent must not be null");
		this.legacyToolBar = Objects.requireNonNull(legacyToolBar, "legacyToolBar must not be null");
		this.legacyMenuBar = Objects.requireNonNull(legacyMenuBar, "legacyMenuBar must not be null");
	}

	public void open() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::open);
			return;
		}
		if (closed.get())
			return;
		try {
			if (dialog == null || !dialog.isDisplayable())
				dialog = createDialog();
			dialog.setVisible(true);
			dialog.setExtendedState(dialog.getExtendedState() & ~Frame.ICONIFIED);
			dialog.toFront();
			dialog.requestFocusInWindow();
		} catch (RuntimeException exception) {
			LOG.error("Failed to open legacy plugin window", exception);
			try {
				JOptionPane.showMessageDialog(owner, "旧版插件窗口无法打开，主工作台可以继续使用。",
						"插件窗口错误", JOptionPane.ERROR_MESSAGE);
			} catch (RuntimeException notificationFailure) {
				LOG.warn("Failed to display legacy plugin window error", notificationFailure);
			}
		}
	}

	private JFrame createDialog() {
		JFrame created = new JFrame("旧版插件窗口 - " + owner.getTitle());
		created.setName("legacy-plugin-window");
		created.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		created.setIconImages(owner.getIconImages());
		created.setMinimumSize(new Dimension(900, 600));
		created.setSize(new Dimension(1180, 760));
		created.setJMenuBar(legacyMenuBar);
		created.setContentPane(createContent(legacyWorkspaceContent, legacyToolBar));
		created.setLocationRelativeTo(owner);
		return created;
	}

	static JPanel createContent(JComponent legacyWorkspaceContent, JToolBar legacyToolBar) {
		detach(legacyWorkspaceContent);
		detach(legacyToolBar);
		legacyToolBar.setVisible(true);

		JLabel notice = new JLabel(
				"C 级插件的 Swing 界面在此窗口中运行；关闭此窗口不会卸载插件。",
				UIManager.getIcon("OptionPane.informationIcon"), SwingConstants.LEADING);
		notice.setBorder(new EmptyBorder(8, 12, 8, 12));
		notice.getAccessibleContext().setAccessibleDescription(
				"旧版插件兼容窗口。关闭窗口只隐藏界面，不卸载插件。");

		JPanel header = new JPanel(new BorderLayout());
		header.add(notice, BorderLayout.NORTH);
		header.add(legacyToolBar, BorderLayout.CENTER);

		JPanel content = new JPanel(new BorderLayout());
		content.setName("legacy-plugin-window-content");
		content.add(header, BorderLayout.NORTH);
		content.add(legacyWorkspaceContent, BorderLayout.CENTER);
		return content;
	}

	private static void detach(Component component) {
		Container parent = component.getParent();
		if (parent != null) {
			parent.remove(component);
			parent.revalidate();
			parent.repaint();
		}
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		Runnable dispose = () -> {
			if (dialog != null) {
				dialog.dispose();
				dialog = null;
			}
		};
		if (SwingUtilities.isEventDispatchThread())
			dispose.run();
		else
			SwingUtilities.invokeLater(dispose);
	}
}
