package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.tracks.PreviewCapabilityCatalog;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/** Session-local feature choice. This object issues neither task authority nor permission. */
final class PreviewAccess {
    private record Selection(String activeGenerator, String generatorId, String destination) {}
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final VersionTrackCatalog tracks;
    private final PreviewCapabilityCatalog evidence;
    private final RevisionedWorkspaceStore store;

    PreviewAccess(RevisionedWorkspaceStore store, VersionTrackCatalog tracks, PreviewCapabilityCatalog evidence) {
        this.store = store; this.tracks = tracks; this.evidence = evidence;
    }

    String target(Command command, WorkspaceState state) {
        if (command.operation() == UiCore.Operation.CREATE_WORKSPACE) return string(command.payload(), "generatorId");
        if (command.operation() == UiCore.Operation.EXECUTE_LOADER_MIGRATION)
            return string(command.payload(), "targetGeneratorId");
        return active(state);
    }

    static boolean control(UiCore.Operation operation) {
        return Set.of(UiCore.Operation.CANCEL_TASK, UiCore.Operation.CREATE_TASK_AUTHORIZATION,
                UiCore.Operation.REVOKE_TASK_AUTHORIZATION, UiCore.Operation.CANCEL_BLOCKBENCH_TASK).contains(operation);
    }

    /** Called inside the workspace revision lock, including the actual Core operation. */
    String rejection(Command command, RequestContext context, WorkspaceState state) {
        String target = target(command, state);
        if (context.previewGeneratorId() != null && !target.equals(context.previewGeneratorId()))
            return "PREVIEW_TARGET_MISMATCH";
        if (!tracks.requiresPreview(target) && !tracks.requiresPreview(active(state))) return "";
        if (control(command.operation())) return "";
        if (context.previewGeneratorId() == null) return "PREVIEW_NEGOTIATION_REQUIRED";
        if (!tracks.requiresPreview(target)) return "PREVIEW_TARGET_MISMATCH";
        Selection selection = current(state);
        String destination = command.operation() == UiCore.Operation.CREATE_WORKSPACE
                ? path(string(command.payload(), "workspaceFolderPath")) : "";
        if (selection == null || !selection.generatorId().equals(target) || !selection.destination().equals(destination))
            return "PREVIEW_OPT_IN_REQUIRED";
        String operationReason = evidence.reason(target, command.operation());
        if (!operationReason.isEmpty()) return operationReason;
        if (command.operation() == UiCore.Operation.CREATE_MOD_ELEMENT) {
            String type = string(command.payload(), "elementType");
            if (evidence.fields(target, type).isEmpty()) return "PREVIEW_ELEMENT_UNVERIFIED";
            JsonObject values = command.payload().has("initialValues")
                    ? command.payload().getAsJsonObject("initialValues") : new JsonObject();
            var issue = PreviewElementFields.validateChange(target, type, new JsonObject(), values);
            if (issue != null) return issue.code();
        } else if (command.operation() == UiCore.Operation.UPDATE_MOD_ELEMENT) {
            var element = state.element(UUID.fromString(string(command.payload(), "elementId")));
            if (element != null) {
                Set<String> fields = evidence.fields(target, element.type());
                if (fields.isEmpty()) return "PREVIEW_ELEMENT_UNVERIFIED";
                for (var raw : command.payload().getAsJsonArray("changes")) {
                    String path = string(raw.getAsJsonObject(), "path").replaceFirst("^/fields/", "/");
                    String[] parts = path.split("/", -1);
                    if (parts.length < 2 || !fields.contains(parts[1])) return "PREVIEW_FIELD_UNVERIFIED";
                }
            }
        }
        return "";
    }

    JsonObject dispatch(UUID workspaceId, UUID requestId, JsonObject request, RequestContext context,
            BiFunction<Command, RequestContext, CommandOutcome> commands,
            BiFunction<JsonObject, RequestContext, JsonObject> acceptance) {
        JsonObject result = new JsonObject();
        result.addProperty("contract", "preview_targets"); result.addProperty("schemaVersion", "1");
        result.addProperty("requestId", requestId.toString()); result.addProperty("workspaceId", workspaceId.toString());
        result.add("diagnostics", new JsonArray());
        try {
            if (!request.has("supportedVersions") || !request.get("supportedVersions").isJsonArray()
                    || request.getAsJsonArray("supportedVersions").size() > 16
                    || request.getAsJsonArray("supportedVersions").asList().stream().anyMatch(value ->
                    !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                    || request.getAsJsonArray("supportedVersions").asList().stream().noneMatch(value -> "1".equals(value.getAsString())))
                return reject(result, "PREVIEW_VERSION_UNSUPPORTED");
            if (!Set.of("supportedVersions", "operation", "generatorId", "workspaceFolderPath", "expectedRevision", "command", "payload").containsAll(request.keySet()))
                return reject(result, "PREVIEW_REQUEST_INVALID");
            result.addProperty("selectedVersion", "1");
            WorkspaceState state = store.read(workspaceId).orElse(null);
            if (state == null) return reject(result, "WORKSPACE_NOT_FOUND");
            result.addProperty("revision", state.revision());
            String operation = string(request, "operation");
            if (Set.of("negotiate", "catalog").contains(operation)) {
                if (!Set.of("supportedVersions", "operation").containsAll(request.keySet())) return reject(result, "PREVIEW_REQUEST_INVALID");
                result.add("targets", catalog(state, context));
            } else if (Set.of("opt_in", "revoke").contains(operation)) {
                if (!Set.of("supportedVersions", "operation", "generatorId", "workspaceFolderPath", "expectedRevision").containsAll(request.keySet()))
                    return reject(result, "PREVIEW_REQUEST_INVALID");
                String target = string(request, "generatorId");
                if (!tracks.requiresPreview(target)) return reject(result, "PREVIEW_TARGET_UNKNOWN");
                if (context.permission() == UiCore.PermissionProfile.READ_ONLY && operation.equals("opt_in"))
                    return reject(result, "PERMISSION_DENIED");
                long revision = revision(request);
                String destination = request.has("workspaceFolderPath") ? path(string(request, "workspaceFolderPath")) : "";
                if (destination.isEmpty() && !target.equals(active(state))) return reject(result, "PREVIEW_TARGET_MISMATCH");
                var transaction = store.coordinate(workspaceId, revision, live -> {
                    if (operation.equals("opt_in")) selections.put(workspaceId, new Selection(active(live), target, destination));
                    else {
                        Selection selected = current(live);
                        if (selected != null && selected.generatorId().equals(target)) selections.remove(workspaceId);
                    }
                    return true;
                });
                if (transaction.status() != RevisionedWorkspaceStore.TransactionResult.Status.COORDINATED) {
                    result.addProperty("revision", transaction.revision());
                    return reject(result, "REVISION_CONFLICT");
                }
                result.add("targets", catalog(store.read(workspaceId).orElseThrow(), context));
            } else if (operation.equals("command")) {
                if (!Set.of("supportedVersions", "operation", "generatorId", "command", "expectedRevision", "payload").containsAll(request.keySet()))
                    return reject(result, "PREVIEW_REQUEST_INVALID");
                String target = string(request, "generatorId");
                if (!tracks.requiresPreview(target)) return reject(result, "PREVIEW_TARGET_UNKNOWN");
                UiCore.Operation coreOperation = UiCore.wireGson().fromJson(request.get("command"), UiCore.Operation.class);
                if (coreOperation == null) return reject(result, "PREVIEW_REQUEST_INVALID");
                JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload").deepCopy() : new JsonObject();
                payload.addProperty("clientMutationId", requestId.toString());
                var command = Command.of(requestId, workspaceId, revision(request), coreOperation, payload);
                var core = commands.apply(command, new RequestContext(context.actor(), context.permission(), false, target)).result();
                result.add("core", UiCore.wireGson().toJsonTree(core));
                result.addProperty("status", core.status()); result.addProperty("revision", core.newRevision());
                result.add("diagnostics", UiCore.wireGson().toJsonTree(core.diagnostics()));
                return result;
            } else if (operation.equals("acceptance")) {
                if (!Set.of("supportedVersions", "operation", "generatorId", "payload").containsAll(request.keySet()))
                    return reject(result, "PREVIEW_REQUEST_INVALID");
                String target = string(request, "generatorId");
                if (!tracks.requiresPreview(target)) return reject(result, "PREVIEW_TARGET_UNKNOWN");
                JsonObject core = acceptance.apply(request.getAsJsonObject("payload"),
                        new RequestContext(context.actor(), context.permission(), false, target));
                result.add("acceptance", core); result.add("status", core.get("status"));
                if (core.has("newRevision")) result.add("revision", core.get("newRevision"));
                if (core.has("diagnostics")) result.add("diagnostics", core.get("diagnostics"));
                return result;
            } else return reject(result, "PREVIEW_REQUEST_INVALID");
            result.addProperty("status", "succeeded"); return result;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | ArithmeticException error) {
            return reject(result, "PREVIEW_REQUEST_INVALID");
        }
    }

    private JsonArray catalog(WorkspaceState state, RequestContext context) {
        JsonArray targets = new JsonArray();
        Selection selected = current(state);
        for (var registration : tracks.registrations()) {
            var status = registration.status();
            JsonObject target = new JsonObject();
            target.addProperty("generatorId", status.generatorId());
            target.addProperty("minecraftVersion", status.minecraftVersion());
            target.addProperty("loader", status.loader().name().toLowerCase(java.util.Locale.ROOT));
            var track = tracks.tracks().stream().filter(item -> item.loaders().stream().anyMatch(loader -> loader.generatorId().equals(status.generatorId()))).findFirst();
            target.addProperty("trackId", track.map(item -> item.id().name().toLowerCase(java.util.Locale.ROOT)).orElse("advanced"));
            boolean preview = tracks.requiresPreview(status.generatorId());
            target.addProperty("previewRequired", preview);
            var configuration = net.mcreator.generator.Generator.GENERATOR_CACHE.get(status.generatorId());
            target.addProperty("available", !preview && configuration != null);
            target.addProperty("workspaceGeneratorName", configuration == null ? status.generatorId() : configuration.getGeneratorName());
            target.addProperty("selected", selected != null && selected.generatorId().equals(status.generatorId()));
            target.addProperty("destination", selected != null && selected.generatorId().equals(status.generatorId()) ? selected.destination() : "");
            JsonObject capabilities = preview ? evidence.projection(status.generatorId()) : new JsonObject();
            target.add("capabilities", capabilities);
            if (preview) {
                for (var raw : capabilities.getAsJsonArray("operations")) {
                    JsonObject item = raw.getAsJsonObject();
                    String reason = context.permission() == UiCore.PermissionProfile.READ_ONLY ? "PERMISSION_DENIED"
                            : !target.get("selected").getAsBoolean() ? "PREVIEW_OPT_IN_REQUIRED" : item.get("reasonCode").getAsString();
                    item.addProperty("available", reason.isEmpty());
                    item.addProperty("accessReasonCode", reason);
                }
            }
            targets.add(target);
        }
        return targets;
    }

    private Selection current(WorkspaceState state) {
        Selection selection = selections.get(state.id());
        if (selection != null && !selection.activeGenerator().equals(active(state))) {
            selections.remove(state.id(), selection); return null;
        }
        return selection;
    }

    String copyRejection(WorkspaceState state, String target, Path destination) {
        Selection choice = current(state);
        if (choice == null || !choice.generatorId().equals(target) || !choice.destination().equals(path(destination.toString())))
            return "PREVIEW_OPT_IN_REQUIRED";
        for (var operation : java.util.List.of(UiCore.Operation.CREATE_WORKSPACE, UiCore.Operation.BUILD_WORKSPACE)) {
            String reason = evidence.reason(target, operation);
            if (!reason.isEmpty()) return reason;
        }
        return "";
    }

    private static String active(WorkspaceState state) {
        return state.generator().has("id") ? state.generator().get("id").getAsString() : "";
    }
    private static String path(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute() || path.getParent() == null) throw new IllegalArgumentException("Absolute child directory required");
        return path.toAbsolutePath().normalize().toString();
    }
    private static String string(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isString()
                || value.get(key).getAsString().isBlank()) throw new IllegalArgumentException("String required: " + key);
        return value.get(key).getAsString();
    }
    private static long revision(JsonObject request) {
        if (!request.has("expectedRevision") || !request.get("expectedRevision").isJsonPrimitive()
                || !request.getAsJsonPrimitive("expectedRevision").isNumber()) throw new IllegalArgumentException("Revision required");
        long revision = request.get("expectedRevision").getAsBigDecimal().longValueExact();
        if (revision < 0) throw new IllegalArgumentException("Negative revision");
        return revision;
    }
    private static JsonObject reject(JsonObject result, String code) {
        result.addProperty("status", "rejected");
        JsonArray diagnostics = new JsonArray();
        diagnostics.add(UiCore.wireGson().toJsonTree(UiCore.Diagnostic.error(code,
                "diagnostic." + code.toLowerCase(java.util.Locale.ROOT), "Preview request rejected: " + code, null, null)));
        result.add("diagnostics", diagnostics); return result;
    }
}
