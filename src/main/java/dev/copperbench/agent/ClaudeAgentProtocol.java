package dev.copperbench.agent;

import com.google.gson.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Direct Claude CLI control host. Uses the same documented wire contract as the official SDK:
 * github.com/anthropics/claude-agent-sdk-python/blob/main/src/claude_agent_sdk/_internal/query.py
 * and _autodocs/api-reference/permissions.md (stdio permission routing). No SDK or API client is installed.
 */
final class ClaudeAgentProtocol implements AgentProtocol {
    private final Channel channel;
    private final String resume;
    private String messageId = "response";
    private volatile boolean denied;
    private String selectedModel;
    private AgentTurnOptions options = AgentTurnOptions.DEFAULT;
    private List<AgentAttachments.Item> attachments = List.of();
    ClaudeAgentProtocol(Channel channel, String resume) { this.channel = channel; this.resume = resume; }
    @Override public void useModel(String model) { selectedModel = AgentModels.id(model); }
    @Override public void configure(AgentTurnOptions options, List<AgentAttachments.Item> attachments) {
        this.options = options; this.attachments = attachments;
    }

    @Override public List<String> arguments() {
        List<String> result = new ArrayList<>(List.of("-p", "--input-format", "stream-json", "--output-format", "stream-json",
                "--verbose", "--include-partial-messages", "--permission-prompt-tool", "stdio", "--permission-mode", options.autoApprove() ? "bypassPermissions" : "default"));
        if (resume != null) result.addAll(List.of("--resume", resume));
        if (selectedModel != null) result.addAll(List.of("--model", selectedModel));
        if (options.effort() != null) result.addAll(List.of("--effort", options.effort()));
        return result;
    }

    @Override public void check() throws Exception {
        channel.control(AgentJson.object("subtype", "initialize", "hooks", JsonNull.INSTANCE));
    }
    @Override public JsonObject models() throws Exception {
        JsonObject initialized = channel.control(AgentJson.object("subtype", "initialize", "hooks", JsonNull.INSTANCE));
        JsonArray models = new JsonArray();
        for (JsonElement value : AgentJson.list(initialized, "models")) {
            JsonObject row = value.getAsJsonObject();
            AgentModels.add(models, AgentJson.required(row, "value"), AgentJson.string(row, "displayName"));
        }
        return AgentModels.catalog(models, null, "cli", false);
    }
    @Override public void start(String text, String instructions) throws Exception {
        check();
        channel.ready();
        String prompt = instructions + "\nPlayer request:\n" + AgentAttachments.prompt(text, attachments);
        JsonElement content = new JsonPrimitive(prompt);
        if (attachments.stream().anyMatch(AgentAttachments.Item::image)) {
            JsonArray blocks = AgentJson.array(AgentJson.object("type", "text", "text", prompt));
            for (AgentAttachments.Item item : attachments) if (item.image()) blocks.add(AgentJson.object("type", "image",
                    "source", AgentJson.object("type", "base64", "media_type", item.mediaType(), "data", item.base64())));
            content = blocks;
        }
        channel.send(AgentJson.object("type", "user", "session_id", resume == null ? "" : resume,
                "message", AgentJson.object("role", "user", "content", content)));
    }

    @Override public void receive(JsonObject frame) {
        String type = AgentJson.string(frame, "type");
        switch (type) {
            case "system" -> {
                if (AgentJson.string(frame, "subtype").equals("init")) {
                    String id = AgentJson.string(frame, "session_id"); if (!id.isBlank()) channel.remoteId(id);
                } else if (AgentJson.string(frame, "subtype").equals("permission_denied")) {
                    denied = true; channel.diagnostic("Claude Code 拒绝了操作：" + AgentJson.detail(frame));
                } else if (AgentJson.string(frame, "subtype").equals("api_retry"))
                    channel.diagnostic("Claude Code 正在重试连接：" + AgentJson.string(frame, "error"));
            }
            case "stream_event" -> stream(AgentJson.child(frame, "event"));
            case "assistant" -> assistant(AgentJson.child(frame, "message"));
            case "user" -> {
                for (JsonElement element : AgentJson.list(AgentJson.child(frame, "message"), "content")) {
                    JsonObject block = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
                    if (AgentJson.string(block, "type").equals("tool_result"))
                        channel.text("tool:" + AgentJson.string(block, "tool_use_id"), "tool", AgentJson.detail(block.get("content")), false);
                }
            }
            case "control_request" -> permission(frame);
            case "control_cancel_request" -> channel.removeApproval("claude:" + AgentJson.string(frame, "request_id"));
            case "result" -> {
                String id = AgentJson.string(frame, "session_id"); if (!id.isBlank()) channel.remoteId(id);
                String finalText = AgentJson.string(frame, "result");
                if (!finalText.isBlank()) channel.text("assistant:" + messageId, "assistant", finalText, false);
                boolean success = AgentJson.string(frame, "subtype").equals("success") && !AgentJson.bool(frame, "is_error");
                boolean hasDenials = denied || !AgentJson.list(frame, "permission_denials").isEmpty();
                String error = !success ? AgentJson.detail(frame.get("errors")) : hasDenials ? "有操作未获 Claude Code 授权；请查看拒绝信息，调整 CLI 权限后明确继续。" : null;
                if (!success && (error == null || error.isBlank() || error.equals("null"))) error = "Claude Code 未成功完成：" + AgentJson.string(frame, "subtype");
                channel.complete(success && !hasDenials, false, error);
            }
            default -> { }
        }
    }

    private void stream(JsonObject event) {
        String type = AgentJson.string(event, "type");
        if (type.equals("message_start")) {
            String id = AgentJson.string(AgentJson.child(event, "message"), "id"); if (!id.isBlank()) messageId = id;
        } else if (type.equals("content_block_delta")) {
            JsonObject delta = AgentJson.child(event, "delta");
            if (AgentJson.string(delta, "type").equals("text_delta"))
                channel.text("assistant:" + messageId, "assistant", AgentJson.string(delta, "text"), true);
        }
    }

    private void assistant(JsonObject message) {
        String id = AgentJson.string(message, "id"); if (!id.isBlank()) messageId = id;
        StringBuilder text = new StringBuilder();
        for (JsonElement value : AgentJson.list(message, "content")) {
            if (!value.isJsonObject()) continue;
            JsonObject block = value.getAsJsonObject();
            if (AgentJson.string(block, "type").equals("text")) text.append(AgentJson.string(block, "text"));
            else if (AgentJson.string(block, "type").equals("tool_use"))
                channel.text("tool:" + AgentJson.string(block, "id"), "tool", AgentJson.string(block, "name") + "\n" + AgentJson.detail(block.get("input")), false);
        }
        if (!text.isEmpty()) channel.text("assistant:" + messageId, "assistant", text.toString(), false);
    }

    private void permission(JsonObject frame) {
        String wireId = AgentJson.required(frame, "request_id"), id = "claude:" + wireId;
        JsonObject request = AgentJson.child(frame, "request");
        if (!AgentJson.string(request, "subtype").equals("can_use_tool")) {
            channel.send(AgentJson.object("type", "control_response", "response", AgentJson.object("subtype", "error", "request_id", wireId,
                    "error", "Unsupported control request: " + AgentJson.string(request, "subtype"))));
            channel.diagnostic("Claude 请求的控制操作暂不支持，未执行该操作：" + AgentJson.string(request, "subtype")); return;
        }
        String tool = AgentJson.required(request, "tool_name"); JsonObject input = AgentJson.child(request, "input");
        if (tool.equals("AskUserQuestion")) { questions(wireId, id, input); return; }
        channel.approval(id, "允许 Claude Code 使用 " + tool + "？", AgentJson.detail(input),
                AgentJson.array(AgentJson.object("id", "allow", "label", "允许这次"), AgentJson.object("id", "deny", "label", "拒绝")), choice -> {
                    if (choice.equals("allow")) answer(wireId, AgentJson.object("behavior", "allow", "updatedInput", input));
                    else { denied = true; answer(wireId, AgentJson.object("behavior", "deny", "message", "The player declined this operation.")); }
                });
    }

    private void questions(String wireId, String id, JsonObject input) {
        JsonArray questions = AgentJson.list(input, "questions");
        if (questions.isEmpty()) { answer(wireId, AgentJson.object("behavior", "deny", "message", "No question was supplied.")); return; }
        JsonObject responses = new JsonObject(); AtomicInteger remaining = new AtomicInteger(questions.size()); int index = 0;
        for (JsonElement element : questions) {
            JsonObject question = element.getAsJsonObject(); String questionText = AgentJson.required(question, "question");
            JsonArray choices = new JsonArray();
            for (JsonElement option : AgentJson.list(question, "options")) {
                String label = AgentJson.string(option.getAsJsonObject(), "label");
                choices.add(AgentJson.object("id", label, "label", label, "description", AgentJson.string(option.getAsJsonObject(), "description")));
            }
            boolean multiSelect = AgentJson.bool(question, "multiSelect");
            channel.question(id + ":question:" + index++, AgentJson.string(question, "header"), questionText, choices, multiSelect, true, answers -> {
                synchronized (responses) {
                    if (multiSelect) {
                        JsonArray selected = new JsonArray(); answers.forEach(selected::add); responses.add(questionText, selected);
                    } else responses.addProperty(questionText, answers.getFirst());
                    if (remaining.decrementAndGet() == 0) {
                        JsonObject updated = input.deepCopy(); updated.add("answers", responses);
                        answer(wireId, AgentJson.object("behavior", "allow", "updatedInput", updated));
                    }
                }
            });
        }
    }

    private void answer(String requestId, JsonObject result) {
        channel.send(AgentJson.object("type", "control_response", "response",
                AgentJson.object("subtype", "success", "request_id", requestId, "response", result)));
    }
    @Override public void stop() {
        channel.send(AgentJson.object("type", "control_request", "request_id", "copperbench-stop", "request", AgentJson.object("subtype", "interrupt")));
    }
}
