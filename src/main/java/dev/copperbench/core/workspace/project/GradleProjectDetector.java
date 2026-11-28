package dev.copperbench.core.workspace.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceFiles;
import dev.copperbench.core.workspace.project.GradleProjectIdentity.Problem;
import dev.copperbench.platform.RuntimePlatform;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies a single-module Fabric or NeoForge Gradle project from its files (ADR-0020). It never runs Gradle, never
 * writes, and reports an unsupported project instead of guessing a version from a range.
 */
public final class GradleProjectDetector {

	public static final String NOT_A_PROJECT = "GRADLE_PROJECT_NOT_FOUND";
	public static final String MCREATOR_WORKSPACE = "GRADLE_PROJECT_IS_MCREATOR_WORKSPACE";
	public static final String WRAPPER_MISSING = "GRADLE_PROJECT_WRAPPER_MISSING";
	public static final String MULTI_MODULE = "GRADLE_PROJECT_MULTI_MODULE";
	public static final String MULTI_LOADER = "GRADLE_PROJECT_MULTI_LOADER";
	public static final String LOADER_UNKNOWN = "GRADLE_PROJECT_LOADER_UNKNOWN";
	public static final String METADATA_INVALID = "GRADLE_PROJECT_METADATA_INVALID";
	public static final String MOD_ID_UNKNOWN = "GRADLE_PROJECT_MOD_ID_UNKNOWN";
	public static final String VERSION_UNKNOWN = "GRADLE_PROJECT_VERSION_UNKNOWN";
	public static final String VERSION_UNSUPPORTED = "GRADLE_PROJECT_VERSION_UNSUPPORTED";

	private static final int MAX_FILE_BYTES = 1024 * 1024;
	private static final Pattern VERSION = Pattern.compile("[0-9]+(\\.[0-9]+){1,2}");
	private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(?:project\\.)?([A-Za-z0-9_.]+)}");
	private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*include(?:Flat)?\\s*[('\"]");
	private static final Pattern MINECRAFT_COORDINATE = Pattern.compile("['\"]com\\.mojang:minecraft:([0-9]+(?:\\.[0-9]+){1,2})['\"]");
	private static final Pattern TOML_SECTION = Pattern.compile("^\\s*\\[\\[?\\s*([A-Za-z0-9_.]+)\\s*]]?");

	private GradleProjectDetector() {
	}

	/** Why the folder cannot be opened as a Gradle project at all. */
	public static final class NotAProjectException extends IOException {
		private final String code;

		NotAProjectException(String code, String message) {
			super(message);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	/** Reads the project's identity; throws only when the folder is not a Gradle project to adopt. */
	public static GradleProjectIdentity inspect(Path folder) throws IOException {
		Path root = WorkspaceFiles.requireDirectory(folder);
		try (var entries = Files.list(root)) {
			if (entries.anyMatch(entry -> entry.getFileName().toString().endsWith(".mcreator") && Files.isRegularFile(entry)))
				throw new NotAProjectException(MCREATOR_WORKSPACE, "This folder holds a Copperbench workspace; open its .mcreator file instead.");
		}
		String build = firstText(root, "build.gradle", "build.gradle.kts");
		if (build == null)
			throw new NotAProjectException(NOT_A_PROJECT, "No build.gradle or build.gradle.kts was found in this folder.");
		String settings = firstText(root, "settings.gradle", "settings.gradle.kts");
		Properties properties = new Properties();
		String rawProperties = text(root, "gradle.properties");
		List<Problem> problems = new ArrayList<>();
		if (rawProperties != null) try {
			properties.load(new StringReader(rawProperties));
		} catch (IllegalArgumentException invalid) {
			problems.add(new Problem(METADATA_INVALID, "gradle.properties could not be read: " + invalid.getMessage()));
		}
		String launcher = RuntimePlatform.current().operatingSystem() == RuntimePlatform.OperatingSystem.WINDOWS ? "gradlew.bat" : "gradlew";
		for (String part : List.of(launcher, "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")) {
			if (file(root, part)) continue;
			problems.add(new Problem(WRAPPER_MISSING, "The project's Gradle Wrapper is incomplete: " + part
					+ " is missing. Copperbench builds with it and does not add one."));
			break;
		}
		if (settings != null && INCLUDE.matcher(withoutComments(settings)).find())
			problems.add(new Problem(MULTI_MODULE, "The settings script includes other modules. Only single-module projects are supported."));

		Metadata fabric = fabric(root, properties, problems);
		Metadata neoForge = neoForge(root, properties, build + "\n" + (settings == null ? "" : settings), problems);
		String loader = GradleProjectIdentity.UNKNOWN;
		Metadata metadata = new Metadata(null, null);
		if (fabric != null && neoForge != null) {
			problems.add(new Problem(MULTI_LOADER, "The project carries both Fabric and NeoForge metadata. Multi-loader projects are not supported yet."));
		} else if (fabric != null || neoForge != null) {
			loader = fabric != null ? "fabric" : "neoforge";
			metadata = fabric != null ? fabric : neoForge;
		} else if (problems.stream().noneMatch(problem -> problem.code().equals(METADATA_INVALID) || problem.code().equals(LOADER_UNKNOWN))) {
			problems.add(new Problem(LOADER_UNKNOWN, "No fabric.mod.json or neoforge.mods.toml was found under src/main/resources or src/main/templates."));
		}
		boolean identified = !loader.equals(GradleProjectIdentity.UNKNOWN);
		if (identified && metadata.modId() == null)
			problems.add(new Problem(MOD_ID_UNKNOWN, "The mod metadata does not state a mod ID that gradle.properties resolves."));

		String version = minecraftVersion(properties, build);
		String generatorId = null;
		if (version == null) {
			problems.add(new Problem(VERSION_UNKNOWN, "No exact Minecraft version was found in gradle.properties (minecraft_version) or as a literal com.mojang:minecraft dependency."));
		} else if (identified) {
			String candidate = loader + "-" + version;
			var catalog = VersionTrackCatalog.official();
			// The preview contract's evidence covers generated workspaces only, so a preview track is not a target here.
			if (catalog.decision(candidate).generatable())
				generatorId = candidate;
			else if (catalog.requiresPreview(candidate))
				problems.add(new Problem(VERSION_UNSUPPORTED, "Minecraft " + version + " on " + loader
						+ " is a preview track, which is not available for existing projects yet."));
			else
				problems.add(new Problem(VERSION_UNSUPPORTED, "Minecraft " + version + " on " + loader + " is not one of Copperbench's version tracks."));
		}
		String fallback = metadata.modId() != null ? metadata.modId() : root.getFileName() == null ? "gradle-project" : root.getFileName().toString();
		String name = metadata.name() != null ? metadata.name() : fallback;
		return new GradleProjectIdentity(name.length() > 160 ? name.substring(0, 160) : name, metadata.modId(), loader,
				version == null ? GradleProjectIdentity.UNKNOWN : version, generatorId, problems);
	}

	private record Metadata(String modId, String name) {
	}

	private static Metadata fabric(Path root, Properties properties, List<Problem> problems) throws IOException {
		String raw = text(root, "src/main/resources/fabric.mod.json");
		if (raw == null) return null;
		try {
			JsonElement parsed = JsonParser.parseString(raw);
			if (!parsed.isJsonObject()) throw new JsonParseException("expected an object");
			JsonObject document = parsed.getAsJsonObject();
			return new Metadata(modId(resolve(string(document, "id"), properties)), label(resolve(string(document, "name"), properties)));
		} catch (JsonParseException | IllegalStateException invalid) {
			problems.add(new Problem(METADATA_INVALID, "src/main/resources/fabric.mod.json is not valid JSON."));
			return null;
		}
	}

	private static Metadata neoForge(Path root, Properties properties, String scripts, List<Problem> problems) throws IOException {
		for (String folder : List.of("src/main/resources/META-INF/", "src/main/templates/META-INF/")) {
			String raw = text(root, folder + "neoforge.mods.toml");
			if (raw == null) {
				// NeoForge 1.20.1 kept Forge's metadata file, so the build scripts must name NeoForge.
				raw = text(root, folder + "mods.toml");
				if (raw != null && !withoutComments(scripts).contains("net.neoforged")) {
					problems.add(new Problem(LOADER_UNKNOWN, folder + "mods.toml was found, but the build scripts do not use NeoForge. Forge projects are not supported."));
					return null;
				}
			}
			if (raw != null) return new Metadata(modId(resolve(tomlMods(raw, "modId"), properties)),
					label(resolve(tomlMods(raw, "displayName"), properties)));
		}
		return null;
	}

	/** The first [[mods]] table's basic string; enough for the MDK layouts and no more. */
	private static String tomlMods(String raw, String key) {
		Pattern entry = Pattern.compile("^\\s*" + key + "\\s*=\\s*[\"']([^\"']*)[\"']");
		boolean mods = false;
		for (String line : raw.split("\\R")) {
			Matcher section = TOML_SECTION.matcher(line);
			if (section.find()) {
				if (mods) return null;
				mods = section.group(1).equals("mods");
				continue;
			}
			Matcher value = entry.matcher(line);
			if (mods && value.find()) return value.group(1);
		}
		return null;
	}

	private static String minecraftVersion(Properties properties, String build) {
		String declared = resolve(properties.getProperty("minecraft_version"), properties);
		if (declared != null && VERSION.matcher(declared.trim()).matches()) return declared.trim();
		Set<String> literals = new LinkedHashSet<>();
		Matcher coordinate = MINECRAFT_COORDINATE.matcher(withoutComments(build));
		while (coordinate.find()) literals.add(coordinate.group(1));
		return literals.size() == 1 ? literals.iterator().next() : null;
	}

	/** Only a value that is entirely one placeholder is resolved; anything else is a value Gradle computes. */
	private static String resolve(String value, Properties properties) {
		if (value == null) return null;
		Matcher placeholder = PLACEHOLDER.matcher(value.trim());
		if (placeholder.matches()) value = properties.getProperty(placeholder.group(1));
		return value == null || value.contains("${") ? null : value;
	}

	private static String modId(String value) {
		return value != null && MOD_ID.matcher(value).matches() ? value : null;
	}

	private static String label(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
	}

	private static String withoutComments(String script) {
		return script.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)(?<!:)//.*$", " ");
	}

	private static String firstText(Path root, String... names) throws IOException {
		for (String name : names) {
			String value = text(root, name);
			if (value != null) return value;
		}
		return null;
	}

	private static boolean file(Path root, String relative) throws IOException {
		return Files.isRegularFile(WorkspaceFiles.canonicalPath(root.resolve(relative)), LinkOption.NOFOLLOW_LINKS);
	}

	private static String text(Path root, String relative) throws IOException {
		Path path = WorkspaceFiles.canonicalPath(root.resolve(relative));
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null;
		if (Files.size(path) > MAX_FILE_BYTES) throw new IOException(relative + " is larger than 1 MiB.");
		try {
			String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
			return value.startsWith("\uFEFF") ? value.substring(1) : value;
		} catch (CharacterCodingException invalid) {
			throw new IOException(relative + " is not UTF-8 text.", invalid);
		}
	}
}
