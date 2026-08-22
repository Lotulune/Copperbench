/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleDistributionPoolTest {

	@Test void hashesTheOfficialGradle97UrlTheSameWayAsTheWrapper() {
		assertEquals("d4tj7w02tcgubx9zk9hbippn6", GradleDistributionPool.hashDistributionUrl(
				"https://services.gradle.org/distributions/gradle-9.7.0-bin.zip"));
		assertEquals("4ticwg1pgcbps2hj28r8so764", GradleDistributionPool.hashDistributionUrl(
				"https://services.gradle.org/distributions/gradle-9.6.1-bin.zip"));
		assertEquals("6lo5v7m69k2fyj2mb4v455sou", GradleDistributionPool.hashDistributionUrl(
				"https://mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
	}

	@Test void unescapesWrapperPropertyUrls() {
		assertEquals("https://services.gradle.org/distributions/gradle-9.7.0-bin.zip",
				GradleDistributionPool.unescapeDistributionUrl(
						"https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip"));
	}

	@Test void copiesAReadyInstallIntoTheUrlHashFolder(@TempDir Path temp) throws Exception {
		Path gradleHome = temp.resolve("gradle-home");
		Path ready = gradleHome.resolve("wrapper/dists/gradle-9.7.0-bin/already-there");
		Path bat = ready.resolve("gradle-9.7.0/bin/gradle.bat");
		Files.createDirectories(bat.getParent());
		Files.writeString(bat, "@echo off\n", StandardCharsets.UTF_8);
		Files.createFile(ready.resolve("gradle-9.7.0-bin.zip.ok"));

		Path workspace = temp.resolve("workspace");
		Path wrapper = workspace.resolve("gradle/wrapper/gradle-wrapper.properties");
		Files.createDirectories(wrapper.getParent());
		Files.writeString(wrapper, """
				distributionBase=GRADLE_USER_HOME
				distributionUrl=https\\://mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip
				""", StandardCharsets.UTF_8);

		assertTrue(GradleDistributionPool.seedForWorkspace(workspace, gradleHome));
		String hash = GradleDistributionPool.hashDistributionUrl(
				"https://mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip");
		Path seeded = gradleHome.resolve("wrapper/dists/gradle-9.7.0-bin").resolve(hash);
		assertTrue(GradleDistributionPool.isReadyInstall(seeded, "9.7.0"));
		assertTrue(Files.isRegularFile(seeded.resolve("gradle-9.7.0-bin.zip.ok")));
		assertFalse(GradleDistributionPool.seedForWorkspace(workspace, gradleHome));
	}

	@Test void doesNothingWhenNoInstallExists(@TempDir Path temp) throws Exception {
		Path workspace = temp.resolve("workspace");
		Path wrapper = workspace.resolve("gradle/wrapper/gradle-wrapper.properties");
		Files.createDirectories(wrapper.getParent());
		Files.writeString(wrapper, "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip\n",
				StandardCharsets.UTF_8);
		assertFalse(GradleDistributionPool.seedForWorkspace(workspace, temp.resolve("empty-home")));
		assertTrue(GradleDistributionPool.findReadyInstall("9.7.0", temp.resolve("empty-home"), List.of()).isEmpty());
	}

	@Test void seedsOfficialAndChinaHashesFromPackagedGradleDists(@TempDir Path temp) throws Exception {
		Path packaged = temp.resolve("gradle-dists/gradle-9.7.0-bin/bundled");
		Path bat = packaged.resolve("gradle-9.7.0/bin/gradle.bat");
		Files.createDirectories(bat.getParent());
		Files.writeString(bat, "@echo off\n", StandardCharsets.UTF_8);
		Path gradleHome = temp.resolve("gradle-home");
		assertEquals(2, GradleDistributionPool.seedPackagedDistributions(gradleHome, List.of(temp.resolve("gradle-dists")),
				true));
		assertTrue(GradleDistributionPool.isReadyInstall(gradleHome.resolve("wrapper/dists/gradle-9.7.0-bin")
				.resolve(GradleDistributionPool.hashDistributionUrl(GradleDistributionPool.officialDistributionUrl("9.7.0"))),
				"9.7.0"));
		assertTrue(GradleDistributionPool.isReadyInstall(gradleHome.resolve("wrapper/dists/gradle-9.7.0-bin")
				.resolve(GradleDistributionPool.hashDistributionUrl(GradleDistributionPool.chinaDistributionUrl("9.7.0"))),
				"9.7.0"));
		assertEquals(0, GradleDistributionPool.seedPackagedDistributions(gradleHome, List.of(temp.resolve("gradle-dists")),
				true));
	}

}
