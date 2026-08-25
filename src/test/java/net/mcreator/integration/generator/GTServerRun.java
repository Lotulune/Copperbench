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

package net.mcreator.integration.generator;

import net.mcreator.gradle.GradleUtils;
import net.mcreator.io.OutputStreamEventHandler;
import net.mcreator.minecraft.ServerUtil;
import net.mcreator.util.TestUtil;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.*;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class GTServerRun {

	private static void appendToStringBuilder(Logger LOG, StringBuilder sb, String s,
			CancellationTokenSource cancellationSource) {
		if (s.contains("/DEBUG]") || s.contains("Downloading: "))
			return; // Skip DEBUG prints

		sb.append(s);
		sb.append(System.lineSeparator());

		// If we detect the server has fully started, stop the server execution
		if (didServerStart(s)) {
			cancellationSource.cancel();
		}

		if (!TestUtil.isRunningInGitHubActions()) {
			LOG.info("[Minecraft Server] {}", s);
		}
	}

	public static void runTest(Logger LOG, String generatorName, Workspace workspace) throws Exception {
		runTest(LOG, generatorName, workspace, Duration.ofMinutes(20));
	}

	public static void runTest(Logger LOG, String generatorName, Workspace workspace, Duration timeout) throws Exception {
		BuildLauncher buildLauncher = GradleUtils.getGradleTaskLauncher(workspace.getGeneratorConfiguration(),
				GradleUtils.getGradleProjectConnection(workspace),
				workspace.getGeneratorConfiguration().getGradleTaskFor("run_server"));

		StringBuilder sb = new StringBuilder();

		CancellationTokenSource cancellationSource = GradleConnector.newCancellationTokenSource();
		CancellationToken token = cancellationSource.token();
		buildLauncher.withCancellationToken(token);
		AtomicBoolean timedOut = new AtomicBoolean(false);
		var timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
		var timeoutTask = timeoutExecutor.schedule(() -> {
			timedOut.set(true);
			cancellationSource.cancel();
		}, timeout.toMillis(), TimeUnit.MILLISECONDS);

		buildLauncher.setStandardError(
				new OutputStreamEventHandler(line -> appendToStringBuilder(LOG, sb, line, cancellationSource)));
		buildLauncher.setStandardOutput(
				new OutputStreamEventHandler(line -> appendToStringBuilder(LOG, sb, line, cancellationSource)));

		try {
			if (!ServerUtil.isEULAAccepted(workspace))
				ServerUtil.acceptEULA(workspace);

			buildLauncher.run();

			// If the server run failed, or crashed, throw an exception
			if (!token.isCancellationRequested() || didRunFail(sb.toString())) {
				throw new Exception("Server run failed with error");
			}
		} catch (BuildCancelledException e) {
			if (timedOut.get())
				throw new IllegalStateException(generatorName + " server run timed out after " + timeout, e);
			if (didRunFail(sb.toString()))
				throw new Exception("Server run failed with error");
		} catch (Exception e) {
			LOG.error("Server run failed for {} generator with log:\n{}", generatorName, sb, e);
			throw e;
		} finally {
			timeoutTask.cancel(false);
			timeoutExecutor.shutdownNow();
		}

		LOG.info("[{}] Gradle server run OK", generatorName);
	}

	private static boolean didRunFail(String result) {
		return result.contains("---- Minecraft Crash Report ----");
	}

	private static boolean didServerStart(String result) {
		return result.contains("For help, type \"help\"") || result.contains("Enabled Gametest Namespaces");
	}

}
