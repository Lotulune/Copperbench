/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.release;

import dev.copperbench.installer.InstallEngine;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;

/** Keeps small local diagnostic reports after the large prepared update is removed. */
final class UpdateLogArchive {
    static final String HEADER = "Copperbench update diagnostics v1";
    static final int MAX_LOG_BYTES = 1024 * 1024;
    static final int KEEP_REPORTS = 5;
    private record Report(Path path, FileTime time) { }

    static Path write(Path cache, Path prepared, Properties result) throws IOException {
        cache = cache.toAbsolutePath().normalize();
        prepared = prepared.toAbsolutePath().normalize();
        if (!cache.equals(prepared.getParent()) || !prepared.getFileName().toString().matches("update-[A-Za-z0-9_-]+"))
            throw new IOException("Invalid completed update location");
        InstallEngine.ensureNoLinks(prepared);
        Path receipt = prepared.resolve("finished.properties");
        InstallEngine.ensureNoLinks(receipt);
        if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing completed update receipt");
        Path history = cache.resolve("history");
        InstallEngine.ensureNoLinks(history);
        Files.createDirectories(history);
        Path requestFile = prepared.resolve("request.properties");
        Properties request = new Properties();
        if (Files.exists(requestFile, LinkOption.NOFOLLOW_LINKS)) {
            InstallEngine.ensureNoLinks(requestFile);
            try (var input = Files.newInputStream(requestFile, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(64 * 1024 + 1);
                if (bytes.length > 64 * 1024) throw new IOException("Update request is too large to archive");
                request.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            }
        }
        Path report = history.resolve(prepared.getFileName() + ".log");
        InstallEngine.ensureNoLinks(report);
        Path temporary = Files.createTempFile(history, ".update-report-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER + "\nrecordedAtUtc=" + Instant.now() + "\n");
                writer.write("updateId=" + prepared.getFileName() + "\n");
                // Never copy arbitrary request properties or connection credentials.
                for (String key : List.of("version", "target", "scope", "language", "theme", "parentPid", "legacy", "deb"))
                    field(writer, "request." + key, request.getProperty(key));
                for (String key : List.of("pid", "status", "message", "cleanupPending"))
                    field(writer, "result." + key, result.getProperty(key));
                for (String name : List.of("update-steps.log", "update.log")) {
                    writer.write("\n--- " + name + " ---\n");
                    writer.write(readLog(prepared.resolve(name)));
                    writer.write("\n");
                }
            }
            try { Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING); }
            Files.setLastModifiedTime(report, Files.getLastModifiedTime(receipt, LinkOption.NOFOLLOW_LINKS));
        } finally { Files.deleteIfExists(temporary); }
        prune(history);
        return report;
    }

    private static void field(Writer writer, String name, String value) throws IOException {
        if (value == null) return;
        if (value.length() > 4096) value = value.substring(0, 4096) + " [truncated]";
        writer.write(name + "=" + value.replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\t", "\\t") + "\n");
    }

    private static String readLog(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return "[not available]";
        InstallEngine.ensureNoLinks(file);
        try (var input = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = input.size();
            boolean truncated = size > MAX_LOG_BYTES;
            input.position(Math.max(0, size - MAX_LOG_BYTES));
            ByteBuffer bytes = ByteBuffer.allocate((int) Math.min(size, MAX_LOG_BYTES));
            while (bytes.hasRemaining() && input.read(bytes) != -1) { }
            String text = new String(bytes.array(), 0, bytes.position(), StandardCharsets.UTF_8);
            if (truncated) {
                int line = text.indexOf('\n');
                if (line >= 0) text = text.substring(line + 1);
                return "[earlier output omitted; retained tail of at most 1 MiB]\n" + text;
            }
            return text;
        }
    }

    private static void prune(Path history) throws IOException {
        List<Report> reports = new ArrayList<>();
        try (var paths = Files.list(history)) {
            for (Path path : paths.toList()) {
                if (!path.getFileName().toString().matches("update-[A-Za-z0-9_-]+\\.log")
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                InstallEngine.ensureNoLinks(path);
                byte[] header;
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { header = input.readNBytes(HEADER.length() + 1); }
                if (!new String(header, StandardCharsets.UTF_8).equals(HEADER + "\n")) continue;
                reports.add(new Report(path, Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)));
            }
        }
        reports.sort(Comparator.comparing(Report::time).thenComparing(r -> r.path().getFileName().toString()));
        for (int i = 0; i < reports.size() - KEEP_REPORTS; i++) {
            InstallEngine.ensureNoLinks(reports.get(i).path());
            Files.deleteIfExists(reports.get(i).path());
        }
    }
}
