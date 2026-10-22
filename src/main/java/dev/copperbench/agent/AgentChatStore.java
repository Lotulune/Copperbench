package dev.copperbench.agent;

import com.google.gson.*;
import dev.copperbench.platform.PrivatePathPermissions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class AgentChatStore {
    static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private final Path file;
    final LinkedHashMap<String, Conversation> sessions = new LinkedHashMap<>();
    String selected;
    boolean unreadable;

    static final class Conversation {
        final String id;
        final String provider;
        String title;
        String remoteId;
        String model;
        AgentTurnOptions options = AgentTurnOptions.DEFAULT;
        long updatedAt;
        final JsonArray messages;
        boolean interrupted;
        boolean defaultTitle;
        Conversation(String id, String provider, String title, long updatedAt, JsonArray messages) {
            this.id = id; this.provider = provider; this.title = title; this.updatedAt = updatedAt; this.messages = messages;
        }
        JsonObject summary() {
            JsonObject result = AgentJson.object("id", id, "provider", provider, "title", title, "updatedAt", updatedAt, "model", model);
            options.put(result);
            if (defaultTitle) AgentCopy.put(result, "title", "新创作");
            result.addProperty("defaultTitle", defaultTitle);
            return result;
        }
        JsonObject json() {
            JsonObject result = summary(); result.addProperty("remoteId", remoteId);
            result.addProperty("interrupted", interrupted); result.add("messages", messages); return result;
        }
    }

    AgentChatStore(Path root) { file = root.resolve(".copperbench/agent-chat/history.json"); }

    void load() throws IOException {
        if (!Files.exists(file)) return;
        if (Files.size(file) > MAX_FILE_BYTES) { unreadable = true; throw new IOException("创作记录超过读取上限，请保留并整理 " + file); }
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
                    AgentJson.required(message, "id");
                    if (!message.has("at") || !message.get("at").isJsonPrimitive()
                            || !message.get("at").getAsJsonPrimitive().isNumber()) throw new JsonParseException("Missing message timestamp");
                    if (!Set.of("user", "assistant", "system", "tool").contains(AgentJson.string(message, "role")))
                        throw new JsonParseException("Unknown message role");
                    message.addProperty("text", AgentJson.bounded(AgentJson.string(message, "text"), 32768));
                    AgentCopy.retainValidTranslation(message, "text");
                    if (message.has("attachments")) {
                        if (!message.get("attachments").isJsonArray() || message.getAsJsonArray("attachments").size() > AgentAttachments.MAX_COUNT)
                            throw new JsonParseException("Invalid message attachments");
                        for (JsonElement attachment : message.getAsJsonArray("attachments")) {
                            JsonObject item = attachment.getAsJsonObject(); UUID.fromString(AgentJson.required(item, "id"));
                            if (AgentJson.required(item, "name").length() > 180 || !Set.of("application/octet-stream", "image/png", "image/jpeg", "image/gif").contains(AgentJson.string(item, "mediaType"))
                                    || item.get("size").getAsLong() <= 0 || item.get("size").getAsLong() > AgentAttachments.MAX_FILE_BYTES)
                                throw new JsonParseException("Invalid message attachment");
                        }
                    }
                    if ("pending".equals(AgentJson.string(message, "delivery"))) message.addProperty("delivery", "failed");
                }
                Conversation conversation = new Conversation(id, provider, AgentJson.bounded(AgentJson.required(session, "title"), 120),
                        session.get("updatedAt").getAsLong(), messages.deepCopy());
                String remote = AgentJson.string(session, "remoteId");
                if (!remote.isBlank()) conversation.remoteId = AgentJson.remoteId(remote);
                if (session.has("model") && !session.get("model").isJsonNull()) {
                    if (!session.get("model").isJsonPrimitive() || !session.get("model").getAsJsonPrimitive().isString()) throw new JsonParseException("Invalid conversation model");
                    conversation.model = AgentModels.id(session.get("model").getAsString());
                }
                conversation.interrupted = AgentJson.bool(session, "interrupted");
                conversation.options = AgentTurnOptions.from(session, AgentTurnOptions.DEFAULT);
                conversation.defaultTitle = AgentJson.bool(session, "defaultTitle");
                if (sessions.putIfAbsent(id, conversation) != null) throw new JsonParseException("Duplicate conversation");
            }
            selected = AgentJson.string(data, "selectedConversationId");
            if (!sessions.containsKey(selected)) selected = null;
        } catch (RuntimeException invalid) {
            sessions.clear(); selected = null; unreadable = true;
            throw new IOException("无法读取创作记录，原文件已保留：" + file, invalid);
        }
    }

    Conversation create(String provider) {
        return create(provider, null);
    }

    Conversation create(String provider, String model) {
        if (sessions.size() >= 100) throw new IllegalStateException("创作会话已达 100 条，请先整理工作区创作记录");
        AgentProvider.label(provider);
        Conversation result = new Conversation(UUID.randomUUID().toString(), provider, "新创作", System.currentTimeMillis(), new JsonArray());
        result.defaultTitle = true;
        result.model = AgentModels.id(model);
        sessions.put(result.id, result); selected = result.id; return result;
    }

    private String serialized() {
        JsonArray entries = new JsonArray(); sessions.values().forEach(session -> entries.add(session.json()));
        return AgentJson.object("schemaVersion", "1", "selectedConversationId", selected, "sessions", entries).toString();
    }

    JsonObject capacity() {
        long bytes = serialized().getBytes(StandardCharsets.UTF_8).length;
        if (unreadable) try { bytes = Files.size(file); } catch (IOException ignored) { }
        return AgentJson.object("count", sessions.size(), "maxCount", 100, "bytes", bytes, "maxBytes", MAX_FILE_BYTES,
                "level", unreadable ? "unreadable" : sessions.size() >= 100 || bytes >= MAX_FILE_BYTES ? "full"
                        : sessions.size() >= 90 || bytes >= MAX_FILE_BYTES * .9 ? "near" : "ok");
    }

    void delete(String id) throws IOException {
        delete(List.of(id));
    }

    void delete(List<String> ids) throws IOException {
        if (unreadable) throw new IOException("请先备份并恢复无法读取的历史记录");
        if (ids.isEmpty() || ids.stream().anyMatch(id -> !sessions.containsKey(id))) throw new IllegalArgumentException("创作会话不存在");
        var previous = new LinkedHashMap<>(sessions);
        String previousSelected = selected;
        ids.forEach(sessions::remove);
        if (ids.contains(selected)) selected = null;
        try { save(); }
        catch (IOException failure) { sessions.clear(); sessions.putAll(previous); selected = previousSelected; throw failure; }
    }

    Path export(String id) throws IOException {
        Conversation session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("创作会话不存在");
        JsonArray messages = new JsonArray();
        for (JsonElement item : session.messages) {
            JsonObject message = item.getAsJsonObject();
            JsonObject exported = AgentJson.object("role", AgentJson.string(message, "role"),
                    "text", AgentJson.redacted(AgentJson.string(message, "text")), "at", message.get("at"));
            if (message.has("attachments")) exported.add("attachments", message.get("attachments").deepCopy());
            if (message.has("delivery")) exported.add("delivery", message.get("delivery"));
            messages.add(exported);
        }
        JsonObject data = AgentJson.object("schemaVersion", "1", "provider", session.provider,
                "title", AgentJson.redacted(session.title), "model", session.model, "updatedAt", session.updatedAt, "messages", messages);
        session.options.put(data);
        data.addProperty("attachmentContentsIncluded", false);
        Path directory = file.getParent().resolve("exports");
        PrivatePathPermissions.createPrivateDirectory(directory);
        Path destination = directory.resolve("conversation-" + UUID.randomUUID() + ".json");
        Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(data), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        PrivatePathPermissions.makePrivateFile(destination);
        return destination;
    }

    Path recover() throws IOException {
        if (!unreadable) throw new IllegalStateException("历史记录可以读取，请使用会话整理");
        Path backup = file.resolveSibling("history-backup-" + UUID.randomUUID() + ".json");
        Files.copy(file, backup);
        PrivatePathPermissions.makePrivateFile(backup);
        // Original is replaced only after a verified byte-for-byte private backup exists.
        if (Files.mismatch(file, backup) != -1) throw new IOException("历史备份验证失败，原文件未变更");
        save(); unreadable = false;
        return backup;
    }

    void save() throws IOException {
        String text = serialized();
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
