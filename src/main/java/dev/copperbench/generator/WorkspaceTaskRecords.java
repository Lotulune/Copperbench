package dev.copperbench.generator;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Durable task observations, excluding request payloads and authorization grants. */
final class WorkspaceTaskRecords {
    private WorkspaceTaskRecords() {}
    static void save(Path root, UUID workspaceId, JsonObject task, List<JsonObject> logs, List<JsonObject> diagnostics) throws IOException {
        UUID id = UUID.fromString(task.get("id").getAsString());
        Path path = path(root, id); WorkspaceExecutionSnapshot.rejectLinks(path);
        Files.createDirectories(path.getParent());
        JsonObject record = new JsonObject(); record.addProperty("workspaceId", workspaceId.toString());
        record.add("task", task.deepCopy());
        record.add("diagnostics", new Gson().toJsonTree(diagnostics));
        JsonArray entries = new JsonArray();
        for (JsonObject entry : logs.stream().skip(Math.max(0, logs.size() - 10000)).toList()) {
            entries.add(safeLog(entry));
        }
        record.add("logs", entries);
        Path temporary = path.resolveSibling(id + ".tmp"); WorkspaceExecutionSnapshot.rejectLinks(temporary);
        Files.writeString(temporary, record.toString(), StandardCharsets.UTF_8);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    /** Append while a task is running so abrupt host exit does not discard output since its last stage change. */
    static void appendLog(Path root, UUID id, JsonObject entry) throws IOException {
        Path file = logPath(root, id); WorkspaceExecutionSnapshot.rejectLinks(file);
        Files.createDirectories(file.getParent());
        Files.writeString(file, safeLog(entry) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static JsonObject safeLog(JsonObject entry) {
        JsonObject safe = entry.deepCopy();
        if (safe.has("text")) safe.addProperty("text", dev.copperbench.automation.audit.SensitiveDataRedactor
                .redact(safe.get("text").getAsString())
                .replaceAll("(?i)(authorization\\b[\\\"']?\\s*[=:]\\s*)[^\\r\\n]+", "$1[REDACTED]"));
        return safe;
    }

    private static void restoreLogs(Path root, UUID id, JsonObject record) {
        // Bound reads independently of the duration or verbosity of the original task.
        try {
            Path file = logPath(root, id); WorkspaceExecutionSnapshot.rejectLinks(file);
            if (!Files.isRegularFile(file)) return;
            byte[] tail;
            boolean partial;
            try (var input = new java.io.RandomAccessFile(file.toFile(), "r")) {
                long start = Math.max(0, input.length() - 8 * 1024 * 1024);
                partial = start > 0;
                input.seek(start); tail = new byte[(int) (input.length() - start)]; input.readFully(tail);
            }
            String text = new String(tail, StandardCharsets.UTF_8);
            if (partial) text = text.substring(text.indexOf('\n') + 1);
            var entries = new java.util.TreeMap<Long, JsonObject>();
            if (record.has("logs")) for (var raw : record.getAsJsonArray("logs")) {
                JsonObject entry = raw.getAsJsonObject(); entries.put(entry.get("sequence").getAsLong(), entry);
            }
            for (String line : text.split("\n")) {
                try {
                    JsonObject entry = JsonParser.parseString(line).getAsJsonObject();
                    entries.put(entry.get("sequence").getAsLong(), safeLog(entry));
                } catch (RuntimeException ignored) { /* An interrupted final append must not discard earlier records. */ }
            }
            JsonArray logs = new JsonArray();
            entries.values().stream().skip(Math.max(0, entries.size() - 10000)).forEach(logs::add);
            record.add("logs", logs);
        } catch (IOException | RuntimeException ignored) { /* Atomic task snapshot remains usable without its journal. */ }
    }

    static Optional<JsonObject> read(Path root, UUID workspaceId, UUID id) {
        return read(root, workspaceId, id, true);
    }

    private static Optional<JsonObject> read(Path root, UUID workspaceId, UUID id, boolean includeLogs) {
        try {
            Path path = path(root, id); WorkspaceExecutionSnapshot.rejectLinks(path);
            if (!Files.isRegularFile(path) || Files.size(path) > 16 * 1024 * 1024) return Optional.empty();
            JsonObject record = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!workspaceId.toString().equals(record.get("workspaceId").getAsString())) return Optional.empty();
            JsonObject task = record.getAsJsonObject("task");
            if (!id.toString().equals(task.get("id").getAsString())) return Optional.empty();
            if (includeLogs) restoreLogs(root, id, record);
            if (Set.of("queued", "running").contains(task.get("state").getAsString())) {
                task.addProperty("state", "failed"); task.addProperty("resultObservation", "unconfirmed_after_session_loss");
                task.addProperty("cancellable", false);
                JsonObject stage = new JsonObject(); stage.addProperty("key", "task.interrupted_unconfirmed");
                stage.addProperty("fallback", "Previous session ended without an observed terminal result; inspect logs before rerunning.");
                task.add("stage", stage);
            }
            task.addProperty("restoredFromHistory", true);
            return Optional.of(record);
        } catch (IOException | RuntimeException ignored) { return Optional.empty(); }
    }

    static List<JsonObject> recent(Path root, UUID workspaceId) {
        Path directory = path(root, new UUID(0, 0)).getParent();
        try {
            WorkspaceExecutionSnapshot.rejectLinks(directory);
            if (!Files.isDirectory(directory)) return List.of();
            try (var paths = Files.list(directory)) {
                return paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                                && p.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.json"))
                        .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                        .limit(100).map(p -> {
                            try { return read(root, workspaceId, UUID.fromString(p.getFileName().toString().substring(0, 36)), false); }
                            catch (IllegalArgumentException e) { return Optional.<JsonObject>empty(); }
                        }).flatMap(Optional::stream).map(record -> record.getAsJsonObject("task")).toList();
            }
        } catch (IOException | RuntimeException ignored) { return List.of(); }
    }

    private static Path path(Path root, UUID id) { return root.toAbsolutePath().normalize().resolve(".copperbench/task-records/" + id + ".json"); }
    private static Path logPath(Path root, UUID id) { return path(root, id).resolveSibling(id + ".logs.jsonl"); }
}
