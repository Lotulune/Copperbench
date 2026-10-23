package dev.copperbench.agent;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Reads only documented catalog/policy RPCs. Never creates a thread or sends a model turn. */
final class CodexModelCatalog {
    @FunctionalInterface interface Rpc { JsonObject call(String method, JsonObject params) throws Exception; }
    static JsonObject read(Rpc rpc) throws Exception {
        JsonArray models = new JsonArray(); Set<String> seenModels = new HashSet<>(), cursors = new HashSet<>();
        String cursor = "";
        do {
            JsonObject params = AgentJson.object("limit", 100, "includeHidden", false);
            if (!cursor.isEmpty()) params.addProperty("cursor", cursor);
            JsonObject page = rpc.call("model/list", params);
            if (!page.has("data") || !page.get("data").isJsonArray()) throw new IOException("The CLI did not return a supported model catalog");
            for (JsonElement value : page.getAsJsonArray("data")) {
                JsonObject item = value.getAsJsonObject(); String id = AgentJson.required(item, "model");
                if (id.length() > 256) throw new IOException("The CLI returned an invalid model identifier");
                if (!seenModels.add(id)) continue;
                JsonArray efforts = new JsonArray();
                for (JsonElement entry : AgentJson.list(item, "supportedReasoningEfforts")) {
                    String effort = AgentJson.required(entry.getAsJsonObject(), "reasoningEffort");
                    if (effort.length() > 64) throw new IOException("The CLI returned an invalid reasoning effort");
                    efforts.add(effort);
                }
                models.add(AgentJson.object("id", id, "label", AgentJson.bounded(AgentJson.required(item, "displayName"), 256),
                        "efforts", efforts, "defaultEffort", AgentJson.string(item, "defaultReasoningEffort")));
            }
            cursor = AgentJson.string(page, "nextCursor");
            if (models.size() > 500 || !cursor.isEmpty() && (!cursors.add(cursor) || cursors.size() > 8)) throw new IOException("The CLI model catalog exceeded its size limit");
        } while (!cursor.isEmpty());
        JsonObject requirements = AgentJson.child(rpc.call("configRequirements/read", new JsonObject()), "requirements");
        JsonArray permissions = new JsonArray();
        for (String profile : AgentRunSettings.PROFILES) {
            AgentRunSettings setting = new AgentRunSettings("", "", profile);
            if (allowed(requirements, "allowedSandboxModes", setting.sandbox()) && allowed(requirements, "allowedApprovalPolicies", setting.approval())) permissions.add(profile);
        }
        return AgentJson.object("state", "ready", "models", models, "permissionProfiles", permissions);
    }
    private static boolean allowed(JsonObject requirements, String key, String value) {
        return !requirements.has(key) || requirements.get(key).isJsonNull()
                || AgentJson.list(requirements, key).asList().contains(new JsonPrimitive(value));
    }
    static JsonObject detect(AgentProvider provider, Path root) throws Exception {
        if (provider.executable() == null) throw new IOException("Install Codex CLI before reading its model catalog");
        try (AgentProcess process = new AgentProcess(provider.executable(), List.of("app-server"), root)) {
            BlockingQueue<JsonObject> replies = new LinkedBlockingQueue<>(64);
            AtomicReference<Exception> failure = new AtomicReference<>(); AtomicBoolean eof = new AtomicBoolean();
            Thread reader = Thread.startVirtualThread(() -> {
                try { AgentProcess.lines(process.process.getInputStream(), line -> {
                    if (line.isBlank()) return;
                    JsonObject frame = JsonParser.parseString(line).getAsJsonObject();
                    if (frame.has("id") && !frame.has("method") && !replies.offer(frame)) throw new IllegalStateException("Too many CLI catalog replies");
                }); } catch (Exception exception) { failure.set(exception); } finally { eof.set(true); }
            });
            Thread errors = Thread.startVirtualThread(() -> { try { AgentProcess.lines(process.process.getErrorStream(), ignored -> {}); } catch (IOException ignored) {} });
            AtomicInteger ids = new AtomicInteger();
            Rpc rpc = (method, params) -> {
                int id = ids.incrementAndGet(); process.write(AgentJson.object("id", id, "method", method, "params", params).toString());
                long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                while (System.nanoTime() < deadline) {
                    JsonObject frame = replies.poll(100, TimeUnit.MILLISECONDS);
                    if (frame != null && frame.get("id").getAsString().equals(Integer.toString(id))) {
                        if (frame.has("error")) throw new IOException(AgentJson.detail(frame.get("error")));
                        if (!frame.has("result") || !frame.get("result").isJsonObject()) throw new IOException("The CLI returned an invalid catalog response");
                        return frame.getAsJsonObject("result");
                    }
                    if (failure.get() != null) throw new IOException("The CLI catalog protocol could not be read", failure.get());
                    if (eof.get() && replies.isEmpty()) throw new IOException("The CLI closed before returning its model catalog");
                }
                throw new IOException("Reading the CLI model catalog timed out");
            };
            try {
                rpc.call("initialize", AgentJson.object("clientInfo", AgentJson.object("name", "copperbench", "title", "Copperbench", "version", "1.0")));
                process.write(AgentJson.object("method", "initialized", "params", new JsonObject()).toString());
                JsonObject result = read(rpc); process.endInput(); process.await(Duration.ofSeconds(2)); process.close();
                if (process.process.isAlive() || process.hasLiveChildren()) throw new IOException("CLI catalog process cleanup was not confirmed");
                return result;
            } finally { process.close(); reader.join(1000); errors.join(1000); }
        }
    }
}
