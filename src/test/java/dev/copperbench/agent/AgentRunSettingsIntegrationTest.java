package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class AgentRunSettingsIntegrationTest {
    @TempDir Path root;
    static final class Wire implements AgentProtocol.Channel {
        final List<JsonObject> sent = new ArrayList<>(); final List<String> methods = new ArrayList<>();
        int approvalCount;
        @Override public JsonObject rpc(String method, JsonObject params) {
            methods.add(method); sent.add(AgentJson.object("method", method, "params", params));
            return switch (method) {
                case "model/list" -> ProfessionalAgentFixtureCli.modelCatalog();
                case "configRequirements/read" -> AgentJson.object("requirements", JsonNull.INSTANCE);
                case "thread/start", "thread/resume" -> AgentJson.object("thread", AgentJson.object("id", "thread-settings"), "model", params.get("model"));
                case "turn/start" -> AgentJson.object("turn", AgentJson.object("id", "turn-settings"));
                default -> new JsonObject();
            };
        }
        @Override public JsonObject control(JsonObject request) { return new JsonObject(); }
        @Override public void send(JsonObject frame) { sent.add(frame); }
        @Override public void text(String key, String role, String text, boolean append) { }
        @Override public void remoteId(String id) { }
        @Override public void approval(String id, String title, String detail, JsonArray choices, Consumer<String> answer) { approvalCount++; }
        @Override public void question(String id, String title, String detail, JsonArray choices, boolean multi, boolean custom, Consumer<List<String>> answer) { }
        @Override public void removeApproval(String id) { }
        @Override public void diagnostic(String text) { }
        @Override public void complete(boolean success, boolean interrupted, String error) { }
        JsonObject params(String method) { return sent.stream().filter(frame -> method.equals(AgentJson.string(frame, "method"))).findFirst().orElseThrow().getAsJsonObject("params"); }
    }
    private CodexAgentProtocol protocol(Wire wire, String remote, AgentRunSettings settings) {
        CodexAgentProtocol protocol = new CodexAgentProtocol(wire, root, remote);
        protocol.useModel(settings.model().isEmpty() ? null : settings.model()); protocol.configure(settings.options(), List.of()); protocol.runSettings(settings); return protocol;
    }
    @Test void explicitSettingsReachTheResumedThreadAndNextTurn() throws Exception {
        Wire wire = new Wire(); protocol(wire, "existing-thread", new AgentRunSettings("fixture-model", "high", "full-access")).start("analyze", "instructions");
        JsonObject thread = wire.params("thread/resume"), turn = wire.params("turn/start");
        assertEquals("fixture-model", AgentJson.string(thread, "model")); assertEquals("existing-thread", AgentJson.string(thread, "threadId"));
        assertEquals("danger-full-access", AgentJson.string(thread, "sandbox")); assertEquals("on-request", AgentJson.string(thread, "approvalPolicy"));
        assertEquals("high", AgentJson.string(turn, "effort")); assertEquals("fixture-model", AgentJson.string(turn, "model"));
    }
    @Test void legacyAutoApprovalRemainsDifferentFromFullAccessWithApproval() throws Exception {
        Wire wire = new Wire(); CodexAgentProtocol protocol = new CodexAgentProtocol(wire, root, null);
        protocol.configure(new AgentTurnOptions("high", "auto-approve"), List.of()); protocol.start("analyze", "instructions");
        assertEquals("never", AgentJson.string(wire.params("thread/start"), "approvalPolicy"));
        assertEquals("danger-full-access", AgentJson.string(wire.params("thread/start"), "sandbox"));
        assertFalse(wire.methods.contains("configRequirements/read"));
    }
    @Test void switchingModelUsesItsAdvertisedDefaultAndRejectsAnUnsupportedExplicitEffort() throws Exception {
        Wire wire = new Wire(); protocol(wire, null, new AgentRunSettings("other-model", "", "workspace")).start("analyze", "instructions");
        assertEquals("medium", AgentJson.string(wire.params("turn/start"), "effort"));
        Wire rejected = new Wire(); assertThrows(IllegalArgumentException.class,
                () -> protocol(rejected, null, new AgentRunSettings("other-model", "high", "workspace")).start("analyze", "instructions"));
        assertFalse(rejected.methods.contains("thread/start")); assertFalse(rejected.methods.contains("turn/start"));
    }
    @Test void mainManualModelIdsAreNotRemovedByTheNewCatalogPicker() throws Exception {
        Wire wire = new Wire(); protocol(wire, null, new AgentRunSettings("custom/deployment", "", "workspace")).start("analyze", "instructions");
        assertEquals("custom/deployment", AgentJson.string(wire.params("thread/start"), "model"));
        assertFalse(wire.params("turn/start").has("effort"));
    }
    @Test void readOnlyDeclinesEscalationWithoutOfferingAnApproval() throws Exception {
        Wire wire = new Wire(); CodexAgentProtocol protocol = protocol(wire, null, new AgentRunSettings("", "", "read-only"));
        protocol.start("analyze", "instructions"); protocol.receive(AgentJson.object("id", 42, "method", "item/commandExecution/requestApproval", "params", new JsonObject()));
        assertEquals(0, wire.approvalCount); assertEquals("decline", AgentJson.string(AgentJson.child(wire.sent.getLast(), "result"), "decision"));
        assertEquals("read-only", AgentJson.string(wire.params("thread/start"), "sandbox")); assertEquals("never", AgentJson.string(wire.params("thread/start"), "approvalPolicy"));
    }
    @Test void settingsPersistBesideExistingModelAndTurnOptions() throws Exception {
        AgentChatStore store = new AgentChatStore(root); var session = store.create("codex", "fixture-model");
        session.runSettings = new AgentRunSettings("fixture-model", "high", "read-only"); session.options = session.runSettings.options(); store.save();
        AgentChatStore loaded = new AgentChatStore(root); loaded.load();
        assertEquals(session.runSettings, loaded.sessions.get(session.id).runSettings); assertEquals(session.options, loaded.sessions.get(session.id).options);
        assertEquals("fixture-model", loaded.sessions.get(session.id).model);
        assertThrows(IllegalArgumentException.class, () -> AgentRunSettings.parse(AgentJson.object("effort", "high")));
        assertThrows(IllegalArgumentException.class, () -> AgentRunSettings.parse(AgentJson.object("config", "anything")));
    }
    @Test void catalogPermissionProfilesRespectManagedPolicy() throws Exception {
        JsonObject catalog = CodexModelCatalog.read((method, params) -> method.equals("model/list") ? ProfessionalAgentFixtureCli.modelCatalog()
                : AgentJson.object("requirements", AgentJson.object("allowedSandboxModes", AgentJson.array(new JsonPrimitive("read-only")), "allowedApprovalPolicies", AgentJson.array(new JsonPrimitive("never")))));
        assertEquals(AgentJson.array(new JsonPrimitive("read-only")), catalog.getAsJsonArray("permissionProfiles"));
        assertThrows(IllegalArgumentException.class, () -> AgentRunSettings.DEFAULT.validate(catalog));
    }
}
