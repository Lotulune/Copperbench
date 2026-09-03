/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves the Java home used by generated workspaces in source and installed Windows layouts. */
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
		Path root = Objects.requireNonNull(distributionRoot).toAbsolutePath().normalize();
		List<Path> attempted = new ArrayList<>();

		Path installedLayout = root.resolve("jdk").normalize();
		attempted.add(installedLayout);
		if (isJavaHome(installedLayout)) return resolved(root, javaRelease, installedLayout, attempted);

		Path sourceLayout = root.resolve(javaRelease > 21 ? "jdk/jbr25_win_64" : "jdk/jdk21_win_64")
				.normalize();
		attempted.add(sourceLayout);
		if (isJavaHome(sourceLayout)) return resolved(root, javaRelease, sourceLayout, attempted);

		if (fallbackJavaHome != null) {
			Path fallback = fallbackJavaHome.toAbsolutePath().normalize();
			attempted.add(fallback);
			if (isJavaHome(fallback)) return resolved(root, javaRelease, fallback, attempted);
		}

		LOG.error("Bundled JDK resolution failed: distributionRoot={}, javaRelease={}, attempted={}, java.home={}, user.dir={}",
				root, javaRelease, attempted, System.getProperty("java.home"), System.getProperty("user.dir"));
		throw new MissingJdkException(root, javaRelease, attempted);
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
