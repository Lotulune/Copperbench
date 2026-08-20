/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric261GeneratorTest {

	@TempDir Path output;

	@Test void generatesTheStageSevenFabric261VerticalSlice() throws Exception {
		var workspace = Fabric1211GoldenWorkspace.create261();
		var generator = new Fabric1211Generator(Path.of(".").toAbsolutePath().normalize(),
				Fabric1211Generator.Profile.FABRIC_261);
		var result = generator.generate(output, workspace);
		assertEquals("fabric-26.1.2", result.generatorId());
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("minecraft_version=26.1.2"));
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("fabric_api_version=0.155.2+26.1.2"));
		String gradle = Files.readString(output.resolve("build.gradle"));
		assertTrue(gradle.contains("VERSION_25"));
		assertTrue(gradle.contains("id 'net.fabricmc.fabric-loom'"));
		assertFalse(gradle.contains("fabric-loom-remap"));
		assertFalse(gradle.contains("officialMojangMappings"));
		assertFalse(gradle.contains("modImplementation"));
		assertTrue(gradle.contains("implementation \"net.fabricmc:fabric-loader"));
		String mod = Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(mod.contains("COPPERBENCH_STAGE7_FABRIC261_READY"));
		assertTrue(mod.contains("Identifier.fromNamespaceAndPath(MOD_ID, path)"));
		assertFalse(mod.contains("ResourceLocation"));
		assertTrue(Files.readString(output.resolve("src/main/java/dev/coppertrails/init/ModBlocks.java"))
				.contains("CreativeModeTabEvents.modifyOutputEvent"));
		assertTrue(Files.readString(output.resolve(".copperbench/generator-lock.json")).contains("fabric-26.1.2"));
	}
}
