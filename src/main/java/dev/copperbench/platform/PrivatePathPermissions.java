/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Applies owner-only permissions when the backing filesystem exposes POSIX attributes. */
public final class PrivatePathPermissions {
    private static final Set<PosixFilePermission> DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private PrivatePathPermissions() {
    }

    public static void createPrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        apply(directory, DIRECTORY);
    }

    public static void makePrivateFile(Path file) throws IOException {
        apply(file, FILE);
    }

    public static boolean posixSupported(Path path) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }

    private static void apply(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (!posixSupported(path)) return;
        Files.setPosixFilePermissions(path, permissions);
    }
}
