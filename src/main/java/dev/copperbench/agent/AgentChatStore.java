package dev.copperbench.agent;

import com.google.gson.*;
import dev.copperbench.platform.PrivatePathPermissions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class AgentChatStore {
    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private final Path file;
    final LinkedHashMap<String, Conversation> sessions = new LinkedHashMap<>();
    String selected;

    static final class Conversation {
        final String id;
        final String provider;
        String title;
        String remoteId;
        long updatedAt;
        final JsonArray messages;
        boolean interrupted;
        Conversation(String id, String provider, String title, long updatedAt, JsonArray messages) {
            this.id = id; this.provider = provider; this.title = title; this.updatedAt = updatedAt; this.messages = messages;
        }
        JsonObject summary() { return AgentJson.object("id", id, "provider", provider, "title", title, "updatedAt", updatedAt); }
        JsonObject json() {
            JsonObject result = summary(); result.addProperty("remoteId", remoteId);
            result.addProperty("interrupted", interrupted); result.add("messages", messages); return result;
        }
    }

    AgentChatStore(Path root) { file = root.resolve(".copperbench/agent-chat/history.json"); }

    void load() throws IOException {
        if (!Files.exists(file)) return;
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("创作记录超过读取上限，请保留并整理 " + file);
        try {
            JsonObject data = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"1".equals(AgentJson.string(data, "schemaVersion"))) throw new JsonParseException("Unknown history schema");
            JsonArray saved = AgentJson.list(data, "sessions");
            if (saved.size() > 100) throw new JsonParseException("Too many conversations");
            for (JsonElement element : saved) {
                JsonObject session = element.getAsJsonObject();
                String id = AgentJson.required(session, "id"), provider = AgentJson.required(session, "provider");
                UUID.fromString(id); AgentProvider.label(provider);
                JsonArray messages = AgentJson.list(session, "messages");
                if (messages.size() > 1000) throw new JsonParseException("Too many messages");
                for (JsonElement value : messages) {
                    JsonObject message = value.getAsJsonObject();
                    if (!Set.of("user", "assistant", "system", "tool").contains(AgentJson.string(message, "role")))
                        throw new JsonParseException("Unknown message role");
                    message.addProperty("text", AgentJson.bounded(AgentJson.string(message, "text"), 32768));
                }
                Conversation conversation = new Conversation(id, provider, AgentJson.bounded(AgentJson.required(session, "title"), 120),
                        session.get("updatedAt").getAsLong(), messages.deepCopy());
                String remote = AgentJson.string(session, "remoteId");
                if (!remote.isBlank()) conversation.remoteId = AgentJson.remoteId(remote);
                conversation.interrupted = AgentJson.bool(session, "interrupted");
                if (sessions.putIfAbsent(id, conversation) != null) throw new JsonParseException("Duplicate conversation");
            }
            selected = AgentJson.string(data, "selectedConversationId");
            if (!sessions.containsKey(selected)) selected = null;
        } catch (RuntimeException invalid) {
            sessions.clear(); selected = null;
            throw new IOException("无法读取创作记录，原文件已保留：" + file, invalid);
        }
    }

    Conversation create(String provider) {
        if (sessions.size() >= 100) throw new IllegalStateException("创作会话已达 100 条，请先整理工作区创作记录");
        AgentProvider.label(provider);
        Conversation result = new Conversation(UUID.randomUUID().toString(), provider, "新创作", System.currentTimeMillis(), new JsonArray());
        sessions.put(result.id, result); selected = result.id; return result;
    }

    void save() throws IOException {
        JsonArray entries = new JsonArray(); sessions.values().forEach(session -> entries.add(session.json()));
        String text = AgentJson.object("schemaVersion", "1", "selectedConversationId", selected, "sessions", entries).toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IOException("创作记录已达 16 MiB，请整理历史后继续");
        PrivatePathPermissions.createPrivateDirectory(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "history-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            PrivatePathPermissions.makePrivateFile(temporary);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
