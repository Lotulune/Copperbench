/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import dev.copperbench.ui.NativeDialogTheme;
import dev.copperbench.ui.NativeDialogs;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/** The real independent update surface; its preview has no installation side effects. */
final class UpdateInstallerWindow extends JFrame {
    private final String language;
    private final AtomicBoolean cancelled;
    private final JLabel heading = new JLabel(), phase = new JLabel(), percent = new JLabel();
    private final JTextArea status = NativeDialogs.body("");
    private final JLabel hint = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton stop;
    private boolean committed;

    static UpdateInstallerWindow create(String language, String theme, String version, AtomicBoolean cancelled) {
        NativeDialogTheme.install(theme, language);
        return new UpdateInstallerWindow(language, version, cancelled);
    }

    private UpdateInstallerWindow(String language, String version, AtomicBoolean cancelled) {
        super(UpdateUiMessages.text(language, "Copperbench 更新", "Copperbench update"));
        this.language = language; this.cancelled = cancelled;
        NativeDialogs.brand(this);
        JPanel content = new JPanel(new BorderLayout(0, 24));
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 24));
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.add(new JLabel(NativeDialogs.icon(48)), BorderLayout.WEST);
        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 4));
        heading.setName("update-worker-heading");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        titles.add(heading);
        JLabel target = new JLabel(version.isBlank() ? "Copperbench" : text("更新至 ", "Updating to ") + version);
        target.setForeground(UIManager.getColor("Label.disabledForeground"));
        titles.add(target); header.add(titles, BorderLayout.CENTER); content.add(header, BorderLayout.NORTH);

        status.setName("update-worker-status");
        status.setRows(3); status.setColumns(38);
        JPanel middle = new JPanel(new BorderLayout(0, 20));
        middle.add(NativeDialogs.scroll(status), BorderLayout.CENTER);
        JPanel meter = new JPanel(new BorderLayout(0, 10));
        JPanel labels = new JPanel(new BorderLayout(12, 0));
        phase.setName("update-worker-phase"); percent.setName("update-worker-percent");
        labels.add(phase, BorderLayout.CENTER); labels.add(percent, BorderLayout.EAST);
        meter.add(labels, BorderLayout.NORTH); meter.add(progress, BorderLayout.CENTER);
        progress.setName("update-worker-progress");
        progress.setIndeterminate(true);
        progress.getAccessibleContext().setAccessibleName(text("更新进度", "Update progress"));
        middle.add(meter, BorderLayout.SOUTH); content.add(middle, BorderLayout.CENTER);

        stop = new JButton(text("取消更新", "Cancel update")); stop.setName("update-worker-cancel");
        stop.addActionListener(event -> cancel());
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel footer = new JPanel(new BorderLayout(16, 0));
        footer.add(hint, BorderLayout.CENTER); footer.add(stop, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH); setContentPane(content);
        heading.setText(text("等待关闭 Copperbench", "Waiting for Copperbench to close"));
        status.setText(text("请保存工作并关闭 Copperbench 及其 Minecraft 客户端。程序退出后会自动开始安装更新。",
                "Save your work and close Copperbench and its Minecraft clients. Installation will start automatically when they exit."));
        phase.setText(text("等待程序退出", "Waiting for the application to exit"));
        hint.setText(text("安装开始前可以取消", "You can cancel before installation starts"));
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent event) { cancel(); } });
        NativeDialogs.fit(this, null);
        setMinimumSize(getSize());
        SwingUtilities.invokeLater(stop::requestFocusInWindow);
    }

    private void cancel() {
        if (committed || cancelled.getAndSet(true)) return;
        stop.setEnabled(false);
        heading.setText(text("正在取消更新", "Cancelling the update"));
        status.setText(text("正在结束更新进程，请稍候。", "The update process is stopping. Please wait."));
        phase.setText(text("正在取消…", "Cancelling…")); hint.setText("");
    }

    void committing() {
        committed = true; stop.setEnabled(false);
        heading.setText(text("正在安装更新", "Installing the update"));
        status.setText(text("正在准备并替换程序文件。完成后会自动重新启动 Copperbench。",
                "Preparing and replacing application files. Copperbench will restart automatically when the update is complete."));
        phase.setText(UpdateUiMessages.phase("prepare", language));
        percent.setText(""); progress.setIndeterminate(true);
        hint.setText(text("安装期间无法取消，请保持窗口开启", "Keep this window open until installation finishes"));
    }

    void progress(InstallEngine.Progress value) {
        phase.setText(UpdateUiMessages.phase(value.phase(), language));
        boolean measured = value.phase().equals("copy") && value.total() > 0;
        boolean complete = value.phase().equals("complete");
        progress.setIndeterminate(!measured && !complete);
        int amount = complete ? 100 : (int) Math.min(100, value.completed() * 100 / Math.max(1, value.total()));
        progress.setValue(amount); percent.setText(measured || complete ? amount + "%" : "");
        if (complete) heading.setText(text("正在重新启动 Copperbench", "Restarting Copperbench"));
    }

    void failure(Throwable error) {
        heading.setText(text("更新未完成", "Update did not complete"));
        status.setText(UpdateUiMessages.errorSummary(error, language));
        progress.setVisible(false); percent.setText(""); hint.setText(""); phase.setText("");
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.add(NativeDialogs.message(UpdateUiMessages.errorSummary(error, language)), BorderLayout.CENTER);
        JButton details = new JButton(text("错误详情", "Error details"));
        details.setName("update-worker-error-details");
        details.addActionListener(event -> NativeDialogs.details(this, UpdateUiMessages.details(error),
                text("更新错误详情", "Update error details"), text("关闭", "Close")));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); actions.add(details);
        content.add(actions, BorderLayout.SOUTH);
        message(content, text("更新未完成", "Update did not complete"), JOptionPane.ERROR_MESSAGE);
    }

    void cleanupWarning(java.nio.file.Path path) {
        message(text("升级已完成，但临时文件未能清理：\n", "Update installed, but temporary files could not be removed:\n") + path,
                text("更新清理", "Update cleanup"), JOptionPane.WARNING_MESSAGE);
    }

    private void message(Object content, String title, int type) {
        String close = text("关闭", "Close");
        NativeDialogs.show(this, content, title, type, new Object[]{close}, close);
    }
    private String text(String zh, String en) { return UpdateUiMessages.text(language, zh, en); }
}
