/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.migration.MigrationReport.Disposition;
import dev.copperbench.migration.MigrationReport.MigrationItem;
import dev.copperbench.migration.MigrationReport.SemanticChange;
import dev.copperbench.migration.MigrationReport.SemanticComparison;
import dev.copperbench.tracks.VersionTrackCatalog;
import dev.copperbench.release.ElementCoverageCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Copy-only Fabric/NeoForge migration. The source workspace is hashed before
 * and after execution; a failed or successful copy never writes back to source.
 */
public final class LoaderMigrationService {

	private static final Set<String> SLICE_TYPES = Set.copyOf(ElementCoverageCatalog.FIRST_PARTY_SLICE);
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final VersionTrackCatalog catalog;

	public LoaderMigrationService(VersionTrackCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog);
	}

	public MigrationReport preview(WorkspaceState source, String targetGeneratorId) {
		return preview(source, targetGeneratorId, sourceHash(source), null);
	}

	public MigrationReport execute(WorkspaceState source, String targetGeneratorId, Path sourceRoot, Path targetRoot)
			throws IOException {
		Objects.requireNonNull(source);
		Objects.requireNonNull(targetGeneratorId);
		Objects.requireNonNull(targetRoot);
		String before = sourceRoot == null ? sourceHash(source) : WorkspaceTreeHasher.hash(sourceRoot);
		MigrationReport preview = preview(source, targetGeneratorId, before, null);
		if (!catalog.migratable(generatorId(source), targetGeneratorId))
			return new MigrationReport("loader", generatorId(source), targetGeneratorId, before, null, true, false,
					preview.items());
		Path destination = targetRoot.toAbsolutePath().normalize();
		Path origin = sourceRoot == null ? null : sourceRoot.toAbsolutePath().normalize();
		if (sourceRoot != null) {
			if (destination.startsWith(origin) || origin.startsWith(destination))
				throw new IllegalArgumentException("Migration target must be outside the source workspace");
			if (Files.exists(destination)) {
				try (var children = Files.list(destination)) {
					if (children.findAny().isPresent())
						throw new IllegalArgumentException("Migration target must be an empty directory");
				}
			}
			copyTree(origin, destination);
			rewriteGenerator(destination, targetGeneratorId, source.generator());
		} else {
			Files.createDirectories(destination);
			writeProjection(destination, source, targetGeneratorId);
		}
		String after = sourceRoot == null ? sourceHash(source) : WorkspaceTreeHasher.hash(sourceRoot);
		boolean unchanged = before.equals(after);
		boolean complete = unchanged && preview.items().stream()
				.noneMatch(item -> item.disposition() == Disposition.BLOCKED);
		SemanticComparison comparison = origin == null ? null
				: semanticComparison(origin, destination, generatorId(source), targetGeneratorId);
		MigrationReport result = new MigrationReport("loader", generatorId(source), targetGeneratorId, before,
				destination.toString().replace('\\', '/'), unchanged, complete, preview.items(), comparison);
		writeReport(destination, result);
		return result;
	}

	private static SemanticComparison semanticComparison(Path sourceRoot, Path targetRoot, String sourceGeneratorId,
			String targetGeneratorId) throws IOException {
		JsonObject sourceWorkspace = readJsonObject(sourceRoot.resolve("workspace.mcreator"));
		JsonObject targetWorkspace = readJsonObject(targetRoot.resolve("workspace.mcreator"));
		String actualTargetGenerator = workspaceGeneratorId(targetWorkspace);
		boolean generatorChanged = !sourceGeneratorId.equals(actualTargetGenerator)
				&& targetGeneratorId.equals(actualTargetGenerator);

		JsonObject normalizedSource = sourceWorkspace.deepCopy();
		JsonObject normalizedTarget = targetWorkspace.deepCopy();
		removeGeneratorMetadata(normalizedSource);
		removeGeneratorMetadata(normalizedTarget);
		boolean workspaceMetadataPreserved = normalizedSource.equals(normalizedTarget);

		Map<String, SemanticElement> sourceElements = semanticElements(sourceRoot.resolve("elements"));
		Map<String, SemanticElement> targetElements = semanticElements(targetRoot.resolve("elements"));
		List<SemanticChange> changes = new ArrayList<>();
		changes.add(new SemanticChange("/generator", sourceGeneratorId + " -> " + actualTargetGenerator,
				"generator", generatorChanged ? "changed" : "unexpected"));
		if (!workspaceMetadataPreserved)
			changes.add(new SemanticChange("/workspace", "workspace.mcreator", "workspace_metadata", "changed"));

		int preserved = 0;
		int changed = 0;
		int added = 0;
		int removed = 0;
		for (Map.Entry<String, SemanticElement> entry : sourceElements.entrySet()) {
			SemanticElement target = targetElements.get(entry.getKey());
			if (target == null) {
				removed++;
				changes.add(new SemanticChange("/" + entry.getKey(), entry.getValue().name(), entry.getValue().type(),
						"removed"));
			} else if (entry.getValue().semanticallyEquals(target)) {
				preserved++;
			} else {
				changed++;
				changes.add(new SemanticChange("/" + entry.getKey(), entry.getValue().name(), entry.getValue().type(),
						"changed"));
			}
		}
		for (Map.Entry<String, SemanticElement> entry : targetElements.entrySet()) {
			if (sourceElements.containsKey(entry.getKey())) continue;
			added++;
			changes.add(new SemanticChange("/" + entry.getKey(), entry.getValue().name(), entry.getValue().type(),
					"added"));
		}
		changes.sort(Comparator.comparing(SemanticChange::path));
		return new SemanticComparison(generatorChanged, workspaceMetadataPreserved, preserved, changed, added, removed,
				changes);
	}

	private static JsonObject readJsonObject(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static String workspaceGeneratorId(JsonObject document) {
		if (document.has("workspaceSettings") && document.get("workspaceSettings").isJsonObject()) {
			JsonObject settings = document.getAsJsonObject("workspaceSettings");
			if (settings.has("currentGenerator") && settings.get("currentGenerator").isJsonPrimitive())
				return settings.get("currentGenerator").getAsString();
		}
		if (document.has("dev.copperbench") && document.get("dev.copperbench").isJsonObject()) {
			JsonObject product = document.getAsJsonObject("dev.copperbench");
			if (product.has("generator") && product.get("generator").isJsonObject()) {
				JsonObject generator = product.getAsJsonObject("generator");
				if (generator.has("id") && generator.get("id").isJsonPrimitive()) return generator.get("id").getAsString();
			}
		}
		return "";
	}

	private static void removeGeneratorMetadata(JsonObject document) {
		if (document.has("workspaceSettings") && document.get("workspaceSettings").isJsonObject())
			document.getAsJsonObject("workspaceSettings").remove("currentGenerator");
		if (document.has("dev.copperbench") && document.get("dev.copperbench").isJsonObject()) {
			JsonObject product = document.getAsJsonObject("dev.copperbench");
			product.remove("generator");
			if (product.isEmpty()) document.remove("dev.copperbench");
		}
	}

	private static Map<String, SemanticElement> semanticElements(Path elementsRoot) throws IOException {
		Map<String, SemanticElement> elements = new TreeMap<>();
		if (!Files.isDirectory(elementsRoot)) return elements;
		try (var paths = Files.walk(elementsRoot)) {
			for (Path path : paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".mod.json")).toList()) {
				String relative = "elements/" + elementsRoot.relativize(path).toString().replace('\\', '/');
				String raw = Files.readString(path, StandardCharsets.UTF_8);
				JsonElement json = null;
				try {
					json = JsonParser.parseString(raw);
				} catch (RuntimeException ignored) {
					// Preserve malformed-but-copied definitions by exact text instead of blocking comparison.
				}
				String fileName = path.getFileName().toString();
				String name = fileName.substring(0, fileName.length() - ".mod.json".length());
				String type = json != null && json.isJsonObject() && json.getAsJsonObject().has("_type")
						&& json.getAsJsonObject().get("_type").isJsonPrimitive()
						? json.getAsJsonObject().get("_type").getAsString() : "mod_element";
				elements.put(relative, new SemanticElement(name, type, raw, json));
			}
		}
		return elements;
	}

	private record SemanticElement(String name, String type, String raw, JsonElement json) {
		boolean semanticallyEquals(SemanticElement other) {
			if (json != null && other.json != null) return json.equals(other.json);
			return raw.equals(other.raw);
		}
	}

	private MigrationReport preview(WorkspaceState source, String targetGeneratorId, String sourceHash,
			String targetDirectory) {
		String sourceGenerator = generatorId(source);
		List<MigrationItem> items = new ArrayList<>();
		if (!catalog.migratable(sourceGenerator, targetGeneratorId)) {
			items.add(new MigrationItem("/generator", sourceGenerator, "generator", Disposition.BLOCKED,
					catalog.decision(targetGeneratorId).reasonCode(),
					"Choose a first-party Fabric/NeoForge pair on the same Minecraft version."));
			items.sort(Comparator.comparing(MigrationItem::path));
			return new MigrationReport("loader", sourceGenerator, targetGeneratorId, sourceHash, targetDirectory, true,
					false, items);
		}
		items.add(new MigrationItem("/generator", sourceGenerator, "generator", Disposition.SUPPORTED,
				"GENERATOR_SWITCH", "The target copy uses " + targetGeneratorId + " as its single active generator."));
		for (Element element : source.elements()) {
			String path = "/elements/" + element.id();
			if (!SLICE_TYPES.contains(element.type())) {
				items.add(new MigrationItem(path, element.name(), element.type(), Disposition.BLOCKED,
						"ELEMENT_TYPE_NOT_IN_SLICE",
						"Keep this element in the source workspace or convert it manually after copy."));
				continue;
			}
			if (hasLoaderExclusiveFields(element, sourceLoader(sourceGenerator))) {
				items.add(new MigrationItem(path, element.name(), element.type(), Disposition.MANUAL,
						"LOADER_EXCLUSIVE_FIELDS_PRESERVED",
						"Loader-exclusive fields were copied unchanged and need review in the target generator."));
			} else {
				items.add(new MigrationItem(path, element.name(), element.type(), Disposition.SUPPORTED,
						"COMMON_FIELDS_COPIED", "Common Java element fields copy without conversion."));
			}
		}
		JsonObject document = source.upstreamDocument();
		for (String key : document.keySet()) {
			if (key.equals("copperbench") || key.equals("dev.copperbench") || key.equals("workspaceSettings")
					|| key.equals("mod_elements") || key.equals("elements"))
				continue;
			items.add(new MigrationItem("/upstream/" + key, key, "unknown_field", Disposition.MANUAL,
					"UNKNOWN_FIELD_PRESERVED", "Unknown upstream data is copied into the target and must not be dropped."));
		}
		items.sort(Comparator.comparing(MigrationItem::path));
		boolean complete = items.stream().noneMatch(item -> item.disposition() == Disposition.BLOCKED);
		return new MigrationReport("loader", sourceGenerator, targetGeneratorId, sourceHash, targetDirectory, true,
				complete, items);
	}

	private static boolean hasLoaderExclusiveFields(Element element, String loader) {
		JsonObject values = element.values();
		if (values.has("extensions") && values.get("extensions").isJsonObject()) {
			JsonObject extensions = values.getAsJsonObject("extensions");
			if (extensions.has(loader))
				return true;
		}
		if (values.has("fields") && values.get("fields").isJsonObject()) {
			JsonObject fields = values.getAsJsonObject("fields");
			if (fields.has(loader) || fields.has(loader + "Exclusive"))
				return true;
			for (String key : fields.keySet()) {
				if (key.toLowerCase(Locale.ROOT).startsWith(loader + "_")
						|| key.toLowerCase(Locale.ROOT).startsWith(loader + ":"))
					return true;
			}
		}
		return false;
	}

	private static String generatorId(WorkspaceState source) {
		JsonElement id = source.generator().get("id");
		return id == null || !id.isJsonPrimitive() ? "" : id.getAsString();
	}

	private static String sourceLoader(String generatorId) {
		int separator = generatorId.indexOf('-');
		return separator < 0 ? generatorId : generatorId.substring(0, separator);
	}

	private static String sourceHash(WorkspaceState source) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(JSON.toJson(source.generator()).getBytes(StandardCharsets.UTF_8));
			digest.update(JSON.toJson(source.upstreamDocument()).getBytes(StandardCharsets.UTF_8));
			source.elements().stream().sorted(Comparator.comparing(element -> element.id().toString()))
					.forEach(element -> digest.update(JSON.toJson(element.values()).getBytes(StandardCharsets.UTF_8)));
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("JVM must provide SHA-256", exception);
		}
	}

	private static void copyTree(Path source, Path target) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
				if (WorkspaceTreeHasher.excluded(source, directory) && !directory.equals(source))
					return FileVisitResult.SKIP_SUBTREE;
				Files.createDirectories(target.resolve(source.relativize(directory)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				if (WorkspaceTreeHasher.excluded(source, file))
					return FileVisitResult.CONTINUE;
				Path destination = target.resolve(source.relativize(file));
				Files.createDirectories(destination.getParent());
				Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void rewriteGenerator(Path targetRoot, String targetGeneratorId, JsonObject sourceGenerator)
			throws IOException {
		Path workspaceFile = targetRoot.resolve("workspace.mcreator");
		JsonObject document = Files.isRegularFile(workspaceFile)
				? JsonParser.parseString(Files.readString(workspaceFile, StandardCharsets.UTF_8)).getAsJsonObject()
				: new JsonObject();
		JsonObject settings = document.has("workspaceSettings") && document.get("workspaceSettings").isJsonObject()
				? document.getAsJsonObject("workspaceSettings") : new JsonObject();
		settings.addProperty("currentGenerator", targetGeneratorId);
		document.add("workspaceSettings", settings);
		JsonObject product = document.has("dev.copperbench") && document.get("dev.copperbench").isJsonObject()
				? document.getAsJsonObject("dev.copperbench") : new JsonObject();
		JsonObject generator = sourceGenerator.deepCopy();
		generator.addProperty("id", targetGeneratorId);
		int separator = targetGeneratorId.indexOf('-');
		generator.addProperty("loader", separator < 0 ? targetGeneratorId : targetGeneratorId.substring(0, separator));
		generator.addProperty("displayName", displayName(targetGeneratorId));
		product.add("generator", generator);
		document.add("dev.copperbench", product);
		Files.writeString(workspaceFile, JSON.toJson(document), StandardCharsets.UTF_8);
	}

	private static void writeProjection(Path targetRoot, WorkspaceState source, String targetGeneratorId)
			throws IOException {
		JsonObject projection = new JsonObject();
		projection.addProperty("sourceWorkspaceId", source.id().toString());
		projection.addProperty("targetGeneratorId", targetGeneratorId);
		projection.add("upstreamDocument", source.upstreamDocument());
		Files.writeString(targetRoot.resolve("migration-projection.json"), JSON.toJson(projection),
				StandardCharsets.UTF_8);
	}

	private static void writeReport(Path targetRoot, MigrationReport report) throws IOException {
		Files.writeString(targetRoot.resolve("migration-report.json"), JSON.toJson(report.toJson()),
				StandardCharsets.UTF_8);
	}

	private static String displayName(String generatorId) {
		int separator = generatorId.indexOf('-');
		if (separator < 1 || separator == generatorId.length() - 1)
			return generatorId;
		String loader = generatorId.substring(0, separator);
		return Character.toUpperCase(loader.charAt(0)) + loader.substring(1) + " " + generatorId.substring(separator + 1);
	}
}
