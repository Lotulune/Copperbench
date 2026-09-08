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

/** Immutable all-or-nothing review model for a multi-file asset import. */
public record AssetImportBatchPlan(List<AssetImportPlan> items, int createCount, int replaceCount,
		int identicalCount, boolean canApply, List<String> issueCodes) {

	public AssetImportBatchPlan {
		items = List.copyOf(Objects.requireNonNull(items, "items"));
		issueCodes = List.copyOf(Objects.requireNonNull(issueCodes, "issueCodes"));
		if (items.isEmpty()) throw new IllegalArgumentException("items must not be empty");
		if (createCount < 0 || replaceCount < 0 || identicalCount < 0)
			throw new IllegalArgumentException("batch counts must not be negative");
	}

	public int changedCount() {
		return createCount + replaceCount;
	}

	public JsonObject toJson() {
		JsonObject value = new JsonObject();
		JsonArray itemValues = new JsonArray();
		items.forEach(item -> itemValues.add(item.toJson()));
		value.add("items", itemValues);
		value.addProperty("createCount", createCount);
		value.addProperty("replaceCount", replaceCount);
		value.addProperty("identicalCount", identicalCount);
		value.addProperty("changedCount", changedCount());
		value.addProperty("canApply", canApply);
		JsonArray issues = new JsonArray();
		issueCodes.forEach(issues::add);
		value.add("issueCodes", issues);
		return value;
	}
}
