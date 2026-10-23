package dev.copperbench.agent;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.copperbench.platform.PrivatePathPermissions;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Archives recoverable conversation/run metadata while excluding another active execution owner. */
final class AgentHistoryRecovery implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    final Path backup;

    private AgentHistoryRecovery(FileChannel channel, FileLock lock, Path backup) {
        this.channel = channel; this.lock = lock; this.backup = backup;
    }

    static AgentHistoryRecovery begin(Path workspace) throws IOException {
        Path root = workspace.toRealPath(), directory = root.resolve(".copperbench/agent-chat");
        Path runs = directory.resolve("runs");
        for (Path path : List.of(root.resolve(".copperbench"), directory, runs)) {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(path) || !path.toRealPath().equals(path)) throw new IOException("Agent history is outside its project directory");
            } else PrivatePathPermissions.createPrivateDirectory(path);
        }
        FileChannel channel = FileChannel.open(runs.resolve("active.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try { lock = channel.tryLock(); }
        catch (OverlappingFileLockException busy) { lock = null; }
        catch (IOException | RuntimeException failure) { channel.close(); throw failure; }
        if (lock == null) { channel.close(); throw new IOException("Another Agent execution owns this project. Stop it before recovering history."); }
        List<Path[]> moved = new ArrayList<>();
        try {
            try (var files = Files.list(runs)) {
                for (Path path : files.filter(p -> p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).toList())
                    requireStopped(path);
            }
            Path backup = directory.resolve("history-archive-" + UUID.randomUUID());
            PrivatePathPermissions.createPrivateDirectory(backup);
            PrivatePathPermissions.createPrivateDirectory(backup.resolve("runs"));
            Path history = directory.resolve("history.json");
            if (Files.exists(history, LinkOption.NOFOLLOW_LINKS)) move(history, backup.resolve("history.json"), moved);
            try (var files = Files.list(runs)) {
                for (Path path : files.filter(p -> !p.getFileName().toString().equals("active.lock")).toList())
                    move(path, backup.resolve("runs").resolve(path.getFileName()), moved);
            }
            return new AgentHistoryRecovery(channel, lock, backup);
        } catch (IOException | RuntimeException failure) {
            for (int i = moved.size() - 1; i >= 0; i--) {
                Path[] item = moved.get(i);
                try { Files.move(item[1], item[0]); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            try { lock.release(); } finally { channel.close(); }
            throw failure;
        }
    }

    private static void move(Path from, Path to, List<Path[]> moved) throws IOException {
        Files.move(from, to);
        moved.add(new Path[] {from, to});
    }

    private static void requireStopped(Path file) throws IOException {
        requireStopped(readRecord(file));
    }

    static JsonObject readRecord(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 24 * 1024 * 1024)
            throw new IOException("Run cleanup could not be verified; its history was preserved: " + file.getFileName());
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8); var reader = new JsonReader(input)) {
            JsonObject run = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (Set.of("schemaVersion", "runId", "conversationId", "state").contains(name))
                    run.add(name, JsonParser.parseReader(reader));
                else if (name.equals("execution") || name.equals("verification")) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); run.add(name, JsonNull.INSTANCE); continue; }
                    JsonObject fields = new JsonObject(); reader.beginObject();
                    while (reader.hasNext()) {
                        String field = reader.nextName();
                        if (Set.of("cleanup", "status", "executionTaskId").contains(field)) fields.add(field, JsonParser.parseReader(reader));
                        else reader.skipValue();
                    }
                    reader.endObject(); run.add(name, fields);
                } else reader.skipValue();
                // These fields precede potentially large file changes in records written by this service.
                if (run.keySet().containsAll(Set.of("schemaVersion", "runId", "conversationId", "state", "execution", "verification"))) break;
            }
            if (!run.has("schemaVersion") || run.get("schemaVersion").getAsInt() != 1
                    || !file.getFileName().toString().equals(AgentJson.string(run, "runId") + ".json"))
                throw new IOException("Run cleanup could not be verified; its history was preserved: " + file.getFileName());
            return run;
        } catch (IOException | RuntimeException malformed) {
            throw new IOException("Run cleanup could not be verified; its history was preserved: " + file.getFileName(), malformed);
        }
    }

    static boolean stopped(JsonObject run) {
        try {
            if (!AgentRunJournal.terminal(AgentJson.string(run, "state"))
                    || !Set.of("confirmed", "not_needed").contains(AgentJson.string(AgentJson.child(run, "execution"), "cleanup"))
                    || !run.has("verification") || !run.get("verification").isJsonObject()) return false;
            JsonObject verification = run.getAsJsonObject("verification");
            String status = AgentJson.string(verification, "status"), cleanup = AgentJson.string(verification, "cleanup");
            if (status.equals("not_run")) return (cleanup.isEmpty() || cleanup.equals("not_needed"))
                    && AgentJson.string(verification, "executionTaskId").isEmpty();
            return Set.of("passed", "failed", "blocked", "cancelled").contains(status)
                    && (Set.of("confirmed", "not_needed").contains(cleanup)
                        || cleanup.equals("core_terminal") && !AgentJson.string(verification, "executionTaskId").isEmpty());
        } catch (RuntimeException malformed) { return false; }
    }

    static void requireStopped(JsonObject run) throws IOException {
        if (!stopped(run)) throw new IOException("Run cleanup is not confirmed. History recovery cannot clear this execution block.");
    }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
