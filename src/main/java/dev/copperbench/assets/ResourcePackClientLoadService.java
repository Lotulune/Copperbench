/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Prepares a standalone resource pack for a Minecraft test client by exporting
 * a deterministic ZIP into {@code run/resourcepacks} and writing {@code options.txt}.
 * Launching the real client remains an explicit generator/runtime concern.
 */
public final class ResourcePackClientLoadService {

	private final AssetWorkspaceService assets;
	private final ResourcePackExportService exporter;

	public ResourcePackClientLoadService(AssetWorkspaceService assets, ResourcePackExportService exporter) {
		this.assets = Objects.requireNonNull(assets);
		this.exporter = Objects.requireNonNull(exporter);
	}

	public ClientLoadPreparation prepare(String sourceRelativeDirectory, String zipFileName) {
		if (zipFileName == null || !zipFileName.toLowerCase(Locale.ROOT).endsWith(".zip"))
			throw new AssetPathViolationException("Resource pack client load requires a .zip file name");
		String fileName = Path.of(zipFileName).getFileName().toString();
		String exportRelative = "run/resourcepacks/" + fileName;
		ResourcePackExportService.ExportResult exported = exporter.export(sourceRelativeDirectory, exportRelative);
		Path packMeta = assets.workspaceRoot().resolve(sourceRelativeDirectory).resolve("pack.mcmeta");
		int packFormat = readPackFormat(packMeta);
		Path options = assets.workspaceRoot().resolve("run/options.txt");
		try {
			Files.createDirectories(options.getParent());
			Files.writeString(options, """
					lang:en_us
					resourcePacks:["vanilla","fabric","file/%s"]
					incompatibleResourcePacks:[]
					""".formatted(fileName), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new AssetPathViolationException("Resource pack client options could not be written");
		}
		return new ClientLoadPreparation(exported.relativePath(), exported.sha256(), packFormat,
				"run/options.txt", true);
	}

	private static int readPackFormat(Path packMeta) {
		try {
			if (!Files.isRegularFile(packMeta))
				throw new AssetPathViolationException("Resource pack requires pack.mcmeta");
			JsonObject root = JsonParser.parseString(Files.readString(packMeta)).getAsJsonObject();
			if (!root.has("pack") || !root.getAsJsonObject("pack").has("pack_format"))
				throw new AssetPathViolationException("pack.mcmeta is missing pack_format");
			return root.getAsJsonObject("pack").get("pack_format").getAsInt();
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof AssetPathViolationException violation)
				throw violation;
			throw new AssetPathViolationException("pack.mcmeta is invalid");
		}
	}

	public record ClientLoadPreparation(String zipRelativePath, String sha256, int packFormat,
			String optionsRelativePath, boolean readyForClient) {
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("zipRelativePath", zipRelativePath);
			json.addProperty("sha256", sha256);
			json.addProperty("packFormat", packFormat);
			json.addProperty("optionsRelativePath", optionsRelativePath);
			json.addProperty("readyForClient", readyForClient);
			json.addProperty("clientLaunched", false);
			return json;
		}
	}
}
