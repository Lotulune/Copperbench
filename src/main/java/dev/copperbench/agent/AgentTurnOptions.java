package dev.copperbench.agent;

import com.google.gson.JsonObject;
import java.util.Set;

/** Explicit, per-conversation overrides; never writes the host CLI configuration. */
record AgentTurnOptions(String effort, String permission) {
    static final AgentTurnOptions DEFAULT = new AgentTurnOptions(null, "default");
    static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

    AgentTurnOptions {
        // Catalogs advertise non-empty effort identifiers; the Codex adapter validates per-model membership.
        if (effort != null && !effort.matches("[a-z][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("思考强度无效");
        if (!Set.of("default", "auto-approve", "read-only", "full-access").contains(permission)) throw new IllegalArgumentException("权限选项无效");
    }

    static AgentTurnOptions from(JsonObject request, AgentTurnOptions fallback) {
        return new AgentTurnOptions(request.has("effort") ? AgentJson.required(request, "effort") : fallback.effort,
                request.has("permission") ? AgentJson.required(request, "permission") : fallback.permission);
    }

    boolean autoApprove() { return permission.equals("auto-approve"); }
    void put(JsonObject target) {
        if (effort != null) target.addProperty("effort", effort);
        target.addProperty("permission", permission);
    }
}
