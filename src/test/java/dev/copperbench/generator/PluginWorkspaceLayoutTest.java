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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginWorkspaceLayoutTest {

	@TempDir Path temp;

	@Test void copiedWorkspaceMetadataAloneDoesNotSuppressTargetGeneration() throws Exception {
		Files.writeString(temp.resolve("workspace.mcreator"), "{}");
		assertFalse(PluginWorkspaceLayout.present(temp));
	}

	@Test void materializedWorkspaceWithSourcesIsProtectedFromProjectionOverwrite() throws Exception {
		Files.writeString(temp.resolve("workspace.mcreator"), "{}");
		Path source = temp.resolve("src/main/java/example/Example.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "class Example {}\n");
		assertTrue(PluginWorkspaceLayout.present(temp));
		assertTrue(PluginWorkspaceLayout.relativeSourcePaths(temp).contains("src/main/java/example/Example.java"));
	}
}
