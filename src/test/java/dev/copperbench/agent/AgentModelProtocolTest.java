package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentModelProtocolTest {
    @TempDir Path root;

    @Test void codexCatalogPagesWithoutCreatingThreadsAndAppliesModelOnResumeAndTurn() throws Exception {
        AgentProtocolTest.Wire wire = new AgentProtocolTest.Wire() {
            @Override public JsonObject rpc(String method, JsonObject params) {
                JsonObject result = super.rpc(method, params);
                if (!method.equals("model/list")) return result;
                boolean first = !params.has("cursor");
                if (!first) assertEquals("page-two", AgentJson.string(params, "cursor"));
                return AgentJson.object("data", AgentJson.array(AgentJson.object("model", first ? "alpha" : "beta",
                        "displayName", first ? "Alpha" : "Beta", "isDefault", !first)), "nextCursor", first ? "page-two" : null);
            }
        };
        JsonObject catalog = new CodexAgentProtocol(wire, root, null).models();
        assertEquals("beta", AgentJson.string(catalog, "defaultModel"));
        assertEquals(2, catalog.getAsJsonArray("models").size());
        assertEquals(List.of("initialize", "model/list", "model/list"), wire.methods);
        CodexAgentProtocol protocol = new CodexAgentProtocol(wire, root, "existing-thread");
        protocol.useModel("beta"); protocol.start("create", "instructions");
        for (String method : List.of("thread/resume", "turn/start")) {
            JsonObject frame = wire.sent.stream().filter(row -> AgentJson.string(row, "method").equals(method)).findFirst().orElseThrow();
            assertEquals("beta", AgentJson.string(AgentJson.child(frame, "params"), "model"));
        }
    }

    @Test void selectedModelsReachNativeClaudeGrokAndAntigravityArguments() {
        for (String provider : List.of("claude", "grok", "antigravity")) {
            AgentProtocol protocol = AgentProtocol.create(provider, new AgentProtocolTest.Wire(), root, null);
            protocol.useModel("deepseek-ai/deepseek-v4-flash");
            List<String> arguments = protocol.arguments();
            assertEquals("deepseek-ai/deepseek-v4-flash", arguments.get(arguments.indexOf("--model") + 1));
        }
    }

    @Test void claudeNativeCatalogUsesInitializeWithoutSendingAUserMessage() throws Exception {
        AgentProtocolTest.Wire wire = new AgentProtocolTest.Wire() {
            @Override public JsonObject control(JsonObject request) {
                super.control(request);
                return AgentJson.object("models", AgentJson.array(AgentJson.object("value", "test-model", "displayName", "Test model")));
            }
        };
        JsonObject catalog = new ClaudeAgentProtocol(wire, null).models();
        assertEquals("test-model", AgentJson.string(catalog.getAsJsonArray("models").get(0).getAsJsonObject(), "id"));
        assertEquals(1, wire.sent.size());
        assertEquals("initialize", AgentJson.string(AgentJson.child(wire.sent.getFirst(), "request"), "subtype"));
    }

    @Test void acpRequiresModelAcknowledgementBeforeSendingThePrompt() throws Exception {
        for (boolean confirmed : List.of(false, true)) {
            AgentProtocolTest.Wire wire = new AgentProtocolTest.Wire() {
                @Override public JsonObject rpc(String method, JsonObject params) {
                    JsonObject result = super.rpc(method, params);
                    if (method.equals("session/new") || method.equals("session/set_config_option")) {
                        result.add("configOptions", AgentJson.array(AgentJson.object("id", "active-model", "category", "model", "type", "select",
                                "currentValue", method.equals("session/set_config_option") && confirmed ? "chosen" : "default", "options", new JsonArray())));
                    }
                    if (method.equals("session/set_config_option")) {
                        assertEquals("active-model", AgentJson.string(params, "configId"));
                        assertEquals("chosen", AgentJson.string(params, "value"));
                    }
                    return result;
                }
            };
            AgentProtocol protocol = AgentProtocol.create("deepseek", wire, root, null); protocol.useModel("chosen");
            if (confirmed) protocol.start("create", "instructions");
            else assertThrows(IOException.class, () -> protocol.start("create", "instructions"));
            assertEquals(confirmed, wire.sent.stream().anyMatch(frame -> AgentJson.string(frame, "method").equals("session/prompt")));
        }
    }
}
