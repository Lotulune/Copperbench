package dev.copperbench.core.contract;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Java representation of the frozen UI-Core v1.0 envelopes. */
public final class UiCore {

	public static final String SCHEMA_VERSION = "1.0";
	private static final Gson WIRE_GSON = new GsonBuilder().serializeNulls().create();

	private UiCore() {
	}

	/** Serializer required at adapter boundaries because the schema requires explicit null properties. */
	public static Gson wireGson() {
		return WIRE_GSON;
	}

	public enum Operation {
		@SerializedName("create_workspace") CREATE_WORKSPACE,
		@SerializedName("create_mod_element") CREATE_MOD_ELEMENT,
		@SerializedName("update_mod_element") UPDATE_MOD_ELEMENT,
		@SerializedName("delete_mod_element") DELETE_MOD_ELEMENT,
		@SerializedName("update_procedure") UPDATE_PROCEDURE,
		@SerializedName("create_registry_entry") CREATE_REGISTRY_ENTRY,
		@SerializedName("update_registry_entry") UPDATE_REGISTRY_ENTRY,
		@SerializedName("delete_registry_entry") DELETE_REGISTRY_ENTRY,
		@SerializedName("rename_registry_entry") RENAME_REGISTRY_ENTRY,
		@SerializedName("validate_workspace") VALIDATE_WORKSPACE,
		@SerializedName("generate_workspace") GENERATE_WORKSPACE,
		@SerializedName("build_workspace") BUILD_WORKSPACE,
		@SerializedName("export_workspace") EXPORT_WORKSPACE,
		@SerializedName("run_client") RUN_CLIENT,
		@SerializedName("run_server") RUN_SERVER,
		@SerializedName("run_datagen") RUN_DATAGEN,
		@SerializedName("publish_datagen_output") PUBLISH_DATAGEN_OUTPUT,
		@SerializedName("run_gametest") RUN_GAMETEST,
		@SerializedName("cancel_task") CANCEL_TASK,
		@SerializedName("create_recovery_point") CREATE_RECOVERY_POINT,
		@SerializedName("restore_recovery_point") RESTORE_RECOVERY_POINT,
		@SerializedName("execute_loader_migration") EXECUTE_LOADER_MIGRATION,
		@SerializedName("import_upstream_workspace") IMPORT_UPSTREAM_WORKSPACE,
		@SerializedName("create_publish_batch") CREATE_PUBLISH_BATCH,
		@SerializedName("prepare_resource_pack_client") PREPARE_RESOURCE_PACK_CLIENT,
		@SerializedName("get_workbench") GET_WORKBENCH,
		@SerializedName("list_new_workspace_generators") LIST_NEW_WORKSPACE_GENERATORS,
		@SerializedName("list_assets") LIST_ASSETS,
		@SerializedName("list_mod_elements") LIST_MOD_ELEMENTS,
		@SerializedName("get_mod_element_editor") GET_MOD_ELEMENT_EDITOR,
		@SerializedName("preview_mod_element_change") PREVIEW_MOD_ELEMENT_CHANGE,
		@SerializedName("get_procedure_editor") GET_PROCEDURE_EDITOR,
		@SerializedName("preview_procedure_change") PREVIEW_PROCEDURE_CHANGE,
		@SerializedName("get_workspace_references") GET_WORKSPACE_REFERENCES,
		@SerializedName("list_workspace_registries") LIST_WORKSPACE_REGISTRIES,
		@SerializedName("preview_registry_rename") PREVIEW_REGISTRY_RENAME,
		@SerializedName("plan_workspace_changes") PLAN_WORKSPACE_CHANGES,
		@SerializedName("preview_workspace_plan") PREVIEW_WORKSPACE_PLAN,
		@SerializedName("apply_workspace_plan") APPLY_WORKSPACE_PLAN,
		@SerializedName("get_task") GET_TASK,
		@SerializedName("preview_datagen_output") PREVIEW_DATAGEN_OUTPUT,
		@SerializedName("get_history") GET_HISTORY,
		@SerializedName("get_diff") GET_DIFF,
		@SerializedName("list_operation_approvals") LIST_OPERATION_APPROVALS,
		@SerializedName("resolve_operation_approval") RESOLVE_OPERATION_APPROVAL,
		@SerializedName("get_version_tracks") GET_VERSION_TRACKS,
		@SerializedName("get_release_notes") GET_RELEASE_NOTES,
		@SerializedName("preview_loader_migration") PREVIEW_LOADER_MIGRATION,
		@SerializedName("preview_upstream_import") PREVIEW_UPSTREAM_IMPORT,
		@SerializedName("list_publish_batches") LIST_PUBLISH_BATCHES,
		@SerializedName("list_installed_plugins") LIST_INSTALLED_PLUGINS,
		@SerializedName("get_element_coverage") GET_ELEMENT_COVERAGE,
		@SerializedName("get_upstream_tools") GET_UPSTREAM_TOOLS
	}

	public enum Actor {
		@SerializedName("ui") UI,
		@SerializedName("mcp") MCP,
		@SerializedName("headless") HEADLESS,
		@SerializedName("legacy_ui") LEGACY_UI,
		@SerializedName("system") SYSTEM
	}

	public enum PermissionProfile {
		@SerializedName("read_only") READ_ONLY,
		@SerializedName("workspace") WORKSPACE,
		@SerializedName("full_access") FULL_ACCESS
	}

	public enum Severity {
		@SerializedName("info") INFO,
		@SerializedName("warning") WARNING,
		@SerializedName("error") ERROR
	}

	public record LocalizedText(String key, String fallback, JsonObject args) {
		public LocalizedText {
			Objects.requireNonNull(key);
			Objects.requireNonNull(fallback);
			args = args == null ? new JsonObject() : args.deepCopy();
		}

		public static LocalizedText of(String key, String fallback) {
			return new LocalizedText(key, fallback, new JsonObject());
		}

		public static LocalizedText of(String key, String fallback, JsonObject args) {
			return new LocalizedText(key, fallback, args);
		}
	}

	public record ActionHint(String id, LocalizedText label, String kind, String target) {
	}

	public record Diagnostic(String code, Severity severity, LocalizedText message, String path, UUID elementId,
			boolean recoverable, List<ActionHint> actions) {
		public Diagnostic {
			actions = actions == null ? List.of() : List.copyOf(actions);
		}

		public static Diagnostic error(String code, String messageKey, String fallback, String path, UUID elementId) {
			return new Diagnostic(code, Severity.ERROR, LocalizedText.of(messageKey, fallback), path, elementId, true,
					List.of());
		}

		public static Diagnostic error(String code, String messageKey, String fallback, JsonObject args, String path,
				UUID elementId) {
			return new Diagnostic(code, Severity.ERROR, LocalizedText.of(messageKey, fallback, args), path, elementId,
					true, List.of());
		}
	}

	public record Command(String messageType, String schemaVersion, UUID requestId, UUID workspaceId,
			long expectedRevision, Operation operation, JsonObject payload) {
		public Command {
			Objects.requireNonNull(requestId);
			Objects.requireNonNull(workspaceId);
			Objects.requireNonNull(operation);
			messageType = "command";
			schemaVersion = SCHEMA_VERSION;
			payload = payload == null ? new JsonObject() : payload.deepCopy();
			if (expectedRevision < 0)
				throw new IllegalArgumentException("expectedRevision must be non-negative");
		}

		public static Command of(UUID requestId, UUID workspaceId, long expectedRevision, Operation operation,
				JsonObject payload) {
			return new Command("command", SCHEMA_VERSION, requestId, workspaceId, expectedRevision, operation, payload);
		}
	}

	public record Query(String messageType, String schemaVersion, UUID requestId, UUID workspaceId,
			Operation operation, JsonObject payload) {
		public Query {
			Objects.requireNonNull(requestId);
			Objects.requireNonNull(workspaceId);
			Objects.requireNonNull(operation);
			messageType = "query";
			schemaVersion = SCHEMA_VERSION;
			payload = payload == null ? new JsonObject() : payload.deepCopy();
		}

		public static Query of(UUID requestId, UUID workspaceId, Operation operation, JsonObject payload) {
			return new Query("query", SCHEMA_VERSION, requestId, workspaceId, operation, payload);
		}
	}

	public record CommandResult(String messageType, String schemaVersion, UUID requestId, UUID workspaceId,
			Operation operation, String status, long newRevision, String recoveryPointId, JsonElement task,
			JsonElement data, List<Diagnostic> diagnostics, JsonElement conflict, JsonElement denial) {
		public CommandResult {
			messageType = "command_result";
			schemaVersion = SCHEMA_VERSION;
			task = copyOrNull(task);
			data = copyOrNull(data);
			diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
			conflict = copyOrNull(conflict);
			denial = copyOrNull(denial);
		}
	}

	public record QueryResult(String messageType, String schemaVersion, UUID requestId, UUID workspaceId,
			Operation operation, String status, long revision, JsonElement data, List<Diagnostic> diagnostics) {
		public QueryResult {
			messageType = "query_result";
			schemaVersion = SCHEMA_VERSION;
			data = copyOrNull(data);
			diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
		}
	}

	public record Event(String messageType, String schemaVersion, UUID eventId, UUID workspaceId, long revision,
			long sequence, String occurredAt, String event, UUID causedByRequestId, JsonObject payload) {
		public Event {
			messageType = "event";
			schemaVersion = SCHEMA_VERSION;
			payload = payload == null ? new JsonObject() : payload.deepCopy();
		}
	}

	public record CommandOutcome(CommandResult result, List<Event> events) {
		public CommandOutcome {
			events = events == null ? List.of() : List.copyOf(events);
		}
	}

	public record RequestContext(Actor actor, PermissionProfile permission) {
		public RequestContext {
			Objects.requireNonNull(actor);
			Objects.requireNonNull(permission);
		}
	}

	public record Handshake(String messageType, UUID requestId, List<String> supportedSchemaVersions,
			JsonObject client) {
		public Handshake {
			messageType = "handshake";
			Objects.requireNonNull(requestId);
			supportedSchemaVersions = List.copyOf(supportedSchemaVersions);
			client = client.deepCopy();
		}
	}

	public record HandshakeResult(String messageType, UUID requestId, String status, String selectedSchemaVersion,
			List<String> coreSchemaVersions, List<Diagnostic> diagnostics) {
		public HandshakeResult {
			messageType = "handshake_result";
			coreSchemaVersions = List.copyOf(coreSchemaVersions);
			diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
		}
	}

	private static JsonElement copyOrNull(JsonElement value) {
		return value == null || value.isJsonNull() ? JsonNull.INSTANCE : value.deepCopy();
	}
}
