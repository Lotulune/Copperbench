/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Background independent host for one workspace, reached with the SDK's {@code Workspace.connect}.
 *
 * <p>{@code Workspace.open} starts a Core process per script and stops it on close, which repeats about seven
 * seconds of product startup for every short agent script. This host keeps the workspace open behind the same
 * owner-only connection file and token as the desktop, and exits once no client has been connected and no task has
 * run for the idle timeout. It holds the workspace write lock while it runs, like {@code Workspace.open}.</p>
 */
final class HeadlessPythonHost {
	static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(15);
	static final Duration MIN_IDLE_TIMEOUT = Duration.ofSeconds(5);
	static final Duration MAX_IDLE_TIMEOUT = Duration.ofHours(8);
	private static final long POLL_MILLIS = 250;

	private HeadlessPythonHost() {}

	/** Parses {@code serve [--idle-timeout <seconds>]}. */
	static Duration idleTimeout(String[] commandArguments) {
		if (commandArguments.length == 1) return DEFAULT_IDLE_TIMEOUT;
		if (commandArguments.length != 3 || !commandArguments[1].equals("--idle-timeout"))
			throw new IllegalArgumentException("Usage: headless --workspace <path.mcreator|Gradle project folder> serve [--idle-timeout <seconds>]");
		long seconds;
		try { seconds = Long.parseLong(commandArguments[2]); }
		catch (NumberFormatException exception) { throw new IllegalArgumentException("--idle-timeout must be a whole number of seconds"); }
		Duration value = Duration.ofSeconds(seconds);
		if (value.compareTo(MIN_IDLE_TIMEOUT) < 0 || value.compareTo(MAX_IDLE_TIMEOUT) > 0)
			throw new IllegalArgumentException("--idle-timeout must be between " + MIN_IDLE_TIMEOUT.toSeconds()
					+ " and " + MAX_IDLE_TIMEOUT.toSeconds() + " seconds");
		return value;
	}

	/**
	 * Publishes the connection, reports readiness on {@code output} and blocks until idle.
	 *
	 * @param tasksActive true while any task of this workspace is queued or running
	 */
	static int serve(Path workspaceFile, UUID workspaceId, HeadlessWorkspaceEntryAdapter adapter,
			BooleanSupplier tasksActive, Duration idleTimeout, PrintWriter output) throws IOException, InterruptedException {
		try (DesktopPythonRuntime runtime = DesktopPythonRuntime.start(workspaceFile, workspaceId, adapter)) {
			JsonObject ready = new JsonObject();
			ready.addProperty("nativeApiVersion", "1");
			ready.addProperty("status", "serving");
			ready.addProperty("workspaceId", workspaceId.toString());
			ready.addProperty("idleTimeoutSeconds", idleTimeout.toSeconds());
			// The token stays in the owner-only connection file; stdout may be logged.
			output.println(ready);
			output.flush();
			long idleNanos = idleTimeout.toNanos();
			long busyUntil = System.nanoTime();
			while (true) {
				if (runtime.activeClients() > 0 || tasksActive.getAsBoolean()) busyUntil = System.nanoTime();
				long idleFor = Math.min(System.nanoTime() - busyUntil, runtime.nanosSinceClientChange());
				if (runtime.activeClients() == 0 && idleFor >= idleNanos) return HeadlessExitCode.SUCCESS.code();
				Thread.sleep(POLL_MILLIS);
			}
		}
	}
}
