package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Port for validation, generation and build processes managed outside workspace transactions. */
public interface WorkspaceTaskGateway {

	/** Session-owned source preparation, invoked only by explicit generation/build/run tasks. */
	@FunctionalInterface interface GenerationPreparation {
		void prepare(dev.copperbench.core.workspace.WorkspaceState state, java.nio.file.Path executionRoot,
				Operation operation, Consumer<String> output) throws Exception;
	}

	default void setGenerationPreparation(GenerationPreparation preparation) { }

	/** Preparation failure with optional, explicitly classified source conflicts. */
	final class GenerationPreparationException extends java.io.IOException {
		/** Safe fallback for conflicts whose source location is not known. */
		public static final String SOURCE_CONFLICT_MESSAGE = "Source files changed or are not owned by the generator. Review them before generating again.";

		/** Stable reasons and the ownership evidence available when generation stopped. */
		public enum ConflictReason {
			UNOWNED_BASE_FILE("The existing base file is not owned by this generator.", "not_owned_by_generator"),
			UNOWNED_ELEMENT_FILE("The existing element file is not owned by this generator.", "not_owned_by_generator"),
			SOURCE_CHANGED("The source no longer matches the recorded generation input.", "recorded_input"),
			PATH_OUTSIDE_WORKSPACE("A source path leaves the workspace; its location is withheld.", "unknown"),
			INVALID_SOURCE_PATH("A source path is not a safe workspace-relative file path.", "unknown"),
			UNSAFE_PATH("A source path contains a symbolic link or another filesystem redirection.", "unknown"),
			NON_REGULAR_FILE("The source path must refer to a regular file.", "unknown"),
			AMBIGUOUS_USER_CODE_REGION("User-code regions are nested, duplicated or unnamed.", "unknown"),
			MISMATCHED_USER_CODE_REGION("A user-code region end does not match its start.", "unknown"),
			UNCLOSED_USER_CODE_REGION("A user-code region has no matching end.", "unknown");

			private final String explanation;
			private final String ownership;
			ConflictReason(String explanation, String ownership) {
				this.explanation = explanation; this.ownership = ownership;
			}
			/** @return fixed, non-sensitive explanation, independent of exception text */
			public String explanation() { return explanation; }
			/** @return ownership evidence, not permission to overwrite the source */
			public String ownership() { return ownership; }
		}

		/**
		 * A conflict location uses forward slashes and is relative to the workspace root.
		 * Absolute paths, traversal, control characters and platform-specific separators are rejected.
		 * A null path means that no safe location is available; it must not be reconstructed from a cause.
		 * @param relativePath safe workspace-relative file path, or null
		 * @param reason explicit conflict classification
		 */
		public record SourceConflict(String relativePath, ConflictReason reason) {
			public SourceConflict {
				java.util.Objects.requireNonNull(reason, "Conflict reason is required");
				if (relativePath != null && (relativePath.isBlank() || relativePath.indexOf('\\') >= 0
						|| relativePath.indexOf(':') >= 0 || relativePath.chars().anyMatch(Character::isISOControl)
						|| java.util.Arrays.stream(relativePath.split("/", -1))
								.anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))))
					throw new IllegalArgumentException("Conflict location must be a safe workspace-relative file path");
			}
		}

		private final String code;
		private final List<SourceConflict> conflicts;
		/**
		 * Creates a legacy preparation failure without a source location.
		 * @param code stable diagnostic code
		 * @param message public explanation; source-conflict responses use a fixed safe fallback
		 * @param cause internal cause, never used to infer a public source path
		 */
		public GenerationPreparationException(String code, String message, Throwable cause) {
			super(message, cause); this.code = code; this.conflicts = List.of();
		}
		/**
		 * Creates a source conflict with explicitly checked locations.
		 * @param conflicts one or more classified conflicts
		 * @param cause internal cause, not part of the public diagnostic
		 */
		public GenerationPreparationException(List<SourceConflict> conflicts, Throwable cause) {
			super(SOURCE_CONFLICT_MESSAGE, cause); this.code = "GENERATION_SOURCE_CONFLICT";
			this.conflicts = List.copyOf(conflicts);
			if (this.conflicts.isEmpty()) throw new IllegalArgumentException("At least one source conflict is required");
		}
		/** @return stable diagnostic code */
		public String code() { return code; }
		/** @return immutable conflict details; legacy failures return an empty list */
		public List<SourceConflict> conflicts() { return conflicts; }
	}

	JsonObject start(UUID workspaceId, Operation operation, JsonObject payload);

	Optional<JsonObject> find(UUID workspaceId, UUID taskId);

	List<JsonObject> active(UUID workspaceId);

	/** Bounded observations for task discovery, including terminal tasks retained across sessions. */
	default List<JsonObject> recent(UUID workspaceId) { return active(workspaceId); }

	Optional<JsonObject> cancel(UUID workspaceId, UUID taskId);

	/**
	 * Subscribes to asynchronous task state changes. Implementations that do not
	 * have a push transport may retain the default no-op; polling remains the
	 * compatibility path through {@link #find(UUID, UUID)} and {@link #logs(UUID, UUID)}.
	 */
	default AutoCloseable subscribeTaskEvents(Consumer<TaskEvent> listener) {
		return () -> { };
	}

	default List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		return List.of();
	}

	default List<JsonObject> logsAfter(UUID workspaceId, UUID taskId, long afterSequence) {
		if (afterSequence < 0)
			throw new IllegalArgumentException("afterSequence must be non-negative");
		return logs(workspaceId, taskId).stream()
				.filter(entry -> entry.has("sequence") && entry.get("sequence").isJsonPrimitive()
						&& entry.get("sequence").getAsLong() > afterSequence)
				.toList();
	}

	default List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		return List.of();
	}

	/**
	 * Returns read-only execution context from the same backend that owns build
	 * and run tasks. Implementations should report facts rather than mutate or
	 * prepare the workspace.
	 */
	default JsonObject environment(UUID workspaceId) {
		return new JsonObject();
	}

	/**
	 * Returns a bounded read-only preview for a source file already referenced by a task diagnostic.
	 * Implementations must reject arbitrary filesystem paths and keep the read inside the task staging root.
	 */
	default Optional<JsonObject> sourcePreview(UUID workspaceId, UUID taskId, String sourcePath) {
		return Optional.empty();
	}

	default Optional<JsonObject> previewDatagen(UUID workspaceId, UUID taskId) {
		return Optional.empty();
	}

	default JsonObject publishDatagen(UUID workspaceId, UUID taskId, JsonObject payload) {
		throw new UnsupportedOperationException("Datagen publishing is not available");
	}

	default void completeDatagenPublish(UUID workspaceId, UUID taskId) {
	}

	default void rollbackDatagenPublish(UUID workspaceId, UUID taskId) {
	}

	record TaskEvent(UUID workspaceId, UUID taskId, String event, JsonObject task,
		List<JsonObject> entries, List<JsonObject> diagnostics) {
		public TaskEvent {
			if (workspaceId == null || taskId == null || event == null)
				throw new IllegalArgumentException("Task event identity is required");
			task = task == null ? null : task.deepCopy();
			entries = entries == null ? List.of() : entries.stream().map(JsonObject::deepCopy).toList();
			diagnostics = diagnostics == null ? List.of() : diagnostics.stream().map(JsonObject::deepCopy).toList();
		}
	}
}
