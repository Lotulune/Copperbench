/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class InstallerMain {
    static Path ownJar() {
        try { return Path.of(InstallerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath(); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    public static void main(String[] args) throws Exception {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Invalid installer argument");
            String key = args[i].substring(2);
            options.put(key, i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true");
        }
        if (options.containsKey("worker")) { worker(Path.of(options.get("worker"))); return; }
        if (options.containsKey("preview")) { InstallerWindow.preview(Path.of(options.get("preview"))); return; }
        SwingUtilities.invokeLater(() -> InstallerWindow.create(options).setVisible(true));
    }
    static InstallEngine.Result execute(InstallEngine.Plan plan, Path registrar, boolean deb, AtomicBoolean cancel,
                                        Consumer<InstallEngine.Progress> progress) throws IOException {
        return execute(plan, registrar, deb, cancel, progress, false, InstallTrace.NONE);
    }
    static InstallEngine.Result execute(InstallEngine.Plan plan, Path registrar, boolean deb, AtomicBoolean cancel,
                                        Consumer<InstallEngine.Progress> progress, boolean componentUpdate, InstallTrace trace) throws IOException {
        PlatformInstall.checkPlatform();
        PlatformInstall.ensureStopped(plan.target());
        if (PlatformInstall.WINDOWS && plan.scope().equals("system")) return elevated(plan, registrar, cancel, progress, componentUpdate, trace);
        if (deb) return updateDeb(plan, cancel, progress);
        Path scratch = Files.createTempDirectory("copperbench-setup-");
        try { return new InstallEngine().install(plan, cancel::get, progress, installed -> PlatformInstall.register(installed, registrar, scratch, componentUpdate, trace)); }
        finally { deleteScratch(scratch); }
    }
    private static InstallEngine.Result elevated(InstallEngine.Plan plan, Path registrar, AtomicBoolean cancel,
                                                 Consumer<InstallEngine.Progress> progress, boolean componentUpdate, InstallTrace trace) throws IOException {
        Path scratch = Files.createTempDirectory("copperbench-upgrade-");
        Properties request = new Properties();
        request.setProperty("payload", plan.payload().toString()); request.setProperty("manifest", plan.manifest().toString());
        request.setProperty("jar", plan.installerJar().toString()); request.setProperty("target", plan.target().toString());
        request.setProperty("version", plan.version()); request.setProperty("language", plan.language()); request.setProperty("theme", plan.theme());
        request.setProperty("desktop", Boolean.toString(plan.desktop())); request.setProperty("legacy", Boolean.toString(plan.legacy()));
        request.setProperty("registrar", registrar.toString());
        request.setProperty("componentUpdate", Boolean.toString(componentUpdate));
        Path requestFile = scratch.resolve("request.properties");
        saveProperties(requestFile, request);
        Path ini = scratch.resolve("elevate.ini");
        PlatformInstall.writeIni(ini, Map.of("Java", plan.payload().resolve("jdk/bin/javaw.exe").toString(), "Jar", plan.installerJar().toString(), "Request", requestFile.toString()));
        try {
            trace.executable(registrar);
            trace.event("helper-request", "mode", "/ELEVATE", "executable", registrar.toString());
            Process process = new ProcessBuilder(registrar.toString(), "/ELEVATE=" + ini).start();
            trace.event("helper-started", "mode", "/ELEVATE", "childPid", Long.toString(process.pid()));
            while (process.isAlive()) {
                if (cancel.get()) Files.writeString(scratch.resolve("cancel"), "cancel");
                Path stateFile = scratch.resolve("progress.properties");
                if (Files.exists(stateFile)) {
                    try {
                        Properties state = loadProperties(stateFile);
                        progress.accept(new InstallEngine.Progress(state.getProperty("phase", "copy"), Long.parseLong(state.getProperty("done", "0")), Long.parseLong(state.getProperty("total", "1"))));
                    } catch (NoSuchFileException | NumberFormatException ignored) { /* Retry the next progress publication. */ }
                }
                try { Thread.sleep(150); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("Installation was interrupted", error); }
            }
            trace.event("helper-exited", "mode", "/ELEVATE", "exitCode", Integer.toString(process.exitValue()));
            Path resultFile = scratch.resolve("result.properties");
            if (!Files.exists(resultFile)) throw new IOException("System authorization was cancelled or the upgrade worker could not start.");
            Properties result = loadProperties(resultFile);
            if (!result.getProperty("status", "error").equals("success")) throw new IOException(result.getProperty("message", "System installation failed."));
            return new InstallEngine.Result(plan.target(), result.containsKey("backup") ? Path.of(result.getProperty("backup")) : null);
        } finally { trace.relayWorkerLog(scratch.resolve("worker-steps.log")); deleteScratch(scratch); }
    }
    private static void worker(Path requestFile) throws IOException {
        Properties p = loadProperties(requestFile);
        Path scratch = requestFile.toAbsolutePath().getParent();
        boolean componentUpdate = Boolean.parseBoolean(p.getProperty("componentUpdate", "false"));
        InstallTrace trace = componentUpdate ? new InstallTrace(scratch.resolve("worker-steps.log")) : InstallTrace.NONE;
        trace.event("elevated-worker-start");
        Properties result = new Properties();
        try {
            InstallEngine.Plan plan = new InstallEngine.Plan(Path.of(p.getProperty("payload")), Path.of(p.getProperty("manifest")), Path.of(p.getProperty("jar")),
                    Path.of(p.getProperty("target")), p.getProperty("version"), p.getProperty("language"), p.getProperty("theme"), "system", Boolean.parseBoolean(p.getProperty("desktop")), Boolean.parseBoolean(p.getProperty("legacy")));
            PlatformInstall.Existing existing = PlatformInstall.detect();
            if (existing == null || !existing.scope().equals("system") || !existing.path().equals(plan.target()))
                throw new IOException("The existing system installation changed. Restart setup.");
            PlatformInstall.ensureStopped(plan.target());
            long[] last = {0};
            InstallEngine.Result installed = new InstallEngine().install(plan, () -> Files.exists(scratch.resolve("cancel")), state -> {
                trace.phase(state);
                if (System.nanoTime() - last[0] < 150_000_000 && state.phase().equals("copy")) return;
                last[0] = System.nanoTime();
                Properties progress = new Properties();
                progress.setProperty("phase", state.phase()); progress.setProperty("done", Long.toString(state.completed())); progress.setProperty("total", Long.toString(state.total()));
                try { saveProperties(scratch.resolve("progress.properties"), progress); } catch (IOException error) { throw new UncheckedIOException(error); }
            }, installedPlan -> PlatformInstall.register(installedPlan, Path.of(p.getProperty("registrar")), scratch, componentUpdate, trace));
            result.setProperty("status", "success");
            trace.event("elevated-worker-finished", "status", "success");
            if (installed.previousVersion() != null) result.setProperty("backup", installed.previousVersion().toString());
        } catch (Exception error) {
            trace.failure("elevated-worker", error);
            result.setProperty("status", "error"); result.setProperty("message", error.toString());
        }
        saveProperties(scratch.resolve("result.properties"), result);
    }
    private static InstallEngine.Result updateDeb(InstallEngine.Plan plan, AtomicBoolean cancel, Consumer<InstallEngine.Progress> progress) throws IOException {
        Path scratch = Files.createTempDirectory("copperbench-deb-");
        try {
            Path root = scratch.resolve("package"), app = root.resolve("opt/copperbench");
            InstallEngine.Plan staged = new InstallEngine.Plan(plan.payload(), plan.manifest(), plan.installerJar(), app, plan.version(), plan.language(), plan.theme(), "system", false, false);
            new InstallEngine().install(staged, cancel::get, progress, ignored -> { });
            Properties receipt = loadProperties(app.resolve(InstallEngine.RECEIPT));
            receipt.setProperty("target", "/opt/copperbench"); saveProperties(app.resolve(InstallEngine.RECEIPT), receipt);
            Files.createDirectories(root.resolve("DEBIAN"));
            Files.writeString(root.resolve("DEBIAN/control"), "Package: copperbench\nVersion: " + plan.version() + "\nArchitecture: amd64\nSection: devel\nPriority: optional\nMaintainer: Copperbench Contributors\nDescription: Visual Minecraft mod creation and agent collaboration\n", StandardCharsets.UTF_8);
            Files.createDirectories(root.resolve("usr/bin"));
            Files.writeString(root.resolve("usr/bin/copperbench"), "#!/bin/sh\nexec /opt/copperbench/copperbench.sh \"$@\"\n", StandardCharsets.UTF_8);
            if (!root.resolve("usr/bin/copperbench").toFile().setExecutable(true, false)) throw new IOException("Could not prepare the system launcher");
            Files.createDirectories(root.resolve("usr/share/applications"));
            Files.writeString(root.resolve("usr/share/applications/copperbench.desktop"), "[Desktop Entry]\nType=Application\nName=Copperbench\nComment=Minecraft mod creation\nComment[zh_CN]=Minecraft 模组创作\nExec=/usr/bin/copperbench %f\nIcon=/opt/copperbench/icon.png\nTerminal=false\nCategories=Development;IDE;\n", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("usr/share/applications/copperbench-uninstall.desktop"), "[Desktop Entry]\nType=Application\nName=Uninstall Copperbench\nName[zh_CN]=卸载 Copperbench\nExec=/opt/copperbench/jdk/bin/java -jar /opt/copperbench/copperbench-setup.jar --uninstall\nIcon=/opt/copperbench/icon.png\nTerminal=false\nCategories=Development;\n", StandardCharsets.UTF_8);
            if (cancel.get()) throw new java.util.concurrent.CancellationException();
            progress.accept(new InstallEngine.Progress("package", 0, 1));
            Path deb = scratch.resolve("copperbench.deb");
            PlatformInstall.Command packaged = PlatformInstall.command(List.of("/usr/bin/dpkg-deb", "--build", "--root-owner-group", "-Znone", root.toString(), deb.toString()));
            if (packaged.code() != 0) throw new IOException(packaged.output());
            if (cancel.get()) throw new java.util.concurrent.CancellationException();
            progress.accept(new InstallEngine.Progress("authorize", 0, 1));
            PlatformInstall.Command installed = PlatformInstall.command(List.of("/usr/bin/pkexec", "/usr/bin/dpkg", "--install", deb.toString()));
            if (installed.code() != 0) throw new IOException("The system package manager could not complete the upgrade.\n" + installed.output());
            return new InstallEngine.Result(Path.of("/opt/copperbench"), null);
        } finally { deleteScratch(scratch); }
    }
    static Properties loadProperties(Path path) throws IOException {
        Properties p = new Properties(); try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); } return p;
    }
    static void saveProperties(Path path, Properties p) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "setup-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { p.store(writer, "Copperbench setup"); }
            var permissions = Files.getFileAttributeView(path, PosixFileAttributeView.class);
            if (permissions != null && Files.exists(path))
                Files.setPosixFilePermissions(temporary, permissions.readAttributes().permissions());
            for (int attempt = 0; ; attempt++) {
                try {
                    try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
                    break;
                } catch (AccessDeniedException locked) {
                    // Windows readers/scanners can briefly deny replacement of a progress file.
                    // Keep the previous complete value while retrying; permanent failures stay visible.
                    if (!PlatformInstall.WINDOWS || attempt >= 9) throw locked;
                    try { Thread.sleep(100); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        IOException failure = new IOException("Installation was interrupted", interrupted);
                        failure.addSuppressed(locked); throw failure;
                    }
                }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    static void deleteScratch(Path scratch) throws IOException {
        try (var paths = Files.walk(scratch)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
}
