/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent process, running from the prepared runtime so Windows can replace the installed runtime. */
public final class UpdateInstaller {
    public static void main(String[] args) throws Exception {
        Path request = Path.of(args[0]).toAbsolutePath().normalize();
        Properties p = InstallerMain.loadProperties(request); Path scratch = request.getParent();
        Path target = Path.of(p.getProperty("target")); boolean zh = p.getProperty("language").equals("zh");
        AtomicBoolean cancel = new AtomicBoolean();
        JFrame frame = new JFrame(zh ? "Copperbench 更新" : "Copperbench update");
        JLabel status = new JLabel(zh ? "请保存工作并关闭 Copperbench，随后自动升级。" : "Save your work and close Copperbench to install the update.");
        JButton stop = new JButton(zh ? "取消更新" : "Cancel update"); stop.addActionListener(e -> cancel.set(true));
        SwingUtilities.invokeAndWait(() -> {
            JPanel content = new JPanel(new BorderLayout(0, 20)); content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
            content.add(status, BorderLayout.CENTER); content.add(stop, BorderLayout.SOUTH); frame.setContentPane(content);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
        Properties result = new Properties(); result.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        try {
            long deadline = System.nanoTime() + java.time.Duration.ofMinutes(10).toNanos();
            Optional<ProcessHandle> parent = ProcessHandle.of(Long.parseLong(p.getProperty("parentPid")));
            while (parent.map(ProcessHandle::isAlive).orElse(false) || running(target)) {
                if (cancel.get()) throw new java.util.concurrent.CancellationException();
                if (System.nanoTime() > deadline) throw new IOException(zh ? "应用仍在运行，更新已停止。" : "The application is still running. Update stopped.");
                Thread.sleep(200);
            }
            SwingUtilities.invokeAndWait(() -> stop.setEnabled(false));
            Path payload = scratch.resolve("payload");
            InstallEngine.Plan plan = new InstallEngine.Plan(payload, scratch.resolve("payload.tsv"), payload.resolve("copperbench-setup.jar"), target,
                    p.getProperty("version"), p.getProperty("language"), p.getProperty("theme"), p.getProperty("scope"),
                    Boolean.parseBoolean(p.getProperty("desktop", "true")), Boolean.parseBoolean(p.getProperty("legacy")));
            var installed = InstallerMain.execute(plan, payload.resolve("copperbench-update-host.exe"), Boolean.parseBoolean(p.getProperty("deb")), cancel,
                    progress -> SwingUtilities.invokeLater(() -> status.setText((zh ? "正在升级… " : "Installing… ")
                            + (progress.total() == 0 ? 0 : progress.completed() * 100 / progress.total()) + "%")));
            result.setProperty("status", "success");
            if (installed.previousVersion() != null) {
                result.setProperty("cleanupPending", installed.previousVersion().toString());
                SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(frame,
                        (zh ? "升级完成，但临时文件未能清理：\n" : "Update installed, but temporary files could not be removed:\n") + installed.previousVersion()));
            }
            InstallerMain.saveProperties(scratch.resolve("finished.properties"), result);
            new ProcessBuilder(target.resolve(PlatformInstall.WINDOWS ? "copperbench.exe" : "copperbench.sh").toString()).directory(target.toFile()).start();
        } catch (Exception error) {
            result.setProperty("status", error instanceof java.util.concurrent.CancellationException ? "cancelled" : "failed");
            result.setProperty("message", error.toString()); InstallerMain.saveProperties(scratch.resolve("finished.properties"), result);
            if (!(error instanceof java.util.concurrent.CancellationException)) SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(frame,
                    (zh ? "更新未完成。\n" : "Update did not complete.\n") + error.getMessage(), "Copperbench", JOptionPane.ERROR_MESSAGE));
        } finally { SwingUtilities.invokeAndWait(frame::dispose); }
        System.exit(0);
    }
    private static boolean running(Path target) throws IOException {
        try { PlatformInstall.ensureStopped(target); return false; }
        catch (IOException busy) { return true; }
    }
}
