/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RecoveryPointSource;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Preview-first, recovery-protected deletion of workspace assets. The preview names every asset reference that would
 * stop resolving; deleting anything other than assessed cleanup candidates needs that impact confirmed.
 */
public final class AssetDeleteService {
	public static final int MAX_ASSETS = 200;
	private static final Set<String> PACK_METADATA = Set.of("pack.mcmeta", "src/main/pack.mcmeta");

	private final AssetWorkspaceService assets;
	private final LocalHistoryService history;
	private final Predicate<String> generatorOwned;

	/** @param generatorOwned whether a workspace-relative path is written by the generator, which would recreate it */
	public AssetDeleteService(AssetWorkspaceService assets, LocalHistoryService history, Predicate<String> generatorOwned) {
		this.assets = Objects.requireNonNull(assets, "assets");
		this.history = Objects.requireNonNull(history, "history");
		this.generatorOwned = Objects.requireNonNull(generatorOwned, "generatorOwned");
	}

	public AssetDeletePlan preview(List<String> assetIds, AssetReferenceGraph graph, AssetHealthReport health) {
		if (assetIds == null || assetIds.isEmpty() || assetIds.size() > MAX_ASSETS)
			throw new AssetDeleteException("ASSET_DELETE_REQUEST_INVALID", "Select 1 to " + MAX_ASSETS + " assets to delete.");
		if (assetIds.stream().distinct().count() != assetIds.size())
			throw new AssetDeleteException("ASSET_DELETE_REQUEST_INVALID", "Each asset may be selected once.");
		Map<String, AssetDescriptor> byId = graph.assets().stream().collect(Collectors.toMap(AssetDescriptor::id, Function.identity()));
		List<AssetDescriptor> selected = new ArrayList<>();
		for (String id : assetIds) {
			AssetDescriptor asset = byId.get(id);
			if (asset == null) throw new AssetDeleteException("ASSET_DELETE_NOT_FOUND", "An asset to delete no longer exists. Refresh the asset list.");
			selected.add(asset);
		}
		selected.sort(Comparator.comparing(AssetDescriptor::relativePath));
		Set<String> deleting = selected.stream().map(AssetDescriptor::relativePath).collect(Collectors.toSet());

		LinkedHashSet<String> planIssues = new LinkedHashSet<>();
		List<AssetDeletePlan.Entry> entries = new ArrayList<>();
		List<AssetDeletePlan.AffectedReference> affected = new ArrayList<>();
		boolean allCleanupCandidates = true;
		for (AssetDescriptor asset : selected) {
			AssetHealthReport.Entry entryHealth = health.findById(asset.id()).orElseThrow(() ->
					new AssetDeleteException("ASSET_DELETE_PLAN_STALE", "The asset health scan does not match the asset list."));
			List<String> issues = new ArrayList<>();
			if (PACK_METADATA.contains(asset.relativePath())) issues.add("ASSET_DELETE_PACK_METADATA");
			if (generatorOwned.test(asset.relativePath())) issues.add("ASSET_DELETE_GENERATED");
			// References from files deleted in the same plan disappear with them.
			for (AssetReference reference : graph.incoming(asset.relativePath()))
				if (!deleting.contains(reference.sourcePath()))
					affected.add(new AssetDeletePlan.AffectedReference(reference.sourceAssetId(), reference.sourcePath(),
							reference.sourcePointer(), reference.rawValue(), reference.targetPath()));
			planIssues.addAll(issues);
			allCleanupCandidates &= entryHealth.safeUnused();
			entries.add(new AssetDeletePlan.Entry(asset.id(), asset.relativePath(), asset.sha256(), asset.category(),
					asset.size(), entryHealth.inboundCount(), entryHealth.workspaceReferenceCount(), entryHealth.safeUnused(), issues));
		}
		affected.sort(Comparator.comparing(AssetDeletePlan.AffectedReference::sourcePath)
				.thenComparing(AssetDeletePlan.AffectedReference::sourcePointer));
		if (!affected.isEmpty()) planIssues.add("ASSET_DELETE_BREAKS_REFERENCES");
		if (entries.stream().anyMatch(entry -> entry.workspaceReferenceCount() > 0)) planIssues.add("ASSET_DELETE_WORKSPACE_USAGE");
		if (!allCleanupCandidates) planIssues.add("ASSET_DELETE_USAGE_NOT_ASSESSED");
		boolean blocked = planIssues.contains("ASSET_DELETE_PACK_METADATA") || planIssues.contains("ASSET_DELETE_GENERATED");
		return new AssetDeletePlan(entries, affected, !blocked, !allCleanupCandidates || !affected.isEmpty(), List.copyOf(planIssues));
	}

	/**
	 * Deletes the files of a plan that still matches the workspace. A recovery point is taken first and restored if
	 * any file cannot be deleted.
	 */
	public ApplyResult apply(AssetDeletePlan approved, AssetDeletePlan current, boolean referencesConfirmed, Actor actor,
			String taskId) throws LocalHistoryException {
		Objects.requireNonNull(actor, "actor");
		if (!approved.equals(current))
			throw new AssetDeleteException("ASSET_DELETE_PLAN_STALE", "The assets or their references changed after the preview.");
		if (!current.canApply())
			throw new AssetDeleteException("ASSET_DELETE_NOT_APPLICABLE", "The reviewed asset deletion is blocked.");
		if (current.requiresReferenceConfirmation() && !referencesConfirmed)
			throw new AssetDeleteException("ASSET_DELETE_CONFIRMATION_REQUIRED",
					"Confirm the listed usage before deleting assets that are not cleanup candidates.");
		List<Path> files = new ArrayList<>();
		for (AssetDeletePlan.Entry entry : current.entries()) files.add(assets.resolveAuthorizedPath(entry.relativePath()));
		String first = current.entries().getFirst().relativePath();
		RecoveryPoint recovery = history.createRecoveryPoint(new RecoveryPointRequest("Before asset delete: " + first
				+ (current.entries().size() > 1 ? " and " + (current.entries().size() - 1) + " more" : ""), actor, taskId,
				RecoveryPointSource.ASSET));
		try {
			for (Path file : files) {
				if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
					throw new AssetDeleteException("ASSET_DELETE_PLAN_STALE", "An asset to delete is no longer a regular file.");
				Files.delete(file);
			}
			return new ApplyResult(current.entries().stream().map(AssetDeletePlan.Entry::relativePath).toList(), recovery);
		} catch (Exception failure) {
			try {
				history.restore(recovery.id());
			} catch (Exception rollback) {
				failure.addSuppressed(rollback);
			}
			if (failure instanceof AssetDeleteException deleteException) throw deleteException;
			throw new AssetDeleteException("ASSET_DELETE_WRITE_FAILED", "Asset deletion failed and was rolled back.", failure);
		}
	}

	public record ApplyResult(List<String> deletedPaths, RecoveryPoint recoveryPoint) {
		public ApplyResult {
			deletedPaths = List.copyOf(deletedPaths);
			Objects.requireNonNull(recoveryPoint, "recoveryPoint");
		}
	}

	public static final class AssetDeleteException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final String code;

		public AssetDeleteException(String code, String message) {
			super(message);
			this.code = code;
		}

		public AssetDeleteException(String code, String message, Throwable cause) {
			super(message, cause);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}
}
