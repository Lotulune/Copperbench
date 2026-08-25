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
import java.util.Set;

/**
 * Honest first-release mod-element matrix. Bundled generator plugins still
 * contain upstream templates; the new UI/MCP/headless first-party slice is
 * block, item, recipe, procedure, function, loot table, and advancement. The
 * Stage 9 generator matrix has fixed evidence; the remaining product gates are
 * tracked separately.
 */
public final class ElementCoverageCatalog {

	public static final List<String> FIRST_PARTY_SLICE = List.of("block", "item", "recipe", "procedure", "function",
			"loottable", "achievement");

	public static final List<String> UNSUPPORTED_IN_NEW_UI = List.of("armor", "armortrim", "attribute",
			"bannerpattern", "biome", "code", "command", "damagetype", "dimension", "enchantment", "feature", "fluid",
			"gamerule", "gui", "itemextension", "keybind", "livingentity", "overlay",
			"painting", "particle", "plant", "potion", "potioneffect", "projectile", "specialentity", "structure",
			"tab", "tool", "villagerprofession", "villagertrade");

	public static final List<String> BEDROCK_ADDON_NOT_APPLICABLE = List.of("bebiome", "beblock", "beentity", "beitem",
			"bescript");

	private static final Set<String> FIRST_PARTY = Set.copyOf(FIRST_PARTY_SLICE);

	private ElementCoverageCatalog() {
	}

	public static boolean isFirstParty(String elementType) {
		return elementType != null && FIRST_PARTY.contains(elementType);
	}

	public static JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.0");
		root.add("firstPartySlice", strings(FIRST_PARTY_SLICE));
		root.add("unsupportedInNewUi", strings(UNSUPPORTED_IN_NEW_UI));
		root.add("bedrockAddonNotApplicable", strings(BEDROCK_ADDON_NOT_APPLICABLE));
		root.addProperty("appliesToGenerators",
				"shared Java application service; Stage 9 golden generation passed all eight generators");
		root.addProperty("notes",
				"Create and update in the new UI, MCP, and headless accept only the first-party slice. Function, loot table, and advancement passed the eight-generator golden build but remain Stage 9 development preview until the dedicated editor and remaining product gates pass. Imported upstream types are preserved and listed, but the editor is read-only and updates are rejected.");
		return root;
	}

	private static JsonArray strings(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}
}
