/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import dev.copperbench.platform.RuntimePlatform;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** First-release desktop support. Windows 10 is out of scope. */
public final class SupportedPlatform {

	public static final String MINIMUM_OS = "Windows 11";
	public static final int MINIMUM_BUILD = 22000;
	public static final String UNSUPPORTED_MESSAGE =
			"Copperbench 需要 64 位 Windows 11（系统内部版本 22000 或更高）。不支持 Windows 10。";
	public static final String STAGE15_LINUX_CANDIDATE_PROPERTY = "copperbench.stage15LinuxCandidate";

	private SupportedPlatform() {
	}

	public static boolean isSupported(String osName, int buildNumber) {
		if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("win"))
			return false;
		return buildNumber >= MINIMUM_BUILD;
	}

	public static int currentWindowsBuild() {
		String override = System.getProperty("copperbench.windows.build");
		if (override != null && !override.isBlank())
			return Integer.parseInt(override.trim());
		try {
			Process process = new ProcessBuilder("reg", "query",
					"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "/v", "CurrentBuild")
					.redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!process.waitFor(5, TimeUnit.SECONDS))
				process.destroyForcibly();
			Matcher matcher = Pattern.compile("CurrentBuild\\s+REG_SZ\\s+(\\d+)").matcher(output);
			if (matcher.find())
				return Integer.parseInt(matcher.group(1));
		} catch (Exception ignored) {
		}
		return 0;
	}

	public static boolean currentHostSupported() {
		String osName = System.getProperty("os.name");
		int build = currentWindowsBuild();
		if (build == 0)
			return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
		return isSupported(osName, build);
	}

	/** Runtime admission used by Stage 15 Linux candidates without changing the public support claim. */
	public static boolean currentHostRunnable() {
		if (currentHostSupported()) return true;
		return Boolean.parseBoolean(System.getProperty(STAGE15_LINUX_CANDIDATE_PROPERTY, "false"))
				&& RuntimePlatform.current().isLinuxX64();
	}

	public static void refuseIfUnsupported() {
		if (Boolean.parseBoolean(System.getProperty("copperbench.allowUnsupportedOs", "false")))
			return;
		if (currentHostRunnable())
			return;
		if (!GraphicsEnvironment.isHeadless())
			JOptionPane.showOptionDialog(null, UNSUPPORTED_MESSAGE, "Copperbench 系统要求",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new Object[]{"确定"}, "确定");
		throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
	}
}
