package dev.copperbench.assets;

import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RestoreResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Keeps asset import/replace operations inside the local-history audit boundary. */
public final class AssetMutationHistoryBoundary {
	private final AssetWorkspaceService assets;
	private final LocalHistoryService history;

	public AssetMutationHistoryBoundary(AssetWorkspaceService assets, LocalHistoryService history) {
		this.assets = Objects.requireNonNull(assets, "assets");
		this.history = Objects.requireNonNull(history, "history");
	}

	public RecoveryPoint importOrReplace(Path source, String targetRelativePath, Actor actor, String taskId)
			throws LocalHistoryException {
		Objects.requireNonNull(source, "source");
		Path target = target(targetRelativePath);
		try {
			Path input = source.toRealPath();
			if (!Files.isRegularFile(input)) throw new IOException("source is not a file");
			Files.createDirectories(target.getParent());
			Path temporary = Files.createTempFile(target.getParent(), ".copperbench-asset-", ".tmp");
			try {
				Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
				try {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (IOException exception) {
			throw new AssetPathViolationException("Asset import failed: " + exception.getMessage());
		}
		return history.createRecoveryPoint(new RecoveryPointRequest("Asset import or replacement: " + targetRelativePath,
				actor, taskId));
	}

	public RestoreResult restore(String recoveryPointId) throws LocalHistoryException {
		return history.restore(recoveryPointId);
	}

	private Path target(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) throw new AssetPathViolationException("Target path is required");
		Path requested;
		try { requested = Path.of(relativePath); } catch (RuntimeException exception) { throw new AssetPathViolationException("Target path is invalid"); }
		Path normalized = assets.workspaceRoot().resolve(requested).normalize();
		if (requested.isAbsolute() || !normalized.startsWith(assets.workspaceRoot())
				|| normalized.startsWith(assets.workspaceRoot().resolve(".copperbench")))
			throw new AssetPathViolationException("Target path escapes the asset workspace");
		try {
			Path parent = normalized.getParent();
			if (parent != null && Files.exists(parent) && !parent.toRealPath().startsWith(assets.workspaceRoot()))
				throw new IOException("target parent escapes workspace");
		} catch (IOException exception) {
			throw new AssetPathViolationException("Target path escapes the asset workspace");
		}
		return normalized;
	}
}
