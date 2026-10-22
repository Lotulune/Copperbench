package dev.copperbench.agent;

import com.google.gson.JsonObject;
import java.util.Set;

/** Explicit, per-conversation overrides; never writes the host CLI configuration. */
record AgentTurnOptions(String effort, String permission) {
    static final AgentTurnOptions DEFAULT = new AgentTurnOptions(null, "default");
    static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

    AgentTurnOptions {
        if (effort != null && !EFFORTS.contains(effort)) throw new IllegalArgumentException("思考强度无效");
        if (!Set.of("default", "auto-approve").contains(permission)) throw new IllegalArgumentException("权限选项无效");
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
