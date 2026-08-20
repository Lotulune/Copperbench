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
 * block, item, recipe, and procedure on every Java generator.
 */
public final class ElementCoverageCatalog {

	public static final List<String> FIRST_PARTY_SLICE = List.of("block", "item", "recipe", "procedure");

	public static final List<String> UNSUPPORTED_IN_NEW_UI = List.of("achievement", "armor", "armortrim", "attribute",
			"bannerpattern", "biome", "code", "command", "damagetype", "dimension", "enchantment", "feature", "fluid",
			"function", "gamerule", "gui", "itemextension", "keybind", "livingentity", "loottable", "overlay",
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
		root.addProperty("appliesToGenerators", "all eight first-party Fabric and NeoForge generators");
		root.addProperty("notes",
				"Create and update in the new UI, MCP, and headless accept only the first-party slice. Imported upstream types are preserved and listed, but the editor is read-only and updates are rejected.");
		return root;
	}

	private static JsonArray strings(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}
}
