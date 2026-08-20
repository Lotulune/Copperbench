package dev.copperbench.core.application;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Deterministic task gateway for adapters, fixtures and tests until generator ports are connected. */
public final class InMemoryWorkspaceTaskGateway implements WorkspaceTaskGateway {

	private final Map<UUID, Map<UUID, JsonObject>> tasks = new LinkedHashMap<>();
	private final Clock clock;
	private final Supplier<UUID> ids;

	public InMemoryWorkspaceTaskGateway(Clock clock, Supplier<UUID> ids) {
		this.clock = clock;
		this.ids = ids;
	}

	@Override public synchronized JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
		UUID taskId = ids.get();
		JsonObject task = new JsonObject();
		task.addProperty("id", taskId.toString());
			task.addProperty("kind", switch (operation) {
			case VALIDATE_WORKSPACE -> "validate";
			case GENERATE_WORKSPACE -> "generate";
			case BUILD_WORKSPACE -> "build";
			case EXPORT_WORKSPACE -> "export";
			case RUN_CLIENT -> "run_client";
			default -> throw new IllegalArgumentException("Operation does not start a task: " + operation);
		});
		task.addProperty("state", "running");
		task.addProperty("cancellable", true);
		task.addProperty("progress", 0);
		task.add("stage", localized("task.started", "Task started"));
		task.addProperty("startedAt", clock.instant().toString());
		task.add("completedAt", JsonNull.INSTANCE);
		task.add("diagnostics", counts());
		tasks.computeIfAbsent(workspaceId, ignored -> new LinkedHashMap<>()).put(taskId, task);
		return task.deepCopy();
	}

	@Override public synchronized Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
		JsonObject task = tasks.getOrDefault(workspaceId, Map.of()).get(taskId);
		return Optional.ofNullable(task == null ? null : task.deepCopy());
	}

	@Override public synchronized List<JsonObject> active(UUID workspaceId) {
		List<JsonObject> active = new ArrayList<>();
		for (JsonObject task : tasks.getOrDefault(workspaceId, Map.of()).values()) {
			String state = task.get("state").getAsString();
			if (state.equals("queued") || state.equals("running"))
				active.add(task.deepCopy());
		}
		return List.copyOf(active);
	}

	@Override public synchronized Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
		JsonObject task = tasks.getOrDefault(workspaceId, Map.of()).get(taskId);
		if (task == null)
			return Optional.empty();
		String state = task.get("state").getAsString();
		if (!state.equals("queued") && !state.equals("running"))
			return Optional.of(task.deepCopy());
		task.addProperty("state", "cancelled");
		task.addProperty("cancellable", false);
		task.addProperty("progress", 1);
		task.add("stage", localized("task.cancelled", "Task cancelled"));
		task.addProperty("completedAt", clock.instant().toString());
		return Optional.of(task.deepCopy());
	}

	private static JsonObject localized(String key, String fallback) {
		JsonObject value = new JsonObject();
		value.addProperty("key", key);
		value.addProperty("fallback", fallback);
		value.add("args", new JsonObject());
		return value;
	}

	private static JsonObject counts() {
		JsonObject counts = new JsonObject();
		counts.addProperty("error", 0);
		counts.addProperty("warning", 0);
		counts.addProperty("info", 0);
		return counts;
	}
}
