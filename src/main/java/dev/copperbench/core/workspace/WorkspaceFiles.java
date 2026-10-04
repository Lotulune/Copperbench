/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Filesystem boundaries shared by workspace exports and copy-only migrations. */
public final class WorkspaceFiles {

	private WorkspaceFiles() {
	}

	/** Resolves ordinary filesystem aliases while refusing links and redirected existing ancestors. */
	public static Path canonicalPath(Path path) throws IOException {
		Path absolute = path.toAbsolutePath().normalize();
		Path nearestExisting = null;
		for (Path current = absolute; current != null; current = current.getParent()) {
			BasicFileAttributes attributes;
			try {
				attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			} catch (NoSuchFileException exception) {
				continue;
			}
			if (attributes.isSymbolicLink() || attributes.isOther()
					|| !current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS)))
				throw new IOException("Workspace paths may not use symbolic links or redirected locations: " + current);
			if (nearestExisting == null) nearestExisting = current;
		}
		return nearestExisting == null ? absolute : nearestExisting.toRealPath(LinkOption.NOFOLLOW_LINKS)
				.resolve(nearestExisting.relativize(absolute)).normalize();
	}

	public static Path requireDirectory(Path directory) throws IOException {
		Path canonical = canonicalPath(directory);
		if (!Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS))
			throw new IOException("Workspace directory is unavailable: " + directory);
		return canonical;
	}

	/** Checks every existing ancestor, including when the target's parent does not exist yet. */
	public static Path requireWithin(Path root, Path path) throws IOException {
		Path canonicalRoot = canonicalPath(root);
		Path canonical = canonicalPath(path);
		if (!canonical.startsWith(canonicalRoot))
			throw new IOException("Path is outside the workspace: " + path);
		return canonical;
	}

	public static Path requireRegularFile(Path root, Path file) throws IOException {
		Path canonical = requireWithin(root, file);
		if (!Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS))
			throw new IOException("Workspace input is not a regular file: " + file);
		return canonical;
	}

	/** Inventories the whole selected tree before callers create output files. */
	public static List<Path> regularFiles(Path root, Predicate<Path> excluded) throws IOException {
		return inventory(requireDirectory(root), excluded).files();
	}

	/** Copies regular files without following links or replacing files created by another writer. */
	public static void copyTree(Path source, Path target, Predicate<Path> excluded) throws IOException {
		Path sourceRoot = requireDirectory(source);
		Path targetRoot = canonicalPath(target);
		if (targetRoot.startsWith(sourceRoot) || sourceRoot.startsWith(targetRoot))
			throw new IOException("Workspace copy source and target must be separate directories");
		Inventory inventory = inventory(sourceRoot, excluded);
		for (Path directory : inventory.directories())
			createDirectories(targetRoot, targetRoot.resolve(sourceRoot.relativize(directory)));
		for (Path file : inventory.files()) {
			Path input = requireRegularFile(sourceRoot, file);
			Path output = requireWithin(targetRoot, targetRoot.resolve(sourceRoot.relativize(file)));
			// No REPLACE_EXISTING: a concurrently created target is a conflict. Retain the
			// platform copy semantics (including executable permissions on POSIX systems).
			Files.copy(input, output, LinkOption.NOFOLLOW_LINKS);
			// A changed input copied as a link is rejected before callers use the copy.
			// Do not attempt deletion through a path whose boundary validation failed.
			requireRegularFile(targetRoot, output);
		}
	}

	/** Writes through a newly allocated temporary file; a predictable .tmp path is never opened. */
	public static void writeAtomically(Path root, Path target, ContentWriter writer) throws IOException {
		Path output = requireWithin(root, target);
		createDirectories(root, output.getParent());
		Path temporary = Files.createTempFile(output.getParent(), ".copperbench-write-", ".tmp");
		try {
			requireWithin(root, temporary);
			try (OutputStream stream = Files.newOutputStream(temporary, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
				writer.write(stream);
			}
			requireWithin(root, output);
			requireRegularFile(root, temporary);
			try {
				Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			deleteTemporaryIfWithin(root, temporary);
		}
	}

	private static void deleteTemporaryIfWithin(Path root, Path temporary) throws IOException {
		try {
			// The final component may itself have become a link; deleting that link is
			// safe only while its parent is still an authorized directory.
			requireWithin(root, temporary.getParent());
		} catch (IOException boundaryUnavailable) {
			return;
		}
		Files.deleteIfExists(temporary);
	}

	private static void createDirectories(Path root, Path directory) throws IOException {
		Path verified = requireWithin(root, directory);
		Files.createDirectories(verified);
		requireWithin(root, verified);
		if (!Files.isDirectory(verified, LinkOption.NOFOLLOW_LINKS))
			throw new IOException("Workspace output parent is not a directory: " + directory);
	}

	private static Inventory inventory(Path root, Predicate<Path> excluded) throws IOException {
		List<Path> directories = new ArrayList<>();
		List<Path> files = new ArrayList<>();
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
					throws IOException {
				requireWithin(root, directory);
				if (!directory.equals(root) && excluded.test(root.relativize(directory)))
					return FileVisitResult.SKIP_SUBTREE;
				directories.add(directory);
				return FileVisitResult.CONTINUE;
			}

			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				// Reject links even when their spelling would otherwise be excluded from the copy.
				requireWithin(root, file);
				if (excluded.test(root.relativize(file))) return FileVisitResult.CONTINUE;
				requireRegularFile(root, file);
				files.add(file);
				return FileVisitResult.CONTINUE;
			}
		});
		files.sort(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')));
		return new Inventory(List.copyOf(directories), List.copyOf(files));
	}

	@FunctionalInterface
	public interface ContentWriter {
		void write(OutputStream stream) throws IOException;
	}

	private record Inventory(List<Path> directories, List<Path> files) {
	}
}
