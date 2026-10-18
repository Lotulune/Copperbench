/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.nio.file.*;
import java.util.*;

/** Full payload file-lifecycle rehearsal; deliberately does not write OS registration or launch the app. */
public final class InstallRehearsal {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("payload manifest setup.jar output-directory version");
        Path output = Path.of(args[3]).toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IllegalArgumentException("Rehearsal output must be a new directory");
        Files.createDirectories(output);
        Path target = output.resolve("安装路径 with spaces"), project = output.resolve("user-project/workspace.mcreator");
        Files.createDirectories(project.getParent()); Files.writeString(project, "workspace must survive");
        InstallEngine.Plan plan = new InstallEngine.Plan(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), target, args[4], "zh", "dark", "user", false, false);
        long start = System.nanoTime();
        InstallEngine engine = new InstallEngine();
        var first = engine.install(plan, () -> false, ignored -> {}, ignored -> {});
        if (first.previousVersion() != null) throw new AssertionError("Fresh install unexpectedly replaced something");
        Files.writeString(target.resolve("personal-note.txt"), "keep me");
        var upgrade = engine.install(plan, () -> false, ignored -> {}, ignored -> {});
        if (upgrade.previousVersion() != null) throw new AssertionError("Upgrade left previous application files behind");
        try (var siblings = Files.list(output)) {
            if (siblings.anyMatch(path -> path.getFileName().toString().startsWith("Copperbench-previous-"))) throw new AssertionError("Upgrade retained an old version directory");
        }
        if (!Files.readString(target.resolve("personal-note.txt")).equals("keep me")) throw new AssertionError("Upgrade lost unmanaged data");
        InstallEngine.uninstall(target);
        if (Files.exists(target.resolve("copperbench.exe")) || !Files.readString(target.resolve("personal-note.txt")).equals("keep me")
                || !Files.readString(project).equals("workspace must survive")) throw new AssertionError("Uninstall data preservation failed");
        String receipt = "{\n  \"result\": \"passed\",\n  \"scope\": \"full-payload file install, upgrade, uninstall and data preservation\",\n"
                + "  \"osRegistrationTested\": false,\n  \"applicationLaunched\": false,\n  \"durationSeconds\": "
                + ((System.nanoTime() - start) / 1_000_000_000.0) + "\n}\n";
        Files.writeString(output.resolve("result.json"), receipt);
        System.out.println(receipt);
    }
}
