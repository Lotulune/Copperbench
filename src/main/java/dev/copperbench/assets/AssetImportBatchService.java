/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import dev.copperbench.assets.AssetImportService.AssetImportException;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RecoveryPointSource;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Atomic multi-file import coordinator that reuses the single-file validation boundary. */
public final class AssetImportBatchService {
	private final AssetImportService importer;
	private final LocalHistoryService history;

	public AssetImportBatchService(AssetWorkspaceService assets, LocalHistoryService history) {
		this.importer = new AssetImportService(Objects.requireNonNull(assets, "assets"),
				Objects.requireNonNull(history, "history"));
		this.history = history;
	}

	public AssetImportBatchPlan preview(List<Request> requests) {
		if (requests == null || requests.isEmpty())
			throw new AssetImportException("ASSET_IMPORT_BATCH_EMPTY", "At least one asset source is required");
		List<AssetImportPlan> items = new ArrayList<>(requests.size());
		Set<String> targets = new HashSet<>();
		boolean targetConflict = false;
		int creates = 0;
		int replaces = 0;
		int identical = 0;
		for (Request request : requests) {
			AssetImportPlan item = importer.preview(request.source(), request.targetRelativePath());
			items.add(item);
			if (!targets.add(item.targetRelativePath().toLowerCase(Locale.ROOT))) targetConflict = true;
			switch (item.conflict()) {
				case CREATE -> creates++;
				case REPLACE -> replaces++;
				case IDENTICAL -> identical++;
			}
		}
		List<String> issues = targetConflict ? List.of("ASSET_IMPORT_BATCH_TARGET_CONFLICT") : List.of();
		return new AssetImportBatchPlan(items, creates, replaces, identical,
				!targetConflict && creates + replaces > 0, issues);
	}

	public ApplyResult apply(AssetImportBatchPlan approved, Actor actor, String taskId) throws LocalHistoryException {
		Objects.requireNonNull(approved, "approved");
		Objects.requireNonNull(actor, "actor");
		List<Request> requests = approved.items().stream()
				.map(item -> new Request(item.source(), item.targetRelativePath())).toList();
		AssetImportBatchPlan current = preview(requests);
		if (!sameSnapshot(approved, current))
			throw new AssetImportException("ASSET_IMPORT_BATCH_PLAN_STALE",
					"The asset batch changed after preview");
		if (!current.canApply())
			throw new AssetImportException("ASSET_IMPORT_BATCH_NOT_APPLICABLE", "The asset batch has no applicable changes");

		for (AssetImportPlan item : approved.items()) importer.revalidate(item);
		List<StagedItem> staged = new ArrayList<>(current.changedCount());
		try {
			for (AssetImportPlan item : current.items()) {
				if (item.conflict() == AssetImportPlan.Conflict.IDENTICAL) continue;
				String suffix = item.sourceFileName().contains(".")
						? item.sourceFileName().substring(item.sourceFileName().lastIndexOf('.')) : ".bin";
				Path snapshot = Files.createTempFile("copperbench-asset-batch-", suffix);
				Files.copy(item.source(), snapshot, StandardCopyOption.REPLACE_EXISTING);
				staged.add(new StagedItem(item, snapshot));
			}
		} catch (Exception stagingFailure) {
			staged.forEach(StagedItem::deleteQuietly);
			throw new AssetImportException("ASSET_IMPORT_BATCH_SOURCE_STAGE_FAILED",
					"Asset batch sources could not be snapshotted before writing", stagingFailure);
		}

		RecoveryPoint recovery = history.createRecoveryPoint(new RecoveryPointRequest(
				"Before asset batch import: " + current.changedCount() + " assets", actor, taskId,
				RecoveryPointSource.ASSET));
		List<AssetDescriptor> imported = new ArrayList<>(current.changedCount());
		try {
			for (StagedItem item : staged)
				imported.add(importer.writeSourceToTarget(item.snapshot(), item.plan().targetRelativePath()));
			return new ApplyResult(imported, recovery, current.createCount(), current.replaceCount(), current.identicalCount());
		} catch (Exception failure) {
			try {
				history.restore(recovery.id());
			} catch (Exception rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}
			if (failure instanceof LocalHistoryException localHistoryException) throw localHistoryException;
			if (failure instanceof AssetImportException importException) throw importException;
			throw new AssetImportException("ASSET_IMPORT_BATCH_WRITE_FAILED",
					"Asset batch import failed and was rolled back", failure);
		} finally {
			staged.forEach(StagedItem::deleteQuietly);
		}
	}

	private record StagedItem(AssetImportPlan plan, Path snapshot) {
		private void deleteQuietly() {
			try {
				Files.deleteIfExists(snapshot);
			} catch (Exception ignored) {
			}
		}
	}

	private static boolean sameSnapshot(AssetImportBatchPlan expected, AssetImportBatchPlan current) {
		if (expected.items().size() != current.items().size()
				|| expected.createCount() != current.createCount()
				|| expected.replaceCount() != current.replaceCount()
				|| expected.identicalCount() != current.identicalCount()
				|| expected.canApply() != current.canApply()
				|| !expected.issueCodes().equals(current.issueCodes())) return false;
		for (int index = 0; index < expected.items().size(); index++) {
			AssetImportPlan left = expected.items().get(index);
			AssetImportPlan right = current.items().get(index);
			if (!left.source().equals(right.source()) || left.sourceSize() != right.sourceSize()
					|| !left.sourceSha256().equals(right.sourceSha256())
					|| !left.targetRelativePath().equals(right.targetRelativePath())
					|| left.conflict() != right.conflict()
					|| !Objects.equals(left.targetSha256(), right.targetSha256())) return false;
		}
		return true;
	}

	public record Request(Path source, String targetRelativePath) {
		public Request {
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(targetRelativePath, "targetRelativePath");
		}
	}

	public record ApplyResult(List<AssetDescriptor> assets, RecoveryPoint recoveryPoint, int createCount,
			int replaceCount, int skippedIdenticalCount) {
		public ApplyResult {
			assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
			Objects.requireNonNull(recoveryPoint, "recoveryPoint");
		}

		public int importedCount() {
			return assets.size();
		}
	}
}
