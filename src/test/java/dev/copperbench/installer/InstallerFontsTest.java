package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.Font;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class InstallerFontsTest {
    @Test void bundledPhysicalFontCoversBothLanguagesAndDefaultControls() throws Exception {
        try (var input = InstallerFonts.class.getResourceAsStream(InstallerFonts.RESOURCE)) {
            assertNotNull(input);
            assertEquals(InstallerFonts.SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        }
        var before = UIManager.getDefaults().clone();
        try {
            InstallerFonts.installDefaults();
            String labels = "简体中文 安装位置 许可协议 附加条款 取消 完成 正在复制并校验文件… English · Copperbench";
            for (int style : new int[]{Font.PLAIN, Font.BOLD})
                assertDoesNotThrow(() -> InstallerFonts.requireGlyphs(InstallerFonts.font(style, 14), labels));
            for (JComponent component : new JComponent[]{new JCheckBox(), new JComboBox<>(), new JTextArea(), new JTabbedPane(), new JButton()}) {
                assertTrue(component.getFont().getFamily().contains("Noto Sans CJK SC"));
                assertDoesNotThrow(() -> InstallerFonts.requireGlyphs(component.getFont(), labels));
            }
        } finally { UIManager.getDefaults().putAll((java.util.Map<?, ?>) before); }
    }

    @Test void missingGlyphFailsEvenWhenScreenshotGenerationWouldSucceed() {
        assertThrows(IllegalStateException.class,
                () -> InstallerFonts.requireGlyphs(InstallerFonts.font(Font.PLAIN, 14), "Install \uDBFF\uDFFF"));
    }

    @Test void rerunRemovesAStalePassedReceiptBeforeCheckingAnyText() throws Exception {
        var directory = Files.createTempDirectory("installer-glyphs-");
        try {
            Files.writeString(directory.resolve("glyph-report.json"), "{\"glyphCoverage\":\"passed\"}");
            var check = new InstallerVisualCheck(directory);
            assertFalse(Files.exists(directory.resolve("glyph-report.json")));
            assertThrows(IllegalStateException.class, () -> check.text(InstallerFonts.font(Font.PLAIN, 14), "\uDBFF\uDFFF"));
            assertFalse(Files.exists(directory.resolve("glyph-report.json")));
        } finally { Files.delete(directory); }
    }
}
