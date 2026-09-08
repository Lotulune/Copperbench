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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledJdkLocatorTest {

	@TempDir Path root;

	@Test void installedFlatLayoutWinsOverSourceLayout() throws Exception {
		Path installed = javaHome(root.resolve("jdk"));
		javaHome(root.resolve("jdk/jbr25_win_64"));

		assertEquals(installed.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 25, root.resolve("missing-fallback")));
	}

	@Test void installedJava21SidecarWinsForJava17And21Tracks() throws Exception {
		javaHome(root.resolve("jdk"));
		Path java21 = javaHome(root.resolve("jdk21"));

		assertEquals(java21.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 21, root.resolve("missing-fallback")));
		assertEquals(java21.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 17, root.resolve("missing-fallback")));
	}

	@Test void linuxSourceLayoutsAreSelectedWithoutFallingBackToWindowsBinaries() throws Exception {
		RuntimePlatform linux = RuntimePlatform.detect("Linux", "x86_64");
		Path java25 = unixJavaHome(root.resolve("jdk/jbr25_linux_64"));
		javaHome(root.resolve("jdk/jbr25_win_64"));
		assertEquals(java25.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 25, root.resolve("missing-fallback"), linux));

		Files.delete(root.resolve("jdk/jbr25_linux_64/bin/java"));
		Path java21 = unixJavaHome(root.resolve("jdk/jdk21_linux_64"));
		assertEquals(java21.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 21, root.resolve("missing-fallback"), linux));
	}

	@Test void sourceLayoutsAreSelectedByRequiredJavaRelease() throws Exception {
		RuntimePlatform windows = RuntimePlatform.detect("Windows 11", "amd64");
		Path java25 = javaHome(root.resolve(windows.sourceJavaHome(25)));
		assertEquals(java25.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 25, root.resolve("missing-fallback"), windows));

		Files.delete(java25.resolve("bin/java.exe"));
		Path java21 = javaHome(root.resolve(windows.sourceJavaHome(21)));
		assertEquals(java21.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 21, root.resolve("missing-fallback"), windows));
		assertEquals(java21.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 17, root.resolve("missing-fallback"), windows));
	}

	@Test void fallsBackToRunningJavaOnlyWhenItIsUsable() throws Exception {
		Path fallback = javaHome(root.resolve("runtime"));
		assertEquals(fallback.toAbsolutePath().normalize(), BundledJdkLocator.locate(root, 25, fallback));
	}

	@Test void missingJdkReportsStableCodeAndEveryAttemptedPath() {
		Path fallback = root.resolve("missing-runtime");
		RuntimePlatform linux = RuntimePlatform.detect("Linux", "x86_64");
		var failure = assertThrows(BundledJdkLocator.MissingJdkException.class,
				() -> BundledJdkLocator.locate(root, 25, fallback, linux));

		assertEquals("BUNDLED_JDK_MISSING", failure.diagnosticCode());
		assertEquals(3, failure.attempted().size());
		assertTrue(failure.getMessage().contains(root.resolve("jdk").toAbsolutePath().normalize().toString()));
		assertTrue(failure.getMessage().contains(
				root.resolve(linux.sourceJavaHome(25)).toAbsolutePath().normalize().toString()));
		assertTrue(failure.getMessage().contains(fallback.toAbsolutePath().normalize().toString()));
	}

	private static Path javaHome(Path home) throws Exception {
		Files.createDirectories(home.resolve("bin"));
		Files.writeString(home.resolve("bin/java.exe"), "fixture");
		return home;
	}

	private static Path unixJavaHome(Path home) throws Exception {
		Files.createDirectories(home.resolve("bin"));
		Files.writeString(home.resolve("bin/java"), "fixture");
		return home;
	}
}
