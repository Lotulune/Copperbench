/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForge1211GeneratorTest {

	@TempDir Path output;

	@Test void generatesTheParityBlockItemRecipeProcedureAndResources() throws Exception {
		var generator = new NeoForge1211Generator(Path.of(".").toAbsolutePath().normalize());

		var result = generator.generate(output, NeoForge1211GoldenWorkspace.create());

		assertEquals(NeoForge1211Generator.GENERATOR_ID, result.generatorId());
		assertEquals("copper_trails", result.modId());
		assertTrue(result.generatedPaths().contains("src/main/resources/META-INF/neoforge.mods.toml"));
		assertTrue(Files.readString(output.resolve("src/main/resources/META-INF/neoforge.mods.toml"))
				.contains("type=\"required\""));
		assertTrue(result.generatedPaths().contains("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(result.generatedPaths().contains("src/main/resources/data/copper_trails/recipe/trail_lamp.json"));
		assertFalse(Files.exists(output.resolve("src/main/resources/fabric.mod.json")));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("net.neoforged.moddev"));
		String properties = Files.readString(output.resolve("gradle.properties"));
		assertTrue(properties.contains("neoforge_version=21.1.232"));
		assertTrue(properties.contains("jdk/jdk21_win_64"));
		assertTrue(Files.readString(output.resolve("settings.gradle"))
				.contains("org.gradle.toolchains.foojay-resolver-convention"));
		assertTrue(Files.readString(output.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
		assertTrue(Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE5_NEOFORGE_READY"));
		assertTrue(Files.size(output.resolve(
				"src/main/resources/assets/copper_trails/textures/block/trail_lamp.png")) > 0);
	}
}
