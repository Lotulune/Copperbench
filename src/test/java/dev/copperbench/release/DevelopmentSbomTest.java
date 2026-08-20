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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentSbomTest {

	@Test void declaredLibraryLicenseFilesExist() throws Exception {
		String gradle = Files.readString(Path.of("build.gradle"));
		for (var component : DevelopmentSbom.DIRECT) {
			assertTrue(gradle.contains(component.version()), component.name());
			Path license = Path.of(component.path());
			assertTrue(Files.exists(license), component.path());
		}
		assertTrue(Files.isDirectory(Path.of("jdk/jbr25_win_64")));
		assertTrue(Files.isDirectory(Path.of("jdk/jdk21_win_64")));
	}
}
