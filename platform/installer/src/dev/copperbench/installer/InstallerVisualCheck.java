/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Glyph checks are a hard gate. Captures still require visual review for clipping and contrast. */
final class InstallerVisualCheck {
    private final Path output;
    private final List<String> captures = new ArrayList<>();
    private int checkedTexts;

    InstallerVisualCheck(Path output) throws IOException {
        this.output = output;
        Files.createDirectories(output);
        // A failed rerun must not leave a stale successful receipt.
        Files.deleteIfExists(output.resolve("glyph-report.json"));
    }

    void text(Font font, String value) {
        if (value == null || value.isEmpty()) return;
        InstallerFonts.requireGlyphs(font, value);
        checkedTexts++;
    }

    private void check(Component component) {
        Font font = component.getFont();
        if (font != null) {
            if (component instanceof JLabel label) text(font, label.getText());
            if (component instanceof AbstractButton button) text(font, button.getText());
            if (component instanceof JTextComponent editor) text(font, editor.getText());
            if (component instanceof JComboBox<?> box)
                for (int i = 0; i < box.getItemCount(); i++) text(font, String.valueOf(box.getItemAt(i)));
            if (component instanceof JTabbedPane tabs)
                for (int i = 0; i < tabs.getTabCount(); i++) text(font, tabs.getTitleAt(i));
        }
        if (component instanceof Container parent)
            for (Component child : parent.getComponents()) check(child);
    }

    void capture(Container root, String file) throws IOException {
        check(root);
        if (root.getWidth() <= 0 || root.getHeight() <= 0) throw new IOException("Empty preview: " + file);
        var transform = root.getGraphicsConfiguration().getDefaultTransform();
        BufferedImage image = new BufferedImage((int) Math.ceil(root.getWidth() * transform.getScaleX()),
                (int) Math.ceil(root.getHeight() * transform.getScaleY()), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { graphics.scale(transform.getScaleX(), transform.getScaleY()); root.printAll(graphics); } finally { graphics.dispose(); }
        if (!ImageIO.write(image, "png", output.resolve(file).toFile())) throw new IOException("PNG writer unavailable");
        captures.add(file);
    }

    void finish() throws Exception {
        String fontHash;
        try (var stream = InstallerVisualCheck.class.getResourceAsStream(InstallerFonts.RESOURCE)) {
            fontHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        if (!fontHash.equals(InstallerFonts.SHA256)) throw new IOException("Unexpected bundled font digest");
        String jarHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(InstallerMain.ownJar())));
        String sourceHash;
        try (var stream = InstallerVisualCheck.class.getResourceAsStream("/installer-source.json")) {
            if (stream == null) throw new IOException("Missing installer source manifest");
            byte[] source = stream.readAllBytes();
            sourceHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
            Files.write(output.resolve("installer-source.json"), source);
        }
        List<String> entries = new ArrayList<>();
        for (String file : captures) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output.resolve(file))));
            entries.add("{\"file\":" + quote(file) + ",\"sha256\":" + quote(hash) + "}");
        }
        String report = "{\n\"schemaVersion\":1,\"glyphCoverage\":\"passed\",\"checkedTexts\":" + checkedTexts
                + ",\"visualReview\":\"required\",\"installerActionsExecuted\":false,\n\"os\":" + quote(System.getProperty("os.name"))
                + ",\"java\":" + quote(System.getProperty("java.runtime.version"))
                + ",\"uiScale\":" + quote(System.getProperty("sun.java2d.uiScale", "default"))
                + ",\"hostSansSerifCanDisplayChinese\":" + (new Font(Font.SANS_SERIF, Font.PLAIN, 14).canDisplayUpTo("安装许可简体中文") == -1)
                + ",\"font\":" + quote(InstallerFonts.font(Font.PLAIN, 14).getFontName())
                + ",\"fontSha256\":" + quote(fontHash) + ",\"installerJarSha256\":" + quote(jarHash)
                + ",\"sourceManifestSha256\":" + quote(sourceHash) + ",\n\"captures\":[" + String.join(",", entries) + "]\n}\n";
        Files.writeString(output.resolve("glyph-report.json"), report, StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
