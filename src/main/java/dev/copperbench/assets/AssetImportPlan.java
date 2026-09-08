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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable, replay-checkable preview of one external-file asset import. */
public record AssetImportPlan(Path source, String sourceFileName, long sourceSize, String sourceSha256,
		String sourceMediaType, AssetCategory category, String targetRelativePath, Conflict conflict,
		String targetSha256, List<String> duplicatePaths, boolean canApply, List<String> issueCodes) {

	public AssetImportPlan {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(sourceFileName, "sourceFileName");
		Objects.requireNonNull(sourceSha256, "sourceSha256");
		Objects.requireNonNull(sourceMediaType, "sourceMediaType");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(targetRelativePath, "targetRelativePath");
		Objects.requireNonNull(conflict, "conflict");
		duplicatePaths = List.copyOf(Objects.requireNonNull(duplicatePaths, "duplicatePaths"));
		issueCodes = List.copyOf(Objects.requireNonNull(issueCodes, "issueCodes"));
		if (sourceSize < 0) throw new IllegalArgumentException("sourceSize must not be negative");
	}

	public enum Conflict {
		CREATE,
		IDENTICAL,
		REPLACE
	}

	/** Wire form intentionally excludes the external absolute source path. */
	public JsonObject toJson() {
		JsonObject value = new JsonObject();
		value.addProperty("sourceFileName", sourceFileName);
		value.addProperty("sourceSize", sourceSize);
		value.addProperty("sourceSha256", sourceSha256);
		value.addProperty("sourceMediaType", sourceMediaType);
		value.addProperty("category", category.name());
		value.addProperty("targetRelativePath", targetRelativePath);
		value.addProperty("conflict", conflict.name());
		if (targetSha256 == null) value.add("targetSha256", com.google.gson.JsonNull.INSTANCE);
		else value.addProperty("targetSha256", targetSha256);
		JsonArray duplicates = new JsonArray();
		duplicatePaths.forEach(duplicates::add);
		value.add("duplicatePaths", duplicates);
		value.addProperty("canApply", canApply);
		JsonArray issues = new JsonArray();
		issueCodes.forEach(issues::add);
		value.add("issueCodes", issues);
		return value;
	}
}
