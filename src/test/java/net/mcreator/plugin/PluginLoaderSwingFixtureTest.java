/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.plugin;

import net.mcreator.Launcher;
import net.mcreator.io.LoggingSystem;
import net.mcreator.plugin.events.ui.PreferencesDialogEvent;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginLoaderSwingFixtureTest {

	@BeforeAll static void initializePluginRuntime() throws Exception {
		LoggingSystem.init();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		Properties configuration = new Properties();
		configuration.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
		Launcher.version = new MCreatorVersionNumber(configuration);
		PreferencesManager.init();
	}

	@Test void compilesAndLoadsRepresentativeSwingPluginThroughPluginLoader(@TempDir Path temporaryDirectory)
			throws Exception {
		Path pluginDirectory = temporaryDirectory.resolve("fixture-c-swing");
		Path source = pluginDirectory.resolve("fixtures/SwingPlugin.java");
		Files.createDirectories(source.getParent());
		copyResource("/plugin-compatibility/c-swing/plugin.json", pluginDirectory.resolve("plugin.json"));
		copyResource("/plugin-compatibility/c-swing/fixtures/SwingPlugin.java", source);

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "The test JDK must include javac");
		int compilationResult = compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"),
				"-d", pluginDirectory.toString(), source.toString());
		assertEquals(0, compilationResult, "Representative Swing plugin did not compile against the plugin API");

		boolean javaPluginsEnabled = PreferencesManager.PREFERENCES.hidden.enableJavaPlugins.get();
		try {
			PreferencesManager.PREFERENCES.hidden.enableJavaPlugins.set(true);
			PluginLoader loader = new PluginLoader(temporaryDirectory.toFile());
			JavaPlugin plugin = loader.getJavaPlugins().stream().findFirst().orElseThrow();

			assertEquals("fixture-c-swing", plugin.getPlugin().getID());
			assertEquals("fixtures.SwingPlugin", plugin.getClass().getName());
			assertEquals(1, plugin.getListeners().get(MCreatorLoadedEvent.class).size());
			assertEquals(1, plugin.getListeners().get(PreferencesDialogEvent.SectionsLoaded.class).size());
			assertEquals("fixture-c-swing-panel",
					assertInstanceOf(JPanel.class, plugin.getClass().getMethod("createLegacyPanel").invoke(plugin)).getName());
			assertEquals("fixture-c-swing-menu",
					assertInstanceOf(JMenu.class, plugin.getClass().getMethod("createLegacyMenu").invoke(plugin)).getName());
			assertEquals("fixture-c-swing-toolbar",
					assertInstanceOf(JToolBar.class, plugin.getClass().getMethod("createLegacyToolBar").invoke(plugin)).getName());
		} finally {
			PreferencesManager.PREFERENCES.hidden.enableJavaPlugins.set(javaPluginsEnabled);
		}
	}

	private static void copyResource(String resourceName, Path target) throws Exception {
		try (InputStream resource = PluginLoaderSwingFixtureTest.class.getResourceAsStream(resourceName)) {
			assertNotNull(resource, resourceName);
			Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
