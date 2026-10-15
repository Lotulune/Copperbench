/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.generator;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchService;
import java.nio.file.WatchKey;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Input observations for live build/export/client tasks, separate from packaged-JAR acceptance. */
public final class WorkspaceTaskInputIdentity {
    private static final int MAX_RECEIPT_BYTES = 16 * 1024;
    private WorkspaceTaskInputIdentity() {}

    /** Observes authored input events as well as endpoint hashes, including edits subsequently restored. */
    public static final class Monitor implements AutoCloseable {
        private final Path root;
        private final WatchService watch;
        private final java.util.Set<Path> publishedOutputs = new java.util.HashSet<>();
        private Monitor(Path workspaceRoot) throws IOException {
            root = workspaceRoot.toAbsolutePath().normalize();
            watch = root.getFileSystem().newWatchService();
            try {
                int[] directories = {0};
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                        if (!directory.equals(root) && WorkspaceExecutionSnapshot.excluded(root.relativize(directory))) return FileVisitResult.SKIP_SUBTREE;
                        WorkspaceExecutionSnapshot.rejectLinks(directory);
                        if (++directories[0] > 100_000) throw new IOException("Input event observation exceeds 100,000 directories");
                        directory.register(watch, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | RuntimeException failure) { watch.close(); throw failure; }
        }
        /** Only this task's successfully published, receipt-backed file is exempt; directory events remain inputs. */
        public void publishedOutput(Path output) { publishedOutputs.add(output.toAbsolutePath().normalize()); }
        public boolean changed() throws IOException, InterruptedException {
            boolean changed = false;
            // Let the filesystem deliver final process writes; this happens once, never in task polling.
            WatchKey key = watch.poll(50, TimeUnit.MILLISECONDS);
            while (key != null) {
                Path directory = (Path) key.watchable();
                for (var event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) throw new IOException("Input event observation overflowed; input stability is unknown");
                    Path input = directory.resolve((Path) event.context());
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY && Files.isDirectory(input, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                    if (!WorkspaceExecutionSnapshot.excluded(root.relativize(input))
                            && !publishedOutputs.contains(input.toAbsolutePath().normalize())) changed = true;
                }
                if (!key.reset()) changed = true;
                key = watch.poll();
            }
            return changed;
        }
        @Override public void close() throws IOException { watch.close(); }
    }

    public static Monitor monitor(Path root) throws IOException { return new Monitor(root); }

    public static JsonObject start(Path root, UUID workspaceId, UUID taskId, long revision,
            Clock clock, BooleanSupplier cancelled) throws IOException {
        var input = WorkspaceExecutionSnapshot.observeInputIdentity(root, cancelled);
        Path path = path(root, taskId);
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("sha256", input.sha256()); snapshot.addProperty("fileCount", input.fileCount());
        snapshot.addProperty("bytes", input.bytes()); snapshot.addProperty("manifestPath", path.toString());
        JsonObject receipt = new JsonObject();
        receipt.addProperty("schemaVersion", "1.0"); receipt.addProperty("workspaceId", workspaceId.toString());
        receipt.addProperty("taskId", taskId.toString()); receipt.addProperty("revision", revision);
        receipt.addProperty("observedAtStart", clock.instant().toString());
        receipt.addProperty("boundary", "after_generation_before_process");
        receipt.add("sourceSnapshot", snapshot.deepCopy());
        Files.createDirectories(path.getParent()); WorkspaceExecutionSnapshot.rejectLinks(path);
        Files.writeString(path, receipt.toString() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return snapshot;
    }

    public static void finish(Path root, UUID workspaceId, UUID taskId, JsonObject snapshot,
            boolean revisionUnchanged, Monitor monitor, Clock clock, BooleanSupplier cancelled) throws IOException, InterruptedException {
        Path path = path(root, taskId);
        JsonObject receipt = read(path, workspaceId, taskId, snapshot);
        var input = WorkspaceExecutionSnapshot.observeInputIdentity(root, cancelled);
        boolean noInputEvents = !monitor.changed();
        receipt.addProperty("observedAtCompletion", clock.instant().toString());
        receipt.addProperty("sourceSha256AtCompletion", input.sha256());
        receipt.addProperty("noInputEventsObserved", noInputEvents);
        receipt.addProperty("inputUnchangedAtCompletion", revisionUnchanged && noInputEvents
                && snapshot.get("sha256").getAsString().equals(input.sha256()));
        replace(path, receipt);
    }

    public static String failureDetail(Exception failure) {
        String code = failure instanceof WorkspaceExecutionSnapshot.SnapshotException snapshot ? snapshot.code() : failure.getClass().getSimpleName();
        String message = failure.getMessage() == null ? code : code + ": " + failure.getMessage();
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }

    /** A missing freshness observation must not change an ordinary task's execution semantics. */
    public static void unavailable(Path root, UUID workspaceId, UUID taskId, String cause, Clock clock) throws IOException {
        Path path = path(root, taskId);
        JsonObject receipt;
        if (Files.exists(path)) receipt = read(path, workspaceId, taskId, null);
        else {
            receipt = new JsonObject(); receipt.addProperty("schemaVersion", "1.0");
            receipt.addProperty("workspaceId", workspaceId.toString()); receipt.addProperty("taskId", taskId.toString());
            receipt.addProperty("boundary", "after_generation_before_process");
        }
        receipt.addProperty("inputUnavailableCause", cause);
        receipt.addProperty("unavailableAt", clock.instant().toString());
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) replace(path, receipt);
        else Files.writeString(path, receipt.toString() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static void replace(Path path, JsonObject receipt) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "task-input-", ".tmp");
        try {
            Files.writeString(temporary, receipt.toString() + "\n", StandardCharsets.UTF_8);
            WorkspaceExecutionSnapshot.rejectLinks(path);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    /** A single current input inventory is shared across this explicitly requested, bounded task batch. */
    public static JsonObject observe(Path root, UUID workspaceId, List<JsonObject> tasks, Clock clock) throws IOException {
        if (tasks.size() > 8) throw new IllegalArgumentException("Select at most eight task references");
        JsonObject facts = new JsonObject();
        boolean needsCurrent = false;
        for (JsonObject task : tasks) {
            UUID id = UUID.fromString(task.get("id").getAsString());
            JsonObject fact = new JsonObject(); fact.addProperty("status", "unknown");
            fact.addProperty("reason", "input_identity_not_recorded");
            Path receiptPath;
            try { receiptPath = path(root, id); }
            catch (IOException | RuntimeException unavailable) {
                fact.addProperty("reason", "input_identity_unavailable"); fact.addProperty("detail", failureDetail(unavailable));
                facts.add(id.toString(), fact); continue;
            }
            if (task.has("sourceSnapshot") && task.get("sourceSnapshot").isJsonObject()) {
                JsonObject snapshot = task.getAsJsonObject("sourceSnapshot");
                try {
                    JsonObject receipt = read(receiptPath, workspaceId, id, snapshot);
                    fact.addProperty("sourceSha256", snapshot.get("sha256").getAsString());
                    if (receipt.has("inputUnavailableCause")) {
                        fact.addProperty("reason", "input_identity_unavailable"); fact.add("detail", receipt.get("inputUnavailableCause").deepCopy());
                    } else if (!"succeeded".equals(task.get("state").getAsString()) || task.has("resultObservation"))
                        fact.addProperty("reason", "task_not_confirmed_successful");
                    else if (!receipt.has("observedAtCompletion")) fact.addProperty("reason", "completion_input_not_recorded");
                    else if (!receipt.get("inputUnchangedAtCompletion").getAsBoolean()) {
                        fact.addProperty("status", "changed_during_task"); fact.addProperty("reason", "inputs_changed_during_task");
                    } else { fact.addProperty("reason", "current_input_pending"); needsCurrent = true; }
                } catch (IOException | RuntimeException unavailable) {
                    // Old, missing or invalid identity receipts are uncertainty, never a current-input claim.
                    fact.addProperty("reason", "input_identity_unavailable");
                    fact.addProperty("detail", failureDetail(unavailable));
                }
            } else if (Files.exists(receiptPath)) {
                try {
                    JsonObject receipt = read(receiptPath, workspaceId, id, null);
                    fact.addProperty("reason", "input_identity_unavailable");
                    if (receipt.has("inputUnavailableCause")) fact.add("detail", receipt.get("inputUnavailableCause").deepCopy());
                } catch (IOException | RuntimeException unavailable) {
                    fact.addProperty("reason", "input_identity_unavailable"); fact.addProperty("detail", failureDetail(unavailable));
                }
            } else if (Files.exists(receiptPath.getParent()) && !Files.isDirectory(receiptPath.getParent())) {
                fact.addProperty("reason", "input_identity_unavailable"); fact.addProperty("detail", "Task input receipt storage is not a directory");
            }
            facts.add(id.toString(), fact);
        }
        String current = null, currentFailure = null;
        if (needsCurrent) {
            try { current = WorkspaceExecutionSnapshot.fingerprint(root, () -> false); }
            catch (IOException | RuntimeException unavailable) { currentFailure = failureDetail(unavailable); }
        }
        for (var entry : facts.entrySet()) {
            JsonObject fact = entry.getValue().getAsJsonObject();
            if ("current_input_pending".equals(fact.get("reason").getAsString())) {
                if (currentFailure != null) {
                    fact.addProperty("reason", "current_input_unavailable"); fact.addProperty("detail", currentFailure);
                } else {
                    boolean matches = fact.get("sourceSha256").getAsString().equals(current);
                    fact.addProperty("status", matches ? "current" : "changed");
                    fact.addProperty("reason", matches ? "input_identity_matches" : "inputs_changed_after_task");
                }
            }
        }
        JsonObject result = new JsonObject(); result.addProperty("workspaceId", workspaceId.toString());
        result.addProperty("observedAt", clock.instant().toString());
        result.add("currentSha256", current == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(current));
        result.add("tasks", facts);
        return result;
    }

    private static Path path(Path root, UUID taskId) throws IOException {
        Path checkedRoot = WorkspaceExecutionSnapshot.canonicalPath(root);
        Path target = checkedRoot.resolve(".copperbench/task-inputs/" + taskId + ".json");
        WorkspaceExecutionSnapshot.rejectLinks(target);
        return target;
    }

    private static JsonObject read(Path path, UUID workspaceId, UUID taskId, JsonObject snapshot) throws IOException {
        if (snapshot != null && (!snapshot.has("manifestPath") || !WorkspaceExecutionSnapshot.canonicalPath(Path.of(snapshot.get("manifestPath").getAsString())).equals(path)))
            throw new IOException("The task input identity is outside its recorded workspace location");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(MAX_RECEIPT_BYTES + 1); }
        if (bytes.length > MAX_RECEIPT_BYTES) throw new IOException("The task input identity exceeds its receipt limit");
        try {
            JsonObject value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"1.0".equals(value.get("schemaVersion").getAsString())
                    || !workspaceId.toString().equals(value.get("workspaceId").getAsString())
                    || !taskId.toString().equals(value.get("taskId").getAsString())
                    || !"after_generation_before_process".equals(value.get("boundary").getAsString())
                    || snapshot != null && !snapshot.equals(value.getAsJsonObject("sourceSnapshot")))
                throw new IOException("The task input identity does not match the original task");
            return value;
        } catch (RuntimeException invalid) { throw new IOException("The task input identity receipt is invalid", invalid); }
    }
}
