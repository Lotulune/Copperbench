/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** External process boundary shared by maintained Gradle generator targets. */
@FunctionalInterface public interface GradleProcessRunner {

	ProcessResult run(Path workspaceRoot, List<String> arguments, Duration timeout, Consumer<String> output)
			throws Exception;

	record ProcessResult(int exitCode, boolean readinessMarkerSeen, String runtimeFailureCode) {
		public ProcessResult(int exitCode, boolean readinessMarkerSeen) {
			this(exitCode, readinessMarkerSeen, null);
		}
	}

	/** Stable boundary error when the Gradle wrapper cannot be started at all. */
	final class ProcessStartException extends IOException {
		private final String executable;
		private final Path workspaceRoot;

		public ProcessStartException(String executable, Path workspaceRoot, IOException cause) {
			super("Could not start Gradle executable " + executable + " in "
					+ workspaceRoot.toAbsolutePath().normalize() + ": " + cause.getMessage(), cause);
			this.executable = executable;
			this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
		}

		public String executable() {
			return executable;
		}

		public Path workspaceRoot() {
			return workspaceRoot;
		}
	}
}
