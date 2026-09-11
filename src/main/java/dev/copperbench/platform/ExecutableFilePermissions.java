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
import java.util.HashSet;
import java.util.Set;

/** Portable helper for files that must be executable on POSIX hosts but need no special handling on Windows. */
public final class ExecutableFilePermissions {
	private ExecutableFilePermissions() {
	}

	public static void ensureOwnerExecutable(Path file) throws IOException {
		if (!Files.isRegularFile(file)) throw new IOException("Executable file does not exist: " + file);
		if (!posixSupported(file)) return;
		Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(file));
		permissions.add(PosixFilePermission.OWNER_EXECUTE);
		Files.setPosixFilePermissions(file, permissions);
	}

	public static boolean posixSupported(Path path) {
		return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
	}
}
