/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForge262GeneratorTest {

	@TempDir Path output;

	@Test void generatesTheLatestStableNeoForge262VerticalSlice() throws Exception {
		var workspace = Fabric1211GoldenWorkspace.createNeoForge262();
		var generator = new NeoForge1211Generator(Path.of(".").toAbsolutePath().normalize(),
				NeoForge1211Generator.Profile.NEOFORGE_262);
		var result = generator.generate(output, workspace);
		assertEquals("neoforge-26.2", result.generatorId());
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("minecraft_version=26.2"));
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("neoforge_version=26.2.0.63"));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("JavaLanguageVersion.of(25)"));
		assertTrue(Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE7_NEOFORGE262_READY"));
		assertTrue(Files.readString(output.resolve("src/main/java/dev/coppertrails/init/ModBlocks.java"))
				.contains("REGISTRY.registerBlock"));
		assertTrue(Files.readString(output.resolve("src/main/java/dev/coppertrails/init/ModItems.java"))
				.contains("REGISTRY.registerItem"));
		assertTrue(Files.isRegularFile(output.resolve("src/main/resources/META-INF/neoforge.mods.toml")));
	}
}
