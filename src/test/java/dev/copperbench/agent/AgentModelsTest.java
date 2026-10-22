package dev.copperbench.agent;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentModelsTest {
    @TempDir Path root;

    @Test void configuredClaudeSourceReturnsItsOwnModelsWithoutExposingOrRewritingCredentials() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        List<String> headers = new ArrayList<>();
        server.createContext("/v1/models", exchange -> {
            requests.incrementAndGet(); headers.add(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean first = exchange.getRequestURI().getRawQuery() == null;
            String response = first ? "{\"data\":[{\"id\":\"deepseek-v4-flash\",\"display_name\":\"DeepSeek Flash\"}],\"has_more\":true,\"last_id\":\"deepseek-v4-flash\"}"
                    : "{\"data\":[{\"id\":\"z-ai/glm-5.3\"}],\"has_more\":false}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        }); server.start();
        try {
            Path config = configure(server.getAddress().getPort()); byte[] before = Files.readAllBytes(config);
            JsonObject result = ClaudeModelSource.configured(Map.of(), root, root.resolve("workspace")).fetch();
            assertEquals("service", AgentJson.string(result, "source")); assertTrue(AgentJson.bool(result, "requiresSelection"));
            assertEquals(2, result.getAsJsonArray("models").size());
            assertTrue(result.toString().contains("deepseek-v4-flash")); assertFalse(result.toString().contains("fixture-private-token"));
            assertEquals(List.of("Bearer fixture-private-token", "Bearer fixture-private-token"), headers);
            assertEquals(2, requests.get()); assertArrayEquals(before, Files.readAllBytes(config));
        } finally { server.stop(0); }
    }

    @Test void redirectsCannotForwardTheHostCredentialAndErrorsDoNotEchoResponseBodies() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); AtomicInteger forwarded = new AtomicInteger();
        server.createContext("/v1/models", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/other");
            byte[] bytes = "fixture-private-token".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(302, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/other", exchange -> { forwarded.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); }); server.start();
        try {
            configure(server.getAddress().getPort());
            var failure = assertThrows(java.io.IOException.class, () -> ClaudeModelSource.configured(Map.of(), root, root.resolve("workspace")).fetch());
            assertTrue(failure.getMessage().contains("302")); assertFalse(failure.getMessage().contains("fixture-private-token")); assertEquals(0, forwarded.get());
        } finally { server.stop(0); }
    }

    @Test void workspaceOverridesCannotRedirectAHostCredential() throws Exception {
        configure(12345); Path local = root.resolve("workspace/.claude/settings.local.json"); Files.createDirectories(local.getParent());
        Files.writeString(local, "{\"env\":{\"ANTHROPIC_BASE_URL\":\"https://untrusted.invalid\"}}", StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> ClaudeModelSource.configured(Map.of(), root, root.resolve("workspace")));
    }

    @Test void modelIdsAreBoundedBeforeEnteringCliArgumentsAndHistory() {
        for (String invalid : List.of("--permission-mode=bypassPermissions", "model\n--flag", "model%PATH%", "model name", "x".repeat(201)))
            assertThrows(IllegalArgumentException.class, () -> AgentModels.id(invalid));
        assertEquals("deepseek-ai/deepseek-v4-flash", AgentModels.id("deepseek-ai/deepseek-v4-flash"));
        assertNull(AgentModels.id(""));
    }

    @Test void acpGroupedModelOptionsKeepCurrentModelAndIgnorePermissionOptions() {
        JsonObject session = AgentJson.object("configOptions", AgentJson.array(
                AgentJson.object("id", "mode", "category", "mode", "type", "select", "currentValue", "ask", "options", new JsonArray()),
                AgentJson.object("id", "llm-model", "category", "model", "type", "select", "currentValue", "grok-code", "options", AgentJson.array(
                        AgentJson.object("group", "available", "name", "Available", "options", AgentJson.array(AgentJson.object("value", "grok-code", "name", "Grok Code")))))));
        JsonObject result = AgentModels.acpCatalog(session);
        assertEquals("grok-code", AgentJson.string(result, "defaultModel")); assertEquals(1, result.getAsJsonArray("models").size());
        assertFalse(result.toString().contains("ask"));
    }

    @Test void modelIsRetainedAcrossReopenAndIncludedInExportWithoutNativeResumeIds() throws Exception {
        AgentChatStore store = new AgentChatStore(root); var session = store.create("claude", "deepseek-v4-flash");
        session.remoteId = "private-resume-id"; store.save();
        AgentChatStore reopened = new AgentChatStore(root); reopened.load();
        assertEquals("deepseek-v4-flash", reopened.sessions.get(session.id).model);
        String exported = Files.readString(reopened.export(session.id));
        assertTrue(exported.contains("deepseek-v4-flash")); assertFalse(exported.contains("private-resume-id"));
    }

    private Path configure(int port) throws Exception {
        Path file = root.resolve(".claude/settings.json"); Files.createDirectories(file.getParent());
        Files.writeString(file, AgentJson.object("env", AgentJson.object("ANTHROPIC_BASE_URL", "http://127.0.0.1:" + port,
                "ANTHROPIC_AUTH_TOKEN", "fixture-private-token")).toString(), StandardCharsets.UTF_8);
        return file;
    }
}
