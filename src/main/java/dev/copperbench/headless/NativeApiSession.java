package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.UUID;

/** Persistent local scripting boundary. Uses Core envelopes, with no MCP server or transport. */
public final class NativeApiSession {
    private static final int MAX_REQUEST_CHARS = 4 * 1024 * 1024;
    private final HeadlessWorkspaceEntryAdapter adapter;
    private final UUID workspaceId;
    private final PythonContext context;

    public NativeApiSession(HeadlessWorkspaceEntryAdapter adapter, UUID workspaceId) {
        this(adapter, workspaceId, new PythonContext(adapter, workspaceId));
    }

    public NativeApiSession(HeadlessWorkspaceEntryAdapter adapter, UUID workspaceId, PythonContext context) {
        this.adapter = adapter;
        this.workspaceId = workspaceId;
        this.context = context;
    }

    /** Owns neither the input nor the workspace; the launcher closes the workspace on EOF. */
    public int serve(Reader input, PrintWriter output) throws IOException {
        JsonObject ready = new JsonObject();
        ready.addProperty("nativeApiVersion", "1");
        ready.addProperty("workspaceId", workspaceId.toString());
        ready.addProperty("status", "ready");
        var authorizedOperations = new com.google.gson.JsonArray();
        var operations = new com.google.gson.JsonArray();
        for (Operation operation : Operation.values()) {
            operations.add(UiCore.wireGson().toJsonTree(operation));
            if (dev.copperbench.automation.security.TaskAuthorizationStore.capability(operation) != null)
                authorizedOperations.add(UiCore.wireGson().toJsonTree(operation));
        }
        ready.add("taskAuthorizedOperations", authorizedOperations);
        ready.add("operations", operations);
        write(output, ready);
        BufferedReader reader = new BufferedReader(input);
        while (true) {
            StringBuilder line = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1 && ch != '\n') {
                if (line.length() >= MAX_REQUEST_CHARS) {
                    write(output, error(null, "NATIVE_REQUEST_TOO_LARGE", "Request exceeds 4 Mi characters"));
                    return HeadlessExitCode.INVALID_ARGUMENTS.code();
                }
                line.append((char) ch);
            }
            if (line.isEmpty() && ch == -1) return HeadlessExitCode.SUCCESS.code();
            write(output, dispatch(line.toString()));
            if (output.checkError()) return HeadlessExitCode.INTERNAL_ERROR.code();
            if (ch == -1) return HeadlessExitCode.SUCCESS.code();
        }
    }

    JsonObject dispatch(String line) {
        String id = null;
        try {
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            id = request.get("id").getAsString();
            UUID requestId = UUID.fromString(id);
            String name = request.get("operation").getAsString();
            if ("context".equals(request.get("kind").getAsString())) {
                JsonObject response = new JsonObject();
                response.addProperty("id", id);
                response.add("result", context.dispatch(name, request.getAsJsonObject("payload")));
                return response;
            }
            Operation operation = UiCore.wireGson().fromJson(request.get("operation"), Operation.class);
            if (operation == null) throw new IllegalArgumentException("Unknown operation: " + name);
            JsonObject payload = request.has("payload") ? request.getAsJsonObject("payload").deepCopy() : new JsonObject();
            JsonObject response = new JsonObject();
            response.addProperty("id", id);
            switch (request.get("kind").getAsString()) {
                case "query" -> response.add("result", UiCore.wireGson().toJsonTree(
                        adapter.query(Query.of(requestId, workspaceId, operation, payload))));
                case "command" -> {
                    long revision = request.get("expectedRevision").getAsBigDecimal().longValueExact();
                    if (!payload.has("clientMutationId")) payload.addProperty("clientMutationId", id);
                    response.add("result", UiCore.wireGson().toJsonTree(adapter.execute(
                            Command.of(requestId, workspaceId, revision, operation, payload)).result()));
                }
                default -> throw new IllegalArgumentException("kind must be query or command");
            }
            return response;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | ArithmeticException
                 | com.google.gson.JsonParseException exception) {
            return error(id, "NATIVE_INVALID_REQUEST", "Invalid native API request: " + exception.getMessage());
        } catch (RuntimeException exception) {
            return error(id, "NATIVE_OPERATION_FAILED", "Core operation failed; inspect the application log");
        }
    }

    private static JsonObject error(String id, String code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private static void write(PrintWriter output, JsonObject value) {
        output.println(UiCore.wireGson().toJson(value));
        output.flush();
    }
}
