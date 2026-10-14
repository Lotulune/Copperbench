package dev.copperbench.core.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.generator.RequiredCaseManifest;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Per-request version negotiation for required-case acceptance; never changes the v1.0 wire objects. */
public final class AcceptanceExtension {
    public static final String CONTRACT = "required_cases";
    public static final String VERSION = "1";
    private AcceptanceExtension() {}

    /** Executes through existing permission/revision checked Core commands, without issuing authority. */
    public static JsonObject dispatch(UUID requestId, UUID workspaceId, JsonObject request,
            Function<UiCore.Command, UiCore.CommandOutcome> commands,
            Function<UiCore.Query, UiCore.QueryResult> queries, Function<UUID, JsonObject> evidence,
            Function<JsonObject, JsonObject> coverageDiagnostics) {
        JsonObject result = new JsonObject();
        result.addProperty("contract", CONTRACT);
        result.addProperty("schemaVersion", VERSION);
        result.addProperty("requestId", requestId.toString());
        try {
            if (!request.has("supportedVersions") || !request.get("supportedVersions").isJsonArray()
                    || request.getAsJsonArray("supportedVersions").size() > 16
                    || request.getAsJsonArray("supportedVersions").asList().stream().anyMatch(value -> !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                    || request.getAsJsonArray("supportedVersions").asList().stream().noneMatch(value -> value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString() && VERSION.equals(value.getAsString())))
                return failed(result, "ACCEPTANCE_VERSION_UNSUPPORTED", "Negotiate required_cases version 1 before using this contract.");
            String operation = request.get("operation").getAsString();
            boolean detailed = request.has("diagnosticVersions");
            if (detailed && (!"get".equals(operation) || !request.get("diagnosticVersions").isJsonArray()
                    || request.getAsJsonArray("diagnosticVersions").isEmpty() || request.getAsJsonArray("diagnosticVersions").size() > 16
                    || request.getAsJsonArray("diagnosticVersions").asList().stream().anyMatch(value -> !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                    || request.getAsJsonArray("diagnosticVersions").asList().stream().noneMatch(value -> "1".equals(value.getAsString()))))
                return failed(result, "ACCEPTANCE_DIAGNOSTICS_VERSION_UNSUPPORTED", "Negotiate diagnostic version 1 on a get request.");
            JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload").deepCopy() : new JsonObject();
            if (!request.keySet().stream().allMatch(Set.of("supportedVersions", "operation", "payload", "expectedRevision", "diagnosticVersions")::contains))
                throw new IllegalArgumentException("Unknown acceptance request field.");
            result.addProperty("selectedVersion", VERSION);
            result.addProperty("status", "succeeded");
            switch (operation) {
                case "negotiate", "catalog" -> {
                    if (!payload.isEmpty()) throw new IllegalArgumentException("Discovery payload must be empty.");
                    JsonArray manifests = new JsonArray();
                    RequiredCaseManifest.catalog().forEach(manifest -> manifests.add(manifest.projection()));
                    result.add("manifests", manifests);
                }
                case "get" -> {
                    requireKeys(payload, Set.of("taskId", "afterLogSequence"));
                    UUID taskId = UUID.fromString(payload.get("taskId").getAsString());
                    var core = queries.apply(UiCore.Query.of(requestId, workspaceId, UiCore.Operation.GET_TASK, payload));
                    result.add("core", UiCore.wireGson().toJsonTree(core));
                    result.addProperty("status", core.status());
                    result.addProperty("revision", core.revision());
                    result.add("diagnostics", UiCore.wireGson().toJsonTree(core.diagnostics()));
                    if (core.status().equals("succeeded")) result.add("requiredCases", evidence.apply(taskId));
                    if (detailed && core.status().equals("succeeded"))
                        result.add("coverageDiagnostics", coverageDiagnostics.apply(core.data().getAsJsonObject().getAsJsonObject("task")));
                }
                case "run", "export" -> {
                    boolean run = operation.equals("run");
                    requireKeys(payload, run ? Set.of("requiredCases", "taskAuthorizationId")
                            : Set.of("verifiedTaskId", "allowHistorical", "taskAuthorizationId"));
                    if (run) payload.add("requiredCases", RequiredCaseManifest.parse(payload.getAsJsonObject("requiredCases")).projection());
                    else {
                        UUID.fromString(payload.get("verifiedTaskId").getAsString());
                        if (payload.has("allowHistorical") && (!payload.get("allowHistorical").isJsonPrimitive()
                                || !payload.getAsJsonPrimitive("allowHistorical").isBoolean()))
                            throw new IllegalArgumentException("allowHistorical must be a boolean.");
                        payload.addProperty("requireRequiredCases", true);
                    }
                    if (!request.has("expectedRevision") || !request.get("expectedRevision").isJsonPrimitive()
                            || !request.getAsJsonPrimitive("expectedRevision").isNumber())
                        throw new IllegalArgumentException("expectedRevision must be an integer.");
                    long revision = request.get("expectedRevision").getAsBigDecimal().longValueExact();
                    payload.addProperty("clientMutationId", requestId.toString());
                    payload.addProperty("scope", "workspace");
                    var core = commands.apply(UiCore.Command.of(requestId, workspaceId, revision,
                            run ? UiCore.Operation.RUN_GAMETEST : UiCore.Operation.EXPORT_WORKSPACE, payload)).result();
                    result.add("core", UiCore.wireGson().toJsonTree(core));
                    result.addProperty("status", core.status());
                    result.addProperty("newRevision", core.newRevision());
                    result.add("task", core.task().deepCopy());
                    result.add("diagnostics", UiCore.wireGson().toJsonTree(core.diagnostics()));
                    result.add("conflict", core.conflict().deepCopy());
                }
                default -> throw new IllegalArgumentException("Unknown acceptance operation.");
            }
            return result;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | ArithmeticException exception) {
            return failed(result, "ACCEPTANCE_REQUEST_INVALID", exception.getMessage());
        }
    }

    private static void requireKeys(JsonObject payload, Set<String> allowed) {
        if (!allowed.containsAll(payload.keySet())) throw new IllegalArgumentException("Unknown acceptance payload field.");
    }

    private static JsonObject failed(JsonObject result, String code, String message) {
        result.addProperty("status", "failed");
        JsonArray diagnostics = new JsonArray();
        JsonObject args = new JsonObject(); args.addProperty("detail", message == null ? "Invalid request." : message);
        diagnostics.add(UiCore.wireGson().toJsonTree(UiCore.Diagnostic.error(code, "diagnostic.acceptance_request",
                "Acceptance request rejected: {detail}", args, null, null)));
        result.add("diagnostics", diagnostics);
        return result;
    }
}
