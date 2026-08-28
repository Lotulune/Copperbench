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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForge1201GeneratorTest {

	@TempDir Path output;

	@Test void generatesTheMaintenanceNeoForge1201VerticalSlice() throws Exception {
		var workspace = Fabric1211GoldenWorkspace.createNeoForge1201();
		var generator = new NeoForge1211Generator(Path.of(".").toAbsolutePath().normalize(),
				NeoForge1211Generator.Profile.NEOFORGE_1201);
		var result = generator.generate(output, workspace);
		assertEquals("neoforge-1.20.1", result.generatorId());
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("minecraft_version=1.20.1"));
		assertTrue(Files.readString(output.resolve("gradle.properties")).contains("neoforge_version=47.1.106"));
		assertTrue(Files.readString(output.resolve("gradle.properties"))
				.contains("net.neoforged.gradle.caching.enabled=false"));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("net.neoforged.gradle.userdev"));
		assertTrue(Files.readString(output.resolve("build.gradle"))
				.contains("net.neoforged:forge:${minecraft_version}-${neoforge_version}"));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("JavaLanguageVersion.of(17)"));
		assertTrue(Files.readString(output.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("gradle-8.8-bin.zip"));
		assertTrue(Files.isRegularFile(output.resolve("src/main/resources/META-INF/mods.toml")));
		assertFalse(Files.exists(output.resolve("src/main/resources/META-INF/neoforge.mods.toml")));
		String toml = Files.readString(output.resolve("src/main/resources/META-INF/mods.toml"));
		assertTrue(toml.contains("mandatory=true"));
		assertTrue(toml.contains("modId=\"forge\""));
		assertTrue(toml.contains("loaderVersion=\"[47,)\""));
		String blocks = Files.readString(output.resolve("src/main/java/dev/coppertrails/init/ModBlocks.java"));
		assertTrue(blocks.contains("RegistryObject<Block>"));
		assertTrue(blocks.contains("net.minecraftforge.registries.RegistryObject"));
		String mod = Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(mod.contains("net.minecraftforge.eventbus.api.IEventBus"));
		assertTrue(mod.contains("net.minecraftforge.fml.common.Mod"));
		assertTrue(Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE7_NEOFORGE1201_READY"));
		assertEquals(output.resolve("runs/server"), generator.serverRunDirectory(output));
		generator.prepareServerRun(output);
		String forgeServerConfig = Files.readString(output.resolve("runs/server/world/serverconfig/forge-server.toml"));
		assertTrue(forgeServerConfig.contains("advertiseDedicatedServerToLan = false"));
	}
}
