/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import net.mcreator.io.UserFolderManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Per-user first launch state; independent of release number and workspace creation. */
public final class FirstRunOnboarding {
    private FirstRunOnboarding() {}
    private static Path marker() { return UserFolderManager.getFileFromConfigFolder("first-run-tour.txt").toPath(); }
    public static void initialize() throws IOException {
        initialize(marker(), Files.exists(UserFolderManager.getFileFromConfigFolder("preferences").toPath())
                || Files.exists(UserFolderManager.getFileFromConfigFolder("userpreferences").toPath())
                || Files.exists(UserFolderManager.getFileFromConfigFolder("preferences.json").toPath())
                || Files.exists(UserFolderManager.getFileFromConfigFolder("ui-language.txt").toPath())
                || Files.exists(UserFolderManager.getFileFromUserFolder("recentworkspaces").toPath()));
    }
    static void initialize(Path marker, boolean existingUser) throws IOException {
        Files.createDirectories(marker.toAbsolutePath().getParent());
        try { Files.writeString(marker, existingUser ? "seen\n" : "pending\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException ignored) { }
    }
    public static boolean claim() {
        try { return claim(marker()); }
        catch (IOException error) {
            org.apache.logging.log4j.LogManager.getLogger(FirstRunOnboarding.class).warn("Could not save first-run state", error);
            return false;
        }
    }
    static synchronized boolean claim(Path marker) throws IOException {
        if (!Files.exists(marker) || !Files.readString(marker, StandardCharsets.UTF_8).trim().equals("pending")) return false;
        Files.writeString(marker, "seen\n", StandardCharsets.UTF_8);
        return true;
    }
}
