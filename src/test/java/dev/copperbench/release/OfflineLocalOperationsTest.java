/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import dev.copperbench.ProductIdentity;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineLocalOperationsTest {

	@TempDir Path workspace;

	@Test void generateAndHistoryDoNotRequireImplicitNetwork() throws Exception {
		assertFalse(ProductIdentity.IMPLICIT_NETWORK_SERVICES_ENABLED);
		Path repository = Path.of(".").toAbsolutePath().normalize();
		var result = new Fabric1211Generator(repository).generate(workspace, Fabric1211GoldenWorkspace.create());
		assertTrue(result.generatedPaths().contains("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(Files.isRegularFile(workspace.resolve("gradlew.bat")));
		assertTrue(Files.isRegularFile(workspace.resolve("gradle/wrapper/gradle-wrapper.jar")));
		assertTrue(Files.readString(workspace.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("gradle-9.6.0-bin.zip"));
	}
}
