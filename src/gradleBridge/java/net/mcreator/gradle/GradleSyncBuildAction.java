/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2024, Pylo, opensource contributors
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

package net.mcreator.gradle;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.eclipse.RunEclipseSynchronizationTasks;

/**
 * Build action transported into workspace Gradle daemons.
 *
 * <p>This class deliberately lives in the {@code gradleBridge} source set and
 * is compiled with {@code --release 17}. Supported workspaces may run their
 * Gradle daemon on Java 17 or 21 even though Copperbench itself runs on Java
 * 25, so this transported class must not inherit the application's bytecode
 * level.</p>
 */
public class GradleSyncBuildAction implements BuildAction<Void> {

	public GradleSyncBuildAction() {
	}

	@Override public Void execute(BuildController controller) {
		controller.getModel(RunEclipseSynchronizationTasks.class);
		return null;
	}

}
