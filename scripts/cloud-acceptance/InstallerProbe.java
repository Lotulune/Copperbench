/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test driver compiled against the published setup JAR; never replaces installer implementation. */
public final class InstallerProbe {
    private static Set<Path> previousDirectories(Path target) throws Exception {
        Path parent = target.getParent();
        if (!Files.isDirectory(parent)) return Set.of();
        try (var children = Files.list(parent)) {
            return new HashSet<>(children.filter(path -> path.getFileName().toString().startsWith("Copperbench-previous-")).toList());
        }
    }
    private static String quoted(Path path) {
        return path == null ? "null" : "\"" + path.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("uninstall")) {
            if (PlatformInstall.WINDOWS) throw new IllegalArgumentException("Use the released Windows uninstaller");
            Path target = Path.of(args[1]).toAbsolutePath().normalize();
            PlatformInstall.ensureStopped(target);
            InstallEngine.uninstall(target);
            PlatformInstall.unregisterLinux(target);
            if (PlatformInstall.detect() != null) throw new AssertionError("Registration survived uninstall");
            System.out.println("{\"status\":\"passed\",\"operation\":\"uninstall\"}");
            return;
        }
        if (args.length != 8 || !args[0].equals("install"))
            throw new IllegalArgumentException("install payload manifest setup.jar target version registrar fresh|upgrade");
        Path target = Path.of(args[4]).toAbsolutePath().normalize();
        boolean upgrade = args[7].equals("upgrade");
        var existing = PlatformInstall.detect();
        if (upgrade ? existing == null || !existing.path().equals(target) : existing != null || Files.exists(target))
            throw new AssertionError("Unexpected existing installation");
        Set<Path> previousDirectories = previousDirectories(target);
        var plan = new InstallEngine.Plan(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), target,
                args[5], "zh", "dark", "user", true, false);
        String[] phase = {""};
        var result = InstallerMain.execute(plan, Path.of(args[6]), false, new AtomicBoolean(), state -> {
            if (!state.phase().equals(phase[0])) {
                phase[0] = state.phase();
                System.err.println("Installer phase: " + phase[0]);
            }
        });
        if (result.previousVersion() != null) throw new AssertionError("Temporary upgrade backup was not cleaned up");
        Set<Path> remaining = previousDirectories(target);
        remaining.removeAll(previousDirectories);
        if (!remaining.isEmpty()) throw new AssertionError("Installer left temporary previous-version directories: " + remaining);
        if (!InstallEngine.readReceipt(target).getProperty("version").equals(args[5]))
            throw new AssertionError("Installed version does not match package");
        existing = PlatformInstall.detect();
        if (existing == null || !existing.path().equals(target) || !existing.scope().equals("user"))
            throw new AssertionError("OS registration does not identify the installed target");
        System.out.println("{\"status\":\"passed\",\"operation\":\"install\",\"target\":" + quoted(target)
                + ",\"backup\":" + quoted(result.previousVersion()) + ",\"backupCleanup\":\"confirmed\"}");
    }
}
