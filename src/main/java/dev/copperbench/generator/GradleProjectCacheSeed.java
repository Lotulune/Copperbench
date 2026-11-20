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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Copies Fabric Loom's per-project dependency cache into a fresh isolated task directory.
 *
 * <p>Isolated GameTest and server runs start from a frozen copy of the workspace inputs and a new test host, so
 * Loom remaps Minecraft and every mod dependency again before compiling. On a Fabric 1.21.1 workspace that cold
 * configuration made a small build take about 98 seconds; with the project cache copied it took about 34. The
 * cache holds only dependency artifacts derived from the shared Gradle cache, keyed by their inputs, so the frozen
 * sources and the tested JAR stay the same. Seeding is best effort: any problem leaves the task on Loom's normal
 * cold path.</p>
 */
final class GradleProjectCacheSeed {
	static final String LOOM_CACHE = ".gradle/loom-cache";
	/** Larger caches are not copied; a cold build is then cheaper than duplicating the cache. */
	static final long MAX_BYTES = 1024L * 1024 * 1024;

	private GradleProjectCacheSeed() {}

	record Result(boolean seeded, long files, long bytes, String reason) {
		String logLine(String origin) {
			return seeded ? String.format(Locale.ROOT,
					"GRADLE_PROJECT_CACHE_SEEDED: copied %d Loom cache files (%.1f MiB) from the %s", files, bytes / 1048576.0, origin)
					: "GRADLE_PROJECT_CACHE_NOT_SEEDED: " + reason + "; Loom prepares its dependencies from the shared Gradle cache";
		}
	}

	/** Seeds {@code target}'s Loom cache from {@code source}; never replaces an existing target cache. */
	static Result seed(Path source, Path target, BooleanSupplier cancelled) {
		Path from = source.resolve(LOOM_CACHE).normalize();
		Path to = target.resolve(LOOM_CACHE).normalize();
		if (!Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS)) return new Result(false, 0, 0, "the source has no Loom project cache");
		if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) return new Result(false, 0, 0, "the task already has a Loom project cache");
		Path staging = to.resolveSibling("loom-cache.seeding-" + UUID.randomUUID());
		long[] totals = new long[2];
		try {
			Files.createDirectories(staging);
			Files.walkFileTree(from, new SimpleFileVisitor<>() {
				@Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
					if (cancelled.getAsBoolean()) throw new IOException("cancelled");
					if (attributes.isSymbolicLink() || attributes.isOther()) return FileVisitResult.SKIP_SUBTREE;
					Files.createDirectories(staging.resolve(from.relativize(directory).toString()));
					return FileVisitResult.CONTINUE;
				}

				@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
					if (cancelled.getAsBoolean()) throw new IOException("cancelled");
					// Links and lock files belong to the source project; never follow or share them.
					if (!attributes.isRegularFile() || file.getFileName().toString().endsWith(".lock")) return FileVisitResult.CONTINUE;
					totals[1] += attributes.size();
					if (totals[1] > MAX_BYTES) throw new IOException("the Loom project cache is larger than "
							+ (MAX_BYTES >> 20) + " MiB");
					Files.copy(file, staging.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
					totals[0]++;
					return FileVisitResult.CONTINUE;
				}
			});
			// Publish only a complete copy: Loom must never see a partially seeded cache.
			Files.move(staging, to, StandardCopyOption.ATOMIC_MOVE);
			return new Result(true, totals[0], totals[1], null);
		} catch (IOException | RuntimeException exception) {
			deleteQuietly(staging);
			String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			return new Result(false, 0, 0, "seeding stopped (" + reason + ")");
		}
	}

	static void seedAndLog(Path source, Path target, String origin, BooleanSupplier cancelled, Consumer<String> log) {
		Result result = seed(source, target, cancelled);
		if (result.seeded() || !result.reason().startsWith("the source has no")) log.accept(result.logLine(origin));
	}

	private static void deleteQuietly(Path directory) {
		if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try { Files.deleteIfExists(path); } catch (IOException ignored) { }
			});
		} catch (IOException ignored) { }
	}
}
