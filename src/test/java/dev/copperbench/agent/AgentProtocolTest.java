package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AgentProtocolTest {
    @TempDir Path workspace;
    record Pending(String id, String title, String detail, JsonArray choices, Consumer<String> answer) { }
    record PendingQuestion(String id, JsonArray choices, boolean multiSelect, boolean allowCustom, Consumer<List<String>> answer) { }
    static final class Wire implements AgentProtocol.Channel {
        final List<JsonObject> sent = new ArrayList<>();
        final List<String> methods = new ArrayList<>();
        final List<Pending> approvals = new ArrayList<>();
        final List<PendingQuestion> questions = new ArrayList<>();
        final Map<String, String> messages = new HashMap<>();
        final List<String> removed = new ArrayList<>();
        JsonObject initialize = AgentJson.object("protocolVersion", 1, "agentCapabilities", AgentJson.object("sessionCapabilities",
                AgentJson.object("resume", new JsonObject(), "close", new JsonObject())));
        String remote; Boolean success; boolean interrupted; String error;
        @Override public JsonObject rpc(String method, JsonObject params) {
            methods.add(method); sent.add(AgentJson.object("method", method, "params", params));
            return switch (method) {
                case "initialize" -> initialize;
                case "model/list" -> FakeAgentCli.modelCatalog();
                case "configRequirements/read" -> AgentJson.object("requirements", JsonNull.INSTANCE);
                case "thread/start", "thread/resume" -> AgentJson.object("thread", AgentJson.object("id", "thread-123"));
                case "turn/start" -> AgentJson.object("turn", AgentJson.object("id", "turn-123"));
                case "session/new" -> AgentJson.object("sessionId", "acp-session-123");
                default -> new JsonObject();
            };
        }
        @Override public JsonObject control(JsonObject request) { sent.add(AgentJson.object("request", request)); return new JsonObject(); }
        @Override public void send(JsonObject frame) { sent.add(frame.deepCopy()); }
        @Override public void text(String key, String role, String text, boolean append) { messages.put(key, append ? messages.getOrDefault(key, "") + text : text); }
        @Override public void remoteId(String id) { remote = id; }
        @Override public void approval(String id, String title, String detail, JsonArray choices, Consumer<String> answer) { approvals.add(new Pending(id, title, detail, choices, answer)); }
        @Override public void question(String id, String title, String detail, JsonArray choices, boolean multiSelect, boolean allowCustom, Consumer<List<String>> answer) {
            questions.add(new PendingQuestion(id, choices, multiSelect, allowCustom, answer));
        }
        @Override public void removeApproval(String id) { removed.add(id); }
        @Override public void diagnostic(String text) { messages.put("notice", text); }
        @Override public void complete(boolean success, boolean interrupted, String error) { this.success = success; this.interrupted = interrupted; this.error = error; }
    }

    @Test void codexResumesExactThreadAndRoutesApprovalByServerRequestId() throws Exception {
        Wire wire = new Wire(); CodexAgentProtocol protocol = new CodexAgentProtocol(wire, workspace, "thread-old");
        protocol.start("create a block", "use the actual SDK");
        assertTrue(wire.methods.contains("thread/resume"));
        JsonObject params = AgentJson.child(wire.sent.get(2), "params");
        assertEquals("thread-old", AgentJson.string(params, "threadId"));
        assertEquals("workspace-write", AgentJson.string(params, "sandbox"));
        protocol.receive(AgentJson.object("id", 57, "method", "item/commandExecution/requestApproval", "params", AgentJson.object("command", "gradle build")));
        wire.approvals.getFirst().answer().accept("decline");
        JsonObject answer = wire.sent.getLast();
        assertEquals(57, answer.get("id").getAsInt());
        assertEquals("decline", AgentJson.string(AgentJson.child(answer, "result"), "decision"));
        protocol.stop();
        assertEquals("turn/interrupt", AgentJson.string(wire.sent.getLast(), "method"));
        protocol.receive(AgentJson.object("method", "turn/completed", "params", AgentJson.object("turn", AgentJson.object("status", "failed", "error", AgentJson.object("message", "build failed")))));
        assertFalse(wire.success); assertTrue(wire.error.contains("build failed"));
    }

    @Test void claudeApprovalIsARealMatchedControlReplyAndPreservesOriginalInput() {
        Wire wire = new Wire(); ClaudeAgentProtocol protocol = new ClaudeAgentProtocol(wire, null);
        JsonObject input = AgentJson.object("command", "gradle build", "timeout", 120000);
        protocol.receive(AgentJson.object("type", "control_request", "request_id", "req-specific", "request",
                AgentJson.object("subtype", "can_use_tool", "tool_name", "Bash", "input", input)));
        wire.approvals.getFirst().answer().accept("allow");
        JsonObject answer = AgentJson.child(wire.sent.getLast(), "response");
        assertEquals("control_response", AgentJson.string(wire.sent.getLast(), "type"));
        assertEquals("req-specific", AgentJson.string(answer, "request_id"));
        assertEquals(input, AgentJson.child(AgentJson.child(answer, "response"), "updatedInput"));
        assertEquals("allow", AgentJson.string(AgentJson.child(answer, "response"), "behavior"));
        protocol.receive(AgentJson.object("type", "control_request", "request_id", "req-deny", "request",
                AgentJson.object("subtype", "can_use_tool", "tool_name", "Bash", "input", input)));
        wire.approvals.getLast().answer().accept("deny");
        JsonObject denied = AgentJson.child(AgentJson.child(wire.sent.getLast(), "response"), "response");
        assertEquals("deny", AgentJson.string(denied, "behavior")); assertFalse(denied.has("updatedInput"));
        protocol.receive(AgentJson.object("type", "control_cancel_request", "request_id", "req-specific"));
        assertEquals(List.of("claude:req-specific"), wire.removed);
    }

    @Test void codexAdditionalPermissionsAreGrantedOnlyForTheDisplayedRequestAndCurrentTurn() {
        Wire wire = new Wire(); CodexAgentProtocol protocol = new CodexAgentProtocol(wire, workspace, null);
        JsonObject permissions = AgentJson.object("network", AgentJson.object("enabled", true));
        protocol.receive(AgentJson.object("id", 88, "method", "item/permissions/requestApproval", "params", AgentJson.object("permissions", permissions)));
        wire.approvals.getFirst().answer().accept("allow");
        JsonObject result = AgentJson.child(wire.sent.getLast(), "result");
        assertEquals(permissions, result.get("permissions")); assertEquals("turn", AgentJson.string(result, "scope"));
        protocol.receive(AgentJson.object("id", 89, "method", "item/permissions/requestApproval", "params", AgentJson.object("permissions", permissions)));
        wire.approvals.getLast().answer().accept("deny");
        assertTrue(AgentJson.child(AgentJson.child(wire.sent.getLast(), "result"), "permissions").isEmpty());
    }

    @Test void claudeAnswersAllQuestionsOnceAndRejectsUnknownControlMethods() {
        Wire wire = new Wire(); ClaudeAgentProtocol protocol = new ClaudeAgentProtocol(wire, null);
        JsonObject input = AgentJson.object("questions", AgentJson.array(
                AgentJson.object("question", "Which color?", "header", "Color", "options", AgentJson.array(AgentJson.object("label", "Blue"))),
                AgentJson.object("question", "What name?", "header", "Name", "options", new JsonArray())));
        protocol.receive(AgentJson.object("type", "control_request", "request_id", "question-1", "request", AgentJson.object("subtype", "can_use_tool", "tool_name", "AskUserQuestion", "input", input)));
        wire.questions.getFirst().answer().accept(List.of("Blue")); assertTrue(wire.sent.isEmpty());
        wire.questions.getLast().answer().accept(List.of("Moon stone"));
        JsonObject updated = AgentJson.child(AgentJson.child(AgentJson.child(wire.sent.getLast(), "response"), "response"), "updatedInput");
        assertEquals("Moon stone", AgentJson.string(AgentJson.child(updated, "answers"), "What name?"));
        assertEquals(input.get("questions"), updated.get("questions"));
        protocol.receive(AgentJson.object("type", "control_request", "request_id", "unknown", "request", AgentJson.object("subtype", "execute_arbitrary_command", "command", "malicious")));
        assertEquals("error", AgentJson.string(AgentJson.child(wire.sent.getLast(), "response"), "subtype"));
    }

    @Test void claudeMultiSelectPreservesAllChoicesAndCustomTextInOneControlReply() {
        Wire wire = new Wire(); ClaudeAgentProtocol protocol = new ClaudeAgentProtocol(wire, null);
        JsonObject question = AgentJson.object("question", "Which effects?", "header", "Effects", "multiSelect", true, "options", AgentJson.array(
                AgentJson.object("label", "Glow", "description", "Emit light"), AgentJson.object("label", "Flight", "description", "Allow flight")));
        protocol.receive(AgentJson.object("type", "control_request", "request_id", "effects-1", "request", AgentJson.object("subtype", "can_use_tool", "tool_name", "AskUserQuestion", "input", AgentJson.object("questions", AgentJson.array(question)))));
        PendingQuestion pending = wire.questions.getFirst();
        assertTrue(pending.multiSelect()); assertTrue(pending.allowCustom());
        assertEquals("Emit light", AgentJson.string(pending.choices().get(0).getAsJsonObject(), "description"));
        assertTrue(wire.sent.isEmpty());
        pending.answer().accept(List.of("Glow", "Flight", "Frost"));
        assertEquals(1, wire.sent.size());
        JsonObject response = AgentJson.child(wire.sent.getFirst(), "response");
        assertEquals("effects-1", AgentJson.string(response, "request_id"));
        JsonObject updated = AgentJson.child(AgentJson.child(response, "response"), "updatedInput");
        assertEquals(AgentJson.array(new JsonPrimitive("Glow"), new JsonPrimitive("Flight"), new JsonPrimitive("Frost")), AgentJson.child(updated, "answers").get("Which effects?"));
        assertEquals(question, updated.getAsJsonArray("questions").get(0));
    }

    @Test void codexOtherInputReachesTheRequestedQuestionWithoutChangingPermissionDecisions() {
        Wire wire = new Wire(); CodexAgentProtocol protocol = new CodexAgentProtocol(wire, workspace, null);
        protocol.receive(AgentJson.object("id", 64, "method", "item/tool/requestUserInput", "params", AgentJson.object("questions", AgentJson.array(
                AgentJson.object("id", "name", "header", "Name", "question", "Choose a name", "isOther", true, "options", AgentJson.array(AgentJson.object("label", "Lamp", "description", "Standard lamp")))))));
        PendingQuestion pending = wire.questions.getFirst();
        assertFalse(pending.multiSelect()); assertTrue(pending.allowCustom());
        pending.answer().accept(List.of("Moon lantern"));
        assertEquals(64, wire.sent.getLast().get("id").getAsInt());
        assertEquals("Moon lantern", AgentJson.child(AgentJson.child(AgentJson.child(wire.sent.getLast(), "result"), "answers"), "name").getAsJsonArray("answers").get(0).getAsString());
        assertTrue(wire.approvals.isEmpty());
    }

    @Test void codexSecretQuestionRejectsTheWholeRequestBeforePublishingPartialQuestions() {
        Wire wire = new Wire(); CodexAgentProtocol protocol = new CodexAgentProtocol(wire, workspace, null);
        protocol.receive(AgentJson.object("id", 65, "method", "item/tool/requestUserInput", "params", AgentJson.object("questions", AgentJson.array(
                AgentJson.object("id", "name", "header", "Name", "question", "Name?"),
                AgentJson.object("id", "secret", "header", "Secret", "question", "Credential?", "isSecret", true)))));
        assertTrue(wire.questions.isEmpty()); assertTrue(wire.approvals.isEmpty());
        assertEquals(-32601, AgentJson.child(wire.sent.getFirst(), "error").get("code").getAsInt());
    }

    @Test void grokUsesAcpLoadPermissionAndCancelWithoutBypass() throws Exception {
        Wire wire = new Wire(); AcpAgentProtocol protocol = new AcpAgentProtocol(AcpAgentProtocol.Backend.GROK, wire, workspace, "existing-grok-session");
        protocol.start("continue", "context"); assertTrue(wire.methods.contains("session/load"));
        assertFalse(protocol.arguments().contains("--always-approve"));
        protocol.receive(AgentJson.object("jsonrpc", "2.0", "id", "permission-9", "method", "session/request_permission", "params",
                AgentJson.object("toolCall", AgentJson.object("title", "Run build"), "options", AgentJson.array(
                        AgentJson.object("optionId", "grant-once", "name", "Allow once", "kind", "allow_once")))));
        wire.approvals.getFirst().answer().accept("grant-once");
        JsonObject outcome = AgentJson.child(AgentJson.child(wire.sent.getLast(), "result"), "outcome");
        assertEquals("selected", AgentJson.string(outcome, "outcome")); assertEquals("grant-once", AgentJson.string(outcome, "optionId"));
        protocol.stop(); assertEquals("session/cancel", AgentJson.string(wire.sent.getLast(), "method"));
        protocol.receive(AgentJson.object("id", "copperbench-prompt", "result", AgentJson.object("stopReason", "cancelled")));
        assertFalse(wire.success); assertTrue(wire.interrupted);
    }

    @Test void deepseekUsesOfficialProfileAndResumesTheExactSessionBeforeClosingIt() throws Exception {
        Wire wire = new Wire(); AcpAgentProtocol protocol = new AcpAgentProtocol(AcpAgentProtocol.Backend.DEEPSEEK, wire, workspace, "saved-dsh-session");
        assertEquals(List.of("--profile", "acp"), protocol.arguments());
        protocol.start("continue the lantern", "Use the workspace SDK");
        assertEquals(List.of("initialize", "session/resume"), wire.methods);
        assertTrue(AgentJson.child(AgentJson.child(wire.sent.getFirst(), "params"), "clientCapabilities").isEmpty());
        JsonObject params = AgentJson.child(wire.sent.get(1), "params");
        assertEquals("saved-dsh-session", AgentJson.string(params, "sessionId"));
        assertEquals(workspace.toString(), AgentJson.string(params, "cwd"));
        assertEquals("saved-dsh-session", wire.remote, "A resume reply has no sessionId; retain the original");
        protocol.receive(AgentJson.object("id", "copperbench-prompt", "result", AgentJson.object("stopReason", "end_turn")));
        assertTrue(wire.success);
        protocol.finish();
        assertEquals("session/close", wire.methods.getLast());
        assertEquals("saved-dsh-session", AgentJson.string(AgentJson.child(wire.sent.getLast(), "params"), "sessionId"));
    }

    @Test void deepseekIdOnlyPermissionRestoresToolDetailsAndDenialRemainsFailure() throws Exception {
        Wire wire = new Wire(); AcpAgentProtocol protocol = new AcpAgentProtocol(AcpAgentProtocol.Backend.DEEPSEEK, wire, workspace, null);
        protocol.start("build", "context");
        protocol.receive(acpUpdate(AgentJson.object("sessionUpdate", "tool_call", "toolCallId", "build-7", "title", "pwsh",
                "rawInput", AgentJson.object("command", ".\\gradlew.bat build"), "status", "in_progress")));
        protocol.receive(AgentJson.object("jsonrpc", "2.0", "id", 21, "method", "session/request_permission", "params",
                AgentJson.object("sessionId", wire.remote, "toolCall", AgentJson.object("toolCallId", "build-7"), "options", AgentJson.array(
                        AgentJson.object("optionId", "reject-once", "name", "Reject", "kind", "reject_once")))));
        Pending pending = wire.approvals.getFirst();
        assertEquals("pwsh", pending.title()); assertTrue(pending.detail().contains("gradlew.bat build"));
        pending.answer().accept("reject-once");
        assertEquals(21, wire.sent.getLast().get("id").getAsInt());
        assertEquals("reject-once", AgentJson.string(AgentJson.child(AgentJson.child(wire.sent.getLast(), "result"), "outcome"), "optionId"));
        protocol.receive(AgentJson.object("id", "copperbench-prompt", "result", AgentJson.object("stopReason", "end_turn")));
        assertFalse(wire.success); assertTrue(wire.error.contains("未获授权"));
    }

    @Test void deepseekMissingPermissionDetailsAndUnsupportedClientExecutionAreNeverApproved() throws Exception {
        Wire wire = new Wire(); AcpAgentProtocol protocol = new AcpAgentProtocol(AcpAgentProtocol.Backend.DEEPSEEK, wire, workspace, null);
        protocol.start("build", "context");
        protocol.receive(AgentJson.object("id", "unknown-tool", "method", "session/request_permission", "params",
                AgentJson.object("toolCall", AgentJson.object("toolCallId", "missing"), "options", AgentJson.array(
                        AgentJson.object("optionId", "allow-once", "name", "Allow once", "kind", "allow_once")))));
        assertTrue(wire.approvals.isEmpty());
        assertEquals("cancelled", AgentJson.string(AgentJson.child(AgentJson.child(wire.sent.getLast(), "result"), "outcome"), "outcome"));
        protocol.receive(AgentJson.object("id", 29, "method", "terminal/create", "params", AgentJson.object("command", "never-run")));
        assertEquals(-32601, AgentJson.child(wire.sent.getLast(), "error").get("code").getAsInt());
        protocol.receive(AgentJson.object("id", "copperbench-prompt", "result", AgentJson.object("stopReason", "end_turn")));
        assertFalse(wire.success); assertTrue(wire.error.contains("terminal/create"));
    }

    @Test void deepseekRecoveredToolErrorsRemainVisibleWithoutOverridingTheTerminalOutcome() throws Exception {
        Wire wire = new Wire(); AcpAgentProtocol protocol = new AcpAgentProtocol(AcpAgentProtocol.Backend.DEEPSEEK, wire, workspace, null);
        protocol.start("build", "context");
        protocol.receive(acpUpdate(AgentJson.object("sessionUpdate", "tool_call", "toolCallId", "compile", "title", "Compile",
                "rawInput", AgentJson.object("command", "gradle build"), "status", "in_progress")));
        protocol.receive(acpUpdate(AgentJson.object("sessionUpdate", "tool_call_update", "toolCallId", "compile", "status", "failed",
                "content", AgentJson.array(AgentJson.object("type", "content", "content", AgentJson.object("type", "text", "text", "Compilation failed"))))));
        assertTrue(wire.messages.get("tool:compile").contains("gradle build"));
        assertTrue(wire.messages.get("tool:compile").contains("Compilation failed"));
        protocol.receive(acpUpdate(AgentJson.object("sessionUpdate", "tool_call", "toolCallId", "compile-retry", "title", "Compile after repair", "status", "in_progress")));
        protocol.receive(acpUpdate(AgentJson.object("sessionUpdate", "tool_call_update", "toolCallId", "compile-retry", "status", "completed", "rawOutput", "BUILD SUCCESSFUL")));
        protocol.receive(AgentJson.object("id", "copperbench-prompt", "result", AgentJson.object("stopReason", "end_turn")));
        assertTrue(wire.success); assertNull(wire.error);
        assertTrue(wire.messages.get("tool:compile").contains("Compilation failed"));
        assertTrue(wire.messages.get("tool:compile-retry").contains("BUILD SUCCESSFUL"));
        protocol.receive(AgentJson.object("id", "copperbench-prompt", "error", AgentJson.object("code", -32603, "message", "turn failed: no configured model")));
        assertFalse(wire.success); assertTrue(wire.error.contains("no configured model"));
    }

    @Test void deepseekCannotSilentlyStartANewSessionWhenResumeIsUnavailable() {
        Wire wire = new Wire(); wire.initialize = AgentJson.object("protocolVersion", 1);
        AcpAgentProtocol protocol = new AcpAgentProtocol(AcpAgentProtocol.Backend.DEEPSEEK, wire, workspace, "saved-session");
        assertThrows(java.io.IOException.class, () -> protocol.start("continue", "context"));
        assertEquals(List.of("initialize"), wire.methods);
    }

    @Test void onlyTheObservedDeepseekCatalogLogIsAcceptedOutsideJsonFrames() {
        String listing = "[llm-deepseek-account] model listing from https://api.deepseek.com/models loaded 2 model(s), skipped 0 row(s)";
        Wire wire = new Wire();
        AcpAgentProtocol deepseek = new AcpAgentProtocol(AcpAgentProtocol.Backend.DEEPSEEK, wire, workspace, null);
        assertTrue(deepseek.stdoutDiagnostic(listing));
        assertEquals(listing, wire.messages.get("deepseek-catalog"));
        assertFalse(deepseek.stdoutDiagnostic("[llm-deepseek-account] corrupted protocol output"));
        assertFalse(deepseek.stdoutDiagnostic("{malformed JSON"));
        assertFalse(new AcpAgentProtocol(AcpAgentProtocol.Backend.GROK, wire, workspace, null).stdoutDiagnostic(listing));
    }

    private static JsonObject acpUpdate(JsonObject update) {
        return AgentJson.object("method", "session/update", "params", AgentJson.object("sessionId", "acp-session-123", "update", update));
    }

    @Test void antigravityDenialIsNotSuccessEvenWhenResultAndProcessWouldSucceed() {
        Wire wire = new Wire(); AntigravityAgentProtocol protocol = new AntigravityAgentProtocol(wire, "existing-agy");
        protocol.start("build", "context");
        assertEquals("user", AgentJson.string(wire.sent.getFirst(), "event"));
        assertFalse(wire.sent.getFirst().has("type"));
        protocol.stderr("Permission denied for run_command; add a scoped rule to allow it");
        protocol.receive(AgentJson.object("event", "result", "result", AgentJson.object("status", "SUCCESS", "response", "I could not run it", "conversation_id", "agy-123")));
        assertFalse(wire.success); assertNotNull(protocol.completionError()); assertTrue(wire.approvals.isEmpty());
        int count = wire.sent.size(); protocol.stop(); assertEquals(count, wire.sent.size(), "No unsupported control_request is sent");
    }

    @Test void lateAntigravityStderrDenialCanOverrideAnEarlierSuccessReceipt() {
        Wire wire = new Wire(); AntigravityAgentProtocol protocol = new AntigravityAgentProtocol(wire, null);
        protocol.receive(AgentJson.object("event", "result", "result", AgentJson.object("status", "SUCCESS", "response", "done")));
        assertTrue(wire.success); protocol.stderr("Tool requires approval in headless mode"); assertNotNull(protocol.completionError());
    }

    @Test void redactionPreservesOrdinaryWorkAndHidesCredentialsInErrorsAndDetails() {
        assertEquals("Create a sword with damage 12", AgentJson.redacted("Create a sword with damage 12"));
        String secret = "sk-notarealsecret0123456789";
        String filtered = AgentJson.redacted("Failed: api_key=" + secret + " Authorization: Bearer fake-token-long");
        assertFalse(filtered.contains(secret));
        assertFalse(filtered.contains("fake-token-long"), "The Authorization scheme and credential must be filtered together");
        assertTrue(filtered.contains("Failed:"));
        assertFalse(AgentJson.redacted("{\"Authorization\":\"Basic ZmFrZTpzZWNyZXQ=\"}").contains("ZmFrZTpzZWNyZXQ="));
        assertEquals("Build a shield; token count 42", AgentJson.redacted("Build a shield; token count 42"));
        assertFalse(AgentJson.redacted("https://localhost/?access_token=fake-private-value").contains("fake-private-value"));
    }
}
