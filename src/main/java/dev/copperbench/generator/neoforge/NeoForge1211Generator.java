/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.generator.GradleWorkspaceBackend;
import dev.copperbench.generator.PluginWorkspaceLayout;
import dev.copperbench.generator.fabric.Fabric1211Generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Generates the maintained NeoForge 1.21.1 workspace projection through the shared all-type generator path. */
public final class NeoForge1211Generator implements GradleWorkspaceBackend {

	public static final String GENERATOR_ID = Profile.NEOFORGE_1211.generatorId();
	public static final String MINECRAFT_VERSION = Profile.NEOFORGE_1211.minecraftVersion();
	public static final String NEOFORGE_VERSION = Profile.NEOFORGE_1211.neoForgeVersion();
	public static final String MODDEV_VERSION = Profile.NEOFORGE_1211.moddevVersion();
	public static final String TEMPLATE_SOURCE = Profile.NEOFORGE_1211.templateSource();

	public record Profile(String generatorId, String minecraftVersion, String neoForgeVersion, String moddevVersion,
			int javaRelease, String readyMarker, String templateSource, String jdkRelativePath,
			Fabric1211Generator.Profile fabricProfile, boolean modernDeferred) {
		public static final Profile NEOFORGE_1211 = new Profile("neoforge-1.21.1", "1.21.1", "21.1.232", "2.0.141",
				21, "COPPERBENCH_STAGE5_NEOFORGE_READY", "plugins/generator-1.21.1/neoforge-1.21.1",
				"jdk/jdk21_win_64", Fabric1211Generator.Profile.FABRIC_1211, true);
		public static final Profile NEOFORGE_261 = new Profile("neoforge-26.1.2", "26.1.2", "26.1.2.95", "2.0.141",
				25, "COPPERBENCH_STAGE7_NEOFORGE261_READY", "plugins/generator-26.1.x/neoforge-26.1.2",
				"jdk/jbr25_win_64", Fabric1211Generator.Profile.FABRIC_261, true);
		public static final Profile NEOFORGE_262 = new Profile("neoforge-26.2", "26.2", "26.2.0.63", "2.0.141",
				25, "COPPERBENCH_STAGE7_NEOFORGE262_READY", "neoforge-26.2-first-party",
				"jdk/jbr25_win_64", Fabric1211Generator.Profile.FABRIC_262, true);
		public static final Profile NEOFORGE_1201 = new Profile("neoforge-1.20.1", "1.20.1", "47.1.106", "7.0.165",
				17, "COPPERBENCH_STAGE7_NEOFORGE1201_READY", "neoforge-1.20.1-maintenance",
				"jdk/jdk21_win_64", Fabric1211Generator.Profile.FABRIC_1201, false);

		/** 1.20.1 NeoForged publishes Forge-named artifacts, not net.neoforged:neoforge. */
		public String loaderDependency() {
			return modernDeferred ? "net.neoforged:neoforge:" + neoForgeVersion
					: "net.neoforged:forge:" + minecraftVersion + "-" + neoForgeVersion;
		}

		public String modsTomlLoaderModId() {
			return modernDeferred ? "neoforge" : "forge";
		}

		public String fmlLoaderRange() {
			return modernDeferred ? "[4,)" : "[47,)";
		}
	}

	private boolean usesRegistryAwareFactories() {
		return profile.modernDeferred() && profile.javaRelease() >= 25;
	}

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path distributionRoot;
	private final Fabric1211Generator commonGenerator;
	private final Profile profile;

	public NeoForge1211Generator(Path distributionRoot) {
		this(distributionRoot, Profile.NEOFORGE_1211);
	}

	public NeoForge1211Generator(Path distributionRoot, Profile profile) {
		this.distributionRoot = Objects.requireNonNull(distributionRoot).toAbsolutePath().normalize();
		this.profile = Objects.requireNonNull(profile);
		this.commonGenerator = new Fabric1211Generator(this.distributionRoot, profile.fabricProfile());
	}

	@Override public String displayName() {
		return "NeoForge " + profile.minecraftVersion();
	}

	@Override public String diagnosticPrefix() {
		return "NEOFORGE";
	}

	@Override public Path serverRunDirectory(Path targetRoot) {
		return profile.modernDeferred() ? targetRoot.resolve("run") : targetRoot.resolve("runs/server");
	}

	@Override public void prepareServerRun(Path targetRoot) throws IOException {
		if (profile.modernDeferred()) return;
		Path forgeServerConfig = serverRunDirectory(targetRoot).resolve("world/serverconfig/forge-server.toml");
		Files.createDirectories(forgeServerConfig.getParent());
		Files.writeString(forgeServerConfig, "[server]\nadvertiseDedicatedServerToLan = false\n",
				StandardCharsets.UTF_8);
	}

	@Override public List<ValidationIssue> validate(WorkspaceState workspace) {
		if (!profile.generatorId().equals(generatorId(workspace))) {
			return List.of(new ValidationIssue("NEOFORGE_WORKSPACE_INVALID",
					"Workspace generator must be " + profile.generatorId(), "/generator", null));
		}
		return commonGenerator.validate(asFabricWorkspace(workspace)).stream()
				.map(issue -> new ValidationIssue(issue.code().replaceFirst("^FABRIC_", "NEOFORGE_"),
						issue.message().replace("Fabric", "NeoForge"), issue.path(), issue.elementId()))
				.toList();
	}

	@Override public GenerationResult generate(Path targetRoot, WorkspaceState workspace) throws IOException {
		return generate(targetRoot, workspace, true);
	}

	/** Regenerates a copied loader-migration target instead of preserving its source-loader files. */
	public GenerationResult generateMigrationTarget(Path targetRoot, WorkspaceState workspace) throws IOException {
		return generate(targetRoot, workspace, false);
	}

	private GenerationResult generate(Path targetRoot, WorkspaceState workspace, boolean preservePluginWorkspace)
			throws IOException {
		List<ValidationIssue> issues = validate(workspace);
		if (!issues.isEmpty()) throw new IllegalArgumentException(issues.getFirst().message());
		Path root = Objects.requireNonNull(targetRoot).toAbsolutePath().normalize();
		Descriptor descriptor = descriptor(workspace);
		if (preservePluginWorkspace && PluginWorkspaceLayout.present(root))
			return new GenerationResult(profile.generatorId(), descriptor.modId(),
					PluginWorkspaceLayout.relativeSourcePaths(root));
		var common = preservePluginWorkspace
				? commonGenerator.generate(root, asFabricWorkspace(workspace))
				: commonGenerator.generateMigrationTarget(root, asFabricWorkspace(workspace));
		Set<String> generated = new LinkedHashSet<>(common.generatedPaths());

		Files.deleteIfExists(root.resolve("src/main/resources/fabric.mod.json"));
		generated.remove("src/main/resources/fabric.mod.json");
		writeBuildFiles(root, workspace.revision(), descriptor, generated);
		writeJavaSources(root, descriptor, workspace.elements(), generated);
		writeModMetadata(root, descriptor, generated);

		List<String> paths = new ArrayList<>(generated);
		paths.sort(Comparator.naturalOrder());
		return new GenerationResult(profile.generatorId(), descriptor.modId(), paths);
	}

	private void writeBuildFiles(Path root, long revision, Descriptor descriptor, Set<String> generated)
			throws IOException {
		write(root, "settings.gradle", """
				pluginManagement {
				    repositories {
				        mavenLocal()
				        gradlePluginPortal()
				        maven { url = 'https://maven.neoforged.net/releases' }
				    }
				}

				plugins {
				    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
				}

				rootProject.name = '%s'
				""".formatted(descriptor.modId()), generated);
		if (profile.modernDeferred()) {
			write(root, "build.gradle", """
					plugins {
					    id 'net.neoforged.moddev' version "${moddev_version}"
					}

					version = project.mod_version
					group = project.maven_group
					base.archivesName = project.mod_id

					java.toolchain.languageVersion = JavaLanguageVersion.of(%d)

					neoForge {
					    version = project.neoforge_version

					    runs {
					        client { client() }
					        server { server() }
					    }

					    mods {
					        %s { sourceSet sourceSets.main }
					    }
					}

					tasks.withType(JavaCompile).configureEach {
					    options.encoding = 'UTF-8'
					    options.release = %d
					}
					""".formatted(profile.javaRelease(), descriptor.modId(), profile.javaRelease()), generated);
		} else {
			write(root, "build.gradle", """
					plugins {
					    id 'net.neoforged.gradle.userdev' version "${moddev_version}"
					}

					version = project.mod_version
					group = project.maven_group
					base.archivesName = project.mod_id

					java.toolchain.languageVersion = JavaLanguageVersion.of(%d)

					runs {
					    client { client() }
					    server { server() }
					}

					dependencies {
					    implementation "net.neoforged:forge:${minecraft_version}-${neoforge_version}"
					}

					tasks.withType(JavaCompile).configureEach {
					    options.encoding = 'UTF-8'
					    options.release = %d
					}
					""".formatted(profile.javaRelease(), profile.javaRelease()), generated);
		}
		// NG 7 userdev cache-miss wipes ~/.gradle/caches/minecraft/assets/objects.
		String ngCache = profile.modernDeferred() ? "" : "net.neoforged.gradle.caching.enabled=false\n";
		write(root, "gradle.properties", """
				org.gradle.jvmargs=-Xmx2G
				org.gradle.parallel=true
				org.gradle.configuration-cache=false
				org.gradle.java.installations.auto-detect=false
				org.gradle.java.installations.paths=%s
				%s
				minecraft_version=%s
				neoforge_version=%s
				moddev_version=%s
				mod_version=%s
				maven_group=%s
				mod_id=%s
				""".formatted(distributionRoot.resolve(profile.jdkRelativePath()).toString().replace('\\', '/'),
				ngCache, profile.minecraftVersion(), profile.neoForgeVersion(), profile.moddevVersion(),
				descriptor.version(), descriptor.basePackage(), descriptor.modId()), generated);

		JsonObject provenance = new JsonObject();
		provenance.addProperty("generatorId", profile.generatorId());
		provenance.addProperty("minecraftVersion", profile.minecraftVersion());
		provenance.addProperty("neoForgeVersion", profile.neoForgeVersion());
		provenance.addProperty("loaderDependency", profile.loaderDependency());
		provenance.addProperty("modDevGradleVersion", profile.moddevVersion());
		provenance.addProperty("templateSource", TEMPLATE_SOURCE);
		provenance.addProperty("workspaceRevision", revision);
		write(root, ".copperbench/generator-lock.json", JSON.toJson(provenance) + "\n", generated);
	}

	private void writeJavaSources(Path root, Descriptor descriptor, List<Element> elements,
			Set<String> generated) throws IOException {
		String packagePath = descriptor.basePackage().replace('.', '/');
		List<Element> blocks = ofType(elements, "block");
		List<Element> items = ofType(elements, "item");
		List<Element> procedures = ofType(elements, "procedure");
		StringBuilder procedureImports = new StringBuilder();
		StringBuilder procedureCalls = new StringBuilder();
		for (Element procedure : procedures) {
			procedureImports.append("import ").append(descriptor.basePackage()).append(".procedure.")
					.append(javaName(procedure.name())).append("Procedure;\n");
			procedureCalls.append("\t\t").append(javaName(procedure.name())).append("Procedure.execute();\n");
		}
		String modClass = descriptor.javaName() + "Mod";
		write(root, "src/main/java/" + packagePath + "/" + modClass + ".java", """
				package %s;

				import %s.init.ModBlocks;
				import %s.init.ModItems;
				%s
				import %s;
				import %s;
				import org.apache.logging.log4j.LogManager;
				import org.apache.logging.log4j.Logger;

				@Mod(%s.MOD_ID)
				public final class %s {
					public static final String MOD_ID = %s;
					public static final Logger LOGGER = LogManager.getLogger(%s.class);

					public %s(IEventBus modEventBus) {
						ModBlocks.REGISTRY.register(modEventBus);
						ModItems.REGISTRY.register(modEventBus);
				%s		LOGGER.info("%s");
					}
				}
				""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.basePackage(),
				procedureImports, eventBusImport(), modAnnotationImport(), modClass, modClass,
				javaString(descriptor.modId()), modClass, modClass, procedureCalls, profile.readyMarker()), generated);
		writeBlocks(root, descriptor, blocks, packagePath, generated);
		writeItems(root, descriptor, blocks, items, packagePath, generated);
	}

	private void writeBlocks(Path root, Descriptor descriptor, List<Element> blocks, String packagePath,
			Set<String> generated) throws IOException {
		StringBuilder declarations = new StringBuilder();
		for (Element block : blocks) {
			JsonObject fields = fields(block);
			String type = profile.modernDeferred() ? "DeferredBlock<Block>" : "RegistryObject<Block>";
			declarations.append("\tpublic static final ").append(type).append(" ").append(constantName(block.name()));
			if (usesRegistryAwareFactories()) {
				declarations.append(" = REGISTRY.registerBlock(").append(javaString(block.name()))
						.append(", properties -> new Block(properties.strength(")
						.append(number(fields, "hardness", 2.0)).append("f, ")
						.append(number(fields, "resistance", 2.0)).append("f).lightLevel(state -> ")
						.append(integer(fields, "luminance", 0)).append(")));\n");
			} else {
				declarations.append(" = REGISTRY.register(").append(javaString(block.name()))
						.append(", () -> new Block(BlockBehaviour.Properties.of().strength(")
						.append(number(fields, "hardness", 2.0)).append("f, ")
						.append(number(fields, "resistance", 2.0)).append("f).lightLevel(state -> ")
						.append(integer(fields, "luminance", 0)).append(")));\n");
			}
		}
		if (profile.modernDeferred()) {
			write(root, "src/main/java/" + packagePath + "/init/ModBlocks.java", """
					package %s.init;

					import %s.%sMod;
					import net.minecraft.world.level.block.Block;
					import net.minecraft.world.level.block.state.BlockBehaviour;
					import net.neoforged.neoforge.registries.DeferredBlock;
					import net.neoforged.neoforge.registries.DeferredRegister;

					public final class ModBlocks {
						public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(%sMod.MOD_ID);
					%s
						private ModBlocks() {
						}
					}
					""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(),
					descriptor.javaName(), declarations), generated);
		} else {
			write(root, "src/main/java/" + packagePath + "/init/ModBlocks.java", """
					package %s.init;

					import %s.%sMod;
					import net.minecraft.world.level.block.Block;
					import net.minecraft.world.level.block.state.BlockBehaviour;
					import net.minecraftforge.registries.DeferredRegister;
					import net.minecraftforge.registries.ForgeRegistries;
					import net.minecraftforge.registries.RegistryObject;

					public final class ModBlocks {
						public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, %sMod.MOD_ID);
					%s
						private ModBlocks() {
						}
					}
					""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(),
					descriptor.javaName(), declarations), generated);
		}
	}

	private void writeItems(Path root, Descriptor descriptor, List<Element> blocks, List<Element> items,
			String packagePath, Set<String> generated) throws IOException {
		StringBuilder declarations = new StringBuilder();
		String itemType = profile.modernDeferred() ? "DeferredItem<Item>" : "RegistryObject<Item>";
		for (Element block : blocks) {
			String blockItemType = usesRegistryAwareFactories() ? "DeferredItem<BlockItem>" : itemType;
			declarations.append("\tpublic static final ").append(blockItemType).append(" ")
					.append(constantName(block.name()));
			if (usesRegistryAwareFactories()) {
				declarations.append(" = REGISTRY.registerItem(").append(javaString(block.name()))
						.append(", properties -> new BlockItem(ModBlocks.").append(constantName(block.name()))
						.append(".get(), properties));\n");
			} else {
				declarations.append(" = REGISTRY.register(").append(javaString(block.name()))
						.append(", () -> new BlockItem(ModBlocks.").append(constantName(block.name()))
						.append(".get(), new Item.Properties()));\n");
			}
		}
		for (Element item : items) {
			declarations.append("\tpublic static final ").append(itemType).append(" ").append(constantName(item.name()));
			if (usesRegistryAwareFactories()) {
				declarations.append(" = REGISTRY.registerItem(").append(javaString(item.name()))
						.append(", properties -> new Item(properties.stacksTo(")
						.append(integer(fields(item), "maxStackSize", 64)).append(")));\n");
			} else {
				declarations.append(" = REGISTRY.register(").append(javaString(item.name()))
						.append(", () -> new Item(new Item.Properties().stacksTo(")
						.append(integer(fields(item), "maxStackSize", 64)).append(")));\n");
			}
		}
		if (profile.modernDeferred()) {
			write(root, "src/main/java/" + packagePath + "/init/ModItems.java", """
					package %s.init;

					import %s.%sMod;
					import net.minecraft.world.item.BlockItem;
					import net.minecraft.world.item.Item;
					import net.neoforged.neoforge.registries.DeferredItem;
					import net.neoforged.neoforge.registries.DeferredRegister;

					public final class ModItems {
						public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(%sMod.MOD_ID);
					%s
						private ModItems() {
						}
					}
					""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(),
					descriptor.javaName(), declarations), generated);
		} else {
			write(root, "src/main/java/" + packagePath + "/init/ModItems.java", """
					package %s.init;

					import %s.%sMod;
					import net.minecraft.world.item.BlockItem;
					import net.minecraft.world.item.Item;
					import net.minecraftforge.registries.DeferredRegister;
					import net.minecraftforge.registries.ForgeRegistries;
					import net.minecraftforge.registries.RegistryObject;

					public final class ModItems {
						public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, %sMod.MOD_ID);
					%s
						private ModItems() {
						}
					}
					""".formatted(descriptor.basePackage(), descriptor.basePackage(), descriptor.javaName(),
					descriptor.javaName(), declarations), generated);
		}
	}

	private void writeModMetadata(Path root, Descriptor descriptor, Set<String> generated) throws IOException {
		String file = profile.modernDeferred() ? "src/main/resources/META-INF/neoforge.mods.toml"
				: "src/main/resources/META-INF/mods.toml";
		String loaderRange = profile.fmlLoaderRange();
		String requirement = profile.modernDeferred() ? "type=\"required\"" : "mandatory=true";
		write(root, file, """
				modLoader="javafml"
				loaderVersion="%s"
				license="All-Rights-Reserved"

				[[mods]]
				modId=%s
				version=%s
				displayName=%s
				description="Generated by Copperbench NeoForge %s"

				[[dependencies.%s]]
				modId="%s"
				%s
				versionRange="[%s,)"
				ordering="AFTER"
				side="BOTH"

				[[dependencies.%s]]
				modId="minecraft"
				%s
				versionRange="[%s]"
				ordering="AFTER"
				side="BOTH"
				""".formatted(loaderRange, javaString(descriptor.modId()), javaString(descriptor.version()),
				javaString(descriptor.displayName()), profile.minecraftVersion(), descriptor.modId(),
				profile.modsTomlLoaderModId(), requirement, profile.neoForgeVersion(), descriptor.modId(), requirement,
				profile.minecraftVersion()), generated);
	}

	private WorkspaceState asFabricWorkspace(WorkspaceState workspace) {
		JsonObject generator = workspace.generator();
		generator.addProperty("id", profile.fabricProfile().generatorId());
		generator.addProperty("loader", "fabric");
		return new WorkspaceState(workspace.id(), workspace.name(), workspace.kind(), workspace.revision(),
				workspace.dirty(), generator, workspace.upstreamDocument(), workspace.elements());
	}

	private static Descriptor descriptor(WorkspaceState workspace) {
		JsonObject document = workspace.upstreamDocument();
		JsonObject product = document.has("copperbench") && document.get("copperbench").isJsonObject()
				? document.getAsJsonObject("copperbench") : new JsonObject();
		String modId = string(product, "modId", workspace.name().toLowerCase(Locale.ROOT).replace(' ', '_'));
		return new Descriptor(modId, string(product, "basePackage", "dev.copperbench.generated." + modId),
				string(product, "version", "1.0.0"), workspace.name(), javaName(workspace.name()));
	}

	private String eventBusImport() {
		return profile.modernDeferred() ? "net.neoforged.bus.api.IEventBus"
				: "net.minecraftforge.eventbus.api.IEventBus";
	}

	private String modAnnotationImport() {
		return profile.modernDeferred() ? "net.neoforged.fml.common.Mod" : "net.minecraftforge.fml.common.Mod";
	}

	private static String generatorId(WorkspaceState workspace) {
		JsonObject generator = workspace.generator();
		return generator.has("id") && generator.get("id").isJsonPrimitive() ? generator.get("id").getAsString() : "";
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

	private static void write(Path root, String relative, String value, Set<String> generated) throws IOException {
		Path file = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
		if (!file.startsWith(root)) throw new IOException("Generated path escapes the workspace: " + relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, value.replace("\r\n", "\n"), StandardCharsets.UTF_8);
		generated.add(relative);
	}

	private record Descriptor(String modId, String basePackage, String version, String displayName, String javaName) {
	}
}
