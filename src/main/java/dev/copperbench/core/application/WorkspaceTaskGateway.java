package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port for validation, generation and build processes managed outside workspace transactions. */
public interface WorkspaceTaskGateway {

	JsonObject start(UUID workspaceId, Operation operation, JsonObject payload);

	Optional<JsonObject> find(UUID workspaceId, UUID taskId);

	List<JsonObject> active(UUID workspaceId);

	Optional<JsonObject> cancel(UUID workspaceId, UUID taskId);

	default List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		return List.of();
	}

	default List<JsonObject> diagnostics(UUID workspaceId, UUID taskId) {
		return List.of();
	}
}
