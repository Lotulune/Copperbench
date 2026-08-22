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

class Fabric1201GeneratorTest {

	@TempDir Path output;

	@Test void generatesTheMaintenanceFabric1201VerticalSlice() throws Exception {
		var workspace = Fabric1211GoldenWorkspace.create1201();
		var generator = new Fabric1211Generator(Path.of(".").toAbsolutePath().normalize(),
				Fabric1211Generator.Profile.FABRIC_1201);
		var result = generator.generate(output, workspace);
		assertEquals("fabric-1.20.1", result.generatorId());
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("minecraft_version=1.20.1"));
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("fabric_api_version=0.92.2+1.20.1"));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("id 'fabric-loom'"));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("mappings loom.officialMojangMappings()"));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("VERSION_17"));
		assertTrue(Files.readString(output.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("gradle-8.8-bin.zip"));
		assertFalse(Files.readString(output.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("gradle-9.7.0-bin.zip"));
		assertTrue(Files.readString(output.resolve(".copperbench/generator-lock.json")).contains("gradle-8.8-bin.zip"));
		String mod = Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(mod.contains("COPPERBENCH_STAGE7_FABRIC1201_READY"));
		assertTrue(mod.contains("new ResourceLocation(MOD_ID, path)"));
		assertTrue(Files.readString(output.resolve(".copperbench/generator-lock.json")).contains("fabric-1.20.1"));
		assertTrue(Files.readString(output.resolve("src/main/resources/fabric.mod.json")).contains("\">=17\""));
	}
}
