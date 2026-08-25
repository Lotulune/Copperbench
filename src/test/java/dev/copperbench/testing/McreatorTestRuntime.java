/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.testing;

import net.mcreator.Launcher;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.minecraft.DataListLoader;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.preferences.data.PreferencesData;
import net.mcreator.ui.init.BlocklyJavaScriptsLoader;
import net.mcreator.ui.init.BlocklyToolboxesLoader;
import net.mcreator.ui.init.EntityAnimationsLoader;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.workspace.elements.VariableTypeLoader;

import java.util.Properties;
import java.util.regex.Pattern;

/** Minimal MCreator runtime bootstrap for tests that need real workspace generation without a UI host. */
public final class McreatorTestRuntime {

	private McreatorTestRuntime() {
	}

	public static synchronized void ensureInitialized() throws Exception {
		System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
		configureTestPreferences();
		boolean runtimeReady = Launcher.version != null && PreferencesManager.PREFERENCES != null
				&& PluginLoader.INSTANCE != null && Generator.GENERATOR_CACHE.containsKey("fabric-1.21.1")
				&& ModElementType.BLOCK != null;
		if (runtimeReady)
			return;

		if (PreferencesManager.PREFERENCES == null)
			PreferencesManager.PREFERENCES = new PreferencesData();
		if (Launcher.version == null) {
			Properties configuration = new Properties();
			configuration.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
			Launcher.version = new MCreatorVersionNumber(configuration);
		}
		if (PluginLoader.INSTANCE == null)
			PluginLoader.initInstance();
		ThemeManager.loadThemes();
		UIRES.preloadImages();
		DataListLoader.preloadCache();
		L10N.initTranslations();
		ModAPIManager.initAPIs();
		VariableTypeLoader.loadVariableTypes();
		ModElementTypeLoader.loadModElements();
		BlocklyJavaScriptsLoader.init();
		BlocklyToolboxesLoader.init();
		BlocklyLoader.init();
		EntityAnimationsLoader.init();
		for (String resource : PluginLoader.INSTANCE.getResources(Pattern.compile("generator\\.yaml"))) {
			String generatorName = resource.replace("/generator.yaml", "");
			Generator.GENERATOR_CACHE.computeIfAbsent(generatorName, GeneratorConfiguration::new);
		}
		configureTestPreferences();
	}

	private static void configureTestPreferences() {
		if (PreferencesManager.PREFERENCES != null)
			PreferencesManager.PREFERENCES.backups.enableLocalHistory.set(false);
	}
}
