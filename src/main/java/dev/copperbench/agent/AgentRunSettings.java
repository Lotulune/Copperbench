package dev.copperbench.agent;

import com.google.gson.*;
import java.util.*;

/** Explicit, per-conversation overrides. Empty model/effort retain the CLI or resumed thread defaults. */
record AgentRunSettings(String model, String effort, String permissionProfile) {
    static final AgentRunSettings DEFAULT = new AgentRunSettings("", "", "workspace");
    static final List<String> PROFILES = List.of("workspace", "read-only", "full-access");
    static AgentRunSettings parse(JsonElement value) {
        if (value == null || value.isJsonNull()) return DEFAULT;
        if (!value.isJsonObject()) throw new IllegalArgumentException("Agent settings must be an object");
        JsonObject object = value.getAsJsonObject();
        if (!Set.of("model", "effort", "permissionProfile").containsAll(object.keySet())) throw new IllegalArgumentException("Unknown Agent setting");
        for (String key : object.keySet()) if (!object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Agent settings must contain text values");
        String model = AgentJson.string(object, "model"), effort = AgentJson.string(object, "effort");
        String permission = object.has("permissionProfile") ? AgentJson.string(object, "permissionProfile") : "workspace";
        if (model.length() > 256 || effort.length() > 64 || !PROFILES.contains(permission)) throw new IllegalArgumentException("Unsupported Agent setting");
        if (model.isBlank() && !effort.isBlank()) throw new IllegalArgumentException("Choose a model before selecting its reasoning effort");
        return new AgentRunSettings(model, effort, permission);
    }
    boolean defaults() { return equals(DEFAULT); }
    String sandbox() { return switch (permissionProfile) { case "read-only" -> "read-only"; case "full-access" -> "danger-full-access"; default -> "workspace-write"; }; }
    String approval() { return permissionProfile.equals("read-only") ? "never" : "on-request"; }
    String permissionDescription() { return sandbox() + " / " + approval(); }
    JsonObject json() { return AgentJson.object("model", model, "effort", effort, "permissionProfile", permissionProfile); }
    String effectiveEffort(JsonObject catalog) {
        if (!effort.isEmpty() || model.isEmpty()) return effort;
        JsonObject selected = AgentJson.list(catalog, "models").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(item -> model.equals(AgentJson.string(item, "id"))).findFirst().orElseThrow();
        String preferred = AgentJson.string(selected, "defaultEffort");
        if (!preferred.isEmpty() && !AgentJson.list(selected, "efforts").asList().contains(new JsonPrimitive(preferred)))
            throw new IllegalArgumentException("The CLI advertised an unsupported default reasoning effort");
        return preferred;
    }
    void validate(JsonObject catalog) {
        if (!AgentJson.list(catalog, "permissionProfiles").asList().contains(new JsonPrimitive(permissionProfile)))
            throw new IllegalArgumentException("This permission profile is not allowed by the CLI configuration");
        if (model.isEmpty()) return;
        JsonObject selected = AgentJson.list(catalog, "models").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(item -> model.equals(AgentJson.string(item, "id"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The selected model is no longer in the CLI catalog. Refresh the model list."));
        if (!effort.isEmpty() && !AgentJson.list(selected, "efforts").asList().contains(new JsonPrimitive(effort)))
            throw new IllegalArgumentException("The selected reasoning effort is not supported by this model");
    }
}
