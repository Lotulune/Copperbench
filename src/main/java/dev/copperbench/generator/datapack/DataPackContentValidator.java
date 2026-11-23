/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import dev.copperbench.generator.GradleWorkspaceBackend.ValidationIssue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks the files a data pack loads from {@code src/main}, whether a generator or a person wrote them: JSON that
 * Minecraft can parse, resource paths it accepts, folder names the target version reads, tag entries, and references
 * into the workspace's own namespace. It reports only what Minecraft would skip or fail to load. References into other
 * namespaces are not resolved, because the game, a mod or another pack may define them.
 */
final class DataPackContentValidator {
	static final int MAX_FILES = 20_000, MAX_ISSUES = 50;
	private static final long MAX_PARSE_BYTES = 8L * 1024 * 1024;
	private static final Gson GSON = new Gson();
	private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
	private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
	private static final Set<String> RESOURCE_EXTENSIONS = Set.of("json", "mcfunction", "nbt");
	/** Minecraft 1.21 renamed these folders to singular registry names; 1.20.x packs still use the plural ones. */
	private static final Map<String, String> RENAMED_FOLDERS = Map.of("advancement", "advancements", "function", "functions",
			"loot_table", "loot_tables", "recipe", "recipes", "structure", "structures", "predicate", "predicates",
			"item_modifier", "item_modifiers");
	private static final Map<String, String> RENAMED_TAG_FOLDERS = Map.of("item", "items", "block", "blocks",
			"entity_type", "entity_types", "fluid", "fluids", "game_event", "game_events", "function", "functions");
	/** Registries whose entries come only from data, so an entry in the pack's own namespace exists only as its file. */
	private static final Set<String> DATA_DRIVEN_1_20 = Set.of("worldgen/biome", "worldgen/configured_carver",
			"worldgen/configured_feature", "worldgen/density_function", "worldgen/flat_level_generator_preset",
			"worldgen/multi_noise_biome_source_parameter_list", "worldgen/noise", "worldgen/noise_settings",
			"worldgen/placed_feature", "worldgen/processor_list", "worldgen/structure", "worldgen/structure_set",
			"worldgen/template_pool", "worldgen/world_preset", "damage_type", "dimension_type", "chat_type",
			"trim_material", "trim_pattern");
	private static final Set<String> DATA_DRIVEN_1_21 = Stream.concat(DATA_DRIVEN_1_20.stream(), Stream.of("banner_pattern",
			"enchantment", "enchantment_provider", "jukebox_song", "painting_variant", "wolf_variant")).collect(Collectors.toUnmodifiableSet());
	/** Vanilla tag folders; tags of a mod registry sit under the mod's namespace, where the tag path cannot be split reliably. */
	private static final Set<String> VANILLA_TAG_FOLDERS = Stream.concat(Stream.concat(DATA_DRIVEN_1_21.stream(),
			RENAMED_TAG_FOLDERS.keySet().stream()), Stream.concat(RENAMED_TAG_FOLDERS.values().stream(),
			Stream.of("point_of_interest_type", "cat_variant", "instrument"))).collect(Collectors.toUnmodifiableSet());
	/** Tokens that place a {@code function} argument in command position rather than inside chat text. */
	private static final Set<String> CALL_PREFIXES = Set.of("run", "if", "unless", "schedule");

	private final Path sourceRoot;
	private final boolean pluralFolders;
	private final String ownNamespace;
	private final List<ValidationIssue> issues = new ArrayList<>();
	private final Set<String> files = new HashSet<>(), reportedFolders = new HashSet<>();
	private final List<Reference> references = new ArrayList<>();
	private int omitted;

	private DataPackContentValidator(Path sourceRoot, String generatorId, String ownNamespace) {
		this.sourceRoot = sourceRoot;
		this.pluralFolders = generatorId.equals("datapack-1.20.1");
		this.ownNamespace = "minecraft".equals(ownNamespace) ? null : ownNamespace;
	}

	/**
	 * @param sourceRoot   the workspace's {@code src/main} folder, which holds {@code pack.mcmeta} and {@code data/}
	 * @param ownNamespace the workspace namespace whose references are resolved, or {@code null} to skip reference checks
	 */
	static List<ValidationIssue> validate(Path sourceRoot, String generatorId, String ownNamespace, Consumer<String> log)
			throws IOException {
		return new DataPackContentValidator(sourceRoot, generatorId, ownNamespace).run(log);
	}

	private List<ValidationIssue> run(Consumer<String> log) throws IOException {
		checkPackMetadata();
		Path data = sourceRoot.resolve("data");
		if (Files.isSymbolicLink(data) || !Files.isDirectory(data, LinkOption.NOFOLLOW_LINKS)) {
			log.accept("Data pack content check: no src/main/data folder to check");
			return finish();
		}
		List<String> listed = new ArrayList<>();
		boolean[] truncated = { false };
		Files.walkFileTree(data, EnumSet.noneOf(FileVisitOption.class), 32, new SimpleFileVisitor<>() {
			@Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
				return Files.isSymbolicLink(directory) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}
			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
				if (!attributes.isRegularFile()) return FileVisitResult.CONTINUE;
				if (listed.size() >= MAX_FILES) { truncated[0] = true; return FileVisitResult.TERMINATE; }
				listed.add("data/" + data.relativize(file).toString().replace('\\', '/'));
				return FileVisitResult.CONTINUE;
			}
		});
		listed.sort(String::compareTo);
		files.addAll(listed);
		for (String relative : listed) checkFile(relative);
		if (truncated[0]) {
			log.accept("Data pack content check: stopped after " + MAX_FILES + " files; references were not checked");
		} else if (ownNamespace == null) {
			log.accept("Data pack content check: " + listed.size() + " files; no workspace namespace, so references were not checked");
		} else {
			for (Reference reference : references) if (!files.contains(reference.expected()))
				issue("DATA_PACK_REFERENCE_MISSING", reference.source(), reference.where() + " refers to " + reference.kind() + " "
						+ reference.id() + ", which this pack does not define (expected src/main/" + reference.expected() + ").");
			log.accept("Data pack content check: " + listed.size() + " files, " + references.size()
					+ " references into namespace " + ownNamespace);
		}
		return finish();
	}

	private void checkPackMetadata() throws IOException {
		// A missing pack.mcmeta is reported by generation, which needs it first.
		if (!Files.isRegularFile(sourceRoot.resolve("pack.mcmeta"), LinkOption.NOFOLLOW_LINKS)) return;
		JsonElement json = parse("pack.mcmeta", "src/main/pack.mcmeta", false);
		if (json == null) return;
		JsonObject pack = object(json.getAsJsonObject(), "pack");
		boolean format = pack != null && (pack.has("min_format")
				|| pack.get("pack_format") instanceof com.google.gson.JsonPrimitive value && value.isNumber());
		if (!format || !pack.has("description"))
			issue("DATA_PACK_CONTENT_INVALID", "src/main/pack.mcmeta", "needs a \"pack\" object with \"pack_format\" and"
					+ " \"description\"; Minecraft does not list the pack without them.");
	}

	private void checkFile(String relative) throws IOException {
		String extension = extension(relative);
		if (!RESOURCE_EXTENSIONS.contains(extension)) return;
		String source = "src/main/" + relative;
		String[] parts = relative.split("/");
		if (parts.length < 4) {
			issue("DATA_PACK_RESOURCE_PATH_INVALID", source, "Minecraft reads data only from data/<namespace>/<folder>/,"
					+ " so this file is never loaded.");
			return;
		}
		String namespace = parts[1];
		if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(relative.substring(("data/" + namespace + "/").length())).matches()) {
			issue("DATA_PACK_RESOURCE_PATH_INVALID", source, "Minecraft skips this file: names may use only lowercase letters,"
					+ " digits, '_', '-' and '.', with '/' between folders.");
			return;
		}
		boolean current = currentFolder(namespace, parts);
		if (extension.equals("mcfunction")) {
			if (current && parts[2].equals(folder("function"))) checkFunction(relative, source);
			return;
		}
		if (!extension.equals("json")) return;
		boolean tag = parts[2].equals("tags");
		JsonElement json = parse(relative, source, tag);
		if (json == null || !current) return;
		JsonObject object = json.getAsJsonObject();
		if (tag) checkTag(source, parts, object);
		else if (parts[2].equals(folder("recipe"))) {
			if (!(object.get("type") instanceof com.google.gson.JsonPrimitive type && type.isString()))
				issue("DATA_PACK_CONTENT_INVALID", source, "a recipe needs a \"type\"; Minecraft skips it without one.");
		} else if (parts[2].equals(folder("advancement"))) checkAdvancement(source, object);
		else if (parts[2].equals(folder("loot_table"))) lootTableReferences(source, object, "");
	}

	/** Reports a folder the target version never reads, once per folder, unless the pack also has the current one (multi-version packs keep both). */
	private boolean currentFolder(String namespace, String[] parts) {
		String folder, replacement;
		if (parts[2].equals("tags")) {
			if (parts.length < 5) return true;
			folder = "tags/" + parts[3];
			String renamed = renamed(RENAMED_TAG_FOLDERS, parts[3]);
			replacement = renamed == null ? null : "tags/" + renamed;
		} else {
			folder = parts[2];
			replacement = renamed(RENAMED_FOLDERS, folder);
		}
		if (replacement == null) return true;
		String location = "src/main/data/" + namespace + "/";
		if (!Files.isDirectory(sourceRoot.resolve("data/" + namespace + "/" + replacement), LinkOption.NOFOLLOW_LINKS)
				&& reportedFolders.add(namespace + "/" + folder))
			issue("DATA_PACK_FOLDER_RENAMED", location + folder, "Minecraft " + (pluralFolders ? "1.20.x" : "1.21 and later")
					+ " reads " + replacement + "/, not " + folder + "/, so files here are not loaded. Move them to "
					+ location + replacement + "/.");
		return false;
	}

	/** The folder this version reads instead of {@code folder}, or {@code null} when {@code folder} is current. */
	private String renamed(Map<String, String> singularToPlural, String folder) {
		if (pluralFolders) return singularToPlural.get(folder);
		return singularToPlural.entrySet().stream().filter(entry -> entry.getValue().equals(folder))
				.map(Map.Entry::getKey).findFirst().orElse(null);
	}

	private String folder(String singular) { return pluralFolders ? RENAMED_FOLDERS.get(singular) : singular; }

	private String tagFolder(String singular) { return pluralFolders ? RENAMED_TAG_FOLDERS.get(singular) : singular; }

	private void checkTag(String source, String[] parts, JsonObject tag) {
		boolean worldgen = parts.length > 4 && parts[3].equals("worldgen");
		if (parts.length < 5 || worldgen && parts.length < 6) {
			issue("DATA_PACK_RESOURCE_PATH_INVALID", source, "tags belong in tags/<registry>/ (worldgen tags in"
					+ " tags/worldgen/<registry>/), so this file is never loaded.");
			return;
		}
		String registry = worldgen ? "worldgen/" + parts[4] : parts[3];
		if (!(tag.get("values") instanceof com.google.gson.JsonArray values)) {
			issue("DATA_PACK_TAG_INVALID", source, "a tag needs a \"values\" array; Minecraft fails to load it.");
			return;
		}
		if (tag.has("replace") && !(tag.get("replace") instanceof com.google.gson.JsonPrimitive replace && replace.isBoolean()))
			issue("DATA_PACK_TAG_INVALID", source, "\"replace\" must be true or false.");
		Set<String> dataDriven = pluralFolders ? DATA_DRIVEN_1_20 : DATA_DRIVEN_1_21;
		for (int index = 0; index < values.size(); index++) {
			JsonElement value = values.get(index);
			String where = "values[" + index + "]", id;
			boolean required = true;
			if (value instanceof com.google.gson.JsonPrimitive primitive && primitive.isString()) id = primitive.getAsString();
			else if (value instanceof JsonObject entry && entry.get("id") instanceof com.google.gson.JsonPrimitive primitive && primitive.isString()) {
				id = primitive.getAsString();
				JsonElement flag = entry.get("required");
				if (flag != null && !(flag instanceof com.google.gson.JsonPrimitive bool && bool.isBoolean())) {
					issue("DATA_PACK_TAG_INVALID", source, where + " \"required\" must be true or false.");
					continue;
				}
				required = flag == null || flag.getAsBoolean();
			} else {
				issue("DATA_PACK_TAG_INVALID", source, where + " must be an ID string or an object with an \"id\".");
				continue;
			}
			boolean tagReference = id.startsWith("#");
			String location = normalize(tagReference ? id.substring(1) : id);
			if (location == null) {
				issue("DATA_PACK_TAG_INVALID", source, where + " \"" + id + "\" is not a valid resource ID.");
				continue;
			}
			if (!required) continue;
			if (tagReference) {
				if (VANILLA_TAG_FOLDERS.contains(registry)) reference(source, where, "tag", "#" + location, "tags/" + registry, location, "json");
			} else if (registry.equals(tagFolder("function"))) reference(source, where, "function", location, folder("function"), location, "mcfunction");
			else if (dataDriven.contains(registry)) reference(source, where, registry + " entry", location, registry, location, "json");
		}
	}

	private void checkAdvancement(String source, JsonObject advancement) {
		reference(source, "\"parent\"", "advancement", string(advancement, "parent"), folder("advancement"), "json");
		JsonObject rewards = object(advancement, "rewards");
		if (rewards == null) return;
		reference(source, "rewards.function", "function", string(rewards, "function"), folder("function"), "mcfunction");
		for (String key : List.of("loot", "recipes"))
			if (rewards.get(key) instanceof com.google.gson.JsonArray ids) for (int index = 0; index < ids.size(); index++)
				if (ids.get(index) instanceof com.google.gson.JsonPrimitive id && id.isString())
					reference(source, "rewards." + key + "[" + index + "]", key.equals("loot") ? "loot table" : "recipe",
							id.getAsString(), folder(key.equals("loot") ? "loot_table" : "recipe"), "json");
	}

	/** Loot table entries of type loot_table name another table: {@code value} since 1.21, {@code name} before. */
	private void lootTableReferences(String source, JsonElement element, String where) {
		if (element instanceof com.google.gson.JsonArray array) {
			for (int index = 0; index < array.size(); index++) lootTableReferences(source, array.get(index), where + "[" + index + "]");
			return;
		}
		if (!(element instanceof JsonObject object)) return;
		String type = string(object, "type");
		if ("minecraft:loot_table".equals(type) || "loot_table".equals(type))
			reference(source, where.isEmpty() ? "entry" : where, "loot table", string(object, pluralFolders ? "name" : "value"),
					folder("loot_table"), "json");
		for (var entry : object.entrySet())
			lootTableReferences(source, entry.getValue(), where.isEmpty() ? entry.getKey() : where + "." + entry.getKey());
	}

	private void checkFunction(String relative, String source) throws IOException {
		Path file = sourceRoot.resolve(relative);
		if (Files.size(file) > MAX_PARSE_BYTES) return;
		String[] lines = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\\R", -1);
		for (int number = 0; number < lines.length; number++) {
			String line = lines[number].strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			if (line.startsWith("$")) line = line.substring(1);
			String[] tokens = line.split("\\s+");
			for (int index = 0; index + 1 < tokens.length; index++) {
				if (!tokens[index].equals("function") || index > 0 && !CALL_PREFIXES.contains(tokens[index - 1])) continue;
				String id = tokens[index + 1];
				// Macro lines substitute $(name) when the function runs.
				if (id.contains("$(")) continue;
				String where = "line " + (number + 1);
				if (id.startsWith("#")) {
					String location = normalize(id.substring(1));
					if (location != null) reference(source, where, "function tag", "#" + location, "tags/" + tagFolder("function"), location, "json");
				} else reference(source, where, "function", id, folder("function"), "mcfunction");
			}
		}
	}

	private void reference(String source, String where, String kind, String id, String folder, String extension) {
		String location = id == null ? null : normalize(id);
		if (location != null) reference(source, where, kind, location, folder, location, extension);
	}

	private void reference(String source, String where, String kind, String display, String folder, String location, String extension) {
		int colon = location.indexOf(':');
		if (ownNamespace == null || !location.substring(0, colon).equals(ownNamespace)) return;
		references.add(new Reference(source, where, kind, display, "data/" + ownNamespace + "/" + folder + "/"
				+ location.substring(colon + 1) + "." + extension));
	}

	/** {@code namespace:path} for a valid resource ID, with Minecraft's default namespace; {@code null} otherwise. */
	private static String normalize(String id) {
		int colon = id.indexOf(':');
		String namespace = colon < 1 ? "minecraft" : id.substring(0, colon), path = id.substring(colon + 1);
		return NAMESPACE.matcher(namespace).matches() && PATH.matcher(path).matches() ? namespace + ":" + path : null;
	}

	/** Parses like Minecraft: tags leniently, other data and pack.mcmeta with Gson's legacy strict reader. The root must be an object. */
	private JsonElement parse(String relative, String source, boolean lenient) throws IOException {
		Path file = sourceRoot.resolve(relative);
		if (Files.size(file) > MAX_PARSE_BYTES) return null;
		String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		JsonElement json;
		try {
			if (lenient) json = JsonParser.parseString(text);
			else try (JsonReader reader = new JsonReader(new StringReader(text))) { json = GSON.getAdapter(JsonElement.class).read(reader); }
		} catch (IOException | RuntimeException invalid) {
			issue("DATA_PACK_JSON_INVALID", source, "Minecraft cannot parse this JSON: " + reason(invalid));
			return null;
		}
		if (json == null || !json.isJsonObject()) {
			issue("DATA_PACK_JSON_INVALID", source, "the file must contain a JSON object.");
			return null;
		}
		return json;
	}

	private static String reason(Throwable failure) {
		Throwable cause = failure;
		while (cause.getCause() != null) cause = cause.getCause();
		String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
		int troubleshooting = message.indexOf("\nSee ");
		return troubleshooting < 0 ? message : message.substring(0, troubleshooting);
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent.get(key) instanceof JsonObject child ? child : null;
	}

	private static String string(JsonObject parent, String key) {
		return parent.get(key) instanceof com.google.gson.JsonPrimitive value && value.isString() ? value.getAsString() : null;
	}

	private static String extension(String path) {
		int dot = path.lastIndexOf('.'), slash = path.lastIndexOf('/');
		return dot <= slash ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private void issue(String code, String source, String problem) {
		if (issues.size() >= MAX_ISSUES) { omitted++; return; }
		issues.add(new ValidationIssue(code, source + ": " + problem, "/" + source, null));
	}

	private List<ValidationIssue> finish() {
		List<ValidationIssue> result = new ArrayList<>(issues);
		if (omitted > 0) result.add(new ValidationIssue("DATA_PACK_CONTENT_ISSUES_OMITTED", omitted
				+ " more data pack problems were found; fix the ones listed and validate again.", null, null));
		return List.copyOf(result);
	}

	private record Reference(String source, String where, String kind, String id, String expected) { }
}
