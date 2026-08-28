/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** External Gradle/Minecraft process boundary for Fabric workspace tasks. */
@FunctionalInterface public interface Fabric1211ProcessRunner {

	ProcessResult run(Path workspaceRoot, List<String> arguments, Duration timeout, Consumer<String> output)
			throws Exception;

	static Fabric1211ProcessRunner system() {
		return system("COPPERBENCH_STAGE3_READY");
	}

	static Fabric1211ProcessRunner system(String readinessMarker) {
		return new SystemProcessRunner(readinessMarker, null);
	}

	static Fabric1211ProcessRunner system(String readinessMarker, Path javaHome) {
		return new SystemProcessRunner(readinessMarker, javaHome);
	}

	record ProcessResult(int exitCode, boolean readinessMarkerSeen) {
	}

	final class SystemProcessRunner implements Fabric1211ProcessRunner {
		private static final Duration SERVER_STABILITY_WINDOW = Duration.ofSeconds(2);
		private final String readinessMarker;
		private final Path javaHome;

		private SystemProcessRunner(String readinessMarker, Path javaHome) {
			this.readinessMarker = readinessMarker;
			this.javaHome = javaHome == null ? null : javaHome.toAbsolutePath().normalize();
		}

		@Override public ProcessResult run(Path workspaceRoot, List<String> arguments, Duration timeout,
				Consumer<String> output) throws Exception {
			boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
			String configuredGradle = System.getenv("COPPERBENCH_STAGE5_GRADLE_EXECUTABLE");
			List<String> command = new ArrayList<>();
			if (windows) {
				command.add("cmd.exe");
				command.add("/c");
				command.add(configuredGradle == null || configuredGradle.isBlank() ? "gradlew.bat" : configuredGradle);
			} else {
				command.add(configuredGradle == null || configuredGradle.isBlank() ? "./gradlew" : configuredGradle);
			}
			command.add("--no-daemon");
			command.addAll(arguments);
			ProcessBuilder builder = new ProcessBuilder(command).directory(workspaceRoot.toFile())
					.redirectErrorStream(true);
			builder.environment().put("JAVA_HOME",
					javaHome == null ? System.getProperty("java.home") : javaHome.toString());
			String configuredGradleUserHome = System.getenv("COPPERBENCH_GRADLE_USER_HOME");
			if (configuredGradleUserHome != null && !configuredGradleUserHome.isBlank()) {
				builder.environment().put("GRADLE_USER_HOME", configuredGradleUserHome);
			}
			Process process = builder.start();
			AtomicBoolean marker = new AtomicBoolean();
			AtomicBoolean serverReady = new AtomicBoolean();
			AtomicBoolean serverFatal = new AtomicBoolean();
			AtomicReference<Exception> readFailure = new AtomicReference<>();
			Thread reader = Thread.startVirtualThread(() -> {
				try (BufferedReader lines = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = lines.readLine()) != null) {
						output.accept(line);
						if (line.contains(readinessMarker)) marker.set(true);
						if (isMinecraftServerReadyLine(line)) serverReady.set(true);
						if (isMinecraftServerFatalLine(line)) serverFatal.set(true);
					}
				} catch (Exception exception) {
					readFailure.set(exception);
				}
			});

			Instant deadline = Instant.now().plus(timeout);
			boolean clientRun = isClientRun(arguments);
			boolean serverRun = isServerRun(arguments);
			Instant serverReadyAt = null;
			while (process.isAlive() && Instant.now().isBefore(deadline)) {
				if (Thread.currentThread().isInterrupted()) {
					destroy(process);
					throw new InterruptedException("Fabric process was cancelled");
				}
				if (serverRun && serverFatal.get()) {
					destroy(process);
					reader.join(Duration.ofSeconds(10));
					return new ProcessResult(1, false);
				}
				boolean ready = clientRun ? marker.get() : serverRun && marker.get() && serverReady.get();
				if (clientRun && ready) {
					destroy(process);
					reader.join(Duration.ofSeconds(10));
					return new ProcessResult(0, true);
				}
				if (serverRun && ready) {
					if (serverReadyAt == null) serverReadyAt = Instant.now();
					if (stabilityWindowSatisfied(serverReadyAt, Instant.now())) {
						destroy(process);
						reader.join(Duration.ofSeconds(10));
						return new ProcessResult(0, !serverFatal.get());
					}
				}
				process.waitFor(200, TimeUnit.MILLISECONDS);
			}
			if (process.isAlive()) {
				destroy(process);
				reader.join(Duration.ofSeconds(10));
				return new ProcessResult(124, clientRun ? marker.get()
						: serverRun && marker.get() && serverReady.get() && !serverFatal.get());
			}
			reader.join(Duration.ofSeconds(10));
			if (readFailure.get() != null) throw readFailure.get();
			boolean stableServerExit = serverRun && stabilityWindowSatisfied(serverReadyAt, Instant.now());
			return new ProcessResult(process.exitValue(),
					clientRun ? marker.get()
							: serverRun ? stableServerExit && marker.get() && serverReady.get() && !serverFatal.get()
									: marker.get());
		}

		private static void destroy(Process process) {
			process.descendants().forEach(ProcessHandle::destroy);
			process.destroy();
			try {
				if (!process.waitFor(5, TimeUnit.SECONDS)) {
					process.descendants().forEach(ProcessHandle::destroyForcibly);
					process.destroyForcibly();
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				process.descendants().forEach(ProcessHandle::destroyForcibly);
				process.destroyForcibly();
			}
		}

		static boolean stabilityWindowSatisfied(Instant serverReadyAt, Instant now) {
			return serverReadyAt != null
					&& Duration.between(serverReadyAt, now).compareTo(SERVER_STABILITY_WINDOW) >= 0;
		}
	}

	static boolean isClientRun(List<String> arguments) {
		return arguments.stream().anyMatch(argument -> argument.equals("runClient") || argument.endsWith(":runClient"));
	}

	static boolean isServerRun(List<String> arguments) {
		return arguments.stream().anyMatch(argument -> argument.equals("runServer") || argument.endsWith(":runServer"));
	}

	static boolean isMinecraftServerReadyLine(String line) {
		return line != null && line.contains("Done (") && line.contains("For help, type \"help\"");
	}

	static boolean isMinecraftServerFatalLine(String line) {
		if (line == null) return false;
		return line.contains("/FATAL]")
				|| line.contains("Attempted to load class") && line.contains("DEDICATED_SERVER")
				|| line.contains("Exception in server tick loop")
				|| line.contains("Encountered an unexpected exception");
	}
}
