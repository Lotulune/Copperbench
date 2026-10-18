package dev.copperbench.installer;

import dev.copperbench.ui.NativeDialogs;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class NativeDialogPresentationTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    @Test void standaloneWindowsKeepThemeIdentityAndHonestProgressAcrossLanguages() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path log = directory.resolve("native-dialog-probe.log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("copperbench.test.runtimeClasspath"), Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            boolean exited = child.waitFor(40, TimeUnit.SECONDS);
            String output = java.nio.file.Files.readString(log, StandardCharsets.UTF_8);
            assertTrue(exited, "Native dialog probe timed out: " + output);
            assertEquals(0, child.exitValue(), output);
            assertTrue(output.contains("native-dialog-probe=passed"), output);
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (String language : new String[]{"zh", "en"}) for (String theme : new String[]{"dark", "light"}) verify(language, theme);
                });
                System.out.println("native-dialog-probe=passed");
            } finally {
                SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); });
            }
        }

        private static void verify(String language, String theme) {
            AtomicBoolean cancelled = new AtomicBoolean();
            UpdateInstallerWindow window = UpdateInstallerWindow.create(language, theme, "0.1.11", cancelled);
            try {
                assertFalse(window.getIconImages().isEmpty());
                assertTrue(UIManager.getLookAndFeel().getClass().getName().startsWith("com.formdev.flatlaf."));
                assertEquals(new Color(theme.equals("dark") ? 0x1c1d1c : 0xffffff), window.getContentPane().getBackground());
                for (String background : new String[]{"background", "focusedBackground", "hoverBackground", "pressedBackground"})
                    readable("Button.default.foreground", "Button.default." + background);
                readable("CheckBox.icon.checkmarkColor", "CheckBox.icon.selectedBackground");
                readable("RadioButton.icon.checkmarkColor", "RadioButton.icon.selectedBackground");
                readable("ProgressBar.selectionForeground", "ProgressBar.foreground");
                readable("ProgressBar.selectionBackground", "ProgressBar.background");
                JTextArea message = (JTextArea) named(window, "update-worker-status");
                JProgressBar bar = (JProgressBar) named(window, "update-worker-progress");
                JLabel percent = (JLabel) named(window, "update-worker-percent");
                JButton cancel = (JButton) named(window, "update-worker-cancel");
                assertFalse(message.isEditable()); assertFalse(message.isFocusable());
                assertTrue(bar.isIndeterminate()); assertEquals("", percent.getText()); assertTrue(cancel.isEnabled());
                window.committing();
                assertFalse(cancel.isEnabled()); assertTrue(bar.isIndeterminate()); assertEquals("", percent.getText());
                assertFalse(message.getText().contains("Save your work")); assertFalse(message.getText().contains("请保存工作"));
                window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)); assertFalse(cancelled.get());
                window.progress(new InstallEngine.Progress("copy", 42, 100));
                assertFalse(bar.isIndeterminate()); assertEquals(42, bar.getValue()); assertEquals("42%", percent.getText());
                window.progress(new InstallEngine.Progress("authorize", 0, 1));
                assertTrue(bar.isIndeterminate()); assertEquals("", percent.getText());
                window.progress(new InstallEngine.Progress("complete", 100, 100));
                assertFalse(bar.isIndeterminate()); assertEquals("100%", percent.getText());

                String longText = "Long path / 很长的中文路径 / ".repeat(600);
                JOptionPane plain = new JOptionPane(longText, JOptionPane.INFORMATION_MESSAGE);
                plain.setWantsInput(true);
                JDialog prompt = plain.createDialog(window, "Native prompt regression");
                try {
                    assertFalse(prompt.getIconImages().isEmpty());
                    JScrollPane scroll = (JScrollPane) plain.getMessage();
                    assertEquals(longText, ((JTextArea) scroll.getViewport().getView()).getText());
                    Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                    assertTrue(prompt.getHeight() < screen.height, "Long messages must leave the action row on screen");
                    assertTrue(prompt.getWidth() <= screen.width);
                    assertTrue(scroll.getViewport().getView().getPreferredSize().height > scroll.getViewport().getExtentSize().height,
                            "The complete message must remain reachable by scrolling");
                    assertTrue(hasInput(prompt), "Stock input dialogs must retain their editable field");
                    plain.setMessage(longText + "updated");
                    assertEquals(longText + "updated", ((JTextArea) ((JScrollPane) plain.getMessage()).getViewport().getView()).getText());
                    JOptionPane html = NativeDialogs.pane("<html><b>中文标题</b><br>" + longText + "</html>", JOptionPane.WARNING_MESSAGE, new Object[]{"Close"}, "Close");
                    assertInstanceOf(JEditorPane.class, ((JScrollPane) html.getMessage()).getViewport().getView());
                } finally { prompt.dispose(); }
            } finally { window.dispose(); }
            AtomicBoolean waitingCancelled = new AtomicBoolean();
            UpdateInstallerWindow waiting = UpdateInstallerWindow.create(language, theme, "0.1.11", waitingCancelled);
            try { waiting.dispatchEvent(new WindowEvent(waiting, WindowEvent.WINDOW_CLOSING)); assertTrue(waitingCancelled.get()); }
            finally { waiting.dispose(); }
        }
        private static Component named(Container root, String name) {
            for (Component child : root.getComponents()) {
                if (name.equals(child.getName())) return child;
                if (child instanceof Container container) { Component found = named(container, name); if (found != null) return found; }
            }
            return null;
        }
        private static void readable(String foreground, String background) {
            Color text = UIManager.getColor(foreground), fill = UIManager.getColor(background);
            assertNotNull(text, foreground); assertNotNull(fill, background);
            double first = luminance(text), second = luminance(fill);
            assertTrue((Math.max(first, second) + .05) / (Math.min(first, second) + .05) >= 4.5,
                    foreground + " must remain visible against " + background);
        }
        private static double luminance(Color color) {
            return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
        }
        private static double linear(int channel) {
            double value = channel / 255d;
            return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
        }
        private static boolean hasInput(Container root) {
            for (Component child : root.getComponents()) {
                if (child instanceof JTextField field && field.isEditable() && field.isFocusable()) return true;
                if (child instanceof Container container && hasInput(container)) return true;
            }
            return false;
        }
    }
}
