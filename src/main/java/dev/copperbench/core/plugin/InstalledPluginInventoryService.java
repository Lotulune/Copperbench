/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.release.BundledPluginInventory;
import net.mcreator.io.UserFolderManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lists installed plugins without loading Java code. First-party trees are classified
 * from {@link BundledPluginInventory}; third-party entries use the static classifier.
 */
public final class InstalledPluginInventoryService {

	private final List<Path> roots;
	private final PluginCompatibilityClassifier classifier;
	private final long productVersion;
	private final long productMajorVersion;

	public InstalledPluginInventoryService(List<Path> roots, long productVersion, long productMajorVersion) {
		this.roots = List.copyOf(Objects.requireNonNull(roots));
		this.classifier = new PluginCompatibilityClassifier();
		this.productVersion = productVersion;
		this.productMajorVersion = productMajorVersion;
	}

	public static InstalledPluginInventoryService productDefault() {
		List<Path> roots = new ArrayList<>();
		roots.add(Path.of("plugins").toAbsolutePath().normalize());
		try {
			roots.add(UserFolderManager.getFileFromUserFolder("plugins").toPath().toAbsolutePath().normalize());
		} catch (RuntimeException ignored) {
		}
		return new InstalledPluginInventoryService(roots, 2026002L, 2026002L);
	}

	public JsonObject list() {
		JsonArray plugins = new JsonArray();
		JsonArray scanned = new JsonArray();
		for (Path root : roots) {
			scanned.add(root.toString().replace('\\', '/'));
			if (!Files.isDirectory(root))
				continue;
			try (var stream = Files.list(root)) {
				List<Path> entries = stream.sorted(Comparator.comparing(path -> path.getFileName().toString()
						.toLowerCase(Locale.ROOT))).toList();
				for (Path entry : entries) {
					if (looksLikePlugin(entry))
						plugins.add(describe(entry));
				}
			} catch (IOException ignored) {
			}
		}
		JsonObject json = new JsonObject();
		json.add("plugins", plugins);
		json.add("scannedRoots", scanned);
		json.addProperty("loadsJava", false);
		return json;
	}

	private static boolean looksLikePlugin(Path entry) {
		if (Files.isDirectory(entry))
			return Files.isRegularFile(entry.resolve("plugin.json"));
		String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".zip");
	}

	private JsonObject describe(Path entry) {
		JsonObject manifest = readManifest(entry);
		String id = manifest.has("id") ? manifest.get("id").getAsString() : entry.getFileName().toString();
		JsonObject item = new JsonObject();
		item.addProperty("pluginId", id);
		item.addProperty("path", entry.toAbsolutePath().normalize().toString().replace('\\', '/'));
		BundledPluginInventory.PluginRecord firstParty = firstParty(id);
		item.addProperty("firstParty", firstParty != null);
		if (firstParty != null) {
			item.addProperty("level", firstParty.level());
			item.addProperty("route", firstParty.route());
			item.addProperty("containsJavaCode", false);
			item.addProperty("versionSupported", true);
		} else {
			try {
				PluginCompatibilityClassifier.Assessment assessment = classifier.assess(entry, productVersion,
						productMajorVersion);
				item.addProperty("level", assessment.level().name());
				item.addProperty("route", assessment.route().name());
				item.addProperty("containsJavaCode", assessment.containsJavaCode());
				item.addProperty("versionSupported", assessment.versionSupported());
				item.addProperty("sha256", assessment.sha256());
				JsonArray limitations = new JsonArray();
				assessment.limitations().forEach(limitations::add);
				item.add("limitations", limitations);
			} catch (Exception exception) {
				item.addProperty("level", "X");
				item.addProperty("route", "REJECTED");
				item.addProperty("containsJavaCode", false);
				item.addProperty("versionSupported", false);
				JsonArray limitations = new JsonArray();
				limitations.add("PLUGIN_SCAN_FAILED");
				item.add("limitations", limitations);
			}
		}
		if (manifest.has("info") && manifest.getAsJsonObject("info").has("name"))
			item.addProperty("displayName", manifest.getAsJsonObject("info").get("name").getAsString());
		if (manifest.has("info") && manifest.getAsJsonObject("info").has("version"))
			item.addProperty("version", manifest.getAsJsonObject("info").get("version").getAsString());
		if (manifest.has("supportedversions"))
			item.add("supportedversions", manifest.get("supportedversions"));
		return item;
	}

	private static BundledPluginInventory.PluginRecord firstParty(String pluginId) {
		for (BundledPluginInventory.PluginRecord record : BundledPluginInventory.FIRST_PARTY) {
			if (record.pluginId().equals(pluginId))
				return record;
		}
		return null;
	}

	private static JsonObject readManifest(Path entry) {
		try {
			if (Files.isDirectory(entry)) {
				Path manifest = entry.resolve("plugin.json");
				if (!Files.isRegularFile(manifest))
					return new JsonObject();
				return JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
			}
			try (ZipFile zip = new ZipFile(entry.toFile())) {
				ZipEntry manifest = zip.getEntry("plugin.json");
				if (manifest == null)
					return new JsonObject();
				return JsonParser.parseString(new String(zip.getInputStream(manifest).readAllBytes(),
						StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (Exception exception) {
			return new JsonObject();
		}
	}
}
