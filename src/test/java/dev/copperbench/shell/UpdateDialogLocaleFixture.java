package dev.copperbench.shell;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Exercises the production update dialog and live language preference listener without installing. */
public final class UpdateDialogLocaleFixture {
    public static void main(String[] args) throws Exception {
        String language = args.length > 0 ? args[0] : "zh";
        UiLocalePreferences.save(language);
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Copperbench 更新语言验收");
            JPanel actions = new JPanel();
            JButton chinese = new JButton("简体中文"), english = new JButton("English");
            chinese.addActionListener(e -> save("zh")); english.addActionListener(e -> save("en"));
            actions.add(chinese); actions.add(english); frame.add(actions, BorderLayout.SOUTH);
            frame.setSize(820, 520); frame.setLocationRelativeTo(null); frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); frame.setVisible(true);
            ProductUpdateDialog.show(frame, uri -> {
                if (args.length > 1 && args[1].equals("error")) throw new IOException("Update server returned HTTP 429");
                return new ByteArrayInputStream("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }, () -> {});
        });
    }
    private static void save(String value) {
        try { UiLocalePreferences.save(value); }
        catch (IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
