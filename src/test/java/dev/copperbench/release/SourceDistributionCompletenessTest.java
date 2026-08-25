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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceDistributionCompletenessTest {

	@Test void requiredLicenseAndSourceFilesExist() {
		for (String path : List.of("LICENSE.txt", "LICENSE-ADDITIONAL-TERMS.md", "CHANGES-FROM-UPSTREAM.md", "UPSTREAM.md",
				"compliance/SOURCE_DISTRIBUTION.md", "compliance/THIRD_PARTY_NOTICES.md",
				"compliance/baseline.lock.json", "compliance/BRANDING.md")) {
			assertTrue(Files.isRegularFile(Path.of(path)), path);
		}
	}

	@Test void productShellDoesNotLoadACdn() throws Exception {
		String html = Files.readString(Path.of("ui-shell/index.html"));
		assertFalse(html.toLowerCase().contains("cdn."));
		assertFalse(html.contains("http://"));
		assertFalse(html.contains("https://"));
		assertTrue(Files.isRegularFile(Path.of("ui-shell/package.json")));
	}
}
