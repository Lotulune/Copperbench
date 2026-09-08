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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test void existingPosixGradleWrapperIsNormalizedAndMadeExecutableWithoutBeingReplaced() throws Exception {
		Path distribution = temp.resolve("distribution");
		Path workspace = temp.resolve("workspace");
		Files.createDirectories(distribution.resolve("gradle/wrapper"));
		Files.writeString(distribution.resolve("gradlew"), "distribution wrapper\n");
		Files.writeString(distribution.resolve("gradlew.bat"), "distribution wrapper\r\n");
		Files.write(distribution.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[] { 1 });

		Files.createDirectories(workspace);
		Path existing = workspace.resolve("gradlew");
		Files.writeString(existing, "#!/bin/sh\r\necho workspace\r\n");
		if (java.io.File.separatorChar != '\\')
			Files.setPosixFilePermissions(existing,
					Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

		PluginWorkspaceLayout.ensureGradleRuntime(workspace, distribution, "gradle-9.2.1-bin.zip");

		assertEquals("#!/bin/sh\necho workspace\n", Files.readString(existing));
		if (java.io.File.separatorChar != '\\') assertTrue(Files.isExecutable(existing));
	}
}
