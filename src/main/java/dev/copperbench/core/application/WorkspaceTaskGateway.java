package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Port for validation, generation and build processes managed outside workspace transactions. */
public interface WorkspaceTaskGateway {

	JsonObject start(UUID workspaceId, Operation operation, JsonObject payload);

	Optional<JsonObject> find(UUID workspaceId, UUID taskId);

	List<JsonObject> active(UUID workspaceId);

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
