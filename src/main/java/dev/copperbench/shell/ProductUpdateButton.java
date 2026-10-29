/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Quiet update entry, visible only after a successful newer-release check. */
public final class ProductUpdateButton extends JButton {
    private final Consumer<String> listener = version -> SwingUtilities.invokeLater(() -> refresh(version));
    public ProductUpdateButton() {
        setName("available-product-update");
        setHorizontalAlignment(SwingConstants.LEFT);
        setMargin(new Insets(12, 14, 12, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        putClientProperty("FlatLaf.style", "arc: 10");
        addActionListener(_ -> ProductUpdateDialog.show(SwingUtilities.getWindowAncestor(this)));
        refresh(ProductUpdateNotifications.INSTANCE.availableVersion());
    }
    private void refresh(String version) {
        setText(UiLocalePreferences.text("更新至 ", "Update to ") + (version == null ? "" : version));
        setToolTipText(getText());
        setVisible(version != null);
        if (getParent() != null) { getParent().revalidate(); getParent().repaint(); }
    }
    @Override public void updateUI() {
        super.updateUI();
        boolean dark = ProductTheme.dark();
        setBackground(new Color(dark ? 0x21384c : 0xeaf4ff));
        setForeground(new Color(dark ? 0xb8dcff : 0x245d8f));
    }
    @Override public void addNotify() { super.addNotify(); ProductUpdateNotifications.INSTANCE.addListener(listener); refresh(ProductUpdateNotifications.INSTANCE.availableVersion()); }
    @Override public void removeNotify() { ProductUpdateNotifications.INSTANCE.removeListener(listener); super.removeNotify(); }
}
