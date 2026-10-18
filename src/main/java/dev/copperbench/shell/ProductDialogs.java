/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Shared, keyboard-accessible presentation for product-owned native dialogs. */
public final class ProductDialogs {
    private ProductDialogs() {}

    public static JOptionPane pane(String message, int type, Object[] options, Object initial) {
        JTextArea body = new JTextArea(message);
        body.setEditable(false);
        body.setOpaque(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(UIManager.getFont("Label.font"));
        body.setColumns(0);
        body.setBorder(new EmptyBorder(8, 4, 12, 4));
        body.getAccessibleContext().setAccessibleName(message);
        int width = Math.min(460, Math.max(240, GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width - 180));
        body.setSize(width, Short.MAX_VALUE);
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(width, Math.min(body.getPreferredSize().height + 8,
                Math.max(80, GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height - 220))));
        JOptionPane pane = new JOptionPane(scroll, type, JOptionPane.DEFAULT_OPTION, null, options, initial);
        Color surface = UIManager.getColor("Panel.background");
        boolean dark = surface != null && surface.getRed() + surface.getGreen() + surface.getBlue() < 384;
        pane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0, new Color(dark ? 0xe2aa7a : 0x985029)),
                new EmptyBorder(16, 20, 12, 20)));
        return pane;
    }

    public static int show(Component parent, String message, String title, int type, Object[] options, Object initial) {
        JOptionPane pane = pane(message, type, options, initial);
        JDialog dialog = pane.createDialog(parent, title);
        dialog.setResizable(true);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        dialog.setLocationRelativeTo(parent);
        try { dialog.setVisible(true); } finally { dialog.dispose(); }
        for (int i = 0; i < options.length; i++)
            if (options[i].equals(pane.getValue())) return i;
        return JOptionPane.CLOSED_OPTION;
    }
}
