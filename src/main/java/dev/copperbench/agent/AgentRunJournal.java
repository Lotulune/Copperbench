package dev.copperbench.agent;

import com.google.gson.*;
import dev.copperbench.platform.PrivatePathPermissions;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Durable per-turn evidence, independent of the browser and of provider-owned sessions. */
final class AgentRunJournal implements AutoCloseable {
    @FunctionalInterface interface Capture { JsonObject capture(Path root) throws IOException; }
    record Baseline(JsonObject snapshot, JsonObject git) { }
    private final Path root, directory;
    private final Capture capture;
    final LinkedHashMap<String, JsonObject> runs = new LinkedHashMap<>();
    private FileChannel lockChannel;
    private FileLock lock;
    private JsonObject baseline;
    final String projectId;
    final String executionTarget = "local-native:" + System.getProperty("os.name");

    AgentRunJournal(Path root) throws IOException {
        this(root, AgentFileSnapshot::capture);
    }
    AgentRunJournal(Path root, Capture capture) throws IOException {
        this.capture = capture;
        this.root = root.toRealPath(); directory = this.root.resolve(".copperbench/agent-chat/runs");
        projectId = UUID.nameUUIDFromBytes((executionTarget + ":" + this.root).getBytes(StandardCharsets.UTF_8)).toString();
        if (Files.isDirectory(directory)) try (var guard = FileChannel.open(directory.resolve("active.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock recoveryLock;
            try { recoveryLock = guard.tryLock(); } catch (OverlappingFileLockException busy) { recoveryLock = null; }
            boolean recover = recoveryLock != null;
            try (var paths = Files.list(directory)) {
            for (Path file : paths.filter(path -> path.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).sorted().toList()) {
                if (Files.size(file) > 24 * 1024 * 1024) throw new IOException("Agent run record exceeds the read limit: " + file.getFileName());
                JsonObject record = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!record.has("schemaVersion") || record.get("schemaVersion").getAsInt() != 1
                        || !file.getFileName().toString().equals(AgentJson.required(record, "runId") + ".json")
                        || !record.has("createdAt") || !record.has("execution") || !record.get("execution").isJsonObject()
                        || !record.has("events") || !record.get("events").isJsonArray())
                    throw new IOException("Unsupported or damaged Agent run record; the original file was preserved: " + file.getFileName());
                if (!projectId.equals(AgentJson.string(record, "projectId"))) throw new IOException("Agent history belongs to a different project path or execution environment");
                runs.put(AgentJson.required(record, "runId"), record);
                if (recover && !terminal(AgentJson.string(record, "state"))) {
                    record.addProperty("state", "interrupted");
                    AgentJson.child(record, "execution").addProperty("cleanup", "unconfirmed");
                    record.addProperty("error", "Execution service restarted before resource cleanup was confirmed. Inspect the recorded process and its tools before using another workspace.");
                    save(record);
                }
                JsonObject verification = AgentJson.child(record, "verification");
                if (recover && AgentJson.string(verification, "status").equals("running")) {
                    verification.addProperty("status", "interrupted"); verification.addProperty("cleanup", "unconfirmed"); save(record);
                }
            }
            } finally { if (recoveryLock != null) recoveryLock.release(); }
        }
    }

    static boolean terminal(String state) { return Set.of("completed", "failed", "cancelled", "interrupted").contains(state); }
    boolean blocked() { return runs.values().stream().anyMatch(run -> "unconfirmed".equals(AgentJson.string(AgentJson.child(run, "execution"), "cleanup"))
            || "unconfirmed".equals(AgentJson.string(AgentJson.child(run, "verification"), "cleanup"))
            || terminal(AgentJson.string(run, "state")) && !AgentHistoryRecovery.stopped(run)
                && !(lock != null && "running".equals(AgentJson.string(AgentJson.child(run, "verification"), "status")))); }
    void acquire() throws IOException {
        if (blocked()) throw new IOException("Task stop not yet confirmed. Inspect the previous run's execution details; new writes are blocked.");
        if (lock != null) throw new IOException("An Agent or verification task already owns this workspace");
        PrivatePathPermissions.createPrivateDirectory(directory);
        lockChannel = FileChannel.open(directory.resolve("active.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try { lock = lockChannel.tryLock(); }
        catch (OverlappingFileLockException busy) { lock = null; }
        if (lock == null) { lockChannel.close(); lockChannel = null; throw new IOException("Another Copperbench window is running an Agent in this workspace"); }
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).toList())
                AgentHistoryRecovery.requireStopped(AgentHistoryRecovery.readRecord(file));
        } catch (IOException | RuntimeException failure) {
            release(); throw failure;
        }
    }

    JsonObject begin(String requestId, String sessionId, AgentProvider provider, JsonObject context) throws IOException {
        if (runs.size() >= 100) throw new IOException("Agent run history reached 100 runs. Export and delete finished conversations in conversation history to free space.");
        String id = UUID.randomUUID().toString();
        JsonObject record = AgentJson.object("schemaVersion", 1, "taskId", id, "runId", id, "attempt", 1, "requestId", requestId,
                "conversationId", sessionId, "projectId", projectId, "canonicalRoot", root, "executionTargetId", executionTarget,
                "agentId", provider.id(), "cliVersion", provider.version(), "adapterVersion", "copperbench-1", "state", "preparing",
                "createdAt", System.currentTimeMillis(), "context", context, "events", new JsonArray(),
                "execution", AgentJson.object("cleanup", "pending"), "verification", AgentJson.object("status", "not_run"));
        runs.put(id, record); event(record, "state", AgentJson.object("state", "preparing"));
        return record;
    }
    void baseline(JsonObject run) throws IOException {
        Baseline collected = collectBaseline(); persistBaseline(AgentJson.string(run, "runId"), collected); attachBaseline(run, collected);
    }
    Baseline collectBaseline() throws IOException { return new Baseline(capture.capture(root), gitBaseline()); }
    void persistBaseline(String runId, Baseline collected) throws IOException { write(directory.resolve(runId + ".baseline.json"), collected.snapshot()); }
    void attachBaseline(JsonObject run, Baseline collected) throws IOException {
        baseline = collected.snapshot(); run.addProperty("baselineFingerprint", AgentJson.string(baseline, "fingerprint")); run.add("gitBaseline", collected.git()); save(run);
    }
    JsonObject collectChanges() {
        try { return baseline == null ? AgentJson.object("coverage", "unavailable", "files", new JsonArray()) : AgentFileSnapshot.changes(baseline, capture.capture(root)); }
        catch (IOException | RuntimeException failure) { return AgentJson.object("coverage", "unavailable", "error", AgentJson.redacted(failure.getMessage()), "files", new JsonArray()); }
    }
    JsonObject captureCurrent() throws IOException { return capture.capture(root); }
    private JsonObject gitBaseline() {
        JsonObject result = new JsonObject();
        for (String name : List.of("head", "status")) {
            List<String> command = name.equals("head") ? List.of("git", "rev-parse", "HEAD") : List.of("git", "status", "--porcelain=v1", "-z", "--untracked-files=normal");
            Process process = null;
            try {
                process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
                Process target = process;
                var output = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try { return target.getInputStream().readNBytes(64 * 1024 + 1); } catch (IOException exception) { return new byte[0]; }
                });
                if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroyForcibly(); result.addProperty(name, "unavailable"); }
                else result.addProperty(name, process.exitValue() == 0 ? AgentJson.bounded(new String(output.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8).replace('\0', '\n'), 65536) : "unavailable");
            } catch (Exception failure) { result.addProperty(name, "unavailable"); if (process != null) process.destroyForcibly(); }
        }
        return result;
    }
    void finish(JsonObject run, String state, JsonObject execution, String error) throws IOException {
        finish(run, state, execution, error, collectChanges());
    }
    void finish(JsonObject run, String state, JsonObject execution, String error, JsonObject changes) throws IOException {
        run.addProperty("state", "finalizing"); event(run, "state", AgentJson.object("state", "finalizing"));
        run.add("changes", changes);
        run.addProperty("state", state); run.addProperty("endedAt", System.currentTimeMillis()); run.add("execution", execution);
        if (error != null) run.addProperty("error", AgentJson.bounded(error, 4000));
        event(run, "finished", AgentJson.object("state", state, "cleanup", execution.get("cleanup")));
        baseline = null;
        if (!"unconfirmed".equals(AgentJson.string(execution, "cleanup"))) release();
    }
    JsonObject capacity() { return AgentJson.object("count", runs.size(), "maxCount", 100, "level", runs.size() >= 100 ? "full" : runs.size() >= 90 ? "near" : "ok"); }
    JsonArray exportConversation(String conversationId) throws IOException {
        JsonArray result = new JsonArray(); int bytes = 0;
        for (JsonObject run : runs.values()) if (conversationId.equals(AgentJson.string(run, "conversationId"))) {
            bytes += run.toString().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 16 * 1024 * 1024) throw new IOException("Run evidence exceeds the conversation export limit. Export the required runs individually before deleting the conversation.");
            JsonObject copy = AgentJson.redactedTree(run).getAsJsonObject(); copy.remove("sessionId"); result.add(copy);
        }
        return result;
    }
    void requireDeletable(Collection<String> conversations) throws IOException {
        for (JsonObject run : runs.values()) if (conversations.contains(AgentJson.string(run, "conversationId"))) {
            AgentHistoryRecovery.requireStopped(run);
        }
    }
    void deleteConversations(Collection<String> conversations) throws IOException {
        requireDeletable(conversations);
        if (lock != null) { deleteConfirmedConversations(conversations); return; }
        PrivatePathPermissions.createPrivateDirectory(directory);
        try (var guard = FileChannel.open(directory.resolve("active.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock held;
            try { held = guard.tryLock(); } catch (OverlappingFileLockException busy) { held = null; }
            if (held == null) throw new IOException("Another Agent execution owns this project. Stop it before deleting history.");
            try { deleteConfirmedConversations(conversations); } finally { held.release(); }
        }
    }
    private void deleteConfirmedConversations(Collection<String> conversations) throws IOException {
        List<String> selected = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).toList()) {
                JsonObject record = AgentHistoryRecovery.readRecord(file);
                if (!conversations.contains(AgentJson.string(record, "conversationId"))) continue;
                AgentHistoryRecovery.requireStopped(record);
                String id = AgentJson.required(record, "runId"); UUID.fromString(id); selected.add(id);
            }
        }
        for (String id : selected) {
            Files.deleteIfExists(directory.resolve(id + ".baseline.json")); Files.deleteIfExists(directory.resolve(id + ".json")); runs.remove(id);
        }
    }
    void event(JsonObject run, String type, JsonObject payload) throws IOException {
        JsonArray events = AgentJson.list(run, "events");
        long sequence = run.has("sequence") ? run.get("sequence").getAsLong() + 1 : 1;
        run.addProperty("sequence", sequence);
        events.add(AgentJson.object("schemaVersion", 1, "eventId", UUID.randomUUID(), "runId", run.get("runId"), "sequence", sequence, "recordedAt", System.currentTimeMillis(), "type", type, "payload", AgentJson.redactedTree(payload)));
        // Text lives in the bounded conversation log. Control/terminal evidence is never truncated here.
        save(run);
    }
    void save(JsonObject record) throws IOException { write(directory.resolve(AgentJson.required(record, "runId") + ".json"), record); }
    static void write(Path file, JsonObject value) throws IOException {
        PrivatePathPermissions.createPrivateDirectory(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "record-", ".tmp");
        try {
            PrivatePathPermissions.makePrivateFile(temporary);
            Files.writeString(temporary, value.toString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    JsonArray summaries(String session) {
        JsonArray result = new JsonArray();
        runs.values().stream().filter(run -> session.equals(AgentJson.string(run, "conversationId")))
                .sorted(Comparator.comparingLong((JsonObject run) -> run.get("createdAt").getAsLong()).reversed()).forEach(run -> {
                    JsonObject summary = new JsonObject();
                    for (String key : List.of("runId", "conversationId", "state", "agentId", "createdAt", "endedAt", "cliVersion", "adapterVersion", "canonicalRoot", "executionTargetId", "execution"))
                        if (run.has(key)) summary.add(key, run.get(key).deepCopy());
                    result.add(summary);
                });
        return result;
    }
    void release() throws IOException { if (lock != null) { lock.release(); lock = null; } if (lockChannel != null) { lockChannel.close(); lockChannel = null; } }
    @Override public void close() throws IOException { release(); }
}
