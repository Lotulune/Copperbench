package dev.copperbench.installer;

import javax.swing.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.copperbench.ui.NativeDialogs;

/** Runs the exact independent update window without copying files or writing registration. */
public final class UpdateWorkerLocaleFixture {
    public static void main(String[] args) throws Exception {
        String language = args.length > 0 ? args[0] : "zh";
        String scene = args.length > 1 ? args[1] : "progress";
        String theme = args.length > 2 ? args[2] : "dark";
        SwingUtilities.invokeAndWait(() -> {
            if (scene.equals("installer") || scene.equals("uninstall")) {
                var options = new java.util.HashMap<>(java.util.Map.of("preview", "true", "language", language, "theme", theme));
                if (scene.equals("uninstall")) options.put("uninstall", "true");
                InstallerWindow installer = InstallerWindow.create(options);
                installer.setTitle("Copperbench Native Check — " + scene + " / " + language + " / " + theme);
                installer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                installer.setVisible(true); return;
            }
            AtomicBoolean cancelled = new AtomicBoolean();
            UpdateInstallerWindow frame = UpdateInstallerWindow.create(language, theme, "0.1.11", cancelled);
            frame.setTitle("Copperbench Native Check — " + scene + " / " + language + " / " + theme);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); frame.setVisible(true);
            Timer cleanup = new Timer(200, event -> { if (cancelled.get()) { frame.dispose(); System.exit(0); } }); cleanup.start();
            switch (scene) {
                case "error" -> frame.failure(new IOException("Update file checksum mismatch: " + "long-directory/".repeat(25) + "example.jar"));
                case "confirm" -> NativeDialogs.show(frame,
                        UpdateUiMessages.text(language, "取消后会清理本次复制的文件，原安装不变。", "Cancel and remove files copied during this installation? The previous installation will stay in place."),
                        UpdateUiMessages.text(language, "取消安装", "Cancel installation"), JOptionPane.QUESTION_MESSAGE,
                        language.equals("zh") ? new Object[]{"继续安装", "取消安装"} : new Object[]{"Keep installing", "Cancel installation"},
                        UpdateUiMessages.text(language, "继续安装", "Keep installing"));
                case "long" -> JOptionPane.showMessageDialog(frame,
                        UpdateUiMessages.text(language, "无法完成操作。请检查以下文件：\n", "Could not complete the operation. Check these files:\n")
                                + ("C:/Copperbench/" + "long-directory/".repeat(12) + "测试资源.json\n").repeat(18),
                        UpdateUiMessages.text(language, "操作提示", "Operation notice"), JOptionPane.WARNING_MESSAGE);
                case "details" -> NativeDialogs.details(frame, ("java.io.IOException: checksum mismatch at " + "long-directory/".repeat(12) + "resource.json\n").repeat(80)
                        + "测试路径：C:/Copperbench/工作区/纹理/测试资源.json\nEND OF DIAGNOSTICS", UpdateUiMessages.text(language, "错误详情", "Error details"), UpdateUiMessages.text(language, "关闭", "Close"));
                case "waiting" -> { }
                default -> {
                    frame.committing();
                    if (!scene.equals("preparing")) frame.progress(new InstallEngine.Progress(scene.equals("progress") ? "copy" : scene, 42, 100));
                }
            }
        });
    }
}
