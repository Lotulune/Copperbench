/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.network;

import net.mcreator.io.UserFolderManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configures Gradle distribution and Maven repository mirrors for networks in mainland China.
 * Gradle wrapper downloads happen before init scripts, so the wrapper URL must be rewritten too.
 */
public final class ChinaMirrorService {

	private static final Logger LOG = LogManager.getLogger(ChinaMirrorService.class);

	public static final String INIT_SCRIPT_NAME = "copperbench-china-mirrors.gradle";
	public static final String OFFICIAL_GRADLE_DISTRIBUTION = "https://services.gradle.org/distributions/";
	public static final String CHINA_GRADLE_DISTRIBUTION = "https://mirrors.huaweicloud.com/gradle/";
	public static final int MIN_NETWORK_TIMEOUT_MS = 60_000;

	private static final String INIT_SCRIPT_RESOURCE = "/dev/copperbench/network/china-mirrors.init.gradle";
	private static final Pattern NETWORK_TIMEOUT = Pattern.compile("(?m)^networkTimeout=(\\d+)\\s*$");

	private ChinaMirrorService() {
	}

	public static boolean isEnabled() {
		return PreferencesManager.PREFERENCES != null && Boolean.TRUE.equals(
				PreferencesManager.PREFERENCES.gradle.useChinaMirrors.get());
	}

	public static boolean hasBeenPrompted() {
		return PreferencesManager.PREFERENCES != null && Boolean.TRUE.equals(
				PreferencesManager.PREFERENCES.hidden.chinaMirrorsPrompted.get());
	}

	public static void rememberChoice(boolean useChinaMirrors) {
		if (PreferencesManager.PREFERENCES == null)
			return;
		PreferencesManager.PREFERENCES.gradle.useChinaMirrors.set(useChinaMirrors);
		PreferencesManager.PREFERENCES.hidden.chinaMirrorsPrompted.set(true);
		PreferencesManager.savePreferences();
		syncUserHome();
	}

	public static void syncUserHome() {
		try {
			applyUserHome(UserFolderManager.getGradleHome().toPath(), isEnabled());
		} catch (IOException e) {
			LOG.error("Failed to sync Copperbench Gradle user home mirrors", e);
		}
	}

	public static void applyToWorkspace(@Nullable Workspace workspace) {
		if (workspace == null)
			return;
		applyToWorkspace(workspace.getWorkspaceFolder().toPath(), isEnabled());
	}

	public static boolean applyToWorkspace(Path workspaceFolder, boolean enabled) {
		if (!enabled || workspaceFolder == null)
			return false;
		Path wrapper = workspaceFolder.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");
		if (!Files.isRegularFile(wrapper))
			return false;
		try {
			String original = Files.readString(wrapper, StandardCharsets.UTF_8);
			String rewritten = rewriteWrapperProperties(original, true);
			if (Objects.equals(original, rewritten))
				return false;
			Files.writeString(wrapper, rewritten, StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			LOG.error("Failed to rewrite Gradle wrapper mirrors in {}", wrapper, e);
			return false;
		}
	}

	public static void applyUserHome(Path gradleHome, boolean enabled) throws IOException {
		Path initDirectory = gradleHome.resolve("init.d");
		Path initScript = initDirectory.resolve(INIT_SCRIPT_NAME);
		if (enabled) {
			Files.createDirectories(initDirectory);
			Files.writeString(initScript, loadInitScript(), StandardCharsets.UTF_8);
		} else if (Files.exists(initScript)) {
			Files.delete(initScript);
		}
	}

	public static String loadInitScript() {
		try (InputStream stream = ChinaMirrorService.class.getResourceAsStream(INIT_SCRIPT_RESOURCE)) {
			if (stream == null)
				throw new IllegalStateException("Missing China mirror init script resource");
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read China mirror init script resource", e);
		}
	}

	public static String rewriteWrapperProperties(String content, boolean enabled) {
		if (!enabled || content == null)
			return content;
		String rewritten = content.replace("https\\://services.gradle.org/distributions/",
						"https\\://mirrors.huaweicloud.com/gradle/")
				.replace("https://services.gradle.org/distributions/", CHINA_GRADLE_DISTRIBUTION);
		return bumpNetworkTimeout(rewritten);
	}

	private static String bumpNetworkTimeout(String content) {
		Matcher matcher = NETWORK_TIMEOUT.matcher(content);
		if (matcher.find()) {
			int current = Integer.parseInt(matcher.group(1));
			if (current >= MIN_NETWORK_TIMEOUT_MS)
				return content;
			return matcher.replaceFirst("networkTimeout=" + MIN_NETWORK_TIMEOUT_MS);
		}
		String separator = content.endsWith("\n") || content.endsWith("\r") ? "" : "\n";
		return content + separator + "networkTimeout=" + MIN_NETWORK_TIMEOUT_MS + "\n";
	}

}
