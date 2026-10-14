/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;

/** The offline wizard must not depend on an OS language pack for its own labels. */
final class InstallerFonts {
    static final String RESOURCE = "/fonts/NotoSansCJKsc-Regular.otf";
    static final String SHA256 = "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b";
    private static final Font BASE = load();

    private InstallerFonts() {}

    private static Font load() {
        try (var stream = InstallerFonts.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Bundled installer font is missing: " + RESOURCE);
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | java.awt.FontFormatException exception) {
            throw new IllegalStateException("Cannot load the bundled installer font", exception);
        }
    }

    static Font font(int style, float size) { return BASE.deriveFont(style, size); }

    static void installDefaults() {
        // Includes check boxes, license tabs, option panes and the native Swing file chooser.
        var defaults = UIManager.getDefaults();
        for (Object key : new ArrayList<>(defaults.keySet())) {
            if (defaults.get(key) instanceof Font previous)
                UIManager.put(key, new FontUIResource(font(previous.getStyle(), Math.max(13, previous.getSize2D()))));
        }
    }

    static void requireGlyphs(Font font, String text) {
        String visible = text.replace("\n", "").replace("\r", "").replace("\t", "");
        int missing = font.canDisplayUpTo(visible);
        if (missing >= 0) throw new IllegalStateException("Missing installer glyph in " + font.getFontName()
                + ": " + String.format("U+%04X", visible.codePointAt(missing)) + " in " + text);
    }
}
