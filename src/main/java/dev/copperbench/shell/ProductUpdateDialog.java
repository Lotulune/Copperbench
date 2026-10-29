/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import dev.copperbench.ProductIdentity;
import dev.copperbench.release.ProductUpdates;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.workspace.selector.WorkspaceSelector;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import static dev.copperbench.shell.UiLocalePreferences.text;

/** Shared update entry point for Help & about and newer-version notifications. */
public final class ProductUpdateDialog {
    private static JDialog active;
    private ProductUpdateDialog() {}
    public static void show(Window owner) {
        if (active != null && active.isDisplayable()) { active.toFront(); return; }
        new Controller(owner).check();
    }
    private static final class Controller {
        private final Window owner;
        private final JDialog dialog;
        private final JTextArea status = ProductDialogs.body("");
        private final JTextArea details = ProductDialogs.body("");
        private final JToggleButton showDetails = new JToggleButton(text("技术详情", "Technical details"));
        private final JScrollPane detailScroll = new JScrollPane(details);
        private final JProgressBar progress = new JProgressBar(0, 100);
        private final JButton action = new JButton(text("检查更新", "Check for updates"));
        private final JButton cancel = new JButton(text("关闭", "Close"));
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private ProductUpdates.Available available;
        private Path prepared;
        private boolean busy;
        Controller(Window owner) {
            this.owner = owner; dialog = new net.mcreator.ui.dialogs.MCreatorDialog(owner, text("Copperbench 更新", "Copperbench updates")); active = dialog;
            JPanel panel = new JPanel(new BorderLayout(0, 18)); panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
            JLabel title = new JLabel(text("当前版本 ", "Current version ") + ProductIdentity.VERSION);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f)); panel.add(title, BorderLayout.NORTH);
            status.setEditable(false); status.setOpaque(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setFont(UIManager.getFont("Label.font"));
            status.setColumns(38); status.setRows(5);
            JScrollPane statusScroll = new JScrollPane(status); statusScroll.setBorder(BorderFactory.createEmptyBorder());
            statusScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            JPanel middle = new JPanel(new BorderLayout(0, 12)); middle.add(statusScroll, BorderLayout.CENTER);
            JPanel detailPanel = new JPanel(new BorderLayout(0, 8));
            showDetails.setVisible(false); detailScroll.setVisible(false); detailScroll.setPreferredSize(new Dimension(400, 100));
            detailScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            showDetails.addActionListener(_ -> { detailScroll.setVisible(showDetails.isSelected()); dialog.pack(); });
            detailPanel.add(showDetails, BorderLayout.NORTH); detailPanel.add(detailScroll, BorderLayout.CENTER);
            detailPanel.add(progress, BorderLayout.SOUTH); middle.add(detailPanel, BorderLayout.SOUTH); panel.add(middle, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); buttons.add(cancel); buttons.add(action); panel.add(buttons, BorderLayout.SOUTH);
            action.addActionListener(_ -> { if (prepared != null) install(); else if (available != null) download(); else check(); });
            dialog.getRootPane().setDefaultButton(action);
            cancel.addActionListener(_ -> close());
            dialog.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { close(); } });
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); dialog.setContentPane(panel); dialog.pack();
            dialog.setMinimumSize(dialog.getSize()); dialog.setResizable(true); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
        }
        private void check() {
            begin(text("正在检查稳定版更新…", "Checking for stable updates…"));
            new SwingWorker<ProductUpdates.Available, Void>() {
                @Override protected ProductUpdates.Available doInBackground() throws Exception {
                    var release = ProductUpdateNotifications.INSTANCE.check().get(); return release.isPresent() ? ProductUpdates.inspect(release.get(), cancelled::get) : null;
                }
                @Override protected void done() {
                    finish(); if (cancelled.get()) return;
                    try {
                        available = get();
                        if (available == null) { status.setText(text("已是最新稳定版。", "You have the latest stable version.")); action.setText(text("重新检查", "Check again")); }
                        else { status.setText(text("发现新版本 ", "Version available: ") + available.release().version() + "\n"
                                + text("需要下载 ", "Download size: ") + size(available.downloadBytes()) + "\n"
                                + text("只下载变化的组件。安装成功后自动清理旧程序。", "Only changed components are downloaded. Previous application files are removed after installation."));
                            action.setText(text("下载更新", "Download update")); }
                    } catch (Exception error) { failure(error); }
                }
            }.execute();
        }
        private void download() {
            begin(text("正在准备更新…", "Preparing update…")); progress.setIndeterminate(false); progress.setValue(0);
            new SwingWorker<Path, Integer>() {
                @Override protected Path doInBackground() throws Exception {
                    long[] bytes = {0};
                    return ProductUpdates.prepare(available, cancelled::get, amount -> {
                        bytes[0] += amount; int percent = (int) (100 * bytes[0] / Math.max(1, available.downloadBytes()));
                        if (percent > progress.getValue()) publish(percent);
                    });
                }
                @Override protected void process(java.util.List<Integer> values) { progress.setValue(values.getLast()); }
                @Override protected void done() {
                    finish();
                    try {
                        prepared = get();
                        if (cancelled.get()) { discard(); return; }
                        progress.setValue(100); status.setText(text("更新已下载并校验。保存工作后即可安装并重启。", "The update has been downloaded and verified. Save your work to install and restart."));
                        action.setText(text("安装并重启", "Install and restart"));
                    } catch (Exception error) { if (!cancelled.get()) failure(error); }
                }
            }.execute();
        }
        private void install() {
            try {
                ProductUpdates.launch(prepared); prepared = null; dialog.dispose();
                MCreatorApplication app = owner instanceof MCreator creator ? creator.getApplication()
                        : owner instanceof WorkspaceSelector selector ? selector.getApplication() : null;
                if (app != null) Thread.ofVirtual().name("close-for-update").start(app::closeApplication);
            } catch (Exception error) { failure(error); }
        }
        private void begin(String message) { busy = true; cancelled.set(false); action.setEnabled(false); showDetails.setVisible(false); showDetails.setSelected(false); detailScroll.setVisible(false); progress.setIndeterminate(true); status.setText(message); cancel.setText(text("取消", "Cancel")); }
        private void finish() { busy = false; action.setEnabled(true); progress.setIndeterminate(false); cancel.setText(text("关闭", "Close")); }
        private void failure(Exception error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof CancellationException) return;
            status.setText(text("更新未完成。请检查网络连接后重试。", "The update could not complete. Check your connection and try again."));
            if (cause.getMessage() != null && cause.getMessage().contains("installed Copperbench"))
                status.setText(text("请在已安装的 Copperbench 中应用更新。当前运行目录不包含安装信息。", "Apply updates from an installed Copperbench build. This run directory has no installation information."));
            details.setText(String.valueOf(cause.getMessage())); details.setCaretPosition(0); showDetails.setVisible(true);
            dialog.pack();
            action.setText(prepared != null ? text("重试安装", "Retry installation") : available != null ? text("重试下载", "Retry download") : text("重试检查", "Retry check"));
        }
        private void close() { cancelled.set(true); dialog.dispose(); if (!busy) discard(); }
        private void discard() {
            Path request = prepared; prepared = null;
            if (request != null) Thread.ofVirtual().start(() -> {
                try { ProductUpdates.discard(request); }
                catch (Exception error) { org.apache.logging.log4j.LogManager.getLogger(ProductUpdateDialog.class).warn("Could not clean cancelled update", error); }
            });
        }
        private static String size(long bytes) { return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576d); }
    }
}
