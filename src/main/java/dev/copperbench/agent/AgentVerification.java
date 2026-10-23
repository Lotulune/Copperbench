package dev.copperbench.agent;

import com.google.gson.*;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore.*;
import java.util.*;

/** Fixed user-selected Core operations, using the same lifecycle and permissions as the task drawer. */
public final class AgentVerification {
    private final WorkspaceEntryAdapter entry;
    private final UUID workspaceId;
    public AgentVerification(WorkspaceEntryAdapter entry, UUID workspaceId) { this.entry = entry; this.workspaceId = workspaceId; }
    JsonObject start(String kind) {
        Operation operation = switch (kind) { case "build" -> Operation.BUILD_WORKSPACE; case "validate" -> Operation.VALIDATE_WORKSPACE;
            default -> throw new IllegalArgumentException("Unsupported verification operation"); };
        return command(operation, AgentJson.object("scope", "workspace", "clientMutationId", UUID.randomUUID()));
    }
    JsonObject cancel(String id) { return command(Operation.CANCEL_TASK, AgentJson.object("taskId", id)); }
    private JsonObject command(Operation operation, JsonObject payload) {
        var workbench = entry.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_WORKBENCH, new JsonObject()));
        var result = entry.executeAndPublish(Command.of(UUID.randomUUID(), workspaceId, workbench.revision(), operation, payload)).result();
        return new Gson().toJsonTree(result).getAsJsonObject();
    }
    JsonObject read(String id) {
        var result = entry.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_TASK, AgentJson.object("taskId", id, "afterLogSequence", 0)));
        if (!result.data().isJsonObject()) throw new IllegalStateException("Verification task is unavailable");
        return result.data().getAsJsonObject();
    }
}
