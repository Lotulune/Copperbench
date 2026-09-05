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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Preview-first, recovery-protected external asset import boundary. */
public final class AssetImportService {

	private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".json", ".png", ".jpg", ".jpeg", ".ogg",
			".wav", ".bbmodel", ".mcmeta", ".zip", ".lang");
	private final AssetWorkspaceService assets;
	private final LocalHistoryService history;

	public AssetImportService(AssetWorkspaceService assets, LocalHistoryService history) {
		this.assets = Objects.requireNonNull(assets, "assets");
		this.history = Objects.requireNonNull(history, "history");
	}

	public AssetImportPlan preview(Path source, String targetRelativePath) {
		Path input = source(source);
		String targetPath = normalizeTarget(targetRelativePath);
		if (!compatibleExtensions(input.getFileName().toString(), targetPath))
			throw new AssetImportException("ASSET_IMPORT_EXTENSION_MISMATCH",
					"The selected source type does not match the target asset extension");
		String sourceHash = sha256(input);
		long sourceSize = size(input);
		String mediaType = mediaType(input.getFileName().toString());
		AssetCategory category = AssetCategory.fromRelativePath(targetPath);
		Path target = assets.workspaceRoot().resolve(targetPath).normalize();
		verifyExistingAncestorInsideWorkspace(target);
		String targetHash = Files.isRegularFile(target) ? sha256(target) : null;
		AssetImportPlan.Conflict conflict = targetHash == null ? AssetImportPlan.Conflict.CREATE
				: targetHash.equals(sourceHash) ? AssetImportPlan.Conflict.IDENTICAL : AssetImportPlan.Conflict.REPLACE;
		List<String> duplicates = assets.list().stream()
				.filter(asset -> asset.sha256().equals(sourceHash) && asset.mediaType().equals(mediaType))
				.filter(asset -> !asset.relativePath().equals(targetPath))
				.map(AssetDescriptor::relativePath).sorted().toList();
		List<String> issueCodes = new ArrayList<>();
		if (conflict == AssetImportPlan.Conflict.IDENTICAL) issueCodes.add("ASSET_IMPORT_TARGET_IDENTICAL");
		else if (conflict == AssetImportPlan.Conflict.REPLACE) issueCodes.add("ASSET_IMPORT_TARGET_WILL_REPLACE");
		if (!duplicates.isEmpty()) issueCodes.add("ASSET_IMPORT_DUPLICATE_CONTENT");
		return new AssetImportPlan(input, input.getFileName().toString(), sourceSize, sourceHash, mediaType, category,
				targetPath, conflict, targetHash, duplicates, conflict != AssetImportPlan.Conflict.IDENTICAL,
				List.copyOf(issueCodes));
	}

	public ApplyResult apply(AssetImportPlan approved, Actor actor, String taskId) throws LocalHistoryException {
		Objects.requireNonNull(approved, "approved");
		Objects.requireNonNull(actor, "actor");
		AssetImportPlan current = revalidate(approved);
		if (!current.canApply())
			throw new AssetImportException("ASSET_IMPORT_NOT_APPLICABLE", "The approved asset import has no changes");

		RecoveryPoint recovery = history.createRecoveryPoint(new RecoveryPointRequest(
				"Before asset import: " + current.targetRelativePath(), actor, taskId));
		try {
			AssetDescriptor imported = writeValidated(current);
			return new ApplyResult(imported, recovery, current.conflict());
		} catch (Exception writeFailure) {
			try {
				history.restore(recovery.id());
			} catch (Exception rollbackFailure) {
				writeFailure.addSuppressed(rollbackFailure);
			}
			if (writeFailure instanceof LocalHistoryException localHistoryException) throw localHistoryException;
			throw new AssetImportException("ASSET_IMPORT_WRITE_FAILED", "Asset import failed and was rolled back",
					writeFailure);
		}
	}

	AssetImportPlan revalidate(AssetImportPlan approved) {
		AssetImportPlan current = preview(approved.source(), approved.targetRelativePath());
		if (!sameSnapshot(approved, current))
			throw new AssetImportException("ASSET_IMPORT_PLAN_STALE",
					"The source or target changed after the asset import preview");
		return current;
	}

	AssetDescriptor writeValidated(AssetImportPlan current) throws IOException {
		if (!current.canApply())
			throw new AssetImportException("ASSET_IMPORT_NOT_APPLICABLE", "The approved asset import has no changes");
		return writeSourceToTarget(current.source(), current.targetRelativePath());
	}

	AssetDescriptor writeSourceToTarget(Path source, String targetRelativePath) throws IOException {
		Path target = assets.workspaceRoot().resolve(targetRelativePath).normalize();
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), ".copperbench-asset-import-", ".tmp");
		try {
			Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		return AssetDescriptor.fromFile(assets.workspaceRoot(), target);
	}

	private Path source(Path source) {
		Objects.requireNonNull(source, "source");
		try {
			Path input = source.toRealPath();
			if (!Files.isRegularFile(input)) throw new IOException("source is not a regular file");
			String extension = extension(input.getFileName().toString());
			if (!SUPPORTED_EXTENSIONS.contains(extension))
				throw new AssetImportException("ASSET_IMPORT_TYPE_UNSUPPORTED",
						"Unsupported asset import extension: " + extension);
			return input;
		} catch (AssetImportException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new AssetImportException("ASSET_IMPORT_SOURCE_UNAVAILABLE", "The selected source file is unavailable",
					exception);
		}
	}

	private String normalizeTarget(String relativePath) {
		if (relativePath == null || relativePath.isBlank())
			throw new AssetImportException("ASSET_IMPORT_TARGET_REQUIRED", "Target asset path is required");
		Path requested;
		try {
			requested = Path.of(relativePath.replace('\\', '/'));
		} catch (RuntimeException exception) {
			throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID", "Target asset path is invalid", exception);
		}
		if (requested.isAbsolute())
			throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID", "Target asset path must be workspace-relative");
		Path target = assets.workspaceRoot().resolve(requested).normalize();
		if (!target.startsWith(assets.workspaceRoot()) || target.startsWith(assets.workspaceRoot().resolve(".copperbench")))
			throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID", "Target asset path escapes the workspace");
		String normalized = assets.workspaceRoot().relativize(target).toString().replace('\\', '/');
		if (!isAssetRoot(normalized))
			throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID", "Target path is outside known asset roots");
		if (!SUPPORTED_EXTENSIONS.contains(extension(normalized)))
			throw new AssetImportException("ASSET_IMPORT_TYPE_UNSUPPORTED", "Target asset extension is unsupported");
		try {
			Path parent = target.getParent();
			if (parent != null && Files.exists(parent) && !parent.toRealPath().startsWith(assets.workspaceRoot()))
				throw new IOException("target parent escapes workspace");
		} catch (IOException exception) {
			throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID", "Target asset path escapes the workspace",
					exception);
		}
		return normalized;
	}

	private void verifyExistingAncestorInsideWorkspace(Path target) {
		Path cursor = target.getParent();
		while (cursor != null && !Files.exists(cursor, java.nio.file.LinkOption.NOFOLLOW_LINKS))
			cursor = cursor.getParent();
		if (cursor == null)
			throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID", "Target asset parent is unavailable");
		try {
			Path real = cursor.toRealPath();
			if (!real.startsWith(assets.workspaceRoot()))
				throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID",
						"Target asset path resolves outside the workspace");
		} catch (AssetImportException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new AssetImportException("ASSET_IMPORT_TARGET_INVALID", "Target asset parent is unavailable",
					exception);
		}
	}

	private static boolean isAssetRoot(String relative) {
		return relative.startsWith("assets/") || relative.startsWith("models/") || relative.startsWith("resourcepacks/")
				|| relative.startsWith("src/main/resources/assets/") || relative.startsWith("src/main/assets/")
				|| relative.equals("pack.mcmeta") || relative.equals("pack.png")
				|| relative.equals("src/main/pack.mcmeta") || relative.equals("src/main/pack.png");
	}

	private static boolean sameSnapshot(AssetImportPlan expected, AssetImportPlan current) {
		return expected.source().equals(current.source()) && expected.sourceSize() == current.sourceSize()
				&& expected.sourceSha256().equals(current.sourceSha256())
				&& expected.targetRelativePath().equals(current.targetRelativePath())
				&& expected.conflict() == current.conflict()
				&& Objects.equals(expected.targetSha256(), current.targetSha256());
	}

	private static long size(Path path) {
		try {
			return Files.size(path);
		} catch (IOException exception) {
			throw new AssetImportException("ASSET_IMPORT_SOURCE_UNAVAILABLE", "Could not read source size", exception);
		}
	}

	private static String sha256(Path file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("JVM must provide SHA-256", exception);
		} catch (IOException exception) {
			throw new AssetImportException("ASSET_IMPORT_SOURCE_UNAVAILABLE", "Could not hash asset file", exception);
		}
	}

	private static String extension(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
		int dot = lower.lastIndexOf('.');
		return dot > slash ? lower.substring(dot) : "";
	}

	private static boolean compatibleExtensions(String sourceName, String targetPath) {
		String sourceExtension = extension(sourceName);
		String targetExtension = extension(targetPath);
		if (sourceExtension.equals(targetExtension)) return true;
		return (sourceExtension.equals(".jpg") || sourceExtension.equals(".jpeg"))
				&& (targetExtension.equals(".jpg") || targetExtension.equals(".jpeg"));
	}

	private static String mediaType(String name) {
		return switch (extension(name)) {
			case ".json", ".mcmeta", ".bbmodel" -> "application/json";
			case ".png" -> "image/png";
			case ".jpg", ".jpeg" -> "image/jpeg";
			case ".ogg" -> "audio/ogg";
			case ".wav" -> "audio/wav";
			case ".zip" -> "application/zip";
			case ".lang" -> "text/plain";
			default -> "application/octet-stream";
		};
	}

	public record ApplyResult(AssetDescriptor asset, RecoveryPoint recoveryPoint, AssetImportPlan.Conflict conflict) {
		public ApplyResult {
			Objects.requireNonNull(asset, "asset");
			Objects.requireNonNull(recoveryPoint, "recoveryPoint");
			Objects.requireNonNull(conflict, "conflict");
		}
	}

	public static final class AssetImportException extends RuntimeException {
		private final String code;

		public AssetImportException(String code, String message) {
			super(message);
			this.code = code;
		}

		public AssetImportException(String code, String message, Throwable cause) {
			super(message, cause);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}
}
