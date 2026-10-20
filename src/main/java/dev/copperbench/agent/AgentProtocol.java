package dev.copperbench.agent;

import com.google.gson.*;

import java.util.List;
import java.util.function.Consumer;

/** Provider wire protocols are independent of process ownership and persistence. */
interface AgentProtocol {
    interface Channel {
        JsonObject rpc(String method, JsonObject params) throws Exception;
        JsonObject control(JsonObject request) throws Exception;
        void send(JsonObject frame);
        void text(String key, String role, String text, boolean append);
        void remoteId(String id);
        void approval(String id, String title, String detail, JsonArray choices, Consumer<String> answer);
        void question(String id, String title, String detail, JsonArray choices, boolean multiSelect, boolean allowCustom, Consumer<List<String>> answer);
        void removeApproval(String id);
        void diagnostic(String text);
        void complete(boolean success, boolean interrupted, String error);
        default void ready() { }
    }
    List<String> arguments();
    void start(String text, String instructions) throws Exception;
    void receive(JsonObject frame);
    void stop();
    /** Runs off the output reader, before stdin EOF, so session disposal can acknowledge persistence. */
    default void finish() throws Exception { }
    /** Recognizes only documented/observed provider diagnostics that appear outside the wire frames. */
    default boolean stdoutDiagnostic(String line) { return false; }
    default void stderr(String line) { }
    default String completionError() { return null; }
    /** A terminal turn receipt can precede host-controlled teardown of a persistent streaming process. */
    default boolean persistentAfterTurn() { return false; }

    static AgentProtocol create(String provider, Channel channel, java.nio.file.Path workspace, String remoteId) {
        return switch (provider) {
            case "codex" -> new CodexAgentProtocol(channel, workspace, remoteId);
            case "claude" -> new ClaudeAgentProtocol(channel, remoteId);
            case "grok" -> new AcpAgentProtocol(AcpAgentProtocol.Backend.GROK, channel, workspace, remoteId);
            case "antigravity" -> new AntigravityAgentProtocol(channel, remoteId);
            case "deepseek" -> new AcpAgentProtocol(AcpAgentProtocol.Backend.DEEPSEEK, channel, workspace, remoteId);
            default -> throw new IllegalArgumentException("未知的创作工具");
        };
    }
}
