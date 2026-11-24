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
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;

/**
 * The reviewed impact of deleting assets: each file, the asset references that would no longer resolve and how
 * much workspace usage was found. Only cleanup candidates (assessed and unused) delete without a confirmation.
 */
public record AssetDeletePlan(List<Entry> entries, List<AffectedReference> affectedReferences, boolean canApply,
		boolean requiresReferenceConfirmation, List<String> issueCodes) {
	private static final Gson GSON = new Gson();

	public AssetDeletePlan {
		entries = List.copyOf(entries);
		affectedReferences = List.copyOf(affectedReferences);
		issueCodes = List.copyOf(issueCodes);
		if (entries.isEmpty()) throw new IllegalArgumentException("An asset delete plan needs at least one asset");
	}

	/**
	 * @param workspaceReferenceCount element and workspace usages found by the health scan; text matches can over-count
	 * @param safeUnused              assessed as a cleanup candidate: no asset or workspace usage and no errors
	 */
	public record Entry(String assetId, String relativePath, String sha256, AssetCategory category, long size,
			int inboundReferences, int workspaceReferenceCount, boolean safeUnused, List<String> issueCodes) {
		public Entry {
			Objects.requireNonNull(assetId, "assetId");
			Objects.requireNonNull(relativePath, "relativePath");
			issueCodes = List.copyOf(issueCodes);
		}
	}

	/** An asset document reference that points at a file this plan deletes. */
	public record AffectedReference(String sourceAssetId, String sourcePath, String sourcePointer, String rawValue,
			String targetPath) {
	}

	public JsonObject toJson() {
		JsonObject json = GSON.toJsonTree(this).getAsJsonObject();
		json.addProperty("deleteCount", entries.size());
		json.addProperty("totalSize", entries.stream().mapToLong(Entry::size).sum());
		return json;
	}
}
