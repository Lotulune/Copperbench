package dev.copperbench.core.application;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.CommandResult;
import dev.copperbench.core.contract.UiCore.Diagnostic;
import dev.copperbench.core.contract.UiCore.Event;
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
import dev.copperbench.assets.AssetPathViolationException;
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
import dev.copperbench.tracks.VersionTrackCatalog;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Shared command/query service for legacy UI, JCEF, MCP and headless adapters. */
public final class WorkspaceApplicationService {

	private static final Gson GSON = new Gson();
	private static final Pattern ELEMENT_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
	private static final Set<String> ELEMENT_TYPES = Set.of("block", "item", "recipe", "procedure");

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
			case VALIDATE_WORKSPACE, GENERATE_WORKSPACE, BUILD_WORKSPACE, EXPORT_WORKSPACE, RUN_CLIENT ->
					startTask(command);
			case CANCEL_TASK -> cancelTask(command);
			case CREATE_RECOVERY_POINT -> createRecoveryPoint(command, context);
			case RESTORE_RECOVERY_POINT -> restoreRecoveryPoint(command, context);
			case EXECUTE_LOADER_MIGRATION -> executeLoaderMigration(command, context);
			case IMPORT_UPSTREAM_WORKSPACE -> importUpstreamWorkspace(command, context);
			case CREATE_PUBLISH_BATCH -> createPublishBatch(command, context);
			case PREPARE_RESOURCE_PACK_CLIENT -> prepareResourcePackClient(command, context);
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
				case LIST_MOD_ELEMENTS -> querySuccess(query, state.revision(), elementList(state, query.payload()));
				case GET_MOD_ELEMENT_EDITOR -> editor(query, state, context);
				case PREVIEW_MOD_ELEMENT_CHANGE -> preview(query, state);
				case GET_TASK -> task(query, state);
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
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), invalidPayload(exception.getMessage()));
		}
	}

	private CommandOutcome createWorkspace(Command command, RequestContext context) {
		if (!approved(command))
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
		WorkspaceCreationService.CreationResult created;
		try {
			created = workspaceCreation.create(generatorId, modName, modId, packageName, workspaceFolderPath, version);
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), diagnostic("WORKSPACE_CREATE_FAILED",
					"diagnostic.workspace_create_failed", "The workspace could not be created.", "/workspaceFolderPath",
					null));
		}
		if (!created.complete()) {
			List<Diagnostic> diagnostics = new ArrayList<>();
			for (String code : created.diagnostics())
				diagnostics.add(diagnostic(code, "diagnostic." + code.toLowerCase(Locale.ROOT).replace('_', '.'),
						"The new workspace form is invalid: " + code + ".", null, null));
			return new CommandOutcome(result(command, "rejected", currentRevision(command.workspaceId()),
					JsonNull.INSTANCE, JsonNull.INSTANCE, diagnostics, JsonNull.INSTANCE, JsonNull.INSTANCE),
					List.of());
		}
		TransactionResult<Long> coordinated = store.coordinate(command.workspaceId(), command.expectedRevision(),
				WorkspaceState::nextEventSequence);
		CommandOutcome conflict = checkFailure(command, coordinated);
		if (conflict != null)
			return conflict;
		JsonObject payload = new JsonObject();
		payload.addProperty("workspaceFile", created.workspaceFile());
		payload.addProperty("generatorId", created.generatorId());
		payload.addProperty("modId", modId);
		Event event = event(command, coordinated.revision(), coordinated.value(), "workspace_created", payload);
		return new CommandOutcome(result(command, "committed", coordinated.revision(), JsonNull.INSTANCE,
				payload.deepCopy(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE), List.of(event));
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
		RecoveryPoint recoveryPoint;
		try {
			recoveryPoint = automationRecoveryPoint(command, context);
		} catch (LocalHistoryException exception) {
			return automationRecoveryFailed(command);
		}

		TransactionResult<Mutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			if (state.hasElementName(name))
				return Decision.abort(Mutation.rejected(diagnostic("MOD_ELEMENT_NAME_CONFLICT",
						"diagnostic.mod_element_name_conflict", "An element with this name already exists.", "/name", null)));
			UUID elementId = ids.get();
			String displayName = initialValues.has("displayName") ? initialValues.get("displayName").getAsString()
					: displayName(name);
			Element element = new Element(elementId, type, name, displayName, "draft", "generated", clock.instant(),
					initialValues);
			state.addElement(element);
			Diagnostic persistenceFailure = persist(before, state, command.operation(), element);
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
			return automationRecoveryFailed(command);
		}

		TransactionResult<Mutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			Element existing = state.element(elementId);
			if (existing == null)
				return Decision.abort(Mutation.rejected(elementNotFound(elementId)));
			if (!ElementCoverageCatalog.isFirstParty(existing.type()))
				return Decision.abort(Mutation.rejected(diagnostic("ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE",
						"diagnostic.element_type_outside_first_party_slice",
						"This element type is outside the first-party slice and cannot be updated in the new UI.",
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
			Element updated = new Element(existing.id(), existing.type(), existing.name(), existing.displayName(),
					"valid", existing.ownership(), clock.instant(), values);
			state.replaceElement(updated);
			Diagnostic persistenceFailure = persist(before, state, command.operation(), updated);
			if (persistenceFailure != null)
				return Decision.abort(Mutation.rejected(persistenceFailure));
			return Decision.commit(Mutation.success(updated, state.nextEventSequence()), changedPaths);
		});
		return mutationOutcome(command, context, transaction, "mod_element_updated", recoveryPoint);
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
			return automationRecoveryFailed(command);
		}

		TransactionResult<Mutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			Element removed = state.removeElement(elementId);
			if (removed == null)
				return Decision.abort(Mutation.rejected(elementNotFound(elementId)));
			Diagnostic persistenceFailure = persist(before, state, command.operation(), removed);
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

	private CommandOutcome automationRecoveryFailed(Command command) {
		return failed(command, currentRevision(command.workspaceId()), diagnostic("RECOVERY_POINT_FAILED",
				"diagnostic.recovery_point_failed",
				"The required recovery point could not be created; the workspace was not changed.", null, null));
	}

	private Diagnostic persist(WorkspaceState before, WorkspaceState after, Operation operation, Element element) {
		try {
			mutations.persist(before, after, operation, element);
			return null;
		} catch (Exception exception) {
			return diagnostic("WORKSPACE_PERSISTENCE_FAILED", "diagnostic.workspace_persistence_failed",
					"The workspace change could not be stored and was rolled back.", null, null);
		}
	}

	private CommandOutcome startTask(Command command) {
		TransactionResult<TaskMutation> check;
		try {
			check = store.coordinate(command.workspaceId(), command.expectedRevision(), state ->
					new TaskMutation(tasks.start(command.workspaceId(), command.operation(), command.payload()),
							state.nextEventSequence()));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), diagnostic("TASK_START_FAILED",
					"diagnostic.task_start_failed", "The requested task could not be started.", null, null));
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
			return failed(command, currentRevision(command.workspaceId()), diagnostic("TASK_CANCEL_FAILED",
					"diagnostic.task_cancel_failed", "The requested task could not be cancelled.", "/taskId", null));
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
			return failed(command, check.revision(), invalidPayload(exception.getMessage()));
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
			return failed(command, currentRevision(command.workspaceId()), invalidPayload(exception.getMessage()));
		}
		CommandOutcome conflict = checkFailure(command, coordinated);
		if (conflict != null)
			return conflict;
		RecoveryPoint point;
		try {
			point = history.createRecoveryPoint(new RecoveryPointRequest(label, context.actor(), ""));
		} catch (LocalHistoryException | RuntimeException exception) {
			return failed(command, coordinated.revision(), diagnostic("RECOVERY_POINT_FAILED",
					"diagnostic.recovery_point_failed", "The recovery point could not be created.", null, null));
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
		boolean approved = command.payload().has("userApproved") && command.payload().get("userApproved").isJsonPrimitive()
				&& command.payload().getAsJsonPrimitive("userApproved").isBoolean()
				&& command.payload().getAsJsonPrimitive("userApproved").getAsBoolean();
		if (!approved) {
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
		RestoreResult restored;
		WorkspaceState reloaded;
		try {
			restored = history.restore(pointId);
			reloaded = reloader.reload(command.workspaceId());
		} catch (Exception exception) {
			return failed(command, currentRevision(command.workspaceId()), diagnostic("RECOVERY_POINT_RESTORE_FAILED",
					"diagnostic.recovery_point_restore_failed", "The workspace could not be restored.", null, null));
		}
		TransactionResult<RevisionedWorkspaceStore.Replacement> transaction = store.replace(command.workspaceId(),
				command.expectedRevision(), reloaded, restored.changedPaths());
		CommandOutcome conflict = checkFailure(command, transaction);
		if (conflict != null)
			return conflict;
		JsonObject payload = new JsonObject();
		payload.addProperty("recoveryPointId", pointId);
		payload.addProperty("actor", wire(context.actor()));
		JsonArray paths = new JsonArray();
		restored.changedPaths().forEach(paths::add);
		payload.add("changedPaths", paths);
		Event event = event(command, transaction.revision(), transaction.value().sequence(), "workspace_restored",
				payload);
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "committed", transaction.revision(), pointId,
				JsonNull.INSTANCE, payload.deepCopy(), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE);
		return new CommandOutcome(result, List.of(event));
	}

	private CommandOutcome executeLoaderMigration(Command command, RequestContext context) {
		if (!approved(command))
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
			return failed(command, source.revision(), diagnostic("WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.workspace_root_unavailable", exception.getMessage(), null, null));
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
			return failed(command, source.revision(), diagnostic("LOADER_MIGRATION_FAILED",
					"diagnostic.loader_migration_failed",
					"The loader migration could not create a target copy.", null, null));
		}
	}

	private CommandOutcome importUpstreamWorkspace(Command command, RequestContext context) {
		if (context.permission() != PermissionProfile.FULL_ACCESS)
			return denied(command, context.permission(), PermissionProfile.FULL_ACCESS);
		if (!approved(command))
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
			return failed(command, currentRevision(command.workspaceId()), diagnostic("WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.workspace_root_unavailable", exception.getMessage(), null, null));
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
			return failed(command, currentRevision(command.workspaceId()), diagnostic("UPSTREAM_IMPORT_FAILED",
					"diagnostic.upstream_import_failed",
					"The upstream workspace could not be copied.", null, null));
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
			return failed(command, currentRevision(command.workspaceId()), diagnostic("PUBLISH_BATCH_FAILED",
					"diagnostic.publish_batch_failed", exception.getMessage(), null, null));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), diagnostic("WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.workspace_root_unavailable", "The workspace root is not available for asset export.",
					null, null));
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
			return failed(command, currentRevision(command.workspaceId()), diagnostic("RESOURCE_PACK_CLIENT_FAILED",
					"diagnostic.resource_pack_client_failed", exception.getMessage(), null, null));
		} catch (RuntimeException exception) {
			return failed(command, currentRevision(command.workspaceId()), diagnostic("WORKSPACE_ROOT_UNAVAILABLE",
					"diagnostic.workspace_root_unavailable", "The workspace root is not available for resource packs.",
					null, null));
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
			return queryFailure(query, state.revision(), diagnostic("UPSTREAM_IMPORT_FAILED",
					"diagnostic.upstream_import_failed",
					"The upstream workspace could not be read.", "/sourceWorkspacePath", null));
		}
	}

	private QueryResult listPublishBatches(Query query, WorkspaceState state) {
		try {
			JsonObject projection = new JsonObject();
			JsonArray items = new JsonArray();
			publishBatches(state.id()).list().forEach(batch -> items.add(batch.toJson()));
			projection.add("items", items);
			return querySuccess(query, state.revision(), projection);
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), diagnostic("PUBLISH_BATCH_FAILED",
					"diagnostic.publish_batch_failed", "Publish batches could not be listed.", null, null));
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
		if (rebuild != null && "failed".equals(rebuild.status()))
			diagnostics.add(diagnostic("MIGRATION_REBUILD_FAILED", "diagnostic.migration_rebuild_failed",
					rebuild.message(), null, null));
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

	private static boolean approved(Command command) {
		return command.payload().has("userApproved") && command.payload().get("userApproved").isJsonPrimitive()
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
			JsonObject projection = new JsonObject();
			projection.addProperty("currentRevision", state.revision());
			JsonArray items = new JsonArray();
			points.forEach(point -> items.add(recoveryPoint(point)));
			projection.add("recoveryPoints", items);
			return querySuccess(query, state.revision(), projection);
		} catch (LocalHistoryException exception) {
			return queryFailure(query, state.revision(), diagnostic("HISTORY_READ_FAILED",
					"diagnostic.history_read_failed", "Local history could not be read.", null, null));
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
			return queryFailure(query, state.revision(), diagnostic("HISTORY_DIFF_FAILED",
					"diagnostic.history_diff_failed", "The two recovery points could not be compared.", null, null));
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
		int page = requiredInt(payload, "page");
		int pageSize = requiredInt(payload, "pageSize");
		String search = requiredString(payload, "search").toLowerCase(Locale.ROOT);
		Set<String> types = stringSet(payload.getAsJsonArray("types"));
		Set<String> states = stringSet(payload.getAsJsonArray("states"));
		List<Element> filtered = state.elements().stream()
				.filter(element -> search.isBlank() || element.name().contains(search)
						|| element.displayName().toLowerCase(Locale.ROOT).contains(search))
				.filter(element -> types.isEmpty() || types.contains(element.type()))
				.filter(element -> states.isEmpty() || states.contains(element.state()))
				.sorted(Comparator.comparing(Element::name)).toList();
		int from = Math.min((page - 1) * pageSize, filtered.size());
		int to = Math.min(from + pageSize, filtered.size());
		JsonObject result = new JsonObject();
		JsonArray items = new JsonArray();
		filtered.subList(from, to).forEach(element -> items.add(elementSummary(element)));
		result.add("items", items);
		result.addProperty("page", page);
		result.addProperty("pageSize", pageSize);
		result.addProperty("total", filtered.size());
		JsonArray availableTypes = new JsonArray();
		List.of("block", "item", "recipe", "procedure").forEach(availableTypes::add);
		result.add("availableTypes", availableTypes);
		return result;
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
		JsonArray fields = new JsonArray();
		flattenFields(element.values(), "", fields, readOnly);
		if (fields.isEmpty())
			fields.add(editorField("/displayName", "Display name", element.displayName(), readOnly));
		JsonObject section = new JsonObject();
		section.addProperty("id", "general");
		section.add("title", localized("editor.section.general", "General"));
		section.add("fields", fields);
		JsonArray sections = new JsonArray();
		sections.add(section);
		projection.add("sections", sections);
		projection.add("capabilities", capabilities(context));
		if (!outsideSlice)
			return querySuccess(query, state.revision(), projection);
		return new QueryResult("query_result", UiCore.SCHEMA_VERSION, query.requestId(), query.workspaceId(),
				query.operation(), "succeeded", state.revision(), projection,
				List.of(diagnostic("ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE",
						"diagnostic.element_type_outside_first_party_slice",
						"This element type is outside the first-party slice. The editor is read-only.", "/elementId",
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

	private QueryResult task(Query query, WorkspaceState state) {
		UUID taskId = UUID.fromString(requiredString(query.payload(), "taskId"));
		JsonObject task = tasks.find(state.id(), taskId).orElse(null);
		if (task == null)
			return queryFailure(query, state.revision(), diagnostic("TASK_NOT_FOUND", "diagnostic.task_not_found",
					"The requested task does not exist.", "/taskId", null));
		JsonObject projection = new JsonObject();
		projection.add("task", task);
		projection.add("logs", GSON.toJsonTree(tasks.logs(state.id(), taskId)));
		projection.add("diagnostics", GSON.toJsonTree(tasks.diagnostics(state.id(), taskId)));
		return querySuccess(query, state.revision(), projection);
	}

	private void flattenFields(JsonObject object, String base, JsonArray target, boolean readOnly) {
		for (String key : object.keySet()) {
			JsonElement value = object.get(key);
			String path = base + "/" + key.replace("~", "~0").replace("/", "~1");
			if (value.isJsonObject())
				flattenFields(value.getAsJsonObject(), path, target, readOnly);
			else if (!value.isJsonArray())
				target.add(editorField(path, displayName(key), value, readOnly));
		}
	}

	private JsonObject editorField(String path, String label, Object value, boolean readOnly) {
		JsonObject field = new JsonObject();
		field.addProperty("path", path);
		field.add("label", localized("field." + path.substring(path.lastIndexOf('/') + 1), label));
		String control = value instanceof JsonElement element && element.isJsonPrimitive()
				&& element.getAsJsonPrimitive().isBoolean() ? "toggle" : "text";
		if (value instanceof JsonElement element && element.isJsonPrimitive()
				&& element.getAsJsonPrimitive().isNumber())
			control = "number";
		field.addProperty("control", control);
		field.addProperty("required", false);
		field.addProperty("readOnly", readOnly);
		if (value instanceof JsonElement element)
			field.add("value", element.deepCopy());
		else
			field.addProperty("value", String.valueOf(value));
		field.add("options", new JsonArray());
		field.add("diagnostics", new JsonArray());
		return field;
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

	private Diagnostic validateElementValues(UUID elementId, JsonObject values) {
		if (values.has("fields") && values.get("fields").isJsonObject()) {
			JsonObject fields = values.getAsJsonObject("fields");
			if (fields.has("hardness") && fields.get("hardness").isJsonPrimitive()
					&& fields.getAsJsonPrimitive("hardness").isNumber()) {
				double hardness = fields.get("hardness").getAsDouble();
				if (hardness < 0 || hardness > 100)
					return diagnostic("FIELD_VALUE_OUT_OF_RANGE", "diagnostic.field_value_out_of_range",
							"Hardness must be between 0 and 100.", elementPath(elementId) + "/fields/hardness",
							elementId);
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
				"The command payload is invalid" + (detail == null ? "." : ": " + detail), null, null);
	}

	private Diagnostic diagnostic(String code, String key, String fallback, String path, UUID elementId) {
		return Diagnostic.error(code, key, fallback, path, elementId);
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
}
