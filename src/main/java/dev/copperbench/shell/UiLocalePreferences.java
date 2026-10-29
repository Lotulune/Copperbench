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
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** Product UI preference, independent of Chromium's temporary session cache. */
public final class UiLocalePreferences {
    // Capture before L10N changes Java's default locale for legacy dialogs.
    private static final Locale SYSTEM_LOCALE = Locale.getDefault();
    private static final CopyOnWriteArraySet<Consumer<String>> LISTENERS = new CopyOnWriteArraySet<>();
    private UiLocalePreferences() {}

    public static String read() {
        return read(UserFolderManager.getFileFromConfigFolder("ui-language.txt").toPath());
    }

    public static String read(Path path) {
        return read(path, installationPreference(), SYSTEM_LOCALE);
    }

    public static String read(Path path, Path installationPreference, Locale systemLocale) {
        String saved = readSupported(path);
        if (saved != null) return saved;
        String installed = readSupported(installationPreference);
        return installed != null ? installed : ("zh".equals(systemLocale.getLanguage()) ? "zh" : "en");
    }

    private static String readSupported(Path path) {
        if (path == null) return null;
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            return "en".equals(value) || "zh".equals(value) ? value : null;
        } catch (IOException | SecurityException ignored) { return null; }
    }

    private static Path installationPreference() {
        // Resolve next to the installed lib/ directory, independent of shortcut working directories.
        try {
            Path code = Path.of(UiLocalePreferences.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(code) && code.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe"))
                return code.getParent().resolve("install-language.txt");
            if (Files.isRegularFile(code) && "lib".equals(code.getParent().getFileName().toString()))
                return code.getParent().getParent().resolve("install-language.txt");
        } catch (java.net.URISyntaxException | SecurityException ignored) { }
        return null;
    }

    public static Locale javaLocale() {
        return "zh".equals(read()) ? Locale.SIMPLIFIED_CHINESE : Locale.US;
    }

    public static String text(String chinese, String english) {
        return "zh".equals(read()) ? chinese : english;
    }

    public static void save(String locale) throws IOException {
        save(UserFolderManager.getFileFromConfigFolder("ui-language.txt").toPath(), locale);
        LISTENERS.forEach(listener -> listener.accept(locale));
    }

    public static void addListener(Consumer<String> listener) { LISTENERS.add(listener); }
    public static void removeListener(Consumer<String> listener) { LISTENERS.remove(listener); }

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
