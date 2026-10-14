package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.automation.security.TaskAuthorizationStore;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.migration.VersionCopyUpgradeExecution;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/** Separate negotiated write path; no authority can be obtained from the plan itself. */
final class VersionCopyExecutionAccess {
    static JsonObject dispatch(UUID workspaceId, UUID requestId, JsonObject request, RequestContext context,
            RevisionedWorkspaceStore store, Function<UUID, Path> roots, WorkspaceMutationGateway backend,
            PreviewAccess previews, TaskAuthorizationStore authorizations, WorkspaceTaskGateway tasks) {
        JsonObject result = new JsonObject(); result.addProperty("contract", "version_copy_upgrade");
        result.addProperty("schemaVersion", "2"); result.addProperty("requestId", requestId.toString());
        result.addProperty("workspaceId", workspaceId.toString()); result.add("diagnostics", new JsonArray());
        try {
            var versions = request.getAsJsonArray("supportedVersions");
            if (versions == null || versions.isEmpty() || versions.size() > 16 || versions.asList().stream()
                    .anyMatch(v -> !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())
                    || versions.asList().stream().noneMatch(v -> "2".equals(v.getAsString())))
                return reject(result, "UPGRADE_VERSION_UNSUPPORTED", "Negotiate version_copy_upgrade version 2.");
            result.addProperty("selectedVersion", "2");
            String operation = string(request, "operation");
            Set<String> keys = switch (operation) {
                case "negotiate" -> Set.of("operation", "supportedVersions");
                case "preview" -> Set.of("operation", "supportedVersions", "targetGeneratorId", "outputName");
                case "execute" -> Set.of("operation", "supportedVersions", "targetGeneratorId", "outputName", "planId", "expectedRevision", "userApproved", "taskAuthorizationId");
                default -> throw new IllegalArgumentException("Unknown version-copy operation.");
            };
            if (!keys.containsAll(request.keySet())) return reject(result, "UPGRADE_REQUEST_INVALID", "Unexpected version-copy parameter.");
            if (operation.equals("negotiate")) {
                result.addProperty("status", "succeeded"); result.addProperty("executionAvailable", true);
                result.addProperty("ruleVersion", VersionCopyUpgradeExecution.RULE_VERSION); return result;
            }
            var state = store.read(workspaceId).orElse(null);
            if (state == null) return reject(result, "WORKSPACE_NOT_FOUND", "Reopen the source workspace.");
            result.addProperty("revision", state.revision());
            String target = string(request, "targetGeneratorId"), name = string(request, "outputName");
            boolean execute = operation.equals("execute");
            long expected = state.revision();
            if (execute) {
                if (context.permission() == PermissionProfile.READ_ONLY) return reject(result, "PERMISSION_DENIED", "Execution requires workspace write permission.");
                if (!request.has("expectedRevision") || !request.get("expectedRevision").isJsonPrimitive() || !request.getAsJsonPrimitive("expectedRevision").isNumber())
                    return reject(result, "UPGRADE_REQUEST_INVALID", "An integer expectedRevision is required.");
                expected = request.get("expectedRevision").getAsBigDecimal().longValueExact();
                if (expected < 0 || !string(request, "planId").matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid revision or plan ID.");
            }
            var transaction = store.coordinate(workspaceId, expected, live -> {
                try {
                    if (!tasks.active(workspaceId).isEmpty()) return reject(result, "UPGRADE_SOURCE_BUSY", "Wait for active source tasks to finish before planning or copying.");
                    Path root = roots == null ? null : roots.apply(workspaceId);
                    if (root == null) throw new java.io.IOException("The saved source directory is unavailable.");
                    Path destination = VersionCopyUpgradeExecution.destination(root, name);
                    if (execute) {
                        String choice = previews.copyRejection(live, target, destination);
                        if (!choice.isEmpty()) return reject(result, choice, "Select this exact 26.3 preview and destination before executing.");
                        boolean user = context.actor() == Actor.UI || context.actor() == Actor.LEGACY_UI;
                        if (user) {
                            if (!request.has("userApproved") || !request.get("userApproved").isJsonPrimitive()
                                    || !request.getAsJsonPrimitive("userApproved").isBoolean() || !request.get("userApproved").getAsBoolean())
                                return reject(result, "USER_APPROVAL_REQUIRED", "Confirm the reviewed copy plan.");
                        } else {
                            String id = request.has("taskAuthorizationId") ? string(request, "taskAuthorizationId") : "";
                            for (String capability : List.of("create", "build")) {
                                var authority = authorizations.authorize(id, destination, capability);
                                if (!authority.allowed()) return reject(result, authority.code(), "The target requires task authority for create and build.");
                            }
                        }
                    }
                    var service = new VersionCopyUpgradeExecution(backend);
                    var inspection = service.inspect(live, root, target, name);
                    if (!execute) { result.addProperty("status", "succeeded"); result.add("plan", inspection.plan()); return result; }
                    if (!request.get("planId").equals(inspection.plan().get("planId")))
                        return reject(result, "UPGRADE_PLAN_STALE", "The source, target or rule inputs changed. Request a new plan.");
                    if (!inspection.plan().get("executionAvailable").getAsBoolean())
                        return reject(result, "UPGRADE_MANUAL_TASKS_REQUIRED", "Resolve every manual task before execution.");
                    // Rendering may be lengthy. Expiry or revocation during planning must still prevent the first write.
                    if (context.actor() != Actor.UI && context.actor() != Actor.LEGACY_UI) {
                        for (String capability : List.of("create", "build")) {
                            var authority = authorizations.authorize(string(request, "taskAuthorizationId"), destination, capability);
                            if (!authority.allowed()) return reject(result, authority.code(), "Target task authority changed while checking the plan.");
                        }
                    }
                    var receipt = service.execute(root, inspection);
                    result.add("execution", receipt); result.addProperty("status", receipt.get("conversionComplete").getAsBoolean() ? "succeeded" : "failed");
                    if (!receipt.get("conversionComplete").getAsBoolean()) diagnostic(result, "UPGRADE_TARGET_FAILED", "The target is incomplete. Continue using the source; inspect the target receipt before starting a new copy.");
                    return result;
                } catch (VersionCopyUpgradeExecution.Rejection rejection) { return reject(result, rejection.code, rejection.getMessage()); }
                catch (Exception failure) { return reject(result, "UPGRADE_SOURCE_UNAVAILABLE", String.valueOf(failure.getMessage())); }
            });
            if (transaction.status() != RevisionedWorkspaceStore.TransactionResult.Status.COORDINATED)
                return reject(result, "WORKSPACE_REVISION_CONFLICT", "The source revision changed. Request a new plan.");
            return transaction.value();
        } catch (RuntimeException invalid) { return reject(result, "UPGRADE_REQUEST_INVALID", "Invalid version-copy request."); }
    }
    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString() || object.get(key).getAsString().isBlank())
            throw new IllegalArgumentException("String required: " + key);
        return object.get(key).getAsString();
    }
    private static void diagnostic(JsonObject result, String code, String message) {
        var item = new JsonObject(); item.addProperty("code", code); item.addProperty("message", message); result.getAsJsonArray("diagnostics").add(item);
    }
    private static JsonObject reject(JsonObject result, String code, String message) { result.addProperty("status", "rejected"); diagnostic(result, code, message); return result; }
}
