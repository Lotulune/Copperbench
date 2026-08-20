/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core;

import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.migration.LoaderMigrationRebuildService;
import dev.copperbench.migration.LoaderMigrationService;
import dev.copperbench.migration.UpstreamWorkspaceImportService;
import dev.copperbench.migration.WorkspaceTreeHasher;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage7G6GateTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC);

	@TempDir Path temp;

	@Test void historyImportAndLoaderCopyKeepSourceWorkspacesUnchanged() throws Exception {
		Path gitWorkspace = temp.resolve("existing-git");
		Files.createDirectories(gitWorkspace);
		Files.writeString(gitWorkspace.resolve("workspace.mcreator"), "{\"revision\":0}", StandardCharsets.UTF_8);
		try (Git git = Git.init().setDirectory(gitWorkspace.toFile()).setInitialBranch("creator-work").call()) {
			git.getRepository().getConfig().setString("remote", "origin", "url", "https://example.invalid/mod.git");
			git.getRepository().getConfig().save();
		}
		try (LocalHistoryService history = JGitLocalHistoryService.open(gitWorkspace, CLOCK)) {
			var before = history.createRecoveryPoint(new RecoveryPointRequest("clean", Actor.HEADLESS, "g6"));
			Files.writeString(gitWorkspace.resolve("workspace.mcreator"), "{\"revision\":1,\"dirty\":true}");
			history.createRecoveryPoint(new RecoveryPointRequest("dirty", Actor.HEADLESS, "g6"));
			history.restore(before.id());
			assertEquals("{\"revision\":0}", Files.readString(gitWorkspace.resolve("workspace.mcreator")));
		}
		try (Git git = Git.open(gitWorkspace.toFile())) {
			assertEquals("refs/heads/creator-work", git.getRepository().getFullBranch());
			assertEquals("https://example.invalid/mod.git",
					git.getRepository().getConfig().getString("remote", "origin", "url"));
		}

		Path upstream = temp.resolve("upstream");
		Files.createDirectories(upstream.resolve("elements"));
		Files.writeString(upstream.resolve("workspace.mcreator"),
				"{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"},\"plugin.keep\":true}",
				StandardCharsets.UTF_8);
		String upstreamHash = WorkspaceTreeHasher.hash(upstream);
		var imported = new UpstreamWorkspaceImportService().execute(upstream, temp.resolve("imported"));
		assertTrue(imported.sourceUnchanged());
		assertEquals(upstreamHash, WorkspaceTreeHasher.hash(upstream));
		assertTrue(Files.isRegularFile(temp.resolve("imported/.copperbench/import/report.json")));

		Path source = temp.resolve("fabric-source");
		Files.createDirectories(source);
		Files.writeString(source.resolve("workspace.mcreator"),
				"{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}");
		String sourceHash = WorkspaceTreeHasher.hash(source);
		var catalog = VersionTrackCatalog.official();
		var migrations = new LoaderMigrationService(catalog);
		var report = migrations.execute(Fabric1211GoldenWorkspace.create(), "neoforge-1.21.1", source,
				temp.resolve("neoforge-copy"));
		assertTrue(report.complete());
		assertTrue(report.sourceUnchanged());
		var rebuild = new LoaderMigrationRebuildService(catalog, Path.of(".").toAbsolutePath().normalize())
				.rebuild(Fabric1211GoldenWorkspace.create(), "neoforge-1.21.1", temp.resolve("neoforge-copy"));
		assertTrue(rebuild.generated());
		assertEquals(sourceHash, WorkspaceTreeHasher.hash(source));
	}
}
