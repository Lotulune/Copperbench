/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import javax.swing.*;
import java.awt.*;

/** Native dialog tokens mirror the workbench's light and dark palettes. */
public final class ProductTheme {
    private ProductTheme() {}
    public static boolean dark() {
        Color background = UIManager.getColor("Panel.background");
        return background != null && background.getRed() + background.getGreen() + background.getBlue() < 384;
    }
    public static Color muted() { return new Color(dark() ? 0xb1b6ac : 0x60665d); }
    public static Color accent() { return new Color(dark() ? 0xe2aa7a : 0x985029); }
    /** Preserve icon geometry and alpha while following the control's current text color. */
    public static Icon monochrome(Icon source) {
        return new Icon() {
            @Override public int getIconWidth() { return source.getIconWidth(); }
            @Override public int getIconHeight() { return source.getIconHeight(); }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                var image = new java.awt.image.BufferedImage(getIconWidth(), getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D paint = image.createGraphics();
                source.paintIcon(component, paint, 0, 0);
                paint.setComposite(AlphaComposite.SrcIn);
                paint.setColor(component.getForeground());
                paint.fillRect(0, 0, getIconWidth(), getIconHeight());
                paint.dispose();
                graphics.drawImage(image, x, y, null);
            }
        };
    }
    public static void apply(boolean dark) {
        Color surface = new Color(dark ? 0x1c1d1c : 0xffffff);
        Color panel = new Color(dark ? 0x242624 : 0xf7f7f6);
        Color foreground = new Color(dark ? 0xe6e8e2 : 0x262923);
        for (String key : new String[]{"Panel", "OptionPane", "Viewport", "ScrollPane", "TextArea", "List", "Table"}) {
            UIManager.put(key + ".background", surface);
            UIManager.put(key + ".foreground", foreground);
        }
        for (String key : new String[]{"Label", "Button", "CheckBox", "RadioButton", "TabbedPane", "TextField", "EditorPane", "TextPane"})
            UIManager.put(key + ".foreground", foreground);
        UIManager.put("Button.background", panel);
        UIManager.put("TextField.background", panel);
        UIManager.put("TitlePane.background", panel);
        UIManager.put("TitlePane.foreground", foreground);
        UIManager.put("TitlePane.inactiveBackground", panel);
        UIManager.put("TitlePane.inactiveForeground", foreground);
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusColor", accent());
        for (String key : new String[]{"Component.focusedBorderColor", "Button.focusedBorderColor", "Button.default.focusColor", "Button.default.focusedBorderColor"})
            UIManager.put(key, accent());
        UIManager.put("Component.borderColor", new Color(dark ? 0x42463e : 0xd8ddd6));
        Color primary = accent();
        Color onPrimary = new Color(dark ? 0x241a12 : 0xffffff);
        // FlatButtonUI prefers startBackground to background and has separate
        // interaction colors. Override the entire default-button palette so a
        // theme's pale gradient cannot remain underneath our on-accent text.
        for (String key : new String[]{"background", "startBackground", "endBackground", "focusedBackground"})
            UIManager.put("Button.default." + key, primary);
        UIManager.put("Button.default.hoverBackground", new Color(dark ? 0xf2bd90 : 0x833e11));
        UIManager.put("Button.default.pressedBackground", new Color(dark ? 0xd49a68 : 0x6e330d));
        for (String key : new String[]{"foreground", "focusedForeground", "hoverForeground", "pressedForeground"})
            UIManager.put("Button.default." + key, onPrimary);
        UIManager.put("OptionPane.sameSizeButtons", false);
        UIManager.put("OptionPane.buttonPadding", 12);
        UIManager.put("OptionPane.messagePadding", 16);
        UIManager.put("OptionPane.border", BorderFactory.createEmptyBorder(20, 24, 16, 24));
        UIManager.put("OptionPaneUI", ProductOptionPaneUI.class.getName());
    }
}
