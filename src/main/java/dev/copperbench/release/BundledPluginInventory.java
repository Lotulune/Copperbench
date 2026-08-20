/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/** First-party plugins shipped in the Windows package. Classification is resource-pipeline unless a Java plugin is declared. */
public final class BundledPluginInventory {

	public record PluginRecord(String pluginId, String packageName, String level, String route) {
	}

	public static final List<PluginRecord> FIRST_PARTY = List.of(
			new PluginRecord("core", "mcreator-core", "A", "RESOURCE_PIPELINE"),
			new PluginRecord("generator-1.21.1", "generator-1.21.1", "A", "RESOURCE_PIPELINE"),
			new PluginRecord("generator-26.1.x", "generator-26.1.x", "A", "RESOURCE_PIPELINE"),
			new PluginRecord("generator-addon-26.1x", "generator-addon-26.1x", "A", "RESOURCE_PIPELINE"),
			new PluginRecord("generator-fabric-26.1.2", "generator-fabric-26.1.2", "A", "RESOURCE_PIPELINE"),
			new PluginRecord("localization", "mcreator-localization", "A", "RESOURCE_PIPELINE"),
			new PluginRecord("mcreator-link", "mcreator-link", "A", "RESOURCE_PIPELINE"),
			new PluginRecord("themes", "mcreator-themes", "A", "RESOURCE_PIPELINE"));

	private BundledPluginInventory() {
	}

	public static JsonArray toJson() {
		JsonArray items = new JsonArray();
		for (PluginRecord plugin : FIRST_PARTY) {
			JsonObject json = new JsonObject();
			json.addProperty("pluginId", plugin.pluginId());
			json.addProperty("packageName", plugin.packageName());
			json.addProperty("level", plugin.level());
			json.addProperty("route", plugin.route());
			json.addProperty("firstParty", true);
			items.add(json);
		}
		return items;
	}
}
