/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import javax.swing.*;
import dev.copperbench.ui.NativeDialogs;
import java.awt.*;

/** Shared, keyboard-accessible presentation for product-owned native dialogs. */
public final class ProductDialogs {
    private ProductDialogs() {}

    public static JOptionPane pane(String message, int type, Object[] options, Object initial) {
        return NativeDialogs.pane(message, type, options, initial);
    }

    public static JTextArea body(String message) {
        return NativeDialogs.body(message);
    }

    public static int show(Component parent, String message, String title, int type, Object[] options, Object initial) {
        return NativeDialogs.show(parent, message, title, type, options, initial);
    }
}
