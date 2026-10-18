/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.ui;

import com.formdev.flatlaf.ui.FlatOptionPaneUI;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.HierarchyListener;

/** Also covers legacy JOptionPane callers that do not use the product prompt factory. */
public final class NativeOptionPaneUI extends FlatOptionPaneUI {
    private boolean normalizing;
    private final java.beans.PropertyChangeListener messageChange = event -> {
        if (!JOptionPane.MESSAGE_PROPERTY.equals(event.getPropertyName()) || normalizing) return;
        JOptionPane pane = (JOptionPane) event.getSource();
        Object content = NativeDialogs.content(pane.getMessage());
        if (content != pane.getMessage()) {
            normalizing = true;
            try { pane.setMessage(content); } finally { normalizing = false; }
        }
    };
    private final HierarchyListener hierarchy = event -> {
        Window window = SwingUtilities.getWindowAncestor(event.getComponent());
        if (window instanceof Dialog) NativeDialogs.brand(window);
    };
    public static ComponentUI createUI(JComponent component) { return new NativeOptionPaneUI(); }
    @Override public void installUI(JComponent component) {
        JOptionPane pane = (JOptionPane) component;
        Object content = NativeDialogs.content(pane.getMessage());
        if (content != pane.getMessage()) pane.setMessage(content);
        super.installUI(component);
        component.addHierarchyListener(hierarchy);
        component.addPropertyChangeListener(messageChange);
    }
    @Override public void uninstallUI(JComponent component) {
        component.removeHierarchyListener(hierarchy);
        component.removePropertyChangeListener(messageChange);
        super.uninstallUI(component);
    }
    @Override protected int getMaxCharactersPerLineCount() { return 56; }
}
