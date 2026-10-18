/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;
import dev.copperbench.installer.UpdateUiMessages;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;

/** Shared native dialog tokens, also bundled into the independent setup JVM. */
public final class NativeDialogTheme {
    private NativeDialogTheme() {}

    public static void install(String theme, String language) {
        boolean dark = "dark".equals(theme);
        FlatLaf.setGlobalExtraDefaults(java.util.Map.of("@accentColor", dark ? "#e2aa7a" : "#985029"));
        FlatLaf.setUseNativeWindowDecorations(true);
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        if (!(UIManager.getLookAndFeel() instanceof FlatLaf laf) || laf.isDark() != dark) {
            try { UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf()); }
            catch (UnsupportedLookAndFeelException error) { throw new IllegalStateException("Could not initialize the native dialog theme", error); }
        }
        UIManager.put("defaultFont", new FontUIResource(com.formdev.flatlaf.util.FontUtils.getCompositeFont(
                System.getProperty("os.name").startsWith("Windows") ? "Microsoft YaHei UI" : Font.SANS_SERIF, Font.PLAIN, 14)));
        apply(dark);
        UpdateUiMessages.configureLocale(language);
    }

    public static void apply(boolean dark) {
        Color surface = color(dark ? 0x1c1d1c : 0xffffff), panel = color(dark ? 0x242624 : 0xf4f4f3);
        Color foreground = color(dark ? 0xe6e8e2 : 0x242622), muted = color(dark ? 0xb1b6ac : 0x696d66);
        Color accent = color(dark ? 0xe2aa7a : 0x985029), border = color(dark ? 0x42463e : 0xd8ddd6);
        Color onAccent = color(dark ? 0x241a12 : 0xffffff);
        for (String key : new String[]{"Panel", "OptionPane", "Viewport", "ScrollPane", "TextArea", "List", "Table", "EditorPane", "TextPane"}) {
            UIManager.put(key + ".background", surface);
            UIManager.put(key + ".foreground", foreground);
        }
        for (String key : new String[]{"Label", "Button", "CheckBox", "RadioButton", "TabbedPane", "TextField", "ComboBox"})
            UIManager.put(key + ".foreground", foreground);
        for (String key : new String[]{"Button", "TextField", "ComboBox", "TitlePane"}) UIManager.put(key + ".background", panel);
        UIManager.put("TitlePane.foreground", foreground);
        UIManager.put("TitlePane.inactiveBackground", panel);
        UIManager.put("TitlePane.inactiveForeground", muted);
        UIManager.put("Label.disabledForeground", muted);
        UIManager.put("Button.disabledText", muted);
        UIManager.put("Button.disabledBackground", panel);
        UIManager.put("TextArea.inactiveBackground", panel);
        UIManager.put("TextArea.disabledBackground", panel);
        UIManager.put("TextField.inactiveBackground", panel);
        UIManager.put("Button.arc", 8);
        UIManager.put("Button.margin", new Insets(8, 16, 8, 16));
        UIManager.put("Button.minimumWidth", 88);
        UIManager.put("Component.arc", 8);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.borderColor", border);
        for (String key : new String[]{"Component.focusColor", "Component.focusedBorderColor", "Button.focusedBorderColor",
                "Button.default.focusColor", "Button.default.focusedBorderColor", "CheckBox.icon.selectedBackground", "RadioButton.icon.selectedBackground"})
            UIManager.put(key, accent);
        UIManager.put("Button.default.background", accent);
        UIManager.put("Button.default.focusedBackground", accent);
        UIManager.put("Button.default.hoverBackground", color(dark ? 0xf2bd90 : 0x833e11));
        UIManager.put("Button.default.pressedBackground", color(dark ? 0xd49a68 : 0x6e330d));
        UIManager.put("Button.default.foreground", onAccent);
        for (String control : new String[]{"CheckBox", "RadioButton"}) {
            UIManager.put(control + ".icon.checkmarkColor", onAccent);
            UIManager.put(control + ".icon.selectedBorderColor", accent);
        }
        UIManager.put("ProgressBar.foreground", accent);
        UIManager.put("ProgressBar.background", panel);
        UIManager.put("ProgressBar.selectionForeground", onAccent);
        UIManager.put("ProgressBar.selectionBackground", foreground);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("ProgressBar.horizontalSize", new Dimension(180, 8));
        UIManager.put("OptionPane.sameSizeButtons", false);
        UIManager.put("OptionPane.buttonPadding", 10);
        UIManager.put("OptionPane.messagePadding", 16);
        UIManager.put("OptionPane.border", BorderFactory.createEmptyBorder(20, 24, 18, 24));
        UIManager.put("OptionPaneUI", NativeOptionPaneUI.class.getName());
    }

    private static Color color(int rgb) { return new ColorUIResource(new Color(rgb)); }
}
