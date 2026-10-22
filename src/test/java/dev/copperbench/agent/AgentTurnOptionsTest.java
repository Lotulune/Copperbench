package dev.copperbench.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentTurnOptionsTest {
    @TempDir Path root;

    @Test void fiveEffortsReachNativeFlagsWithoutMappingAndAutoApprovalIsExplicit() {
        for (String provider : List.of("claude", "grok", "antigravity")) for (String effort : AgentTurnOptions.EFFORTS) {
            AgentProtocol protocol = AgentProtocol.create(provider, new AgentProtocolTest.Wire(), root, null);
            protocol.configure(new AgentTurnOptions(effort, "default"), List.of());
            List<String> args = protocol.arguments();
            assertEquals(effort, args.get(args.indexOf(provider.equals("grok") ? "--reasoning-effort" : "--effort") + 1));
            assertFalse(args.contains("--always-approve")); assertFalse(args.contains("bypassPermissions")); assertFalse(args.contains("--dangerously-skip-permissions"));
            protocol.configure(new AgentTurnOptions(effort, "auto-approve"), List.of());
            assertTrue(protocol.arguments().contains(switch (provider) { case "grok" -> "--always-approve"; case "claude" -> "bypassPermissions"; default -> "--dangerously-skip-permissions"; }));
        }
        assertThrows(IllegalArgumentException.class, () -> new AgentTurnOptions("ultra", "default"));
        assertThrows(IllegalArgumentException.class, () -> new AgentTurnOptions("high", "never"));
    }

    @Test void codexAndClaudeReceiveActualImagesAndFileReferences() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7ioAAAAASUVORK5CYII=");
        try (AgentAttachments store = new AgentAttachments(root)) {
            var picture = AgentAttachmentsTest.add(store, "pic.png", png);
            var note = AgentAttachmentsTest.add(store, "note.txt", "selected note".getBytes());
            for (String provider : List.of("codex", "claude")) {
                var wire = new AgentProtocolTest.Wire(); AgentProtocol protocol = AgentProtocol.create(provider, wire, root, null);
                protocol.configure(new AgentTurnOptions("xhigh", "default"), List.of(picture, note)); protocol.start("inspect", "instructions");
                if (provider.equals("codex")) {
                    JsonObject params = wire.sent.stream().filter(frame -> AgentJson.string(frame, "method").equals("turn/start")).findFirst().orElseThrow().getAsJsonObject("params");
                    assertEquals("xhigh", AgentJson.string(params, "effort"));
                    assertEquals("localImage", AgentJson.string(params.getAsJsonArray("input").get(1).getAsJsonObject(), "type"));
                    assertEquals(picture.path().toString(), AgentJson.string(params.getAsJsonArray("input").get(1).getAsJsonObject(), "path"));
                    assertTrue(AgentJson.string(params.getAsJsonArray("input").get(0).getAsJsonObject(), "text").contains(note.path().toString().replace("\\", "\\\\")));
                } else {
                    JsonArray blocks = wire.sent.getLast().getAsJsonObject("message").getAsJsonArray("content");
                    assertArrayEquals(png, Base64.getDecoder().decode(blocks.get(1).getAsJsonObject().getAsJsonObject("source").get("data").getAsString()));
                    assertEquals("image/png", AgentJson.string(blocks.get(1).getAsJsonObject().getAsJsonObject("source"), "media_type"));
                }
            }
            AgentProtocol unsupported = AgentProtocol.create("antigravity", new AgentProtocolTest.Wire(), root, null);
            assertThrows(IllegalArgumentException.class, () -> unsupported.configure(AgentTurnOptions.DEFAULT, List.of(picture)));
        }
    }

    @Test void codexOverridesBothThreadAndTurnWhenResuming() throws Exception {
        for (String permission : List.of("default", "auto-approve")) {
            var wire = new AgentProtocolTest.Wire(); AgentProtocol protocol = AgentProtocol.create("codex", wire, root, "old-thread");
            protocol.configure(new AgentTurnOptions("max", permission), List.of()); protocol.start("go", "instructions");
            JsonObject thread = wire.sent.stream().filter(frame -> AgentJson.string(frame, "method").equals("thread/resume")).findFirst().orElseThrow().getAsJsonObject("params");
            assertEquals(permission.equals("default") ? "workspace-write" : "danger-full-access", AgentJson.string(thread, "sandbox"));
            assertEquals(permission.equals("default") ? "on-request" : "never", AgentJson.string(thread, "approvalPolicy"));
            assertEquals("max", AgentJson.string(wire.sent.getLast().getAsJsonObject("params"), "effort"));
        }
    }

    @Test void dshOpaqueCatalogAndEffortAreAcknowledgedBeforeSending() throws Exception {
        String opaque = "[\"custom-provider\",\"deepseek-v4.1-flash\"]";
        var wire = new AgentProtocolTest.Wire() {
            String current = opaque, effort = "low";
            @Override public JsonObject rpc(String method, JsonObject params) {
                JsonObject result = super.rpc(method, params);
                if (method.equals("session/set_config_option")) {
                    if (AgentJson.string(params, "configId").equals("model")) { assertEquals(opaque, AgentJson.string(params, "value")); current = AgentJson.string(params, "value"); }
                    else { assertEquals("reasoning_effort", AgentJson.string(params, "configId")); effort = AgentJson.string(params, "value"); }
                }
                if (method.equals("session/new") || method.equals("session/resume") || method.equals("session/set_config_option")) result.add("configOptions", AgentJson.array(
                        AgentJson.object("id", "model", "category", "model", "type", "select", "currentValue", current, "options", AgentJson.array(AgentJson.object("value", opaque, "name", "DeepSeek"))),
                        AgentJson.object("id", "reasoning_effort", "category", "thought_level", "type", "select", "currentValue", effort)));
                return result;
            }
        };
        AgentProtocol protocol = AgentProtocol.create("deepseek", wire, root, null);
        JsonObject catalog = protocol.models(); assertEquals("custom-provider/deepseek-v4.1-flash", catalog.getAsJsonArray("models").get(0).getAsJsonObject().get("id").getAsString());
        protocol.useModel("deepseek-v4.1-flash"); protocol.configure(new AgentTurnOptions("high", "default"), List.of());
        protocol.start("request", "instructions");
        assertEquals("session/prompt", AgentJson.string(wire.sent.getLast(), "method"));
        var unsupportedWire = new AgentProtocolTest.Wire(); AgentProtocol unsupported = AgentProtocol.create("deepseek", unsupportedWire, root, null);
        unsupported.configure(new AgentTurnOptions("xhigh", "default"), List.of());
        assertThrows(IOException.class, () -> unsupported.start("request", "instructions"));
        assertFalse(unsupportedWire.sent.stream().anyMatch(frame -> AgentJson.string(frame, "method").equals("session/prompt")));
    }

    @Test void historyRetainsOptionsAndAttachmentMetadataWithoutEmbeddingContents() throws Exception {
        AgentChatStore store = new AgentChatStore(root);
        var session = store.create("codex", "custom-model"); session.options = new AgentTurnOptions("high", "auto-approve");
        store.save(); AgentChatStore reopened = new AgentChatStore(root); reopened.load();
        assertEquals(session.options, reopened.sessions.get(session.id).options);
        JsonObject exported = JsonParser.parseString(Files.readString(reopened.export(session.id))).getAsJsonObject();
        assertEquals("high", AgentJson.string(exported, "effort")); assertFalse(exported.get("attachmentContentsIncluded").getAsBoolean()); assertFalse(exported.has("remoteId"));
    }
}
