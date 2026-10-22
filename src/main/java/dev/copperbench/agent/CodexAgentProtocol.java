package dev.copperbench.agent;

import com.google.gson.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Codex app-server v2. Field names checked against the CLI's generated JSON schemas. */
final class CodexAgentProtocol implements AgentProtocol {
    private final Channel channel;
    private final Path workspace;
    private volatile String threadId;
    private volatile String turnId;
    private String selectedModel;
    private AgentTurnOptions options = AgentTurnOptions.DEFAULT;
    private List<AgentAttachments.Item> attachments = List.of();
    CodexAgentProtocol(Channel channel, Path workspace, String threadId) { this.channel = channel; this.workspace = workspace; this.threadId = threadId; }
    @Override public List<String> arguments() { return List.of("app-server"); }
    @Override public void useModel(String model) { selectedModel = AgentModels.id(model); }
    @Override public void configure(AgentTurnOptions options, List<AgentAttachments.Item> attachments) {
        this.options = options; this.attachments = attachments;
    }

    @Override public JsonObject models() throws Exception {
        check(); JsonArray models = new JsonArray(); String cursor = null, preferred = null; Set<String> cursors = new HashSet<>();
        for (int page = 0; page < 10; page++) {
            JsonObject params = AgentJson.object("limit", 100, "includeHidden", false);
            if (cursor != null) params.addProperty("cursor", cursor);
            JsonObject result = channel.rpc("model/list", params);
            for (JsonElement value : AgentJson.list(result, "data")) {
                JsonObject row = value.getAsJsonObject(); String model = AgentJson.required(row, "model");
                AgentModels.add(models, model, AgentJson.string(row, "displayName"));
                if (AgentJson.bool(row, "isDefault")) preferred = model;
            }
            cursor = AgentJson.string(result, "nextCursor");
            if (cursor.isBlank()) return AgentModels.catalog(models, preferred, "cli", false);
            if (!cursors.add(cursor)) throw new java.io.IOException("模型列表分页无效");
        }
        throw new java.io.IOException("模型列表超过读取上限");
    }

    @Override public void check() throws Exception {
        channel.rpc("initialize", AgentJson.object("clientInfo", AgentJson.object("name", "copperbench", "title", "Copperbench", "version", "1.0")));
        channel.send(AgentJson.object("method", "initialized", "params", new JsonObject()));
    }
    @Override public void start(String text, String instructions) throws Exception {
        check();
        JsonObject params = AgentJson.object("cwd", workspace.toString(), "approvalPolicy", options.autoApprove() ? "never" : "on-request",
                "sandbox", options.autoApprove() ? "danger-full-access" : "workspace-write", "developerInstructions", instructions);
        if (selectedModel != null) params.addProperty("model", selectedModel);
        String method = "thread/start";
        if (threadId != null) { method = "thread/resume"; params.addProperty("threadId", threadId); }
        JsonObject response = channel.rpc(method, params);
        threadId = AgentJson.required(AgentJson.child(response, "thread"), "id"); channel.remoteId(threadId);
        JsonArray input = AgentJson.array(AgentJson.object("type", "text", "text", AgentAttachments.prompt(text, attachments)));
        for (AgentAttachments.Item item : attachments) if (item.image()) input.add(AgentJson.object("type", "localImage", "path", item.path().toString()));
        JsonObject request = AgentJson.object("threadId", threadId, "input", input);
        if (selectedModel != null) request.addProperty("model", selectedModel);
        if (options.effort() != null) request.addProperty("effort", options.effort());
        JsonObject turn = channel.rpc("turn/start", request);
        turnId = AgentJson.required(AgentJson.child(turn, "turn"), "id");
        channel.ready();
    }

    @Override public void receive(JsonObject frame) {
        if (frame.has("id") && !frame.has("method")) return;
        String method = AgentJson.string(frame, "method"); JsonObject params = AgentJson.child(frame, "params");
        if (frame.has("id")) { request(frame.get("id"), method, params); return; }
        switch (method) {
            case "turn/started" -> turnId = AgentJson.string(AgentJson.child(params, "turn"), "id");
            case "item/agentMessage/delta" -> channel.text("assistant:" + AgentJson.string(params, "itemId"), "assistant", AgentJson.string(params, "delta"), true);
            case "item/commandExecution/outputDelta" -> channel.text("tool:" + AgentJson.string(params, "itemId"), "tool", AgentJson.string(params, "delta"), true);
            case "item/started", "item/completed" -> item(AgentJson.child(params, "item"), method.equals("item/completed"));
            case "turn/completed" -> {
                JsonObject turn = AgentJson.child(params, "turn"); String status = AgentJson.string(turn, "status");
                channel.complete(status.equals("completed"), status.equals("interrupted"), status.equals("failed")
                        ? AgentJson.detail(turn.get("error")) : status.equals("completed") || status.equals("interrupted") ? null : "未知的 Codex 结束状态：" + status);
            }
            case "error" -> channel.diagnostic("Codex：" + AgentJson.detail(params.get("error")));
            case "serverRequest/resolved" -> {
                JsonElement id = params.get("requestId"); if (id != null) channel.removeApproval("codex:" + id);
            }
            default -> { }
        }
    }

    private void item(JsonObject item, boolean complete) {
        String type = AgentJson.string(item, "type"), id = AgentJson.string(item, "id");
        if (type.equals("agentMessage") && complete) channel.text("assistant:" + id, "assistant", AgentJson.string(item, "text"), false);
        else if (type.equals("commandExecution")) {
            if (!complete) channel.text("tool:" + id, "tool", AgentJson.string(item, "command") + "\n", true);
            else if (item.has("aggregatedOutput")) channel.text("tool:" + id, "tool",
                    AgentJson.string(item, "command") + "\n" + AgentJson.string(item, "aggregatedOutput") + "\n状态：" + AgentJson.string(item, "status"), false);
        } else if (type.equals("fileChange")) channel.text("tool:" + id, "tool",
                "文件修改：" + AgentJson.detail(item.get("changes")) + "\n状态：" + AgentJson.string(item, "status"), false);
        else if (type.equals("mcpToolCall")) channel.text("tool:" + id, "tool",
                AgentJson.string(item, "server") + " / " + AgentJson.string(item, "tool") + " · " + AgentJson.string(item, "status"), false);
    }

    private void request(JsonElement wireId, String method, JsonObject params) {
        String id = "codex:" + wireId;
        if (method.equals("item/commandExecution/requestApproval") || method.equals("item/fileChange/requestApproval")) {
            JsonArray choices = new JsonArray();
            JsonArray available = AgentJson.list(params, "availableDecisions");
            List<String> decisions = available.isEmpty() ? List.of("accept", "decline", "cancel")
                    : available.asList().stream().filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString)
                    .filter(value -> Set.of("accept", "decline", "cancel", "acceptForSession").contains(value)).toList();
            for (String decision : decisions) choices.add(AgentJson.object("id", decision, "label", switch (decision) {
                case "accept" -> "允许这次"; case "acceptForSession" -> "允许本次 CLI 会话"; case "decline" -> "拒绝"; default -> "停止本轮";
            }));
            if (choices.isEmpty()) { reject(wireId, "当前 CLI 批准类型尚不可呈现；请停止后检查 CLI 配置"); return; }
            JsonObject detail = params.deepCopy();
            if (method.contains("commandExecution") && params.has("command")) {
                // The command is repeated in parsed actions and policy proposals. Decisions are rendered separately;
                // this client never offers persistent policy amendments. Keep the exact command and access scope.
                detail.remove("commandActions"); detail.remove("proposedExecpolicyAmendment"); detail.remove("availableDecisions");
            }
            channel.approval(id, method.contains("commandExecution") ? "允许运行命令？" : "允许修改文件？", AgentJson.detail(detail), choices,
                    choice -> respond(wireId, AgentJson.object("decision", choice)));
        } else if (method.equals("item/permissions/requestApproval")) {
            JsonObject requested = AgentJson.child(params, "permissions");
            channel.approval(id, "允许本轮扩大访问范围？", AgentJson.detail(params), AgentJson.array(
                    AgentJson.object("id", "allow", "label", "仅本轮允许"), AgentJson.object("id", "deny", "label", "拒绝")), choice ->
                    respond(wireId, AgentJson.object("scope", "turn", "permissions", choice.equals("allow") ? requested : new JsonObject())));
        } else if (method.equals("item/tool/requestUserInput")) {
            JsonArray questions = AgentJson.list(params, "questions");
            JsonObject answers = new JsonObject(); AtomicInteger remaining = new AtomicInteger(questions.size());
            if (questions.isEmpty()) { respond(wireId, AgentJson.object("answers", answers)); return; }
            if (questions.asList().stream().anyMatch(question -> AgentJson.bool(question.getAsJsonObject(), "isSecret"))) {
                reject(wireId, "凭据请通过 CLI 官方登录流程提供"); return;
            }
            for (JsonElement element : questions) {
                JsonObject question = element.getAsJsonObject(); String questionId = AgentJson.required(question, "id");
                JsonArray choices = new JsonArray();
                for (JsonElement option : AgentJson.list(question, "options")) {
                    String label = AgentJson.string(option.getAsJsonObject(), "label");
                    choices.add(AgentJson.object("id", label, "label", label, "description", AgentJson.string(option.getAsJsonObject(), "description")));
                }
                channel.question(id + ":" + questionId, AgentJson.string(question, "header"), AgentJson.string(question, "question"),
                        choices, false, AgentJson.bool(question, "isOther") || choices.isEmpty(), selected -> {
                    synchronized (answers) {
                        JsonArray values = new JsonArray(); selected.forEach(values::add);
                        answers.add(questionId, AgentJson.object("answers", values));
                        if (remaining.decrementAndGet() == 0) respond(wireId, AgentJson.object("answers", answers));
                    }
                });
            }
        } else {
            reject(wireId, "Copperbench 无法处理此 CLI 请求：" + method);
            channel.diagnostic("CLI 请求暂不支持：" + method + "。该动作未获批准，可停止并调整任务。");
        }
    }

    private void respond(JsonElement id, JsonObject result) { channel.send(AgentJson.object("id", id, "result", result)); }
    private void reject(JsonElement id, String message) { channel.send(AgentJson.object("id", id, "error", AgentJson.object("code", -32601, "message", message))); }
    @Override public void stop() {
        if (threadId != null && turnId != null) channel.send(AgentJson.object("id", "copperbench-stop", "method", "turn/interrupt", "params", AgentJson.object("threadId", threadId, "turnId", turnId)));
    }
}
