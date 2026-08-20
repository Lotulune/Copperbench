/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Copy-only loader or upstream migration diagnosis. The source workspace is never mutated. */
public record MigrationReport(String kind, String sourceGeneratorId, String targetGeneratorId, String sourceHash,
		String targetDirectory, boolean sourceUnchanged, boolean complete, List<MigrationItem> items) {

	public enum Disposition {
		SUPPORTED, SUBSTITUTE, LOST, BLOCKED, MANUAL
	}

	public record MigrationItem(String path, String name, String type, Disposition disposition, String reasonCode,
			String nextStep) {
		public MigrationItem {
			Objects.requireNonNull(path);
			Objects.requireNonNull(name);
			Objects.requireNonNull(type);
			Objects.requireNonNull(disposition);
			Objects.requireNonNull(reasonCode);
			Objects.requireNonNull(nextStep);
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("path", path);
			json.addProperty("name", name);
			json.addProperty("type", type);
			json.addProperty("disposition", disposition.name().toLowerCase(Locale.ROOT));
			json.addProperty("reasonCode", reasonCode);
			json.addProperty("nextStep", nextStep);
			return json;
		}
	}

	public MigrationReport {
		Objects.requireNonNull(kind);
		Objects.requireNonNull(sourceGeneratorId);
		Objects.requireNonNull(targetGeneratorId);
		Objects.requireNonNull(sourceHash);
		items = List.copyOf(items);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("kind", kind);
		json.addProperty("sourceGeneratorId", sourceGeneratorId);
		json.addProperty("targetGeneratorId", targetGeneratorId);
		json.addProperty("sourceHash", sourceHash);
		if (targetDirectory == null)
			json.add("targetDirectory", com.google.gson.JsonNull.INSTANCE);
		else
			json.addProperty("targetDirectory", targetDirectory);
		json.addProperty("sourceUnchanged", sourceUnchanged);
		json.addProperty("complete", complete);
		JsonArray array = new JsonArray();
		items.forEach(item -> array.add(item.toJson()));
		json.add("items", array);
		long blocked = items.stream().filter(item -> item.disposition() == Disposition.BLOCKED).count();
		long lost = items.stream().filter(item -> item.disposition() == Disposition.LOST).count();
		long manual = items.stream().filter(item -> item.disposition() == Disposition.MANUAL).count();
		json.addProperty("blockedCount", blocked);
		json.addProperty("lostCount", lost);
		json.addProperty("manualCount", manual);
		return json;
	}
}
