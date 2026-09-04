/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.workspace;

import com.google.gson.JsonArray;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.release.ElementCoverageCatalog;
import dev.copperbench.release.GeneratorElementCapabilityCatalog;
import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.gradle.GradleDistributionPool;
import dev.copperbench.gradle.MinecraftMappingsCacheRepair;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.io.JavaWriter;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Builds deterministic workspaces from the eight visual workspace-generator plugins. */
class NewWorkspaceGeneratorGoldenBuildTest {

	private static final List<String> GENERATOR_IDS = List.of(
			"fabric-26.2", "neoforge-26.2", "fabric-26.1.2", "neoforge-26.1.2",
			"fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1");
	private static final List<String> STAGE12_GENERATOR_IDS = GENERATOR_IDS;
	private static final String STAGE8_SELECTED_GENERATOR_PROPERTY = "copperbench.stage8.workspaceGeneratorId";
	private static final String STAGE9_SELECTED_GENERATOR_PROPERTY = "copperbench.stage9.workspaceGeneratorId";
	private static final String STAGE11_SELECTED_GENERATOR_PROPERTY = "copperbench.stage11.workspaceGeneratorId";
	private static final String STAGE12_SELECTED_GENERATOR_PROPERTY = "copperbench.stage12.workspaceGeneratorId";
	private static final String GRADLE_HOME_PROPERTY = "copperbench.gradle.user.home";
	private static final String KEEP_WORKSPACE_PROPERTY = "copperbench.workspaceGeneratorKeepWorkspace";
	private static final String MOJANG_NON_PROXY_HOSTS = "piston-data.mojang.com|piston-meta.mojang.com";
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);
	private static final byte[] FALLBACK_TEXTURE = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	private enum Content {
		EMPTY, STAGE9, STAGE11, STAGE12
	}

	private static void validateMixinClasses(String generatorId, Path jar) throws IOException {
		List<String> missing = new java.util.ArrayList<>();
		try (JarFile archive = new JarFile(jar.toFile())) {
			for (var entry : archive.stream()
					.filter(candidate -> !candidate.isDirectory())
					.filter(candidate -> candidate.getName().endsWith(".json"))
					.filter(candidate -> candidate.getName().toLowerCase(java.util.Locale.ROOT).contains("mixin"))
					.toList()) {
				JsonObject config;
				try (InputStreamReader reader = new InputStreamReader(archive.getInputStream(entry), StandardCharsets.UTF_8)) {
					var parsed = JsonParser.parseReader(reader);
					if (!parsed.isJsonObject())
						continue;
					config = parsed.getAsJsonObject();
				}
				if (!config.has("package") || !config.get("package").isJsonPrimitive())
					continue;
				String packagePath = config.get("package").getAsString().replace('.', '/');
				for (String section : List.of("mixins", "client", "server")) {
					if (!config.has(section) || !config.get(section).isJsonArray())
						continue;
					for (var raw : config.getAsJsonArray(section)) {
						if (!raw.isJsonPrimitive())
							continue;
						String mixin = raw.getAsString();
						String classPath = packagePath + "/" + mixin.replace('.', '/') + ".class";
						if (archive.getEntry(classPath) == null)
							missing.add(entry.getName() + ":" + section + "=" + mixin + " -> " + classPath);
					}
				}
			}
		}
		if (!missing.isEmpty())
			throw failure(generatorId, "mixin-classes", "Mixin configuration references missing classes: " + missing);
	}

	@TestFactory @EnabledIfSystemProperty(named = "copperbench.stage8.workspaceGeneratorBuild", matches = "true")
	Stream<DynamicTest> emptyWorkspaceGeneratorsBuildIntoJars() {
		String selected = System.getProperty(STAGE8_SELECTED_GENERATOR_PROPERTY, "").trim();
		List<String> generators = selected.isEmpty() ? GENERATOR_IDS
				: GENERATOR_IDS.stream().filter(selected::equals).toList();
		if (generators.isEmpty())
			throw failure(selected, "catalog", "Unknown generator id");
		return generators.stream().map(generatorId -> DynamicTest.dynamicTest(
				generatorId + " - empty workspace Gradle build", () -> buildWorkspace(generatorId, Content.EMPTY)));
	}

	@TestFactory @EnabledIfSystemProperty(named = "copperbench.stage9.workspaceGeneratorBuild", matches = "true")
	Stream<DynamicTest> stage9DataElementsBuildIntoJars() {
		String selected = System.getProperty(STAGE9_SELECTED_GENERATOR_PROPERTY, "").trim();
		List<String> generators = selected.isEmpty() ? GENERATOR_IDS
				: GENERATOR_IDS.stream().filter(selected::equals).toList();
		if (generators.isEmpty())
			throw failure(selected, "catalog", "Unknown generator id");
		return generators.stream().map(generatorId -> DynamicTest.dynamicTest(
				generatorId + " - Stage 9 data elements Gradle build", () -> buildWorkspace(generatorId, Content.STAGE9)));
	}

	@TestFactory @EnabledIfSystemProperty(named = "copperbench.stage11.workspaceGeneratorBuild", matches = "true")
	Stream<DynamicTest> stage11JavaElementsBuildIntoJars() {
		String selected = System.getProperty(STAGE11_SELECTED_GENERATOR_PROPERTY, "").trim();
		List<String> generators = selected.isEmpty() ? GENERATOR_IDS
				: GENERATOR_IDS.stream().filter(selected::equals).toList();
		if (generators.isEmpty())
			throw failure(selected, "catalog", "Unknown generator id");
		return generators.stream().map(generatorId -> DynamicTest.dynamicTest(
				generatorId + " - Stage 11 Java elements Gradle build",
				() -> buildWorkspace(generatorId, Content.STAGE11)));
	}

	@TestFactory @EnabledIfSystemProperty(named = "copperbench.stage12.workspaceGeneratorBuild", matches = "true")
	Stream<DynamicTest> stage12CopperbenchEditedComplexElementsBuildIntoJars() {
		String selected = System.getProperty(STAGE12_SELECTED_GENERATOR_PROPERTY, "").trim();
		List<String> generators = selected.isEmpty() ? STAGE12_GENERATOR_IDS
				: STAGE12_GENERATOR_IDS.stream().filter(selected::equals).toList();
		if (generators.isEmpty())
			throw failure(selected, "catalog", "Unknown Stage 12 workspace generator id");
		return generators.stream().map(generatorId -> DynamicTest.dynamicTest(
				generatorId + " - Stage 12 Copperbench-edited complex elements Gradle build",
				() -> buildWorkspace(generatorId, Content.STAGE12)));
	}

	private static void buildWorkspace(String generatorId, Content content) throws Exception {
		McreatorTestRuntime.ensureInitialized();
		String stage = switch (content) {
			case EMPTY -> "stage8";
			case STAGE9 -> "stage9";
			case STAGE11 -> "stage11";
			case STAGE12 -> "stage12";
		};
		Path workspaceRoot = Files.createTempDirectory("copperbench-" + stage + "-" + generatorId.replace('.', '_') + "-")
				.toAbsolutePath().normalize();
		File previousJavaHome = PreferencesManager.PREFERENCES.hidden.java_home.get();
		Path gradleJavaHome = javaHomeFor(generatorId);
		Path gradleHome = Path.of("build", "stage8-workspace-generator-gradle",
				generatorId.replace('.', '_')).toAbsolutePath().normalize();
		String previousGradleHome = System.getProperty(GRADLE_HOME_PROPERTY);
		System.setProperty(GRADLE_HOME_PROPERTY, gradleHome.toString());
		PreferencesManager.PREFERENCES.hidden.java_home.set(gradleJavaHome.resolve("bin/java.exe").toFile());
		try {
			seedGradleNetworkProperties(gradleHome);
			GeneratorConfiguration configuration = Generator.GENERATOR_CACHE.get(generatorId);
			if (configuration == null)
				throw failure(generatorId, "generator-cache", "Generator configuration is not loaded");

			String modId = switch (content) {
				case EMPTY -> "copperbench_empty";
				case STAGE9 -> "copperbench_stage9";
				case STAGE11 -> "copperbench_stage11";
				case STAGE12 -> "copperbench_stage12";
			};
			WorkspaceSettings settings = new WorkspaceSettings(modId);
			settings.setModName(switch (content) {
				case EMPTY -> "Copperbench Empty Workspace";
				case STAGE9 -> "Copperbench Stage 9 Golden";
				case STAGE11 -> "Copperbench Stage 11 Golden";
				case STAGE12 -> "Copperbench Stage 12 Golden";
			});
			settings.setVersion("1.0.0");
			settings.setCurrentGenerator(generatorId);
			settings.setModElementsPackage(switch (content) {
				case EMPTY -> "dev.copperbench.empty";
				case STAGE9 -> "dev.copperbench.stage9";
				case STAGE11 -> "dev.copperbench.stage11";
				case STAGE12 -> "dev.copperbench.stage12";
			});

			try (Workspace workspace = Workspace.createWorkspace(
					workspaceRoot.resolve(modId + ".mcreator").toFile(), settings)) {
				if (!workspace.getModElements().isEmpty())
					throw failure(generatorId, "empty-workspace", "Workspace unexpectedly contains mod elements");
				WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
				GradleUtils.updateMCreatorBuildFile(workspace);
				syncGradle(generatorId, workspace, configuration, gradleHome);
				workspace.getGenerator().reloadGradleCaches();
				try {
					workspace.getGenerator().generateBase(true);
				} catch (Exception exception) {
					throw failure(generatorId, "generate-base", exception.getMessage(), exception);
				}
				workspace.getGenerator().runResourceSetupTasks();
				if (content == Content.STAGE9) {
					createStage9DataElements(workspace, generatorId);
					for (String name : List.of("bootstrap", "trail_cache", "trail_ready")) {
						var element = workspace.getModElementByName(name);
						if (element == null || element.getGeneratableElement() == null)
							throw failure(generatorId, "stage9-persistence", "Missing persisted element " + name);
						if (!workspace.getGenerator().generateElement(element.getGeneratableElement()))
							throw failure(generatorId, "stage9-generation", "Generator rejected element " + name);
					}
					workspace.getGenerator().generateBase(true);
				} else if (content == Content.STAGE11) {
					seedTextures(workspace);
					createStage11JavaElements(workspace, generatorId);
					workspace.getGenerator().generateBase(true);
				} else if (content == Content.STAGE12) {
					seedTextures(workspace);
					createStage12ComplexElements(workspace, generatorId);
					workspace.getGenerator().generateBase(true);
				}
				try (Stream<Path> entries = Files.walk(workspace.getGenerator().getSourceRoot().toPath())) {
					JavaWriter.formatAndOrganiseImportsForFiles(workspace,
							entries.filter(Files::isRegularFile).map(Path::toFile).toList(), null);
				}
			}

			GradleRun gradle = runGradle(generatorId, workspaceRoot, gradleJavaHome, gradleHome);
			writeGradleLog(stage, generatorId, gradle.output());
			if (!gradle.completed())
				throw failure(generatorId, "gradle-build", "Gradle build timed out\n"
						+ gradleDiagnostic(gradle.output()));
			if (gradle.exitCode() != 0)
				throw failure(generatorId, "gradle-build", "Gradle exited with " + gradle.exitCode() + "\n"
						+ gradleDiagnostic(gradle.output()));

			Path jar = findJar(workspaceRoot.resolve("build/libs"));
			if (jar == null)
				throw failure(generatorId, "jar-output", "Gradle build produced no non-sources JAR\n"
						+ gradleDiagnostic(gradle.output()));
			if (!jar.getFileName().toString().startsWith(modId + "-"))
				throw failure(generatorId, "jar-identity", "Expected JAR name to start with user modid " + modId
						+ " but got " + jar.getFileName());
			if (content == Content.STAGE11 || content == Content.STAGE12)
				validateMixinClasses(generatorId, jar);
		} catch (AssertionError error) {
			throw error;
		} catch (Exception exception) {
			throw failure(generatorId, "harness", exception.getMessage(), exception);
		} finally {
			PreferencesManager.PREFERENCES.hidden.java_home.set(previousJavaHome);
			if (previousGradleHome == null)
				System.clearProperty(GRADLE_HOME_PROPERTY);
			else
				System.setProperty(GRADLE_HOME_PROPERTY, previousGradleHome);
			if (Boolean.getBoolean(KEEP_WORKSPACE_PROPERTY))
				System.out.println("Retained generator golden workspace for diagnostics: " + workspaceRoot);
			else
				deleteRecursively(workspaceRoot);
		}
	}

	private static void stopShardGradleDaemons(Path workspaceRoot, Path gradleHome) {
		try {
			ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "--stop")
					.directory(workspaceRoot.toFile()).redirectErrorStream(true);
			builder.environment().putAll(GradleUtils.getEnvironment(GradleUtils.getJavaHome()));
			builder.environment().put("GRADLE_USER_HOME", gradleHome.toString());
			Process process = builder.start();
			process.getInputStream().transferTo(OutputStream.nullOutputStream());
			if (!process.waitFor(30, TimeUnit.SECONDS))
				process.destroyForcibly();
		} catch (Exception exception) {
			System.out.println("Could not stop shard Gradle daemons before mappings retry: " + exception.getMessage());
		}
	}

	private static void createStage9DataElements(Workspace workspace, String generatorId) throws Exception {
		AtomicLong ids = new AtomicLong(100);
		UUID workspaceId = UUID.nameUUIDFromBytes(("stage9-" + generatorId).getBytes(StandardCharsets.UTF_8));
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
				() -> uuid(ids.incrementAndGet()))) {
			JsonObject function = new JsonObject();
			function.addProperty("namespace", "mod");
			JsonArray commands = new JsonArray();
			commands.add("say Copperbench Stage 9 ready");
			commands.add("time set day");
			function.add("commands", commands);
			requireCommitted(generatorId, "function", session.uiEntry().execute(
					create(session.workspaceId(), 0, "function", "bootstrap", function)).result().status());

			JsonObject loot = new JsonObject();
			loot.addProperty("type", "Generic");
			JsonObject entry = new JsonObject();
			entry.addProperty("item", "Blocks.STONE");
			entry.addProperty("weight", 2);
			entry.addProperty("minCount", 1);
			entry.addProperty("maxCount", 3);
			JsonArray entries = new JsonArray();
			entries.add(entry);
			JsonObject pool = new JsonObject();
			pool.addProperty("minRolls", 1);
			pool.addProperty("maxRolls", 2);
			pool.add("entries", entries);
			JsonArray pools = new JsonArray();
			pools.add(pool);
			loot.add("pools", pools);
			requireCommitted(generatorId, "loottable", session.uiEntry().execute(
					create(session.workspaceId(), 1, "loottable", "trail_cache", loot)).result().status());

			JsonObject advancement = new JsonObject();
			advancement.addProperty("title", "Trail Ready");
			advancement.addProperty("description", "Run the bootstrap function");
			advancement.addProperty("rewardFunction", "bootstrap");
			requireCommitted(generatorId, "achievement", session.uiEntry().execute(
					create(session.workspaceId(), 2, "achievement", "trail_ready", advancement)).result().status());
		}
	}

	private static void seedTextures(Workspace workspace) throws IOException {
		for (TextureType type : TextureType.values()) {
			File folder = workspace.getFolderManager().getTexturesFolder(type);
			if (folder == null)
				continue;
			Files.createDirectories(folder.toPath());
			for (String name : List.of("test", "test1", "test2", "test3", "test4", "test5", "test6", "test7", "itest",
					"other0", "example", "entity_texture_0", "entity_texture_1", "entity_texture_2", "effect1",
					"armor_texture_layer_1", "armor_texture_layer_2")) {
				File texture = workspace.getFolderManager().getTextureFile(name, type);
				if (texture == null)
					continue;
				Files.createDirectories(texture.toPath().getParent());
				Files.write(texture.toPath(), FALLBACK_TEXTURE);
			}
		}
	}

	private static void createStage11JavaElements(Workspace workspace, String generatorId) throws Exception {
		Random random = new Random(11);
		JsonArray matrix = new JsonArray();
		for (String type : ElementCoverageCatalog.FIRST_PARTY_SLICE) {
				JsonObject row = new JsonObject();
				row.addProperty("type", type);
				var decision = GeneratorElementCapabilityCatalog.decision(generatorId, type);
				row.addProperty("reasonCode", decision.reasonCode());
				row.addProperty("generatable", decision.generatable());
				if (!decision.generatable()) {
					matrix.add(row);
					continue;
				}
				ModElementType<?> upstream = ModElementTypeLoader.getModElementType(type);
				List<GeneratableElement> examples = TestWorkspaceDataProvider.getModElementExamplesFor(workspace,
						upstream, false, random);
				if (examples.isEmpty()) {
					row.addProperty("generatable", false);
					row.addProperty("reasonCode", "GENERATOR_ELEMENT_EXAMPLE_MISSING");
					matrix.add(row);
					continue;
				}
				GeneratableElement example = examples.getFirst();
				workspace.addModElement(example.getModElement());
				try {
					if (!workspace.getGenerator().generateElement(example)) {
						workspace.removeModElement(example.getModElement());
						row.addProperty("generatable", false);
						row.addProperty("reasonCode", "GENERATOR_ELEMENT_GENERATION_FAILED");
						row.addProperty("name", example.getModElement().getName());
						matrix.add(row);
						continue;
					}
					workspace.getModElementManager().storeModElement(example);
				} catch (RuntimeException exception) {
					try {
						workspace.removeModElement(example.getModElement());
					} catch (RuntimeException ignored) {
					}
					row.addProperty("generatable", false);
					row.addProperty("reasonCode", "GENERATOR_ELEMENT_GENERATION_FAILED");
					row.addProperty("name", example.getModElement().getName());
					row.addProperty("detail", String.valueOf(exception.getMessage()));
					matrix.add(row);
					continue;
				}
				row.addProperty("name", example.getModElement().getName());
				row.addProperty("generatedFiles", example.getModElement().getAssociatedFiles().size());
				matrix.add(row);
		}
		Path evidence = Path.of("build", "stage11-workspace-generator-logs", generatorId + "-capability.json");
		Files.createDirectories(evidence.getParent());
		JsonObject root = new JsonObject();
		root.addProperty("generatorId", generatorId);
		root.add("types", matrix);
		Files.writeString(evidence, root.toString(), StandardCharsets.UTF_8);
		List<String> failed = new java.util.ArrayList<>();
		matrix.forEach(entry -> {
			JsonObject row = entry.getAsJsonObject();
			String reason = row.get("reasonCode").getAsString();
			if ("GENERATOR_ELEMENT_GENERATION_FAILED".equals(reason)
					|| "GENERATOR_ELEMENT_EXAMPLE_MISSING".equals(reason))
				failed.add(row.get("type").getAsString() + "=" + reason);
		});
		if (!failed.isEmpty())
			throw failure(generatorId, "stage11-generation", "Generation failed for " + failed);
	}

	private static void createStage12ComplexElements(Workspace workspace, String generatorId) throws Exception {
		List<String> types = List.of(
				"livingentity", "biome", "dimension", "gui", "overlay",
				"projectile", "specialentity", "feature", "structure", "fluid", "plant",
				"tool", "enchantment", "damagetype", "potion", "gamerule", "particle", "villagertrade");
		Random random = new Random(12);
		AtomicLong ids = new AtomicLong(1_200);
		UUID workspaceId = UUID.nameUUIDFromBytes(("stage12-" + generatorId).getBytes(StandardCharsets.UTF_8));
		List<String> names = new java.util.ArrayList<>();
		JsonArray evidenceRows = new JsonArray();
		try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
				() -> uuid(ids.incrementAndGet()))) {
			var serializer = new GsonBuilder().registerTypeHierarchyAdapter(GeneratableElement.class,
					new GeneratableElement.GSONAdapter(workspace)).create();
			long revision = 0;
			for (String type : types) {
				ModElementType<?> upstream = ModElementTypeLoader.getModElementType(type);
				List<GeneratableElement> examples = TestWorkspaceDataProvider.getModElementExamplesFor(workspace,
						upstream, false, random);
				if (examples.isEmpty())
					throw failure(generatorId, "stage12-example", "No upstream valid example for " + type);
				GeneratableElement example = switch (type) {
					case "villagertrade" -> examples.stream()
							.filter(candidate -> candidate instanceof net.mcreator.element.types.VillagerTrade trade
									&& !trade.trades.isEmpty())
							.findFirst()
							.orElseThrow(() -> failure(generatorId, "stage12-example",
									"No upstream non-empty Villager Trade example"));
					case "plant" -> examples.stream()
							.filter(candidate -> candidate instanceof net.mcreator.element.types.Plant plant
									&& "double".equals(plant.plantType)
									&& plant.canBePlacedOn.isEmpty()
									&& plant.creativeTabs.isEmpty())
							.findFirst()
							.orElseThrow(() -> failure(generatorId, "stage12-example",
									"No upstream minimal double Plant example"));
					default -> examples.getFirst();
				};
				JsonObject stored = serializer.toJsonTree(example, GeneratableElement.class).getAsJsonObject();
				JsonObject definition = stored.getAsJsonObject("definition").deepCopy();
				stabilizeStage12Fixture(type, definition);
				String name = "stage12_" + type;
				var created = session.uiEntry().execute(create(session.workspaceId(), revision, type, name, definition));
				if (!"committed".equals(created.result().status()))
					throw failure(generatorId, "stage12-core-create",
							type + " returned " + created.result().status() + ": " + created.result().diagnostics());
				revision = created.result().newRevision();
				UUID elementId = UUID.fromString(created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString());

				Stage12Mutation mutation = stage12Mutation(type, definition);
				var updated = session.uiEntry().execute(update(session.workspaceId(), revision, elementId, mutation));
				if (!"committed".equals(updated.result().status()))
					throw failure(generatorId, "stage12-core-update",
							type + " returned " + updated.result().status() + ": " + updated.result().diagnostics());
				revision = updated.result().newRevision();
				names.add(name);

				JsonObject row = new JsonObject();
				row.addProperty("type", type);
				row.addProperty("name", name);
				row.addProperty("editedPath", mutation.path());
				row.add("editedValue", mutation.value().deepCopy());
				evidenceRows.add(row);
			}
		}

		for (String name : names) {
			var element = workspace.getModElementByName(name);
			if (element == null || element.getGeneratableElement() == null)
				throw failure(generatorId, "stage12-persistence", "Missing persisted element " + name);
			if (!workspace.getGenerator().generateElement(element.getGeneratableElement()))
				throw failure(generatorId, "stage12-generation", "Generator rejected element " + name);
		}

		for (var raw : evidenceRows) {
			JsonObject row = raw.getAsJsonObject();
			var element = workspace.getModElementByName(row.get("name").getAsString());
			row.addProperty("generatedFiles", element == null ? 0 : element.getAssociatedFiles().size());
		}
		Path evidence = Path.of("build", "stage12-workspace-generator-logs", generatorId + "-elements.json");
		Files.createDirectories(evidence.getParent());
		JsonObject root = new JsonObject();
		root.addProperty("generatorId", generatorId);
		root.add("elements", evidenceRows);
		Files.writeString(evidence, root.toString(), StandardCharsets.UTF_8);
	}

	private static void stabilizeStage12Fixture(String type, JsonObject definition) {
		switch (type) {
		case "plant" -> {
			definition.addProperty("hasTileEntity", false);
			definition.addProperty("tintType", "No tint");
			definition.addProperty("renderType", 12);
			definition.addProperty("isItemTinted", false);
			definition.addProperty("generateFeature", false);
			definition.add("canBePlacedOn", new JsonArray());
			definition.add("restrictionBiomes", new JsonArray());
		}
		case "tool" -> definition.add("repairItems", new JsonArray());
		default -> {
		}
		}
	}

	private static Stage12Mutation stage12Mutation(String type, JsonObject definition) {
		return switch (type) {
			case "livingentity" -> new Stage12Mutation("/health", alternateNumber(definition, "health", 24, 25));
			case "biome" -> new Stage12Mutation("/spawnInCaves", alternateBoolean(definition, "spawnInCaves"));
			case "dimension" -> new Stage12Mutation("/seaLevel", alternateNumber(definition, "seaLevel", 62, 63));
			case "gui" -> new Stage12Mutation("/width", alternateNumber(definition, "width", 200, 201));
			case "overlay" -> new Stage12Mutation("/priority",
					new JsonPrimitive("HIGH".equals(stringValue(definition, "priority")) ? "NORMAL" : "HIGH"));
			case "projectile" -> new Stage12Mutation("/power", alternateNumber(definition, "power", 2.0, 2.5));
			case "specialentity" -> new Stage12Mutation("/rarity",
					new JsonPrimitive("RARE".equals(stringValue(definition, "rarity")) ? "EPIC" : "RARE"));
			case "feature" -> new Stage12Mutation("/skipPlacement", alternateBoolean(definition, "skipPlacement"));
			case "structure" -> new Stage12Mutation("/size", alternateNumber(definition, "size", 2, 3));
			case "fluid" -> new Stage12Mutation("/canMultiply", alternateBoolean(definition, "canMultiply"));
			case "plant" -> new Stage12Mutation("/disableOffset", alternateBoolean(definition, "disableOffset"));
			case "tool" -> new Stage12Mutation("/immuneToFire", alternateBoolean(definition, "immuneToFire"));
			case "enchantment" -> new Stage12Mutation("/weight", alternateNumber(definition, "weight", 11, 12));
			case "damagetype" -> new Stage12Mutation("/exhaustion", alternateNumber(definition, "exhaustion", 0.2, 0.3));
			case "potion" -> new Stage12Mutation("/potionName",
					new JsonPrimitive("Stage 12 " + stringValue(definition, "potionName")));
			case "gamerule" -> new Stage12Mutation("/defaultValueLogic", alternateBoolean(definition, "defaultValueLogic"));
			case "particle" -> new Stage12Mutation("/alwaysShow", alternateBoolean(definition, "alwaysShow"));
			case "villagertrade" -> new Stage12Mutation("/trades", alternateVillagerTrades(definition));
			default -> throw new IllegalStateException("Unexpected Stage 12 type " + type);
		};
	}

	private static JsonPrimitive alternateBoolean(JsonObject definition, String field) {
		return new JsonPrimitive(!definition.get(field).getAsBoolean());
	}

	private static JsonPrimitive alternateNumber(JsonObject definition, String field, Number first, Number second) {
		JsonElement current = definition.get(field);
		boolean useSecond = current != null && current.isJsonPrimitive()
				&& Double.compare(current.getAsDouble(), first.doubleValue()) == 0;
		Number value = useSecond ? second : first;
		if (value instanceof Integer || value instanceof Long)
			return new JsonPrimitive(value.longValue());
		return new JsonPrimitive(value.doubleValue());
	}

	private static String stringValue(JsonObject definition, String field) {
		JsonElement value = definition.get(field);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	private static JsonArray alternateVillagerTrades(JsonObject definition) {
		JsonArray trades = definition.getAsJsonArray("trades").deepCopy();
		if (trades.isEmpty())
			throw new IllegalStateException("Stage 12 Villager Trade example must contain at least one trade");
		JsonObject first = trades.get(0).getAsJsonObject();
		int current = first.has("maxTrades") ? first.get("maxTrades").getAsInt() : 10;
		first.addProperty("maxTrades", current == 11 ? 12 : 11);
		return trades;
	}

	private static Command create(UUID workspaceId, long revision, String type, String name, JsonObject values) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(10 + revision).toString());
		payload.addProperty("elementType", type);
		payload.addProperty("name", name);
		payload.add("initialValues", values);
		return Command.of(uuid(20 + revision), workspaceId, revision, Operation.CREATE_MOD_ELEMENT, payload);
	}

	private static Command update(UUID workspaceId, long revision, UUID elementId, Stage12Mutation mutation) {
		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", uuid(120 + revision).toString());
		payload.addProperty("elementId", elementId.toString());
		JsonArray changes = new JsonArray();
		JsonObject change = new JsonObject();
		change.addProperty("path", mutation.path());
		change.add("value", mutation.value().deepCopy());
		changes.add(change);
		payload.add("changes", changes);
		return Command.of(uuid(220 + revision), workspaceId, revision, Operation.UPDATE_MOD_ELEMENT, payload);
	}

	private static void requireCommitted(String generatorId, String type, String status) {
		if (!"committed".equals(status))
			throw failure(generatorId, "stage9-persistence", type + " returned status " + status);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}

	private static Path javaHomeFor(String generatorId) {
		if (generatorId.endsWith("1.20.1")) {
			for (Path candidate : List.of(Path.of("jdk", "jdk21_win_64"),
					Path.of("..", "..", "jdk", "jdk21_win_64"))) {
				Path javaHome = candidate.toAbsolutePath().normalize();
				if (Files.isRegularFile(javaHome.resolve("bin/java.exe")))
					return javaHome;
			}
			throw failure(generatorId, "java-home", "Bundled JDK 21 was not found from the active worktree");
		}
		return Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
	}

	private static void seedGradleNetworkProperties(Path gradleHome) throws IOException {
		Path userProperties = Path.of(System.getProperty("user.home"), ".gradle", "gradle.properties");
		List<String> networkProperties = proxyPropertiesFromEnvironment();
		if (Files.isRegularFile(userProperties)) {
			List<String> userNetworkProperties = Files.readAllLines(userProperties, StandardCharsets.UTF_8).stream()
					.filter(line -> line.startsWith("systemProp.http.proxy") || line.startsWith("systemProp.https.proxy")
							|| line.startsWith("systemProp.http.nonProxyHosts"))
					.toList();
			if (networkProperties.isEmpty()) {
				networkProperties = userNetworkProperties;
			} else {
				userNetworkProperties.stream().filter(line -> line.startsWith("systemProp.http.nonProxyHosts"))
						.findFirst().ifPresent(networkProperties::add);
			}
		}
		ensureMojangNonProxyHosts(networkProperties);
		if (networkProperties.isEmpty())
			return;
		Files.createDirectories(gradleHome);
		Files.write(gradleHome.resolve("gradle.properties"), networkProperties, StandardCharsets.UTF_8);
	}

	private static void ensureMojangNonProxyHosts(List<String> properties) {
		if (properties.isEmpty())
			return;
		String prefix = "systemProp.http.nonProxyHosts=";
		String existing = properties.stream().filter(line -> line.startsWith(prefix)).findFirst()
				.map(line -> line.substring(prefix.length())).orElse("");
		properties.removeIf(line -> line.startsWith(prefix));
		String merged = existing.isBlank() ? MOJANG_NON_PROXY_HOSTS : existing + "|" + MOJANG_NON_PROXY_HOSTS;
		properties.add(prefix + merged);
	}

	private static List<String> proxyPropertiesFromEnvironment() {
		String httpProxy = environmentValue("HTTP_PROXY", "http_proxy");
		String httpsProxy = environmentValue("HTTPS_PROXY", "https_proxy");
		if (httpsProxy == null)
			httpsProxy = httpProxy;

		List<String> properties = new ArrayList<>();
		appendProxyProperties(properties, "http", httpProxy);
		appendProxyProperties(properties, "https", httpsProxy);
		return properties;
	}

	private static String environmentValue(String... names) {
		for (String name : names) {
			String value = System.getenv(name);
			if (value != null && !value.isBlank())
				return value.trim();
		}
		return null;
	}

	private static void appendProxyProperties(List<String> properties, String scheme, String proxyValue) {
		if (proxyValue == null)
			return;
		try {
			URI proxy = URI.create(proxyValue);
			if (proxy.getHost() == null || proxy.getPort() < 0)
				return;
			properties.add("systemProp." + scheme + ".proxyHost=" + proxy.getHost());
			properties.add("systemProp." + scheme + ".proxyPort=" + proxy.getPort());
		} catch (IllegalArgumentException ignored) {
			// Fall back to user Gradle proxy properties when an environment proxy is malformed.
		}
	}

	private static void syncGradle(String generatorId, Workspace workspace,
			GeneratorConfiguration configuration, Path gradleHome) {
		syncGradle(generatorId, workspace, configuration, gradleHome, true);
	}

	private static void syncGradle(String generatorId, Workspace workspace,
			GeneratorConfiguration configuration, Path gradleHome, boolean allowMappingsRepair) {
		try {
			GradleDistributionPool.seedForWorkspace(workspace.getWorkspaceFolder().toPath(), gradleHome);
			try (ProjectConnection connection = GradleConnector.newConnector()
					.forProjectDirectory(workspace.getWorkspaceFolder())
					.useGradleUserHomeDir(gradleHome.toFile()).connect()) {
			String syncTask = configuration.getGradleTaskFor("sync_task");
			if (syncTask == null)
				GradleUtils.getGradleTaskLauncher(configuration, connection, "help").run();
			else
				GradleUtils.getGradleTaskLauncher(configuration, connection, syncTask).run();
		}
		} catch (Exception exception) {
			if (allowMappingsRepair
					&& MinecraftMappingsCacheRepair.repairCorruptMappings(workspace.getWorkspaceFolder().toPath()) > 0) {
				System.out.println("Repaired corrupt Minecraft mappings cache; retrying Gradle sync once");
				stopShardGradleDaemons(workspace.getWorkspaceFolder().toPath(), gradleHome);
				syncGradle(generatorId, workspace, configuration, gradleHome, false);
				return;
			}
			throw failure(generatorId, "gradle-sync", exception.getMessage(), exception);
		}
	}

	private static GradleRun runGradle(String generatorId, Path workspaceRoot, Path javaHome, Path gradleHome)
			throws Exception {
		ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "--no-daemon", "--no-build-cache",
				"build", "--stacktrace").directory(workspaceRoot.toFile()).redirectErrorStream(true);
		builder.environment().putAll(GradleUtils.getEnvironment(javaHome.toString()));
		builder.environment().put("GRADLE_USER_HOME", gradleHome.toString());
		Process process = builder.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		});
		boolean completed = process.waitFor(Duration.ofMinutes(20).toMillis(), TimeUnit.MILLISECONDS);
		if (!completed) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
		String log = output.get(30, TimeUnit.SECONDS);
		return new GradleRun(completed, completed ? process.exitValue() : -1, log);
	}

	private static Path findJar(Path libs) throws IOException {
		if (!Files.isDirectory(libs))
			return null;
		try (Stream<Path> paths = Files.list(libs)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
					.sorted().findFirst().orElse(null);
		}
	}

	private static AssertionError failure(String generatorId, String phase, String message) {
		return new AssertionError("WORKSPACE_GENERATOR_GOLDEN_FAILED generator=" + generatorId + " phase=" + phase
				+ " detail=" + (message == null || message.isBlank() ? "unknown" : message));
	}

	private static AssertionError failure(String generatorId, String phase, String message, Throwable cause) {
		AssertionError error = failure(generatorId, phase, message);
		error.initCause(cause);
		return error;
	}

	private static String tail(String output) {
		if (output == null)
			return "";
		return output.length() <= 4000 ? output : output.substring(output.length() - 4000);
	}

	private static String gradleDiagnostic(String output) {
		if (output == null || output.isBlank())
			return "No Gradle output";
		int marker = output.lastIndexOf("Caused by:");
		if (marker < 0)
			marker = output.lastIndexOf("* What went wrong:");
		if (marker < 0)
			return tail(output);
		int end = Math.min(output.length(), marker + 3000);
		return output.substring(marker, end).strip();
	}

	private static void writeGradleLog(String stage, String generatorId, String output) throws IOException {
		Path log = Path.of("build", stage + "-workspace-generator-logs", generatorId + ".log");
		Files.createDirectories(log.getParent());
		Files.writeString(log, output == null ? "" : output, StandardCharsets.UTF_8);
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root))
			return;
		IOException lastFailure = null;
		for (int attempt = 0; attempt < 40 && Files.exists(root); attempt++) {
			try (Stream<Path> paths = Files.walk(root)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException exception) {
						throw new UncheckedIOException(exception);
					}
				});
				return;
			} catch (UncheckedIOException exception) {
				lastFailure = exception.getCause();
				try {
					Thread.sleep(250);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw lastFailure;
				}
			} catch (IOException exception) {
				lastFailure = exception;
				try {
					Thread.sleep(250);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw lastFailure;
				}
			}
		}
		if (Files.exists(root) && lastFailure != null)
			throw lastFailure;
	}

	private record GradleRun(boolean completed, int exitCode, String output) {
	}

	private record Stage12Mutation(String path, JsonElement value) {
	}
}
