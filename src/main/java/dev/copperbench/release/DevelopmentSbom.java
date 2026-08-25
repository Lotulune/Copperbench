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
import dev.copperbench.ProductIdentity;

import java.util.List;

/**
 * Development-stage component inventory. This is not a signed CycloneDX
 * production SBOM and does not enumerate every transitive Maven artifact.
 */
public final class DevelopmentSbom {

	public record Component(String name, String version, String type, String license, String path) {
	}

	public static final List<Component> DIRECT = List.of(
			new Component("commons-io", "2.22.0", "library", "Apache-2.0", "license/Apache Commons License.txt"),
			new Component("commons-lang3", "3.20.0", "library", "Apache-2.0", "license/Apache Commons License.txt"),
			new Component("log4j-core", "2.26.1", "library", "Apache-2.0", "license/Apache Log4j License.txt"),
			new Component("freemarker", "2.3.34", "library", "Apache-2.0", "license/FreeMarker License.txt"),
			new Component("gson", "2.14.0", "library", "Apache-2.0", "license/Gson License.txt"),
			new Component("guava", "33.6.0-jre", "library", "Apache-2.0", "license/Guava License.txt"),
			new Component("jgit", "7.7.0.202606012155-r", "library", "BSD-3-Clause", "license/JGit License.txt"),
			new Component("flatlaf", "3.7.1", "library", "Apache-2.0", "license/FlatLaf License.txt"),
			new Component("mcp", "2.0.0", "library", "MIT", "license/"),
			new Component("jna", "5.19.1", "library", "Apache-2.0 / LGPL-2.1", "license/JNA License.txt"));

	private DevelopmentSbom() {
	}

	public static JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.0");
		root.addProperty("kind", "development-inventory");
		root.addProperty("productId", ProductIdentity.ID);
		root.addProperty("productVersion", ProductIdentity.VERSION);
		JsonArray components = new JsonArray();
		add(components, ProductIdentity.NAME, ProductIdentity.VERSION, "application", "GPL-3.0-only", ".");
		add(components, "JetBrains Runtime JCEF", "25", "runtime", "GPL-2.0-with-classpath-exception",
				"jdk/jbr25_win_64");
		add(components, "Bundled JDK 21", "21", "runtime", "GPL-2.0-with-classpath-exception", "jdk/jdk21_win_64");
		for (var plugin : BundledPluginInventory.FIRST_PARTY)
			add(components, plugin.pluginId(), ProductIdentity.VERSION, "plugin", "GPL-3.0-only",
					"plugins/" + plugin.packageName());
		for (Component component : DIRECT)
			add(components, component.name(), component.version(), component.type(), component.license(),
					component.path());
		root.add("components", components);
		return root;
	}

	private static void add(JsonArray components, String name, String version, String type, String license,
			String path) {
		JsonObject json = new JsonObject();
		json.addProperty("name", name);
		json.addProperty("version", version);
		json.addProperty("type", type);
		json.addProperty("license", license);
		json.addProperty("path", path);
		components.add(json);
	}
}
