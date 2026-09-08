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

package net.mcreator.io;

import dev.copperbench.platform.PrivatePathPermissions;
import dev.copperbench.platform.UserDataPaths;

import java.io.File;
import java.nio.file.Files;

public class UserFolderManager {

	private static final String GRADLE_HOME_PROPERTY = "copperbench.gradle.user.home";

	private static UserDataPaths paths() {
		return UserDataPaths.current();
	}

	public static File getDataFolder() {
		return paths().data().toFile();
	}

	public static File getConfigFolder() {
		return paths().config().toFile();
	}

	public static File getCacheFolder() {
		return paths().cache().toFile();
	}

	public static File getStateFolder() {
		return paths().state().toFile();
	}

	public static File getRuntimeFolder() {
		return paths().runtime().toFile();
	}

	public static boolean createUserFolderIfNotExists() {
		try {
			PrivatePathPermissions.createPrivateDirectory(getDataFolder().toPath());
			PrivatePathPermissions.createPrivateDirectory(getConfigFolder().toPath());
			PrivatePathPermissions.createPrivateDirectory(getCacheFolder().toPath());
			PrivatePathPermissions.createPrivateDirectory(getStateFolder().toPath());
			PrivatePathPermissions.createPrivateDirectory(getRuntimeFolder().toPath());
			PrivatePathPermissions.createPrivateDirectory(getGradleHome().toPath());
		} catch (java.io.IOException exception) {
			return false;
		}
		return getDataFolder().isDirectory() && Files.isWritable(getDataFolder().toPath())
				&& getConfigFolder().isDirectory() && Files.isWritable(getConfigFolder().toPath())
				&& getCacheFolder().isDirectory() && Files.isWritable(getCacheFolder().toPath())
				&& getStateFolder().isDirectory() && Files.isWritable(getStateFolder().toPath());
	}

	public static File getFileFromUserFolder(String path) {
		return new File(getDataFolder(), path);
	}

	public static File getFileFromConfigFolder(String path) {
		return new File(getConfigFolder(), path);
	}

	public static File getFileFromCacheFolder(String path) {
		return new File(getCacheFolder(), path);
	}

	public static File getFileFromStateFolder(String path) {
		return new File(getStateFolder(), path);
	}

	public static File getFileFromRuntimeFolder(String path) {
		return new File(getRuntimeFolder(), path);
	}

	public static File getGradleHome() {
		String override = System.getProperty(GRADLE_HOME_PROPERTY);
		if (override != null && !override.isBlank())
			return new File(override);
		return getFileFromCacheFolder("gradle");
	}

}
