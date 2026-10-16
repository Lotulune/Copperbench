/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** UI shared with the native locale acceptance fixture; has no installation side effects. */
final class UpdateInstallerWindow extends JFrame {
    private final String language;
    private final JTextArea status = body("", 3, 38);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton stop;
    UpdateInstallerWindow(String language, AtomicBoolean cancel) {
        super(UpdateUiMessages.text(language, "Copperbench 更新", "Copperbench update"));
        this.language = language;
        status.setText(text("请保存工作并关闭 Copperbench，随后自动升级。", "Save your work and close Copperbench to install the update."));
        status.setName("update-worker-status");
        stop = new JButton(text("取消更新", "Cancel update")); stop.setName("update-worker-cancel");
        stop.addActionListener(e -> { cancel.set(true); stop.setEnabled(false); status.setText(text("正在取消更新…", "Cancelling the update…")); });
        JPanel content = new JPanel(new BorderLayout(0, 16)); content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel body = new JPanel(new BorderLayout(0, 16)); body.add(status, BorderLayout.CENTER); body.add(progress, BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER); content.add(stop, BorderLayout.SOUTH); setContentPane(content);
        progress.setIndeterminate(true); progress.getAccessibleContext().setAccessibleName(text("更新进度", "Update progress"));
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); pack(); setMinimumSize(getSize()); setLocationRelativeTo(null);
    }
    void committing() { stop.setEnabled(false); progress.setIndeterminate(false); progress.setStringPainted(true); }
    void progress(InstallEngine.Progress value) {
        status.setText(UpdateUiMessages.phase(value.phase(), language));
        progress.setValue((int) (value.completed() * 100 / Math.max(1, value.total())));
    }
    void failure(Throwable error) {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.add(body(UpdateUiMessages.errorSummary(error, language), 3, 36), BorderLayout.CENTER);
        JButton details = new JButton(text("错误详情", "Error details"));
        details.addActionListener(e -> {
            JTextArea text = body(UpdateUiMessages.details(error), 12, 56); text.setOpaque(true);
            message(new JScrollPane(text), text("更新错误详情", "Update error details"), JOptionPane.INFORMATION_MESSAGE);
        });
        content.add(details, BorderLayout.SOUTH);
        message(content, text("更新未完成", "Update did not complete"), JOptionPane.ERROR_MESSAGE);
    }
    void cleanupWarning(java.nio.file.Path path) {
        message(body(text("升级已完成，但临时文件未能清理：\n", "Update installed, but temporary files could not be removed:\n") + path, 4, 38),
                text("更新清理", "Update cleanup"), JOptionPane.WARNING_MESSAGE);
    }
    private void message(Object content, String title, int type) {
        String close = text("关闭", "Close");
        JOptionPane.showOptionDialog(this, content, title, JOptionPane.DEFAULT_OPTION, type, null, new Object[]{close}, close);
    }
    private String text(String zh, String en) { return UpdateUiMessages.text(language, zh, en); }
    private static JTextArea body(String message, int rows, int columns) {
        JTextArea area = new JTextArea(message, rows, columns); area.setEditable(false); area.setOpaque(false);
        area.setLineWrap(true); area.setWrapStyleWord(true); area.setFont(UIManager.getFont("Label.font"));
        return area;
    }
}
