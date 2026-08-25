package dev.copperbench.core.workspace;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Owns the independently versioned product metadata namespace inside an upstream workspace document. */
public final class ProductMetadataManager {

	public static final int CURRENT_SCHEMA_VERSION = 1;
	private final UnknownFieldPreservingJsonStore documents;

	public ProductMetadataManager(UnknownFieldPreservingJsonStore documents) {
		this.documents = documents;
	}

	public Metadata loadOrCreate(Path workspaceFile, UUID workspaceId, WorkspaceFileLease lease) throws IOException {
		Metadata metadata = loadOrCreate(workspaceFile, () -> workspaceId, lease);
		if (!metadata.workspaceId().equals(workspaceId))
			throw new IllegalStateException("Workspace ID does not match product metadata");
		return metadata;
	}

	public Metadata loadOrCreate(Path workspaceFile, Supplier<UUID> workspaceIds, WorkspaceFileLease lease)
			throws IOException {
		Objects.requireNonNull(workspaceIds, "workspaceIds must not be null");
		lease.requireValidFor(workspaceFile);
		JsonObject document = documents.updateProductMetadata(workspaceFile,
				metadata -> migrate(metadata, workspaceIds));
		return parse(document.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE));
	}

	public Metadata advanceRevision(Path workspaceFile, UUID workspaceId, long expectedRevision,
			WorkspaceFileLease lease) throws IOException {
		return advanceRevision(workspaceFile, workspaceId, expectedRevision, null, lease);
	}

	public Metadata advanceRevision(Path workspaceFile, UUID workspaceId, long expectedRevision,
			JsonObject registries, WorkspaceFileLease lease) throws IOException {
		lease.requireValidFor(workspaceFile);
		JsonObject document = documents.updateProductMetadata(workspaceFile, metadata -> {
			JsonObject migrated = migrate(metadata, () -> workspaceId);
			Metadata current = parse(migrated);
			if (!current.workspaceId().equals(workspaceId))
				throw new IllegalStateException("Workspace ID does not match product metadata");
			if (current.revision() != expectedRevision)
				throw new RevisionConflictException(expectedRevision, current.revision());
			if (registries != null) migrated.add("registries", registries.deepCopy());
			migrated.addProperty("revision", current.revision() + 1);
			return migrated;
		});
		return parse(document.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE));
	}

	public Metadata synchronizeRevision(Path workspaceFile, UUID workspaceId, long revision,
			WorkspaceFileLease lease) throws IOException {
		lease.requireValidFor(workspaceFile);
		JsonObject document = documents.updateProductMetadata(workspaceFile, metadata -> {
			JsonObject migrated = migrate(metadata, () -> workspaceId);
			Metadata current = parse(migrated);
			if (!current.workspaceId().equals(workspaceId))
				throw new IllegalStateException("Workspace ID does not match product metadata");
			migrated.addProperty("revision", revision);
			return migrated;
		});
		return parse(document.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE));
	}

	private JsonObject migrate(JsonObject source, Supplier<UUID> workspaceIds) {
		JsonObject metadata = source.deepCopy();
		int schemaVersion = metadata.has("schemaVersion") ? metadata.get("schemaVersion").getAsInt() : 0;
		if (schemaVersion > CURRENT_SCHEMA_VERSION)
			throw new UnsupportedMetadataVersionException(schemaVersion, CURRENT_SCHEMA_VERSION);
		if (schemaVersion == 0) {
			UUID workspaceId = Objects.requireNonNull(workspaceIds.get(), "workspace ID must not be null");
			metadata.addProperty("schemaVersion", 1);
			metadata.addProperty("workspaceId", workspaceId.toString());
			metadata.addProperty("revision", 0);
		}
		return metadata;
	}

	private Metadata parse(JsonObject metadata) {
		UUID workspaceId = UUID.fromString(metadata.get("workspaceId").getAsString());
		return new Metadata(metadata.get("schemaVersion").getAsInt(), workspaceId,
				metadata.get("revision").getAsLong());
	}

	public record Metadata(int schemaVersion, UUID workspaceId, long revision) {
	}

	public static final class RevisionConflictException extends IllegalStateException {
		private final long expectedRevision;
		private final long actualRevision;

		public RevisionConflictException(long expectedRevision, long actualRevision) {
			super("Expected workspace revision " + expectedRevision + " but found " + actualRevision);
			this.expectedRevision = expectedRevision;
			this.actualRevision = actualRevision;
		}

		public long expectedRevision() { return expectedRevision; }
		public long actualRevision() { return actualRevision; }
	}

	public static final class UnsupportedMetadataVersionException extends IllegalStateException {
		private final int foundVersion;
		private final int supportedVersion;

		public UnsupportedMetadataVersionException(int foundVersion, int supportedVersion) {
			super("Product metadata schema " + foundVersion + " is newer than supported schema " + supportedVersion);
			this.foundVersion = foundVersion;
			this.supportedVersion = supportedVersion;
		}

		public int foundVersion() { return foundVersion; }
		public int supportedVersion() { return supportedVersion; }
	}
}
