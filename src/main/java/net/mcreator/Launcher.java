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

		// Bootstrap creation also uses the Tooling API in this JVM before Core tasks exist.
		if (!machineReadable || (bootstrap && args.length > 1 && "create-workspace".equals(args[1]))) {
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
			JOptionPane.showOptionDialog(null,
					"<html>当前系统为 32 位，无法运行 64 位 " + ProductIdentity.NAME + "。<br>"
							+ ProductIdentity.NAME + " 不支持 32 位系统。",
					ProductIdentity.NAME + " 启动错误", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, new Object[]{"确定"}, "确定");
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
			JOptionPane.showOptionDialog(null,
					"<html><b>" + ProductIdentity.NAME + " 无法写入用户数据目录！</b><br><br>"
							+ "请确认当前用户具有以下应用数据目录的读取和写入权限：<br><br>"
							+ UserFolderManager.getFileFromUserFolder("/") + "<br>",
					ProductIdentity.NAME + " 文件系统错误", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, new Object[]{"确定"}, "确定");
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
