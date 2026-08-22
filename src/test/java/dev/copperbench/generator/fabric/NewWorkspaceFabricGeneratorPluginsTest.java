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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewWorkspaceFabricGeneratorPluginsTest {

	@Test void fabric262PluginPinsMinecraft26_2() throws Exception {
		Path yaml = Path.of("plugins/generator-fabric-26.2/fabric-26.2/generator.yaml");
		Path wrapper = Path.of(
				"plugins/generator-fabric-26.2/fabric-26.2/workspacebase/gradle/wrapper/gradle-wrapper.properties");
		assertTrue(Files.isRegularFile(yaml));
		String text = Files.readString(yaml);
		assertTrue(text.contains("buildfileversion: 0.158.0"));
		assertTrue(text.contains("datapack-26.1.x"));
		assertTrue(Files.readString(wrapper).contains("gradle-9.7.0-bin.zip"));
		assertTrue(Files.readString(Path.of("plugins/generator-fabric-26.2/plugin.json"))
				.contains("\"id\": \"generator-fabric-26.2\""));
	}

	@Test void fabric1211PluginUses1211ImportsAndResourceLocation() throws Exception {
		Path yaml = Path.of("plugins/generator-1.21.1/fabric-1.21.1/generator.yaml");
		Path gradle = Path.of("plugins/generator-1.21.1/fabric-1.21.1/workspacebase/build.gradle");
		Path item = Path.of("plugins/generator-1.21.1/fabric-1.21.1/templates/item/item.java.ftl");
		assertTrue(Files.isRegularFile(yaml));
		String text = Files.readString(yaml);
		assertTrue(text.contains("buildfileversion: 0.116.15"));
		assertTrue(text.contains("datapack-1.21.1"));
		assertTrue(text.contains("neoforge-1.21.1"));
		assertFalse(text.contains("neoforge-26.1.2"));
		assertTrue(Files.readString(gradle).contains("VERSION_21"));
		assertTrue(Files.readString(item).contains("ResourceLocation"));
		assertFalse(Files.readString(item).contains("Identifier.parse"));
		assertFalse(Files.isDirectory(Path.of("plugins/generator-1.21.1/fabric-1.21.1/mappings")));
	}

	@Test void neoforge262PluginPinsNeoForge262() throws Exception {
		Path yaml = Path.of("plugins/generator-26.2/neoforge-26.2/generator.yaml");
		assertTrue(Files.isRegularFile(yaml));
		assertTrue(Files.readString(yaml).contains("buildfileversion: 26.2.0.63"));
		assertTrue(Files.readString(Path.of("plugins/generator-26.2/plugin.json")).contains("\"id\": \"generator-26.2\""));
		assertTrue(Files.readString(Path.of(
						"plugins/generator-26.2/neoforge-26.2/workspacebase/gradle/wrapper/gradle-wrapper.properties"))
				.contains("gradle-9.7.0-bin.zip"));
	}

	@Test void maintenance1201PluginsUseJava17AndGradle88() throws Exception {
		assertTrue(Files.readString(Path.of("plugins/generator-1.20.1/fabric-1.20.1/generator.yaml"))
				.contains("buildfileversion: 0.92.2"));
		assertTrue(Files.readString(Path.of("plugins/generator-1.20.1/fabric-1.20.1/workspacebase/build.gradle"))
				.contains("VERSION_17"));
		assertTrue(Files.readString(Path.of(
						"plugins/generator-1.20.1/fabric-1.20.1/workspacebase/gradle/wrapper/gradle-wrapper.properties"))
				.contains("gradle-8.8-bin.zip"));
		assertTrue(Files.readString(Path.of("plugins/generator-1.20.1/neoforge-1.20.1/workspacebase/build.gradle"))
				.contains("net.neoforged.gradle.userdev"));
		assertTrue(Files.readString(Path.of("plugins/generator-1.20.1/neoforge-1.20.1/generator.yaml"))
				.contains("buildfileversion: 47.1.106"));
		assertTrue(Files.readString(Path.of("plugins/generator-1.20.1/plugin.json"))
				.contains("\"id\": \"generator-1.20.1\""));
	}

	@Test void workspaceGeneratorsShareGradle97ExceptMaintenance1201() throws Exception {
		String wrappers = Files.readString(Path.of(
				"plugins/generator-1.21.1/fabric-1.21.1/workspacebase/gradle/wrapper/gradle-wrapper.properties"))
				+ Files.readString(Path.of(
				"plugins/generator-1.21.1/neoforge-1.21.1/workspacebase/gradle/wrapper/gradle-wrapper.properties"))
				+ Files.readString(Path.of(
				"plugins/generator-26.1.x/neoforge-26.1.2/workspacebase/gradle/wrapper/gradle-wrapper.properties"))
				+ Files.readString(Path.of(
				"plugins/generator-fabric-26.1.2/fabric-26.1.2/workspacebase/gradle/wrapper/gradle-wrapper.properties"));
		assertTrue(wrappers.contains("gradle-9.7.0-bin.zip"));
		assertFalse(wrappers.contains("gradle-9.6.1-bin.zip"));
		assertTrue(Files.readString(Path.of(
						"plugins/generator-1.20.1/fabric-1.20.1/workspacebase/gradle/wrapper/gradle-wrapper.properties"))
				.contains("gradle-8.8-bin.zip"));
	}

}
