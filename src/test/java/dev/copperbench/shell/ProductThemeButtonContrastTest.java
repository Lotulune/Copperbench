package dev.copperbench.shell;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatButtonUI;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ProductThemeButtonContrastTest {
    private static final class ProbeUI extends FlatButtonUI {
        ProbeUI() { super(false); }
        Color background(JComponent component) { return getBackground(component); }
        Color foreground(JComponent component) { return getForeground(component); }
    }

    @Test void defaultButtonsRemainReadableInBothThemesAndEveryInteractiveState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            boolean previousDark = ProductTheme.dark();
            try {
                for (boolean dark : new boolean[]{false, true}) {
                    UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
                    // A legacy theme may supply these independently of background.
                    for (String state : new String[]{"startBackground", "endBackground", "focusedBackground", "hoverBackground", "pressedBackground"})
                        UIManager.put("Button.default." + state, dark ? Color.DARK_GRAY : Color.WHITE);
                    ProductTheme.apply(dark);
                    for (String label : new String[]{"重新配置工作区", "<html>配置国内源并重试<br><small>Use China mirrors and retry"}) {
                        JRootPane root = new JRootPane();
                        JButton button = new JButton(label);
                        ProbeUI ui = new ProbeUI(); button.setUI(ui);
                        root.getContentPane().add(button); root.setDefaultButton(button);
                        button.setSize(240, 52);
                        assertTrue(button.isDefaultButton());
                        for (String state : new String[]{"normal", "focused", "hover", "pressed"}) {
                            button.putClientProperty(FlatClientProperties.COMPONENT_FOCUS_OWNER,
                                    (Predicate<JComponent>) component -> state.equals("focused"));
                            button.getModel().setRollover(state.equals("hover"));
                            button.getModel().setArmed(state.equals("pressed"));
                            button.getModel().setPressed(state.equals("pressed"));
                            Color foreground = ui.foreground(button), background = ui.background(button);
                            assertTrue(contrast(foreground, background) >= 4.5,
                                    "Default button contrast in " + (dark ? "dark " : "light ") + state);
                            BufferedImage image = new BufferedImage(240, 52, BufferedImage.TYPE_INT_RGB);
                            Graphics2D graphics = image.createGraphics(); button.paint(graphics); graphics.dispose();
                            Color paintedBackground = new Color(image.getRGB(120, 8));
                            assertTrue(contrast(foreground, paintedBackground) >= 4.5,
                                    "Painted gradient must also preserve text contrast in " + state);
                        }
                    }
                }
            } catch (UnsupportedLookAndFeelException failure) {
                throw new AssertionError(failure);
            } finally {
                try { UIManager.setLookAndFeel(previous); ProductTheme.apply(previousDark); }
                catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }
    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }
    private static double linear(int value) {
        double channel = value / 255d;
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }
}
