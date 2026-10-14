/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.util.Locale;
import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.Map;

/** Desktop regression: changing an open combo must not detach its popup during event dispatch. */
public final class InstallerInteractionTest {
    private static InstallerWindow window;
    private static InstallerVisualCheck evidence;
    private static int capture;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("A desktop display is required");
        Locale original = Locale.getDefault();
        if (args.length > 0) evidence = new InstallerVisualCheck(Path.of(args[0]));
        try {
            SwingUtilities.invokeAndWait(() -> {
                verifyCheckmarkAlignment(args.length > 0 ? Path.of(args[0]) : null);
                Locale.setDefault(Locale.US);
                window = new InstallerWindow(Map.of("preview", "true"));
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
            if (evidence != null) evidence.finish();
            System.out.println("PASS: open-popup language/theme changes preserve the form and enabled actions");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (window != null) window.dispose(); });
            Locale.setDefault(original);
        }
    }

    /** Verify painted pixels, rather than trusting the icon's nominal layout box. */
    private static void verifyCheckmarkAlignment(Path output) {
        for (boolean dark : new boolean[]{true, false}) for (double scale : new double[]{1, 1.25, 1.5, 2}) {
            JCheckBox box = new JCheckBox(); new InstallerControls(dark).checkbox(box); box.setSelected(true);
            Icon icon = box.getIcon();
            BufferedImage image = new BufferedImage((int) Math.round(icon.getIconWidth() * scale),
                    (int) Math.round(icon.getIconHeight() * scale), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.scale(scale, scale); icon.paintIcon(box, graphics, 0, 0); graphics.dispose();
            Color ink = Color.decode(dark ? "#241a12" : "#ffffff");
            int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getAlpha() > 128 && Math.abs(pixel.getRed() - ink.getRed())
                        + Math.abs(pixel.getGreen() - ink.getGreen()) + Math.abs(pixel.getBlue() - ink.getBlue()) <= 24) {
                    left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
            }
            double dx = (left + right + 1 - image.getWidth()) / 2.0;
            double dy = (top + bottom + 1 - image.getHeight()) / 2.0;
            if (right < left || Math.abs(dx) > .75 || Math.abs(dy) > .75)
                throw new AssertionError("Off-center checkmark: dark=" + dark + ", scale=" + scale + ", dx=" + dx + ", dy=" + dy);
            if (output != null) {
                try { ImageIO.write(image, "png", output.resolve("checkmark-" + (dark ? "dark" : "light") + "-" + scale + ".png").toFile()); }
                catch (IOException exception) { throw new UncheckedIOException(exception); }
            }
            System.out.println("PASS: checkmark center dark=" + dark + " scale=" + scale + " offset=" + dx + "," + dy);
        }
    }

    private static void select(String name, int index) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> choice = (JComboBox<?>) named(window, name);
            choice.setPopupVisible(true);
            if (!choice.isPopupVisible()) throw new AssertionError("Popup did not open");
            if (evidence != null) {
                try {
                    String prefix = String.valueOf(++capture);
                    evidence.capture(window.getContentPane(), prefix + "-form.png");
                    evidence.capture((Container) choice.getAccessibleContext().getAccessibleChild(0), prefix + "-popup.png");
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }
            choice.setSelectedIndex(index);
            // Leave the popup open, as keyboard selection does. The renderer must close it safely.
        });
        // Drain the next EDT turn, after the combo has finished closing its popup.
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void verify(String heading, String action, String color) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (MenuSelectionManager.defaultManager().getSelectedPath().length != 0) throw new AssertionError("Stale popup after render");
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
