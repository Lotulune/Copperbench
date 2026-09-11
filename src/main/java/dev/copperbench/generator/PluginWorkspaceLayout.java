/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;
import dev.copperbench.platform.ExecutableFilePermissions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/** Detects an already-materialized MCreator plugin workspace so first-party projection does not overwrite it. */
public final class PluginWorkspaceLayout {

	private PluginWorkspaceLayout() {
	}

	static void normalizeLauncherLineEndings(Path launcher) throws IOException {
		String content = Files.readString(launcher, StandardCharsets.UTF_8);
		if (content.indexOf('\r') < 0) return;
		Files.writeString(launcher, content.replace("\r\n", "\n").replace('\r', '\n'), StandardCharsets.UTF_8);
	}

	public static boolean present(Path root) throws IOException {
		if (root == null || !Files.isDirectory(root))
			return false;
		boolean hasWorkspaceFile;
		try (Stream<Path> files = Files.list(root)) {
			hasWorkspaceFile = files.anyMatch(path -> path.getFileName().toString().endsWith(".mcreator"));
		}
		if (!hasWorkspaceFile)
			return false;
		Path src = root.resolve("src");
		if (!Files.isDirectory(src))
			return false;
		try (Stream<Path> files = Files.walk(src)) {
			return files.anyMatch(Files::isRegularFile);
		}
	}

	public static List<String> relativeSourcePaths(Path root) throws IOException {
		Path src = root.resolve("src");
		if (!Files.isDirectory(src))
			return List.of();
		try (Stream<Path> files = Files.walk(src)) {
			return files.filter(Files::isRegularFile)
					.map(path -> root.relativize(path).toString().replace('\\', '/'))
					.sorted()
					.toList();
		}
	}

	/**
	 * Restores only the workspace-local Gradle runtime files required by Copperbench task runners. Existing plugin
	 * build configuration is never overwritten.
	 */
	public static void ensureGradleRuntime(Path root, Path distributionRoot, String gradleWrapperZip)
			throws IOException {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path normalizedDistribution = distributionRoot.toAbsolutePath().normalize();
		Path posixLauncher = normalizedRoot.resolve("gradlew");
		copyIfMissing(posixLauncher, normalizedDistribution.resolve("gradlew"));
		normalizeLauncherLineEndings(posixLauncher);
		ExecutableFilePermissions.ensureOwnerExecutable(posixLauncher);
		copyIfMissing(normalizedRoot.resolve("gradlew.bat"), normalizedDistribution.resolve("gradlew.bat"));
		copyIfMissing(normalizedRoot.resolve("gradle/wrapper/gradle-wrapper.jar"),
				normalizedDistribution.resolve("gradle/wrapper/gradle-wrapper.jar"));
		Path wrapperProperties = normalizedRoot.resolve("gradle/wrapper/gradle-wrapper.properties");
		if (!Files.isRegularFile(wrapperProperties)) {
			Files.createDirectories(wrapperProperties.getParent());
			Files.writeString(wrapperProperties, """
					distributionBase=GRADLE_USER_HOME
					distributionPath=wrapper/dists
					distributionUrl=https\\://mirrors.huaweicloud.com/gradle/%s
					networkTimeout=60000
					retries=3
					retryBackOffMs=2000
					validateDistributionUrl=true
					zipStoreBase=GRADLE_USER_HOME
					zipStorePath=wrapper/dists
					""".formatted(gradleWrapperZip).replace("\r\n", "\n"), StandardCharsets.UTF_8);
		}
	}

	private static void copyIfMissing(Path target, Path source) throws IOException {
		if (Files.isRegularFile(target)) return;
		if (!Files.isRegularFile(source)) throw new IOException("Missing distribution file: " + source);
		Files.createDirectories(target.getParent());
		Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
	}

}
