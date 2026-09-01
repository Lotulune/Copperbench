/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.generator.PluginWorkspaceLayout;
import dev.copperbench.release.ElementCoverageCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Generates the maintained Fabric 1.21.1 workspace projection, including all Stage 11 Java element types. */
public final class Fabric1211Generator {

	public static final String GENERATOR_ID = Profile.FABRIC_1211.generatorId();
	public static final String MINECRAFT_VERSION = Profile.FABRIC_1211.minecraftVersion();
	public static final String LOADER_VERSION = Profile.FABRIC_1211.loaderVersion();
	public static final String LOOM_VERSION = Profile.FABRIC_1211.loomVersion();
	public static final String FABRIC_API_VERSION = Profile.FABRIC_1211.fabricApiVersion();
	public static final String TEMPLATE_SOURCE_COMMIT = "a4c6556aeab4eb100f9f0e3c11d44175384796e6";

	public record Profile(String generatorId, String minecraftVersion, String loaderVersion, String loomVersion,
			String fabricApiVersion, int javaRelease, String readyMarker, boolean modernResourceLocation,
			boolean unobfuscated) {
		public static final Profile FABRIC_1211 = new Profile("fabric-1.21.1", "1.21.1", "0.19.3", "1.17.19",
				"0.116.15+1.21.1", 21, "COPPERBENCH_STAGE3_READY", true, false);
		public static final Profile FABRIC_261 = new Profile("fabric-26.1.2", "26.1.2", "0.19.3", "1.17.19",
				"0.155.2+26.1.2", 25, "COPPERBENCH_STAGE7_FABRIC261_READY", true, true);
		public static final Profile FABRIC_262 = new Profile("fabric-26.2", "26.2", "0.19.3", "1.17.19",
				"0.158.0+26.2", 25, "COPPERBENCH_STAGE7_FABRIC262_READY", true, true);
		public static final Profile FABRIC_1201 = new Profile("fabric-1.20.1", "1.20.1", "0.15.11", "1.7.4",
				"0.92.2+1.20.1", 17, "COPPERBENCH_STAGE7_FABRIC1201_READY", false, false);

		/**
		 * Loom 1.7.4 ships with Gradle 8.8 (fabric-loom 1.7 branch wrapper).
		 * Newer tracks share Gradle 9.7.0 with the New Workspace generator plugins.
		 */
		public String gradleWrapperZip() {
			return javaRelease <= 17 ? "gradle-8.8-bin.zip" : "gradle-9.7.0-bin.zip";
		}

		public String jdkRelativePath() {
			return javaRelease <= 21 ? "jdk/jdk21_win_64" : "jdk/jbr25_win_64";
		}
	}

	private static final Pattern MOD_ID = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
	private static final Pattern PACKAGE = Pattern.compile("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$");
	private static final Pattern ELEMENT_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final byte[] FALLBACK_TEXTURE = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	private final Path distributionRoot;
	private final Profile profile;

	public Fabric1211Generator(Path distributionRoot) {
		this(distributionRoot, Profile.FABRIC_1211);
	}

	public Fabric1211Generator(Path distributionRoot, Profile profile) {
		this.distributionRoot = Objects.requireNonNull(distributionRoot).toAbsolutePath().normalize();
		this.profile = Objects.requireNonNull(profile);
	}

	public Profile profile() {
		return profile;
	}

	public GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws IOException {
		return generate(targetRoot, workspace, true);
	}

	/** Regenerates a copied loader-migration target instead of preserving its source-loader files. */
	public GenerationResult generateMigrationTarget(Path targetRoot, WorkspaceState workspace) throws IOException {
		return generate(targetRoot, workspace, false);
	}

	private GenerationResult generate(Path targetRoot, WorkspaceState workspace, boolean preservePluginWorkspace)
			throws IOException {
		Path root = Objects.requireNonNull(targetRoot).toAbsolutePath().normalize();
		List<ValidationIssue> issues = validate(workspace);
		if (!issues.isEmpty()) throw new IllegalArgumentException(issues.getFirst().message());
		Descriptor descriptor = descriptor(workspace);
		if (preservePluginWorkspace && PluginWorkspaceLayout.present(root))
			return new GenerationResult(profile.generatorId(), descriptor.modId(),
					PluginWorkspaceLayout.relativeSourcePaths(root));
		List<String> generated = new ArrayList<>();
		Files.createDirectories(root);
		if (!preservePluginWorkspace) {
			Files.deleteIfExists(root.resolve("src/main/resources/META-INF/mods.toml"));
			Files.deleteIfExists(root.resolve("src/main/resources/META-INF/neoforge.mods.toml"));
		}

		writeBuildFiles(root, descriptor, workspace.revision(), generated);
		writeJavaSources(root, descriptor, workspace.elements(), generated);
		writeResources(root, descriptor, workspace.elements(), generated);
		generated.sort(Comparator.naturalOrder());
		return new GenerationResult(profile.generatorId(), descriptor.modId(), List.copyOf(generated));
	}

	/** Validates the maintained Fabric slice without writing to the target workspace. */
	public List<ValidationIssue> validate(WorkspaceState workspace) {
		Objects.requireNonNull(workspace);
		List<ValidationIssue> issues = new ArrayList<>();
		try {
			descriptor(workspace);
		} catch (IllegalArgumentException exception) {
			issues.add(new ValidationIssue("FABRIC_WORKSPACE_INVALID", exception.getMessage(), "/generator", null));
			return List.copyOf(issues);
		}

		Set<String> availableResults = new HashSet<>();
		workspace.elements().stream().filter(element -> element.type().equals("block") || element.type().equals("item"))
				.map(Element::name).forEach(availableResults::add);
		for (Element element : workspace.elements()) {
			JsonObject values = fields(element);
			String base = "/elements/" + element.id() + "/values/fields";
			try {
				switch (element.type()) {
					case "block" -> {
						double hardness = number(values, "hardness", 2.0);
						double resistance = number(values, "resistance", Math.max(hardness, 2.0));
						int luminance = integer(values, "luminance", 0);
						if (hardness < 0 || hardness > 100 || resistance < 0)
							issues.add(issue("FABRIC_BLOCK_STRENGTH_INVALID", "Block strength is outside the supported range.",
									base, element));
						if (luminance < 0 || luminance > 15)
							issues.add(issue("FABRIC_BLOCK_LUMINANCE_INVALID", "Block luminance must be between 0 and 15.",
									base + "/luminance", element));
					}
					case "item" -> {
						int maxStack = integer(values, "maxStackSize", 64);
						if (maxStack < 1 || maxStack > 64)
							issues.add(issue("FABRIC_ITEM_STACK_INVALID", "Item stack size must be between 1 and 64.",
									base + "/maxStackSize", element));
					}
					case "recipe" -> validateRecipe(values, availableResults, base, element, issues);
					case "procedure" -> {
						if (string(values, "message", "").isBlank())
							issues.add(issue("FABRIC_PROCEDURE_MESSAGE_REQUIRED", "Procedure message is required.",
									base + "/message", element));
					}
					default -> { }
				}
			} catch (RuntimeException exception) {
				issues.add(issue("FABRIC_FIELD_TYPE_INVALID", "Element fields contain an invalid value.", base, element));
			}
		}
		return List.copyOf(issues);
	}

	private static void validateRecipe(JsonObject values, Set<String> availableResults, String base, Element element,
			List<ValidationIssue> issues) {
		String result = string(values, "result", "");
		if (!availableResults.contains(result))
			issues.add(issue("FABRIC_RECIPE_RESULT_MISSING", "Recipe result must reference a block or item.",
					base + "/result", element));
		if (!values.has("pattern") || !values.get("pattern").isJsonArray()) {
			issues.add(issue("FABRIC_RECIPE_PATTERN_INVALID", "Recipe pattern must contain one to three rows.",
					base + "/pattern", element));
			return;
		}
		JsonArray pattern = values.getAsJsonArray("pattern");
		if (pattern.isEmpty() || pattern.size() > 3 || pattern.asList().stream().anyMatch(row ->
				!row.isJsonPrimitive() || row.getAsString().isEmpty() || row.getAsString().length() > 3))
			issues.add(issue("FABRIC_RECIPE_PATTERN_INVALID", "Recipe pattern must contain one to three rows of up to three symbols.",
					base + "/pattern", element));
	}

	private static ValidationIssue issue(String code, String message, String path, Element element) {
		return new ValidationIssue(code, message, path, element.id());
	}

	private void writeBuildFiles(Path root, Descriptor descriptor, long revision, List<String> generated)
			throws IOException {
		write(root, "settings.gradle", """
				pluginManagement {
				    repositories {
				        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
				        mavenCentral()
				        gradlePluginPortal()
				    }
				}

				rootProject.name = '%s'
				""".formatted(descriptor.modId()), generated);
		write(root, "build.gradle", """
				plugins {
				    id '%s' version "${loom_version}"
				}

				version = project.mod_version
				group = project.maven_group
				base.archivesName = project.mod_id

				dependencies {
				%s}

				processResources {
				    inputs.property 'version', project.version
				    filesMatching('fabric.mod.json') { expand 'version': project.version }
				}

				tasks.withType(JavaCompile).configureEach { it.options.release = %d }

				java {
				    withSourcesJar()
				    sourceCompatibility = JavaVersion.VERSION_%d
				    targetCompatibility = JavaVersion.VERSION_%d
				}
				""".formatted(loomPluginId(), dependencyBlock(), profile.javaRelease(), profile.javaRelease(),
				profile.javaRelease()), generated);
		write(root, "gradle.properties", """
				org.gradle.jvmargs=-Xmx1G
				org.gradle.parallel=true
				org.gradle.configuration-cache=false

				minecraft_version=%s
				loader_version=%s
				loom_version=%s
				fabric_api_version=%s
				mod_version=%s
				maven_group=%s
				mod_id=%s
				""".formatted(profile.minecraftVersion(), profile.loaderVersion(), profile.loomVersion(),
				profile.fabricApiVersion(), descriptor.version(), descriptor.basePackage(), descriptor.modId()), generated);
		write(root, "gradle/wrapper/gradle-wrapper.properties", """
				distributionBase=GRADLE_USER_HOME
				distributionPath=wrapper/dists
				distributionUrl=https\\://mirrors.huaweicloud.com/gradle/%s
				networkTimeout=60000
				retries=3
				retryBackOffMs=2000
				validateDistributionUrl=true
				zipStoreBase=GRADLE_USER_HOME
				zipStorePath=wrapper/dists
				""".formatted(profile.gradleWrapperZip()), generated);
		copy(root, "gradlew", distributionRoot.resolve("gradlew"), generated);
		copy(root, "gradlew.bat", distributionRoot.resolve("gradlew.bat"), generated);
		copy(root, "gradle/wrapper/gradle-wrapper.jar",
				distributionRoot.resolve("gradle/wrapper/gradle-wrapper.jar"), generated);
		write(root, ".gitignore", ".gradle/\nbuild/\nrun/\n", generated);

		JsonObject provenance = new JsonObject();
		provenance.addProperty("generatorId", profile.generatorId());
		provenance.addProperty("minecraftVersion", profile.minecraftVersion());
		provenance.addProperty("loaderVersion", profile.loaderVersion());
		provenance.addProperty("loomVersion", profile.loomVersion());
		provenance.addProperty("fabricApiVersion", profile.fabricApiVersion());
		provenance.addProperty("javaRelease", profile.javaRelease());
		provenance.addProperty("unobfuscated", profile.unobfuscated());
		provenance.addProperty("gradleWrapper", profile.gradleWrapperZip());
		provenance.addProperty("templateSource",
				"https://github.com/FabricMC/fabric-example-mod/tree/" + profile.minecraftVersion());
		provenance.addProperty("templateSourceCommit", TEMPLATE_SOURCE_COMMIT);
		provenance.addProperty("workspaceRevision", revision);
		writeJson(root, ".copperbench/generator-lock.json", provenance, generated);
	}

	private String loomPluginId() {
		if (profile.unobfuscated())
			return "net.fabricmc.fabric-loom";
		return profile.modernResourceLocation() ? "net.fabricmc.fabric-loom-remap" : "fabric-loom";
	}

	private String identifierImport() {
		return profile.unobfuscated() ? "net.minecraft.resources.Identifier" : "net.minecraft.resources.ResourceLocation";
	}

	private String identifierType() {
		return profile.unobfuscated() ? "Identifier" : "ResourceLocation";
	}

	private String identifierFactory() {
		if (profile.unobfuscated())
			return "Identifier.fromNamespaceAndPath(MOD_ID, path)";
		return profile.modernResourceLocation() ? "ResourceLocation.fromNamespaceAndPath(MOD_ID, path)"
				: "new ResourceLocation(MOD_ID, path)";
	}

	private String dependencyBlock() {
		if (profile.unobfuscated())
			return """
					    minecraft "com.mojang:minecraft:${minecraft_version}"
					    implementation "net.fabricmc:fabric-loader:${loader_version}"
					    implementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"
					""";
		return """
				    minecraft "com.mojang:minecraft:${minecraft_version}"
				    mappings loom.officialMojangMappings()
				    modImplementation "net.fabricmc:fabric-loader:${loader_version}"
				    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"
				""";
	}

	private void writeJavaSources(Path root, Descriptor descriptor, List<Element> elements, List<String> generated)
			throws IOException {
		String packagePath = descriptor.basePackage().replace('.', '/');
		String modClass = descriptor.javaName() + "Mod";
		List<Element> blocks = ofType(elements, "block");
		List<Element> items = ofType(elements, "item");
		List<Element> procedures = ofType(elements, "procedure");

		StringBuilder initializer = new StringBuilder();
		if (!blocks.isEmpty()) initializer.append("\t\tModBlocks.register();\n");
		if (!items.isEmpty()) initializer.append("\t\tModItems.register();\n");
		for (Element procedure : procedures)
			initializer.append("\t\t").append(javaName(procedure.name())).append("Procedure.execute();\n");
		initializer.append("\t\tLOGGER.info(\"").append(profile.readyMarker()).append("\");\n");

		write(root, "src/main/java/" + packagePath + "/" + modClass + ".java", """
				package %s;

				import %s.init.ModBlocks;
				import %s.init.ModItems;
				%s
				import net.fabricmc.api.ModInitializer;
				import %s;
				import org.slf4j.Logger;
				import org.slf4j.LoggerFactory;

				public final class %s implements ModInitializer {
					public static final String MOD_ID = %s;
					public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

					@Override public void onInitialize() {
				%s	}

					public static %s id(String path) {
						return %s;
					}
				}
				""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.basePackage(),
				procedureImports(descriptor, procedures), identifierImport(), modClass,
				javaString(descriptor.modId()), initializer, identifierType(), identifierFactory()), generated);

		writeBlocks(root, descriptor, blocks, packagePath, generated);
		writeItems(root, descriptor, items, packagePath, generated);
		for (Element procedure : procedures) writeProcedure(root, descriptor, procedure, packagePath, generated);
		for (Element element : elements) {
			if (!List.of("block", "item", "procedure").contains(element.type()))
				writeGenericElement(root, descriptor, element, packagePath, generated);
		}
	}

	private void writeGenericElement(Path root, Descriptor descriptor, Element element, String packagePath,
			List<String> generated) throws IOException {
		String className = javaName(element.name()) + "Element";
		write(root, "src/main/java/" + packagePath + "/elements/" + className + ".java", """
				package %s.elements;

				/** Generated Stage 11 representation for a supported Java mod element. */
				public final class %s {
					public static final String TYPE = %s;
					public static final String NAME = %s;
					public static final String DISPLAY_NAME = %s;

					private %s() {
					}
				}
				""".formatted(descriptor.basePackage(), className, javaString(element.type()),
				javaString(element.name()), javaString(element.displayName()), className), generated);
		JsonObject elementDescriptor = new JsonObject();
		elementDescriptor.addProperty("type", element.type());
		elementDescriptor.addProperty("name", element.name());
		elementDescriptor.addProperty("displayName", element.displayName());
		elementDescriptor.add("values", element.values());
		writeJson(root, "src/main/resources/copperbench/elements/" + element.name() + ".json", elementDescriptor, generated);
	}

	private void writeBlocks(Path root, Descriptor descriptor, List<Element> blocks, String packagePath,
			List<String> generated) throws IOException {
		if (profile.unobfuscated()) {
			writeUnobfuscatedBlocks(root, descriptor, blocks, packagePath, generated);
			return;
		}
		StringBuilder declarations = new StringBuilder();
		StringBuilder registrations = new StringBuilder();
		for (Element block : blocks) {
			JsonObject fields = fields(block);
			String constant = constantName(block.name());
			double hardness = number(fields, "hardness", 2.0);
			double resistance = number(fields, "resistance", Math.max(hardness, 2.0));
			int luminance = integer(fields, "luminance", 0);
			declarations.append("\tpublic static final Block ").append(constant).append(" = new Block(\n")
					.append("\t\t\tBlockBehaviour.Properties.of().strength(").append(hardness).append("f, ")
					.append(resistance).append("f).lightLevel(state -> ").append(luminance).append("));\n");
			registrations.append("\t\tregister(").append(javaString(block.name())).append(", ").append(constant)
					.append(");\n");
		}
		write(root, "src/main/java/" + packagePath + "/init/ModBlocks.java", """
				package %s.init;

				import %s.%sMod;
				import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
				import net.minecraft.core.Registry;
				import net.minecraft.core.registries.BuiltInRegistries;
				import net.minecraft.world.item.BlockItem;
				import net.minecraft.world.item.CreativeModeTabs;
				import net.minecraft.world.item.Item;
				import net.minecraft.world.level.block.Block;
				import net.minecraft.world.level.block.state.BlockBehaviour;

				public final class ModBlocks {
				%s
					private ModBlocks() {
					}

					public static void register() {
				%s	}

					private static void register(String name, Block block) {
						Registry.register(BuiltInRegistries.BLOCK, %sMod.id(name), block);
						BlockItem item = Registry.register(BuiltInRegistries.ITEM, %sMod.id(name),
								new BlockItem(block, new Item.Properties()));
						ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
								.register(entries -> entries.accept(item));
					}
				}
				""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(), declarations,
				registrations, descriptor.javaName(), descriptor.javaName()), generated);
	}

	private void writeUnobfuscatedBlocks(Path root, Descriptor descriptor, List<Element> blocks, String packagePath,
			List<String> generated) throws IOException {
		StringBuilder declarations = new StringBuilder();
		for (Element block : blocks) {
			JsonObject fields = fields(block);
			String constant = constantName(block.name());
			double hardness = number(fields, "hardness", 2.0);
			double resistance = number(fields, "resistance", Math.max(hardness, 2.0));
			int luminance = integer(fields, "luminance", 0);
			declarations.append("\tpublic static final Block ").append(constant).append(" = register(")
					.append(javaString(block.name()))
					.append(", properties -> new Block(properties.strength(").append(hardness).append("f, ")
					.append(resistance).append("f).lightLevel(state -> ").append(luminance).append(")));\n");
		}
		write(root, "src/main/java/" + packagePath + "/init/ModBlocks.java", """
				package %s.init;

				import %s.%sMod;
				import java.util.function.Function;
				import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
				import net.minecraft.core.Registry;
				import net.minecraft.core.registries.BuiltInRegistries;
				import net.minecraft.core.registries.Registries;
				import net.minecraft.resources.ResourceKey;
				import net.minecraft.world.item.BlockItem;
				import net.minecraft.world.item.CreativeModeTabs;
				import net.minecraft.world.item.Item;
				import net.minecraft.world.level.block.Block;
				import net.minecraft.world.level.block.Blocks;
				import net.minecraft.world.level.block.state.BlockBehaviour;

				public final class ModBlocks {
				%s
					private ModBlocks() {
					}

					public static void register() {
					}

					private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory) {
						Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, %sMod.id(name)), factory,
								BlockBehaviour.Properties.of());
						ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, %sMod.id(name));
						BlockItem item = Registry.register(BuiltInRegistries.ITEM, itemKey,
								new BlockItem(block, new Item.Properties().setId(itemKey)));
						CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
								.register(content -> content.accept(item));
						return block;
					}
				}
				""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(), declarations,
				descriptor.javaName(), descriptor.javaName()), generated);
	}

	private void writeItems(Path root, Descriptor descriptor, List<Element> items, String packagePath,
			List<String> generated) throws IOException {
		if (profile.unobfuscated()) {
			writeUnobfuscatedItems(root, descriptor, items, packagePath, generated);
			return;
		}
		StringBuilder declarations = new StringBuilder();
		StringBuilder registrations = new StringBuilder();
		for (Element item : items) {
			int maxStack = integer(fields(item), "maxStackSize", 64);
			String constant = constantName(item.name());
			declarations.append("\tpublic static final Item ").append(constant)
					.append(" = new Item(new Item.Properties().stacksTo(").append(maxStack).append("));\n");
			registrations.append("\t\tregister(").append(javaString(item.name())).append(", ").append(constant)
					.append(");\n");
		}
		write(root, "src/main/java/" + packagePath + "/init/ModItems.java", """
				package %s.init;

				import %s.%sMod;
				import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
				import net.minecraft.core.Registry;
				import net.minecraft.core.registries.BuiltInRegistries;
				import net.minecraft.world.item.CreativeModeTabs;
				import net.minecraft.world.item.Item;

				public final class ModItems {
				%s
					private ModItems() {
					}

					public static void register() {
				%s	}

					private static void register(String name, Item item) {
						Registry.register(BuiltInRegistries.ITEM, %sMod.id(name), item);
						ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
								.register(entries -> entries.accept(item));
					}
				}
				""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(), declarations,
				registrations, descriptor.javaName()), generated);
	}

	private void writeUnobfuscatedItems(Path root, Descriptor descriptor, List<Element> items, String packagePath,
			List<String> generated) throws IOException {
		StringBuilder declarations = new StringBuilder();
		for (Element item : items) {
			int maxStack = integer(fields(item), "maxStackSize", 64);
			String constant = constantName(item.name());
			declarations.append("\tpublic static final Item ").append(constant).append(" = register(")
					.append(javaString(item.name())).append(", properties -> new Item(properties.stacksTo(")
					.append(maxStack).append(")));\n");
		}
		write(root, "src/main/java/" + packagePath + "/init/ModItems.java", """
				package %s.init;

				import %s.%sMod;
				import java.util.function.Function;
				import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
				import net.minecraft.core.Registry;
				import net.minecraft.core.registries.BuiltInRegistries;
				import net.minecraft.core.registries.Registries;
				import net.minecraft.resources.ResourceKey;
				import net.minecraft.world.item.CreativeModeTabs;
				import net.minecraft.world.item.Item;

				public final class ModItems {
				%s
					private ModItems() {
					}

					public static void register() {
					}

					private static Item register(String name, Function<Item.Properties, Item> factory) {
						ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, %sMod.id(name));
						Item item = Registry.register(BuiltInRegistries.ITEM, key,
								factory.apply(new Item.Properties().setId(key)));
						CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
								.register(content -> content.accept(item));
						return item;
					}
				}
				""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(), declarations,
				descriptor.javaName()), generated);
	}

	private void writeProcedure(Path root, Descriptor descriptor, Element procedure, String packagePath,
			List<String> generated) throws IOException {
		String message = string(fields(procedure), "message", procedure.displayName());
		write(root, "src/main/java/" + packagePath + "/procedure/" + javaName(procedure.name()) + "Procedure.java",
				"""
						package %s.procedure;

						import %s.%sMod;

						public final class %sProcedure {
							private %sProcedure() {
							}

							public static void execute() {
								%sMod.LOGGER.info(%s);
							}
						}
						""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(),
						javaName(procedure.name()), javaName(procedure.name()), descriptor.javaName(), javaString(message)),
				generated);
	}

	private void writeResources(Path root, Descriptor descriptor, List<Element> elements, List<String> generated)
			throws IOException {
		String assets = "src/main/resources/assets/" + descriptor.modId();
		String data = "src/main/resources/data/" + descriptor.modId();
		Map<String, String> language = new LinkedHashMap<>();

		for (Element block : ofType(elements, "block")) {
			language.put("block." + descriptor.modId() + "." + block.name(), block.displayName());
			JsonObject state = new JsonObject();
			JsonObject variants = new JsonObject();
			JsonObject modelRef = new JsonObject();
			modelRef.addProperty("model", descriptor.modId() + ":block/" + block.name());
			variants.add("", modelRef);
			state.add("variants", variants);
			writeJson(root, assets + "/blockstates/" + block.name() + ".json", state, generated);
			writeJson(root, assets + "/models/block/" + block.name() + ".json",
					model("minecraft:block/cube_all", "all", descriptor.modId() + ":block/" + block.name()), generated);
			JsonObject itemModel = new JsonObject();
			itemModel.addProperty("parent", descriptor.modId() + ":block/" + block.name());
			writeJson(root, assets + "/models/item/" + block.name() + ".json", itemModel, generated);
			writeTexture(root, assets + "/textures/block/" + block.name() + ".png", fields(block), generated);
			writeJson(root, data + "/loot_table/blocks/" + block.name() + ".json",
					blockLootTable(descriptor.modId(), block.name()), generated);
		}

		for (Element item : ofType(elements, "item")) {
			language.put("item." + descriptor.modId() + "." + item.name(), item.displayName());
			writeJson(root, assets + "/models/item/" + item.name() + ".json",
					model("minecraft:item/generated", "layer0", descriptor.modId() + ":item/" + item.name()), generated);
			writeTexture(root, assets + "/textures/item/" + item.name() + ".png", fields(item), generated);
		}

		for (Element recipe : ofType(elements, "recipe"))
			writeJson(root, data + "/recipe/" + recipe.name() + ".json",
					recipe(descriptor.modId(), fields(recipe)), generated);

		writeJson(root, assets + "/lang/en_us.json", JSON.toJsonTree(language), generated);
		writeJson(root, "src/main/resources/fabric.mod.json", fabricMod(descriptor), generated);
		JsonObject pack = new JsonObject();
		JsonObject packInfo = new JsonObject();
		packInfo.addProperty("pack_format", 48);
		packInfo.addProperty("description", descriptor.displayName() + " resources");
		pack.add("pack", packInfo);
		writeJson(root, "src/main/resources/pack.mcmeta", pack, generated);
	}

	private Descriptor descriptor(WorkspaceState workspace) {
		JsonObject generator = workspace.generator();
		if (!generator.has("id") || !profile.generatorId().equals(generator.get("id").getAsString()))
			throw new IllegalArgumentException("Workspace generator must be " + profile.generatorId());
		JsonObject document = workspace.upstreamDocument();
		JsonObject product = document.has("copperbench") && document.get("copperbench").isJsonObject()
				? document.getAsJsonObject("copperbench") : new JsonObject();
		String modId = string(product, "modId", workspace.name().toLowerCase(Locale.ROOT).replace(' ', '_'));
		String basePackage = string(product, "basePackage", "dev.copperbench.generated." + modId);
		String version = string(product, "version", "1.0.0");
		if (!MOD_ID.matcher(modId).matches()) throw new IllegalArgumentException("Invalid modId: " + modId);
		if (!PACKAGE.matcher(basePackage).matches())
			throw new IllegalArgumentException("Invalid basePackage: " + basePackage);
		for (Element element : workspace.elements()) {
			if (!ELEMENT_NAME.matcher(element.name()).matches())
				throw new IllegalArgumentException("Invalid element name: " + element.name());
			if (!ElementCoverageCatalog.FIRST_PARTY_SLICE.contains(element.type()))
				throw new IllegalArgumentException("Unsupported Fabric 1.21.1 element type: " + element.type());
		}
		return new Descriptor(modId, basePackage, version, workspace.name(), javaName(workspace.name()));
	}

	private JsonObject fabricMod(Descriptor descriptor) {
		JsonObject mod = new JsonObject();
		mod.addProperty("schemaVersion", 1);
		mod.addProperty("id", descriptor.modId());
		mod.addProperty("version", "${version}");
		mod.addProperty("name", descriptor.displayName());
		mod.addProperty("description", "Generated by Copperbench Fabric 1.21.1");
		mod.addProperty("environment", "*");
		mod.addProperty("license", "All-Rights-Reserved");
		JsonObject entrypoints = new JsonObject();
		JsonArray main = new JsonArray();
		main.add(descriptor.basePackage() + "." + descriptor.javaName() + "Mod");
		entrypoints.add("main", main);
		mod.add("entrypoints", entrypoints);
		JsonObject depends = new JsonObject();
		depends.addProperty("fabricloader", ">=" + profile.loaderVersion());
		depends.addProperty("minecraft", "~" + profile.minecraftVersion());
		depends.addProperty("java", ">=" + profile.javaRelease());
		depends.addProperty("fabric-api", "*");
		mod.add("depends", depends);
		return mod;
	}

	private static JsonObject blockLootTable(String modId, String name) {
		JsonObject root = new JsonObject();
		root.addProperty("type", "minecraft:block");
		JsonObject entry = new JsonObject();
		entry.addProperty("type", "minecraft:item");
		entry.addProperty("name", modId + ":" + name);
		JsonArray entries = new JsonArray();
		entries.add(entry);
		JsonObject pool = new JsonObject();
		pool.addProperty("rolls", 1);
		pool.add("entries", entries);
		JsonArray pools = new JsonArray();
		pools.add(pool);
		root.add("pools", pools);
		return root;
	}

	private static JsonObject recipe(String modId, JsonObject fields) {
		JsonObject recipe = new JsonObject();
		recipe.addProperty("type", "minecraft:crafting_shaped");
		recipe.addProperty("category", "misc");
		JsonArray pattern = fields.has("pattern") ? fields.getAsJsonArray("pattern") : new JsonArray();
		if (pattern.isEmpty()) pattern.add("X");
		recipe.add("pattern", pattern.deepCopy());
		JsonObject key = new JsonObject();
		if (fields.has("key") && fields.get("key").isJsonObject()) {
			for (var entry : fields.getAsJsonObject("key").entrySet()) {
				JsonObject ingredient = new JsonObject();
				ingredient.addProperty("item", entry.getValue().getAsString());
				key.add(entry.getKey(), ingredient);
			}
		} else {
			JsonObject ingredient = new JsonObject();
			ingredient.addProperty("item", "minecraft:stone");
			key.add("X", ingredient);
		}
		recipe.add("key", key);
		JsonObject result = new JsonObject();
		result.addProperty("id", modId + ":" + string(fields, "result", "missing_result"));
		result.addProperty("count", integer(fields, "count", 1));
		recipe.add("result", result);
		return recipe;
	}

	private static JsonObject model(String parent, String textureKey, String texture) {
		JsonObject model = new JsonObject();
		model.addProperty("parent", parent);
		JsonObject textures = new JsonObject();
		textures.addProperty(textureKey, texture);
		model.add("textures", textures);
		return model;
	}

	private static String procedureImports(Descriptor descriptor, List<Element> procedures) {
		StringBuilder imports = new StringBuilder();
		for (Element procedure : procedures)
			imports.append("import ").append(descriptor.basePackage()).append(".procedure.")
					.append(javaName(procedure.name())).append("Procedure;\n");
		return imports.toString();
	}

	private static List<Element> ofType(List<Element> elements, String type) {
		return elements.stream().filter(element -> element.type().equals(type))
				.sorted(Comparator.comparing(Element::name)).toList();
	}

	private static JsonObject fields(Element element) {
		JsonObject values = element.values();
		return values.has("fields") && values.get("fields").isJsonObject()
				? values.getAsJsonObject("fields") : values;
	}

	private static String string(JsonObject object, String name, String fallback) {
		return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : fallback;
	}

	private static double number(JsonObject object, String name, double fallback) {
		return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsDouble() : fallback;
	}

	private static int integer(JsonObject object, String name, int fallback) {
		return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsInt() : fallback;
	}

	private static String javaName(String value) {
		StringBuilder result = new StringBuilder();
		boolean uppercase = true;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (!Character.isLetterOrDigit(character)) {
				uppercase = true;
				continue;
			}
			result.append(uppercase ? Character.toUpperCase(character) : character);
			uppercase = false;
		}
		if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) result.insert(0, "Generated");
		return result.toString();
	}

	private static String constantName(String value) {
		return value.toUpperCase(Locale.ROOT);
	}

	private static String javaString(String value) {
		return JSON.toJson(value);
	}

	private static void writeTexture(Path root, String relative, JsonObject fields, List<String> generated)
			throws IOException {
		byte[] bytes = FALLBACK_TEXTURE;
		if (fields.has("textureBase64") && fields.get("textureBase64").isJsonPrimitive()) {
			try {
				bytes = Base64.getDecoder().decode(fields.get("textureBase64").getAsString());
			} catch (IllegalArgumentException exception) {
				throw new IOException("textureBase64 is not valid Base64", exception);
			}
		}
		Path file = resolve(root, relative);
		Files.createDirectories(file.getParent());
		Files.write(file, bytes);
		generated.add(relative);
	}

	private static void writeJson(Path root, String relative, JsonElement value, List<String> generated)
			throws IOException {
		write(root, relative, JSON.toJson(value) + "\n", generated);
	}

	private static void write(Path root, String relative, String value, List<String> generated) throws IOException {
		Path file = resolve(root, relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, value.replace("\r\n", "\n"), StandardCharsets.UTF_8);
		generated.add(relative);
	}

	private static void copy(Path root, String relative, Path source, List<String> generated) throws IOException {
		if (!Files.isRegularFile(source)) throw new IOException("Missing distribution file: " + source);
		Path target = resolve(root, relative);
		Files.createDirectories(target.getParent());
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		generated.add(relative);
	}

	private static Path resolve(Path root, String relative) throws IOException {
		Path target = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
		if (!target.startsWith(root)) throw new IOException("Generated path escapes the workspace: " + relative);
		return target;
	}

	public record GenerationResult(String generatorId, String modId, List<String> generatedPaths) {
		public GenerationResult {
			generatedPaths = List.copyOf(generatedPaths);
		}
	}

	public record ValidationIssue(String code, String message, String path, UUID elementId) {
	}

	private record Descriptor(String modId, String basePackage, String version, String displayName, String javaName) {
	}
}
