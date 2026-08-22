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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleDownloadProgressTest {

	@Test void extractsFileNameFromMavenUrl() {
		assertEquals("yarn-26.1.2+build.1.jar", GradleDownloadProgress.fileNameFromUrl(
				"https://maven.fabricmc.net/net/fabricmc/yarn/26.1.2+build.1/yarn-26.1.2+build.1.jar"));
	}

	@Test void formatsMegabytesAndPercent() {
		assertEquals("1.5 MB", GradleDownloadProgress.formatBytes(1572864));
		assertEquals(50, GradleDownloadProgress.percent(50, 100));
		assertEquals(0, GradleDownloadProgress.percent(10, -1));
	}

	@Test void trackerPrefersTheLargestActiveDownload() {
		var tracker = new GradleDownloadProgress.Tracker();
		tracker.start("small.jar", 1000);
		tracker.progress("small.jar", 100, 1000);
		tracker.progress("minecraft-client.jar", 20_000_000, 40_000_000);
		GradleDownloadProgress.Snapshot snapshot = tracker.snapshot();
		assertEquals("minecraft-client.jar", snapshot.fileName());
		assertEquals(50, snapshot.percent());
		assertTrue(snapshot.label().contains("minecraft-client.jar"));
		assertTrue(snapshot.label().contains("+1"));
	}

	@Test void findsTheLargestPartialArchive(@TempDir Path gradleHome) throws Exception {
		Path dists = gradleHome.resolve("wrapper/dists/gradle-9.7.0-bin/hash");
		Files.createDirectories(dists);
		Files.write(dists.resolve("gradle-9.7.0-bin.zip.part"), new byte[4096]);
		Files.write(dists.resolve("tiny.part"), new byte[8]);
		Path found = GradleDownloadProgress.largestPartialArchive(gradleHome.resolve("wrapper/dists"));
		assertEquals("gradle-9.7.0-bin.zip.part", found.getFileName().toString());
	}

}
