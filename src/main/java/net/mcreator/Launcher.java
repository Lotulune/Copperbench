/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator;

import dev.copperbench.headless.HeadlessProductLauncher;
import dev.copperbench.ProductIdentity;
import dev.copperbench.release.SupportedPlatform;
import net.mcreator.io.LoggingSystem;
import net.mcreator.io.OS;
import net.mcreator.io.UserFolderManager;
import net.mcreator.io.WindowsPackage;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.util.UTF8Forcer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public class Launcher {

	public static MCreatorVersionNumber version;

	public static void main(String[] args) {
		boolean headless = args.length > 0 && "headless".equalsIgnoreCase(args[0]);
		PrintWriter headlessOutput = headless ? new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(FileDescriptor.out), StandardCharsets.UTF_8), true) : null;
		if (headless)
			System.setProperty("java.awt.headless", "true");
		LoggingSystem.init();
		if (headless)
			LoggingSystem.disableConsoleOutput();

		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();

		UTF8Forcer.forceGlobalUTF8();

		// Disable XML parser depth limit as Blockly XML can go quite nested
		System.setProperty("jdk.xml.maxElementDepth", "0");

		final Logger LOG = LogManager.getLogger("Launcher"); // init logger after log directory is set

		try {
			Properties conf = new Properties();
			conf.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
			version = new MCreatorVersionNumber(conf);
		} catch (IOException e) {
			LOG.error("Failed to read MCreator config", e);
		}

		LOG.info("Starting {} {} with MCreator core {}", ProductIdentity.NAME, ProductIdentity.VERSION, version);
		SupportedPlatform.refuseIfUnsupported();

		// print version of Java
		LOG.info("Java version: {}, VM: {}, vendor: {}", Runtime.version(), System.getProperty("java.vm.name"),
				System.getProperty("java.vendor"));
		LOG.info("Current JAVA_HOME for running instance: {}", System.getProperty("java.home"));

		// after we have libraries loaded, we load preferences
		PreferencesManager.init();

		// set system properties from preferences
		System.setProperty("apple.laf.useScreenMenuBar",
				Boolean.toString(PreferencesManager.PREFERENCES.ui.usemacOSMenuBar.get()));

		// Some flags to prevent rendering issues with certain GPU drivers on Linux
		if (OS.getOS() == OS.LINUX) {
			System.setProperty("sun.java2d.opengl", "false");
			System.setProperty("sun.java2d.pmoffscreen", "false");
		}

		// check if proper version of MCreator per architecture is used
		if (OS.getSystemBits() == OS.BIT32) {
			JOptionPane.showMessageDialog(null,
					"<html>You are trying to run 64-bit " + ProductIdentity.NAME + " on a 32-bit computer.<br>"
							+ ProductIdentity.NAME + " does not support 32-bit platforms.",
					ProductIdentity.NAME + " error", JOptionPane.WARNING_MESSAGE);
			System.exit(-1);
		}

		LOG.info("Installation path: {}", System.getProperty("user.dir"));
		LOG.info("User data directory of {}: {}", ProductIdentity.NAME, UserFolderManager.getFileFromUserFolder("/"));

		WindowsPackage.initIfWindows();

		if (!UserFolderManager.createUserFolderIfNotExists()) {
			if (headless) {
				headlessOutput.println("{\"schemaVersion\":\"1.0\",\"operation\":\"headless_product_start\","
						+ "\"status\":\"failed\",\"code\":\"USER_DIRECTORY_UNAVAILABLE\",\"exitCode\":10}");
				System.exit(10);
				return;
			}
			JOptionPane.showMessageDialog(null,
					"<html><b>" + ProductIdentity.NAME + " failed to write to the user directory!</b><br><br>"
							+ "Make sure the current user can read and write the application data directory:<br><br>"
							+ UserFolderManager.getFileFromUserFolder("/") + "<br>",
					ProductIdentity.NAME + " file system error",
					JOptionPane.WARNING_MESSAGE);
			System.exit(-2);
		}

		if (headless) {
			int exitCode = HeadlessProductLauncher.run(Arrays.copyOfRange(args, 1, args.length),
					headlessOutput);
			System.exit(exitCode);
			return;
		}

		MCreatorApplication.createApplication(Arrays.asList(args));
	}

}
