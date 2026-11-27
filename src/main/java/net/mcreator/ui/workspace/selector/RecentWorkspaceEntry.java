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

package net.mcreator.ui.workspace.selector;

import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.workspace.Workspace;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public final class RecentWorkspaceEntry {

	@Nonnull private String name;
	private GeneratorFlavor type;

	@Nonnull private final String path;

	@Nullable private String mcrVersion;

	public RecentWorkspaceEntry(Workspace workspace, File path, @Nullable String mcrVersion) {
		this.name = workspace.getWorkspaceSettings().getModName();
		this.path = path.toString();
		this.type = workspace.getGeneratorConfiguration().getGeneratorFlavor();
		this.mcrVersion = mcrVersion;
	}

	/** A Gradle project folder Copperbench did not create (ADR-0020); its path is the folder itself. */
	public RecentWorkspaceEntry(@Nonnull String name, File folder, GeneratorFlavor type) {
		this.name = name;
		this.path = folder.toString();
		this.type = type;
		this.mcrVersion = null;
	}

	/** Whether this entry is a project folder rather than a {@code .mcreator} file. */
	public boolean isFolder() {
		return getPath().isDirectory();
	}

	/** The folder that holds the project: the entry itself for a project folder. */
	@Nonnull public File getFolder() {
		File entry = getPath();
		return entry.isDirectory() ? entry : entry.getAbsoluteFile().getParentFile();
	}

	public void update(RecentWorkspaceEntry source) {
		this.name = source.getName();
		this.type = source.getType();
		this.mcrVersion = source.getMCRVersion();
	}

	@Nonnull public File getPath() {
		return new File(path);
	}

	@Nonnull public String getName() {
		return name;
	}

	@Nonnull public GeneratorFlavor getType() {
		if (type == null)
			return GeneratorFlavor.UNKNOWN;

		return type;
	}

	@Nullable public String getMCRVersion() {
		return mcrVersion;
	}

	@Override public int hashCode() {
		return path.hashCode();
	}

	@Override public boolean equals(Object obj) {
		if (obj instanceof RecentWorkspaceEntry cmpObj)
			return cmpObj.path.equals(path);
		return false;
	}
}
