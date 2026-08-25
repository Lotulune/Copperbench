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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Expected Windows x64 export layout. Does not claim an install/upgrade machine test. */
public final class WindowsDistributionLayout {

	public static final List<String> REQUIRED_ENTRIES = List.of("copperbench.exe", "LICENSE.txt",
			"LICENSE-ADDITIONAL-TERMS.md", "jdk/bin/java.exe", "jdk/bin/jcef.dll", "lib/copperbench.jar", "plugins");

	private WindowsDistributionLayout() {
	}

	public static List<String> missing(Path root) {
		List<String> missing = new ArrayList<>();
		for (String entry : REQUIRED_ENTRIES) {
			if (!Files.exists(root.resolve(entry)))
				missing.add(entry);
		}
		return List.copyOf(missing);
	}

	public static JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("executable", "copperbench.exe");
		json.addProperty("bundledJdk", "jdk/jbr25_win_64");
		json.addProperty("jcefBundledWithJdk", true);
		json.addProperty("pluginsDirectory", "plugins");
		json.addProperty("licenseDirectory", "license");
		JsonArray required = new JsonArray();
		REQUIRED_ENTRIES.forEach(required::add);
		json.add("requiredEntries", required);
		return json;
	}
}
