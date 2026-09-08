package dev.copperbench.assets;

import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RestoreResult;

import java.nio.file.Path;
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
		AssetImportService importer = new AssetImportService(assets, history);
		return importer.apply(importer.preview(source, targetRelativePath), actor, taskId).recoveryPoint();
	}

	public RestoreResult restore(String recoveryPointId) throws LocalHistoryException {
		return history.restore(recoveryPointId);
	}

}
