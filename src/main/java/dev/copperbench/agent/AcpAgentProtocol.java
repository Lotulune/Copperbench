package dev.copperbench.agent;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Native ACP client; advertises no client-side filesystem or terminal execution capability. */
final class AcpAgentProtocol implements AgentProtocol {
    enum Backend {
        GROK("grok", List.of("agent", "--no-leader", "stdio"), "session/load"),
        DEEPSEEK("deepseek", List.of("--profile", "acp"), "session/resume");
        final String id;
        final List<String> arguments;
        final String resumeMethod;
        Backend(String id, List<String> arguments, String resumeMethod) {
            this.id = id; this.arguments = arguments; this.resumeMethod = resumeMethod;
        }
    }
    private final Backend backend;
    private final Channel channel;
    private final Path workspace;
    private final Map<String, JsonObject> toolCalls = new LinkedHashMap<>();
    private volatile String sessionId;
    private volatile boolean prompting;
    private boolean closeSession;
    private int messageIndex;
    private boolean newMessage = true;
    private volatile boolean denied;
    private volatile String clientError;
    AcpAgentProtocol(Backend backend, Channel channel, Path workspace, String sessionId) {
        this.backend = backend; this.channel = channel; this.workspace = workspace; this.sessionId = sessionId;
    }
    private String label() { return AgentProvider.label(backend.id); }
    @Override public List<String> arguments() { return backend.arguments; }

    @Override public boolean stdoutDiagnostic(String line) {
        // dsh 0.2.1-rc.2's account catalog calls console.info while constructing session config options.
        if (backend != Backend.DEEPSEEK || !line.matches("\\[llm-deepseek-account] model listing from \\S+ loaded \\d+ model\\(s\\), skipped \\d+ row\\(s\\)")) return false;
        channel.text("deepseek-catalog", "tool", line, false);
        return true;
    }

    @Override public void start(String text, String instructions) throws Exception {
        JsonObject initialized = channel.rpc("initialize", AgentJson.object("protocolVersion", 1,
                "clientCapabilities", new JsonObject(), "clientInfo", AgentJson.object("name", "copperbench", "title", "Copperbench", "version", "1.0")));
        if (!new JsonPrimitive(1).equals(initialized.get("protocolVersion")))
            throw new IOException(label() + " 的 ACP 版本不兼容，请更新 CLI 后重试。");
        if (backend == Backend.DEEPSEEK) {
            JsonObject capabilities = AgentJson.child(AgentJson.child(initialized, "agentCapabilities"), "sessionCapabilities");
            closeSession = capabilities.has("close");
            if (sessionId != null && !capabilities.has("resume"))
                throw new IOException("此版本的 DeepSeek Harness 不支持恢复会话，请更新 dsh 或新建对话。");
        }
        JsonObject params = AgentJson.object("cwd", workspace.toString(), "mcpServers", new JsonArray());
        String method = "session/new";
        if (sessionId != null) { method = backend.resumeMethod; params.addProperty("sessionId", sessionId); }
        JsonObject session = channel.rpc(method, params);
        if (sessionId == null) sessionId = AgentJson.required(session, "sessionId");
        channel.remoteId(sessionId); prompting = true;
        channel.ready();
        channel.send(AgentJson.object("jsonrpc", "2.0", "id", "copperbench-prompt", "method", "session/prompt",
                "params", AgentJson.object("sessionId", sessionId, "prompt",
                        AgentJson.array(AgentJson.object("type", "text", "text", instructions + "\nPlayer request:\n" + text)))));
    }

    private void completed(JsonObject frame) {
        if (frame.has("error")) { channel.complete(false, false, AgentJson.detail(frame.get("error"))); return; }
        JsonObject result = AgentJson.child(frame, "result");
        String stop = AgentJson.string(result, "stopReason");
        boolean success = stop.equals("end_turn");
        // Tool errors remain in the transcript: an agent may recover using a new tool-call ID.
        // end_turn settles the conversation; it is not a build or gameplay acceptance receipt.
        String failure = clientError != null ? clientError : denied ? label() + " 有操作未获授权，请检查拒绝信息后明确继续。"
                : success || stop.equals("cancelled") ? null : label() + " 本轮结束：" + stop;
        channel.complete(success && failure == null, stop.equals("cancelled"), failure);
    }

    @Override public void receive(JsonObject frame) {
        if (frame.has("id") && !frame.has("method") && frame.get("id").isJsonPrimitive()
                && frame.get("id").getAsString().equals("copperbench-prompt")) { completed(frame); return; }
        if (frame.has("id") && !frame.has("method")) return;
        String method = AgentJson.string(frame, "method"); JsonObject params = AgentJson.child(frame, "params");
        if (frame.has("id")) {
            if (method.equals("session/request_permission")) permission(frame.get("id"), params);
            else {
                channel.send(AgentJson.object("jsonrpc", "2.0", "id", frame.get("id"), "error", AgentJson.object("code", -32601, "message", "Unsupported client method: " + method)));
                clientError = label() + " 请求了未启用的客户端能力：" + method + "；未执行该操作。";
                channel.diagnostic(clientError);
            }
            return;
        }
        if (!prompting || !method.equals("session/update") || !sameSession(params)) return; // Replay is already in local history.
        JsonObject update = AgentJson.child(params, "update");
        switch (AgentJson.string(update, "sessionUpdate")) {
            case "agent_message_chunk" -> {
                if (newMessage) { messageIndex++; newMessage = false; }
                channel.text("assistant:" + messageIndex, "assistant", AgentJson.string(AgentJson.child(update, "content"), "text"), true);
            }
            case "tool_call", "tool_call_update" -> {
                newMessage = true;
                String id = AgentJson.required(update, "toolCallId");
                JsonObject tool = toolCalls.computeIfAbsent(id, ignored -> new JsonObject());
                merge(tool, update);
                channel.text("tool:" + id, "tool", toolDetail(tool), false);
                if (toolCalls.size() > 1000) toolCalls.remove(toolCalls.keySet().iterator().next());
            }
            case "plan" -> channel.text("plan", "tool", "制作步骤：\n" + AgentJson.detail(update.get("entries")), false);
            default -> { }
        }
    }

    private void permission(JsonElement wireId, JsonObject params) {
        JsonObject requested = AgentJson.child(params, "toolCall");
        JsonObject cached = toolCalls.get(AgentJson.string(requested, "toolCallId"));
        JsonObject tool = cached == null ? new JsonObject() : cached.deepCopy();
        merge(tool, requested);
        if (!prompting || !sameSession(params) || (AgentJson.string(tool, "title").isBlank() && !tool.has("rawInput"))) {
            cancelPermission(wireId, "未提供当前操作的完整信息，审批请求已取消。"); return;
        }
        JsonArray choices = new JsonArray();
        Map<String, String> kinds = new LinkedHashMap<>();
        for (JsonElement element : AgentJson.list(params, "options")) {
            JsonObject option = element.getAsJsonObject(); String id = AgentJson.required(option, "optionId");
            kinds.put(id, AgentJson.string(option, "kind"));
            choices.add(AgentJson.object("id", id, "label", AgentJson.required(option, "name")));
        }
        if (choices.isEmpty()) {
            cancelPermission(wireId, "未提供可选择的批准选项，该请求已取消。"); return;
        }
        String title = AgentJson.string(tool, "title");
        channel.approval(backend.id + ":" + wireId, title.isBlank() ? label() + " 请求执行操作" : title, toolDetail(tool), choices, choice -> {
            if (kinds.getOrDefault(choice, "").startsWith("reject")) denied = true;
            channel.send(AgentJson.object("jsonrpc", "2.0", "id", wireId, "result",
                    AgentJson.object("outcome", AgentJson.object("outcome", "selected", "optionId", choice))));
        });
    }

    private boolean sameSession(JsonObject params) {
        String id = AgentJson.string(params, "sessionId");
        return id.isBlank() || id.equals(sessionId);
    }
    private static void merge(JsonObject target, JsonObject update) {
        update.entrySet().forEach(entry -> { if (!entry.getValue().isJsonNull()) target.add(entry.getKey(), entry.getValue().deepCopy()); });
    }
    private static String toolDetail(JsonObject tool) {
        StringBuilder detail = new StringBuilder(AgentJson.string(tool, "title"));
        String status = AgentJson.string(tool, "status");
        if (!status.isBlank()) detail.append(" · ").append(status);
        for (String field : List.of("rawInput", "rawOutput", "content", "locations"))
            if (tool.has(field)) detail.append('\n').append(AgentJson.detail(tool.get(field)));
        return detail.toString();
    }
    private void cancelPermission(JsonElement wireId, String reason) {
        denied = true;
        channel.send(AgentJson.object("jsonrpc", "2.0", "id", wireId, "result", AgentJson.object("outcome", AgentJson.object("outcome", "cancelled"))));
        channel.diagnostic(label() + " " + reason);
    }

    @Override public void finish() throws Exception {
        if (closeSession && sessionId != null) {
            try { channel.rpc("session/close", AgentJson.object("sessionId", sessionId)); }
            catch (java.util.concurrent.CancellationException stopped) { throw stopped; }
            catch (InterruptedException interrupted) { throw interrupted; }
            catch (Exception failure) {
                Throwable cause = failure instanceof java.util.concurrent.ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
                throw new IOException(label() + " 会话关闭未获确认：" + cause.getMessage(), failure);
            }
        }
    }
    @Override public void stop() {
        if (sessionId != null) channel.send(AgentJson.object("jsonrpc", "2.0", "method", "session/cancel", "params", AgentJson.object("sessionId", sessionId)));
    }
}
