package dev.copperbench.core.application;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.ActionHint;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.CommandResult;
import dev.copperbench.core.contract.UiCore.Diagnostic;
import dev.copperbench.core.contract.UiCore.Event;
import dev.copperbench.core.contract.UiCore.LocalizedText;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore.Decision;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore.TransactionResult;
import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.assets.AssetPublishBatchService;
import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetDiagnostic;
import dev.copperbench.assets.AssetPathViolationException;
import dev.copperbench.assets.AssetReference;
import dev.copperbench.assets.AssetReferenceGraph;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.assets.ResourcePackClientLoadService;
import dev.copperbench.assets.ResourcePackExportService;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;
import dev.copperbench.history.RestoreResult;
import dev.copperbench.history.WorkspaceChange;
import dev.copperbench.migration.LoaderMigrationRebuildService;
import dev.copperbench.migration.LoaderMigrationService;
import dev.copperbench.migration.MigrationReport;
import dev.copperbench.migration.UpstreamWorkspaceImportService;
import dev.copperbench.core.plugin.InstalledPluginInventoryService;
import dev.copperbench.release.ElementCoverageCatalog;
import dev.copperbench.release.ReleaseManifest;
import dev.copperbench.release.UpstreamToolCatalog;
import dev.copperbench.procedure.ProcedureIr;
import dev.copperbench.procedure.ProcedureIrCodec;
import dev.copperbench.references.WorkspaceReferenceIndex;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Shared command/query service for legacy UI, JCEF, MCP and headless adapters. */
public final class WorkspaceApplicationService {

	private static final Gson GSON = new Gson();
	private static final Logger LOG = LogManager.getLogger(WorkspaceApplicationService.class);
	private static final Marker OPERATION_FAILURE = MarkerManager.getMarker("COPPERBENCH_OPERATION_FAILURE");
	private static final Pattern ELEMENT_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
	private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");
	private static final Pattern RESOURCE_PATH = Pattern.compile("^[a-z0-9_./-]+$");
	private static final Pattern LANGUAGE_KEY = Pattern.compile("^[a-z0-9_.-]+$");
	private static final Set<String> REGISTRY_NAMES = Set.of("variables", "tags", "languageKeys");
	private static final Set<String> ELEMENT_TYPES = Set.copyOf(ElementCoverageCatalog.FIRST_PARTY_SLICE);
	private static final int TASK_EVENT_HISTORY_LIMIT = 2048;
	private static final ProcedureIrCodec PROCEDURES = new ProcedureIrCodec();

	private final RevisionedWorkspaceStore store;
	private final WorkspaceTaskGateway tasks;
	private final WorkspaceMutationGateway mutations;
	private final LocalHistoryService history;
	private final WorkspaceStateReloader reloader;
	private final VersionTrackCatalog tracks;
	private final LoaderMigrationService migrations;
	private final LoaderMigrationRebuildService rebuilds;
	private final UpstreamWorkspaceImportService imports;
	private final Function<UUID, Path> workspaceRoots;
	private final Clock clock;
	private final Supplier<UUID> ids;
	private final InstalledPluginInventoryService installedPlugins;
	private final WorkspaceCreationService workspaceCreation;
	private final WorkspacePlanEngine plans;
	private final WorkspaceReferenceIndex references = new WorkspaceReferenceIndex();
	private final Map<UUID, CopyOnWriteArrayList<Consumer<Event>>> eventListeners = new ConcurrentHashMap<>();
	private final Map<UUID, Deque<Event>> taskEventHistory = new ConcurrentHashMap<>();

	public WorkspaceApplicationService(RevisionedWorkspaceStore store, WorkspaceTaskGateway tasks, Clock clock,
			Supplier<UUID> ids) {
		this(store, tasks, WorkspaceMutationGateway.noOp(), clock, ids);
	}

	public WorkspaceApplicationService(RevisionedWorkspaceStore store, WorkspaceTaskGateway tasks,
			WorkspaceMutationGateway mutations, Clock clock, Supplier<UUID> ids) {
		this(store, tasks, mutations, null, null, clock, ids);
	}

	public WorkspaceApplicationService(RevisionedWorkspaceStore store, WorkspaceTaskGateway tasks,
			WorkspaceMutationGateway mutations, LocalHistoryService history, WorkspaceStateReloader reloader,
			Clock clock, Supplier<UUID> ids) {
		this(store, tasks, mutations, history, reloader, null, clock, ids);
	}

	public WorkspaceApplicationService(RevisionedWorkspaceStore store, WorkspaceTaskGateway tasks,
			WorkspaceMutationGateway mutations, LocalHistoryService history, WorkspaceStateReloader reloader,
			Function<UUID, Path> workspaceRoots, Clock clock, Supplier<UUID> ids) {
		this(store, tasks, mutations, history, reloader, workspaceRoots, clock, ids, true);
	}

	WorkspaceApplicationService(RevisionedWorkspaceStore store, WorkspaceTaskGateway tasks,
			WorkspaceMutationGateway mutations, LocalHistoryService history, WorkspaceStateReloader reloader,
			Function<UUID, Path> workspaceRoots, Clock clock, Supplier<UUID> ids, boolean subscribeTaskEvents) {
		this.store = store;
		this.tasks = tasks;
		this.mutations = mutations;
		this.history = history;
		this.reloader = reloader;
		this.tracks = VersionTrackCatalog.official();
		this.migrations = new LoaderMigrationService(this.tracks);
		this.rebuilds = new LoaderMigrationRebuildService(this.tracks, Path.of(".").toAbsolutePath().normalize());
		this.imports = new UpstreamWorkspaceImportService();
		this.workspaceRoots = workspaceRoots;
		this.clock = clock;
		this.ids = ids;
		this.installedPlugins = InstalledPluginInventoryService.productDefault();
		this.workspaceCreation = new WorkspaceCreationService(this.tracks);
		this.plans = new WorkspacePlanEngine(store, tasks, mutations, history, clock, ids);
		if (subscribeTaskEvents)
			tasks.subscribeTaskEvents(this::publishTaskEvent);
	}

	/**
	 * Subscribes to retained asynchronous task events. The returned handle is
	 * idempotent and may be closed when a browser page or client disconnects.
	 */
	public AutoCloseable subscribeEvents(UUID workspaceId, long afterSequence, Consumer<Event> listener) {
		if (afterSequence < 0)
			throw new IllegalArgumentException("afterSequence must be non-negative");
		if (listener == null)
			throw new NullPointerException("listener");
		if (store.read(workspaceId).isEmpty())
			throw new IllegalArgumentException("Workspace not found: " + workspaceId);
		CopyOnWriteArrayList<Consumer<Event>> listeners = eventListeners.computeIfAbsent(workspaceId,
				ignored -> new CopyOnWriteArrayList<>());
		Deque<Event> history = taskEventHistory.computeIfAbsent(workspaceId, ignored -> new ArrayDeque<>());
		synchronized (history) {
			listeners.add(listener);
			try {
				for (Event event : history)
					if (event.sequence() > afterSequence)
						listener.accept(event);
			} catch (RuntimeException exception) {
				listeners.remove(listener);
				throw exception;
			}
		}
		return () -> listeners.remove(listener);
	}

	private void publishTaskEvent(WorkspaceTaskGateway.TaskEvent taskEvent) {
		JsonObject payload = new JsonObject();
		switch (taskEvent.event()) {
			case "task_progressed", "task_completed" -> {
				if (taskEvent.task() == null)
					return;
				payload.add("task", taskEvent.task());
			}
			case "task_log_appended" -> {
				payload.addProperty("taskId", taskEvent.taskId().toString());
				payload.add("entries", GSON.toJsonTree(taskEvent.entries()));
			}
			case "diagnostics_changed" -> {
				JsonObject counts = taskEvent.task() != null && taskEvent.task().has("diagnostics")
						? taskEvent.task().getAsJsonObject("diagnostics") : counts();
				payload.add("counts", counts.deepCopy());
				payload.add("diagnostics", GSON.toJsonTree(taskEvent.diagnostics()));
			}
			default -> {
				return;
			}
		}
		WorkspaceState state = store.read(taskEvent.workspaceId()).orElse(null);
		if (state == null)
			return;
		RevisionedWorkspaceStore.TransactionResult<Long> coordinated = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			state = store.read(taskEvent.workspaceId()).orElse(null);
			if (state == null)
				return;
			coordinated = store.coordinate(taskEvent.workspaceId(), state.revision(), WorkspaceState::nextEventSequence);
			if (coordinated.status() == RevisionedWorkspaceStore.TransactionResult.Status.COORDINATED)
				break;
		}
		if (coordinated == null || coordinated.status() != RevisionedWorkspaceStore.TransactionResult.Status.COORDINATED)
			return;
		Event event = new Event("event", UiCore.SCHEMA_VERSION, ids.get(), taskEvent.workspaceId(),
				coordinated.revision(), coordinated.value(), clock.instant().toString(), taskEvent.event(), null, payload);
		Deque<Event> history = taskEventHistory.computeIfAbsent(taskEvent.workspaceId(), ignored -> new ArrayDeque<>());
		CopyOnWriteArrayList<Consumer<Event>> listeners = eventListeners.computeIfAbsent(taskEvent.workspaceId(),
				ignored -> new CopyOnWriteArrayList<>());
		List<Consumer<Event>> recipients;
		synchronized (history) {
			history.addLast(event);
			while (history.size() > TASK_EVENT_HISTORY_LIMIT)
				history.removeFirst();
			// Snapshot live recipients while replay history is locked. A subscriber is
			// therefore either in this live snapshot or replays this event, never both.
			recipients = List.copyOf(listeners);
		}
		for (Consumer<Event> listener : recipients) {
			try {
				listener.accept(event);
			} catch (RuntimeException exception) {
				LOG.debug("Task event listener disconnected", exception);
			}
		}
	}

	public CommandOutcome execute(Command command, RequestContext context) {
		if (context.permission() == PermissionProfile.READ_ONLY
				&& command.operation() != Operation.VALIDATE_WORKSPACE)
			return denied(command, context.permission(), PermissionProfile.WORKSPACE);

		return switch (command.operation()) {
			case CREATE_WORKSPACE -> createWorkspace(command, context);
			case CREATE_MOD_ELEMENT -> create(command, context);
			case UPDATE_MOD_ELEMENT -> update(command, context);
			case DELETE_MOD_ELEMENT -> delete(command, context);
			case UPDATE_PROCEDURE -> updateProcedure(command, context);
			case CREATE_REGISTRY_ENTRY, UPDATE_REGISTRY_ENTRY, DELETE_REGISTRY_ENTRY, RENAME_REGISTRY_ENTRY ->
					mutateRegistry(command, context);
			case VALIDATE_WORKSPACE, GENERATE_WORKSPACE, BUILD_WORKSPACE, EXPORT_WORKSPACE, RUN_CLIENT, RUN_DATAGEN,
					RUN_GAMETEST ->
					startTask(command);
			case RUN_SERVER -> runServer(command, context);
			case PUBLISH_DATAGEN_OUTPUT -> publishDatagenOutput(command, context);
			case CANCEL_TASK -> cancelTask(command);
			case CREATE_RECOVERY_POINT -> createRecoveryPoint(command, context);
			case RESTORE_RECOVERY_POINT -> restoreRecoveryPoint(command, context);
			case EXECUTE_LOADER_MIGRATION -> executeLoaderMigration(command, context);
			case IMPORT_UPSTREAM_WORKSPACE -> importUpstreamWorkspace(command, context);
			case CREATE_PUBLISH_BATCH -> createPublishBatch(command, context);
			case PREPARE_RESOURCE_PACK_CLIENT -> prepareResourcePackClient(command, context);
			case APPLY_WORKSPACE_PLAN -> plans.apply(command, context);
			default -> failed(command, 0, diagnostic("UNSUPPORTED_OPERATION", "diagnostic.unsupported_operation",
					"The requested operation is not supported.", null, null));
		};
	}

	public QueryResult query(Query query, RequestContext context) {
		WorkspaceState state = store.read(query.workspaceId()).orElse(null);
		if (state == null)
			return queryFailure(query, 0, workspaceNotFound());
		try {
			return switch (query.operation()) {
				case GET_WORKBENCH -> querySuccess(query, state.revision(), workbench(state, context));
				case LIST_NEW_WORKSPACE_GENERATORS -> querySuccess(query, state.revision(), newWorkspaceGenerators());
				case LIST_ASSETS -> listAssets(query, state);
				case LIST_MOD_ELEMENTS -> querySuccess(query, state.revision(), elementList(state, query.payload()));
				case GET_MOD_ELEMENT_EDITOR -> editor(query, state, context);
				case PREVIEW_MOD_ELEMENT_CHANGE -> preview(query, state);
				case GET_PROCEDURE_EDITOR -> procedureEditor(query, state, context);
				case PREVIEW_PROCEDURE_CHANGE -> previewProcedure(query, state);
				case GET_WORKSPACE_REFERENCES -> workspaceReferences(query, state);
				case LIST_WORKSPACE_REGISTRIES -> listRegistries(query, state);
				case PREVIEW_REGISTRY_RENAME -> previewRegistryRename(query, state);
				case PLAN_WORKSPACE_CHANGES -> plans.plan(query, context);
				case PREVIEW_WORKSPACE_PLAN -> plans.preview(query, context);
				case GET_TASK -> task(query, state);
				case PREVIEW_DATAGEN_OUTPUT -> previewDatagenOutput(query, state);
				case GET_HISTORY -> historyList(query, state);
				case GET_DIFF -> historyDiff(query, state);
				case GET_VERSION_TRACKS -> querySuccess(query, state.revision(), versionTracks(state));
				case GET_RELEASE_NOTES -> querySuccess(query, state.revision(), ReleaseManifest.official());
				case PREVIEW_LOADER_MIGRATION -> previewLoaderMigration(query, state);
				case PREVIEW_UPSTREAM_IMPORT -> previewUpstreamImport(query, state);
				case LIST_PUBLISH_BATCHES -> listPublishBatches(query, state);
				case LIST_INSTALLED_PLUGINS -> querySuccess(query, state.revision(), installedPlugins.list());
				case GET_ELEMENT_COVERAGE -> querySuccess(query, state.revision(), ElementCoverageCatalog.toJson());
				case GET_UPSTREAM_TOOLS -> querySuccess(query, state.revision(), UpstreamToolCatalog.toJson());
				default -> queryFailure(query, state.revision(), diagnostic("UNSUPPORTED_OPERATION",
						"diagnostic.unsupported_operation", "The requested operation is not supported.", null, null));
			};
		} catch (ListCursorException exception) {
			return queryFailure(query, state.revision(), diagnostic(exception.code(),
					"diagnostic.list_cursor_invalid", exception.getMessage(), null, null));
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), invalidPayload(exception.getMessage()));
		}
	}

	private QueryResult listAssets(Query query, WorkspaceState state) {
		Path root = workspaceRoot(query.workspaceId());
		if (root == null)
			return queryFailure(query, state.revision(), diagnostic("ASSET_WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.asset_workspace_root_unavailable",
					"The workspace root is not available for asset indexing.", null, null));
		try {
			AssetReferenceGraph graph = new AssetWorkspaceService(root).referenceGraph();
			JsonObject projection = new JsonObject();
			projection.addProperty("schemaVersion", UiCore.SCHEMA_VERSION);
			projection.add("assets", GSON.toJsonTree(graph.assets().stream().map(WorkspaceApplicationService::asset).toList()));
			projection.add("references", GSON.toJsonTree(graph.references().stream()
					.map(WorkspaceApplicationService::assetReference).toList()));
			projection.add("diagnostics", GSON.toJsonTree(graph.diagnostics().stream()
					.map(WorkspaceApplicationService::assetDiagnostic).toList()));
			return querySuccess(query, state.revision(), projection);
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), failureDiagnostic(query, "ASSET_QUERY_FAILED",
					"diagnostic.asset_query_failed", "The workspace asset index could not be read.", null, null,
					exception));
		}
	}

	private static JsonObject asset(AssetDescriptor descriptor) {
		JsonObject value = new JsonObject();
		value.addProperty("id", descriptor.id());
		value.addProperty("relativePath", descriptor.relativePath());
		value.addProperty("category", descriptor.category().name());
		value.addProperty("size", descriptor.size());
		value.addProperty("sha256", descriptor.sha256());
		value.addProperty("mediaType", descriptor.mediaType());
		value.addProperty("updatedAt", descriptor.updatedAt().toString());
		return value;
	}

	private static JsonObject assetReference(AssetReference reference) {
		JsonObject value = new JsonObject();
		value.addProperty("sourceAssetId", reference.sourceAssetId());
		value.addProperty("sourcePath", reference.sourcePath());
		value.addProperty("targetPath", reference.targetPath());
		value.addProperty("targetAssetId", reference.targetAssetId());
		value.addProperty("kind", reference.kind().name());
		return value;
	}

	private static JsonObject assetDiagnostic(AssetDiagnostic diagnostic) {
		JsonObject value = new JsonObject();
		value.addProperty("code", diagnostic.code());
		value.addProperty("severity", diagnostic.severity().name());
		value.addProperty("sourcePath", diagnostic.sourcePath());
		if (diagnostic.targetPath() == null)
			value.add("targetPath", JsonNull.INSTANCE);
		else
			value.addProperty("targetPath", diagnostic.targetPath());
		value.addProperty("message", diagnostic.message());
		return value;
	}

	private CommandOutcome createWorkspace(Command command, RequestContext context) {
		if (!approved(command, context))
			return approvalRequired(command, context, "Creating a workspace writes a new folder and requires confirmation.");
		String generatorId;
		String modName;
		String modId;
		String packageName;
		String workspaceFolderPath;
		try {
			generatorId = requiredString(command.payload(), "generatorId");
			modName = requiredString(command.payload(), "modName");
			modId = requiredString(command.payload(), "modId");
			packageName = optionalString(command.payload(), "packageName");
			workspaceFolderPath = requiredString(command.payload(), "workspaceFolderPath");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		if (packageName == null || packageName.isBlank())
			packageName = "net.mcreator." + modId.replaceAll("[^a-z0-9_]", "");
		String version = optionalString(command.payload(), "version");
		String effectivePackageName = packageName;
		TransactionResult<WorkspaceCreationMutation> coordinated;
		try {
			coordinated = store.coordinate(command.workspaceId(), command.expectedRevision(), state -> {
				WorkspaceCreationService.CreationResult created = workspaceCreation.create(generatorId, modName, modId,
						effectivePackageName, workspaceFolderPath, version);
				return new WorkspaceCreationMutation(created, created.complete() ? state.nextEventSequence() : 0);
			});
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"WORKSPACE_CREATE_FAILED",
					"diagnostic.workspace_create_failed", "The workspace could not be created.", "/workspaceFolderPath",
					null, exception));
		}
		CommandOutcome conflict = checkFailure(command, coordinated);
		if (conflict != null)
			return conflict;
		WorkspaceCreationService.CreationResult created = coordinated.value().creation();
		if (!created.complete()) {
			List<Diagnostic> diagnostics = new ArrayList<>();
			for (String code : created.diagnostics())
				diagnostics.add(diagnostic(code, "diagnostic." + code.toLowerCase(Locale.ROOT),
						workspaceCreationFallback(code), workspaceCreationPath(code), null));
			return new CommandOutcome(result(command, "rejected", currentRevision(command.workspaceId()),
					JsonNull.INSTANCE, JsonNull.INSTANCE, diagnostics, JsonNull.INSTANCE, JsonNull.INSTANCE),
					List.of());
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("workspaceFile", created.workspaceFile());
		payload.addProperty("generatorId", created.generatorId());
		payload.addProperty("modId", modId);
		Event event = event(command, coordinated.revision(), coordinated.value().sequence(), "workspace_created", payload);
		return new CommandOutcome(result(command, "committed", coordinated.revision(), JsonNull.INSTANCE,
				payload.deepCopy(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
	}

	private static String workspaceCreationFallback(String code) {
		return switch (code) {
			case "UNSUPPORTED_GENERATOR" -> "The selected generator is not supported.";
			case "GENERATOR_NOT_INSTALLED" -> "The selected generator plugin is not installed.";
			case "MOD_NAME_INVALID" -> "The mod name is invalid.";
			case "MOD_ID_INVALID" -> "The mod ID is invalid.";
			case "PACKAGE_NAME_INVALID" -> "The Java package name is invalid.";
			case "WORKSPACE_FOLDER_REQUIRED" -> "A workspace folder is required.";
			case "WORKSPACE_FOLDER_OUTSIDE_ROOT" -> "The workspace folder is outside the allowed root.";
			case "WORKSPACE_FOLDER_NOT_EMPTY" -> "The workspace folder is not empty.";
			default -> "The new workspace form is invalid: " + code + ".";
		};
	}

	private static String workspaceCreationPath(String code) {
		return switch (code) {
			case "UNSUPPORTED_GENERATOR", "GENERATOR_NOT_INSTALLED" -> "/generatorId";
			case "MOD_NAME_INVALID" -> "/modName";
			case "MOD_ID_INVALID" -> "/modId";
			case "PACKAGE_NAME_INVALID" -> "/packageName";
			case "WORKSPACE_FOLDER_REQUIRED", "WORKSPACE_FOLDER_OUTSIDE_ROOT", "WORKSPACE_FOLDER_NOT_EMPTY" ->
					"/workspaceFolderPath";
			default -> null;
		};
	}

	private CommandOutcome create(Command command, RequestContext context) {
		JsonObject payload = command.payload();
		String type;
		String name;
		JsonObject initialValues;
		try {
			type = requiredString(payload, "elementType");
			name = requiredString(payload, "name");
			initialValues = payload.getAsJsonObject("initialValues").deepCopy();
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		if (!ELEMENT_TYPES.contains(type) || !ELEMENT_NAME.matcher(name).matches())
			return failed(command, currentRevision(command.workspaceId()), diagnostic("MOD_ELEMENT_INVALID_IDENTITY",
					"diagnostic.mod_element_invalid_identity", "Element type or name is invalid.", "/name", null));
		JsonObject normalizedValues = defaultElementValues(type, name, initialValues);
		RecoveryPoint recoveryPoint;
		try {
			recoveryPoint = automationRecoveryPoint(command, context);
		} catch (LocalHistoryException exception) {
			return automationRecoveryFailed(command, exception);
		}

		TransactionResult<Mutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			if (state.hasElementName(name))
				return Decision.abort(Mutation.rejected(diagnostic("MOD_ELEMENT_NAME_CONFLICT",
						"diagnostic.mod_element_name_conflict", "An element with this name already exists.", "/name", null)));
			UUID elementId = ids.get();
			String displayName = normalizedValues.has("displayName") ? normalizedValues.get("displayName").getAsString()
					: displayName(name);
			Element element = new Element(elementId, type, name, displayName, "draft", "generated", clock.instant(),
					normalizedValues);
			state.addElement(element);
			Diagnostic persistenceFailure = persist(before, state, command, element);
			if (persistenceFailure != null)
				return Decision.abort(Mutation.rejected(persistenceFailure));
			return Decision.commit(Mutation.success(element, state.nextEventSequence()), List.of(elementPath(elementId)));
		});
		return mutationOutcome(command, context, transaction, "mod_element_created", recoveryPoint);
	}

	private CommandOutcome update(Command command, RequestContext context) {
		UUID elementId;
		JsonArray changes;
		try {
			elementId = UUID.fromString(requiredString(command.payload(), "elementId"));
			changes = command.payload().getAsJsonArray("changes").deepCopy();
			if (changes.isEmpty())
				throw new IllegalArgumentException("changes must not be empty");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		RecoveryPoint recoveryPoint;
		try {
			recoveryPoint = automationRecoveryPoint(command, context);
		} catch (LocalHistoryException exception) {
			return automationRecoveryFailed(command, exception);
		}

		TransactionResult<Mutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			Element existing = state.element(elementId);
			if (existing == null)
				return Decision.abort(Mutation.rejected(elementNotFound(elementId)));
			if (!ElementCoverageCatalog.isFirstParty(existing.type()))
				return Decision.abort(Mutation.rejected(diagnostic("ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE",
						"diagnostic.element_type_outside_first_party_slice",
						"This element type is outside the supported Java catalog and cannot be updated in the new UI.",
						"/elementId", elementId)));
			JsonObject values = existing.values().deepCopy();
			List<String> changedPaths = new ArrayList<>();
			try {
				for (JsonElement rawChange : changes) {
					JsonObject change = rawChange.getAsJsonObject();
					String pointer = requiredString(change, "path");
					JsonElement value = change.has("value") ? change.get("value") : JsonNull.INSTANCE;
					JsonPointerPatch.set(values, pointer, value);
					changedPaths.add(elementPath(elementId) + pointer);
				}
			} catch (RuntimeException exception) {
				return Decision.abort(Mutation.rejected(invalidPayload(exception.getMessage())));
			}
			Diagnostic validation = validateElementValues(elementId, values);
			if (validation != null)
				return Decision.abort(Mutation.rejected(validation));
			String updatedDisplayName = values.has("displayName") && values.get("displayName").isJsonPrimitive()
					? values.get("displayName").getAsString() : existing.displayName();
			Element updated = new Element(existing.id(), existing.type(), existing.name(), updatedDisplayName,
					"valid", existing.ownership(), clock.instant(), values);
			state.replaceElement(updated);
			Diagnostic persistenceFailure = persist(before, state, command, updated);
			if (persistenceFailure != null)
				return Decision.abort(Mutation.rejected(persistenceFailure));
			return Decision.commit(Mutation.success(updated, state.nextEventSequence()), changedPaths);
		});
		return mutationOutcome(command, context, transaction, "mod_element_updated", recoveryPoint);
	}

	private CommandOutcome updateProcedure(Command command, RequestContext context) {
		UUID elementId;
		JsonArray edits;
		try {
			elementId = UUID.fromString(requiredString(command.payload(), "elementId"));
			edits = command.payload().getAsJsonArray("edits").deepCopy();
			if (edits.isEmpty()) throw new IllegalArgumentException("edits must not be empty");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		RecoveryPoint recoveryPoint;
		try {
			recoveryPoint = procedureRecoveryPoint(command, context, edits.size());
		} catch (LocalHistoryException exception) {
			return automationRecoveryFailed(command, exception);
		}

		TransactionResult<Mutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			Element existing = state.element(elementId);
			if (existing == null) return Decision.abort(Mutation.rejected(elementNotFound(elementId)));
			if (!existing.type().equals("procedure"))
				return Decision.abort(Mutation.rejected(diagnostic("PROCEDURE_ELEMENT_REQUIRED",
						"diagnostic.procedure_element_required", "The requested element is not a Procedure.",
						"/elementId", elementId)));
			ProcedureIr candidate;
			try {
				candidate = PROCEDURES.applyEdits(PROCEDURES.read(existing.values(), elementId), edits);
			} catch (RuntimeException exception) {
				return Decision.abort(Mutation.rejected(invalidPayload(exception.getMessage())));
			}
			List<ProcedureIr.ValidationIssue> issues = PROCEDURES.validate(candidate);
			JsonObject values = existing.values().deepCopy();
			values.add("procedureIr", PROCEDURES.toJson(candidate));
			values.addProperty("procedurexml", PROCEDURES.toBlocklyXml(candidate));
			Element updated = new Element(existing.id(), existing.type(), existing.name(), existing.displayName(),
					issues.stream().anyMatch(ProcedureIr.ValidationIssue::error) ? "invalid" : "valid",
					existing.ownership(), clock.instant(), values);
			state.replaceElement(updated);
			Diagnostic persistenceFailure = persist(before, state, command, updated);
			if (persistenceFailure != null) return Decision.abort(Mutation.rejected(persistenceFailure));
			return Decision.commit(Mutation.success(updated, state.nextEventSequence()),
					List.of(elementPath(elementId) + "/procedureIr", elementPath(elementId) + "/procedurexml"));
		});
		return mutationOutcome(command, context, transaction, "procedure_updated", recoveryPoint);
	}

	private RecoveryPoint procedureRecoveryPoint(Command command, RequestContext context, int editCount)
			throws LocalHistoryException {
		RecoveryPoint automated = automationRecoveryPoint(command, context);
		if (automated != null || history == null || editCount < 10) return automated;
		WorkspaceState current = store.read(command.workspaceId()).orElse(null);
		if (current == null || current.revision() != command.expectedRevision()) return null;
		String taskId = command.payload().has("clientMutationId")
				? command.payload().get("clientMutationId").getAsString() : command.requestId().toString();
		return history.createRecoveryPoint(new RecoveryPointRequest("Before large Procedure edit", context.actor(), taskId));
	}

	private CommandOutcome delete(Command command, RequestContext context) {
		UUID elementId;
		try {
			elementId = UUID.fromString(requiredString(command.payload(), "elementId"));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		RecoveryPoint recoveryPoint;
		try {
			recoveryPoint = automationRecoveryPoint(command, context);
		} catch (LocalHistoryException exception) {
			return automationRecoveryFailed(command, exception);
		}

		TransactionResult<Mutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			Element removed = state.removeElement(elementId);
			if (removed == null)
				return Decision.abort(Mutation.rejected(elementNotFound(elementId)));
			Diagnostic persistenceFailure = persist(before, state, command, removed);
			if (persistenceFailure != null)
				return Decision.abort(Mutation.rejected(persistenceFailure));
			return Decision.commit(Mutation.success(removed, state.nextEventSequence()), List.of(elementPath(elementId)));
		});
		return deleteOutcome(command, context, transaction, recoveryPoint);
	}

	private RecoveryPoint automationRecoveryPoint(Command command, RequestContext context)
			throws LocalHistoryException {
		if (context.actor() != UiCore.Actor.MCP || history == null) return null;
		WorkspaceState current = store.read(command.workspaceId()).orElse(null);
		if (current == null || current.revision() != command.expectedRevision()) return null;
		String taskId = command.payload().has("clientMutationId")
				? command.payload().get("clientMutationId").getAsString() : command.requestId().toString();
		return history.createRecoveryPoint(new RecoveryPointRequest(
				"Before MCP " + command.operation().name().toLowerCase(Locale.ROOT), context.actor(), taskId));
	}

	private CommandOutcome automationRecoveryFailed(Command command, Throwable cause) {
		return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command, "RECOVERY_POINT_FAILED",
				"diagnostic.recovery_point_failed",
				"The required recovery point could not be created; the workspace was not changed.", null, null, cause));
	}

	private Diagnostic persist(WorkspaceState before, WorkspaceState after, Command command, Element element) {
		try {
			mutations.persist(before, after, command.operation(), element);
			return null;
		} catch (Exception exception) {
			return failureDiagnostic(command, "WORKSPACE_PERSISTENCE_FAILED", "diagnostic.workspace_persistence_failed",
					"The workspace change could not be stored and was rolled back.", null, null, exception);
		}
	}

	private CommandOutcome startTask(Command command) {
		TransactionResult<TaskMutation> check;
		try {
			check = store.coordinate(command.workspaceId(), command.expectedRevision(), state ->
					new TaskMutation(tasks.start(command.workspaceId(), command.operation(), command.payload()),
							state.nextEventSequence()));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command, "TASK_START_FAILED",
					"diagnostic.task_start_failed", "The requested task could not be started.", null, null, exception));
		}
		CommandOutcome rejected = checkFailure(command, check);
		if (rejected != null)
			return rejected;
		JsonObject task = check.value().task();
		JsonObject eventPayload = new JsonObject();
		eventPayload.add("task", task.deepCopy());
		Event event = event(command, check.revision(), check.value().sequence(), "task_started", eventPayload);
		CommandResult result = result(command, "accepted", check.revision(), task, JsonNull.INSTANCE, List.of(),
				JsonNull.INSTANCE, JsonNull.INSTANCE);
		return new CommandOutcome(result, List.of(event));
	}

	private CommandOutcome cancelTask(Command command) {
		UUID taskId;
		try {
			taskId = UUID.fromString(requiredString(command.payload(), "taskId"));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		TransactionResult<TaskMutation> check;
		try {
			check = store.coordinate(command.workspaceId(), command.expectedRevision(), state -> {
				JsonObject task = tasks.cancel(command.workspaceId(), taskId).orElse(null);
				return new TaskMutation(task, task == null ? 0 : state.nextEventSequence());
			});
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command, "TASK_CANCEL_FAILED",
					"diagnostic.task_cancel_failed", "The requested task could not be cancelled.", "/taskId", null,
					exception));
		}
		CommandOutcome rejected = checkFailure(command, check);
		if (rejected != null)
			return rejected;
		try {
			JsonObject task = check.value().task();
			if (task == null)
				return failed(command, check.revision(), diagnostic("TASK_NOT_FOUND", "diagnostic.task_not_found",
						"The requested task does not exist.", "/taskId", null));
			JsonObject eventPayload = new JsonObject();
			eventPayload.add("task", task.deepCopy());
			Event event = event(command, check.revision(), check.value().sequence(), "task_completed", eventPayload);
			return new CommandOutcome(result(command, "cancelled", check.revision(), task, JsonNull.INSTANCE,
					List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
		} catch (RuntimeException exception) {
			return failed(command, check.revision(), failureDiagnostic(command, "TASK_CANCEL_FAILED",
					"diagnostic.task_cancel_failed", "The requested task could not be cancelled.", "/taskId", null,
					exception));
		}
	}

	private CommandOutcome createRecoveryPoint(Command command, RequestContext context) {
		if (history == null)
			return failed(command, currentRevision(command.workspaceId()), historyUnavailable());
		String label;
		try {
			label = requiredString(command.payload(), "label");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		TransactionResult<Long> coordinated;
		try {
			coordinated = store.coordinate(command.workspaceId(), command.expectedRevision(),
					state -> state.nextEventSequence());
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"RECOVERY_POINT_FAILED", "diagnostic.recovery_point_failed",
					"The recovery point could not be created.", null, null, exception));
		}
		CommandOutcome conflict = checkFailure(command, coordinated);
		if (conflict != null)
			return conflict;
		RecoveryPoint point;
		try {
			point = history.createRecoveryPoint(new RecoveryPointRequest(label, context.actor(), ""));
		} catch (LocalHistoryException | RuntimeException exception) {
			return failed(command, coordinated.revision(), failureDiagnostic(command, "RECOVERY_POINT_FAILED",
					"diagnostic.recovery_point_failed", "The recovery point could not be created.", null, null,
					exception));
		}
		JsonObject payload = new JsonObject();
		payload.add("recoveryPoint", recoveryPoint(point));
		Event event = event(command, coordinated.revision(), coordinated.value(), "recovery_point_created", payload);
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "committed", coordinated.revision(), point.id(),
				JsonNull.INSTANCE, payload.deepCopy(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE);
		return new CommandOutcome(result, List.of(event));
	}

	private CommandOutcome restoreRecoveryPoint(Command command, RequestContext context) {
		if (history == null || reloader == null)
			return failed(command, currentRevision(command.workspaceId()), historyUnavailable());
		String pointId;
		try {
			pointId = requiredString(command.payload(), "recoveryPointId");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		if (!approved(command, context)) {
			JsonObject denial = new JsonObject();
			denial.addProperty("currentProfile", wire(context.permission()));
			denial.addProperty("requiredProfile", wire(context.permission()));
			denial.addProperty("approvalRequired", true);
			denial.addProperty("protectedOperation", true);
			Diagnostic diagnostic = diagnostic("USER_APPROVAL_REQUIRED", "diagnostic.user_approval_required",
					"Restoring a recovery point is a protected operation and requires explicit user confirmation.",
					null, null);
			return new CommandOutcome(result(command, "rejected", currentRevision(command.workspaceId()),
					JsonNull.INSTANCE, JsonNull.INSTANCE, List.of(diagnostic), JsonNull.INSTANCE, denial), List.of());
		}
		TransactionResult<RevisionedWorkspaceStore.Replacement> transaction;
		try {
			transaction = store.restore(command.workspaceId(), command.expectedRevision(), newRevision -> {
				RecoveryPoint safetyPoint = history.createRecoveryPoint(new RecoveryPointRequest(
						"Before restoring " + pointId, context.actor(), ""));
				boolean restoreStarted = false;
				try {
					restoreStarted = true;
					RestoreResult restored = history.restore(pointId);
					WorkspaceState reloaded = reloader.reload(command.workspaceId());
					mutations.persistRestoredRevision(reloaded, newRevision);
					return new RevisionedWorkspaceStore.Restoration(reloaded, restored.changedPaths());
				} catch (Exception exception) {
					if (restoreStarted) {
						try {
							history.restore(safetyPoint.id());
							WorkspaceState rolledBack = reloader.reload(command.workspaceId());
							mutations.persistRestoredRevision(rolledBack, command.expectedRevision());
						} catch (Exception rollbackFailure) {
							exception.addSuppressed(rollbackFailure);
						}
					}
					throw exception;
				}
			});
		} catch (Exception exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"RECOVERY_POINT_RESTORE_FAILED", "diagnostic.recovery_point_restore_failed",
					"The workspace could not be restored.", null, null, exception));
		}
		CommandOutcome conflict = checkFailure(command, transaction);
		if (conflict != null)
			return conflict;
		JsonObject payload = new JsonObject();
		payload.addProperty("recoveryPointId", pointId);
		payload.addProperty("actor", wire(context.actor()));
		JsonArray paths = new JsonArray();
		transaction.value().changedPaths().forEach(paths::add);
		payload.add("changedPaths", paths);
		Event event = event(command, transaction.revision(), transaction.value().sequence(), "workspace_restored",
				payload);
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "committed", transaction.revision(), pointId,
				JsonNull.INSTANCE, payload.deepCopy(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE);
		return new CommandOutcome(result, List.of(event));
	}

	private CommandOutcome executeLoaderMigration(Command command, RequestContext context) {
		if (!approved(command, context))
			return approvalRequired(command, context, "Migrating a loader creates a workspace copy and requires confirmation.");
		String targetGeneratorId;
		String outputName;
		try {
			targetGeneratorId = requiredString(command.payload(), "targetGeneratorId");
			outputName = requiredString(command.payload(), "outputName");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		WorkspaceState source = store.read(command.workspaceId()).orElse(null);
		if (source == null)
			return failed(command, 0, workspaceNotFound());
		Path sourceRoot = workspaceRoot(command.workspaceId());
		Path targetRoot;
		try {
			targetRoot = siblingOutput(command.workspaceId(), outputName);
		} catch (RuntimeException exception) {
			return failed(command, source.revision(), failureDiagnostic(command, "WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.workspace_root_unavailable", "The workspace root is not available.", null, null,
					exception));
		}
		try {
			TransactionResult<Long> coordinated = store.coordinate(command.workspaceId(), command.expectedRevision(),
					WorkspaceState::nextEventSequence);
			CommandOutcome conflict = checkFailure(command, coordinated);
			if (conflict != null)
				return conflict;
			MigrationReport report = migrations.execute(source, targetGeneratorId, sourceRoot, targetRoot);
			LoaderMigrationRebuildService.RebuildResult rebuild = null;
			if (report.complete() && report.targetDirectory() != null)
				rebuild = rebuilds.rebuild(source, targetGeneratorId, targetRoot);
			return copyOutcome(command, coordinated.revision(), coordinated.value(), "loader_migration_executed",
					report, rebuild);
		} catch (Exception exception) {
			return failed(command, source.revision(), failureDiagnostic(command, "LOADER_MIGRATION_FAILED",
					"diagnostic.loader_migration_failed",
					"The loader migration could not create a target copy.", null, null, exception));
		}
	}

	private CommandOutcome importUpstreamWorkspace(Command command, RequestContext context) {
		if (context.permission() != PermissionProfile.FULL_ACCESS)
			return denied(command, context.permission(), PermissionProfile.FULL_ACCESS);
		if (!approved(command, context))
			return approvalRequired(command, context, "Importing an upstream workspace copies it and requires confirmation.");
		String sourcePath;
		String outputName;
		try {
			sourcePath = requiredString(command.payload(), "sourceWorkspacePath");
			outputName = requiredString(command.payload(), "outputName");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		Path targetRoot;
		try {
			targetRoot = siblingOutput(command.workspaceId(), outputName);
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"WORKSPACE_ROOT_UNAVAILABLE", "diagnostic.workspace_root_unavailable",
					"The workspace root is not available.", null, null, exception));
		}
		try {
			TransactionResult<Long> coordinated = store.coordinate(command.workspaceId(), command.expectedRevision(),
					WorkspaceState::nextEventSequence);
			CommandOutcome conflict = checkFailure(command, coordinated);
			if (conflict != null)
				return conflict;
			MigrationReport report = imports.execute(Path.of(sourcePath), targetRoot);
			return copyOutcome(command, coordinated.revision(), coordinated.value(), "upstream_workspace_imported",
					report);
		} catch (Exception exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"UPSTREAM_IMPORT_FAILED",
					"diagnostic.upstream_import_failed",
					"The upstream workspace could not be copied.", null, null, exception));
		}
	}

	private CommandOutcome createPublishBatch(Command command, RequestContext context) {
		String name;
		String sourceDirectory;
		String output;
		try {
			name = requiredString(command.payload(), "name");
			sourceDirectory = requiredString(command.payload(), "sourceDirectory");
			output = requiredString(command.payload(), "output");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		try {
			TransactionResult<Long> coordinated = store.coordinate(command.workspaceId(), command.expectedRevision(),
					WorkspaceState::nextEventSequence);
			CommandOutcome conflict = checkFailure(command, coordinated);
			if (conflict != null)
				return conflict;
			AssetPublishBatchService.PublishBatch batch = publishBatches(command.workspaceId())
					.create(name, sourceDirectory, output, context.actor(), command.requestId().toString());
			JsonObject payload = new JsonObject();
			payload.addProperty("complete", true);
			payload.add("batch", batch.toJson());
			Event event = event(command, coordinated.revision(), coordinated.value(), "publish_batch_created", payload);
			return new CommandOutcome(result(command, "committed", coordinated.revision(), JsonNull.INSTANCE,
					payload.deepCopy(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
		} catch (AssetPathViolationException | LocalHistoryException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"PUBLISH_BATCH_FAILED", "diagnostic.publish_batch_failed",
					"The publish batch could not be created.", null, null, exception));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.workspace_root_unavailable", "The workspace root is not available for asset export.",
					null, null, exception));
		}
	}

	private CommandOutcome prepareResourcePackClient(Command command, RequestContext context) {
		String sourceDirectory;
		String zipFileName;
		try {
			sourceDirectory = requiredString(command.payload(), "sourceDirectory");
			zipFileName = requiredString(command.payload(), "zipFileName");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		try {
			TransactionResult<Long> coordinated = store.coordinate(command.workspaceId(), command.expectedRevision(),
					WorkspaceState::nextEventSequence);
			CommandOutcome conflict = checkFailure(command, coordinated);
			if (conflict != null)
				return conflict;
			var preparation = resourcePackClient(command.workspaceId()).prepare(sourceDirectory, zipFileName);
			JsonObject payload = preparation.toJson();
			payload.addProperty("complete", true);
			Event event = event(command, coordinated.revision(), coordinated.value(), "resource_pack_client_prepared",
					payload);
			return new CommandOutcome(result(command, "committed", coordinated.revision(), JsonNull.INSTANCE,
					payload.deepCopy(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
		} catch (AssetPathViolationException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"RESOURCE_PACK_CLIENT_FAILED", "diagnostic.resource_pack_client_failed",
					"The resource pack test client could not be prepared.", null, null, exception));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), failureDiagnostic(command,
					"WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.workspace_root_unavailable", "The workspace root is not available for resource packs.",
					null, null, exception));
		}
	}

	private QueryResult previewLoaderMigration(Query query, WorkspaceState state) {
		try {
			String targetGeneratorId = requiredString(query.payload(), "targetGeneratorId");
			return querySuccess(query, state.revision(), migrations.preview(state, targetGeneratorId).toJson());
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), invalidPayload(exception.getMessage()));
		}
	}

	private QueryResult previewUpstreamImport(Query query, WorkspaceState state) {
		try {
			String sourcePath = requiredString(query.payload(), "sourceWorkspacePath");
			return querySuccess(query, state.revision(), imports.preview(Path.of(sourcePath)).toJson());
		} catch (Exception exception) {
			return queryFailure(query, state.revision(), failureDiagnostic(query, "UPSTREAM_IMPORT_FAILED",
					"diagnostic.upstream_import_failed",
					"The upstream workspace could not be read.", "/sourceWorkspacePath", null, exception));
		}
	}

	private QueryResult listPublishBatches(Query query, WorkspaceState state) {
		try {
			List<AssetPublishBatchService.PublishBatch> batches = publishBatches(state.id()).list();
			JsonObject payload = query.payload();
			if (!cursorListRequested(payload)) {
				JsonObject projection = new JsonObject();
				JsonArray items = new JsonArray();
				batches.forEach(batch -> items.add(batch.toJson()));
				projection.add("items", items);
				return querySuccess(query, state.revision(), projection);
			}

			int limit = listLimit(payload);
			String sort = optionalString(payload, "sort");
			if (sort == null || sort.isBlank()) sort = "-createdAt";
			JsonObject filter = payload.has("filter") && payload.get("filter").isJsonObject()
					? payload.getAsJsonObject("filter") : null;
			String search = filter != null && filter.has("search")
					? requiredString(filter, "search").toLowerCase(Locale.ROOT) : "";
			Set<String> fields = listFields(payload, Set.of("id", "name", "sourceDirectory", "outputPath", "sha256",
					"assetCount", "createdAt", "assets"), "publish batch");
			Comparator<AssetPublishBatchService.PublishBatch> comparator = publishBatchComparator(sort);
			List<AssetPublishBatchService.PublishBatch> filtered = batches.stream()
					.filter(batch -> search.isBlank() || batch.name().toLowerCase(Locale.ROOT).contains(search)
							|| batch.sourceDirectory().toLowerCase(Locale.ROOT).contains(search)
							|| batch.outputPath().toLowerCase(Locale.ROOT).contains(search))
					.sorted(comparator).toList();
			String dataset = batches.stream().map(batch -> batch.id().toString()).sorted()
					.reduce((left, right) -> left + "," + right).orElse("");
			String signature = "publish-batches|" + dataset + "|" + search + "|" + sort + "|"
					+ listFieldSignature(fields) + "|" + limit;
			int from = listCursorOffset(payload, state.revision(), signature, filtered.size());
			int to = Math.min(from + limit, filtered.size());
			JsonArray items = new JsonArray();
			filtered.subList(from, to).forEach(batch -> items.add(projectListFields(batch.toJson(), fields)));
			JsonObject projection = cursorListProjection(items, filtered.size(), limit, state.revision(), to, signature);
			return querySuccess(query, state.revision(), projection);
		} catch (ListCursorException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), failureDiagnostic(query, "PUBLISH_BATCH_FAILED",
					"diagnostic.publish_batch_failed", "Publish batches could not be listed.", null, null, exception));
		}
	}

	private JsonObject newWorkspaceGenerators() {
		return workspaceCreation.toProjection();
	}

	private JsonObject versionTracks(WorkspaceState state) {
		JsonObject projection = tracks.toProjection();
		JsonObject current = state.generator().deepCopy();
		String generatorId = current.has("id") && current.get("id").isJsonPrimitive()
				? current.get("id").getAsString() : "";
		var decision = tracks.decision(generatorId);
		JsonObject overlay = new JsonObject();
		overlay.add("generator", current);
		overlay.addProperty("status", decision.status().name().toLowerCase(Locale.ROOT));
		overlay.addProperty("reasonCode", decision.reasonCode());
		overlay.addProperty("generatable", decision.generatable());
		projection.add("currentWorkspace", overlay);
		return projection;
	}

	private CommandOutcome copyOutcome(Command command, long revision, long sequence, String eventName,
			MigrationReport report) {
		return copyOutcome(command, revision, sequence, eventName, report, null);
	}

	private CommandOutcome copyOutcome(Command command, long revision, long sequence, String eventName,
			MigrationReport report, LoaderMigrationRebuildService.RebuildResult rebuild) {
		JsonObject payload = report.toJson();
		if (rebuild != null)
			payload.add("rebuild", rebuild.toJson());
		Event event = event(command, revision, sequence, eventName, payload);
		String status = report.complete() ? "committed" : "rejected";
		List<Diagnostic> diagnostics = new ArrayList<>();
		if (!report.complete())
			diagnostics.add(diagnostic("MIGRATION_INCOMPLETE", "diagnostic.migration_incomplete",
					"The copy was created or previewed but is not a complete supported migration.", null, null));
		if (rebuild != null && "failed".equals(rebuild.status())) {
			Throwable cause = rebuild.cause() != null ? rebuild.cause()
					: new IllegalStateException(rebuild.reasonCode() + ": " + rebuild.message());
			diagnostics.add(failureDiagnostic(command, "MIGRATION_REBUILD_FAILED",
					"diagnostic.migration_rebuild_failed",
					"The migration target copy was created, but it could not be rebuilt.", null, null, cause));
		}
		if (!report.complete() && report.targetDirectory() == null)
			return new CommandOutcome(result(command, status, revision, JsonNull.INSTANCE, payload, diagnostics,
					JsonNull.INSTANCE, JsonNull.INSTANCE), List.of());
		return new CommandOutcome(result(command, "committed", revision, JsonNull.INSTANCE, payload, diagnostics,
				JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
	}

	private CommandOutcome approvalRequired(Command command, RequestContext context, String fallback) {
		JsonObject denial = new JsonObject();
		denial.addProperty("currentProfile", wire(context.permission()));
		denial.addProperty("requiredProfile", wire(context.permission()));
		denial.addProperty("approvalRequired", true);
		denial.addProperty("protectedOperation", true);
		Diagnostic diagnostic = diagnostic("USER_APPROVAL_REQUIRED", "diagnostic.user_approval_required", fallback,
				null, null);
		return new CommandOutcome(result(command, "rejected", currentRevision(command.workspaceId()),
				JsonNull.INSTANCE, JsonNull.INSTANCE, List.of(diagnostic), JsonNull.INSTANCE, denial), List.of());
	}

	private static boolean approved(Command command, RequestContext context) {
		boolean trustedActor = context.actor() == UiCore.Actor.UI || context.actor() == UiCore.Actor.LEGACY_UI;
		return trustedActor && command.payload().has("userApproved")
				&& command.payload().get("userApproved").isJsonPrimitive()
				&& command.payload().getAsJsonPrimitive("userApproved").isBoolean()
				&& command.payload().getAsJsonPrimitive("userApproved").getAsBoolean();
	}

	private Path workspaceRoot(UUID workspaceId) {
		if (workspaceRoots == null)
			return null;
		return workspaceRoots.apply(workspaceId);
	}

	private Path siblingOutput(UUID workspaceId, String outputName) {
		if (outputName == null || !outputName.matches("^[a-z][a-z0-9_-]{0,63}$"))
			throw new IllegalArgumentException("outputName must be a lowercase identifier");
		Path root = workspaceRoot(workspaceId);
		if (root == null)
			throw new IllegalArgumentException("Workspace root is not available");
		Path parent = root.toAbsolutePath().normalize().getParent();
		if (parent == null)
			throw new IllegalArgumentException("Workspace root has no parent directory");
		Path destination = parent.resolve(outputName).normalize();
		if (!destination.getParent().equals(parent))
			throw new IllegalArgumentException("outputName escapes the workspace parent");
		return destination;
	}

	private AssetPublishBatchService publishBatches(UUID workspaceId) {
		Path root = workspaceRoot(workspaceId);
		if (root == null)
			throw new IllegalStateException("Workspace root is not available");
		AssetWorkspaceService assets = new AssetWorkspaceService(root);
		return new AssetPublishBatchService(assets, new ResourcePackExportService(assets), history, clock);
	}

	private ResourcePackClientLoadService resourcePackClient(UUID workspaceId) {
		Path root = workspaceRoot(workspaceId);
		if (root == null)
			throw new IllegalStateException("Workspace root is not available");
		AssetWorkspaceService assets = new AssetWorkspaceService(root);
		return new ResourcePackClientLoadService(assets, new ResourcePackExportService(assets));
	}

	private QueryResult historyList(Query query, WorkspaceState state) {
		if (history == null)
			return queryFailure(query, state.revision(), historyUnavailable());
		try {
			List<RecoveryPoint> points = history.listRecoveryPoints();
			JsonObject payload = query.payload();
			if (!cursorListRequested(payload)) {
				JsonObject projection = new JsonObject();
				projection.addProperty("currentRevision", state.revision());
				JsonArray items = new JsonArray();
				points.forEach(point -> items.add(recoveryPoint(point)));
				projection.add("recoveryPoints", items);
				return querySuccess(query, state.revision(), projection);
			}

			int limit = listLimit(payload);
			String sort = optionalString(payload, "sort");
			if (sort == null || sort.isBlank()) sort = "-createdAt";
			JsonObject filter = payload.has("filter") && payload.get("filter").isJsonObject()
					? payload.getAsJsonObject("filter") : null;
			String search = filter != null && filter.has("search")
					? requiredString(filter, "search").toLowerCase(Locale.ROOT) : "";
			String actor = filter != null && filter.has("actor") ? requiredString(filter, "actor") : "";
			Set<String> fields = listFields(payload, Set.of("id", "label", "actor", "taskId", "createdAt"),
					"recovery point");
			Comparator<RecoveryPoint> comparator = recoveryPointComparator(sort);
			List<RecoveryPoint> filtered = points.stream()
					.filter(point -> search.isBlank() || point.label().toLowerCase(Locale.ROOT).contains(search)
							|| point.taskId().toLowerCase(Locale.ROOT).contains(search))
					.filter(point -> actor.isBlank() || wire(point.actor()).equals(actor))
					.sorted(comparator).toList();
			String dataset = points.stream().map(RecoveryPoint::id).sorted()
					.reduce((left, right) -> left + "," + right).orElse("");
			String signature = "history|" + dataset + "|" + search + "|" + actor + "|" + sort + "|"
					+ listFieldSignature(fields) + "|" + limit;
			int from = listCursorOffset(payload, state.revision(), signature, filtered.size());
			int to = Math.min(from + limit, filtered.size());
			JsonArray items = new JsonArray();
			filtered.subList(from, to).forEach(point -> items.add(projectListFields(recoveryPoint(point), fields)));
			JsonObject projection = cursorListProjection(items, filtered.size(), limit, state.revision(), to, signature);
			projection.addProperty("currentRevision", state.revision());
			projection.add("recoveryPoints", projection.remove("items"));
			return querySuccess(query, state.revision(), projection);
		} catch (ListCursorException exception) {
			throw exception;
		} catch (LocalHistoryException exception) {
			return queryFailure(query, state.revision(), failureDiagnostic(query, "HISTORY_READ_FAILED",
					"diagnostic.history_read_failed", "Local history could not be read.", null, null, exception));
		}
	}

	private QueryResult historyDiff(Query query, WorkspaceState state) {
		if (history == null)
			return queryFailure(query, state.revision(), historyUnavailable());
		String from;
		String to;
		try {
			from = requiredString(query.payload(), "fromRecoveryPointId");
			to = requiredString(query.payload(), "toRecoveryPointId");
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), invalidPayload(exception.getMessage()));
		}
		try {
			List<WorkspaceChange> changes = history.compare(from, to);
			JsonObject projection = new JsonObject();
			projection.addProperty("fromRecoveryPointId", from);
			projection.addProperty("toRecoveryPointId", to);
			projection.addProperty("baseRevision", state.revision());
			JsonArray items = new JsonArray();
			changes.forEach(change -> {
				JsonObject item = new JsonObject();
				item.addProperty("type", change.type().name().toLowerCase(Locale.ROOT));
				item.addProperty("path", change.path());
				items.add(item);
			});
			projection.add("changes", items);
			return querySuccess(query, state.revision(), projection);
		} catch (LocalHistoryException exception) {
			return queryFailure(query, state.revision(), failureDiagnostic(query, "HISTORY_DIFF_FAILED",
					"diagnostic.history_diff_failed", "The two recovery points could not be compared.", null, null,
					exception));
		}
	}

	private JsonObject recoveryPoint(RecoveryPoint point) {
		JsonObject json = new JsonObject();
		json.addProperty("id", point.id());
		json.addProperty("label", point.label());
		json.addProperty("actor", wire(point.actor()));
		json.addProperty("taskId", point.taskId());
		json.addProperty("createdAt", point.createdAt().toString());
		return json;
	}

	private Diagnostic historyUnavailable() {
		return diagnostic("HISTORY_UNAVAILABLE", "diagnostic.history_unavailable",
				"Local history is not available for this workspace.", null, null);
	}

	private CommandOutcome mutationOutcome(Command command, RequestContext context,
			TransactionResult<Mutation> transaction, String eventName, RecoveryPoint recoveryPoint) {
		CommandOutcome rejected = checkFailure(command, transaction);
		if (rejected != null)
			return rejected;
		Mutation mutation = transaction.value();
		if (transaction.status() == TransactionResult.Status.ABORTED)
			return failed(command, transaction.revision(), mutation.diagnostic());
		JsonObject payload = new JsonObject();
		payload.add("element", elementSummary(mutation.element()));
		JsonObject data = payload.deepCopy();
		Event event = event(command, transaction.revision(), mutation.sequence(), eventName, payload);
		return new CommandOutcome(result(command, "committed", transaction.revision(), recoveryPoint,
				JsonNull.INSTANCE, data, List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
	}

	private CommandOutcome deleteOutcome(Command command, RequestContext context,
			TransactionResult<Mutation> transaction, RecoveryPoint recoveryPoint) {
		CommandOutcome rejected = checkFailure(command, transaction);
		if (rejected != null)
			return rejected;
		Mutation mutation = transaction.value();
		if (transaction.status() == TransactionResult.Status.ABORTED)
			return failed(command, transaction.revision(), mutation.diagnostic());
		JsonObject data = new JsonObject();
		data.addProperty("elementId", mutation.element().id().toString());
		JsonObject eventPayload = data.deepCopy();
		eventPayload.addProperty("name", mutation.element().name());
		Event event = event(command, transaction.revision(), mutation.sequence(), "mod_element_deleted", eventPayload);
		return new CommandOutcome(result(command, "committed", transaction.revision(), recoveryPoint,
				JsonNull.INSTANCE, data, List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
	}

	private CommandOutcome checkFailure(Command command, TransactionResult<?> transaction) {
		if (transaction.status() == TransactionResult.Status.NOT_FOUND)
			return failed(command, 0, workspaceNotFound());
		if (transaction.status() != TransactionResult.Status.CONFLICT)
			return null;
		JsonObject conflict = new JsonObject();
		conflict.addProperty("expectedRevision", command.expectedRevision());
		conflict.addProperty("actualRevision", transaction.revision());
		JsonArray paths = new JsonArray();
		transaction.changedPaths().forEach(paths::add);
		conflict.add("changedPaths", paths);
		Diagnostic diagnostic = diagnostic("WORKSPACE_REVISION_CONFLICT", "diagnostic.workspace_revision_conflict",
				"The workspace changed after this request was created.", null, null);
		return new CommandOutcome(result(command, "rejected", transaction.revision(), JsonNull.INSTANCE,
				JsonNull.INSTANCE, List.of(diagnostic), conflict, JsonNull.INSTANCE), List.of());
	}

	private JsonObject workbench(WorkspaceState state, RequestContext context) {
		JsonObject projection = new JsonObject();
		JsonObject workspace = new JsonObject();
		workspace.addProperty("id", state.id().toString());
		workspace.addProperty("name", state.name());
		workspace.addProperty("kind", state.kind());
		workspace.addProperty("revision", state.revision());
		workspace.addProperty("dirty", state.dirty());
		workspace.add("generator", state.generator());
		JsonObject lock = new JsonObject();
		lock.addProperty("state", context.permission() == PermissionProfile.READ_ONLY ? "read_only" : "write_available");
		lock.add("holder", JsonNull.INSTANCE);
		workspace.add("lock", lock);
		JsonObject compatibility = new JsonObject();
		compatibility.addProperty("mode", "upstream");
		compatibility.addProperty("unknownDataPreserved", true);
		workspace.add("compatibility", compatibility);
		projection.add("workspace", workspace);

		JsonObject permission = new JsonObject();
		permission.addProperty("profile", wire(context.permission()));
		permission.addProperty("canRequestElevation", true);
		permission.addProperty("protectedOperationsAlwaysConfirm", true);
		projection.add("permission", permission);
		JsonObject connection = new JsonObject();
		connection.addProperty("core", "connected");
		connection.addProperty("network", "online");
		connection.addProperty("bridge", "ready");
		projection.add("connection", connection);
		projection.add("elementCounts", elementCounts(state.elements()));
		projection.add("activeTasks", toArray(tasks.active(state.id())));
		projection.add("capabilities", capabilities(context));
		JsonArray recent = new JsonArray();
		state.recentElements(12).forEach(element -> recent.add(elementSummary(element)));
		projection.add("recentElements", recent);
		return projection;
	}

	private JsonObject elementList(WorkspaceState state, JsonObject payload) {
		int page = payload.has("page") ? requiredInt(payload, "page") : 1;
		int pageSize = payload.has("limit") ? requiredInt(payload, "limit") : requiredInt(payload, "pageSize");
		if (page < 1) throw new IllegalArgumentException("page must be at least 1");
		if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("page size must be between 1 and 200");
		JsonObject filter = payload.has("filter") && payload.get("filter").isJsonObject()
				? payload.getAsJsonObject("filter") : null;
		String search = listSearch(payload, filter);
		Set<String> types = listSet(payload, filter, "types");
		Set<String> states = listSet(payload, filter, "states");
		Boolean firstParty = listBoolean(filter, "firstParty");
		String sort = optionalString(payload, "sort");
		if (sort == null || sort.isBlank()) sort = "name";
		Set<String> fields = listFields(payload);
		String cursor = optionalString(payload, "cursor");
		Comparator<Element> comparator = elementListComparator(sort);
		List<Element> filtered = state.elements().stream()
				.filter(element -> search.isBlank() || element.name().contains(search)
						|| element.displayName().toLowerCase(Locale.ROOT).contains(search))
				.filter(element -> types.isEmpty() || types.contains(element.type()))
				.filter(element -> states.isEmpty() || states.contains(element.state()))
				.filter(element -> firstParty == null || firstParty == ElementCoverageCatalog.isFirstParty(element.type()))
				.sorted(comparator).toList();
		String querySignature = elementListQuerySignature(search, types, states, firstParty, sort, fields, pageSize);
		int from = cursor == null || cursor.isBlank()
				? Math.min((page - 1) * pageSize, filtered.size())
				: Math.min(decodeListCursor(cursor, state.revision(), querySignature), filtered.size());
		int to = Math.min(from + pageSize, filtered.size());
		JsonObject result = new JsonObject();
		JsonArray items = new JsonArray();
		filtered.subList(from, to).forEach(element -> items.add(elementListItem(element, fields)));
		result.add("items", items);
		result.addProperty("page", from / pageSize + 1);
		result.addProperty("pageSize", pageSize);
		result.addProperty("total", filtered.size());
		if (to < filtered.size())
			result.addProperty("nextCursor", encodeListCursor(state.revision(), to, querySignature));
		else
			result.add("nextCursor", JsonNull.INSTANCE);
		JsonArray availableTypes = new JsonArray();
		ElementCoverageCatalog.FIRST_PARTY_SLICE.forEach(availableTypes::add);
		result.add("availableTypes", availableTypes);
		return result;
	}

	private static String listSearch(JsonObject payload, JsonObject filter) {
		String value = filter != null && filter.has("search") ? requiredString(filter, "search")
				: optionalString(payload, "search");
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static Set<String> listSet(JsonObject payload, JsonObject filter, String property) {
		JsonArray values = filter != null && filter.has(property) ? filter.getAsJsonArray(property)
				: payload.has(property) ? payload.getAsJsonArray(property) : new JsonArray();
		return stringSet(values);
	}

	private static Boolean listBoolean(JsonObject filter, String property) {
		if (filter == null || !filter.has(property) || !filter.get(property).isJsonPrimitive()) return null;
		return filter.get(property).getAsBoolean();
	}

	private static boolean cursorListRequested(JsonObject payload) {
		return payload != null && (payload.has("cursor") || payload.has("limit") || payload.has("sort")
				|| payload.has("filter") || payload.has("fields"));
	}

	private static int listLimit(JsonObject payload) {
		int limit = payload.has("limit") ? requiredInt(payload, "limit") : 200;
		if (limit < 1 || limit > 200) throw new IllegalArgumentException("list limit must be between 1 and 200");
		return limit;
	}

	private static Set<String> listFields(JsonObject payload, Set<String> supported, String listName) {
		if (!payload.has("fields")) return Set.of();
		Set<String> fields = stringSet(payload.getAsJsonArray("fields"));
		if (fields.isEmpty() || !supported.containsAll(fields))
			throw new IllegalArgumentException("fields contains an unsupported " + listName + " field");
		return fields;
	}

	private static String listFieldSignature(Set<String> fields) {
		return fields.stream().sorted().reduce((left, right) -> left + "," + right).orElse("*");
	}

	private static JsonObject projectListFields(JsonObject value, Set<String> fields) {
		if (fields.isEmpty()) return value;
		JsonObject projected = new JsonObject();
		fields.stream().sorted().forEach(field -> projected.add(field, value.get(field)));
		return projected;
	}

	private static int listCursorOffset(JsonObject payload, long revision, String signature, int size) {
		String cursor = optionalString(payload, "cursor");
		return cursor == null || cursor.isBlank() ? 0 : Math.min(decodeListCursor(cursor, revision, signature), size);
	}

	private static JsonObject cursorListProjection(JsonArray items, int total, int limit, long revision, int to,
			String signature) {
		JsonObject projection = new JsonObject();
		projection.add("items", items);
		projection.addProperty("pageSize", limit);
		projection.addProperty("total", total);
		if (to < total) projection.addProperty("nextCursor", encodeListCursor(revision, to, signature));
		else projection.add("nextCursor", JsonNull.INSTANCE);
		return projection;
	}

	private static Comparator<JsonObject> registryEntryComparator(String registry, String sort) {
		boolean descending = sort.startsWith("-");
		String field = descending ? sort.substring(1) : sort;
		Comparator<JsonObject> comparator = switch (field) {
			case "name" -> Comparator.comparing(entry -> registryName(registry, entry), String.CASE_INSENSITIVE_ORDER);
			case "kind" -> Comparator.comparing(entry -> string(entry, "kind", ""));
			case "id" -> Comparator.comparing(entry -> string(entry, "id", ""));
			default -> throw new IllegalArgumentException("Unsupported registry entry sort: " + sort);
		};
		if (descending) comparator = comparator.reversed();
		return comparator.thenComparing(entry -> string(entry, "id", ""));
	}

	private static Comparator<RecoveryPoint> recoveryPointComparator(String sort) {
		boolean descending = sort.startsWith("-");
		String field = descending ? sort.substring(1) : sort;
		Comparator<RecoveryPoint> comparator = switch (field) {
			case "createdAt" -> Comparator.comparing(RecoveryPoint::createdAt);
			case "label" -> Comparator.comparing(RecoveryPoint::label, String.CASE_INSENSITIVE_ORDER);
			case "actor" -> Comparator.comparing(point -> wire(point.actor()));
			default -> throw new IllegalArgumentException("Unsupported recovery point sort: " + sort);
		};
		if (descending) comparator = comparator.reversed();
		return comparator.thenComparing(RecoveryPoint::id);
	}

	private static Comparator<AssetPublishBatchService.PublishBatch> publishBatchComparator(String sort) {
		boolean descending = sort.startsWith("-");
		String field = descending ? sort.substring(1) : sort;
		Comparator<AssetPublishBatchService.PublishBatch> comparator = switch (field) {
			case "createdAt" -> Comparator.comparing(AssetPublishBatchService.PublishBatch::createdAt);
			case "name" -> Comparator.comparing(AssetPublishBatchService.PublishBatch::name,
					String.CASE_INSENSITIVE_ORDER);
			case "assetCount" -> Comparator.comparingInt(AssetPublishBatchService.PublishBatch::assetCount);
			default -> throw new IllegalArgumentException("Unsupported publish batch sort: " + sort);
		};
		if (descending) comparator = comparator.reversed();
		return comparator.thenComparing(batch -> batch.id().toString());
	}

	private static Set<String> listFields(JsonObject payload) {
		return listFields(payload, Set.of("id", "type", "name", "displayName", "state", "ownership",
				"updatedAt", "firstParty", "diagnostics"), "mod element summary");
	}

	private static Comparator<Element> elementListComparator(String sort) {
		boolean descending = sort.startsWith("-");
		String field = descending ? sort.substring(1) : sort;
		Comparator<Element> primary = switch (field) {
			case "name" -> Comparator.comparing(Element::name);
			case "displayName" -> Comparator.comparing(Element::displayName, String.CASE_INSENSITIVE_ORDER);
			case "type" -> Comparator.comparing(Element::type);
			case "state" -> Comparator.comparing(Element::state);
			case "updatedAt" -> Comparator.comparing(Element::updatedAt);
			default -> throw new IllegalArgumentException("Unsupported list_mod_elements sort: " + sort);
		};
		if (descending) primary = primary.reversed();
		return primary.thenComparing(element -> element.id().toString());
	}

	private JsonObject elementListItem(Element element, Set<String> fields) {
		JsonObject summary = elementSummary(element);
		if (fields.isEmpty()) return summary;
		JsonObject projected = new JsonObject();
		fields.stream().sorted().forEach(field -> projected.add(field, summary.get(field)));
		return projected;
	}

	private static String elementListQuerySignature(String search, Set<String> types, Set<String> states,
			Boolean firstParty, String sort, Set<String> fields, int pageSize) {
		String typeKey = types.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
		String stateKey = states.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
		String fieldKey = fields.stream().sorted().reduce((left, right) -> left + "," + right).orElse("*");
		return search + "|" + typeKey + "|" + stateKey + "|" + firstParty + "|" + sort + "|" + fieldKey
				+ "|" + pageSize;
	}

	private static String encodeListCursor(long revision, int offset, String querySignature) {
		String raw = "v1:" + revision + ":" + offset + ":" + listCursorSignatureDigest(querySignature);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static String listCursorSignatureDigest(String querySignature) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
					.digest(querySignature.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required for list cursor validation.", exception);
		}
	}

	private static int decodeListCursor(String cursor, long revision, String querySignature) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = raw.split(":", 4);
			if (parts.length != 4 || !"v1".equals(parts[0]))
				throw ListCursorException.invalid("The list cursor format is invalid.");
			long cursorRevision = Long.parseLong(parts[1]);
			int offset = Integer.parseInt(parts[2]);
			String expectedQueryDigest = listCursorSignatureDigest(querySignature);
			if (cursorRevision != revision)
				throw ListCursorException.stale("The list cursor belongs to an older workspace revision.");
			if (!expectedQueryDigest.equals(parts[3]))
				throw ListCursorException.invalid("The list cursor does not match the current query.");
			if (offset < 0) throw ListCursorException.invalid("The list cursor offset is invalid.");
			return offset;
		} catch (ListCursorException exception) {
			throw exception;
		} catch (IllegalArgumentException exception) {
			throw ListCursorException.invalid("The list cursor could not be decoded.");
		}
	}

	private QueryResult editor(Query query, WorkspaceState state, RequestContext context) {
		UUID elementId = UUID.fromString(requiredString(query.payload(), "elementId"));
		Element element = state.element(elementId);
		if (element == null)
			return queryFailure(query, state.revision(), elementNotFound(elementId));
		boolean outsideSlice = !ElementCoverageCatalog.isFirstParty(element.type());
		boolean readOnly = outsideSlice || context.permission() == PermissionProfile.READ_ONLY;
		JsonObject projection = new JsonObject();
		projection.add("element", elementSummary(element));
		projection.add("sections", editorSections(element, readOnly));
		projection.add("capabilities", capabilities(context));
		if (!outsideSlice)
			return querySuccess(query, state.revision(), projection);
		return new QueryResult("query_result", UiCore.SCHEMA_VERSION, query.requestId(), query.workspaceId(),
				query.operation(), "succeeded", state.revision(), projection,
				List.of(diagnostic("ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE",
						"diagnostic.element_type_outside_first_party_slice",
						"This element type is outside the supported Java catalog. The editor is read-only.", "/elementId",
						element.id())));
	}

	private QueryResult preview(Query query, WorkspaceState state) {
		UUID elementId = UUID.fromString(requiredString(query.payload(), "elementId"));
		Element element = state.element(elementId);
		if (element == null)
			return queryFailure(query, state.revision(), elementNotFound(elementId));
		JsonArray changes = query.payload().getAsJsonArray("changes");
		if (changes == null || changes.isEmpty())
			return queryFailure(query, state.revision(), invalidPayload("changes must not be empty"));
		JsonObject values = element.values().deepCopy();
		JsonArray changedPaths = new JsonArray();
		for (JsonElement rawChange : changes) {
			JsonObject change = rawChange.getAsJsonObject();
			String pointer = requiredString(change, "path");
			JsonPointerPatch.set(values, pointer, change.has("value") ? change.get("value") : JsonNull.INSTANCE);
			changedPaths.add(elementPath(elementId) + pointer);
		}
		Diagnostic diagnostic = validateElementValues(elementId, values);
		JsonObject projection = new JsonObject();
		projection.addProperty("elementId", elementId.toString());
		projection.addProperty("baseRevision", state.revision());
		projection.addProperty("canApply", diagnostic == null);
		projection.add("changedPaths", changedPaths);
		projection.add("candidateValues", values);
		JsonArray diagnostics = new JsonArray();
		if (diagnostic != null)
			diagnostics.add(GSON.toJsonTree(diagnostic));
		projection.add("diagnostics", diagnostics);
		return querySuccess(query, state.revision(), projection);
	}

	/**
	 * Stage 12 keeps the Stage 11 wire and persistence contract intact while adding a domain-oriented
	 * projection for the high-complexity P0 types. Every leaf still appears exactly once, and fields
	 * that Copperbench does not understand are deliberately collected under {@code advanced} instead
	 * of being omitted. That makes the typed UI safe for upstream/newer-generator round trips.
	 */
	private JsonArray editorSections(Element element, boolean readOnly) {
		JsonArray fields = new JsonArray();
		flattenFields(element.values(), "", fields, readOnly, element.type());
		if (fields.isEmpty())
			fields.add(editorField("/displayName", "Display name", element.displayName(), readOnly, element.type()));

		List<String> order = stage12SectionOrder(element.type());
		if (order == null)
			return singleEditorSection("general", "General", fields);

		List<JsonArray> grouped = new ArrayList<>();
		for (int index = 0; index < order.size(); index++) grouped.add(new JsonArray());
		for (JsonElement rawField : fields) {
			JsonObject field = rawField.getAsJsonObject();
			String sectionId = stage12SectionId(element.type(), field.get("path").getAsString());
			int sectionIndex = order.indexOf(sectionId);
			if (sectionIndex < 0) sectionIndex = order.indexOf("advanced");
			grouped.get(sectionIndex).add(field);
		}

		JsonArray sections = new JsonArray();
		for (int index = 0; index < order.size(); index++) {
			if (grouped.get(index).isEmpty()) continue;
			String id = order.get(index);
			sections.add(editorSection(id, stage12SectionTitle(id), grouped.get(index)));
		}
		return sections;
	}

	private JsonArray singleEditorSection(String id, String title, JsonArray fields) {
		JsonArray sections = new JsonArray();
		sections.add(editorSection(id, title, fields));
		return sections;
	}

	private JsonObject editorSection(String id, String title, JsonArray fields) {
		JsonObject section = new JsonObject();
		section.addProperty("id", id);
		section.add("title", localized(editorSectionKey(id), title));
		section.add("fields", fields);
		return section;
	}

	private String editorSectionKey(String id) {
		return switch (id) {
			case "general" -> "editor.section.general";
			case "identity" -> "editor.section.identity";
			case "appearance" -> "editor.section.appearance";
			case "resources" -> "editor.section.resources";
			case "attributes" -> "editor.section.attributes";
			case "behavior" -> "editor.section.behavior";
			case "equipment" -> "editor.section.equipment";
			case "spawning" -> "editor.section.spawning";
			case "events" -> "editor.section.events";
			case "climate" -> "editor.section.climate";
			case "generation" -> "editor.section.generation";
			case "environment" -> "editor.section.environment";
			case "layout" -> "editor.section.layout";
			case "components" -> "editor.section.components";
			case "advanced" -> "editor.section.advanced";
			default -> "editor.section.general";
		};
	}

	private List<String> stage12SectionOrder(String elementType) {
		return switch (elementType) {
			case "livingentity" -> List.of("identity", "appearance", "resources", "attributes", "behavior",
					"equipment", "spawning", "events", "advanced");
			case "biome" -> List.of("identity", "climate", "appearance", "generation", "spawning", "resources",
					"advanced");
			case "dimension" -> List.of("identity", "environment", "appearance", "generation", "resources",
					"advanced");
			case "gui" -> List.of("identity", "layout", "components", "behavior", "resources", "advanced");
			default -> null;
		};
	}

	private String stage12SectionId(String elementType, String path) {
		String field = editorFieldName(path).toLowerCase(Locale.ROOT);
		if (Set.of("displayname", "name", "description", "mobname", "moblabel", "title").contains(field))
			return "identity";

		return switch (elementType) {
			case "livingentity" -> livingEntitySection(field);
			case "biome" -> biomeSection(field);
			case "dimension" -> dimensionSection(field);
			case "gui" -> guiSection(field);
			default -> "advanced";
		};
	}

	private String livingEntitySection(String field) {
		if (isResourceReferenceField(field)) return "resources";
		if (Set.of("modelwidth", "modelheight", "modelshadowsize", "mountedyoffset", "visualscale",
				"boundingboxscale", "transparentmodelcondition", "isshakingcondition", "solidboundingbox",
				"hasspawnegg", "spawneggbasecolor", "spawneggdotcolor", "bossbarcolor", "bossbartype").contains(field)
				|| field.contains("model")) return "appearance";
		if (Set.of("attackstrength", "attackknockback", "knockbackresistance", "movementspeed", "stepheight",
				"armorbasevalue", "trackingrange", "followrange", "health", "xpamount").contains(field))
			return "attributes";
		if (field.startsWith("equipment") || Set.of("mobdrop", "guiboundto", "inventorysize",
				"inventorystacksize", "rangedattackitem", "breedtriggeritems", "creativetabs").contains(field))
			return "equipment";
		if (field.contains("spawn") || field.equals("restrictionbiomes") || field.equals("doesdespawnwhenidle"))
			return "spawning";
		if (isProcedureReferenceField(field)) return "events";
		if (field.equals("aixml") || field.equals("aibase") || field.equals("hasai") || field.equals("ranged")
				|| field.equals("rangeditemtype") || field.equals("rangedattackinterval")
				|| field.equals("rangedattackradius") || field.equals("breedable") || field.equals("tameable")
				|| field.equals("ridable") || field.startsWith("cancontrol") || field.startsWith("immuneto")
				|| field.equals("watermob") || field.equals("flyingmob") || field.equals("disablecollisions")
				|| field.equals("mobbehaviourtype") || field.equals("mobcreaturetype") || field.equals("isboss")
				|| field.equals("entitydataentries") || field.equals("animations")
				|| field.equals("sensitivetovibration") || field.equals("vibrationalevents")) return "behavior";
		return "advanced";
	}

	private String biomeSection(String field) {
		if (isResourceReferenceField(field)) return "resources";
		if (field.contains("temperature") || field.contains("rain") || field.contains("downfall")
				|| field.contains("climate") || field.contains("precipitation")) return "climate";
		if (field.contains("color") || field.contains("fog") || field.contains("sky") || field.contains("grass")
				|| field.contains("foliage") || field.contains("water") || field.contains("particle")
				|| field.contains("ambient")) return "appearance";
		if (field.contains("spawn") || field.contains("creature") || field.contains("mob")) return "spawning";
		if (field.contains("feature") || field.contains("structure") || field.contains("ore")
				|| field.contains("tree") || field.contains("carver") || field.contains("generation")
				|| field.contains("surface")) return "generation";
		return "advanced";
	}

	private String dimensionSection(String field) {
		if (isResourceReferenceField(field)) return "resources";
		if (field.contains("fog") || field.contains("sky") || field.contains("cloud") || field.contains("sun")
				|| field.contains("moon") || field.contains("color")) return "appearance";
		if (field.startsWith("generate") || field.contains("biome") || field.contains("structure")
				|| field.contains("feature") || field.contains("worldgen") || field.contains("ore")) return "generation";
		if (field.contains("height") || field.contains("scale") || field.contains("skylight")
				|| field.contains("natural") || field.contains("bed") || field.contains("respawn")
				|| field.contains("portal") || field.contains("infiniburn") || field.contains("ambientlight")
				|| field.contains("ultrawarm") || field.contains("ceiling")) return "environment";
		return "advanced";
	}

	private String guiSection(String field) {
		if (isResourceReferenceField(field)) return "resources";
		if (field.equals("width") || field.equals("height") || field.endsWith("x") || field.endsWith("y")
				|| field.contains("anchor") || field.contains("offset") || field.contains("background")) return "layout";
		if (field.contains("component") || field.contains("button") || field.contains("label")
				|| field.contains("slot") || field.contains("checkbox") || field.contains("textfield")
				|| field.contains("image") || field.contains("widget")) return "components";
		if (field.contains("pause") || field.contains("container") || field.contains("inventory")
				|| isProcedureReferenceField(field)) return "behavior";
		return "advanced";
	}

	private String stage12SectionTitle(String id) {
		return switch (id) {
			case "identity" -> "Identity";
			case "appearance" -> "Appearance";
			case "resources" -> "Resources";
			case "attributes" -> "Attributes";
			case "behavior" -> "Behavior";
			case "equipment" -> "Equipment & Inventory";
			case "spawning" -> "Spawning";
			case "events" -> "Events & Procedures";
			case "climate" -> "Climate";
			case "generation" -> "Generation";
			case "environment" -> "Environment";
			case "layout" -> "Layout";
			case "components" -> "Components";
			case "advanced" -> "Advanced / Preserved";
			default -> displayName(id);
		};
	}

	private QueryResult previewDatagenOutput(Query query, WorkspaceState state) {
		UUID taskId = UUID.fromString(requiredString(query.payload(), "taskId"));
		JsonObject preview = tasks.previewDatagen(query.workspaceId(), taskId).orElse(null);
		if (preview == null)
			return queryFailure(query, state.revision(), diagnostic("DATAGEN_STAGING_NOT_FOUND",
					"diagnostic.datagen_staging_not_found", "The staged datagen output does not exist.",
					"/taskId", null));
		return querySuccess(query, state.revision(), preview);
	}

	private CommandOutcome publishDatagenOutput(Command command, RequestContext context) {
		UUID taskId;
		try {
			taskId = UUID.fromString(requiredString(command.payload(), "taskId"));
			requiredString(command.payload(), "manifestHash");
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		RecoveryPoint recoveryPoint;
		try {
			recoveryPoint = datagenRecoveryPoint(command, context, taskId);
		} catch (LocalHistoryException exception) {
			return automationRecoveryFailed(command, exception);
		}
		TransactionResult<DatagenMutation> transaction = store.transact(command.workspaceId(),
				command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			JsonObject data;
			try {
				data = tasks.publishDatagen(command.workspaceId(), taskId, command.payload());
			} catch (RuntimeException exception) {
				return Decision.abort(DatagenMutation.rejected(failureDiagnostic(command,
						"DATAGEN_PUBLISH_FAILED", "diagnostic.datagen_publish_failed",
						"The staged datagen output could not be published.", "/taskId", null, exception)));
			}
			JsonArray changed = data.has("changedPaths") ? data.getAsJsonArray("changedPaths") : new JsonArray();
			if (changed.isEmpty()) {
				tasks.rollbackDatagenPublish(command.workspaceId(), taskId);
				return Decision.abort(DatagenMutation.rejected(diagnostic("DATAGEN_NO_CHANGES",
						"diagnostic.datagen_no_changes", "The staged datagen output has no changes to publish.",
						"/taskId", null)));
			}
			Diagnostic persistenceFailure = persistWorkspaceData(before, state, command);
			if (persistenceFailure != null) {
				tasks.rollbackDatagenPublish(command.workspaceId(), taskId);
				return Decision.abort(DatagenMutation.rejected(persistenceFailure));
			}
			List<String> paths = new ArrayList<>();
			changed.forEach(raw -> paths.add("/" + raw.getAsString()));
			return Decision.commit(DatagenMutation.success(data, state.nextEventSequence()), paths);
		});
		CommandOutcome rejected = checkFailure(command, transaction);
		if (rejected != null) return rejected;
		DatagenMutation mutation = transaction.value();
		if (transaction.status() == TransactionResult.Status.ABORTED)
			return failed(command, transaction.revision(), mutation.diagnostic());
		tasks.completeDatagenPublish(command.workspaceId(), taskId);
		Event event = event(command, transaction.revision(), mutation.sequence(), "datagen_published",
				mutation.data().deepCopy());
		return new CommandOutcome(result(command, "committed", transaction.revision(), recoveryPoint,
				JsonNull.INSTANCE, mutation.data(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
	}

	private RecoveryPoint datagenRecoveryPoint(Command command, RequestContext context, UUID taskId)
			throws LocalHistoryException {
		if (history == null) return null;
		WorkspaceState current = store.read(command.workspaceId()).orElse(null);
		if (current == null || current.revision() != command.expectedRevision()) return null;
		return history.createRecoveryPoint(new RecoveryPointRequest("Before datagen publish", context.actor(),
				taskId.toString()));
	}

	private CommandOutcome runServer(Command command, RequestContext context) {
		if (!approved(command, context))
			return approvalRequired(command, context,
					"Starting a dedicated test server requires explicit desktop EULA confirmation.");
		JsonObject payload = command.payload().deepCopy();
		payload.addProperty("eulaAccepted", true);
		Command approved = Command.of(command.requestId(), command.workspaceId(), command.expectedRevision(),
				command.operation(), payload);
		return startTask(approved);
	}

	private CommandOutcome registryMutationOutcome(Command command, TransactionResult<RegistryMutation> transaction,
			RecoveryPoint recoveryPoint) {
		CommandOutcome rejected = checkFailure(command, transaction);
		if (rejected != null) return rejected;
		RegistryMutation mutation = transaction.value();
		if (transaction.status() == TransactionResult.Status.ABORTED)
			return failed(command, transaction.revision(), mutation.diagnostic());
		JsonObject eventPayload = mutation.data().deepCopy();
		Event event = event(command, transaction.revision(), mutation.sequence(), "registry_updated", eventPayload);
		return new CommandOutcome(result(command, "committed", transaction.revision(), recoveryPoint,
				JsonNull.INSTANCE, mutation.data(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
	}

	private RecoveryPoint registryRecoveryPoint(Command command, RequestContext context) throws LocalHistoryException {
		RecoveryPoint automated = automationRecoveryPoint(command, context);
		if (automated != null || history == null || (command.operation() != Operation.RENAME_REGISTRY_ENTRY
				&& command.operation() != Operation.DELETE_REGISTRY_ENTRY)) return automated;
		WorkspaceState current = store.read(command.workspaceId()).orElse(null);
		if (current == null || current.revision() != command.expectedRevision()) return null;
		String taskId = command.payload().has("clientMutationId")
				? command.payload().get("clientMutationId").getAsString() : command.requestId().toString();
		return history.createRecoveryPoint(new RecoveryPointRequest("Before registry mutation", context.actor(), taskId));
	}

	private Diagnostic persistWorkspaceData(WorkspaceState before, WorkspaceState after, Command command) {
		try {
			mutations.persistWorkspaceData(before, after, command.operation());
			return null;
		} catch (Exception exception) {
			return failureDiagnostic(command, "WORKSPACE_PERSISTENCE_FAILED", "diagnostic.workspace_persistence_failed",
					"The structured workspace change could not be stored and was rolled back.", null, null, exception);
		}
	}

	private QueryResult procedureEditor(Query query, WorkspaceState state, RequestContext context) {
		UUID elementId = UUID.fromString(requiredString(query.payload(), "elementId"));
		Element element = state.element(elementId);
		if (element == null) return queryFailure(query, state.revision(), elementNotFound(elementId));
		if (!element.type().equals("procedure"))
			return queryFailure(query, state.revision(), diagnostic("PROCEDURE_ELEMENT_REQUIRED",
					"diagnostic.procedure_element_required", "The requested element is not a Procedure.",
					"/elementId", elementId));
		ProcedureIr ir;
		try {
			ir = PROCEDURES.read(element.values(), elementId);
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), diagnostic("PROCEDURE_IR_INVALID",
					"diagnostic.procedure_ir_invalid", "The Procedure graph could not be parsed.",
					elementPath(elementId) + "/procedurexml", elementId));
		}
		JsonObject projection = procedureProjection(state, element, ir, context);
		List<Diagnostic> diagnostics = procedureDiagnostics(elementId, PROCEDURES.validate(ir));
		return new QueryResult("query_result", UiCore.SCHEMA_VERSION, query.requestId(), query.workspaceId(),
				query.operation(), "succeeded", state.revision(), projection, diagnostics);
	}

	private QueryResult previewProcedure(Query query, WorkspaceState state) {
		UUID elementId = UUID.fromString(requiredString(query.payload(), "elementId"));
		Element element = state.element(elementId);
		if (element == null) return queryFailure(query, state.revision(), elementNotFound(elementId));
		if (!element.type().equals("procedure"))
			return queryFailure(query, state.revision(), diagnostic("PROCEDURE_ELEMENT_REQUIRED",
					"diagnostic.procedure_element_required", "The requested element is not a Procedure.",
					"/elementId", elementId));
		JsonArray edits = query.payload().getAsJsonArray("edits");
		if (edits == null || edits.isEmpty())
			return queryFailure(query, state.revision(), invalidPayload("edits must not be empty"));
		ProcedureIr current = PROCEDURES.read(element.values(), elementId);
		ProcedureIr candidate = PROCEDURES.applyEdits(current, edits);
		List<ProcedureIr.ValidationIssue> issues = PROCEDURES.validate(candidate);
		JsonObject projection = new JsonObject();
		projection.addProperty("elementId", elementId.toString());
		projection.addProperty("baseRevision", state.revision());
		projection.addProperty("canSaveDraft", true);
		projection.addProperty("canGenerate", issues.stream().noneMatch(ProcedureIr.ValidationIssue::error));
		projection.add("candidateIr", PROCEDURES.toJson(candidate));
		projection.addProperty("sourcePreview", PROCEDURES.sourcePreview(candidate));
		projection.add("diagnostics", GSON.toJsonTree(procedureDiagnostics(elementId, issues)));
		JsonArray changedPaths = new JsonArray();
		changedPaths.add(elementPath(elementId) + "/procedureIr");
		changedPaths.add(elementPath(elementId) + "/procedurexml");
		projection.add("changedPaths", changedPaths);
		return querySuccess(query, state.revision(), projection);
	}

	private QueryResult workspaceReferences(Query query, WorkspaceState state) {
		String target = query.payload().has("target") && query.payload().get("target").isJsonPrimitive()
				? query.payload().get("target").getAsString() : "";
		return querySuccess(query, state.revision(), references.projection(state, target));
	}

	private QueryResult listRegistries(Query query, WorkspaceState state) {
		JsonObject payload = query.payload();
		String selected = payload.has("registry") && payload.get("registry").isJsonPrimitive()
				? payload.get("registry").getAsString() : "";
		if (!selected.isBlank() && !REGISTRY_NAMES.contains(selected))
			return queryFailure(query, state.revision(), invalidPayload("Unsupported registry: " + selected));
		JsonObject data = new JsonObject();
		JsonObject registries = state.registries();
		if (!cursorListRequested(payload)) {
			if (selected.isBlank()) data.add("registries", registries);
			else data.add(selected, registries.getAsJsonArray(selected));
			data.add("languageStats", languageStats(registries.getAsJsonArray("languageKeys")));
			data.addProperty("stableIds", true);
			data.addProperty("referenceAwareRename", true);
			return querySuccess(query, state.revision(), data);
		}

		if (selected.isBlank())
			return queryFailure(query, state.revision(), invalidPayload("registry is required for cursor pagination"));
		int limit = listLimit(payload);
		String sort = optionalString(payload, "sort");
		if (sort == null || sort.isBlank()) sort = "name";
		JsonObject filter = payload.has("filter") && payload.get("filter").isJsonObject()
				? payload.getAsJsonObject("filter") : null;
		String search = filter != null && filter.has("search")
				? requiredString(filter, "search").toLowerCase(Locale.ROOT) : "";
		Set<String> fields = listFields(payload, Set.of("id", "kind", "name", "key", "dataType", "scope",
				"namespace", "category", "value", "members", "translations", "support"), "registry entry");
		List<JsonObject> entries = new ArrayList<>();
		registries.getAsJsonArray(selected).forEach(raw -> entries.add(raw.getAsJsonObject().deepCopy()));
		Comparator<JsonObject> comparator = registryEntryComparator(selected, sort);
		List<JsonObject> filtered = entries.stream()
				.filter(entry -> search.isBlank() || registryName(selected, entry).toLowerCase(Locale.ROOT).contains(search))
				.sorted(comparator).toList();
		String dataset = entries.stream().map(entry -> string(entry, "id", "")).sorted()
				.reduce((left, right) -> left + "," + right).orElse("");
		String signature = "registry|" + selected + "|" + dataset + "|" + search + "|" + sort + "|"
				+ listFieldSignature(fields) + "|" + limit;
		int from = listCursorOffset(payload, state.revision(), signature, filtered.size());
		int to = Math.min(from + limit, filtered.size());
		JsonArray items = new JsonArray();
		filtered.subList(from, to).forEach(entry -> items.add(projectListFields(entry, fields)));
		data = cursorListProjection(items, filtered.size(), limit, state.revision(), to, signature);
		data.addProperty("registry", selected);
		data.add("languageStats", languageStats(registries.getAsJsonArray("languageKeys")));
		data.addProperty("stableIds", true);
		data.addProperty("referenceAwareRename", true);
		return querySuccess(query, state.revision(), data);
	}

	private QueryResult previewRegistryRename(Query query, WorkspaceState state) {
		UUID entryId = UUID.fromString(requiredString(query.payload(), "entryId"));
		String newName = requiredString(query.payload(), "newName");
		RegistryLocation location = findRegistryEntry(state.registries(), entryId);
		if (location == null)
			return queryFailure(query, state.revision(), registryEntryNotFound(entryId));
		Diagnostic validation = validateRegistryName(location.registry(), location.entry(), newName,
				state.registries(), entryId);
		if (validation != null) return queryFailure(query, state.revision(), validation);
		JsonObject data = new JsonObject();
		data.addProperty("entryId", entryId.toString());
		data.addProperty("registry", location.registry());
		data.addProperty("oldName", registryName(location.registry(), location.entry()));
		data.addProperty("newName", newName);
		JsonObject impacted = references.projection(state, entryId.toString());
		data.add("references", impacted);
		data.addProperty("impactedElementCount", distinctReferenceSources(impacted));
		data.addProperty("canApply", true);
		return querySuccess(query, state.revision(), data);
	}

	private CommandOutcome mutateRegistry(Command command, RequestContext context) {
		RecoveryPoint recoveryPoint;
		try {
			recoveryPoint = registryRecoveryPoint(command, context);
		} catch (LocalHistoryException exception) {
			return automationRecoveryFailed(command, exception);
		}
		TransactionResult<RegistryMutation> transaction = store.transact(command.workspaceId(),
				command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			JsonObject registries = state.registries();
			RegistryEdit edit;
			try {
				edit = switch (command.operation()) {
					case CREATE_REGISTRY_ENTRY -> createRegistryEntry(command.payload(), registries);
					case UPDATE_REGISTRY_ENTRY -> updateRegistryEntry(command.payload(), registries);
					case DELETE_REGISTRY_ENTRY -> deleteRegistryEntry(command.payload(), registries, state);
					case RENAME_REGISTRY_ENTRY -> renameRegistryEntry(command.payload(), registries, state);
					default -> throw new IllegalArgumentException("Unsupported registry mutation");
				};
			} catch (RegistryValidationException exception) {
				return Decision.abort(RegistryMutation.rejected(exception.diagnostic()));
			} catch (RuntimeException exception) {
				return Decision.abort(RegistryMutation.rejected(invalidPayload(exception.getMessage())));
			}
			state.replaceRegistries(registries);
			Diagnostic persistenceFailure = persistWorkspaceData(before, state, command);
			if (persistenceFailure != null)
				return Decision.abort(RegistryMutation.rejected(persistenceFailure));
			return Decision.commit(RegistryMutation.success(edit.entry(), state.nextEventSequence(), edit.data()),
					edit.changedPaths());
		});
		return registryMutationOutcome(command, transaction, recoveryPoint);
	}

	private RegistryEdit createRegistryEntry(JsonObject payload, JsonObject registries) {
		String registry = requiredRegistry(payload);
		if (!payload.has("entry") || !payload.get("entry").isJsonObject())
			throw new IllegalArgumentException("entry is required");
		JsonObject entry = payload.getAsJsonObject("entry").deepCopy();
		UUID entryId = ids.get();
		entry.addProperty("id", entryId.toString());
		entry.addProperty("kind", registryKind(registry));
		String name = registryName(registry, entry);
		Diagnostic validation = validateRegistryName(registry, entry, name, registries, null);
		if (validation != null) throw new RegistryValidationException(validation);
		if (registry.equals("variables")) {
			if (!entry.has("dataType")) entry.addProperty("dataType", "number");
			if (!entry.has("scope")) entry.addProperty("scope", "global");
		} else if (registry.equals("tags")) {
			if (!entry.has("namespace")) entry.addProperty("namespace", "mod");
			if (!entry.has("category")) entry.addProperty("category", "items");
			if (!entry.has("members")) entry.add("members", new JsonArray());
		} else if (!entry.has("translations")) entry.add("translations", new JsonObject());
		entry.add("support", registrySupport("supported", "REGISTRY_ENTRY_SUPPORTED"));
		registries.getAsJsonArray(registry).add(entry);
		JsonObject data = new JsonObject();
		data.add("entry", entry.deepCopy());
		return new RegistryEdit(entry, data, List.of("/registries/" + registry + "/" + entryId));
	}

	private RegistryEdit updateRegistryEntry(JsonObject payload, JsonObject registries) {
		UUID entryId = UUID.fromString(requiredString(payload, "entryId"));
		RegistryLocation location = findRegistryEntry(registries, entryId);
		if (location == null) throw new RegistryValidationException(registryEntryNotFound(entryId));
		JsonArray changes = payload.has("changes") && payload.get("changes").isJsonArray()
				? payload.getAsJsonArray("changes") : new JsonArray();
		if (changes.isEmpty()) throw new IllegalArgumentException("changes must not be empty");
		JsonObject entry = location.entry().deepCopy();
		for (JsonElement raw : changes) {
			JsonObject change = raw.getAsJsonObject();
			String path = requiredString(change, "path");
			if (path.equals("/id") || path.equals("/kind"))
				throw new IllegalArgumentException("Stable registry identity fields cannot be changed");
			JsonPointerPatch.set(entry, path, change.has("value") ? change.get("value") : JsonNull.INSTANCE);
		}
		String name = registryName(location.registry(), entry);
		Diagnostic validation = validateRegistryName(location.registry(), entry, name, registries, entryId);
		if (validation != null) throw new RegistryValidationException(validation);
		location.entries().set(location.index(), entry);
		JsonObject data = new JsonObject();
		data.add("entry", entry.deepCopy());
		return new RegistryEdit(entry, data, List.of("/registries/" + location.registry() + "/" + entryId));
	}

	private RegistryEdit deleteRegistryEntry(JsonObject payload, JsonObject registries, WorkspaceState state) {
		UUID entryId = UUID.fromString(requiredString(payload, "entryId"));
		RegistryLocation location = findRegistryEntry(registries, entryId);
		if (location == null) throw new RegistryValidationException(registryEntryNotFound(entryId));
		JsonObject impacted = references.projection(state, entryId.toString());
		boolean force = payload.has("force") && payload.get("force").getAsBoolean();
		if (impacted.getAsJsonArray("edges").size() > 0 && !force)
			throw new RegistryValidationException(diagnostic("REGISTRY_ENTRY_IN_USE",
					"diagnostic.registry_entry_in_use", "The registry entry is still referenced.",
					"/entryId", null));
		JsonObject removed = location.entry().deepCopy();
		location.entries().remove(location.index());
		JsonObject data = new JsonObject();
		data.addProperty("entryId", entryId.toString());
		data.add("references", impacted);
		return new RegistryEdit(removed, data, List.of("/registries/" + location.registry() + "/" + entryId));
	}

	private RegistryEdit renameRegistryEntry(JsonObject payload, JsonObject registries, WorkspaceState state) {
		UUID entryId = UUID.fromString(requiredString(payload, "entryId"));
		String newName = requiredString(payload, "newName");
		RegistryLocation location = findRegistryEntry(registries, entryId);
		if (location == null) throw new RegistryValidationException(registryEntryNotFound(entryId));
		Diagnostic validation = validateRegistryName(location.registry(), location.entry(), newName, registries, entryId);
		if (validation != null) throw new RegistryValidationException(validation);
		String oldName = registryName(location.registry(), location.entry());
		JsonObject renamed = location.entry().deepCopy();
		renamed.addProperty(location.registry().equals("languageKeys") ? "key" : "name", newName);
		location.entries().set(location.index(), renamed);
		JsonArray changedElements = new JsonArray();
		for (Element element : state.elements()) {
			JsonObject values = element.values().deepCopy();
			if (!rewriteRegistryReferences(values, location.registry(), entryId.toString(), oldName, newName, ""))
				continue;
			if (element.type().equals("procedure") && values.has("procedureIr"))
				values.addProperty("procedurexml", PROCEDURES.toBlocklyXml(PROCEDURES.read(values, element.id())));
			state.replaceElement(new Element(element.id(), element.type(), element.name(), element.displayName(),
					element.state(), element.ownership(), clock.instant(), values));
			changedElements.add(element.id().toString());
		}
		JsonObject data = new JsonObject();
		data.add("entry", renamed.deepCopy());
		data.addProperty("oldName", oldName);
		data.add("changedElementIds", changedElements);
		List<String> paths = new ArrayList<>();
		paths.add("/registries/" + location.registry() + "/" + entryId);
		changedElements.forEach(raw -> paths.add(elementPath(UUID.fromString(raw.getAsString()))));
		return new RegistryEdit(renamed, data, List.copyOf(paths));
	}

	private JsonObject procedureProjection(WorkspaceState state, Element element, ProcedureIr ir,
			RequestContext context) {
		JsonObject projection = new JsonObject();
		projection.add("element", elementSummary(element));
		projection.addProperty("baseRevision", state.revision());
		projection.addProperty("readOnly", context.permission() == PermissionProfile.READ_ONLY);
		projection.add("ir", PROCEDURES.toJson(ir));
		projection.add("nodeCatalog", procedureNodeCatalog(state));
		projection.addProperty("sourcePreview", PROCEDURES.sourcePreview(ir));
		projection.addProperty("sourceOwnership", "generated");
		projection.add("references", references.projection(state, element.id().toString()));
		return projection;
	}

	private JsonArray procedureNodeCatalog(WorkspaceState state) {
		JsonArray catalog = new JsonArray();
		procedureNode(catalog, "controls_if", "control", "条件", "statement", true, null);
		procedureNode(catalog, "controls_repeat_ext", "control", "重复循环", "statement", true, null);
		procedureNode(catalog, "controls_while", "control", "条件循环", "statement", true, null);
		procedureNode(catalog, "math_number", "value", "数值", "number", true, null);
		procedureNode(catalog, "math_binary_ops", "value", "数值运算", "number", true, null);
		procedureNode(catalog, "text", "value", "文本", "string", true, null);
		procedureNode(catalog, "logic_boolean", "value", "布尔值", "logic", true, null);
		procedureNode(catalog, "logic_binary_ops", "value", "布尔运算", "logic", true, null);
		procedureNode(catalog, "variables_get_number", "variable", "读取变量", "number", true, null);
		procedureNode(catalog, "variables_set_number", "variable", "设置变量", "statement", true, null);
		procedureNode(catalog, "entity_from_deps", "context", "上下文实体", "entity", true, null);
		procedureNode(catalog, "coord_x", "context", "上下文 X", "number", true, null);
		procedureNode(catalog, "coord_y", "context", "上下文 Y", "number", true, null);
		procedureNode(catalog, "coord_z", "context", "上下文 Z", "number", true, null);
		procedureNode(catalog, "mcitem_all", "context", "物品引用", "itemstack", true, null);
		procedureNode(catalog, "call_procedure", "procedure", "调用 Procedure", "statement", true, null);
		boolean javaGenerator = !state.generator().has("loader")
				|| !state.generator().get("loader").getAsString().equals("resource_pack");
		procedureNode(catalog, "return_number", "procedure", "返回数值", "statement", javaGenerator,
				javaGenerator ? null : "PROCEDURE_RETURN_UNSUPPORTED");
		return catalog;
	}

	private void procedureNode(JsonArray target, String type, String category, String label, String output,
			boolean available, String reasonCode) {
		JsonObject node = new JsonObject();
		node.addProperty("type", type);
		node.addProperty("category", category);
		node.add("label", localized("procedure.node." + type, label));
		node.addProperty("output", output);
		node.addProperty("availability", available ? "available" : "unavailable");
		if (reasonCode == null) node.add("reasonCode", JsonNull.INSTANCE); else node.addProperty("reasonCode", reasonCode);
		target.add(node);
	}

	private List<Diagnostic> procedureDiagnostics(UUID elementId, List<ProcedureIr.ValidationIssue> issues) {
		List<Diagnostic> diagnostics = new ArrayList<>();
		for (ProcedureIr.ValidationIssue issue : issues) {
			String path = elementPath(elementId) + "/procedureIr";
			if (issue.nodeId() != null) path += "/nodes/" + issue.nodeId();
			if (issue.port() != null) path += "/ports/" + issue.port();
			JsonObject args = new JsonObject();
			if (issue.nodeId() != null) args.addProperty("nodeId", issue.nodeId().toString());
			if (issue.port() != null) args.addProperty("port", issue.port());
			diagnostics.add(new Diagnostic(issue.code(), issue.error() ? UiCore.Severity.ERROR : UiCore.Severity.WARNING,
					LocalizedText.of("diagnostic." + issue.code().toLowerCase(Locale.ROOT), issue.message(), args),
					path, elementId, true, List.of(new ActionHint("open_procedure_node",
							LocalizedText.of("action.open_procedure_node", "Locate node"), "open_field", path))));
		}
		return List.copyOf(diagnostics);
	}

	private QueryResult task(Query query, WorkspaceState state) {
		UUID taskId = UUID.fromString(requiredString(query.payload(), "taskId"));
		long afterLogSequence = query.payload().has("afterLogSequence")
				? requiredLong(query.payload(), "afterLogSequence") : 0;
		JsonObject task = tasks.find(state.id(), taskId).orElse(null);
		if (task == null)
			return queryFailure(query, state.revision(), diagnostic("TASK_NOT_FOUND", "diagnostic.task_not_found",
					"The requested task does not exist.", "/taskId", null));
		JsonObject projection = new JsonObject();
		projection.add("task", task);
		projection.add("logs", GSON.toJsonTree(tasks.logsAfter(state.id(), taskId, afterLogSequence)));
		projection.add("diagnostics", GSON.toJsonTree(tasks.diagnostics(state.id(), taskId)));
		return querySuccess(query, state.revision(), projection);
	}

	private void flattenFields(JsonObject object, String base, JsonArray target, boolean readOnly, String elementType) {
		for (String key : object.keySet()) {
			JsonElement value = object.get(key);
			String path = base + "/" + key.replace("~", "~0").replace("/", "~1");
			if (value.isJsonObject())
				flattenFields(value.getAsJsonObject(), path, target, readOnly, elementType);
			else
				target.add(editorField(path, displayName(key), value, readOnly, elementType));
		}
	}

	private JsonObject editorField(String path, String label, Object value, boolean readOnly, String elementType) {
		JsonObject field = new JsonObject();
		field.addProperty("path", path);
		field.add("label", localized("field." + path.substring(path.lastIndexOf('/') + 1), label));
		String fieldName = editorFieldName(path);
		String normalizedFieldName = fieldName.toLowerCase(Locale.ROOT);
		String control = value instanceof JsonElement element && element.isJsonArray() ? "json" : "text";
		if (fieldName.equals("code") || fieldName.equals("description") || fieldName.equals("triggerxml"))
			control = "textarea";
		if (isResourceReferenceField(normalizedFieldName)) control = "resource_reference";
		if (isProcedureReferenceField(normalizedFieldName)) control = "procedure_reference";
		if (fieldName.equals("type") || fieldName.equals("frame") || fieldName.equals("toolType")
				|| fieldName.equals("rarity") || fieldName.equals("sentiment") || fieldName.equals("priority")
				|| fieldName.equals("entityType") || (elementType.equals("livingentity")
				&& Set.of("bossBarColor", "bossBarType", "mobBehaviourType", "mobCreatureType", "aiBase")
						.contains(fieldName))) control = "select";
		if (value instanceof JsonElement element && element.isJsonPrimitive()
				&& element.getAsJsonPrimitive().isBoolean()) control = "toggle";
		if (value instanceof JsonElement element && element.isJsonPrimitive()
				&& element.getAsJsonPrimitive().isNumber())
			control = "number";
		field.addProperty("control", control);
		field.addProperty("required", elementType.equals("livingentity")
				&& Set.of("mobName", "mobLabel", "mobModelName", "mobModelTexture").contains(fieldName));
		field.addProperty("readOnly", readOnly);
		if (value instanceof JsonElement element)
			field.add("value", element.deepCopy());
		else
			field.addProperty("value", String.valueOf(value));
		JsonArray options = new JsonArray();
		if (fieldName.equals("frame")) {
			options.add(fieldOption("task", "Task"));
			options.add(fieldOption("goal", "Goal"));
			options.add(fieldOption("challenge", "Challenge"));
		} else if (fieldName.equals("type")) {
			List<String> typeOptions = switch (elementType) {
				case "command" -> List.of("STANDARD", "SINGLEPLAYER_ONLY", "MULTIPLAYER_ONLY", "CLIENTSIDE");
				case "gamerule" -> List.of("Number", "Logic");
				default -> List.of("Generic", "Block", "Entity", "Chest", "Fishing", "Advancement reward", "Gift", "Archaeology");
			};
			for (String option : typeOptions) options.add(fieldOption(option, option));
		} else if (fieldName.equals("toolType")) {
			for (String option : List.of("Pickaxe", "Axe", "Shovel", "Hoe", "Sword", "MultiTool"))
				options.add(fieldOption(option, option));
		} else if (fieldName.equals("rarity")) {
			for (String option : List.of("COMMON", "UNCOMMON", "RARE", "EPIC")) options.add(fieldOption(option, option));
		} else if (fieldName.equals("sentiment")) {
			for (String option : List.of("POSITIVE", "NEUTRAL", "NEGATIVE")) options.add(fieldOption(option, option));
		} else if (fieldName.equals("priority")) {
			for (String option : List.of("NORMAL", "HIGH", "HIGHEST", "LOW", "LOWEST")) options.add(fieldOption(option, option));
		} else if (fieldName.equals("entityType")) {
			for (String option : List.of("Boat", "ChestBoat", "Raft", "ChestRaft")) options.add(fieldOption(option, option));
		} else if (elementType.equals("livingentity") && fieldName.equals("bossBarColor")) {
			for (String option : List.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE"))
				options.add(fieldOption(option, option));
		} else if (elementType.equals("livingentity") && fieldName.equals("bossBarType")) {
			for (String option : List.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20"))
				options.add(fieldOption(option, option));
		} else if (elementType.equals("livingentity") && fieldName.equals("mobBehaviourType")) {
			for (String option : List.of("Mob", "Creature", "Raider")) options.add(fieldOption(option, option));
		} else if (elementType.equals("livingentity") && fieldName.equals("mobCreatureType")) {
			for (String option : List.of("UNDEFINED", "UNDEAD", "ARTHROPOD", "ILLAGER", "WATER"))
				options.add(fieldOption(option, option));
		} else if (elementType.equals("livingentity") && fieldName.equals("aiBase")) {
			for (String option : List.of("(none)", "Bat", "Blaze", "Chicken", "Cow", "Creeper", "Enderman", "Horse",
					"IronGolem", "MagmaCube", "Ocelot", "Pig", "Skeleton", "Slime", "Spider", "Squid",
					"Villager", "Witch", "Wolf", "Zombie")) options.add(fieldOption(option, option));
		}
		field.add("options", options);
		JsonObject constraints = editorConstraints(elementType, fieldName);
		if (constraints != null) field.add("constraints", constraints);
		field.add("diagnostics", new JsonArray());
		return field;
	}

	private static String editorFieldName(String path) {
		return path.substring(path.lastIndexOf('/') + 1).replace("~1", "/").replace("~0", "~");
	}

	private static boolean isResourceReferenceField(String fieldName) {
		String field = fieldName.toLowerCase(Locale.ROOT);
		return field.equals("icon") || field.contains("texture") || field.contains("sound") || field.contains("music")
				|| field.contains("font") || field.equals("basetexture") || field.equals("armortexturefile");
	}

	private static boolean isProcedureReferenceField(String fieldName) {
		String field = fieldName.toLowerCase(Locale.ROOT);
		return field.equals("parent") || field.equals("rewardfunction") || field.startsWith("on")
				|| field.startsWith("when") || field.endsWith("condition") || field.equals("breatheunderwater")
				|| field.equals("pushedbyfluids") || field.equals("visualscale") || field.equals("boundingboxscale")
				|| field.equals("solidboundingbox") || field.equals("vibrationsensitivityradius");
	}

	private JsonObject editorConstraints(String elementType, String fieldName) {
		if (!elementType.equals("livingentity")) return null;
		double[] constraint = switch (fieldName) {
			case "modelWidth", "modelHeight", "modelShadowSize" -> new double[] { 0, 16, 0.1 };
			case "mountedYOffset" -> new double[] { -1024, 1024, 0.1 };
			case "attackStrength" -> new double[] { 0, 10000, 1 };
			case "attackKnockback", "knockbackResistance" -> new double[] { 0, 1000, 0.1 };
			case "movementSpeed" -> new double[] { 0, 50, 0.1 };
			case "stepHeight" -> new double[] { 0, 255, 0.1 };
			case "armorBaseValue" -> new double[] { 0, 100, 0.1 };
			case "trackingRange" -> new double[] { 0, 2048, 1 };
			case "followRange", "health", "rangedAttackInterval" -> new double[] { 0, 1024, 1 };
			case "xpAmount" -> new double[] { 0, 100000, 1 };
			case "inventorySize" -> new double[] { 0, 256, 1 };
			case "inventoryStackSize" -> new double[] { 1, 1024, 1 };
			case "rangedAttackRadius" -> new double[] { 0, 1024, 0.1 };
			case "spawningProbability" -> new double[] { 1, 1000, 1 };
			case "minNumberOfMobsPerGroup", "maxNumberOfMobsPerGroup" -> new double[] { 1, 128, 1 };
			default -> null;
		};
		if (constraint == null) return null;
		JsonObject constraints = new JsonObject();
		constraints.addProperty("min", constraint[0]);
		constraints.addProperty("max", constraint[1]);
		constraints.addProperty("step", constraint[2]);
		return constraints;
	}

	private JsonObject fieldOption(String value, String label) {
		JsonObject option = new JsonObject();
		option.addProperty("value", value);
		JsonObject args = new JsonObject();
		args.addProperty("label", label);
		option.add("label", localized("field.option", label, args));
		option.addProperty("disabled", false);
		option.add("reason", JsonNull.INSTANCE);
		return option;
	}

	private JsonArray capabilities(RequestContext context) {
		JsonArray capabilities = new JsonArray();
		capabilities.add(capability("mod_elements.create", context.permission() == PermissionProfile.READ_ONLY
				? "unavailable" : "available"));
		capabilities.add(capability("workspace.build", context.permission() == PermissionProfile.READ_ONLY
				? "unavailable" : "available"));
		capabilities.add(capability("workspace.migrate_loader", context.permission() == PermissionProfile.READ_ONLY
				? "unavailable" : "available"));
		capabilities.add(capability("workspace.import_upstream",
				context.permission() == PermissionProfile.FULL_ACCESS ? "available" : "unavailable"));
		capabilities.add(capability("assets.publish_batch", context.permission() == PermissionProfile.READ_ONLY
				? "unavailable" : "available"));
		return capabilities;
	}

	private JsonObject capability(String id, String availability) {
		JsonObject capability = new JsonObject();
		capability.addProperty("id", id);
		capability.addProperty("availability", availability);
		if (availability.equals("available")) {
			capability.add("reasonCode", JsonNull.INSTANCE);
			capability.add("message", JsonNull.INSTANCE);
		} else {
			capability.addProperty("reasonCode", "PERMISSION_DENIED");
			capability.add("message", localized("capability.permission_denied", "Current permission is read only."));
		}
		capability.add("affectedPaths", new JsonArray());
		return capability;
	}

	private JsonObject elementSummary(Element element) {
		JsonObject summary = new JsonObject();
		summary.addProperty("id", element.id().toString());
		summary.addProperty("type", element.type());
		summary.addProperty("name", element.name());
		summary.addProperty("displayName", element.displayName());
		summary.addProperty("state", element.state());
		summary.addProperty("ownership", element.ownership());
		summary.addProperty("updatedAt", element.updatedAt().toString());
		summary.addProperty("firstParty", ElementCoverageCatalog.isFirstParty(element.type()));
		summary.add("diagnostics", counts());
		return summary;
	}

	private JsonObject elementCounts(List<Element> elements) {
		JsonObject counts = new JsonObject();
		counts.addProperty("total", elements.size());
		for (String state : List.of("valid", "invalid", "draft", "unsupported"))
			counts.addProperty(state, elements.stream().filter(element -> element.state().equals(state)).count());
		return counts;
	}

	private JsonObject defaultElementValues(String type, String name, JsonObject supplied) {
		JsonObject values = supplied.deepCopy();
		if (!values.has("displayName")) values.addProperty("displayName", displayName(name));
		// Every Stage 11 type gets a stable editable identity and description field. Type-specific
		// values supplied by an imported workspace are retained and rendered below these fields.
		if (!values.has("name")) values.addProperty("name", name);
		if (!values.has("description")) values.addProperty("description", "");
		switch (type) {
			case "item" -> {
				if (!values.has("texture")) values.addProperty("texture", "minecraft:barrier");
			}
			case "armor" -> {
				if (!values.has("enableHelmet")) values.addProperty("enableHelmet", true);
				if (!values.has("enableBody")) values.addProperty("enableBody", true);
				if (!values.has("enableLeggings")) values.addProperty("enableLeggings", true);
				if (!values.has("enableBoots")) values.addProperty("enableBoots", true);
			}
			case "tool" -> {
				if (!values.has("toolType")) values.addProperty("toolType", "Pickaxe");
				if (!values.has("customModelName")) values.addProperty("customModelName", "Normal");
				if (!values.has("blockingModelName")) values.addProperty("blockingModelName", "Normal");
				if (!values.has("efficiency")) values.addProperty("efficiency", 4.0);
				if (!values.has("attackSpeed")) values.addProperty("attackSpeed", 1.0);
				if (!values.has("usageCount")) values.addProperty("usageCount", 100);
			}
			case "itemextension" -> {
				if (!values.has("enableFuel")) values.addProperty("enableFuel", false);
				if (!values.has("hasDispenseBehavior")) values.addProperty("hasDispenseBehavior", false);
			}
			case "attribute" -> {
				if (!values.has("defaultValue")) values.addProperty("defaultValue", 0.0);
				if (!values.has("minValue")) values.addProperty("minValue", 0.0);
				if (!values.has("maxValue")) values.addProperty("maxValue", 1.0);
			}
			case "bannerpattern" -> {
				if (!values.has("requireItem")) values.addProperty("requireItem", true);
			}
			case "command" -> {
				if (!values.has("commandName")) values.addProperty("commandName", name);
				if (!values.has("type")) values.addProperty("type", "STANDARD");
				if (!values.has("permissionLevel")) values.addProperty("permissionLevel", "4");
			}
			case "damagetype" -> {
				if (!values.has("normalDeathMessage")) values.addProperty("normalDeathMessage", " was hurt");
			}
			case "enchantment" -> {
				if (!values.has("isTreasureEnchantment")) values.addProperty("isTreasureEnchantment", false);
				if (!values.has("isCurse")) values.addProperty("isCurse", false);
			}
			case "gamerule" -> {
				if (!values.has("type")) values.addProperty("type", "Logic");
				if (!values.has("category")) values.addProperty("category", "MISC");
				if (!values.has("defaultValueLogic")) values.addProperty("defaultValueLogic", false);
			}
			case "painting" -> {
				if (!values.has("title")) values.addProperty("title", displayName(name));
				if (!values.has("author")) values.addProperty("author", "Copperbench");
			}
			case "particle" -> {
				if (!values.has("animate")) values.addProperty("animate", true);
				if (!values.has("fixedScale")) values.addProperty("fixedScale", false);
			}
			case "potion" -> {
				if (!values.has("potionName")) values.addProperty("potionName", displayName(name));
				if (!values.has("duration")) values.addProperty("duration", 3600);
			}
			case "potioneffect" -> {
				if (!values.has("effectName")) values.addProperty("effectName", displayName(name));
				if (!values.has("isInstant")) values.addProperty("isInstant", false);
			}
			case "tab" -> {
				if (!values.has("showSearch")) values.addProperty("showSearch", false);
			}
			case "biome" -> {
				if (!values.has("spawnParticles")) values.addProperty("spawnParticles", true);
				if (!values.has("spawnInCaves")) values.addProperty("spawnInCaves", false);
			}
			case "dimension" -> {
				if (!values.has("generateOreVeins")) values.addProperty("generateOreVeins", true);
				if (!values.has("generateAquifers")) values.addProperty("generateAquifers", true);
			}
			case "feature" -> {
				if (!values.has("skipPlacement")) values.addProperty("skipPlacement", false);
			}
			case "fluid" -> {
				if (!values.has("bucketName")) values.addProperty("bucketName", displayName(name) + " Bucket");
				if (!values.has("generateBucket")) values.addProperty("generateBucket", true);
			}
			case "plant" -> {
				if (!values.has("renderType")) values.addProperty("renderType", 0);
				if (!values.has("unbreakable")) values.addProperty("unbreakable", false);
			}
			case "structure" -> {
				if (!values.has("useStartHeight")) values.addProperty("useStartHeight", false);
				if (!values.has("poolName")) values.addProperty("poolName", name);
			}
			case "livingentity" -> {
				if (!values.has("mobName")) values.addProperty("mobName", name);
				if (!values.has("mobLabel")) values.addProperty("mobLabel", displayName(name));
				if (!values.has("hasSpawnEgg")) values.addProperty("hasSpawnEgg", true);
				if (!values.has("isBoss")) values.addProperty("isBoss", false);
			}
			case "projectile" -> {
				if (!values.has("projectileItem")) values.addProperty("projectileItem", "Items.ARROW");
				if (!values.has("entityModel")) values.addProperty("entityModel", "Default");
				if (!values.has("customModelTexture")) values.addProperty("customModelTexture", "");
				if (!values.has("actionSound")) values.addProperty("actionSound", "");
				if (!values.has("power")) values.addProperty("power", 1.0);
				if (!values.has("damage")) values.addProperty("damage", 5.0);
				if (!values.has("knockback")) values.addProperty("knockback", 5);
				if (!values.has("disableGravity")) values.addProperty("disableGravity", false);
				if (!values.has("igniteFire")) values.addProperty("igniteFire", false);
				if (!values.has("disableDiscarding")) values.addProperty("disableDiscarding", false);
				if (!values.has("showParticles")) values.addProperty("showParticles", false);
			}
			case "gui" -> {
				if (!values.has("renderBgLayer")) values.addProperty("renderBgLayer", true);
				if (!values.has("doesPauseGame")) values.addProperty("doesPauseGame", false);
			}
			case "armortrim" -> {
				if (!values.has("armorTextureFile")) values.addProperty("armorTextureFile", name);
			}
			case "keybind" -> {
				if (!values.has("keyBindingName")) values.addProperty("keyBindingName", displayName(name));
				if (!values.has("keyBindingCategoryKey")) values.addProperty("keyBindingCategoryKey", "key.categories.misc");
			}
			case "villagerprofession" -> {
				if (!values.has("displayName")) values.addProperty("displayName", displayName(name));
			}
			case "specialentity" -> {
				if (!values.has("name")) values.addProperty("name", name);
				if (!values.has("entityType")) values.addProperty("entityType", "Boat");
				if (!values.has("rarity")) values.addProperty("rarity", "COMMON");
			}
			case "overlay" -> {
				if (!values.has("priority")) values.addProperty("priority", "NORMAL");
				if (!values.has("baseTexture")) values.addProperty("baseTexture", "");
			}
			case "villagertrade" -> {
				if (!values.has("villagerProfession")) values.addProperty("villagerProfession", "WANDERING_TRADER");
				if (!values.has("trades")) values.add("trades", new JsonArray());
			}
			case "code" -> {
				if (!values.has("code")) values.addProperty("code", "");
			}
			case "function" -> {
				if (!values.has("name")) values.addProperty("name", name);
				if (!values.has("namespace")) values.addProperty("namespace", "mod");
				if (!values.has("code") && !values.has("commands"))
					values.addProperty("code", "# New Copperbench function\n");
			}
			case "loottable" -> {
				if (!values.has("name")) values.addProperty("name", name);
				if (!values.has("namespace")) values.addProperty("namespace", "mod");
				if (!values.has("type")) values.addProperty("type", "Generic");
				if (!values.has("pools")) values.add("pools", new JsonArray());
			}
			case "achievement" -> {
				if (!values.has("title")) values.addProperty("title", displayName(name));
				if (!values.has("description")) values.addProperty("description", "");
				if (!values.has("icon")) values.addProperty("icon", "Blocks.STONE");
				if (!values.has("frame")) values.addProperty("frame", "task");
				if (!values.has("parent")) values.addProperty("parent", "ROOT");
				if (!values.has("showPopup")) values.addProperty("showPopup", true);
				if (!values.has("announceToChat")) values.addProperty("announceToChat", true);
				if (!values.has("rewardXP")) values.addProperty("rewardXP", 0);
				if (!values.has("rewardLoot")) values.add("rewardLoot", new JsonArray());
				if (!values.has("rewardRecipes")) values.add("rewardRecipes", new JsonArray());
			}
			default -> {
			}
		}
		return values;
	}

	private Diagnostic validateElementValues(UUID elementId, JsonObject values) {
		if (values.has("fields") && values.get("fields").isJsonObject()) {
			JsonObject fields = values.getAsJsonObject("fields");
			if (fields.has("hardness") && fields.get("hardness").isJsonPrimitive()
					&& fields.getAsJsonPrimitive("hardness").isNumber()) {
				double hardness = fields.get("hardness").getAsDouble();
				if (hardness < 0 || hardness > 100) {
					JsonObject args = new JsonObject();
					args.addProperty("min", 0);
					args.addProperty("max", 100);
					return diagnostic("FIELD_VALUE_OUT_OF_RANGE", "diagnostic.field_value_out_of_range",
							"Hardness must be between {min} and {max}.", args,
							elementPath(elementId) + "/fields/hardness", elementId);
				}
			}
		}
		return null;
	}

	private CommandOutcome denied(Command command, PermissionProfile current, PermissionProfile required) {
		JsonObject denial = new JsonObject();
		denial.addProperty("currentProfile", wire(current));
		denial.addProperty("requiredProfile", wire(required));
		denial.addProperty("approvalRequired", true);
		denial.addProperty("protectedOperation", false);
		Diagnostic diagnostic = diagnostic("PERMISSION_DENIED", "diagnostic.permission_denied",
				"Current permission does not allow this operation.", null, null);
		return new CommandOutcome(result(command, "rejected", currentRevision(command.workspaceId()), JsonNull.INSTANCE,
				JsonNull.INSTANCE, List.of(diagnostic), JsonNull.INSTANCE, denial), List.of());
	}

	private CommandOutcome failed(Command command, long revision, Diagnostic diagnostic) {
		return new CommandOutcome(result(command, "rejected", revision, JsonNull.INSTANCE, JsonNull.INSTANCE,
				List.of(diagnostic), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of());
	}

	private CommandResult result(Command command, String status, long revision, JsonElement task, JsonElement data,
			List<Diagnostic> diagnostics, JsonElement conflict, JsonElement denial) {
		return result(command, status, revision, null, task, data, diagnostics, conflict, denial);
	}

	private CommandResult result(Command command, String status, long revision, RecoveryPoint recoveryPoint,
			JsonElement task, JsonElement data, List<Diagnostic> diagnostics, JsonElement conflict, JsonElement denial) {
		return new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(), command.workspaceId(),
				command.operation(), status, revision, recoveryPoint == null ? null : recoveryPoint.id(), task, data,
				diagnostics, conflict, denial);
	}

	private Event event(Command command, long revision, long sequence, String eventName, JsonObject payload) {
		return new Event("event", UiCore.SCHEMA_VERSION, ids.get(), command.workspaceId(), revision, sequence,
				clock.instant().toString(), eventName, command.requestId(), payload);
	}

	private QueryResult querySuccess(Query query, long revision, JsonElement data) {
		return new QueryResult("query_result", UiCore.SCHEMA_VERSION, query.requestId(), query.workspaceId(),
				query.operation(), "succeeded", revision, data, List.of());
	}

	private QueryResult queryFailure(Query query, long revision, Diagnostic diagnostic) {
		return new QueryResult("query_result", UiCore.SCHEMA_VERSION, query.requestId(), query.workspaceId(),
				query.operation(), "rejected", revision, JsonNull.INSTANCE, List.of(diagnostic));
	}

	private Diagnostic workspaceNotFound() {
		return diagnostic("WORKSPACE_NOT_FOUND", "diagnostic.workspace_not_found",
				"The requested workspace is not open.", null, null);
	}

	private Diagnostic elementNotFound(UUID elementId) {
		return diagnostic("MOD_ELEMENT_NOT_FOUND", "diagnostic.mod_element_not_found",
				"The requested mod element does not exist.", elementPath(elementId), elementId);
	}

	private Diagnostic invalidPayload(String detail) {
		return diagnostic("COMMAND_PAYLOAD_INVALID", "diagnostic.command_payload_invalid",
				"The command payload is invalid.", null, null);
	}

	private Diagnostic diagnostic(String code, String key, String fallback, String path, UUID elementId) {
		return Diagnostic.error(code, key, fallback, path, elementId);
	}

	private Diagnostic diagnostic(String code, String key, String fallback, JsonObject args, String path,
			UUID elementId) {
		return Diagnostic.error(code, key, fallback, args, path, elementId);
	}

	private Diagnostic failureDiagnostic(Command command, String code, String key, String fallback, String path,
			UUID elementId, Throwable cause) {
		return failureDiagnostic(command.requestId(), command.workspaceId(), code, key, fallback, path, elementId,
				cause);
	}

	private Diagnostic failureDiagnostic(Query query, String code, String key, String fallback, String path,
			UUID elementId, Throwable cause) {
		return failureDiagnostic(query.requestId(), query.workspaceId(), code, key, fallback, path, elementId, cause);
	}

	private Diagnostic failureDiagnostic(UUID requestId, UUID workspaceId, String code, String key, String fallback,
			String path, UUID elementId, Throwable cause) {
		String failureId = UUID.randomUUID().toString();
		LOG.error(OPERATION_FAILURE, "Copperbench failure {} (code={}, requestId={}, workspaceId={})", failureId, code, requestId,
				workspaceId, cause);
		JsonObject args = new JsonObject();
		args.addProperty("failureId", failureId);
		ActionHint openLogs = new ActionHint("open_logs", LocalizedText.of("action.open_logs", "View logs"),
				"open_logs", failureId);
		return new Diagnostic(code, UiCore.Severity.ERROR, LocalizedText.of(key, fallback, args), path, elementId,
				true, List.of(openLogs));
	}

	private String requiredRegistry(JsonObject payload) {
		String registry = requiredString(payload, "registry");
		if (!REGISTRY_NAMES.contains(registry)) throw new IllegalArgumentException("Unsupported registry: " + registry);
		return registry;
	}

	private RegistryLocation findRegistryEntry(JsonObject registries, UUID entryId) {
		for (String registry : REGISTRY_NAMES) {
			JsonArray entries = registries.getAsJsonArray(registry);
			for (int index = 0; index < entries.size(); index++) {
				JsonObject entry = entries.get(index).getAsJsonObject();
				if (entry.has("id") && entryId.toString().equals(entry.get("id").getAsString()))
					return new RegistryLocation(registry, entries, index, entry);
			}
		}
		return null;
	}

	private Diagnostic validateRegistryName(String registry, JsonObject entry, String name, JsonObject registries,
			UUID ignoredId) {
		boolean valid = switch (registry) {
			case "variables" -> VARIABLE_NAME.matcher(name).matches();
			case "tags" -> RESOURCE_PATH.matcher(name).matches()
					&& RESOURCE_PATH.matcher(entry.has("namespace") ? entry.get("namespace").getAsString() : "mod").matches();
			case "languageKeys" -> LANGUAGE_KEY.matcher(name).matches();
			default -> false;
		};
		if (!valid) return diagnostic("REGISTRY_ENTRY_NAME_INVALID", "diagnostic.registry_entry_name_invalid",
				"The registry entry name is invalid.", "/newName", null);
		for (JsonElement raw : registries.getAsJsonArray(registry)) {
			JsonObject candidate = raw.getAsJsonObject();
			if (ignoredId != null && candidate.has("id") && ignoredId.toString().equals(candidate.get("id").getAsString()))
				continue;
			if (!registryName(registry, candidate).equals(name)) continue;
			if (!registry.equals("tags") || (string(candidate, "namespace", "mod")
					.equals(string(entry, "namespace", "mod")) && string(candidate, "category", "items")
					.equals(string(entry, "category", "items"))))
				return diagnostic("REGISTRY_ENTRY_NAME_CONFLICT", "diagnostic.registry_entry_name_conflict",
						"A registry entry with this name already exists.", "/newName", null);
		}
		return null;
	}

	private Diagnostic registryEntryNotFound(UUID entryId) {
		return diagnostic("REGISTRY_ENTRY_NOT_FOUND", "diagnostic.registry_entry_not_found",
				"The requested registry entry does not exist.", "/registries/" + entryId, null);
	}

	private static String registryName(String registry, JsonObject entry) {
		return requiredString(entry, registry.equals("languageKeys") ? "key" : "name");
	}

	private static String registryKind(String registry) {
		return switch (registry) {
			case "variables" -> "variable";
			case "tags" -> "tag";
			case "languageKeys" -> "language_key";
			default -> throw new IllegalArgumentException("Unsupported registry: " + registry);
		};
	}

	private static JsonObject registrySupport(String state, String reasonCode) {
		JsonObject support = new JsonObject();
		support.addProperty("state", state);
		support.addProperty("reasonCode", reasonCode);
		return support;
	}

	private static JsonObject languageStats(JsonArray entries) {
		Set<String> languages = new HashSet<>();
		Set<String> keys = new HashSet<>();
		int duplicates = 0;
		for (JsonElement raw : entries) {
			JsonObject entry = raw.getAsJsonObject();
			if (!keys.add(string(entry, "key", ""))) duplicates++;
			if (entry.has("translations") && entry.get("translations").isJsonObject())
				languages.addAll(entry.getAsJsonObject("translations").keySet());
		}
		int missing = 0;
		for (JsonElement raw : entries) {
			JsonObject translations = raw.getAsJsonObject().has("translations")
					&& raw.getAsJsonObject().get("translations").isJsonObject()
					? raw.getAsJsonObject().getAsJsonObject("translations") : new JsonObject();
			for (String language : languages)
				if (!translations.has(language) || translations.get(language).getAsString().isBlank()) missing++;
		}
		JsonObject stats = new JsonObject();
		stats.addProperty("keyCount", entries.size());
		stats.addProperty("languageCount", languages.size());
		stats.addProperty("missingTranslationCount", missing);
		stats.addProperty("duplicateKeyCount", duplicates);
		return stats;
	}

	private static int distinctReferenceSources(JsonObject projection) {
		Set<String> sources = new HashSet<>();
		projection.getAsJsonArray("edges").forEach(raw -> sources.add(raw.getAsJsonObject().get("sourceId").getAsString()));
		return sources.size();
	}

	private boolean rewriteRegistryReferences(JsonElement value, String registry, String entryId, String oldName,
			String newName, String path) {
		boolean changed = false;
		if (value == null || value.isJsonNull()) return false;
		if (value.isJsonObject()) {
			JsonObject object = value.getAsJsonObject();
			for (String key : List.copyOf(object.keySet())) {
				JsonElement child = object.get(key);
				String childPath = path + "/" + key;
				if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()
						&& child.getAsString().equals(oldName) && registryReferenceField(registry, key, path)) {
					object.addProperty(key, newName);
					changed = true;
				} else changed |= rewriteRegistryReferences(child, registry, entryId, oldName, newName, childPath);
			}
		} else if (value.isJsonArray()) {
			JsonArray array = value.getAsJsonArray();
			for (int index = 0; index < array.size(); index++)
				changed |= rewriteRegistryReferences(array.get(index), registry, entryId, oldName, newName,
						path + "/" + index);
		}
		return changed;
	}

	private static boolean registryReferenceField(String registry, String key, String parentPath) {
		String normalized = key.toLowerCase(Locale.ROOT);
		return switch (registry) {
			case "variables" -> normalized.contains("variable") || normalized.equals("var")
					|| normalized.equals("name") && parentPath.contains("/procedureIr/dependencies");
			case "tags" -> normalized.contains("tag");
			case "languageKeys" -> normalized.contains("language") || normalized.contains("translation");
			default -> false;
		};
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && object.get(key).isJsonPrimitive()
				? object.get(key).getAsString() : fallback;
	}

	private long currentRevision(UUID workspaceId) {
		return store.read(workspaceId).map(WorkspaceState::revision).orElse(0L);
	}

	private static String requiredString(JsonObject object, String property) {
		if (object == null || !object.has(property) || !object.get(property).isJsonPrimitive())
			throw new IllegalArgumentException("Missing string property " + property);
		return object.get(property).getAsString();
	}

	private static String optionalString(JsonObject object, String property) {
		if (object == null || !object.has(property) || !object.get(property).isJsonPrimitive())
			return null;
		return object.get(property).getAsString();
	}

	private static int requiredInt(JsonObject object, String property) {
		if (object == null || !object.has(property) || !object.get(property).isJsonPrimitive())
			throw new IllegalArgumentException("Missing integer property " + property);
		return object.get(property).getAsInt();
	}

	private static Set<String> stringSet(JsonArray values) {
		if (values == null)
			throw new IllegalArgumentException("Missing array property");
		Set<String> result = new HashSet<>();
		values.forEach(value -> result.add(value.getAsString()));
		return result;
	}

	private static String displayName(String value) {
		String[] words = value.split("_");
		List<String> displayWords = new ArrayList<>();
		for (String word : words)
			displayWords.add(word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		return String.join(" ", displayWords);
	}

	private static String elementPath(UUID elementId) {
		return "/elements/" + elementId;
	}

	private static JsonObject localized(String key, String fallback) {
		return localized(key, fallback, new JsonObject());
	}

	private static long requiredLong(JsonObject object, String property) {
		if (object == null || !object.has(property) || !object.get(property).isJsonPrimitive())
			throw new IllegalArgumentException("Missing integer property " + property);
		long value = object.get(property).getAsLong();
		if (value < 0) throw new IllegalArgumentException(property + " must not be negative");
		return value;
	}

	private static JsonObject localized(String key, String fallback, JsonObject args) {
		JsonObject value = new JsonObject();
		value.addProperty("key", key);
		value.addProperty("fallback", fallback);
		value.add("args", args == null ? new JsonObject() : args.deepCopy());
		return value;
	}

	private static JsonObject counts() {
		JsonObject counts = new JsonObject();
		counts.addProperty("error", 0);
		counts.addProperty("warning", 0);
		counts.addProperty("info", 0);
		return counts;
	}

	private static JsonArray toArray(List<JsonObject> values) {
		JsonArray array = new JsonArray();
		values.forEach(value -> array.add(value.deepCopy()));
		return array;
	}

	private static String wire(UiCore.Actor actor) {
		return switch (actor) {
			case UI -> "ui";
			case MCP -> "mcp";
			case HEADLESS -> "headless";
			case LEGACY_UI -> "legacy_ui";
			case SYSTEM -> "system";
		};
	}

	private static String wire(PermissionProfile profile) {
		return switch (profile) {
			case READ_ONLY -> "read_only";
			case WORKSPACE -> "workspace";
			case FULL_ACCESS -> "full_access";
		};
	}

	private record Mutation(Element element, long sequence, Diagnostic diagnostic) {
		private static Mutation success(Element element, long sequence) {
			return new Mutation(element, sequence, null);
		}

		private static Mutation rejected(Diagnostic diagnostic) {
			return new Mutation(null, 0, diagnostic);
		}
	}

	private record TaskMutation(JsonObject task, long sequence) {
	}

	private record RegistryLocation(String registry, JsonArray entries, int index, JsonObject entry) {
	}

	private record RegistryEdit(JsonObject entry, JsonObject data, List<String> changedPaths) {
	}

	private record RegistryMutation(JsonObject entry, long sequence, JsonObject data, Diagnostic diagnostic) {
		private static RegistryMutation success(JsonObject entry, long sequence, JsonObject data) {
			return new RegistryMutation(entry.deepCopy(), sequence, data.deepCopy(), null);
		}

		private static RegistryMutation rejected(Diagnostic diagnostic) {
			return new RegistryMutation(null, 0, null, diagnostic);
		}
	}

	private record DatagenMutation(JsonObject data, long sequence, Diagnostic diagnostic) {
		private static DatagenMutation success(JsonObject data, long sequence) {
			return new DatagenMutation(data.deepCopy(), sequence, null);
		}

		private static DatagenMutation rejected(Diagnostic diagnostic) {
			return new DatagenMutation(null, 0, diagnostic);
		}
	}

	private static final class RegistryValidationException extends RuntimeException {
		private final Diagnostic diagnostic;

		private RegistryValidationException(Diagnostic diagnostic) {
			super(diagnostic.code());
			this.diagnostic = diagnostic;
		}

		private Diagnostic diagnostic() { return diagnostic; }
	}

	private record WorkspaceCreationMutation(WorkspaceCreationService.CreationResult creation, long sequence) {
	}

	private static final class ListCursorException extends IllegalArgumentException {
		private final String code;

		private ListCursorException(String code, String message) {
			super(message);
			this.code = code;
		}

		private static ListCursorException invalid(String message) {
			return new ListCursorException("LIST_CURSOR_INVALID", message);
		}

		private static ListCursorException stale(String message) {
			return new ListCursorException("LIST_CURSOR_STALE", message);
		}

		private String code() {
			return code;
		}
	}
}
