package dev.copperbench.assets;

import java.util.Objects;

/** One normalized edge in the workspace asset graph, including the exact structured-document source location. */
public record AssetReference(String sourceAssetId, String sourcePath, String sourcePointer, String rawValue,
		String expectedPrefix, String targetPath, String targetAssetId, ReferenceKind kind,
		String resolution, String resourceSource, String resourceVersion) {
	public AssetReference(String sourceAssetId, String sourcePath, String sourcePointer, String rawValue,
			String expectedPrefix, String targetPath, String targetAssetId, ReferenceKind kind) {
		this(sourceAssetId, sourcePath, sourcePointer, rawValue, expectedPrefix, targetPath, targetAssetId, kind,
				"workspace_resolved", "workspace", null);
	}
	public AssetReference {
		Objects.requireNonNull(sourceAssetId, "sourceAssetId");
		Objects.requireNonNull(sourcePath, "sourcePath");
		Objects.requireNonNull(sourcePointer, "sourcePointer");
		Objects.requireNonNull(rawValue, "rawValue");
		Objects.requireNonNull(targetPath, "targetPath");
		Objects.requireNonNull(kind, "kind");
		if (!sourcePointer.isEmpty() && !sourcePointer.startsWith("/"))
			throw new IllegalArgumentException("sourcePointer must be a JSON Pointer");
	}

	public enum ReferenceKind { JSON_STRING, RESOURCE_ID, FILE_PATH }
}
