/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import dev.copperbench.platform.RuntimePlatform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves the Java home used by generated workspaces in source and installed product layouts. */
public final class BundledJdkLocator {
	private static final Logger LOG = LogManager.getLogger(BundledJdkLocator.class);

	private BundledJdkLocator() {
	}

	public static Path locate(Path distributionRoot, int javaRelease) {
		String configuredJavaHome = System.getProperty("java.home");
		Path fallback = configuredJavaHome == null || configuredJavaHome.isBlank()
				? null : Path.of(configuredJavaHome);
		return locate(distributionRoot, javaRelease, fallback);
	}

	static Path locate(Path distributionRoot, int javaRelease, Path fallbackJavaHome) {
		return locate(distributionRoot, javaRelease, fallbackJavaHome, RuntimePlatform.current());
	}

	static Path locate(Path distributionRoot, int javaRelease, Path fallbackJavaHome, RuntimePlatform platform) {
		Path root = Objects.requireNonNull(distributionRoot).toAbsolutePath().normalize();
		RuntimePlatform runtimePlatform = Objects.requireNonNull(platform);
		List<Path> attempted = new ArrayList<>();

		if (javaRelease <= 21) {
			for (Path java21 : java21Candidates(root, runtimePlatform)) {
				attempted.add(java21);
				if (isJavaHome(java21)) return resolved(root, javaRelease, java21, attempted);
			}
		} else {
			Path installedLayout = root.resolve("jdk").normalize();
			attempted.add(installedLayout);
			if (isJavaHome(installedLayout)) return resolved(root, javaRelease, installedLayout, attempted);
			String sourceRelative = runtimePlatform.sourceJavaHome(javaRelease);
			if (sourceRelative != null) {
				Path sourceLayout = root.resolve(sourceRelative).normalize();
				if (!attempted.contains(sourceLayout)) {
					attempted.add(sourceLayout);
					if (isJavaHome(sourceLayout)) return resolved(root, javaRelease, sourceLayout, attempted);
				}
			}
		}

		if (fallbackJavaHome != null) {
			Path fallback = fallbackJavaHome.toAbsolutePath().normalize();
			attempted.add(fallback);
			if (isJavaHome(fallback)) return resolved(root, javaRelease, fallback, attempted);
		}

		LOG.error("Bundled JDK resolution failed: distributionRoot={}, javaRelease={}, attempted={}, java.home={}, user.dir={}",
				root, javaRelease, attempted, System.getProperty("java.home"), System.getProperty("user.dir"));
		throw new MissingJdkException(root, javaRelease, attempted);
	}

	private static List<Path> java21Candidates(Path root, RuntimePlatform platform) {
		List<Path> candidates = new ArrayList<>();
		candidates.add(root.resolve("jdk21").normalize());
		String sourceRelative = platform.sourceJavaHome(21);
		if (sourceRelative == null) return candidates;
		Path source = root.resolve(sourceRelative).normalize();
		if (!candidates.contains(source)) candidates.add(source);
		Path ancestor = root.getParent();
		for (int depth = 0; ancestor != null && depth < 3; depth++, ancestor = ancestor.getParent()) {
			Path candidate = ancestor.resolve(sourceRelative).normalize();
			if (!candidates.contains(candidate)) candidates.add(candidate);
		}
		return candidates;
	}

	private static Path resolved(Path distributionRoot, int javaRelease, Path javaHome, List<Path> attempted) {
		LOG.info("Bundled JDK resolved: distributionRoot={}, javaRelease={}, resolvedJavaHome={}, attempted={}, java.home={}, user.dir={}",
				distributionRoot, javaRelease, javaHome, attempted, System.getProperty("java.home"),
				System.getProperty("user.dir"));
		return javaHome;
	}

	static boolean isJavaHome(Path javaHome) {
		if (javaHome == null) return false;
		return Files.isRegularFile(javaHome.resolve("bin/java.exe"))
				|| Files.isRegularFile(javaHome.resolve("bin/java"));
	}

	public static final class MissingJdkException extends IllegalStateException {
		private final List<Path> attempted;

		private MissingJdkException(Path distributionRoot, int javaRelease, List<Path> attempted) {
			super("No usable Java home found for Java " + javaRelease + " under " + distributionRoot
					+ ". Tried: " + attempted.stream().map(Path::toString).toList());
			this.attempted = List.copyOf(attempted);
		}

		public String diagnosticCode() {
			return "BUNDLED_JDK_MISSING";
		}

		public List<Path> attempted() {
			return attempted;
		}
	}
}
