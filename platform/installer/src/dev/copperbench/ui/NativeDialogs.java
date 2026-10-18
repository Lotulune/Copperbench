/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.List;

/** Bounded, branded native prompts shared by the workbench and standalone setup. */
public final class NativeDialogs {
    private NativeDialogs() {}

    private static final class Artwork {
        static final BufferedImage ICON = load();
        private static BufferedImage load() {
            URL resource = NativeDialogs.class.getResource("/icon.png");
            if (resource == null) resource = NativeDialogs.class.getResource("/net/mcreator/ui/res/icon.png");
            if (resource == null) throw new IllegalStateException("Product window icon is missing");
            try { return ImageIO.read(resource); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        }
    }

    public static ImageIcon icon(int size) { return new ImageIcon(Artwork.ICON.getScaledInstance(size, size, Image.SCALE_SMOOTH)); }

    public static void brand(Window window) {
        window.setIconImages(List.of(Artwork.ICON));
    }

    public static JTextArea body(String message) {
        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new FontUIResource(UIManager.getFont("Label.font")));
        area.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        area.getAccessibleContext().setAccessibleName(message);
        return area;
    }

    public static JComponent textComponent(String text) {
        JComponent area;
        if (text.stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("<html")) {
            JEditorPane html = new JEditorPane("text/html", text);
            html.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            html.setEditable(false); html.setFocusable(false); html.setOpaque(false);
            html.setFont(new FontUIResource(UIManager.getFont("Label.font")));
            html.setBorder(BorderFactory.createEmptyBorder());
            area = html;
        } else area = body(text);
        area.setName("native-dialog-message");
        return area;
    }

    public static JScrollPane message(String text) {
        JComponent area = textComponent(text);
        Rectangle screen = screenBounds(null);
        int width = Math.max(180, Math.min(500, screen.width - 180));
        area.setSize(width, Short.MAX_VALUE);
        JScrollPane scroll = scroll(area);
        scroll.setPreferredSize(new Dimension(width, Math.min(area.getPreferredSize().height + 8, Math.max(80, screen.height - 240))));
        return scroll;
    }

    public static JScrollPane scroll(JComponent content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }

    public static JOptionPane pane(Object message, int type, Object[] options, Object initial) {
        return new JOptionPane(content(message),
                type, JOptionPane.DEFAULT_OPTION, null, options, initial);
    }

    public static Object content(Object message) {
        if (message instanceof String text) return message(text);
        if (message instanceof Object[] messages) {
            Object[] wrapped = new Object[messages.length];
            boolean changed = false;
            for (int i = 0; i < messages.length; i++) { wrapped[i] = content(messages[i]); changed |= wrapped[i] != messages[i]; }
            return changed ? wrapped : messages;
        }
        return message;
    }

    public static int show(Component owner, Object message, String title, int type, Object[] options, Object initial) {
        JOptionPane pane = pane(message, type, options, initial);
        JDialog dialog = pane.createDialog(owner, title);
        brand(dialog);
        dialog.setResizable(true);
        fit(dialog, owner);
        try { dialog.setVisible(true); } finally { dialog.dispose(); }
        for (int i = 0; i < options.length; i++) if (options[i].equals(pane.getValue())) return i;
        return JOptionPane.CLOSED_OPTION;
    }

    public static void details(Component owner, String text, String title, String close) {
        JTextArea area = body(text);
        area.setName("native-dialog-details");
        area.setFocusable(true);
        area.setOpaque(true);
        area.setFont(new FontUIResource(com.formdev.flatlaf.util.FontUtils.getCompositeFont(
                System.getProperty("os.name").startsWith("Windows") ? "Consolas" : Font.MONOSPACED, Font.PLAIN, 13)));
        area.setWrapStyleWord(false);
        area.setTabSize(4);
        area.setCaretPosition(0);
        JScrollPane scroll = scroll(area);
        Rectangle screen = screenBounds(owner);
        scroll.setPreferredSize(new Dimension(Math.min(680, Math.max(220, screen.width - 160)),
                Math.min(360, Math.max(100, screen.height - 240))));
        show(owner, scroll, title, JOptionPane.PLAIN_MESSAGE, new Object[]{close}, close);
    }

    public static void fit(Window window, Component owner) {
        window.pack();
        Rectangle screen = screenBounds(owner);
        window.setBounds(window.getX(), window.getY(), Math.min(window.getWidth(), Math.max(200, screen.width - 32)),
                Math.min(window.getHeight(), Math.max(160, screen.height - 32)));
        window.setMinimumSize(new Dimension(Math.min(360, window.getWidth()), Math.min(180, window.getHeight())));
        window.setLocationRelativeTo(owner);
    }

    private static Rectangle screenBounds(Component owner) {
        if (GraphicsEnvironment.isHeadless()) return new Rectangle(0, 0, 1280, 720);
        GraphicsConfiguration config = owner == null ? GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
                : owner.getGraphicsConfiguration();
        if (config == null) return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle bounds = new Rectangle(config.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
        bounds.x += insets.left; bounds.y += insets.top;
        bounds.width -= insets.left + insets.right; bounds.height -= insets.top + insets.bottom;
        return bounds;
    }
}
