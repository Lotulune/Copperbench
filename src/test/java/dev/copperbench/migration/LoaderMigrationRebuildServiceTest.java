/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.migration;

import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderMigrationRebuildServiceTest {

	@TempDir Path temp;

	private final VersionTrackCatalog catalog = VersionTrackCatalog.official();
	private final LoaderMigrationService migrations = new LoaderMigrationService(catalog);
	private final LoaderMigrationRebuildService rebuilds = new LoaderMigrationRebuildService(catalog,
			Path.of(".").toAbsolutePath().normalize());

	@Test void generatesTheTargetCopyWithTheDestinationGeneratorAndLeavesSourceUnchanged() throws Exception {
		Path source = temp.resolve("source");
		Files.createDirectories(source.resolve("elements"));
		Files.writeString(source.resolve("workspace.mcreator"),
				"{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}", StandardCharsets.UTF_8);
		String before = WorkspaceTreeHasher.hash(source);
		WorkspaceState state = Fabric1211GoldenWorkspace.create();
		Path target = temp.resolve("copied-neoforge");
		MigrationReport report = migrations.execute(state, "neoforge-1.21.1", source, target);
		assertTrue(report.complete());
		var rebuild = rebuilds.rebuild(state, "neoforge-1.21.1", target);
		assertTrue(rebuild.generated());
		assertEquals("neoforge-1.21.1", rebuild.generatorId());
		assertTrue(Files.isRegularFile(target.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java")));
		assertTrue(Files.readString(target.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE5_NEOFORGE_READY"));
		assertTrue(Files.isRegularFile(target.resolve(".copperbench/generator-lock.json")));
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertTrue(Files.readString(source.resolve("workspace.mcreator")).contains("fabric-1.21.1"));
	}

	@Test void generatesA261CopyBetweenFirstPartyPreviewLoaders() throws Exception {
		var state = Fabric1211GoldenWorkspace.create261();
		Path target = temp.resolve("copied-neoforge-261");
		assertTrue(migrations.execute(state, "neoforge-26.1.2", null, target).complete());
		var rebuild = rebuilds.rebuild(state, "neoforge-26.1.2", target);
		assertTrue(rebuild.generated());
		assertEquals("neoforge-26.1.2", rebuild.generatorId());
		assertTrue(Files.readString(target.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE7_NEOFORGE261_READY"));
	}

	@Test void generatesA1201CopyBetweenFirstPartyMaintenanceLoaders() throws Exception {
		var state = Fabric1211GoldenWorkspace.create1201();
		Path target = temp.resolve("copied-neoforge-1201");
		assertTrue(migrations.execute(state, "neoforge-1.20.1", null, target).complete());
		var rebuild = rebuilds.rebuild(state, "neoforge-1.20.1", target);
		assertTrue(rebuild.generated());
		assertEquals("neoforge-1.20.1", rebuild.generatorId());
		assertTrue(Files.readString(target.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE7_NEOFORGE1201_READY"));
		assertTrue(Files.isRegularFile(target.resolve("src/main/resources/META-INF/mods.toml")));
	}

	@Test void generatesA262CopyBetweenFirstPartyLatestLoaders() throws Exception {
		var state = Fabric1211GoldenWorkspace.create262();
		Path target = temp.resolve("copied-neoforge-262");
		assertTrue(migrations.execute(state, "neoforge-26.2", null, target).complete());
		var rebuild = rebuilds.rebuild(state, "neoforge-26.2", target);
		assertTrue(rebuild.generated());
		assertEquals("neoforge-26.2", rebuild.generatorId());
		assertTrue(Files.readString(target.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE7_NEOFORGE262_READY"));
	}

	@Test void skipsRebuildWhenTheTargetTrackCannotBeGenerated() {
		var rebuild = rebuilds.rebuild(Fabric1211GoldenWorkspace.create(), "neoforge-26.3", temp.resolve("nope"));
		assertFalse(rebuild.generated());
		assertEquals("skipped", rebuild.status());
		assertEquals("VERSION_TRACK_NOT_REBUILDABLE", rebuild.reasonCode());
	}

	@Test @EnabledIfSystemProperty(named = "copperbench.stage7.migrationBuild", matches = "true")
	void rebuiltNeoForgeCopyBuildsAJar() throws Exception {
		WorkspaceState state = Fabric1211GoldenWorkspace.create();
		Path target = temp.resolve("copied-neoforge-build");
		assertTrue(migrations.execute(state, "neoforge-1.21.1", null, target).complete());
		assertTrue(rebuilds.rebuild(state, "neoforge-1.21.1", target).generated());

		ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "--no-daemon", "build",
				"--stacktrace").directory(target.toFile()).redirectErrorStream(true);
		Path jdk21 = Path.of(".").toAbsolutePath().normalize().resolve("jdk/jdk21_win_64");
		if (Files.isDirectory(jdk21))
			builder.environment().put("JAVA_HOME", jdk21.toString());
		Process process = builder.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
		boolean completed = process.waitFor(Duration.ofMinutes(15).toMillis(), TimeUnit.MILLISECONDS);
		if (!completed) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
		String log = output.get(30, TimeUnit.SECONDS);
		assertTrue(completed, "Migrated NeoForge build timed out:\n" + log);
		assertEquals(0, process.exitValue(), log);
		assertTrue(Files.isRegularFile(target.resolve("build/libs/copper_trails-1.0.0.jar")), log);
	}
}
