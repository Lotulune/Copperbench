/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Detects an already-materialized MCreator plugin workspace so first-party projection does not overwrite it. */
public final class PluginWorkspaceLayout {

	private PluginWorkspaceLayout() {
	}

	public static boolean present(Path root) throws IOException {
		if (root == null || !Files.isDirectory(root))
			return false;
		boolean hasWorkspaceFile;
		try (Stream<Path> files = Files.list(root)) {
			hasWorkspaceFile = files.anyMatch(path -> path.getFileName().toString().endsWith(".mcreator"));
		}
		if (!hasWorkspaceFile)
			return false;
		Path src = root.resolve("src");
		if (!Files.isDirectory(src))
			return false;
		try (Stream<Path> files = Files.walk(src)) {
			return files.anyMatch(Files::isRegularFile);
		}
	}

	public static List<String> relativeSourcePaths(Path root) throws IOException {
		Path src = root.resolve("src");
		if (!Files.isDirectory(src))
			return List.of();
		try (Stream<Path> files = Files.walk(src)) {
			return files.filter(Files::isRegularFile)
					.map(path -> root.relativize(path).toString().replace('\\', '/'))
					.sorted()
					.toList();
		}
	}
}
