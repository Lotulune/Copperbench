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

/** Stage 11 first-party Java mod-element coverage catalog. */
public final class ElementCoverageCatalog {

	public static final List<String> FIRST_PARTY_SLICE = List.of("block", "item", "recipe", "procedure", "function",
			"loottable", "achievement", "armor", "armortrim", "tool", "itemextension", "attribute", "bannerpattern",
			"command", "damagetype", "enchantment", "gamerule", "keybind", "painting", "particle", "potion",
			"potioneffect", "tab", "villagerprofession", "villagertrade", "biome", "dimension", "feature", "fluid",
			"plant", "structure", "livingentity", "specialentity", "projectile", "gui", "overlay", "code");

	public static final List<String> UNSUPPORTED_IN_NEW_UI = List.of();

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
		root.addProperty("appliesToGenerators", "shared Java application service; generator capability gates remain explicit per loader/version");
		root.addProperty("notes", "All Java mod element types in this catalog share the application-service CRUD, editor schema, diagnostics, persistence and recovery semantics. Bedrock add-on types remain outside this catalog.");
		return root;
	}

	private static JsonArray strings(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}
}
