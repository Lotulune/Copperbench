/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import dev.copperbench.ProductIdentity;
import dev.copperbench.release.ProductUpdates;
import dev.copperbench.installer.UpdateUiMessages;
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

/** Shared update entry point for the native selector, legacy menus and the workbench settings. */
public final class ProductUpdateDialog {
    private static JDialog active;
    private ProductUpdateDialog() {}
    public static void show(Window owner) {
        if (active != null && active.isDisplayable()) { active.toFront(); return; }
        new Controller(owner, dev.copperbench.installer.UpdatePreparation::open, null).check();
    }
    static void show(Window owner, dev.copperbench.installer.UpdatePreparation.ContentSource source, Runnable closeAction) {
        new Controller(owner, source, closeAction).check();
    }
    private static final class Controller {
        private final dev.copperbench.installer.UpdatePreparation.ContentSource source;
        private final Runnable closeAction;
        private enum Stage { CHECKING, CURRENT, AVAILABLE, PREPARING, READY, FAILED }
        private Stage stage = Stage.CHECKING;
        private Throwable error;
        private final java.util.function.Consumer<String> localeListener;
        private final JLabel title = new JLabel();
        private final JButton details = new JButton();
        private final Window owner;
        private final JDialog dialog;
        private final JTextArea status = new JTextArea(5, 38);
        private final JProgressBar progress = new JProgressBar(0, 100);
        private final JButton action = new JButton(text("检查更新", "Check for updates"));
        private final JButton cancel = new JButton(text("关闭", "Close"));
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private ProductUpdates.Available available;
        private Path prepared;
        private boolean busy;
        Controller(Window owner, dev.copperbench.installer.UpdatePreparation.ContentSource source, Runnable closeAction) {
            this.source = source; this.closeAction = closeAction;
            this.owner = owner; dialog = new JDialog(owner, text("Copperbench 更新", "Copperbench updates")); active = dialog;
            JPanel panel = new JPanel(new BorderLayout(0, 18)); panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
            localeListener = ignored -> SwingUtilities.invokeLater(() -> { if (dialog.isDisplayable()) render(); });
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f)); panel.add(title, BorderLayout.NORTH);
            status.setEditable(false); status.setOpaque(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setFont(UIManager.getFont("Label.font"));
            JPanel middle = new JPanel(new BorderLayout(0, 12)); middle.add(status, BorderLayout.CENTER); middle.add(progress, BorderLayout.SOUTH); panel.add(middle, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); buttons.add(details); buttons.add(cancel); buttons.add(action); panel.add(buttons, BorderLayout.SOUTH);
            action.addActionListener(_ -> { if (prepared != null) install(); else if (available != null) download(); else check(); });
            cancel.addActionListener(_ -> close());
            details.setName("update-error-details");
            details.addActionListener(_ -> {
                if (error == null) return;
                String close = text("关闭", "Close");
                ProductDialogs.show(dialog, UpdateUiMessages.details(error), text("更新错误详情", "Update error details"),
                        JOptionPane.INFORMATION_MESSAGE, new Object[]{close}, close);
            });
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { close(); }
                @Override public void windowClosed(WindowEvent e) { UiLocalePreferences.removeListener(localeListener); }
            });
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); dialog.setContentPane(panel); render(); dialog.pack();
            UiLocalePreferences.addListener(localeListener);
            dialog.setMinimumSize(dialog.getSize()); dialog.setResizable(true); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
        }
        private void check() {
            begin(Stage.CHECKING);
            new SwingWorker<ProductUpdates.Available, Void>() {
                @Override protected ProductUpdates.Available doInBackground() throws Exception {
                    var release = ProductUpdates.check(source); return release.isPresent() ? ProductUpdates.inspect(release.get(), cancelled::get, source) : null;
                }
                @Override protected void done() {
                    finish(); if (cancelled.get()) return;
                    try {
                        available = get();
                        stage = available == null ? Stage.CURRENT : Stage.AVAILABLE; render();
                    } catch (Exception error) { failure(error); }
                }
            }.execute();
        }
        private void download() {
            begin(Stage.PREPARING); progress.setIndeterminate(false); progress.setValue(0);
            new SwingWorker<Path, Integer>() {
                @Override protected Path doInBackground() throws Exception {
                    long[] bytes = {0};
                    return ProductUpdates.prepare(available, cancelled::get, amount -> {
                        bytes[0] += amount; int percent = (int) (100 * bytes[0] / Math.max(1, available.downloadBytes()));
                        if (percent > progress.getValue()) publish(percent);
                    }, source);
                }
                @Override protected void process(java.util.List<Integer> values) { progress.setValue(values.getLast()); }
                @Override protected void done() {
                    finish();
                    try {
                        prepared = get();
                        if (cancelled.get()) { discard(); return; }
                        progress.setValue(100); stage = Stage.READY; render();
                    } catch (Exception error) { if (!cancelled.get()) failure(error); }
                }
            }.execute();
        }
        private void install() {
            try {
                ProductUpdates.launch(prepared); prepared = null; dialog.dispose();
                MCreatorApplication app = owner instanceof MCreator creator ? creator.getApplication()
                        : owner instanceof WorkspaceSelector selector ? selector.getApplication() : null;
                if (closeAction != null) Thread.ofVirtual().name("close-for-update").start(closeAction);
                else if (app != null) Thread.ofVirtual().name("close-for-update").start(app::closeApplication);
            } catch (Exception error) { failure(error); }
        }
        private void begin(Stage next) { stage = next; error = null; busy = true; cancelled.set(false); progress.setIndeterminate(true); render(); }
        private void finish() { busy = false; progress.setIndeterminate(false); render(); }
        private void failure(Exception failure) {
            Throwable cause = failure;
            while ((cause instanceof java.util.concurrent.ExecutionException || cause instanceof java.util.concurrent.CompletionException) && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof CancellationException) return;
            error = cause; stage = Stage.FAILED; render();
            org.apache.logging.log4j.LogManager.getLogger(ProductUpdateDialog.class).warn("Product update failed", cause);
        }
        private void render() {
            dialog.setTitle(text("Copperbench 更新", "Copperbench updates"));
            title.setText(text("当前版本 ", "Current version ") + ProductIdentity.VERSION);
            status.getAccessibleContext().setAccessibleName(text("更新状态", "Update status"));
            progress.getAccessibleContext().setAccessibleName(text("更新进度", "Update progress"));
            details.setText(text("错误详情", "Error details")); details.setVisible(error != null);
            cancel.setText(busy ? text("取消", "Cancel") : text("关闭", "Close")); action.setEnabled(!busy);
            status.setText(switch (stage) {
                case CHECKING -> text("正在检查稳定版更新…", "Checking for stable updates…");
                case CURRENT -> text("已是最新稳定版。", "You have the latest stable version.");
                case AVAILABLE -> text("发现新版本 ", "Version available: ") + available.release().version() + "\n"
                        + text("需要下载 ", "Download size: ") + size(available.downloadBytes()) + "\n"
                        + text("只下载变化的组件。安装成功后自动清理旧程序。", "Only changed components are downloaded. Previous application files are removed after installation.");
                case PREPARING -> text("正在准备更新…", "Preparing update…");
                case READY -> text("更新已下载并校验。保存工作后即可安装并重启。", "The update has been downloaded and verified. Save your work to install and restart.");
                case FAILED -> UpdateUiMessages.errorSummary(error, UiLocalePreferences.read());
            });
            action.setText(stage == Stage.FAILED
                    ? prepared != null ? text("重试安装", "Retry installation") : available != null ? text("重试下载", "Retry download") : text("重试检查", "Retry check")
                    : prepared != null ? text("安装并重启", "Install and restart") : available != null ? text("下载更新", "Download update") : text("重新检查", "Check again"));
            if (dialog.isDisplayable()) { dialog.pack(); dialog.setLocationRelativeTo(owner); }
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
