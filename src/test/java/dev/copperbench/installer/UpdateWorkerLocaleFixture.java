package dev.copperbench.installer;

import javax.swing.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the exact independent update window without copying files or writing registration. */
public final class UpdateWorkerLocaleFixture {
    public static void main(String[] args) throws Exception {
        String language = args.length > 0 ? args[0] : "zh";
        SwingUtilities.invokeAndWait(() -> {
            UpdateUiMessages.configureLocale(language);
            UpdateInstallerWindow frame = new UpdateInstallerWindow(language, new AtomicBoolean());
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); frame.setVisible(true);
            if (args.length > 1 && args[1].equals("error")) frame.failure(new IOException("Update file checksum mismatch: example.jar"));
            else { frame.committing(); frame.progress(new InstallEngine.Progress("copy", 42, 100)); }
        });
    }
}
