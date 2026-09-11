/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.platform.RuntimePlatform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
		return new SystemProcessRunner(readinessMarker, () -> javaHome);
	}

	static Fabric1211ProcessRunner system(String readinessMarker, Supplier<Path> javaHome) {
		return new SystemProcessRunner(readinessMarker, javaHome);
	}

	record ProcessResult(int exitCode, boolean readinessMarkerSeen, String runtimeFailureCode) {
		public ProcessResult(int exitCode, boolean readinessMarkerSeen) {
			this(exitCode, readinessMarkerSeen, null);
		}
	}

	final class SystemProcessRunner implements Fabric1211ProcessRunner {
		private static final Duration SERVER_STABILITY_WINDOW = Duration.ofSeconds(2);
		private final String readinessMarker;
		private final Supplier<Path> javaHome;

		private SystemProcessRunner(String readinessMarker, Supplier<Path> javaHome) {
			this.readinessMarker = readinessMarker;
			this.javaHome = javaHome;
		}

		@Override public ProcessResult run(Path workspaceRoot, List<String> arguments, Duration timeout,
				Consumer<String> output) throws Exception {
			RuntimePlatform platform = RuntimePlatform.current();
			boolean windows = platform.operatingSystem() == RuntimePlatform.OperatingSystem.WINDOWS;
			boolean linux = platform.operatingSystem() == RuntimePlatform.OperatingSystem.LINUX;
			boolean clientRun = isClientRun(arguments);

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
			Path resolvedJavaHome = javaHome == null ? null : javaHome.get();
			if (resolvedJavaHome == null) {
				String configuredJavaHome = System.getProperty("java.home");
				resolvedJavaHome = configuredJavaHome == null || configuredJavaHome.isBlank()
						? null : Path.of(configuredJavaHome);
			}
			if (resolvedJavaHome != null) {
				builder.environment().put("JAVA_HOME", resolvedJavaHome.toAbsolutePath().normalize().toString());
			}
			String configuredGradleUserHome = System.getenv("COPPERBENCH_GRADLE_USER_HOME");
			if (configuredGradleUserHome != null && !configuredGradleUserHome.isBlank()) {
				builder.environment().put("GRADLE_USER_HOME", configuredGradleUserHome);
			}
			if (resolvedJavaHome != null)
				dev.copperbench.gradle.GradleRuntimeCompatibility.configure(resolvedJavaHome, builder.environment(), output);
			Process process;
			try {
				process = builder.start();
			} catch (IOException exception) {
				throw new GradleProcessRunner.ProcessStartException(command.getFirst(), workspaceRoot, exception);
			}
			AtomicBoolean marker = new AtomicBoolean();
			AtomicBoolean serverReady = new AtomicBoolean();
			AtomicBoolean serverFatal = new AtomicBoolean();
			AtomicReference<Exception> readFailure = new AtomicReference<>();
			AtomicReference<String> runtimeFailureCode = new AtomicReference<>();
			Thread reader = Thread.startVirtualThread(() -> {
				try (BufferedReader lines = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = lines.readLine()) != null) {
						output.accept(line);
						if (line.contains(readinessMarker)) marker.set(true);
						if (isMinecraftServerReadyLine(line)) serverReady.set(true);
						if (isMinecraftServerFatalLine(line)) serverFatal.set(true);
						if (clientRun && linux) {
							String code = linuxGraphicalFailureCode(line);
							if (code != null) runtimeFailureCode.compareAndSet(null, code);
						}
					}
				} catch (Exception exception) {
					readFailure.set(exception);
				}
			});

			boolean noTimeout = timeout == null || timeout.isZero() || timeout.isNegative();
			Instant deadline = noTimeout ? null : Instant.now().plus(timeout);
			boolean serverRun = isServerRun(arguments);
			Instant serverReadyAt = null;
			while (process.isAlive() && (noTimeout || Instant.now().isBefore(deadline))) {
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
			if (process.isAlive() && !noTimeout) {
				destroy(process);
				reader.join(Duration.ofSeconds(10));
				return new ProcessResult(124, clientRun ? marker.get()
						: serverRun && marker.get() && serverReady.get() && !serverFatal.get(), runtimeFailureCode.get());
			}
			reader.join(Duration.ofSeconds(10));
			if (readFailure.get() != null) throw readFailure.get();
			boolean stableServerExit = serverRun && stabilityWindowSatisfied(serverReadyAt, Instant.now());
			return new ProcessResult(process.exitValue(),
					clientRun ? marker.get()
							: serverRun ? stableServerExit && marker.get() && serverReady.get() && !serverFatal.get()
									: marker.get(), runtimeFailureCode.get());
		}

		private static void destroy(Process process) {
			List<ProcessHandle> descendants = process.descendants().toList();
			boolean interrupted = Thread.interrupted();
			try {
				descendants.forEach(ProcessHandle::destroy);
				process.destroy();
				if (!waitForExit(process.toHandle(), descendants, Duration.ofSeconds(5))) {
					// The Gradle wrapper can exit before its JavaExec/Minecraft child. Keep the original
					// descendant handles and force every survivor instead of treating root exit as cleanup.
					descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
					process.descendants().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
					if (process.isAlive()) process.destroyForcibly();
					waitForExit(process.toHandle(), descendants, Duration.ofSeconds(5));
				}
			} catch (InterruptedException exception) {
				interrupted = true;
				descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
				process.descendants().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
				if (process.isAlive()) process.destroyForcibly();
			} finally {
				if (interrupted) Thread.currentThread().interrupt();
			}
		}

		private static boolean waitForExit(ProcessHandle root, List<ProcessHandle> descendants, Duration timeout)
				throws InterruptedException {
			long deadline = System.nanoTime() + timeout.toNanos();
			while (root.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
				if (System.nanoTime() >= deadline) return false;
				Thread.sleep(50);
			}
			return true;
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

	static String linuxGraphicalFailureCode(String line) {
		if (line == null) return null;
		String normalized = line.toLowerCase(Locale.ROOT);
		if (normalized.contains("the display environment variable is missing")
				|| normalized.contains("failed to open display")
				|| normalized.contains("cannot open display")
				|| normalized.contains("failed to connect to display")
				|| normalized.contains("failed to connect to wayland display")
				|| normalized.contains("glfw_platform_unavailable"))
			return "LINUX_DISPLAY_UNAVAILABLE";
		if (normalized.contains("libgl error")
				|| normalized.contains("glxbadfbconfig")
				|| normalized.contains("egl_bad")
				|| normalized.contains("failed to create opengl context")
				|| normalized.contains("could not create gl context")
				|| normalized.contains("couldn't find a valid opengl pixel format"))
			return "LINUX_OPENGL_INITIALIZATION_FAILED";
		if (normalized.contains("failed to initialize glfw") || normalized.contains("glfw error"))
			return "LINUX_GLFW_INITIALIZATION_FAILED";
		return null;
	}
}
