package dev.copperbench.core.application;

import com.google.gson.JsonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Deterministic task gateway for adapters, fixtures and tests until generator ports are connected. */
public final class InMemoryWorkspaceTaskGateway implements WorkspaceTaskGateway {

	private final Map<UUID, Map<UUID, JsonObject>> tasks = new LinkedHashMap<>();
	private final Map<UUID, Map<UUID, List<JsonObject>>> logs = new LinkedHashMap<>();
	private final Map<UUID, Map<UUID, JsonObject>> datagenPreviews = new LinkedHashMap<>();
	private final CopyOnWriteArrayList<Consumer<TaskEvent>> listeners = new CopyOnWriteArrayList<>();
	private final Clock clock;
	private final Supplier<UUID> ids;

	public InMemoryWorkspaceTaskGateway(Clock clock, Supplier<UUID> ids) {
		this.clock = clock;
		this.ids = ids;
	}

	@Override public synchronized Optional<JsonObject> previewDatagen(UUID workspaceId, UUID taskId) {
		JsonObject preview = datagenPreviews.getOrDefault(workspaceId, Map.of()).get(taskId);
		return Optional.ofNullable(preview == null ? null : preview.deepCopy());
	}

	@Override public synchronized JsonObject publishDatagen(UUID workspaceId, UUID taskId, JsonObject payload) {
		JsonObject preview = datagenPreviews.getOrDefault(workspaceId, Map.of()).get(taskId);
		if (preview == null)
			throw new IllegalArgumentException("Datagen staging not found: " + taskId);
		String expectedHash = preview.get("manifestHash").getAsString();
		String suppliedHash = payload.has("manifestHash") ? payload.get("manifestHash").getAsString() : "";
		if (!expectedHash.equals(suppliedHash))
			throw new IllegalArgumentException("Datagen manifest hash does not match staged output");
		JsonObject data = new JsonObject();
		data.addProperty("taskId", taskId.toString());
		data.addProperty("manifestHash", expectedHash);
		JsonArray changedPaths = new JsonArray();
		changedPaths.add("src/generated/resources/data/copperbench_eval/generated.json");
		data.add("changedPaths", changedPaths);
		return data;
	}

	@Override public synchronized void completeDatagenPublish(UUID workspaceId, UUID taskId) {
		datagenPreviews.getOrDefault(workspaceId, Map.of()).remove(taskId);
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
			case RUN_SERVER -> "run_server";
			case RUN_DATAGEN -> "run_datagen";
			case RUN_GAMETEST -> "run_gametest";
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
		if (operation == Operation.RUN_DATAGEN) {
			JsonObject preview = new JsonObject();
			preview.addProperty("taskId", taskId.toString());
			preview.addProperty("manifestHash", "a".repeat(64));
			JsonArray files = new JsonArray();
			JsonObject file = new JsonObject();
			file.addProperty("path", "src/generated/resources/data/copperbench_eval/generated.json");
			file.addProperty("status", "add");
			files.add(file);
			preview.add("files", files);
			datagenPreviews.computeIfAbsent(workspaceId, ignored -> new LinkedHashMap<>()).put(taskId, preview);
		}
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

	/** Deterministic log hook used by task-event fixtures and headless simulations. */
	void appendLog(UUID workspaceId, UUID taskId, String level, String text) {
		JsonObject snapshot;
		JsonObject entry = new JsonObject();
		synchronized (this) {
			JsonObject task = tasks.getOrDefault(workspaceId, Map.of()).get(taskId);
			if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
			List<JsonObject> currentLogs = logs.computeIfAbsent(workspaceId, ignored -> new LinkedHashMap<>())
					.computeIfAbsent(taskId, ignored -> new ArrayList<>());
			entry.addProperty("sequence", currentLogs.size() + 1L);
			entry.addProperty("timestamp", clock.instant().toString());
			entry.addProperty("level", level);
			entry.addProperty("text", text);
			currentLogs.add(entry.deepCopy());
			snapshot = task.deepCopy();
		}
		publish(new TaskEvent(workspaceId, taskId, "task_log_appended", snapshot, List.of(entry), List.of()));
	}

	@Override public synchronized List<JsonObject> logs(UUID workspaceId, UUID taskId) {
		return logs.getOrDefault(workspaceId, Map.of()).getOrDefault(taskId, List.of()).stream()
				.map(JsonObject::deepCopy).toList();
	}

	@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
		JsonObject snapshot;
		synchronized (this) {
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
			if ("run_datagen".equals(task.get("kind").getAsString()))
				datagenPreviews.getOrDefault(workspaceId, Map.of()).remove(taskId);
			snapshot = task.deepCopy();
		}
		publish(new TaskEvent(workspaceId, taskId, "task_completed", snapshot, List.of(), List.of()));
		return Optional.of(snapshot);
	}

	@Override public AutoCloseable subscribeTaskEvents(Consumer<TaskEvent> listener) {
		listeners.add(listener);
		return () -> listeners.remove(listener);
	}

	private void publish(TaskEvent event) {
		for (Consumer<TaskEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException ignored) {
				// A disconnected observer must not break task state updates.
			}
		}
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
