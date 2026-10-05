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
import dev.copperbench.core.workspace.WorkspaceFiles;
import dev.copperbench.migration.MigrationReport.Disposition;
import dev.copperbench.migration.MigrationReport.MigrationItem;
import dev.copperbench.release.ElementCoverageCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Imports an upstream MCreator workspace by copying it. The source directory is
 * never written; unknown JSON fields are preserved in the copy and listed in the report.
 */
public final class UpstreamWorkspaceImportService {

	private static final Set<String> KNOWN_ROOT_KEYS = Set.of("workspaceSettings", "mod_elements", "variable_elements",
			"sound_elements", "tag_elements", "language_map", "folder_elements", "linkedModElements",
			"dev.copperbench");
	private static final Set<String> SLICE_TYPES = Set.copyOf(ElementCoverageCatalog.FIRST_PARTY_SLICE);
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public MigrationReport preview(Path sourceRoot) throws IOException {
		Path workspaceFile = requireWorkspaceFile(sourceRoot);
		String hash = WorkspaceTreeHasher.hash(sourceRoot);
		return buildReport(workspaceFile, hash, null, true);
	}

	public MigrationReport execute(Path sourceRoot, Path targetRoot) throws IOException {
		requireWorkspaceFile(sourceRoot);
		Path origin = WorkspaceFiles.requireDirectory(sourceRoot);
		Path destination = WorkspaceFiles.canonicalPath(Objects.requireNonNull(targetRoot));
		if (destination.startsWith(origin) || origin.startsWith(destination))
			throw new IllegalArgumentException("Import target must be outside the source workspace");
		if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
			try (Stream<Path> children = Files.list(destination)) {
				if (children.findAny().isPresent())
					throw new IllegalArgumentException("Import target must be an empty directory");
			}
		}
		String before = WorkspaceTreeHasher.hash(origin);
		WorkspaceFiles.copyTree(origin, destination, relative -> WorkspaceTreeHasher.excluded(origin, origin.resolve(relative)));
		MigrationReport preview = buildReport(destination.resolve("workspace.mcreator"), before,
				destination.toString().replace('\\', '/'), true);
		WorkspaceFiles.writeAtomically(destination, destination.resolve(".copperbench/import/report.json"),
				output -> output.write(JSON.toJson(preview.toJson()).getBytes(StandardCharsets.UTF_8)));
		WorkspaceFiles.writeAtomically(destination, destination.resolve(".copperbench/import/source-hash.txt"),
				output -> output.write(before.getBytes(StandardCharsets.UTF_8)));
		String after = WorkspaceTreeHasher.hash(origin);
		boolean unchanged = before.equals(after);
		return new MigrationReport(preview.kind(), preview.sourceGeneratorId(), preview.targetGeneratorId(), before,
				destination.toString().replace('\\', '/'), unchanged, unchanged && preview.complete(), preview.items());
	}

	private MigrationReport buildReport(Path workspaceFile, String sourceHash, String targetDirectory,
			boolean sourceUnchanged) throws IOException {
		JsonObject document = JsonParser.parseString(Files.readString(workspaceFile, StandardCharsets.UTF_8))
				.getAsJsonObject();
		String generatorId = readGeneratorId(document);
		List<MigrationItem> items = new ArrayList<>();
		items.add(new MigrationItem("/workspace.mcreator", "workspace", "workspace", Disposition.SUPPORTED,
				"UPSTREAM_WORKSPACE_COPIED", "The upstream workspace is copied; the source directory is left untouched."));
		if (generatorId.isBlank()) {
			items.add(new MigrationItem("/workspaceSettings/currentGenerator", "generator", "generator",
					Disposition.MANUAL, "GENERATOR_UNSPECIFIED",
					"Set an active generator after import before generating or building."));
		} else {
			items.add(new MigrationItem("/workspaceSettings/currentGenerator", generatorId, "generator",
					Disposition.SUPPORTED, "GENERATOR_PRESERVED",
					"The original generator identifier is preserved in the copy."));
		}
		for (String key : document.keySet()) {
			if (KNOWN_ROOT_KEYS.contains(key))
				continue;
			items.add(new MigrationItem("/" + key, key, "unknown_field", Disposition.MANUAL, "UNKNOWN_FIELD_PRESERVED",
					"Unknown upstream field kept in the copied workspace.mcreator."));
		}
		Path elementsDir = workspaceFile.getParent().resolve("elements");
		if (Files.isDirectory(elementsDir)) {
			try (Stream<Path> files = Files.list(elementsDir)) {
				files.filter(path -> path.getFileName().toString().endsWith(".mod.json")).sorted()
						.forEach(file -> items.add(elementItem(elementsDir.relativize(file).toString(), file)));
			}
		}
		items.sort(Comparator.comparing(MigrationItem::path));
		boolean complete = items.stream().noneMatch(item -> item.disposition() == Disposition.BLOCKED);
		return new MigrationReport("upstream_import", generatorId.isBlank() ? "unknown" : generatorId, generatorId,
				sourceHash, targetDirectory, sourceUnchanged, complete, items);
	}

	private static MigrationItem elementItem(String relative, Path file) {
		String name = relative.replace('\\', '/').replace(".mod.json", "");
		String type = "unknown";
		try {
			JsonObject raw = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (raw.has("_type") && raw.get("_type").isJsonPrimitive())
				type = raw.get("_type").getAsString().toLowerCase(Locale.ROOT);
			else if (raw.has("type") && raw.get("type").isJsonPrimitive())
				type = raw.get("type").getAsString().toLowerCase(Locale.ROOT);
		} catch (IOException | RuntimeException ignored) {
			return new MigrationItem("/elements/" + name, name, type, Disposition.MANUAL, "ELEMENT_UNREADABLE",
					"The element definition could not be parsed; the file is still copied.");
		}
		if (SLICE_TYPES.contains(type))
			return new MigrationItem("/elements/" + name, name, type, Disposition.SUPPORTED, "ELEMENT_COPIED",
					"Known Java element copied with its original definition.");
		return new MigrationItem("/elements/" + name, name, type, Disposition.MANUAL, "ELEMENT_OUTSIDE_FIRST_PARTY",
				"This element type is copied but is outside the supported Java first-party catalog.");
	}

	private static String readGeneratorId(JsonObject document) {
		JsonElement settings = document.get("workspaceSettings");
		if (settings != null && settings.isJsonObject()) {
			JsonElement generator = settings.getAsJsonObject().get("currentGenerator");
			if (generator != null && generator.isJsonPrimitive())
				return generator.getAsString();
		}
		return "";
	}

	private static Path requireWorkspaceFile(Path sourceRoot) throws IOException {
		if (sourceRoot == null || !Files.isDirectory(sourceRoot))
			throw new IllegalArgumentException("Upstream workspace directory is required");
		Path root = WorkspaceFiles.requireDirectory(sourceRoot);
		Path workspaceFile = WorkspaceFiles.requireWithin(root, root.resolve("workspace.mcreator"));
		if (!Files.isRegularFile(workspaceFile, LinkOption.NOFOLLOW_LINKS))
			throw new IllegalArgumentException("Upstream workspace must contain workspace.mcreator");
		return workspaceFile;
	}
}
