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
import dev.copperbench.headless.BootstrapProductLauncher;
import dev.copperbench.ProductIdentity;
import dev.copperbench.shell.ProductDialogs;
import static dev.copperbench.shell.UiLocalePreferences.text;
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
		boolean bootstrap = args.length > 0 && "bootstrap".equalsIgnoreCase(args[0]);
		boolean machineReadable = headless || bootstrap;
		PrintWriter machineOutput = machineReadable ? new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(FileDescriptor.out), StandardCharsets.UTF_8), true) : null;
		// Keep the native API pipe free of incidental stdout from plugins and runtime initialization.
		if (headless && args.length == 4 && "api".equals(args[3]))
			System.setOut(System.err);
		if (headless)
			System.setProperty("java.awt.headless", "true");
		LoggingSystem.init();
		if (machineReadable)
			LoggingSystem.disableConsoleOutput();

		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();

		UTF8Forcer.forceGlobalUTF8();

		// Disable XML parser depth limit as Blockly XML can go quite nested
		System.setProperty("jdk.xml.maxElementDepth", "0");

		final Logger LOG = LogManager.getLogger("Launcher"); // init logger after log directory is set

		// Headless generation now also prepares the import index through the Tooling API in this JVM.
		if (headless || !machineReadable || (bootstrap && args.length > 1 && "create-workspace".equals(args[1]))) {
			try {
				dev.copperbench.gradle.GradleRuntimeCompatibility.configureApplicationRuntime(LOG::warn);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Application local IPC probe interrupted", exception);
			} catch (IOException exception) {
				LOG.error("Application local IPC is unavailable; networking features may not start", exception);
			}
		}

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

		try { dev.copperbench.shell.FirstRunOnboarding.initialize(); }
		catch (IOException exception) { LOG.warn("Could not initialize first-run guidance", exception); }
		dev.copperbench.release.ProductUpdates.cleanupFinished();

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
			ProductDialogs.show(null,
					text("Copperbench 需要 64 位系统，请在支持的 64 位设备上安装。",
							"Copperbench requires a 64-bit system. Please install it on a supported 64-bit device."),
					text("无法启动 Copperbench", "Could not start Copperbench"), JOptionPane.ERROR_MESSAGE,
					new Object[]{text("确定", "OK")}, text("确定", "OK"));
			System.exit(-1);
		}

		LOG.info("Installation path: {}", System.getProperty("user.dir"));
		LOG.info("User data directory of {}: {}", ProductIdentity.NAME, UserFolderManager.getFileFromUserFolder("/"));

		WindowsPackage.initIfWindows();

		if (!UserFolderManager.createUserFolderIfNotExists()) {
			if (machineReadable) {
				machineOutput.println("{\"schemaVersion\":\"1.0\",\"operation\":\""
						+ (headless ? "headless_product_start" : "bootstrap_product_start") + "\","
						+ "\"status\":\"failed\",\"code\":\"USER_DIRECTORY_UNAVAILABLE\",\"exitCode\":10}");
				System.exit(10);
				return;
			}
			ProductDialogs.show(null,
					text("无法保存应用数据。请检查以下目录的读写权限和磁盘剩余空间：\n\n",
							"Could not save app data. Check write access and available disk space for this folder:\n\n")
							+ UserFolderManager.getFileFromUserFolder("/"),
					text("无法启动 Copperbench", "Could not start Copperbench"), JOptionPane.ERROR_MESSAGE,
					new Object[]{text("确定", "OK")}, text("确定", "OK"));
			System.exit(-2);
		}

		if (headless) {
			int exitCode = HeadlessProductLauncher.run(Arrays.copyOfRange(args, 1, args.length),
					machineOutput);
			System.exit(exitCode);
			return;
		}

		if (bootstrap) {
			int exitCode = BootstrapProductLauncher.run(Arrays.copyOfRange(args, 1, args.length), machineOutput);
			System.exit(exitCode);
			return;
		}

		MCreatorApplication.createApplication(Arrays.asList(args));
	}

}
