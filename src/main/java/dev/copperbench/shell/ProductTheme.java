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
        dev.copperbench.ui.NativeDialogTheme.apply(dark);
    }
}
