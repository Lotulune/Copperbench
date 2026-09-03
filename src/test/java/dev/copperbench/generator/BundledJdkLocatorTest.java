/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

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

	@Test void sourceLayoutsAreSelectedByRequiredJavaRelease() throws Exception {
		Path java25 = javaHome(root.resolve("jdk/jbr25_win_64"));
		assertEquals(java25.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 25, root.resolve("missing-fallback")));

		Files.delete(root.resolve("jdk/jbr25_win_64/bin/java.exe"));
		Path java21 = javaHome(root.resolve("jdk/jdk21_win_64"));
		assertEquals(java21.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 21, root.resolve("missing-fallback")));
		assertEquals(java21.toAbsolutePath().normalize(),
				BundledJdkLocator.locate(root, 17, root.resolve("missing-fallback")));
	}

	@Test void fallsBackToRunningJavaOnlyWhenItIsUsable() throws Exception {
		Path fallback = javaHome(root.resolve("runtime"));
		assertEquals(fallback.toAbsolutePath().normalize(), BundledJdkLocator.locate(root, 25, fallback));
	}

	@Test void missingJdkReportsStableCodeAndEveryAttemptedPath() {
		Path fallback = root.resolve("missing-runtime");
		var failure = assertThrows(BundledJdkLocator.MissingJdkException.class,
				() -> BundledJdkLocator.locate(root, 25, fallback));

		assertEquals("BUNDLED_JDK_MISSING", failure.diagnosticCode());
		assertEquals(3, failure.attempted().size());
		assertTrue(failure.getMessage().contains(root.resolve("jdk").toAbsolutePath().normalize().toString()));
		assertTrue(failure.getMessage().contains(root.resolve("jdk/jbr25_win_64").toAbsolutePath().normalize().toString()));
		assertTrue(failure.getMessage().contains(fallback.toAbsolutePath().normalize().toString()));
	}

	private static Path javaHome(Path home) throws Exception {
		Files.createDirectories(home.resolve("bin"));
		Files.writeString(home.resolve("bin/java.exe"), "fixture");
		return home;
	}
}
