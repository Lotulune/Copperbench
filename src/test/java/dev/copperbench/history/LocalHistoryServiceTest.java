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
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHistoryServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);

	@TempDir Path workspace;

	@Test void rewritingTrackedWorkspaceFileInvalidatesCurrentRecoveryPoint() throws Exception {
		Path sentinel = workspace.resolve("stage13-history-sentinel.txt");
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"revision\":0}");
		Files.writeString(sentinel, "stage13-history-baseline");

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			RecoveryPoint point = history.createRecoveryPoint(
					new RecoveryPointRequest("Stage13 baseline", Actor.UI, ""));
			assertEquals(point.id(), history.currentRecoveryPointId());

			Files.writeString(sentinel, "stage13-history-mutated");
			assertNull(history.currentRecoveryPointId());
		}
	}

	@Test void previewAndRestoreStillWorkAfterReopeningHistoryAroundAnExternalTrackedFileEdit() throws Exception {
		Path workspaceFile = workspace.resolve("workspace.mcreator");
		Path trackedFile = workspace.resolve("gradle/wrapper/gradle-wrapper.properties");
		Files.createDirectories(trackedFile.getParent());
		Files.writeString(workspaceFile, "{\"revision\":0}");
		Files.writeString(trackedFile, "distributionUrl=https://example.invalid/gradle.zip\n");

		RecoveryPoint baseline;
		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			baseline = history.createRecoveryPoint(new RecoveryPointRequest("Stage13 baseline", Actor.UI, ""));
			assertEquals(baseline.id(), history.currentRecoveryPointId());
		}

		Files.writeString(trackedFile,
				"distributionUrl=https://example.invalid/gradle.zip\n# stage13-history-mutated");

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			assertNull(history.currentRecoveryPointId());
			assertEquals(List.of(new WorkspaceChange(ChangeType.MODIFY,
					"gradle/wrapper/gradle-wrapper.properties")), history.previewRestore(baseline.id()));

			RestoreResult restored = history.restore(baseline.id());
			assertTrue(restored.changedPaths().contains("gradle/wrapper/gradle-wrapper.properties"));
			assertEquals("distributionUrl=https://example.invalid/gradle.zip\n", Files.readString(trackedFile));
			assertEquals(baseline.id(), history.currentRecoveryPointId());
		}
	}

	@Test void currentRecoveryPointRehashesTrackedContentWhenSizeAndTimestampMatchTheIndex() throws Exception {
		Path trackedFile = workspace.resolve("gradle/wrapper/gradle-wrapper.properties");
		Files.createDirectories(trackedFile.getParent());
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"revision\":0}");
		Files.writeString(trackedFile, "distributionUrl=https://example.invalid/a.zip\n");

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			RecoveryPoint baseline = history.createRecoveryPoint(
					new RecoveryPointRequest("Before external rewrite", Actor.UI, ""));
			assertEquals(baseline.id(), history.currentRecoveryPointId());

			FileTime indexedTimestamp = Files.getLastModifiedTime(trackedFile);
			String original = Files.readString(trackedFile);
			String rewritten = original.replace("a.zip", "b.zip");
			assertEquals(original.length(), rewritten.length());
			Files.writeString(trackedFile, rewritten);
			Files.setLastModifiedTime(trackedFile, indexedTimestamp);

			assertNull(history.currentRecoveryPointId(),
					"content changes must invalidate the current recovery point even when stat metadata is unchanged");
		}
	}

	@Test void creatorCanCompareAndRestoreRecoveryPointsWithoutChangingExistingGitMetadata() throws Exception {
		Files.writeString(workspace.resolve("workspace.mcreator"), "{\"revision\":0}");
		Files.createDirectories(workspace.resolve(".mcreator/localHistory"));
		Files.writeString(workspace.resolve(".mcreator/localHistory/HEAD"), "upstream-history-marker");

		try (Git userGit = Git.init().setDirectory(workspace.toFile()).setInitialBranch("creator-work").call()) {
			userGit.getRepository().getConfig().setString("remote", "origin", "url", "https://example.invalid/user.git");
			userGit.getRepository().getConfig().save();
		}

		try (LocalHistoryService history = JGitLocalHistoryService.open(workspace, CLOCK)) {
			Files.createDirectories(workspace.resolve("elements"));
			Files.writeString(workspace.resolve("elements/copper_block.mod.json"),
					"{\"settings\":{\"hardness\":1,\"enabled\":true}}");
			RecoveryPoint before = history.createRecoveryPoint(
					new RecoveryPointRequest("Before AI edit", Actor.MCP, "task-42"));
			assertEquals(before.id(), history.currentRecoveryPointId());

			Files.writeString(workspace.resolve("workspace.mcreator"), "{\"revision\":1}");
			Files.writeString(workspace.resolve("elements/copper_block.mod.json"),
					"{\"settings\":{\"hardness\":2,\"luminance\":7}}");
			assertNull(history.currentRecoveryPointId());
			RecoveryPoint after = history.createRecoveryPoint(
					new RecoveryPointRequest("After AI edit", Actor.MCP, "task-42", RecoveryPointSource.WORKSPACE_PLAN));
			assertEquals(RecoveryPointSource.WORKSPACE_PLAN, after.source());
			assertEquals(after.id(), history.currentRecoveryPointId());

			List<WorkspaceChange> compared = history.compare(before.id(), after.id());
			assertEquals(2, compared.size());
			WorkspaceChange elementChange = compared.get(0);
			assertEquals(ChangeType.MODIFY, elementChange.type());
			assertEquals("elements/copper_block.mod.json", elementChange.path());
			assertEquals(List.of(
					new HistoryFieldChange(ChangeType.DELETE, "/settings/enabled"),
					new HistoryFieldChange(ChangeType.MODIFY, "/settings/hardness"),
					new HistoryFieldChange(ChangeType.ADD, "/settings/luminance")
			), elementChange.fieldChanges());
			assertEquals(new WorkspaceChange(ChangeType.MODIFY, "workspace.mcreator"), compared.get(1));

			Files.writeString(workspace.resolve("workspace.mcreator"), "{\"revision\":2,\"unsaved\":true}");
			Files.writeString(workspace.resolve("scratch.txt"), "current working tree only");
			assertNull(history.currentRecoveryPointId());
			List<WorkspaceChange> restorePreview = history.previewRestore(before.id());
			assertTrue(Files.exists(workspace.resolve(".mcreator/localHistory/HEAD")));
			assertEquals(3, restorePreview.size());
			assertEquals(List.of(
					new HistoryFieldChange(ChangeType.ADD, "/settings/enabled"),
					new HistoryFieldChange(ChangeType.MODIFY, "/settings/hardness"),
					new HistoryFieldChange(ChangeType.DELETE, "/settings/luminance")
			), restorePreview.get(0).fieldChanges());
			assertEquals(new WorkspaceChange(ChangeType.DELETE, "scratch.txt"), restorePreview.get(1));
			assertEquals(new WorkspaceChange(ChangeType.MODIFY, "workspace.mcreator"), restorePreview.get(2));

			RestoreResult restored = history.restore(before.id());
			assertTrue(Files.exists(workspace.resolve(".mcreator/localHistory/HEAD")));
			assertTrue(restored.changedPaths().contains("workspace.mcreator"));
			assertTrue(restored.changedPaths().contains("elements/copper_block.mod.json"));
			assertTrue(restored.changedPaths().contains("scratch.txt"));
			assertEquals("{\"revision\":0}", Files.readString(workspace.resolve("workspace.mcreator")));
			assertEquals("{\"settings\":{\"hardness\":1,\"enabled\":true}}",
					Files.readString(workspace.resolve("elements/copper_block.mod.json")));
			assertFalse(Files.exists(workspace.resolve("scratch.txt")));
			assertEquals(before.id(), history.currentRecoveryPointId());
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
