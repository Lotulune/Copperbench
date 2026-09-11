/* Copyright (C) 2026 Copperbench contributors. SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.platform;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/** Imports pre-XDG Linux preferences once, preserving the original and any active configuration. */
public final class LegacyPreferencesMigration {
    private LegacyPreferencesMigration() {}

    public static void migrateCurrent() throws IOException {
        migrate(RuntimePlatform.current(), System.getenv(), Path.of(System.getProperty("user.home", ".")));
    }

    public static void migrate(RuntimePlatform platform, Map<String, String> environment, Path home) throws IOException {
        if (platform.operatingSystem() != RuntimePlatform.OperatingSystem.LINUX
                || UserDataPaths.homeOverride(environment) != null) return;
        Path config = UserDataPaths.resolve(platform, environment, home).config();
        List<String> formats = List.of("userpreferences", "preferences");
        // Either active format is authoritative, including a dangling link.
        if (formats.stream().anyMatch(name -> Files.exists(config.resolve(name), LinkOption.NOFOLLOW_LINKS))) return;
        Path legacy = home.toAbsolutePath().normalize().resolve(".copperbench");
        for (String name : formats) {
            Path source = legacy.resolve(name);
            if (!Files.isRegularFile(source)) continue;
            PrivatePathPermissions.createPrivateDirectory(config);
            Path temporary = Files.createTempFile(config, ".legacy-preferences-", ".tmp");
            try {
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                PrivatePathPermissions.makePrivateFile(temporary);
                Files.move(temporary, config.resolve(name));
            } catch (FileAlreadyExistsException ignored) {
                // Another startup already supplied the active file; never replace it.
            } finally {
                Files.deleteIfExists(temporary);
            }
            return;
        }
    }
}
