/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Fabric1211GoldenWorkspace {

	private Fabric1211GoldenWorkspace() {
	}

	public static WorkspaceState create261() {
		WorkspaceState base = create();
		var generator = base.generator();
		generator.addProperty("id", "fabric-26.1.2");
		generator.addProperty("minecraftVersion", "26.1.2");
		generator.addProperty("displayName", "Fabric 26.1");
		return base.withGenerator(generator);
	}

	public static WorkspaceState createNeoForge261() {
		WorkspaceState base = create();
		var generator = base.generator();
		generator.addProperty("id", "neoforge-26.1.2");
		generator.addProperty("loader", "neoforge");
		generator.addProperty("minecraftVersion", "26.1.2");
		generator.addProperty("displayName", "NeoForge 26.1");
		return base.withGenerator(generator);
	}

	public static WorkspaceState create262() {
		WorkspaceState base = create();
		var generator = base.generator();
		generator.addProperty("id", "fabric-26.2");
		generator.addProperty("minecraftVersion", "26.2");
		generator.addProperty("displayName", "Fabric 26.2");
		return base.withGenerator(generator);
	}

	public static WorkspaceState createNeoForge262() {
		WorkspaceState base = create();
		var generator = base.generator();
		generator.addProperty("id", "neoforge-26.2");
		generator.addProperty("loader", "neoforge");
		generator.addProperty("minecraftVersion", "26.2");
		generator.addProperty("displayName", "NeoForge 26.2");
		return base.withGenerator(generator);
	}

	public static WorkspaceState create1201() {
		WorkspaceState base = create();
		var generator = base.generator();
		generator.addProperty("id", "fabric-1.20.1");
		generator.addProperty("minecraftVersion", "1.20.1");
		generator.addProperty("displayName", "Fabric 1.20.1");
		return base.withGenerator(generator);
	}

	public static WorkspaceState createNeoForge1201() {
		WorkspaceState base = create();
		var generator = base.generator();
		generator.addProperty("id", "neoforge-1.20.1");
		generator.addProperty("loader", "neoforge");
		generator.addProperty("minecraftVersion", "1.20.1");
		generator.addProperty("displayName", "NeoForge 1.20.1");
		return base.withGenerator(generator);
	}

	public static WorkspaceState create() {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");

		JsonObject document = new JsonObject();
		JsonObject product = new JsonObject();
		product.addProperty("modId", "copper_trails");
		product.addProperty("basePackage", "dev.coppertrails");
		product.addProperty("version", "1.0.0");
		document.add("copperbench", product);

		Instant now = Instant.parse("2026-08-17T03:00:00Z");
		return new WorkspaceState(UUID.fromString("11111111-1111-4111-8111-111111111111"), "Copper Trails",
				"mod", 4, true, generator, document, List.of(
					element(1, "block", "trail_lamp", blockFields(), now),
					element(2, "item", "trail_compass", itemFields(), now),
					element(3, "recipe", "trail_lamp", recipeFields(), now),
					element(4, "procedure", "announce_trail", procedureFields(), now)));
	}

	private static Element element(long id, String type, String name, JsonObject fields, Instant now) {
		JsonObject values = new JsonObject();
		values.add("fields", fields);
		return new Element(UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", id)), type, name,
				fields.has("displayName") ? fields.get("displayName").getAsString() : name, "valid", "generated", now,
				values);
	}

	private static JsonObject blockFields() {
		JsonObject fields = new JsonObject();
		fields.addProperty("displayName", "Trail Lamp");
		fields.addProperty("hardness", 3.0);
		fields.addProperty("resistance", 6.0);
		fields.addProperty("luminance", 12);
		return fields;
	}

	private static JsonObject itemFields() {
		JsonObject fields = new JsonObject();
		fields.addProperty("displayName", "Trail Compass");
		fields.addProperty("maxStackSize", 16);
		return fields;
	}

	private static JsonObject recipeFields() {
		JsonObject fields = new JsonObject();
		fields.addProperty("result", "trail_lamp");
		JsonArray pattern = new JsonArray();
		pattern.add(" C ");
		pattern.add("CTC");
		pattern.add(" C ");
		fields.add("pattern", pattern);
		JsonObject key = new JsonObject();
		key.addProperty("C", "minecraft:copper_ingot");
		key.addProperty("T", "minecraft:torch");
		fields.add("key", key);
		return fields;
	}

	private static JsonObject procedureFields() {
		JsonObject fields = new JsonObject();
		fields.addProperty("message", "Copper trails are ready");
		return fields;
	}
}
