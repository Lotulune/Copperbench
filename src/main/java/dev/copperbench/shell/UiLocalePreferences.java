/*
 * Copyright (C) 2026 Copperbench contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.copperbench.shell;

import net.mcreator.io.UserFolderManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** Product UI preference, independent of Chromium's temporary session cache. */
public final class UiLocalePreferences {
    private UiLocalePreferences() {}

    public static String read() {
        return read(UserFolderManager.getFileFromConfigFolder("ui-language.txt").toPath());
    }

    public static String read(Path path) {
        try { return "en".equals(Files.readString(path, StandardCharsets.UTF_8).trim()) ? "en" : "zh"; }
        catch (IOException | SecurityException ignored) { return "zh"; }
    }

    public static void save(String locale) throws IOException {
        save(UserFolderManager.getFileFromConfigFolder("ui-language.txt").toPath(), locale);
    }

    public static void save(Path path, String locale) throws IOException {
        if (!"zh".equals(locale) && !"en".equals(locale)) throw new IllegalArgumentException("Unsupported UI locale");
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "ui-language-", ".tmp");
        try {
            Files.writeString(temporary, locale + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
