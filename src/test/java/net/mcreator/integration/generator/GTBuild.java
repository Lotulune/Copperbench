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

package net.mcreator.integration.generator;

import net.mcreator.gradle.GradleUtils;
import net.mcreator.io.OutputStreamEventHandler;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class GTBuild {

	public static void runTest(Logger LOG, String generatorName, Workspace workspace)
			throws GradleConnectionException, IllegalStateException {
		runTest(LOG, generatorName, workspace, Duration.ofMinutes(20));
	}

	public static void runTest(Logger LOG, String generatorName, Workspace workspace, Duration timeout)
			throws GradleConnectionException, IllegalStateException {
		BuildLauncher buildLauncher = GradleUtils.getGradleTaskLauncher(workspace.getGeneratorConfiguration(),
				GradleUtils.getGradleProjectConnection(workspace), "build");
		CancellationTokenSource cancellationSource = GradleConnector.newCancellationTokenSource();
		buildLauncher.withCancellationToken(cancellationSource.token());
		AtomicBoolean timedOut = new AtomicBoolean(false);
		var timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
		var timeoutTask = timeoutExecutor.schedule(() -> {
			timedOut.set(true);
			cancellationSource.cancel();
		}, timeout.toMillis(), TimeUnit.MILLISECONDS);

		StringBuilder sb = new StringBuilder();

		buildLauncher.setStandardError(
				new OutputStreamEventHandler(line -> sb.append(line).append(System.lineSeparator())));

		try {
			buildLauncher.run();

			if (sb.toString().contains(": warning:") || sb.toString().contains(": error: ")) {
				LOG.warn("Gradle build for {} generator produced log:\n{}", generatorName, sb);
			}
		} catch (BuildCancelledException exception) {
			if (timedOut.get())
				throw new IllegalStateException(generatorName + " build timed out after " + timeout, exception);
			throw exception;
		} catch (GradleConnectionException | IllegalStateException e) {
			LOG.error("Gradle build failed for {} generator with log:\n{}", generatorName, sb, e);
			throw e;
		} finally {
			timeoutTask.cancel(false);
			timeoutExecutor.shutdownNow();
		}

		LOG.info("[{}] Gradle build OK", generatorName);
	}

}
