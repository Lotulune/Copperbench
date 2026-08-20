/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.history;

import dev.copperbench.core.contract.UiCore.Actor;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHistoryServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);

	@TempDir Path workspace;

	@Test void creatorCanCompareAndRestoreRecoveryPointsWithoutChangingExistingGitMetadata() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"revision\":0}");
		Files.createDirectories(workspace.resolve(".mcreator/localHistory"));
		Files.writeString(workspace.resolve(".mcreator/localHistory/HEAD"), "upstream-history-marker");

		try (Git userGit = Git.init().setDirectory(workspace.toFile()).setInitialBranch("creator-work").call()) {
			userGit.getRepository().getConfig().setString("remote", "origin", "url", "https://example.invalid/user.git");
			userGit.getRepository().getConfig().save();
		}

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			RecoveryPoint before = history.createRecoveryPoint(
					new RecoveryPointRequest("Before AI edit", Actor.MCP, "task-42"));

			Files.writeString(workspace.resolve("workspace.mcreator"), "{\"revision\":1}");
			Files.createDirectories(workspace.resolve("elements"));
			Files.writeString(workspace.resolve("elements/copper_block.mod.json"), "{\"name\":\"Copper Block\"}");
			RecoveryPoint after = history.createRecoveryPoint(
					new RecoveryPointRequest("After AI edit", Actor.MCP, "task-42"));

			assertEquals(List.of(
					new WorkspaceChange(ChangeType.ADD, "elements/copper_block.mod.json"),
					new WorkspaceChange(ChangeType.MODIFY, "workspace.mcreator")
			), history.compare(before.id(), after.id()));

			RestoreResult restored = history.restore(before.id());
			assertTrue(restored.changedPaths().contains("workspace.mcreator"));
			assertTrue(restored.changedPaths().contains("elements/copper_block.mod.json"));
			assertEquals("{\"revision\":0}", Files.readString(workspace.resolve("workspace.mcreator")));
			assertFalse(Files.exists(workspace.resolve("elements/copper_block.mod.json")));
			assertEquals(List.of(after, before), history.listRecoveryPoints());
		}

		try (Git userGit = Git.open(workspace.toFile())) {
			assertEquals("refs/heads/creator-work", userGit.getRepository().getFullBranch());
			assertEquals("https://example.invalid/user.git",
					userGit.getRepository().getConfig().getString("remote", "origin", "url"));
		}
		assertEquals("upstream-history-marker",
				Files.readString(workspace.resolve(".mcreator/localHistory/HEAD")));
	}

	@Test void failedAutomatedOperationRestoresTheWorkspaceBeforeReturningTheFailure() throws Exception {
		Path workspaceFile = workspace.resolve("workspace.mcreator");
		Files.writeString(workspaceFile, "{\"revision\":7}");

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			RecoveryPointCoordinator coordinator = new RecoveryPointCoordinator(history);
			RecoverableOperationException failure = assertThrows(RecoverableOperationException.class,
					() -> coordinator.execute(new RecoveryPointRequest("Before rejected mutation", Actor.MCP, "task-77"),
							() -> {
								Files.writeString(workspaceFile, "{\"revision\":8,\"partial\":true}");
								throw new IllegalStateException("simulated persistence failure");
							}));

			assertEquals("{\"revision\":7}", Files.readString(workspaceFile));
			assertEquals("simulated persistence failure", failure.getCause().getMessage());
			assertFalse(failure.recoveryPointId().isBlank());
		}
	}
}
