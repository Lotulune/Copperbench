package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.migration.VersionCopyUpgradeService;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/** Versioned extension; read-only planning grants no preview selection or write authority. */
final class VersionCopyUpgradeAccess {
    static JsonObject dispatch(UUID workspaceId, UUID requestId, JsonObject request, RevisionedWorkspaceStore store,
            Function<UUID, Path> roots, Function<WorkspaceState.Element, JsonObject> identities) {
        JsonObject result = new JsonObject(); result.addProperty("contract", "version_copy_upgrade");
        result.addProperty("schemaVersion", "1"); result.addProperty("requestId", requestId.toString());
        result.addProperty("workspaceId", workspaceId.toString()); result.add("diagnostics", new JsonArray());
        try {
            if (!request.has("supportedVersions") || !request.get("supportedVersions").isJsonArray()
                    || request.getAsJsonArray("supportedVersions").size() > 16
                    || request.getAsJsonArray("supportedVersions").asList().stream().anyMatch(v -> !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())
                    || request.getAsJsonArray("supportedVersions").asList().stream().noneMatch(v -> "1".equals(v.getAsString())))
                return reject(result, "UPGRADE_VERSION_UNSUPPORTED", "Negotiate version_copy_upgrade version 1.");
            result.addProperty("selectedVersion", "1");
            if (!Set.of("supportedVersions", "operation", "targetGeneratorId").containsAll(request.keySet()))
                return reject(result, "UPGRADE_REQUEST_INVALID", "Only read-only preview parameters are accepted.");
            String operation = request.get("operation").getAsString();
            if ("negotiate".equals(operation)) {
                if (request.has("targetGeneratorId")) return reject(result, "UPGRADE_REQUEST_INVALID", "Negotiation does not select a target.");
                result.addProperty("status", "succeeded"); result.addProperty("executionAvailable", false); return result;
            }
            if (!"preview".equals(operation)) return reject(result, "UPGRADE_OPERATION_UNSUPPORTED", "Only read-only preview is available.");
            if (!request.has("targetGeneratorId") || !request.get("targetGeneratorId").isJsonPrimitive() || !request.getAsJsonPrimitive("targetGeneratorId").isString())
                return reject(result, "UPGRADE_REQUEST_INVALID", "Select an exact target generator.");
            var state = store.read(workspaceId).orElse(null);
            if (state == null) return reject(result, "WORKSPACE_NOT_FOUND", "Reopen the source workspace.");
            result.addProperty("revision", state.revision());
            // Coordinates with product mutations without changing revision, event sequence or persistence.
            var coordinated = store.coordinate(workspaceId, state.revision(), live -> {
                try { return new VersionCopyUpgradeService().preview(live, roots == null ? null : roots.apply(workspaceId),
                        request.get("targetGeneratorId").getAsString(), identities); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            });
            if (coordinated.status() != RevisionedWorkspaceStore.TransactionResult.Status.COORDINATED)
                return reject(result, "WORKSPACE_REVISION_CONFLICT", "The source changed. Request a new plan.");
            result.addProperty("status", "succeeded"); result.add("plan", coordinated.value()); return result;
        } catch (java.io.UncheckedIOException error) {
            return reject(result, "UPGRADE_SOURCE_UNAVAILABLE", error.getCause().getMessage());
        } catch (com.google.gson.JsonParseException error) {
            return reject(result, "UPGRADE_SOURCE_UNAVAILABLE", "A saved source definition is invalid JSON. Repair it before previewing.");
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException error) {
            return reject(result, "UPGRADE_REQUEST_INVALID", error.getMessage() == null ? "Invalid upgrade preview request." : error.getMessage());
        }
    }

    private static JsonObject reject(JsonObject result, String code, String text) {
        result.addProperty("status", "rejected"); JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("code", code); diagnostic.addProperty("message", text); result.getAsJsonArray("diagnostics").add(diagnostic); return result;
    }
}
