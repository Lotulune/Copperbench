package dev.copperbench.agent;

import com.google.gson.*;

import java.util.*;
import java.util.regex.Pattern;

/** Native Antigravity stream-json; never sends unsupported Claude control frames. */
final class AntigravityAgentProtocol implements AgentProtocol {
    private static final Pattern DENIED = Pattern.compile("(?i)(permission.{0,80}(denied|blocked|required)|(?:denied|blocked).{0,80}permission|requires? (?:user )?(?:approval|permission)|soft.denied|not (?:allowed|permitted)|权限.{0,20}(拒绝|需要)|操作.{0,20}未获授权)");
    private final Channel channel;
    private final String resume;
    private volatile boolean denied;
    private String lastResponseKey = "assistant:response";
    AntigravityAgentProtocol(Channel channel, String resume) { this.channel = channel; this.resume = resume; }
    @Override public List<String> arguments() {
        List<String> args = new ArrayList<>(List.of("--input-format", "stream-json", "--output-format", "stream-json"));
        if (resume != null) args.addAll(List.of("--conversation", resume));
        return args;
    }
    @Override public void start(String text, String instructions) {
        channel.ready();
        channel.send(AgentJson.object("event", "user", "message", AgentJson.object("content", instructions + "\nPlayer request:\n" + text)));
    }
    @Override public void receive(JsonObject frame) {
        switch (AgentJson.string(frame, "event")) {
            case "init" -> {
                String id = AgentJson.string(frame, "conversation_id"); if (!id.isBlank()) channel.remoteId(id);
            }
            case "step_update" -> {
                JsonObject step = AgentJson.child(frame, "step_update");
                String index = step.has("step_index") ? step.get("step_index").toString() : "response";
                String type = AgentJson.string(step, "step_type");
                if (type.equals("agent_response")) {
                    lastResponseKey = "assistant:" + index;
                    channel.text(lastResponseKey, "assistant", AgentJson.string(step, "text_delta"), true);
                } else if (type.equals("tool")) {
                    JsonObject tool = AgentJson.child(step, "tool_info");
                    String error = AgentJson.detail(tool.get("error"));
                    if (DENIED.matcher(error).find()) denied = true;
                    channel.text("tool:" + index, "tool", AgentJson.string(step, "tool_name") + " · " + AgentJson.string(step, "state") + "\n" + AgentJson.detail(tool), false);
                }
            }
            case "result" -> {
                JsonObject result = AgentJson.child(frame, "result");
                String id = AgentJson.string(result, "conversation_id"); if (!id.isBlank()) channel.remoteId(id);
                String response = AgentJson.string(result, "response");
                if (!response.isBlank()) channel.text(lastResponseKey, "assistant", response, false);
                String status = AgentJson.string(result, "status");
                String error = AgentJson.string(result, "error");
                if (DENIED.matcher(error).find()) denied = true;
                if (denied) error = "Antigravity 已拒绝需要批准的操作。其无界面模式不支持交互授权；请在官方 CLI 中调整所需权限，再返回明确继续。";
                else if (!status.equals("SUCCESS") && error.isBlank()) error = "Antigravity 本轮结束：" + status;
                channel.complete(status.equals("SUCCESS") && !denied, status.equals("INTERRUPTED"), error.isBlank() ? null : error);
            }
            default -> { }
        }
    }
    @Override public void stderr(String line) {
        if (DENIED.matcher(line).find()) {
            denied = true;
            channel.diagnostic("Antigravity 权限拒绝：" + line + "\n请在官方 CLI 中处理所需权限后重新发送；这里不会伪造批准。");
        }
    }
    @Override public void stop() {
        // No cancellation/control wire protocol exists. AgentRun terminates this owned process tree.
        channel.complete(false, true, null);
    }
    @Override public String completionError() {
        return denied ? "Antigravity 有操作因权限被拒绝；请在官方 CLI 中处理权限后明确继续。" : null;
    }
    @Override public boolean persistentAfterTurn() { return true; }
}
