/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPointRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Named, hashed, historically bounded export of selected workspace assets.
 * The ZIP is deterministic via {@link ResourcePackExportService} when the
 * selection is a resource-pack directory; otherwise a batch manifest is stored
 * and the same exporter writes the selected tree.
 */
public final class AssetPublishBatchService {

	private static final Pattern BATCH_NAME = Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final AssetWorkspaceService assets;
	private final ResourcePackExportService exporter;
	private final LocalHistoryService history;
	private final Clock clock;

	public AssetPublishBatchService(AssetWorkspaceService assets, ResourcePackExportService exporter,
			LocalHistoryService history, Clock clock) {
		this.assets = Objects.requireNonNull(assets);
		this.exporter = Objects.requireNonNull(exporter);
		this.history = history;
		this.clock = Objects.requireNonNull(clock);
	}

	public PublishBatch create(String name, String sourceRelativeDirectory, String outputRelativePath, Actor actor,
			String taskId) throws LocalHistoryException {
		if (name == null || !BATCH_NAME.matcher(name).matches())
			throw new AssetPathViolationException("Publish batch name must be a lowercase identifier");
		ResourcePackExportService.ExportResult exported = exporter.export(sourceRelativeDirectory, outputRelativePath);
		List<AssetDescriptor> selected = assets.list().stream()
				.filter(asset -> asset.relativePath().equals(sourceRelativeDirectory)
						|| asset.relativePath().startsWith(sourceRelativeDirectory.replace('\\', '/') + "/"))
				.sorted(Comparator.comparing(AssetDescriptor::relativePath)).toList();
		PublishBatch batch = new PublishBatch(UUID.nameUUIDFromBytes(
				(name + ":" + exported.sha256()).getBytes(StandardCharsets.UTF_8)), name,
				sourceRelativeDirectory.replace('\\', '/'), exported.relativePath(), exported.sha256(),
				selected.size(), clock.instant().toString(), selected.stream().map(AssetDescriptor::relativePath).toList());
		Path manifest = assets.workspaceRoot().resolve(".copperbench/publish-batches/" + name + ".json");
		try {
			Files.createDirectories(manifest.getParent());
			Files.writeString(manifest, JSON.toJson(batch.toJson()), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new AssetPathViolationException("Publish batch manifest could not be stored");
		}
		if (history != null)
			history.createRecoveryPoint(new RecoveryPointRequest("Publish batch " + name, actor, taskId));
		return batch;
	}

	public List<PublishBatch> list() {
		Path directory = assets.workspaceRoot().resolve(".copperbench/publish-batches");
		if (!Files.isDirectory(directory))
			return List.of();
		try (var stream = Files.list(directory)) {
			List<PublishBatch> batches = new ArrayList<>();
			stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted()
					.forEach(path -> {
						try {
							batches.add(PublishBatch.fromJson(JsonParser.parseString(Files.readString(path))
									.getAsJsonObject()));
						} catch (IOException | RuntimeException ignored) {
							// Skip unreadable manifests; listing must stay deterministic and non-fatal.
						}
					});
			return List.copyOf(batches);
		} catch (IOException exception) {
			throw new AssetPathViolationException("Publish batches could not be listed");
		}
	}

	public record PublishBatch(UUID id, String name, String sourceDirectory, String outputPath, String sha256,
			int assetCount, String createdAt, List<String> assets) {
		public PublishBatch {
			Objects.requireNonNull(id);
			Objects.requireNonNull(name);
			Objects.requireNonNull(sourceDirectory);
			Objects.requireNonNull(outputPath);
			Objects.requireNonNull(sha256);
			Objects.requireNonNull(createdAt);
			assets = List.copyOf(assets);
		}

		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("id", id.toString());
			json.addProperty("name", name);
			json.addProperty("sourceDirectory", sourceDirectory);
			json.addProperty("outputPath", outputPath);
			json.addProperty("sha256", sha256);
			json.addProperty("assetCount", assetCount);
			json.addProperty("createdAt", createdAt);
			JsonArray array = new JsonArray();
			assets.forEach(array::add);
			json.add("assets", array);
			return json;
		}

		static PublishBatch fromJson(JsonObject json) {
			List<String> assets = new ArrayList<>();
			json.getAsJsonArray("assets").forEach(value -> assets.add(value.getAsString()));
			return new PublishBatch(UUID.fromString(json.get("id").getAsString()), json.get("name").getAsString(),
					json.get("sourceDirectory").getAsString(), json.get("outputPath").getAsString(),
					json.get("sha256").getAsString(), json.get("assetCount").getAsInt(),
					json.get("createdAt").getAsString(), assets);
		}
	}
}
