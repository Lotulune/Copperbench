/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.gradle;

import net.mcreator.io.UserFolderManager;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reuses already-downloaded Gradle distributions across workspaces.
 * Gradle itself already shares {@code GRADLE_USER_HOME}; this pool also copies a ready
 * install into the hash folder for a different distribution URL (official vs China mirror).
 */
public final class GradleDistributionPool {

	private static final Logger LOG = LogManager.getLogger(GradleDistributionPool.class);
	private static final Pattern VERSION = Pattern.compile("gradle-([0-9]+(?:\\.[0-9]+){1,3})-bin\\.zip");

	/** Workspace generators currently ship 9.7.0; 1.20.1 stays on 8.8. 9.6.1 is packed if a build machine still has it. */
	public static final List<String> PACKAGED_VERSIONS = List.of("9.7.0", "9.6.1", "8.8");

	private GradleDistributionPool() {
	}

	public static String officialDistributionUrl(String version) {
		return "https://services.gradle.org/distributions/gradle-" + version + "-bin.zip";
	}

	public static String chinaDistributionUrl(String version) {
		return "https://mirrors.huaweicloud.com/gradle/gradle-" + version + "-bin.zip";
	}

	public static int seedPackagedDistributions() {
		return seedPackagedDistributions(UserFolderManager.getGradleHome().toPath(), extraSearchRoots(), true);
	}

	public static int seedPackagedDistributions(Path gradleHome, List<Path> extraRoots, boolean includeChinaMirror) {
		int seeded = 0;
		for (String version : PACKAGED_VERSIONS) {
			if (seedDistributionUrl(officialDistributionUrl(version), gradleHome, extraRoots))
				seeded++;
			if (includeChinaMirror && seedDistributionUrl(chinaDistributionUrl(version), gradleHome, extraRoots))
				seeded++;
		}
		return seeded;
	}

	public static boolean seedForWorkspace(@Nullable Workspace workspace) {
		if (workspace == null)
			return false;
		return seedForWorkspace(workspace.getWorkspaceFolder().toPath(), UserFolderManager.getGradleHome().toPath());
	}

	public static boolean seedForWorkspace(Path workspaceFolder, Path gradleHome) {
		return seedForWorkspace(workspaceFolder, gradleHome, extraSearchRoots());
	}

	static boolean seedForWorkspace(Path workspaceFolder, Path gradleHome, List<Path> extraRoots) {
		Path wrapper = workspaceFolder.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");
		if (!Files.isRegularFile(wrapper))
			return false;
		try {
			Properties properties = new Properties();
			try (var reader = Files.newBufferedReader(wrapper, StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
			String url = properties.getProperty("distributionUrl");
			if (url == null || url.isBlank())
				return false;
			return seedDistributionUrl(unescapeDistributionUrl(url), gradleHome, extraRoots);
		} catch (IOException e) {
			LOG.warn("Failed to seed Gradle distribution for {}", workspaceFolder, e);
			return false;
		}
	}

	public static String unescapeDistributionUrl(String url) {
		return url.replace("\\:", ":");
	}

	public static String hashDistributionUrl(String url) {
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			byte[] bytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
			return new BigInteger(1, bytes).toString(36);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot hash Gradle distribution URL", e);
		}
	}

	public static Optional<String> versionFromUrl(String url) {
		Matcher matcher = VERSION.matcher(url);
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	public static boolean isReadyInstall(Path hashDirectory, String version) {
		if (hashDirectory == null || version == null)
			return false;
		Path launcher = hashDirectory.resolve("gradle-" + version).resolve("bin").resolve("gradle.bat");
		return Files.isRegularFile(launcher);
	}

	public static boolean seedDistributionUrl(String distributionUrl, Path gradleHome, List<Path> extraRoots) {
		Optional<String> version = versionFromUrl(distributionUrl);
		if (version.isEmpty())
			return false;
		Path target = gradleHome.resolve("wrapper").resolve("dists")
				.resolve("gradle-" + version.get() + "-bin").resolve(hashDistributionUrl(distributionUrl));
		if (isReadyInstall(target, version.get()))
			return false;
		Optional<Path> source = findReadyInstall(version.get(), gradleHome, extraRoots);
		if (source.isEmpty())
			return false;
		try {
			copyInstall(source.get(), target, version.get());
			LOG.info("Reused Gradle {} from {} into {}", version.get(), source.get(), target);
			return true;
		} catch (IOException e) {
			LOG.warn("Failed to copy Gradle {} into {}", version.get(), target, e);
			return false;
		}
	}

	static Optional<Path> findReadyInstall(String version, Path gradleHome, List<Path> extraRoots) {
		List<Path> roots = new ArrayList<>();
		roots.add(gradleHome.resolve("wrapper").resolve("dists"));
		for (Path extra : extraRoots) {
			if (extra == null)
				continue;
			roots.add(extra.resolve("wrapper").resolve("dists"));
			roots.add(extra);
		}
		String distName = "gradle-" + version + "-bin";
		for (Path root : roots) {
			Path distRoot = root.resolve(distName);
			if (!Files.isDirectory(distRoot))
				continue;
			try (Stream<Path> hashes = Files.list(distRoot)) {
				Optional<Path> found = hashes.filter(Files::isDirectory)
						.filter(path -> isReadyInstall(path, version)).findFirst();
				if (found.isPresent())
					return found;
			} catch (IOException ignored) {
			}
		}
		return Optional.empty();
	}

	static void copyInstall(Path sourceHashDir, Path targetHashDir, String version) throws IOException {
		Files.createDirectories(targetHashDir);
		Path sourceTree = sourceHashDir.resolve("gradle-" + version);
		Path targetTree = targetHashDir.resolve("gradle-" + version);
		copyTree(sourceTree, targetTree);
		Path ok = targetHashDir.resolve("gradle-" + version + "-bin.zip.ok");
		if (!Files.exists(ok))
			Files.createFile(ok);
		Path sourceZip = sourceHashDir.resolve("gradle-" + version + "-bin.zip");
		if (Files.isRegularFile(sourceZip))
			Files.copy(sourceZip, targetHashDir.resolve(sourceZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
	}

	private static void copyTree(Path source, Path target) throws IOException {
		try (Stream<Path> walk = Files.walk(source)) {
			for (Path path : walk.toList()) {
				Path relative = target.resolve(source.relativize(path).toString());
				if (Files.isDirectory(path))
					Files.createDirectories(relative);
				else {
					Files.createDirectories(relative.getParent());
					Files.copy(path, relative, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	static List<Path> extraSearchRoots() {
		List<Path> roots = new ArrayList<>();
		String userHome = System.getProperty("user.home");
		if (userHome != null)
			roots.add(Path.of(userHome, ".gradle"));
		String pool = System.getProperty("copperbench.gradle.pool");
		if (pool != null && !pool.isBlank())
			roots.add(Path.of(pool));
		roots.addAll(installGradleDistsRoots());
		return roots.stream().filter(Objects::nonNull).filter(Files::isDirectory).toList();
	}

	static List<Path> installGradleDistsRoots() {
		Set<Path> roots = new LinkedHashSet<>();
		String userDir = System.getProperty("user.dir");
		if (userDir != null && !userDir.isBlank())
			roots.add(Path.of(userDir, "gradle-dists"));
		try {
			var source = GradleDistributionPool.class.getProtectionDomain().getCodeSource();
			if (source != null && source.getLocation() != null) {
				Path code = Path.of(source.getLocation().toURI());
				if (Files.isRegularFile(code)) {
					Path lib = code.getParent();
					if (lib != null && lib.getParent() != null)
						roots.add(lib.getParent().resolve("gradle-dists"));
				}
			}
		} catch (Exception ignored) {
		}
		return List.copyOf(roots);
	}

}
