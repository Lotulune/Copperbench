/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Objects;

/** Local update diagnostics. Only explicitly selected fields enter this log. */
final class InstallTrace {
    static final InstallTrace NONE = new InstallTrace(null);
    private static final int MAX_WORKER_LOG_BYTES = 1024 * 1024;
    private final Path file;
    private boolean unavailable;
    private String lastPhase;

    InstallTrace(Path file) { this.file = file; }

    synchronized void event(String event, String... fields) {
        if (file == null || unavailable) return;
        if (fields.length % 2 != 0) throw new IllegalArgumentException("Trace fields require key/value pairs");
        StringBuilder line = new StringBuilder(Instant.now().toString()).append('\t').append(event)
                .append("\tpid=").append(ProcessHandle.current().pid());
        for (int i = 0; i < fields.length; i += 2)
            line.append('\t').append(fields[i]).append('=').append(escape(fields[i + 1]));
        line.append('\n');
        try {
            InstallEngine.ensureNoLinks(file);
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException error) {
            unavailable = true;
            System.err.println("Could not write update diagnostics: " + error);
        }
    }

    synchronized void phase(InstallEngine.Progress progress) {
        if (!Objects.equals(lastPhase, progress.phase())) {
            lastPhase = progress.phase();
            event("phase", "name", progress.phase(), "completed", Long.toString(progress.completed()),
                    "total", Long.toString(progress.total()));
        }
    }

    void failure(String operation, Throwable error) {
        event("failure", "operation", operation, "type", error.getClass().getName(), "message", error.getMessage());
    }

    void relayWorkerLog(Path workerLog) {
        if (file == null || !Files.exists(workerLog, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            InstallEngine.ensureNoLinks(workerLog);
            try (var input = Files.newInputStream(workerLog, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_WORKER_LOG_BYTES + 1);
                System.out.println("--- Elevated update worker diagnostics (UTC) ---");
                System.out.print(new String(bytes, 0, Math.min(bytes.length, MAX_WORKER_LOG_BYTES), StandardCharsets.UTF_8));
                if (bytes.length > MAX_WORKER_LOG_BYTES) System.out.println("\n[Worker diagnostics truncated at 1 MiB]");
                System.out.println("--- End elevated worker diagnostics ---");
            }
        } catch (IOException | SecurityException error) {
            failure("read-worker-diagnostics", error);
            System.err.println("Could not read elevated update diagnostics: " + error);
        }
    }

    static String escape(String value) {
        String text = Objects.toString(value, "");
        if (text.length() > 4096) text = text.substring(0, 4096) + " [truncated]";
        return text.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
