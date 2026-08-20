package dev.copperbench.assets;

import java.util.Objects;

/** One normalized edge in the workspace asset graph. */
public record AssetReference(String sourceAssetId, String sourcePath, String targetPath, String targetAssetId,
		ReferenceKind kind) {
	public AssetReference {
		Objects.requireNonNull(sourceAssetId, "sourceAssetId");
		Objects.requireNonNull(sourcePath, "sourcePath");
		Objects.requireNonNull(targetPath, "targetPath");
		Objects.requireNonNull(kind, "kind");
	}

	public enum ReferenceKind { JSON_STRING, RESOURCE_ID }
}
