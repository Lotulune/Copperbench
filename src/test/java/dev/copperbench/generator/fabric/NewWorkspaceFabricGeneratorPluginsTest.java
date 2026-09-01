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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewWorkspaceFabricGeneratorPluginsTest {

	@Test void incompleteGeneratorsDoNotOverrideTheUpstreamStableDefault() throws Exception {
		for (Path yaml : List.of(
				Path.of("plugins/generator-fabric-26.2/fabric-26.2/generator.yaml"),
				Path.of("plugins/generator-26.2/neoforge-26.2/generator.yaml"),
				Path.of("plugins/generator-fabric-26.1.2/fabric-26.1.2/generator.yaml"),
				Path.of("plugins/generator-26.1.x/neoforge-26.1.2/generator.yaml"),
				Path.of("plugins/generator-26.1.x/datapack-26.1.x/generator.yaml"),
				Path.of("plugins/generator-26.1.x/resourcepack-26.1.x/generator.yaml"),
				Path.of("plugins/generator-1.21.1/fabric-1.21.1/generator.yaml"))) {
			assertTrue(Files.readString(yaml).contains("status: experimental"), yaml.toString());
		}
		assertTrue(Files.readString(Path.of("plugins/generator-1.21.1/neoforge-1.21.1/generator.yaml"))
				.contains("status: lts"));
	}

	@Test void advancementTemplatesIgnoreTheLegacyNoFunctionSentinel() throws Exception {
		for (Path template : List.of(
				Path.of("plugins/generator-1.21.1/datapack-1.21.1/templates/advancement.json.ftl"),
				Path.of("plugins/generator-26.1.x/datapack-26.1.x/templates/advancement.json.ftl"))) {
			assertTrue(Files.readString(template)
					.contains("data.rewardFunction?has_content && data.rewardFunction != \"No function\""),
					template.toString());
		}
	}

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
		assertTrue(Files.readString(gradle).contains("net.fabricmc.fabric-loom-remap"));
		assertTrue(Files.readString(gradle).contains("mappings loom.officialMojangMappings()"));
		assertTrue(Files.readString(gradle).contains("modImplementation \"net.fabricmc:fabric-loader"));
		assertTrue(Files.readString(item).contains("ResourceLocation"));
		assertFalse(Files.readString(item).contains("Identifier.parse"));
		Path attributes = Path.of("plugins/generator-1.21.1/fabric-1.21.1/mappings/attributes.yaml");
		assertTrue(Files.isRegularFile(attributes));
		assertTrue(Files.readString(attributes).contains("_mcreator_map_template"));
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
		String fabric1201Gradle = Files.readString(
				Path.of("plugins/generator-1.20.1/fabric-1.20.1/workspacebase/build.gradle"));
		assertTrue(fabric1201Gradle.contains("VERSION_17"));
		assertTrue(fabric1201Gradle.contains("mappings loom.officialMojangMappings()"));
		assertTrue(fabric1201Gradle.contains("modImplementation \"net.fabricmc:fabric-loader"));
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

	@Test void fabricRuntimeMetadataMatchesEachTrackJavaVersion() throws Exception {
		assertTrue(Files.readString(Path.of(
				"plugins/generator-1.20.1/fabric-1.20.1/templates/modbase/fabric.mod.json.ftl"))
				.contains("\"java\": \">=17\""));
		assertTrue(Files.readString(Path.of(
				"plugins/generator-1.21.1/fabric-1.21.1/templates/modbase/fabric.mod.json.ftl"))
				.contains("\"java\": \">=21\""));
	}

	@Test void maintenance1201OverridesModernDatapackPathsAndFormat() throws Exception {
		for (Path generator : List.of(
				Path.of("plugins/generator-1.20.1/fabric-1.20.1"),
				Path.of("plugins/generator-1.20.1/neoforge-1.20.1"))) {
			String generatorYaml = Files.readString(generator.resolve("generator.yaml"));
			assertTrue(generatorYaml.contains("structures_dir: \"@MODDATAROOT/structures\""));
			assertFalse(generatorYaml.contains("structures_dir: \"@MODDATAROOT/structure\""));
			assertTrue(Files.readString(generator.resolve("templates/pack.mcmeta.ftl"))
					.contains("\"pack_format\": 15"), generator.toString());
			assertTrue(Files.readString(generator.resolve("recipe.definition.yaml")).contains("/recipes/"));
			assertTrue(Files.readString(generator.resolve("loottable.definition.yaml")).contains("/loot_tables/"));
			assertTrue(Files.readString(generator.resolve("achievement.definition.yaml")).contains("/advancements/"));
			assertTrue(Files.readString(generator.resolve("function.definition.yaml")).contains("/functions/"));
		}
	}

	@Test void itemModelBooleanPredicatesCompareAgainstTheRequestedValue() throws Exception {
		for (Path template : List.of(
				Path.of("plugins/generator-1.20.1/fabric-1.20.1/templates/item/overrides_model.java.ftl"),
				Path.of("plugins/generator-1.21.1/fabric-1.21.1/templates/item/overrides_model.java.ftl"),
				Path.of("plugins/generator-fabric-26.1.2/fabric-26.1.2/templates/item/overrides_model.java.ftl"),
				Path.of("plugins/generator-fabric-26.2/fabric-26.2/templates/item/overrides_model.java.ftl"),
				Path.of("plugins/generator-26.1.x/neoforge-26.1.2/templates/item/overrides_model.java.ftl"),
				Path.of("plugins/generator-26.2/neoforge-26.2/templates/item/overrides_model.java.ftl"))) {
			String source = Files.readString(template);
			assertTrue(source.contains("property.get(itemStack, level, entity, seed, displayContext) == value"),
					template.toString());
			assertFalse(source.contains("|| !value"), template.toString());
		}
	}

	@Test void fabricDoesNotSilentlyMapUnsupportedNeoForgeAttributesToArmor() throws Exception {
		for (Path mapping : List.of(
				Path.of("plugins/generator-fabric-26.1.2/fabric-26.1.2/mappings/attributes.yaml"),
				Path.of("plugins/generator-fabric-26.2/fabric-26.2/mappings/attributes.yaml"),
				Path.of("vendor/fabric-generator/src/main/resources/fabric-26.1.2/mappings/attributes.yaml"))) {
			String source = Files.readString(mapping);
			assertTrue(source.contains("_unsupported:"), mapping.toString());
			assertTrue(source.contains("- SWIM_SPEED"), mapping.toString());
			assertTrue(source.contains("- NAMETAG_RENDER_DISTANCE"), mapping.toString());
			assertFalse(source.contains("Attributes.ARMOR"), mapping.toString());
		}
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

	@Test void workspaceGeneratorsAllowReliableDistributionDownloads() throws Exception {
		try (var paths = Files.walk(Path.of("plugins"))) {
			List<Path> wrappers = paths.filter(path -> path.getFileName().toString().equals("gradle-wrapper.properties"))
					.filter(path -> path.toString().contains("workspacebase")).toList();
			assertFalse(wrappers.isEmpty(), "No workspace generator Gradle wrappers were found");
			for (Path wrapper : wrappers) {
				assertTrue(Files.readString(wrapper).contains("networkTimeout=60000"), wrapper.toString());
			}
		}
	}

	@Test void fabricAlwaysGeneratesMixinAndEventInfrastructure() throws Exception {
		for (Path yaml : List.of(
				Path.of("plugins/generator-1.20.1/fabric-1.20.1/generator.yaml"),
				Path.of("plugins/generator-1.21.1/fabric-1.21.1/generator.yaml"),
				Path.of("plugins/generator-fabric-26.1.2/fabric-26.1.2/generator.yaml"),
				Path.of("plugins/generator-fabric-26.2/fabric-26.2/generator.yaml"))) {
			String text = Files.readString(yaml);
			String infrastructure = text.substring(text.indexOf("# Mixins"), text.indexOf("java_models:"));
			assertFalse(infrastructure.contains("condition:"), yaml.toString());
			for (String required : List.of("LivingEntityMixin.java", "PlayerMixin.java", "ItemStackMixin.java",
					"BlockItemMixin.java", "BoneMealItemMixin.java", "CommandsMixin.java", "ExperienceOrbMixin.java",
					"LivingEntityEvents.java", "PlayerEvents.java", "BlockEvents.java", "ItemEvents.java",
					"MiscEvents.java"))
				assertTrue(infrastructure.contains(required), yaml + " does not generate " + required);
		}
	}

	@Test void neoforgeAlwaysProvidesNetworkingUsedByProcedureTriggers() throws Exception {
		Path maintenance = Path.of("plugins/generator-1.20.1/neoforge-1.20.1/templates/modbase/mod.java.ftl");
		String maintenanceText = Files.readString(maintenance);
		assertTrue(maintenanceText.contains("SimpleChannel PACKET_HANDLER"), maintenance.toString());
		assertTrue(maintenanceText.contains("void addNetworkMessage("), maintenance.toString());

		for (Path template : List.of(
				Path.of("plugins/generator-1.21.1/neoforge-1.21.1/templates/modbase/mod.java.ftl"),
				Path.of("plugins/generator-26.1.x/neoforge-26.1.2/templates/modbase/mod.java.ftl"),
				Path.of("plugins/generator-26.2/neoforge-26.2/templates/modbase/mod.java.ftl"))) {
			String text = Files.readString(template);
			assertFalse(text.contains("<#if w.hasElementsOfType(\"gui\") || w.hasElementsOfType(\"keybind\")"
					+ " || w.hasVariables()>"), template.toString());
			assertTrue(text.contains("modEventBus.addListener(this::registerNetworking);"), template.toString());
			assertTrue(text.contains("void addNetworkMessage("), template.toString());
		}
	}

	@Test void resourcePackClientEmitsTheReadinessMarker() throws Exception {
		Path loader = Path.of("plugins/generator-1.21.1/resourcepack-1.21.1/workspacebase/packloader/src/main/java/"
				+ "net/mcreator/packloader/PackLoaderMod.java");
		assertTrue(Files.readString(loader).contains("COPPERBENCH_RESOURCE_PACK_READY"));
	}

}
