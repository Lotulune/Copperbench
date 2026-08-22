/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.gradle;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Aggregates Gradle file-download events into a single status line and percent. */
public final class GradleDownloadProgress {

	public record Snapshot(String label, int percent, String fileName, long bytes, long totalBytes, int finishedFiles) {
	}

	private GradleDownloadProgress() {
	}

	public static String fileNameFromUrl(@Nullable String url) {
		if (url == null || url.isBlank())
			return "";
		String path = url;
		int query = path.indexOf('?');
		if (query >= 0)
			path = path.substring(0, query);
		int slash = path.lastIndexOf('/');
		String name = slash >= 0 ? path.substring(slash + 1) : path;
		return name.isBlank() ? path : name;
	}

	public static String fileNameFromUri(@Nullable URI uri) {
		return uri == null ? "" : fileNameFromUrl(uri.toString());
	}

	public static int percent(long bytes, long totalBytes) {
		if (totalBytes <= 0 || bytes <= 0)
			return 0;
		return (int) Math.min(100, Math.round(bytes * 100.0d / totalBytes));
	}

	public static String formatBytes(long bytes) {
		if (bytes < 1024)
			return bytes + " B";
		if (bytes < 1024 * 1024)
			return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0d);
		return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0d * 1024.0d));
	}

	public static String describe(String fileName, long bytes, long totalBytes, int extraActive, int finishedFiles) {
		String name = fileName == null || fileName.isBlank() ? "..." : fileName;
		String size = totalBytes > 0 ?
				formatBytes(bytes) + " / " + formatBytes(totalBytes) :
				(bytes > 0 ? formatBytes(bytes) : "");
		StringBuilder label = new StringBuilder();
		if (finishedFiles > 0)
			label.append(finishedFiles).append(" done · ");
		label.append(name);
		if (!size.isBlank())
			label.append("  ").append(size);
		if (extraActive > 0)
			label.append("  +").append(extraActive);
		return label.toString();
	}

	public static final class Tracker {

		private final Map<String, long[]> active = new ConcurrentHashMap<>();
		private final AtomicInteger finishedFiles = new AtomicInteger();

		public void start(String fileName, long totalBytes) {
			if (fileName == null || fileName.isBlank())
				return;
			active.put(fileName, new long[] { 0L, Math.max(-1L, totalBytes) });
		}

		public void progress(String fileName, long bytes, long totalBytes) {
			if (fileName == null || fileName.isBlank())
				return;
			active.put(fileName, new long[] { Math.max(0L, bytes), totalBytes });
		}

		public void finish(String fileName, long bytes) {
			if (fileName != null && !fileName.isBlank())
				active.remove(fileName);
			finishedFiles.incrementAndGet();
			if (bytes > 0 && fileName != null)
				active.remove(fileName);
		}

		public Snapshot snapshot() {
			Map.Entry<String, long[]> best = null;
			for (Map.Entry<String, long[]> entry : active.entrySet()) {
				if (best == null || entry.getValue()[0] >= best.getValue()[0])
					best = entry;
			}
			int finished = finishedFiles.get();
			if (best == null) {
				String label = finished > 0 ? finished + " done" : "";
				return new Snapshot(label, finished > 0 ? 100 : 0, "", 0, -1, finished);
			}
			String fileName = best.getKey();
			long bytes = best.getValue()[0];
			long total = best.getValue()[1];
			int extra = Math.max(0, active.size() - 1);
			return new Snapshot(describe(fileName, bytes, total, extra, finished), percent(bytes, total), fileName,
					bytes, total, finished);
		}
	}

	public static AutoCloseable watchPartialArchives(Path gradleHome, Tracker tracker, Consumer<Snapshot> emit) {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "gradle-dist-watch");
			thread.setDaemon(true);
			return thread;
		});
		Path dists = gradleHome.resolve("wrapper").resolve("dists");
		ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
			try {
				Path part = largestPartialArchive(dists);
				if (part == null)
					return;
				long size = Files.size(part);
				String name = part.getFileName().toString().replace(".part", "");
				tracker.progress(name, size, -1);
				emit.accept(tracker.snapshot());
			} catch (Exception ignored) {
			}
		}, 0, 250, TimeUnit.MILLISECONDS);
		return () -> {
			future.cancel(false);
			executor.shutdownNow();
		};
	}

	@Nullable static Path largestPartialArchive(Path dists) throws IOException {
		if (dists == null || !Files.isDirectory(dists))
			return null;
		try (Stream<Path> stream = Files.walk(dists, 4)) {
			return stream.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".part")
							|| path.getFileName().toString().endsWith("-bin.zip"))
					.filter(path -> {
						String name = path.getFileName().toString();
						return name.endsWith(".part") || !Files.exists(path.resolveSibling(name + ".ok"));
					}).max((left, right) -> {
						try {
							return Long.compare(Files.size(left), Files.size(right));
						} catch (IOException e) {
							return 0;
						}
					}).orElse(null);
		}
	}

}
