/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import dev.copperbench.gradle.GradleDistributionPool;
import dev.copperbench.network.ChinaMirrorService;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.minecraft.DataListLoader;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.init.BlocklyJavaScriptsLoader;
import net.mcreator.ui.init.BlocklyToolboxesLoader;
import net.mcreator.ui.init.EntityAnimationsLoader;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.workspace.elements.VariableTypeLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimal production MCreator bootstrap for the command-line product entry.
 *
 * <p>This deliberately omits SplashScreen, JCEF, windows, Discord, analytics and
 * every other desktop-only service. Upstream model registries do require theme
 * metadata and UIRES to be initialized even when {@code java.awt.headless=true};
 * those resource registries do not create a window or browser.</p>
 */
final class HeadlessRuntimeBootstrap {

	private HeadlessRuntimeBootstrap() {
	}

	static synchronized void ensureInitialized() {
		boolean ready = PluginLoader.INSTANCE != null && ModElementType.BLOCK != null
				&& !Generator.GENERATOR_CACHE.isEmpty();
		if (ready)
			return;

		if (PluginLoader.INSTANCE == null)
			PluginLoader.initInstance();
		PreferencesManager.initNonCore();
		ThemeManager.loadThemes();
		UIRES.preloadImages();
		L10N.initTranslations();
		DataListLoader.preloadCache();
		ModAPIManager.initAPIs();
		VariableTypeLoader.loadVariableTypes();
		BlocklyJavaScriptsLoader.init();
		BlocklyToolboxesLoader.init();
		BlocklyLoader.init();
		EntityAnimationsLoader.init();
		if (ModElementType.BLOCK == null)
			ModElementTypeLoader.loadModElements();

		Set<String> resources = PluginLoader.INSTANCE.getResources(Pattern.compile("generator\\.yaml"));
		List<String> sorted = new ArrayList<>(resources);
		Collections.sort(sorted);
		for (String resource : sorted) {
			String generator = resource.replace("/generator.yaml", "");
			Generator.GENERATOR_CACHE.computeIfAbsent(generator, GeneratorConfiguration::new);
		}

		ChinaMirrorService.syncUserHome();
		GradleDistributionPool.seedPackagedDistributions();
	}
}
