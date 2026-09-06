/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RecoveryPointSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Preview-first, recovery-protected asset rename/move with exact structured-reference rewrites. */
public final class AssetMoveService {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Set<String> RESOURCE_PREFIXES = Set.of("textures/", "models/", "animations/", "sounds/",
			"lang/", "blockstates/", "items/", "font/", "shaders/");
	private final AssetWorkspaceService assets;
	private final LocalHistoryService history;

	public AssetMoveService(AssetWorkspaceService assets, LocalHistoryService history) {
		this.assets = Objects.requireNonNull(assets, "assets");
		this.history = Objects.requireNonNull(history, "history");
	}

	public AssetMovePlan preview(String sourceAssetId, String targetRelativePath) {
		AssetReferenceGraph graph = assets.referenceGraph();
		AssetDescriptor source = graph.assets().stream().filter(asset -> asset.id().equals(sourceAssetId)).findFirst()
				.orElseThrow(() -> new AssetMoveException("ASSET_MOVE_SOURCE_NOT_FOUND", "Source asset does not exist"));
		String target = normalizeTarget(targetRelativePath);
		LinkedHashSet<String> issues = new LinkedHashSet<>();
		if (source.relativePath().equals(target)) issues.add("ASSET_MOVE_TARGET_UNCHANGED");
		if (!extension(source.relativePath()).equals(extension(target))) issues.add("ASSET_MOVE_EXTENSION_MISMATCH");
		if (AssetCategory.fromRelativePath(target) != source.category()) issues.add("ASSET_MOVE_CATEGORY_MISMATCH");
		if (!resourceRoot(source.relativePath()).equals(resourceRoot(target))) issues.add("ASSET_MOVE_RESOURCE_ROOT_MISMATCH");
		Path targetFile = assets.workspaceRoot().resolve(target).normalize();
		if (Files.exists(targetFile, LinkOption.NOFOLLOW_LINKS)) issues.add("ASSET_MOVE_TARGET_EXISTS");

		Map<String, AssetDescriptor> descriptors = new HashMap<>();
		graph.assets().forEach(asset -> descriptors.put(asset.relativePath(), asset));
		List<AssetMovePlan.ReferenceRewrite> rewrites = new ArrayList<>();
		for (AssetReference reference : graph.incoming(source.relativePath())) {
			AssetDescriptor sourceDocument = descriptors.get(reference.sourcePath());
			if (sourceDocument == null || reference.sourcePointer().isEmpty()) {
				issues.add("ASSET_MOVE_REFERENCE_UNREWRITABLE");
				continue;
			}
			try {
				String replacement = replacement(reference, target);
				String normalized = AssetWorkspaceService.normalizeReference(replacement, reference.sourcePath(),
						reference.expectedPrefix());
				if (!normalized.equals(target)) {
					issues.add("ASSET_MOVE_REFERENCE_UNREWRITABLE");
					continue;
				}
				rewrites.add(new AssetMovePlan.ReferenceRewrite(reference.sourceAssetId(), reference.sourcePath(),
						sourceDocument.sha256(), reference.sourcePointer(), reference.rawValue(), replacement, reference.kind()));
			} catch (RuntimeException exception) {
				issues.add("ASSET_MOVE_REFERENCE_UNREWRITABLE");
			}
		}
		rewrites.sort(Comparator.comparing(AssetMovePlan.ReferenceRewrite::sourcePath)
				.thenComparing(AssetMovePlan.ReferenceRewrite::sourcePointer));
		return new AssetMovePlan(source.id(), source.relativePath(), source.sha256(), source.category(), target,
				AssetDescriptor.stableIdForPath(target), rewrites, issues.isEmpty(), List.copyOf(issues));
	}

	public ApplyResult apply(AssetMovePlan approved, Actor actor, String taskId) throws LocalHistoryException {
		Objects.requireNonNull(approved, "approved");
		Objects.requireNonNull(actor, "actor");
		AssetMovePlan current = preview(approved.sourceAssetId(), approved.targetRelativePath());
		if (!sameSnapshot(approved, current))
			throw new AssetMoveException("ASSET_MOVE_PLAN_STALE", "Asset move impact changed after preview");
		if (!current.canApply())
			throw new AssetMoveException("ASSET_MOVE_NOT_APPLICABLE", "Asset move preview is blocked");

		RecoveryPoint recovery = history.createRecoveryPoint(new RecoveryPointRequest(
				"Before asset move: " + current.sourceRelativePath() + " -> " + current.targetRelativePath(), actor,
				taskId, RecoveryPointSource.ASSET));
		try {
			rewriteIncomingReferences(current);
			Path source = assets.workspaceRoot().resolve(current.sourceRelativePath()).normalize();
			Path target = assets.workspaceRoot().resolve(current.targetRelativePath()).normalize();
			Files.createDirectories(target.getParent());
			move(source, target);

			AssetReferenceGraph refreshed = assets.referenceGraph();
			AssetDescriptor moved = refreshed.assets().stream()
					.filter(asset -> asset.relativePath().equals(current.targetRelativePath())).findFirst()
					.orElseThrow(() -> new AssetMoveException("ASSET_MOVE_POSTCHECK_FAILED",
							"Moved asset was not indexed at the target path"));
			if (!moved.id().equals(current.targetAssetId()))
				throw new AssetMoveException("ASSET_MOVE_POSTCHECK_FAILED", "Moved asset identity did not match preview");
			boolean danglingOldTarget = refreshed.diagnostics().stream().anyMatch(diagnostic ->
					"MISSING_ASSET_REFERENCE".equals(diagnostic.code())
							&& current.sourceRelativePath().equals(diagnostic.targetPath()));
			if (danglingOldTarget || !refreshed.incoming(current.sourceRelativePath()).isEmpty())
				throw new AssetMoveException("ASSET_MOVE_POSTCHECK_FAILED",
						"Asset move left a reference to the old path");
			if (refreshed.incoming(current.targetRelativePath()).size() < current.rewrites().size())
				throw new AssetMoveException("ASSET_MOVE_POSTCHECK_FAILED",
						"Not all previewed inbound references resolved to the moved asset");
			return new ApplyResult(moved, recovery, current.rewrites().size());
		} catch (Exception failure) {
			try {
				history.restore(recovery.id());
			} catch (Exception rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}
			if (failure instanceof LocalHistoryException localHistoryException) throw localHistoryException;
			if (failure instanceof AssetMoveException moveException) throw moveException;
			throw new AssetMoveException("ASSET_MOVE_WRITE_FAILED", "Asset move failed and was rolled back", failure);
		}
	}

	private void rewriteIncomingReferences(AssetMovePlan plan) throws IOException {
		Map<String, List<AssetMovePlan.ReferenceRewrite>> bySource = new HashMap<>();
		for (AssetMovePlan.ReferenceRewrite rewrite : plan.rewrites())
			bySource.computeIfAbsent(rewrite.sourcePath(), ignored -> new ArrayList<>()).add(rewrite);
		for (String sourcePath : bySource.keySet().stream().sorted().toList()) {
			Path sourceFile = assets.workspaceRoot().resolve(sourcePath).normalize();
			AssetDescriptor current = AssetDescriptor.fromFile(assets.workspaceRoot(), sourceFile);
			List<AssetMovePlan.ReferenceRewrite> rewrites = bySource.get(sourcePath);
			if (rewrites.stream().anyMatch(rewrite -> !rewrite.sourceSha256().equals(current.sha256())))
				throw new AssetMoveException("ASSET_MOVE_PLAN_STALE", "Reference source changed after preview: " + sourcePath);
			JsonElement document = JsonParser.parseString(Files.readString(sourceFile, StandardCharsets.UTF_8));
			for (AssetMovePlan.ReferenceRewrite rewrite : rewrites)
				replaceAtPointer(document, rewrite.sourcePointer(), rewrite.oldRawValue(), rewrite.newRawValue());
			writeJson(sourceFile, document);
		}
	}

	private static void replaceAtPointer(JsonElement document, String pointer, String expected, String replacement) {
		String[] tokens = pointer.substring(1).split("/", -1);
		JsonElement cursor = document;
		for (int index = 0; index < tokens.length - 1; index++) {
			String token = unescapePointer(tokens[index]);
			if (cursor.isJsonObject()) cursor = cursor.getAsJsonObject().get(token);
			else if (cursor.isJsonArray()) cursor = cursor.getAsJsonArray().get(Integer.parseInt(token));
			else cursor = null;
			if (cursor == null)
				throw new AssetMoveException("ASSET_MOVE_PLAN_STALE", "Reference JSON Pointer no longer exists: " + pointer);
		}
		String leaf = unescapePointer(tokens[tokens.length - 1]);
		JsonElement value;
		if (cursor.isJsonObject()) value = cursor.getAsJsonObject().get(leaf);
		else if (cursor.isJsonArray()) value = cursor.getAsJsonArray().get(Integer.parseInt(leaf));
		else value = null;
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
				|| !value.getAsString().equals(expected))
			throw new AssetMoveException("ASSET_MOVE_PLAN_STALE", "Reference value changed after preview: " + pointer);
		if (cursor.isJsonObject()) cursor.getAsJsonObject().addProperty(leaf, replacement);
		else cursor.getAsJsonArray().set(Integer.parseInt(leaf), new JsonPrimitive(replacement));
	}

	private static String unescapePointer(String token) {
		return token.replace("~1", "/").replace("~0", "~");
	}

	private static void writeJson(Path target, JsonElement document) throws IOException {
		Path temporary = Files.createTempFile(target.getParent(), ".copperbench-asset-ref-", ".tmp");
		try {
			Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
			move(temporary, target);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void move(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private String normalizeTarget(String relativePath) {
		if (relativePath == null || relativePath.isBlank())
			throw new AssetMoveException("ASSET_MOVE_TARGET_REQUIRED", "Target asset path is required");
		Path requested;
		try {
			requested = Path.of(relativePath.replace('\\', '/'));
		} catch (RuntimeException exception) {
			throw new AssetMoveException("ASSET_MOVE_TARGET_INVALID", "Target asset path is invalid", exception);
		}
		if (requested.isAbsolute())
			throw new AssetMoveException("ASSET_MOVE_TARGET_INVALID", "Target asset path must be workspace-relative");
		Path target = assets.workspaceRoot().resolve(requested).normalize();
		if (!target.startsWith(assets.workspaceRoot()) || target.startsWith(assets.workspaceRoot().resolve(".copperbench")))
			throw new AssetMoveException("ASSET_MOVE_TARGET_INVALID", "Target asset path escapes the workspace");
		String normalized = assets.workspaceRoot().relativize(target).toString().replace('\\', '/');
		if (!isAssetRoot(normalized))
			throw new AssetMoveException("ASSET_MOVE_TARGET_INVALID", "Target path is outside known asset roots");
		verifyExistingAncestor(target);
		return normalized;
	}

	private void verifyExistingAncestor(Path target) {
		Path cursor = target.getParent();
		while (cursor != null && !Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) cursor = cursor.getParent();
		if (cursor == null)
			throw new AssetMoveException("ASSET_MOVE_TARGET_INVALID", "Target asset parent is unavailable");
		try {
			if (!cursor.toRealPath().startsWith(assets.workspaceRoot()))
				throw new AssetMoveException("ASSET_MOVE_TARGET_INVALID", "Target path resolves outside the workspace");
		} catch (AssetMoveException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new AssetMoveException("ASSET_MOVE_TARGET_INVALID", "Target asset parent is unavailable", exception);
		}
	}

	private static String replacement(AssetReference reference, String targetPath) {
		String relativeTarget = stripResourceRoot(targetPath);
		if (!relativeTarget.startsWith("assets/"))
			throw new AssetMoveException("ASSET_MOVE_REFERENCE_UNREWRITABLE", "Target is not a namespaced asset");
		String[] parts = relativeTarget.split("/", 3);
		if (parts.length < 3)
			throw new AssetMoveException("ASSET_MOVE_REFERENCE_UNREWRITABLE", "Target resource path is incomplete");
		String namespace = parts[1];
		String rest = parts[2];
		String noExtension = removeExtension(rest);
		String raw = reference.rawValue().replace('\\', '/');
		boolean rawHasExtension = hasExtension(raw);
		boolean rawHasKnownPrefix = hasKnownPrefix(raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw);
		String renderedPath = rawHasExtension ? rest : noExtension;
		if (!rawHasKnownPrefix && reference.expectedPrefix() != null
				&& renderedPath.startsWith(reference.expectedPrefix()))
			renderedPath = renderedPath.substring(reference.expectedPrefix().length());

		if (reference.kind() == AssetReference.ReferenceKind.RESOURCE_ID)
			return namespace + ":" + renderedPath;
		if (raw.startsWith("assets/"))
			return "assets/" + namespace + "/" + (rawHasExtension ? rest : noExtension);
		String sourceNamespace = namespace(reference.sourcePath());
		if (namespace.equals(sourceNamespace)) return renderedPath;
		return namespace + ":" + renderedPath;
	}

	private static String stripResourceRoot(String path) {
		String root = resourceRoot(path);
		return root.isEmpty() ? path : path.substring(root.length());
	}

	private static String resourceRoot(String path) {
		String[] parts = path.split("/");
		for (int index = 0; index < parts.length; index++) {
			if (parts[index].equals("assets")) {
				if (index == 0) return "";
				return String.join("/", java.util.Arrays.copyOfRange(parts, 0, index)) + "/";
			}
		}
		return "";
	}

	private static String namespace(String path) {
		String[] parts = stripResourceRoot(path).split("/");
		return parts.length > 1 && parts[0].equals("assets") ? parts[1] : "minecraft";
	}

	private static String extension(String path) {
		String name = path.toLowerCase(Locale.ROOT);
		int slash = name.lastIndexOf('/');
		int dot = name.lastIndexOf('.');
		return dot > slash ? name.substring(dot) : "";
	}

	private static String removeExtension(String path) {
		String extension = extension(path);
		return extension.isEmpty() ? path : path.substring(0, path.length() - extension.length());
	}

	private static boolean hasExtension(String value) {
		return !extension(value).isEmpty();
	}

	private static boolean hasKnownPrefix(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return RESOURCE_PREFIXES.stream().anyMatch(lower::startsWith) || lower.startsWith("assets/");
	}

	private static boolean isAssetRoot(String relative) {
		return relative.startsWith("assets/") || relative.startsWith("models/") || relative.startsWith("resourcepacks/")
				|| relative.startsWith("src/main/resources/assets/") || relative.startsWith("src/main/assets/")
				|| relative.equals("pack.mcmeta") || relative.equals("pack.png")
				|| relative.equals("src/main/pack.mcmeta") || relative.equals("src/main/pack.png");
	}

	private static boolean sameSnapshot(AssetMovePlan expected, AssetMovePlan current) {
		return expected.sourceAssetId().equals(current.sourceAssetId())
				&& expected.sourceRelativePath().equals(current.sourceRelativePath())
				&& expected.sourceSha256().equals(current.sourceSha256())
				&& expected.targetRelativePath().equals(current.targetRelativePath())
				&& expected.targetAssetId().equals(current.targetAssetId())
				&& expected.rewrites().equals(current.rewrites())
				&& expected.canApply() == current.canApply()
				&& expected.issueCodes().equals(current.issueCodes());
	}

	public record ApplyResult(AssetDescriptor asset, RecoveryPoint recoveryPoint, int rewrittenReferences) {
		public ApplyResult {
			Objects.requireNonNull(asset, "asset");
			Objects.requireNonNull(recoveryPoint, "recoveryPoint");
			if (rewrittenReferences < 0) throw new IllegalArgumentException("rewrittenReferences must not be negative");
		}
	}

	public static final class AssetMoveException extends RuntimeException {
		private final String code;

		public AssetMoveException(String code, String message) {
			super(message);
			this.code = code;
		}

		public AssetMoveException(String code, String message, Throwable cause) {
			super(message, cause);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}
}
