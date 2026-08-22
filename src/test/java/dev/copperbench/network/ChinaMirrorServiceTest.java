/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChinaMirrorServiceTest {

	@TempDir Path temp;

	@Test void rewritesEscapedOfficialGradleDistributionUrl() {
		String original = """
				distributionBase=GRADLE_USER_HOME
				distributionPath=wrapper/dists
				distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip
				networkTimeout=10000
				validateDistributionUrl=true
				zipStoreBase=GRADLE_USER_HOME
				zipStorePath=wrapper/dists
				""";
		String rewritten = ChinaMirrorService.rewriteWrapperProperties(original, true);
		assertTrue(rewritten.contains("https\\://mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
		assertFalse(rewritten.contains("services.gradle.org"));
		assertTrue(rewritten.contains("networkTimeout=" + ChinaMirrorService.MIN_NETWORK_TIMEOUT_MS));
	}

	@Test void rewritesUnescapedOfficialGradleDistributionUrlAndAppendsTimeout() {
		String original = "distributionUrl=https://services.gradle.org/distributions/gradle-9.7.0-bin.zip\n";
		String rewritten = ChinaMirrorService.rewriteWrapperProperties(original, true);
		assertTrue(rewritten.contains("https://mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
		assertTrue(rewritten.contains("networkTimeout=" + ChinaMirrorService.MIN_NETWORK_TIMEOUT_MS));
	}

	@Test void leavesOfficialUrlUnchangedWhenMirrorsAreDisabled() {
		String original = "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip\n";
		assertEquals(original, ChinaMirrorService.rewriteWrapperProperties(original, false));
	}

	@Test void doesNotLowerAnAlreadyHighNetworkTimeout() {
		String original = """
				distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
				networkTimeout=120000
				""";
		String rewritten = ChinaMirrorService.rewriteWrapperProperties(original, true);
		assertTrue(rewritten.contains("networkTimeout=120000"));
		assertFalse(rewritten.contains("networkTimeout=60000"));
	}

	@Test void writesAndRemovesTheInitScriptInGradleUserHome() throws Exception {
		Path gradleHome = temp.resolve("gradle-home");
		ChinaMirrorService.applyUserHome(gradleHome, true);
		Path initScript = gradleHome.resolve("init.d").resolve(ChinaMirrorService.INIT_SCRIPT_NAME);
		assertTrue(Files.isRegularFile(initScript));
		String script = Files.readString(initScript, StandardCharsets.UTF_8);
		assertTrue(script.contains("https://maven.aliyun.com/repository/central"));
		assertTrue(script.contains("https://maven.aliyun.com/repository/gradle-plugin"));
		assertTrue(script.contains("https://bmclapi2.bangbang93.com/maven"));
		assertTrue(script.contains("libraries.minecraft.net"));
		assertFalse(script.contains("maven.fabricmc.net"));
		assertFalse(script.contains("maven.neoforged.net"));
		assertEquals(ChinaMirrorService.loadInitScript(), script);

		ChinaMirrorService.applyUserHome(gradleHome, false);
		assertFalse(Files.exists(initScript));
	}

	@Test void rewritesWorkspaceWrapperOnlyWhenEnabled() throws Exception {
		Path workspace = temp.resolve("workspace");
		Path wrapper = workspace.resolve("gradle/wrapper/gradle-wrapper.properties");
		Files.createDirectories(wrapper.getParent());
		Files.writeString(wrapper, """
				distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip
				networkTimeout=10000
				""", StandardCharsets.UTF_8);

		assertFalse(ChinaMirrorService.applyToWorkspace(workspace, false));
		assertTrue(Files.readString(wrapper).contains("services.gradle.org"));

		assertTrue(ChinaMirrorService.applyToWorkspace(workspace, true));
		String rewritten = Files.readString(wrapper, StandardCharsets.UTF_8);
		assertTrue(rewritten.contains("mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
		assertFalse(ChinaMirrorService.applyToWorkspace(workspace, true));
	}

}
