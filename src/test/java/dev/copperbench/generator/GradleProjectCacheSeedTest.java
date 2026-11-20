/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleProjectCacheSeedTest {
	@TempDir Path root;

	@Test void copiesTheLoomProjectCacheWithoutLocksOrOtherProjectState() throws Exception {
		Path workspace = root.resolve("workspace"), task = root.resolve("task");
		write(workspace.resolve(".gradle/loom-cache/remapped_mods/net/fabricmc/api.jar"), "remapped");
		write(workspace.resolve(".gradle/loom-cache/minecraftMaven/minecraft-merged.jar"), "minecraft");
		write(workspace.resolve(".gradle/loom-cache/remapped_mods/remapped.lock"), "held by the workspace build");
		write(workspace.resolve(".gradle/9.7.0/fileHashes/fileHashes.bin"), "project state");
		Files.createDirectories(task);

		var result = GradleProjectCacheSeed.seed(workspace, task, () -> false);

		assertTrue(result.seeded(), result.reason());
		assertEquals(2, result.files());
		assertEquals("remapped", Files.readString(task.resolve(".gradle/loom-cache/remapped_mods/net/fabricmc/api.jar")));
		assertEquals("minecraft", Files.readString(task.resolve(".gradle/loom-cache/minecraftMaven/minecraft-merged.jar")));
		assertFalse(Files.exists(task.resolve(".gradle/loom-cache/remapped_mods/remapped.lock")));
		assertFalse(Files.exists(task.resolve(".gradle/9.7.0")), "only Loom's dependency cache is shared");
		try (var staging = Files.list(task.resolve(".gradle"))) {
			assertEquals(List.of("loom-cache"), staging.map(path -> path.getFileName().toString()).toList());
		}
	}

	@Test void neverReplacesAnExistingCacheAndReportsMissingSources() throws Exception {
		Path workspace = root.resolve("workspace"), task = root.resolve("task");
		Files.createDirectories(workspace);
		assertFalse(GradleProjectCacheSeed.seed(workspace, task, () -> false).seeded());

		write(workspace.resolve(".gradle/loom-cache/a.jar"), "new");
		write(task.resolve(".gradle/loom-cache/a.jar"), "existing");
		var result = GradleProjectCacheSeed.seed(workspace, task, () -> false);
		assertFalse(result.seeded());
		assertEquals("existing", Files.readString(task.resolve(".gradle/loom-cache/a.jar")));
	}

	@Test void cancellationLeavesNoPartialCache() throws Exception {
		Path workspace = root.resolve("workspace"), task = root.resolve("task");
		for (int index = 0; index < 5; index++) write(workspace.resolve(".gradle/loom-cache/mod" + index + ".jar"), "jar");
		int[] checks = {0};
		var result = GradleProjectCacheSeed.seed(workspace, task, () -> ++checks[0] > 3);
		assertFalse(result.seeded());
		assertTrue(result.reason().contains("cancelled"), result.reason());
		try (var staging = Files.list(task.resolve(".gradle"))) {
			assertEquals(0, staging.count(), "a partial copy must not stay behind for Loom to read");
		}
	}

	@Test void onlyRealSeedingOrFailuresAreLogged() throws Exception {
		Path workspace = root.resolve("workspace"), task = root.resolve("task");
		Files.createDirectories(workspace);
		List<String> log = new ArrayList<>();
		GradleProjectCacheSeed.seedAndLog(workspace, task, "workspace", () -> false, log::add);
		assertEquals(List.of(), log, "a loader without Loom has nothing to seed");
		write(workspace.resolve(".gradle/loom-cache/a.jar"), "jar");
		GradleProjectCacheSeed.seedAndLog(workspace, task, "workspace", () -> false, log::add);
		assertEquals(1, log.size());
		assertTrue(log.getFirst().startsWith("GRADLE_PROJECT_CACHE_SEEDED: copied 1 Loom cache files"), log.toString());
	}

	private static void write(Path path, String content) throws Exception {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}
}
