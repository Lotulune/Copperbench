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
import dev.copperbench.core.contract.UiCore.LocalizedText;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore.Decision;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore.TransactionResult;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.history.LocalHistoryException;
import dev.copperbench.history.LocalHistoryService;
import dev.copperbench.history.RecoveryPoint;
import dev.copperbench.history.RecoveryPointRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Stateless plan/preview/apply engine for FR-AI-03. */
final class WorkspacePlanEngine {

	private static final Gson GSON = UiCore.wireGson();
	private static final int MAX_OPERATIONS = 100;
	private static final Set<Operation> SUPPORTED = Set.of(
			Operation.CREATE_MOD_ELEMENT,
			Operation.UPDATE_MOD_ELEMENT,
			Operation.DELETE_MOD_ELEMENT,
			Operation.UPDATE_PROCEDURE,
			Operation.CREATE_REGISTRY_ENTRY,
			Operation.UPDATE_REGISTRY_ENTRY,
			Operation.DELETE_REGISTRY_ENTRY,
			Operation.RENAME_REGISTRY_ENTRY);

	private final RevisionedWorkspaceStore store;
	private final WorkspaceTaskGateway tasks;
	private final WorkspaceMutationGateway mutations;
	private final LocalHistoryService history;
	private final Clock clock;
	private final Supplier<UUID> ids;
	private final byte[] planSecret = new byte[32];

	WorkspacePlanEngine(RevisionedWorkspaceStore store, WorkspaceTaskGateway tasks,
			WorkspaceMutationGateway mutations, LocalHistoryService history, Clock clock, Supplier<UUID> ids) {
		this.store = store;
		this.tasks = tasks;
		this.mutations = mutations;
		this.history = history;
		this.clock = clock;
		this.ids = ids;
		new SecureRandom().nextBytes(planSecret);
	}

	private String planToken(String planId) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(planSecret, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(planId.getBytes(StandardCharsets.US_ASCII)));
		} catch (Exception exception) {
			throw new IllegalStateException("HmacSHA256 is unavailable", exception);
		}
	}

	QueryResult plan(Query query, RequestContext context) {
		WorkspaceState state = store.read(query.workspaceId()).orElse(null);
		if (state == null) return queryFailure(query, 0, diagnostic("WORKSPACE_NOT_FOUND",
				"diagnostic.workspace_not_found", "The workspace does not exist."));
		try {
			long expectedRevision = requiredLong(query.payload(), "expectedRevision");
			if (expectedRevision != state.revision())
				return queryFailure(query, state.revision(), stalePlan(expectedRevision, state.revision()));
			String idempotencyKey = requiredString(query.payload(), "idempotencyKey");
			if (idempotencyKey.length() > 128)
				throw new IllegalArgumentException("idempotencyKey must be at most 128 characters");
			JsonArray operations = normalizedOperations(query.payload(), true);
			Simulation simulation = simulate(state, operations);
			if (!simulation.succeeded())
				return queryFailure(query, state.revision(), simulation.diagnostic());
			JsonObject plan = buildPlan(query.workspaceId(), state, idempotencyKey, operations,
					simulation.state(), context.permission());
			return querySuccess(query, state.revision(), plan);
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), diagnostic("WORKSPACE_PLAN_INVALID",
					"diagnostic.workspace_plan_invalid", exception.getMessage()));
		}
	}

	QueryResult preview(Query query, RequestContext context) {
		WorkspaceState state = store.read(query.workspaceId()).orElse(null);
		if (state == null) return queryFailure(query, 0, diagnostic("WORKSPACE_NOT_FOUND",
				"diagnostic.workspace_not_found", "The workspace does not exist."));
		try {
			JsonObject plan = requiredPlan(query.payload());
			ValidatedPlan validated = validatePlan(query.workspaceId(), state, plan);
			JsonObject projection = validated.plan().deepCopy();
			projection.addProperty("currentRevision", state.revision());
			projection.addProperty("alreadyApplied", validated.alreadyApplied());
			projection.addProperty("wouldApply", !validated.alreadyApplied()
					&& context.permission() != PermissionProfile.READ_ONLY);
			projection.add("permission", permission(context.permission()));
			return querySuccess(query, state.revision(), projection);
		} catch (PlanException exception) {
			return queryFailure(query, state.revision(), exception.diagnostic());
		} catch (RuntimeException exception) {
			return queryFailure(query, state.revision(), diagnostic("WORKSPACE_PLAN_INVALID",
					"diagnostic.workspace_plan_invalid", exception.getMessage()));
		}
	}

	CommandOutcome apply(Command command, RequestContext context) {
		if (context.permission() == PermissionProfile.READ_ONLY)
			return denied(command, context.permission());
		WorkspaceState current = store.read(command.workspaceId()).orElse(null);
		if (current == null)
			return rejected(command, 0, diagnostic("WORKSPACE_NOT_FOUND", "diagnostic.workspace_not_found",
					"The workspace does not exist."));
		ValidatedPlan validated;
		try {
			validated = validatePlan(command.workspaceId(), current, requiredPlan(command.payload()));
		} catch (PlanException exception) {
			return rejected(command, current.revision(), exception.diagnostic());
		} catch (RuntimeException exception) {
			return rejected(command, current.revision(), diagnostic("WORKSPACE_PLAN_INVALID",
					"diagnostic.workspace_plan_invalid", exception.getMessage()));
		}

		JsonObject plan = validated.plan();
		long baseRevision = plan.get("baseRevision").getAsLong();
		if (command.expectedRevision() != baseRevision)
			return revisionConflict(command, current.revision(), List.of());
		if (validated.alreadyApplied())
			return idempotentReplay(command, current.revision(), plan);

		JsonArray operations = plan.getAsJsonArray("operations");
		Simulation simulation = validated.simulation();
		TransactionResult<PlanMutation> transaction = store.transact(command.workspaceId(), command.expectedRevision(), state -> {
			WorkspaceState before = state.copy();
			state.replaceContentFrom(simulation.state());
			RecoveryPoint recoveryPoint = null;
			if (history != null) {
				try {
					recoveryPoint = history.createRecoveryPoint(new RecoveryPointRequest(
							"Before workspace plan " + shortId(plan.get("planId").getAsString()), context.actor(),
							plan.get("idempotencyKey").getAsString()));
				} catch (LocalHistoryException exception) {
					return Decision.abort(PlanMutation.rejected(diagnostic("RECOVERY_POINT_FAILED",
							"diagnostic.recovery_point_failed",
							"The required recovery point could not be created; the workspace was not changed.")));
				}
			}
			try {
				mutations.persistWorkspacePlan(before, state, operationKinds(operations));
			} catch (Exception exception) {
				return Decision.abort(PlanMutation.rejected(diagnostic("WORKSPACE_PLAN_PERSISTENCE_FAILED",
						"diagnostic.workspace_plan_persistence_failed",
						"The workspace plan could not be stored and was rolled back.")));
			}
			long sequence = state.nextEventSequence();
			return Decision.commit(PlanMutation.committed(recoveryPoint, sequence), changedPaths(plan));
		});

		if (transaction.status() == TransactionResult.Status.NOT_FOUND)
			return rejected(command, 0, diagnostic("WORKSPACE_NOT_FOUND", "diagnostic.workspace_not_found",
					"The workspace does not exist."));
		if (transaction.status() == TransactionResult.Status.CONFLICT)
			return revisionConflict(command, transaction.revision(), transaction.changedPaths());
		if (transaction.status() == TransactionResult.Status.ABORTED)
			return rejected(command, transaction.revision(), transaction.value().diagnostic());

		PlanMutation mutation = transaction.value();
		JsonObject data = applyData(plan, false);
		String recoveryPointId = mutation.recoveryPoint() == null ? null : mutation.recoveryPoint().id();
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "committed", transaction.revision(), recoveryPointId,
				JsonNull.INSTANCE, data, List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE);
		JsonObject eventPayload = data.deepCopy();
		Event event = new Event("event", UiCore.SCHEMA_VERSION, UUID.randomUUID(), command.workspaceId(),
				transaction.revision(), mutation.sequence(), clock.instant().toString(), "workspace_plan_applied",
				command.requestId(), eventPayload);
		return new CommandOutcome(result, List.of(event));
	}

	private JsonArray normalizedOperations(JsonObject payload, boolean allocateIds) {
		if (!payload.has("operations") || !payload.get("operations").isJsonArray())
			throw new IllegalArgumentException("operations is required");
		JsonArray source = payload.getAsJsonArray("operations");
		if (source.isEmpty() || source.size() > MAX_OPERATIONS)
			throw new IllegalArgumentException("operations must contain between 1 and " + MAX_OPERATIONS + " items");
		JsonArray normalized = new JsonArray();
		for (JsonElement raw : source) {
			if (!raw.isJsonObject()) throw new IllegalArgumentException("Each plan operation must be an object");
			JsonObject item = raw.getAsJsonObject();
			Operation operation = parseOperation(requiredString(item, "operation"));
			if (!SUPPORTED.contains(operation))
				throw new IllegalArgumentException("Unsupported workspace plan operation: " + wire(operation));
			if (!item.has("payload") || !item.get("payload").isJsonObject())
				throw new IllegalArgumentException("Each plan operation requires an object payload");
			JsonObject operationPayload = item.getAsJsonObject("payload").deepCopy();
			if (operationPayload.has("expectedRevision"))
				throw new IllegalArgumentException("Plan operations use the plan baseRevision, not nested expectedRevision");
			JsonObject next = new JsonObject();
			next.addProperty("operation", wire(operation));
			next.add("payload", operationPayload);
			boolean createsIdentity = operation == Operation.CREATE_MOD_ELEMENT
					|| operation == Operation.CREATE_REGISTRY_ENTRY;
			if (createsIdentity) {
				if (allocateIds) next.addProperty("plannedId", ids.get().toString());
				else next.addProperty("plannedId", UUID.fromString(requiredString(item, "plannedId")).toString());
			} else if (item.has("plannedId")) {
				throw new IllegalArgumentException("plannedId is only valid for create operations");
			}
			normalized.add(next);
		}
		return normalized;
	}

	private Simulation simulate(WorkspaceState base, JsonArray operations) {
		RevisionedWorkspaceStore shadowStore = new RevisionedWorkspaceStore();
		shadowStore.register(base.copy());
		Queue<UUID> simulationIds = new ArrayDeque<>();
		int operationIndex = 0;
		for (JsonElement raw : operations) {
			JsonObject item = raw.getAsJsonObject();
			if (item.has("plannedId")) simulationIds.add(UUID.fromString(item.get("plannedId").getAsString()));
			simulationIds.add(UUID.nameUUIDFromBytes(("workspace-plan-event-" + operationIndex++)
					.getBytes(StandardCharsets.UTF_8)));
		}
		Supplier<UUID> plannedIdSupplier = () -> {
			UUID next = simulationIds.poll();
			if (next == null) throw new IllegalStateException("The workspace plan simulation exhausted its deterministic IDs");
			return next;
		};
		WorkspaceApplicationService shadow = new WorkspaceApplicationService(shadowStore, tasks,
				WorkspaceMutationGateway.noOp(), null, null, null, clock, plannedIdSupplier, false);
		RequestContext simulationContext = new RequestContext(UiCore.Actor.HEADLESS, PermissionProfile.WORKSPACE);
		for (JsonElement raw : operations) {
			JsonObject item = raw.getAsJsonObject();
			Operation operation = parseOperation(item.get("operation").getAsString());
			long revision = shadowStore.read(base.id()).orElseThrow().revision();
			CommandOutcome outcome = shadow.execute(Command.of(UUID.randomUUID(), base.id(), revision, operation,
					item.getAsJsonObject("payload")), simulationContext);
			if (!"committed".equals(outcome.result().status())) {
				Diagnostic diagnostic = outcome.result().diagnostics().isEmpty()
						? diagnostic("WORKSPACE_PLAN_STEP_REJECTED", "diagnostic.workspace_plan_step_rejected",
								"A workspace plan step was rejected.") : outcome.result().diagnostics().getFirst();
				return Simulation.failed(diagnostic);
			}
		}
		return Simulation.succeeded(shadowStore.read(base.id()).orElseThrow());
	}

	private JsonObject buildPlan(UUID workspaceId, WorkspaceState before, String idempotencyKey,
			JsonArray operations, WorkspaceState after, PermissionProfile permissionProfile) {
		JsonObject plan = new JsonObject();
		plan.addProperty("schemaVersion", UiCore.SCHEMA_VERSION);
		plan.addProperty("workspaceId", workspaceId.toString());
		plan.addProperty("baseRevision", before.revision());
		plan.addProperty("idempotencyKey", idempotencyKey);
		plan.add("operations", operations.deepCopy());
		plan.addProperty("operationCount", operations.size());
		plan.addProperty("targetDigest", workspaceDigest(after));
		plan.add("semanticDiff", semanticDiff(before, after));
		JsonArray paths = new JsonArray();
		changedPaths(before, after).forEach(paths::add);
		plan.add("changedPaths", paths);
		plan.add("permission", permission(permissionProfile));
		plan.addProperty("planId", planId(plan));
		plan.addProperty("planToken", planToken(plan.get("planId").getAsString()));
		return plan;
	}

	private ValidatedPlan validatePlan(UUID workspaceId, WorkspaceState current, JsonObject rawPlan) {
		JsonObject plan = rawPlan.deepCopy();
		if (!workspaceId.toString().equals(requiredString(plan, "workspaceId")))
			throw new PlanException(diagnostic("WORKSPACE_PLAN_WRONG_WORKSPACE",
					"diagnostic.workspace_plan_wrong_workspace", "The workspace plan belongs to another workspace."));
		long baseRevision = requiredLong(plan, "baseRevision");
		requiredString(plan, "idempotencyKey");
		JsonArray normalized = normalizedOperations(plan, false);
		plan.add("operations", normalized);
		int operationCount = requiredInt(plan, "operationCount");
		if (operationCount != normalized.size())
			throw new PlanException(diagnostic("WORKSPACE_PLAN_INTEGRITY_FAILED",
					"diagnostic.workspace_plan_integrity_failed",
					"The workspace plan operationCount does not match its ordered operations."));
		JsonArray suppliedSemanticDiff = requiredArray(plan, "semanticDiff");
		JsonArray suppliedChangedPaths = requiredArray(plan, "changedPaths");
		String suppliedPlanId = requiredString(plan, "planId");
		String suppliedPlanToken = requiredString(plan, "planToken");
		String targetDigest = requiredString(plan, "targetDigest");
		if (!suppliedPlanId.equals(planId(plan)))
			throw new PlanException(diagnostic("WORKSPACE_PLAN_INTEGRITY_FAILED",
					"diagnostic.workspace_plan_integrity_failed", "The workspace plan content does not match its planId."));
		if (!MessageDigest.isEqual(suppliedPlanToken.getBytes(StandardCharsets.US_ASCII),
				planToken(suppliedPlanId).getBytes(StandardCharsets.US_ASCII)))
			throw new PlanException(diagnostic("WORKSPACE_PLAN_INTEGRITY_FAILED",
					"diagnostic.workspace_plan_integrity_failed",
					"The workspace plan was not issued by the current Copperbench session."));
		if (current.revision() == baseRevision + 1 && workspaceDigest(current).equals(targetDigest))
			return new ValidatedPlan(true, null, plan);
		if (current.revision() != baseRevision)
			throw new PlanException(stalePlan(baseRevision, current.revision()));
		Simulation simulation = simulate(current, normalized);
		if (!simulation.succeeded()) throw new PlanException(simulation.diagnostic());
		if (!workspaceDigest(simulation.state()).equals(targetDigest))
			throw new PlanException(diagnostic("WORKSPACE_PLAN_TARGET_MISMATCH",
					"diagnostic.workspace_plan_target_mismatch",
					"The workspace plan no longer produces its recorded target state."));
		JsonArray canonicalSemanticDiff = semanticDiff(current, simulation.state());
		JsonArray canonicalChangedPaths = new JsonArray();
		changedPaths(current, simulation.state()).forEach(canonicalChangedPaths::add);
		if (!canonicalSemanticDiff.equals(suppliedSemanticDiff) || !canonicalChangedPaths.equals(suppliedChangedPaths))
			throw new PlanException(diagnostic("WORKSPACE_PLAN_INTEGRITY_FAILED",
					"diagnostic.workspace_plan_integrity_failed",
					"The workspace plan derived diff does not match the validated target state."));
		plan.add("semanticDiff", canonicalSemanticDiff);
		plan.add("changedPaths", canonicalChangedPaths);
		plan.addProperty("operationCount", normalized.size());
		return new ValidatedPlan(false, simulation, plan);
	}

	private static JsonObject permission(PermissionProfile profile) {
		JsonObject result = new JsonObject();
		result.addProperty("currentProfile", wire(profile));
		result.addProperty("requiredProfile", "workspace");
		result.addProperty("allowed", profile != PermissionProfile.READ_ONLY);
		return result;
	}

	private static JsonArray semanticDiff(WorkspaceState before, WorkspaceState after) {
		Map<UUID, Element> oldElements = byId(before.elements());
		Map<UUID, Element> newElements = byId(after.elements());
		Set<UUID> ids = new LinkedHashSet<>();
		ids.addAll(oldElements.keySet());
		ids.addAll(newElements.keySet());
		List<UUID> ordered = ids.stream().sorted(Comparator.comparing(UUID::toString)).toList();
		JsonArray diff = new JsonArray();
		for (UUID id : ordered) {
			Element oldValue = oldElements.get(id);
			Element newValue = newElements.get(id);
			if (oldValue == null) diff.add(elementDiff("element_created", newValue));
			else if (newValue == null) diff.add(elementDiff("element_deleted", oldValue));
			else if (!sameElementContent(oldValue, newValue)) diff.add(elementDiff("element_updated", newValue));
		}
		JsonObject oldRegistries = before.registries();
		JsonObject newRegistries = after.registries();
		for (String registry : List.of("variables", "tags", "languageKeys")) {
			if (oldRegistries.getAsJsonArray(registry).equals(newRegistries.getAsJsonArray(registry))) continue;
			JsonObject item = new JsonObject();
			item.addProperty("kind", "registry_updated");
			item.addProperty("registry", registry);
			item.addProperty("beforeCount", oldRegistries.getAsJsonArray(registry).size());
			item.addProperty("afterCount", newRegistries.getAsJsonArray(registry).size());
			diff.add(item);
		}
		return diff;
	}

	private static List<String> changedPaths(WorkspaceState before, WorkspaceState after) {
		LinkedHashSet<String> paths = new LinkedHashSet<>();
		Map<UUID, Element> oldElements = byId(before.elements());
		Map<UUID, Element> newElements = byId(after.elements());
		Set<UUID> ids = new HashSet<>();
		ids.addAll(oldElements.keySet());
		ids.addAll(newElements.keySet());
		ids.stream().sorted(Comparator.comparing(UUID::toString)).forEach(id -> {
			Element oldValue = oldElements.get(id);
			Element newValue = newElements.get(id);
			if (oldValue == null || newValue == null || !sameElementContent(oldValue, newValue))
				paths.add("/elements/" + id);
		});
		JsonObject oldRegistries = before.registries();
		JsonObject newRegistries = after.registries();
		for (String registry : List.of("variables", "tags", "languageKeys"))
			if (!oldRegistries.getAsJsonArray(registry).equals(newRegistries.getAsJsonArray(registry)))
				paths.add("/registries/" + registry);
		return List.copyOf(paths);
	}

	private static String workspaceDigest(WorkspaceState state) {
		JsonObject content = new JsonObject();
		content.add("registries", state.registries());
		JsonArray elements = new JsonArray();
		state.elements().stream().sorted(Comparator.comparing(item -> item.id().toString())).forEach(element -> {
			JsonObject item = new JsonObject();
			item.addProperty("id", element.id().toString());
			item.addProperty("type", element.type());
			item.addProperty("name", element.name());
			item.addProperty("displayName", element.displayName());
			item.addProperty("state", element.state());
			item.addProperty("ownership", element.ownership());
			item.add("values", element.values());
			elements.add(item);
		});
		content.add("elements", elements);
		return sha256(GSON.toJson(content));
	}

	private static String planId(JsonObject plan) {
		JsonObject core = new JsonObject();
		core.addProperty("workspaceId", requiredString(plan, "workspaceId"));
		core.addProperty("baseRevision", requiredLong(plan, "baseRevision"));
		core.addProperty("idempotencyKey", requiredString(plan, "idempotencyKey"));
		core.add("operations", plan.getAsJsonArray("operations").deepCopy());
		core.addProperty("operationCount", requiredInt(plan, "operationCount"));
		core.addProperty("targetDigest", requiredString(plan, "targetDigest"));
		core.add("semanticDiff", requiredArray(plan, "semanticDiff").deepCopy());
		core.add("changedPaths", requiredArray(plan, "changedPaths").deepCopy());
		return sha256(GSON.toJson(core));
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static Map<UUID, Element> byId(List<Element> elements) {
		Map<UUID, Element> result = new HashMap<>();
		elements.forEach(element -> result.put(element.id(), element));
		return result;
	}

	private static boolean sameElementContent(Element left, Element right) {
		return left.type().equals(right.type()) && left.name().equals(right.name())
				&& left.displayName().equals(right.displayName()) && left.state().equals(right.state())
				&& left.ownership().equals(right.ownership()) && left.values().equals(right.values());
	}

	private static JsonObject elementDiff(String kind, Element element) {
		JsonObject item = new JsonObject();
		item.addProperty("kind", kind);
		item.addProperty("elementId", element.id().toString());
		item.addProperty("type", element.type());
		item.addProperty("name", element.name());
		return item;
	}

	private static List<Operation> operationKinds(JsonArray operations) {
		List<Operation> result = new ArrayList<>();
		operations.forEach(raw -> result.add(parseOperation(raw.getAsJsonObject().get("operation").getAsString())));
		return List.copyOf(result);
	}

	private static List<String> changedPaths(JsonObject plan) {
		List<String> result = new ArrayList<>();
		plan.getAsJsonArray("changedPaths").forEach(raw -> result.add(raw.getAsString()));
		return List.copyOf(result);
	}

	private static JsonObject applyData(JsonObject plan, boolean replay) {
		JsonObject data = new JsonObject();
		data.addProperty("planId", plan.get("planId").getAsString());
		data.addProperty("idempotencyKey", plan.get("idempotencyKey").getAsString());
		data.addProperty("operationCount", plan.get("operationCount").getAsInt());
		data.addProperty("targetDigest", plan.get("targetDigest").getAsString());
		data.addProperty("idempotentReplay", replay);
		data.add("semanticDiff", plan.getAsJsonArray("semanticDiff").deepCopy());
		data.add("changedPaths", plan.getAsJsonArray("changedPaths").deepCopy());
		return data;
	}

	private static JsonObject requiredPlan(JsonObject payload) {
		if (!payload.has("plan") || !payload.get("plan").isJsonObject())
			throw new IllegalArgumentException("plan is required");
		return payload.getAsJsonObject("plan").deepCopy();
	}

	private static Operation parseOperation(String value) {
		Operation operation = GSON.fromJson('"' + value + '"', Operation.class);
		if (operation == null) throw new IllegalArgumentException("Unknown operation: " + value);
		return operation;
	}

	private static String wire(Operation operation) {
		return GSON.toJson(operation).replace("\"", "");
	}

	private static String wire(PermissionProfile profile) {
		return GSON.toJson(profile).replace("\"", "");
	}

	private static String requiredString(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive() || object.get(key).getAsString().isBlank())
			throw new IllegalArgumentException(key + " is required");
		return object.get(key).getAsString();
	}

	private static long requiredLong(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive())
			throw new IllegalArgumentException(key + " is required");
		long value = object.get(key).getAsLong();
		if (value < 0) throw new IllegalArgumentException(key + " must be non-negative");
		return value;
	}

	private static int requiredInt(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive())
			throw new IllegalArgumentException(key + " is required");
		int value = object.get(key).getAsInt();
		if (value < 0) throw new IllegalArgumentException(key + " must be non-negative");
		return value;
	}

	private static JsonArray requiredArray(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonArray())
			throw new IllegalArgumentException(key + " is required");
		return object.getAsJsonArray(key);
	}

	private static String shortId(String value) {
		return value.length() <= 12 ? value : value.substring(0, 12);
	}

	private static Diagnostic stalePlan(long expectedRevision, long actualRevision) {
		JsonObject args = new JsonObject();
		args.addProperty("expectedRevision", expectedRevision);
		args.addProperty("actualRevision", actualRevision);
		return new Diagnostic("WORKSPACE_PLAN_STALE", UiCore.Severity.ERROR,
				LocalizedText.of("diagnostic.workspace_plan_stale",
						"The workspace changed after this plan was created.", args), null, null, true, List.of());
	}

	private static Diagnostic diagnostic(String code, String key, String fallback) {
		return Diagnostic.error(code, key, fallback, null, null);
	}

	private static QueryResult querySuccess(Query query, long revision, JsonElement data) {
		return new QueryResult("query_result", UiCore.SCHEMA_VERSION, query.requestId(), query.workspaceId(),
				query.operation(), "succeeded", revision, data, List.of());
	}

	private static QueryResult queryFailure(Query query, long revision, Diagnostic diagnostic) {
		return new QueryResult("query_result", UiCore.SCHEMA_VERSION, query.requestId(), query.workspaceId(),
				query.operation(), "failed", revision, JsonNull.INSTANCE, List.of(diagnostic));
	}

	private static CommandOutcome rejected(Command command, long revision, Diagnostic diagnostic) {
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "rejected", revision, null, JsonNull.INSTANCE,
				JsonNull.INSTANCE, List.of(diagnostic), JsonNull.INSTANCE, JsonNull.INSTANCE);
		return new CommandOutcome(result, List.of());
	}

	private static CommandOutcome denied(Command command, PermissionProfile profile) {
		JsonObject denial = new JsonObject();
		denial.addProperty("currentProfile", wire(profile));
		denial.addProperty("requiredProfile", "workspace");
		Diagnostic diagnostic = diagnostic("PERMISSION_DENIED", "diagnostic.permission_denied",
				"This operation requires workspace write permission.");
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "rejected", 0, null, JsonNull.INSTANCE,
				JsonNull.INSTANCE, List.of(diagnostic), JsonNull.INSTANCE, denial);
		return new CommandOutcome(result, List.of());
	}

	private static CommandOutcome revisionConflict(Command command, long actualRevision, List<String> changedPaths) {
		JsonObject conflict = new JsonObject();
		conflict.addProperty("expectedRevision", command.expectedRevision());
		conflict.addProperty("actualRevision", actualRevision);
		JsonArray paths = new JsonArray();
		changedPaths.forEach(paths::add);
		conflict.add("changedPaths", paths);
		Diagnostic diagnostic = diagnostic("WORKSPACE_REVISION_CONFLICT", "diagnostic.workspace_revision_conflict",
				"The workspace changed after this request was created.");
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "rejected", actualRevision, null, JsonNull.INSTANCE,
				JsonNull.INSTANCE, List.of(diagnostic), conflict, JsonNull.INSTANCE);
		return new CommandOutcome(result, List.of());
	}

	private static CommandOutcome idempotentReplay(Command command, long revision, JsonObject plan) {
		CommandResult result = new CommandResult("command_result", UiCore.SCHEMA_VERSION, command.requestId(),
				command.workspaceId(), command.operation(), "committed", revision, null, JsonNull.INSTANCE,
				applyData(plan, true), List.of(), JsonNull.INSTANCE, JsonNull.INSTANCE);
		return new CommandOutcome(result, List.of());
	}

	private record Simulation(WorkspaceState state, Diagnostic diagnostic) {
		static Simulation succeeded(WorkspaceState state) { return new Simulation(state.copy(), null); }
		static Simulation failed(Diagnostic diagnostic) { return new Simulation(null, diagnostic); }
		boolean succeeded() { return state != null; }
	}

	private record ValidatedPlan(boolean alreadyApplied, Simulation simulation, JsonObject plan) {
	}

	private record PlanMutation(RecoveryPoint recoveryPoint, long sequence, Diagnostic diagnostic) {
		static PlanMutation committed(RecoveryPoint recoveryPoint, long sequence) {
			return new PlanMutation(recoveryPoint, sequence, null);
		}
		static PlanMutation rejected(Diagnostic diagnostic) { return new PlanMutation(null, 0, diagnostic); }
	}

	private static final class PlanException extends RuntimeException {
		private final Diagnostic diagnostic;
		PlanException(Diagnostic diagnostic) {
			super(diagnostic.code());
			this.diagnostic = diagnostic;
		}
		Diagnostic diagnostic() { return diagnostic; }
	}
}
