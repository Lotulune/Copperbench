/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Resolves the development-test user guide from a source tree or Windows export. */
public final class UserGuideLocator {

	private UserGuideLocator() {
	}

	public static Optional<Path> resolve(Path workingDirectory) {
		Path root = workingDirectory == null ? Path.of(".") : workingDirectory;
		List<Path> candidates = List.of(root.resolve("docs/user/README.md"), root.resolve("user/README.md"));
		for (Path candidate : candidates) {
			if (Files.isRegularFile(candidate))
				return Optional.of(candidate.toAbsolutePath().normalize());
		}
		return Optional.empty();
	}
}
