/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.Map;

/** Desktop regression: changing an open combo must not detach its popup during event dispatch. */
public final class InstallerInteractionTest {
    private static InstallerWindow window;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("A desktop display is required");
        Locale original = Locale.getDefault();
        try {
            SwingUtilities.invokeAndWait(() -> {
                Locale.setDefault(Locale.US);
                window = InstallerWindow.create(Map.of("preview", "true"));
                window.setVisible(true);
                ((JTextField) named(window, "installer-location")).setText("/tmp/Copperbench test location");
                checkbox(window).doClick();
            });
            select("installer-language", 0);
            verify("安装 Copperbench", "开始安装", "#1c1d1c");
            select("installer-theme", 1);
            verify("安装 Copperbench", "开始安装", "#f7f5f0");
            select("installer-language", 1);
            verify("Install Copperbench", "Install", "#f7f5f0");
            select("installer-theme", 0);
            verify("Install Copperbench", "Install", "#1c1d1c");
            SwingUtilities.invokeAndWait(() -> window.setSize(720, 480));
            SwingUtilities.invokeAndWait(() -> {
                window.validate();
                JComponent form = (JComponent) named(window, "installer-form");
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, form);
                if (form.getWidth() != viewport.getExtentSize().width) throw new AssertionError("Compact form overflows horizontally");
                for (String control : new String[]{"installer-browse", "installer-license"}) {
                    JComponent action = (JComponent) named(window, control);
                    action.scrollRectToVisible(new Rectangle(0, 0, action.getWidth(), action.getHeight()));
                    if (action.getVisibleRect().width != action.getWidth()) throw new AssertionError("Clipped compact action: " + control);
                }
                JComponent primary = (JComponent) named(window, "installer-primary");
                if (!primary.getVisibleRect().equals(new Rectangle(0, 0, primary.getWidth(), primary.getHeight())))
                    throw new AssertionError("Primary action must remain outside the scrolling form");
            });
            System.out.println("PASS: language/theme popups preserve inputs and compact forms keep their actions reachable");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (window != null) window.dispose(); });
            Locale.setDefault(original);
        }
    }

    private static void select(String name, int index) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> choice = (JComboBox<?>) named(window, name);
            choice.setPopupVisible(true);
            choice.setSelectedIndex(index);
            choice.setPopupVisible(false);
        });
        // Drain the next EDT turn, after the combo has finished closing its popup.
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void verify(String heading, String action, String color) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (!hasLabel(window, heading)) throw new AssertionError("Missing translated heading: " + heading);
            JButton primary = (JButton) named(window, "installer-primary");
            if (!primary.isEnabled() || !primary.getText().equals(action)) throw new AssertionError("Primary action is stale");
            if (!checkbox(window).isSelected()) throw new AssertionError("License selection was lost");
            if (!((JTextField) named(window, "installer-location")).getText().equals("/tmp/Copperbench test location"))
                throw new AssertionError("Installation location was lost");
            if (!window.getContentPane().getBackground().equals(Color.decode(color))) throw new AssertionError("Theme is stale");
        });
    }

    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = named(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JCheckBox checkbox(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JCheckBox checkbox && (checkbox.getText().contains("license") || checkbox.getText().contains("许可"))) return checkbox;
            if (child instanceof Container container) {
                JCheckBox found = checkbox(container);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasLabel(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) return true;
            if (child instanceof Container container && hasLabel(container, text)) return true;
        }
        return false;
    }
}
