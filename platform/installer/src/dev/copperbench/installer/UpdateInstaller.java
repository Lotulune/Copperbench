/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent process, running from the prepared runtime so Windows can replace the installed runtime. */
public final class UpdateInstaller {
    public static void main(String[] args) throws Exception {
        // This process writes update.log, which the application later reads as UTF-8.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        Path request = Path.of(args[0]).toAbsolutePath().normalize();
        Properties p = InstallerMain.loadProperties(request); Path scratch = request.getParent();
        InstallTrace trace = new InstallTrace(scratch.resolve("update-steps.log"));
        trace.event("update-worker-start", "version", p.getProperty("version"), "scope", p.getProperty("scope"),
                "target", p.getProperty("target"), "parentPid", p.getProperty("parentPid"));
        Path target = Path.of(p.getProperty("target")); String language = p.getProperty("language");
        AtomicBoolean cancel = new AtomicBoolean();
        var view = new java.util.concurrent.atomic.AtomicReference<UpdateInstallerWindow>();
        SwingUtilities.invokeAndWait(() -> {
            view.set(UpdateInstallerWindow.create(language, p.getProperty("theme", "dark"), p.getProperty("version", ""), cancel));
            view.get().setVisible(true);
        });
        UpdateInstallerWindow frame = view.get();
        Properties result = new Properties(); result.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        try {
            long deadline = System.nanoTime() + java.time.Duration.ofMinutes(10).toNanos();
            Optional<ProcessHandle> parent = ProcessHandle.of(Long.parseLong(p.getProperty("parentPid")));
            while (parent.map(ProcessHandle::isAlive).orElse(false) || running(target)) {
                if (cancel.get()) throw new java.util.concurrent.CancellationException();
                if (System.nanoTime() > deadline) throw new IOException("The application is still running. Update stopped.");
                Thread.sleep(200);
            }
            trace.event("application-stopped");
            if (cancel.get()) throw new java.util.concurrent.CancellationException();
            SwingUtilities.invokeAndWait(frame::committing);
            Path payload = scratch.resolve("payload");
            InstallEngine.Plan plan = new InstallEngine.Plan(payload, scratch.resolve("payload.tsv"), payload.resolve("copperbench-setup.jar"), target,
                    p.getProperty("version"), p.getProperty("language"), p.getProperty("theme"), p.getProperty("scope"),
                    Boolean.parseBoolean(p.getProperty("desktop", "true")), Boolean.parseBoolean(p.getProperty("legacy")));
            var installed = InstallerMain.execute(plan, payload.resolve("copperbench-update-host.exe"), Boolean.parseBoolean(p.getProperty("deb")), cancel,
                    progress -> { trace.phase(progress); SwingUtilities.invokeLater(() -> frame.progress(progress)); }, true, trace);
            result.setProperty("status", "success");
            if (installed.previousVersion() != null) {
                result.setProperty("cleanupPending", installed.previousVersion().toString());
                SwingUtilities.invokeAndWait(() -> frame.cleanupWarning(installed.previousVersion()));
            }
            InstallerMain.saveProperties(scratch.resolve("finished.properties"), result);
            trace.event("application-relaunch", "target", target.toString());
            PlatformInstall.launchApplication(target);
            trace.event("update-finished", "status", "success");
        } catch (Exception error) {
            trace.failure("update", error);
            result.setProperty("status", error instanceof java.util.concurrent.CancellationException ? "cancelled" : "failed");
            result.setProperty("message", error.toString()); InstallerMain.saveProperties(scratch.resolve("finished.properties"), result);
            if (!(error instanceof java.util.concurrent.CancellationException)) SwingUtilities.invokeAndWait(() -> frame.failure(error));
        } finally { SwingUtilities.invokeAndWait(frame::dispose); }
        System.exit("success".equals(result.getProperty("status")) ? 0 : 1);
    }
    private static boolean running(Path target) throws IOException {
        try { PlatformInstall.ensureStopped(target); return false; }
        catch (IOException busy) { return true; }
    }
}
