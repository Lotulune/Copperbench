/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import com.formdev.flatlaf.ui.FlatOptionPaneUI;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;

/** Bound legacy plain-text messages too, not just product-owned dialogs. */
public final class ProductOptionPaneUI extends FlatOptionPaneUI {
    public static ComponentUI createUI(JComponent component) { return new ProductOptionPaneUI(); }
    @Override protected int getMaxCharactersPerLineCount() { return 68; }
}
