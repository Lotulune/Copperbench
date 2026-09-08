/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;

/** Immutable impact preview for one reference-safe asset rename/move. */
public record AssetMovePlan(String sourceAssetId, String sourceRelativePath, String sourceSha256,
		AssetCategory category, String targetRelativePath, String targetAssetId, List<ReferenceRewrite> rewrites,
		boolean canApply, List<String> issueCodes) {

	public AssetMovePlan {
		Objects.requireNonNull(sourceAssetId, "sourceAssetId");
		Objects.requireNonNull(sourceRelativePath, "sourceRelativePath");
		Objects.requireNonNull(sourceSha256, "sourceSha256");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(targetRelativePath, "targetRelativePath");
		Objects.requireNonNull(targetAssetId, "targetAssetId");
		rewrites = List.copyOf(Objects.requireNonNull(rewrites, "rewrites"));
		issueCodes = List.copyOf(Objects.requireNonNull(issueCodes, "issueCodes"));
	}

	public JsonObject toJson() {
		JsonObject value = new JsonObject();
		value.addProperty("sourceAssetId", sourceAssetId);
		value.addProperty("sourceRelativePath", sourceRelativePath);
		value.addProperty("sourceSha256", sourceSha256);
		value.addProperty("category", category.name());
		value.addProperty("targetRelativePath", targetRelativePath);
		value.addProperty("targetAssetId", targetAssetId);
		value.addProperty("referenceCount", rewrites.size());
		JsonArray rewriteValues = new JsonArray();
		rewrites.forEach(rewrite -> rewriteValues.add(rewrite.toJson()));
		value.add("rewrites", rewriteValues);
		value.addProperty("canApply", canApply);
		JsonArray issues = new JsonArray();
		issueCodes.forEach(issues::add);
		value.add("issueCodes", issues);
		return value;
	}

	public record ReferenceRewrite(String sourceAssetId, String sourcePath, String sourceSha256,
			String sourcePointer, String oldRawValue, String newRawValue, AssetReference.ReferenceKind kind) {
		public ReferenceRewrite {
			Objects.requireNonNull(sourceAssetId, "sourceAssetId");
			Objects.requireNonNull(sourcePath, "sourcePath");
			Objects.requireNonNull(sourceSha256, "sourceSha256");
			Objects.requireNonNull(sourcePointer, "sourcePointer");
			Objects.requireNonNull(oldRawValue, "oldRawValue");
			Objects.requireNonNull(newRawValue, "newRawValue");
			Objects.requireNonNull(kind, "kind");
		}

		private JsonObject toJson() {
			JsonObject value = new JsonObject();
			value.addProperty("sourceAssetId", sourceAssetId);
			value.addProperty("sourcePath", sourcePath);
			value.addProperty("sourceSha256", sourceSha256);
			value.addProperty("sourcePointer", sourcePointer);
			value.addProperty("oldRawValue", oldRawValue);
			value.addProperty("newRawValue", newRawValue);
			value.addProperty("kind", kind.name());
			return value;
		}
	}
}
