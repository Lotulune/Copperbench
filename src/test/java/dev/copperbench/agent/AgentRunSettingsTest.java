package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AgentRunSettingsTest {
    @TempDir Path root;
    @Test void selectionsReachBothTheResumedThreadAndTheNextTurn() throws Exception {
        var wire = new AgentProtocolTest.Wire();
        var settings = new AgentRunSettings("fixture-model", "high", "full-access");
        new CodexAgentProtocol(wire, root, "existing-thread", settings).start("analyze", "instructions");
        JsonObject thread = wire.sent.stream().filter(frame -> AgentJson.string(frame, "method").equals("thread/resume")).findFirst().orElseThrow().getAsJsonObject("params");
        assertEquals("fixture-model", AgentJson.string(thread, "model")); assertEquals("existing-thread", AgentJson.string(thread, "threadId"));
        assertEquals("danger-full-access", AgentJson.string(thread, "sandbox")); assertEquals("on-request", AgentJson.string(thread, "approvalPolicy"));
        JsonObject turn = wire.sent.stream().filter(frame -> AgentJson.string(frame, "method").equals("turn/start")).findFirst().orElseThrow().getAsJsonObject("params");
        assertEquals("high", AgentJson.string(turn, "effort")); assertEquals("fixture-model", AgentJson.string(turn, "model"));
    }
    @Test void changingTheModelWithDefaultEffortUsesThatModelsAdvertisedDefault() throws Exception {
        var wire = new AgentProtocolTest.Wire();
        new CodexAgentProtocol(wire, root, "existing-thread", new AgentRunSettings("other-model", "", "workspace")).start("analyze", "instructions");
        JsonObject turn = wire.sent.stream().filter(frame -> AgentJson.string(frame, "method").equals("turn/start")).findFirst().orElseThrow().getAsJsonObject("params");
        assertEquals("medium", AgentJson.string(turn, "effort"));
    }
    @Test void unsupportedEffortIsRejectedBeforeCreatingOrResumingAThread() {
        var wire = new AgentProtocolTest.Wire();
        assertThrows(IllegalArgumentException.class, () -> new CodexAgentProtocol(wire, root, null,
                new AgentRunSettings("other-model", "high", "workspace")).start("analyze", "instructions"));
        assertFalse(wire.methods.contains("thread/start")); assertFalse(wire.methods.contains("turn/start"));
    }
    @Test void readOnlyDeclinesEscalationWithoutPresentingAnApprovalButton() throws Exception {
        var wire = new AgentProtocolTest.Wire(); var protocol = new CodexAgentProtocol(wire, root, null, new AgentRunSettings("", "", "read-only"));
        protocol.start("analyze", "instructions");
        protocol.receive(AgentJson.object("id", 42, "method", "item/commandExecution/requestApproval", "params", new JsonObject()));
        assertTrue(wire.approvals.isEmpty()); assertEquals("decline", AgentJson.string(AgentJson.child(wire.sent.getLast(), "result"), "decision"));
        JsonObject thread = wire.sent.stream().filter(frame -> AgentJson.string(frame, "method").equals("thread/start")).findFirst().orElseThrow().getAsJsonObject("params");
        assertEquals("read-only", AgentJson.string(thread, "sandbox")); assertEquals("never", AgentJson.string(thread, "approvalPolicy"));
    }
    @Test void catalogRespectsManagedPolicyAndDoesNotPretendToStartATask() throws Exception {
        JsonObject catalog = CodexModelCatalog.read((method, params) -> method.equals("model/list") ? FakeAgentCli.modelCatalog()
                : AgentJson.object("requirements", AgentJson.object("allowedSandboxModes", AgentJson.array(new JsonPrimitive("read-only")), "allowedApprovalPolicies", AgentJson.array(new JsonPrimitive("never")))));
        assertEquals(AgentJson.array(new JsonPrimitive("read-only")), catalog.getAsJsonArray("permissionProfiles"));
        assertThrows(IllegalArgumentException.class, () -> AgentRunSettings.DEFAULT.validate(catalog));
        assertEquals(2, catalog.getAsJsonArray("models").size());
    }
    @Test void settingsSurviveHistoryReloadAndMalformedOrForeignProviderOverridesAreRejected() throws Exception {
        AgentChatStore store = new AgentChatStore(root); var session = store.create("codex");
        session.settings = new AgentRunSettings("fixture-model", "high", "read-only"); store.save();
        AgentChatStore reopened = new AgentChatStore(root); reopened.load(); assertEquals(session.settings, reopened.sessions.get(session.id).settings);
        assertThrows(IllegalArgumentException.class, () -> AgentRunSettings.parse(AgentJson.object("effort", "high")));
        assertThrows(IllegalArgumentException.class, () -> AgentRunSettings.parse(AgentJson.object("config", "anything")));
        assertThrows(IllegalArgumentException.class, () -> AgentProtocol.create("claude", new AgentProtocolTest.Wire(), root, null, session.settings));
    }
}
