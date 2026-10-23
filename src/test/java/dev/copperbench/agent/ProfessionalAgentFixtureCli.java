package dev.copperbench.agent;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Deterministic subprocess fixture; never loads a real CLI or contacts a service. */
public final class ProfessionalAgentFixtureCli {
    private static JsonElement deepseekPromptId;
    private ProfessionalAgentFixtureCli() { }
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (Arrays.asList(args).contains("--version")) { System.out.println("fixture 1.0"); return; }
        if (mode.equals("child")) { Thread.sleep(120_000); return; }
        if (mode.equals("tree")) {
            Process child = new ProcessBuilder(java(), "-cp", System.getProperty("java.class.path"), ProfessionalAgentFixtureCli.class.getName(), "child").start();
            System.out.println(child.pid()); System.out.flush();
            try { Thread.sleep(120_000); } finally { child.destroyForcibly(); }
            return;
        }
        if (mode.equals("malformed")) { System.out.println("not a JSON frame"); Thread.sleep(300); return; }
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            Files.writeString(Path.of("fixture-frames.jsonl"), line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            JsonObject frame = JsonParser.parseString(line).getAsJsonObject();
            if (mode.equals("codex")) codex(frame);
            else if (mode.startsWith("claude")) claude(frame, mode);
            else if (mode.equals("grok")) grok(frame);
            else if (mode.startsWith("deepseek")) deepseek(frame, mode);
            else if (mode.startsWith("antigravity")) {
                if (mode.equals("antigravity-persistent")) {
                    Process child = new ProcessBuilder(java(), "-cp", System.getProperty("java.class.path"), ProfessionalAgentFixtureCli.class.getName(), "child").start();
                    Files.writeString(Path.of("fixture-pids.json"), AgentJson.object("parent", ProcessHandle.current().pid(), "child", child.pid()).toString());
                }
                output(AgentJson.object("event", "init", "conversation_id", "agy-fixture"));
                if (mode.equals("antigravity-no-result")) return;
                output(AgentJson.object("event", "result", "result", AgentJson.object("status", "SUCCESS", "response", "Completed fixture turn", "conversation_id", "agy-fixture")));
                if (mode.equals("antigravity-failed-exit")) System.exit(7);
                if (mode.equals("antigravity")) {
                    System.err.println("Permission denied for run_command; add a scoped rule."); System.err.flush();
                }
            }
        }
        if (mode.equals("antigravity-persistent")) {
            Files.writeString(Path.of("fixture-eof.marker"), "stdin EOF observed after SUCCESS");
            Thread.sleep(120_000);
        }
        if (mode.startsWith("deepseek")) Files.writeString(Path.of("fixture-eof.marker"), "stdin EOF observed after ACP disposal");
    }

    static String java() { return Path.of(System.getProperty("java.home"), "bin", AgentProcess.windows() ? "java.exe" : "java").toString(); }
    private static void output(JsonObject frame) { System.out.println(frame); System.out.flush(); }
    private static void response(JsonObject request, JsonObject result) { output(AgentJson.object("id", request.get("id"), "result", result)); }

    private static void codex(JsonObject frame) {
        switch (AgentJson.string(frame, "method")) {
            case "initialize" -> response(frame, new JsonObject());
            case "model/list" -> response(frame, modelCatalog());
            case "configRequirements/read" -> response(frame, AgentJson.object("requirements", JsonNull.INSTANCE));
            case "thread/start", "thread/resume" -> response(frame, AgentJson.object("thread", AgentJson.object("id", "codex-fixture")));
            case "turn/start" -> {
                boolean early = Files.exists(Path.of("fixture-early-approval"));
                JsonObject approval = AgentJson.object("id", 711, "method", "item/commandExecution/requestApproval", "params", AgentJson.object("command", "gradle build", "reason", "Compile requested mod"));
                if (early) output(approval);
                response(frame, AgentJson.object("turn", AgentJson.object("id", "turn-fixture")));
                if (!early) output(approval);
            }
            case "turn/interrupt" -> {
                response(frame, new JsonObject());
                output(AgentJson.object("method", "turn/completed", "params", AgentJson.object("turn", AgentJson.object("status", "interrupted"))));
            }
            default -> {
                if (frame.has("id") && frame.get("id").isJsonPrimitive() && frame.get("id").getAsString().equals("711")) {
                    output(AgentJson.object("method", "item/agentMessage/delta", "params", AgentJson.object("itemId", "answer", "delta", "Created ")));
                    output(AgentJson.object("method", "item/agentMessage/delta", "params", AgentJson.object("itemId", "answer", "delta", "a block")));
                    output(AgentJson.object("method", "turn/completed", "params", AgentJson.object("turn", AgentJson.object("status", "completed"))));
                }
            }
        }
    }

    static JsonObject modelCatalog() {
        return AgentJson.object("data", AgentJson.array(
                AgentJson.object("id", "fixture-model", "model", "fixture-model", "displayName", "Fixture model", "defaultReasoningEffort", "low",
                        "supportedReasoningEfforts", AgentJson.array(AgentJson.object("reasoningEffort", "low"), AgentJson.object("reasoningEffort", "high"))),
                AgentJson.object("id", "other-model", "model", "other-model", "displayName", "Other model", "defaultReasoningEffort", "medium",
                        "supportedReasoningEfforts", AgentJson.array(AgentJson.object("reasoningEffort", "medium")))), "nextCursor", JsonNull.INSTANCE);
    }

    private static void claude(JsonObject frame, String mode) {
        String type = AgentJson.string(frame, "type");
        if (type.equals("control_request")) {
            String id = AgentJson.string(frame, "request_id");
            // An unrelated reply must never satisfy the in-flight initialize request.
            output(AgentJson.object("type", "control_response", "response", AgentJson.object("subtype", "success", "request_id", "wrong-id", "response", new JsonObject())));
            output(AgentJson.object("type", "control_response", "response", AgentJson.object("subtype", "success", "request_id", id, "response", new JsonObject())));
        } else if (type.equals("user")) {
            output(AgentJson.object("type", "system", "subtype", "init", "session_id", "claude-fixture"));
            JsonObject input = mode.equals("claude-questions") ? AgentJson.object("questions", AgentJson.array(
                    AgentJson.object("question", "Which effects?", "header", "Effects", "multiSelect", true, "options", AgentJson.array(
                            AgentJson.object("label", "Glow", "description", "Emit light"), AgentJson.object("label", "Flight", "description", "Allow flight"))),
                    AgentJson.object("question", "Which name?", "header", "Name", "multiSelect", false, "options", AgentJson.array(AgentJson.object("label", "Lamp")))))
                    : AgentJson.object("command", "gradle build");
            output(AgentJson.object("type", "control_request", "request_id", "permission-fixture", "request", AgentJson.object("subtype", "can_use_tool", "tool_name", mode.equals("claude-questions") ? "AskUserQuestion" : "Bash", "input", input)));
        } else if (type.equals("control_response")) {
            JsonObject response = AgentJson.child(frame, "response");
            if (!AgentJson.string(response, "request_id").equals("permission-fixture")) throw new IllegalStateException("Mismatched permission response");
            if (mode.equals("claude-questions")) {
                JsonObject answers = AgentJson.child(AgentJson.child(AgentJson.child(response, "response"), "updatedInput"), "answers");
                if (!AgentJson.array(new JsonPrimitive("Glow"), new JsonPrimitive("Flight"), new JsonPrimitive("Frost")).equals(answers.get("Which effects?"))
                        || !AgentJson.string(answers, "Which name?").equals("choice-0"))
                    throw new IllegalStateException("Question answers were lost or custom text was interpreted as an option ID");
            }
            output(AgentJson.object("type", "assistant", "message", AgentJson.object("id", "message-fixture", "content", AgentJson.array(AgentJson.object("type", "text", "text", "Built the fixture")))));
            output(AgentJson.object("type", "result", "subtype", "success", "session_id", "claude-fixture", "is_error", false, "result", "Built the fixture"));
        }
    }

    private static void grok(JsonObject frame) {
        switch (AgentJson.string(frame, "method")) {
            case "initialize" -> response(frame, AgentJson.object("protocolVersion", 1));
            case "session/new", "session/load" -> response(frame, AgentJson.object("sessionId", "grok-fixture"));
            case "session/prompt" -> {
                output(AgentJson.object("method", "session/update", "params", AgentJson.object("update", AgentJson.object("sessionUpdate", "agent_message_chunk", "content", AgentJson.object("type", "text", "text", "Grok fixture completed")))));
                response(frame, AgentJson.object("stopReason", "end_turn"));
            }
            default -> { }
        }
    }

    private static void deepseek(JsonObject frame, String mode) throws IOException {
        switch (AgentJson.string(frame, "method")) {
            case "initialize" -> response(frame, AgentJson.object("protocolVersion", 1, "agentCapabilities", AgentJson.object("sessionCapabilities",
                    AgentJson.object("resume", new JsonObject(), "close", new JsonObject()))));
            case "session/new" -> {
                System.out.println("[llm-deepseek-account] model listing from https://example.invalid/models loaded 2 model(s), skipped 0 row(s)");
                System.out.flush();
                response(frame, AgentJson.object("sessionId", "deepseek-fixture", "configOptions", new JsonArray()));
            }
            case "session/resume" -> {
                if (!AgentJson.string(AgentJson.child(frame, "params"), "sessionId").equals("deepseek-fixture"))
                    throw new IllegalStateException("Resume must use the saved DeepSeek session");
                response(frame, AgentJson.object("configOptions", new JsonArray()));
            }
            case "session/load" -> throw new IllegalStateException("DeepSeek uses session/resume, not session/load");
            case "session/prompt" -> {
                deepseekPromptId = frame.get("id");
                output(AgentJson.object("method", "session/update", "params", AgentJson.object("sessionId", "deepseek-fixture", "update",
                        AgentJson.object("sessionUpdate", "tool_call", "toolCallId", "dsh-build", "title", "pwsh",
                                "status", "in_progress", "rawInput", AgentJson.object("command", "gradle build")))));
                output(AgentJson.object("id", "dsh-permission", "method", "session/request_permission", "params",
                        AgentJson.object("sessionId", "deepseek-fixture", "toolCall", AgentJson.object("toolCallId", "dsh-build"), "options", AgentJson.array(
                                AgentJson.object("optionId", "allow-once", "name", "Allow once", "kind", "allow_once"),
                                AgentJson.object("optionId", "reject-once", "name", "Reject", "kind", "reject_once")))));
            }
            case "session/close" -> {
                Files.writeString(Path.of("fixture-close.marker"), "session/close observed before stdin EOF");
                if (mode.equals("deepseek-close-eof")) System.exit(0);
                if (mode.equals("deepseek-close-error")) output(AgentJson.object("id", frame.get("id"), "error", AgentJson.object("code", -32603, "message", "session persistence failed")));
                else response(frame, new JsonObject());
            }
            case "session/cancel" -> output(AgentJson.object("id", deepseekPromptId, "result", AgentJson.object("stopReason", "cancelled")));
            default -> {
                if (frame.has("id") && frame.get("id").getAsString().equals("dsh-permission")) {
                    String selected = AgentJson.string(AgentJson.child(AgentJson.child(frame, "result"), "outcome"), "optionId");
                    if (!selected.equals("allow-once")) throw new IllegalStateException("Expected the exact allowed option ID");
                    if (mode.equals("deepseek-recovered")) {
                        output(AgentJson.object("method", "session/update", "params", AgentJson.object("sessionId", "deepseek-fixture", "update", AgentJson.object("sessionUpdate", "tool_call_update", "toolCallId", "dsh-build", "status", "failed", "rawOutput", "First build failed"))));
                        output(AgentJson.object("method", "session/update", "params", AgentJson.object("sessionId", "deepseek-fixture", "update", AgentJson.object("sessionUpdate", "tool_call", "toolCallId", "dsh-retry", "title", "Build after repair", "status", "in_progress"))));
                        output(AgentJson.object("method", "session/update", "params", AgentJson.object("sessionId", "deepseek-fixture", "update", AgentJson.object("sessionUpdate", "tool_call_update", "toolCallId", "dsh-retry", "status", "completed", "rawOutput", "BUILD SUCCESSFUL"))));
                    }
                    output(AgentJson.object("method", "session/update", "params", AgentJson.object("sessionId", "deepseek-fixture", "update",
                            AgentJson.object("sessionUpdate", "agent_message_chunk", "content", AgentJson.object("type", "text", "text", "DeepSeek fixture completed")))));
                    output(AgentJson.object("id", deepseekPromptId, "result", AgentJson.object("stopReason", "end_turn")));
                }
            }
        }
    }
}
