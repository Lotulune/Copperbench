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
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public final class JGitLocalHistoryService implements LocalHistoryService {

	private static final String HISTORY_DIRECTORY = ".copperbench/local-history.git";
	private static final String LABEL_HEADER = "Copperbench-Label: ";
	private static final String ACTOR_HEADER = "Copperbench-Actor: ";
	private static final String TASK_HEADER = "Copperbench-Task-Id: ";

	private final Clock clock;
	private final Git git;

	private JGitLocalHistoryService(Clock clock, Git git) {
		this.clock = clock;
		this.git = git;
	}

	public static JGitLocalHistoryService open(Path workspaceRoot, Clock clock) throws LocalHistoryException {
		Path workspace = workspaceRoot.toAbsolutePath().normalize();
		Path historyDirectory = workspace.resolve(HISTORY_DIRECTORY);
		try {
			Files.createDirectories(historyDirectory.getParent());
			boolean create = !Files.isRegularFile(historyDirectory.resolve("HEAD"));
			Repository repository = new FileRepositoryBuilder().setGitDir(historyDirectory.toFile())
					.setWorkTree(workspace.toFile()).build();
			if (create)
				repository.create(false);
			configure(repository, workspace);
			return new JGitLocalHistoryService(clock, new Git(repository));
		} catch (IOException exception) {
			throw new LocalHistoryException("Could not open local history for " + workspace, exception);
		}
	}

	@Override public synchronized RecoveryPoint createRecoveryPoint(RecoveryPointRequest request)
			throws LocalHistoryException {
		try {
			git.add().addFilepattern(".").call();
			git.add().setUpdate(true).addFilepattern(".").call();
			PersonIdent identity = new PersonIdent("Copperbench", "local-history@copperbench.invalid",
					Date.from(clock.instant()), TimeZone.getTimeZone(ZoneOffset.UTC));
			String message = "Copperbench recovery point\n\n" + LABEL_HEADER + request.label() + "\n"
					+ ACTOR_HEADER + request.actor().name() + "\n" + TASK_HEADER + request.taskId();
			RevCommit commit = git.commit().setMessage(message).setAuthor(identity).setCommitter(identity)
					.setSign(false).setAllowEmpty(true).call();
			return toRecoveryPoint(commit);
		} catch (Exception exception) {
			throw new LocalHistoryException("Could not create recovery point", exception);
		}
	}

	@Override public synchronized List<RecoveryPoint> listRecoveryPoints() throws LocalHistoryException {
		try {
			if (git.getRepository().resolve(Constants.HEAD) == null)
				return List.of();
			List<RecoveryPoint> points = new ArrayList<>();
			for (RevCommit commit : git.log().call())
				points.add(toRecoveryPoint(commit));
			return List.copyOf(points);
		} catch (Exception exception) {
			throw new LocalHistoryException("Could not list recovery points", exception);
		}
	}

	@Override public synchronized List<WorkspaceChange> compare(String fromRecoveryPointId,
			String toRecoveryPointId) throws LocalHistoryException {
		try (RevWalk walk = new RevWalk(git.getRepository());
				DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
			RevCommit from = resolveCommit(walk, fromRecoveryPointId);
			RevCommit to = resolveCommit(walk, toRecoveryPointId);
			formatter.setRepository(git.getRepository());
			formatter.setDetectRenames(true);
			List<WorkspaceChange> changes = formatter.scan(from.getTree(), to.getTree()).stream()
					.map(JGitLocalHistoryService::toWorkspaceChange)
					.sorted(Comparator.comparing(WorkspaceChange::path))
					.toList();
			return List.copyOf(changes);
		} catch (LocalHistoryException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new LocalHistoryException("Could not compare recovery points", exception);
		}
	}

	@Override public synchronized RestoreResult restore(String recoveryPointId) throws LocalHistoryException {
		Set<String> changedPaths = new LinkedHashSet<>();
		try (RevWalk walk = new RevWalk(git.getRepository())) {
			RevCommit target = resolveCommit(walk, recoveryPointId);
			DirCache cache = git.getRepository().lockDirCache();
			try {
				DirCacheCheckout checkout = new DirCacheCheckout(git.getRepository(), cache, target.getTree());
				checkout.setFailOnConflict(false);
				checkout.checkout();
				changedPaths.addAll(checkout.getUpdated().keySet());
				changedPaths.addAll(checkout.getRemoved());
				changedPaths.addAll(checkout.getToBeDeleted());
			} finally {
				cache.unlock();
			}
			changedPaths.addAll(git.clean().setCleanDirectories(true).call());
			return new RestoreResult(recoveryPointId, changedPaths);
		} catch (LocalHistoryException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new LocalHistoryException("Could not restore recovery point " + recoveryPointId, exception);
		}
	}

	@Override public void close() {
		git.close();
	}

	private static void configure(Repository repository, Path workspace) throws IOException {
		StoredConfig config = repository.getConfig();
		config.setBoolean("core", null, "bare", false);
		config.setString("core", null, "worktree", workspace.toString());
		config.setBoolean("core", null, "autocrlf", false);
		config.setBoolean("core", null, "filemode", false);
		config.setBoolean("commit", null, "gpgsign", false);
		config.save();

		Path excludes = repository.getDirectory().toPath().resolve("info/exclude");
		Files.createDirectories(excludes.getParent());
		Files.writeString(excludes, String.join("\n", List.of(
				"/.git/", "/.copperbench/", "/.mcreator/localHistory/", "/.gradle/", "/build/", "/run/",
				"/runs/", "/.idea/", "/.eclipse/")) + "\n", StandardCharsets.UTF_8);
	}

	private static RevCommit resolveCommit(RevWalk walk, String recoveryPointId)
			throws IOException, LocalHistoryException {
		var objectId = walk.getObjectReader().resolve(org.eclipse.jgit.lib.AbbreviatedObjectId
				.fromString(recoveryPointId));
		if (objectId == null || objectId.size() != 1)
			throw new LocalHistoryException("Unknown recovery point: " + recoveryPointId);
		return walk.parseCommit(objectId.iterator().next());
	}

	private static RecoveryPoint toRecoveryPoint(RevCommit commit) {
		String message = commit.getFullMessage();
		return new RecoveryPoint(commit.getName(), header(message, LABEL_HEADER),
				Actor.valueOf(header(message, ACTOR_HEADER)), header(message, TASK_HEADER),
				commit.getAuthorIdent().getWhenAsInstant());
	}

	private static String header(String message, String prefix) {
		return message.lines().filter(line -> line.startsWith(prefix)).map(line -> line.substring(prefix.length()))
				.findFirst().orElse("");
	}

	private static WorkspaceChange toWorkspaceChange(DiffEntry entry) {
		return switch (entry.getChangeType()) {
			case ADD -> new WorkspaceChange(ChangeType.ADD, entry.getNewPath());
			case MODIFY -> new WorkspaceChange(ChangeType.MODIFY, entry.getNewPath());
			case DELETE -> new WorkspaceChange(ChangeType.DELETE, entry.getOldPath());
			case RENAME -> new WorkspaceChange(ChangeType.RENAME, entry.getNewPath());
			case COPY -> new WorkspaceChange(ChangeType.COPY, entry.getNewPath());
		};
	}
}
