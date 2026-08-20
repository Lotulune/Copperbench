/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Deterministic SHA-256 of a workspace tree, excluding Git metadata and OS junk. */
public final class WorkspaceTreeHasher {

	private WorkspaceTreeHasher() {
	}

	public static String hash(Path root) throws IOException {
		Path real = root.toRealPath();
		MessageDigest digest = sha256();
		List<Path> files;
		try (Stream<Path> stream = Files.walk(real)) {
			files = stream.filter(Files::isRegularFile).filter(path -> !excluded(real, path))
					.sorted(Comparator.comparing(path -> normalize(real.relativize(path)))).toList();
		}
		for (Path file : files) {
			digest.update(normalize(real.relativize(file)).getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			try (InputStream input = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = input.read(buffer)) != -1)
					digest.update(buffer, 0, read);
			}
			digest.update((byte) 0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	static boolean excluded(Path root, Path path) {
		Path relative = root.relativize(path);
		for (Path part : relative) {
			String name = part.toString();
			if (name.equals(".git") || name.equalsIgnoreCase(".DS_Store") || name.equals("Thumbs.db")
					|| name.toLowerCase(Locale.ROOT).endsWith(".tmp"))
				return true;
		}
		return false;
	}

	static String normalize(Path relative) {
		return relative.toString().replace('\\', '/');
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("JVM must provide SHA-256", exception);
		}
	}
}
