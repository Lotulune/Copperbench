/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGuideLocatorTest {

	@Test void repositoryCheckoutResolvesDocsUserReadme() {
		Path found = UserGuideLocator.resolve(Path.of(".")).orElseThrow();
		assertTrue(Files.isRegularFile(found));
		assertTrue(found.endsWith(Path.of("docs", "user", "README.md")));
	}

	@Test void exportLayoutResolvesBundledUserReadme(@TempDir Path exportRoot) throws Exception {
		Path bundled = exportRoot.resolve("user/README.md");
		Files.createDirectories(bundled.getParent());
		Files.writeString(bundled, "# Copperbench\n");
		assertEquals(bundled.toAbsolutePath().normalize(), UserGuideLocator.resolve(exportRoot).orElseThrow());
	}

	@Test void missingGuideIsAbsent(@TempDir Path empty) {
		assertTrue(UserGuideLocator.resolve(empty).isEmpty());
	}
}
