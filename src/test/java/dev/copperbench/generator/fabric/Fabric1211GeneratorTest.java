/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.release.ElementCoverageCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric1211GeneratorTest {

	@TempDir Path output;

	@Test void generatesTheStageThreeBlockItemRecipeProcedureAndResources() throws Exception {
		WorkspaceState workspace = Fabric1211GoldenWorkspace.create();
		Fabric1211Generator generator = new Fabric1211Generator(Path.of(".").toAbsolutePath().normalize());

		Fabric1211Generator.GenerationResult result = generator.generate(output, workspace);

		assertEquals("fabric-1.21.1", result.generatorId());
		assertEquals("copper_trails", result.modId());
		assertTrue(result.generatedPaths().contains("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(result.generatedPaths().contains("src/main/resources/assets/copper_trails/blockstates/trail_lamp.json"));
		assertTrue(result.generatedPaths().contains("src/main/resources/assets/copper_trails/models/item/trail_compass.json"));
		assertTrue(result.generatedPaths().contains("src/main/resources/data/copper_trails/recipe/trail_lamp.json"));
		assertTrue(result.generatedPaths().contains("src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java"));

		String properties = Files.readString(output.resolve("gradle.properties"));
		assertTrue(properties.contains("minecraft_version=1.21.1"));
		assertTrue(properties.contains("fabric_api_version=0.116.15+1.21.1"));
		String gradle = Files.readString(output.resolve("build.gradle"));
		assertTrue(gradle.contains("id 'net.fabricmc.fabric-loom-remap'"));
		assertTrue(gradle.contains("mappings loom.officialMojangMappings()"));
		assertTrue(gradle.contains("modImplementation \"net.fabricmc:fabric-loader"));
		assertFalse(gradle.contains("implementation \"net.fabricmc:fabric-loader"));
		assertTrue(Files.readString(output.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
		String mod = Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(mod.contains("COPPERBENCH_STAGE3_READY"));
		String language = Files.readString(
				output.resolve("src/main/resources/assets/copper_trails/lang/en_us.json"));
		assertTrue(language.contains("Trail Lamp"));
		assertTrue(language.contains("Trail Compass"));
		assertTrue(Files.size(output.resolve(
				"src/main/resources/assets/copper_trails/textures/block/trail_lamp.png")) > 0);
	}

	@Test void acceptsEveryStage11JavaTypeAndEmitsACompileSafeRepresentation() throws Exception {
		WorkspaceState base = Fabric1211GoldenWorkspace.create();
		List<Element> elements = new ArrayList<>(base.elements());
		long suffix = 900;
		for (String type : ElementCoverageCatalog.FIRST_PARTY_SLICE) {
			if (elements.stream().anyMatch(element -> element.type().equals(type))) continue;
			String name = "stage11_" + type.replace("-", "_");
			elements.add(new Element(UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix++)),
					type, name, name, "valid", "generated", Instant.parse("2026-08-31T00:00:00Z"), new com.google.gson.JsonObject()));
		}
		WorkspaceState workspace = new WorkspaceState(base.id(), base.name(), base.kind(), base.revision(), base.dirty(),
				base.generator(), base.upstreamDocument(), elements);
		Fabric1211Generator generator = new Fabric1211Generator(Path.of(".").toAbsolutePath().normalize());
		var result = generator.generate(output, workspace);
		assertTrue(result.generatedPaths().stream().anyMatch(path -> path.endsWith("/Stage11LivingentityElement.java")));
		assertTrue(result.generatedPaths().contains("src/main/resources/copperbench/elements/stage11_livingentity.json"));
	}

	@Test void materializedPluginWorkspaceGenerationPreservesUserCodeAndIndependentPackagesByteExact() throws Exception {
		Files.writeString(output.resolve("testmod2.mcreator"), "{\"name\":\"testmod2\"}\n");
		Path mainClass = output.resolve("src/main/java/net/mcreator/testmod/Testmod2Mod.java");
		Path wanjianClass = output.resolve("src/main/java/net/mcreator/testmod/wanjian/WanJianGuiZongItem.java");
		Path recipe = output.resolve("src/main/resources/data/testmod2/recipe/wan_jian_gui_zong.json");
		Files.createDirectories(mainClass.getParent());
		Files.createDirectories(wanjianClass.getParent());
		Files.createDirectories(recipe.getParent());
		String userOwnedMain = """
				package net.mcreator.testmod;
				public class Testmod2Mod {
				    // Start of user code block
				    static final String USER_SENTINEL = "wanjian-user-code";
				    // End of user code block
				}
				""";
		String independentPackage = """
				package net.mcreator.testmod.wanjian;
				public final class WanJianGuiZongItem { static final int MAX_SWORDS = 6; }
				""";
		String userRecipe = "{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"id\":\"testmod2:wan_jian_gui_zong\"}}\n";
		Files.writeString(mainClass, userOwnedMain);
		Files.writeString(wanjianClass, independentPackage);
		Files.writeString(recipe, userRecipe);

		WorkspaceState workspace = Fabric1211GoldenWorkspace.create();
		Fabric1211Generator generator = new Fabric1211Generator(Path.of(".").toAbsolutePath().normalize());
		Fabric1211Generator.GenerationResult result = generator.generate(output, workspace);

		assertEquals(userOwnedMain, Files.readString(mainClass));
		assertEquals(independentPackage, Files.readString(wanjianClass));
		assertEquals(userRecipe, Files.readString(recipe));
		assertTrue(result.generatedPaths().contains("src/main/java/net/mcreator/testmod/Testmod2Mod.java"));
		assertTrue(result.generatedPaths().contains("src/main/java/net/mcreator/testmod/wanjian/WanJianGuiZongItem.java"));
		assertFalse(Files.exists(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java")),
				"projection generation must not replace an already materialized plugin workspace");
	}

}
