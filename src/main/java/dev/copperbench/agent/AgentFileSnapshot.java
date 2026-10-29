package dev.copperbench.agent;

import com.google.gson.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Read-only, bounded working-tree snapshots. Never checks out, resets, stages or restores files. */
final class AgentFileSnapshot {
    static final int MAX_FILES = 2000, MAX_TEXT_BYTES = 128 * 1024, MAX_TOTAL_BYTES = 4 * 1024 * 1024;
    private static final Set<String> EXCLUDED = Set.of(".git", ".gradle", ".copperbench", "node_modules", "build", "dist", "output", "run", "logs", ".idea");
    private static boolean secret(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith(".env") || lower.equals("credentials.json") || lower.equals("auth.json")
                || lower.endsWith(".pem") || lower.endsWith(".key") || lower.endsWith(".keystore") || lower.startsWith("id_rsa");
    }

    static JsonObject capture(Path root) throws IOException {
        JsonObject files = new JsonObject(); int[] bytes = {0}, omitted = {0}; boolean[] limited = {false};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                return !directory.equals(root) && EXCLUDED.contains(directory.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException failure) { omitted[0]++; limited[0] = true; return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile() || Files.isSymbolicLink(file) || secret(file.getFileName().toString())) { omitted[0]++; return FileVisitResult.CONTINUE; }
                if (files.size() >= MAX_FILES) { limited[0] = true; return FileVisitResult.TERMINATE; }
                String path = root.relativize(file).toString().replace('\\', '/');
                JsonObject value = AgentJson.object("size", attrs.size(), "modifiedAt", attrs.lastModifiedTime().toMillis());
                try {
                    if (attrs.size() > MAX_TEXT_BYTES || bytes[0] + attrs.size() > MAX_TOTAL_BYTES) {
                        value.addProperty("omitted", "size_limit"); limited[0] = true;
                    } else {
                        byte[] content;
                        try (var stream = Files.newInputStream(file)) { content = stream.readNBytes(MAX_TEXT_BYTES + 1); }
                        if (content.length > MAX_TEXT_BYTES) { value.addProperty("omitted", "size_limit"); limited[0] = true; }
                        else {
                            bytes[0] += content.length; value.addProperty("hash", hash(content));
                            try {
                                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
                                if (text.indexOf('\0') >= 0) value.addProperty("omitted", "binary");
                                else value.addProperty("text", AgentJson.redacted(text));
                            } catch (CharacterCodingException binary) { value.addProperty("omitted", "binary"); }
                        }
                    }
                } catch (IOException failure) { value.addProperty("omitted", "unreadable"); limited[0] = true; }
                files.add(path, value); return FileVisitResult.CONTINUE;
            }
        });
        // mtime alone is deliberately only a partial observation for oversized/unreadable files.
        StringBuilder identity = new StringBuilder();
        new TreeSet<>(files.keySet()).forEach(path -> {
            JsonObject file = files.getAsJsonObject(path);
            identity.append(path).append('\0').append(file.has("hash") ? file.get("hash") : file.toString()).append('\n');
        });
        return AgentJson.object("capturedAt", System.currentTimeMillis(), "files", files, "fingerprint", hash(identity.toString().getBytes(StandardCharsets.UTF_8)),
                "coverage", limited[0] ? "partial" : "complete", "excludedFiles", omitted[0],
                "scope", "Regular project files; excludes .git, .gradle, .copperbench, node_modules, build, dist, output, run, logs, .idea, symlinks and credential files. Text: 128 KiB/file, 4 MiB total, 2000 files. Binary files: hash only; larger files: size/mtime only.");
    }

    static JsonObject changes(JsonObject before, JsonObject after) {
        JsonObject left = AgentJson.child(before, "files"), right = AgentJson.child(after, "files");
        Set<String> paths = new TreeSet<>(left.keySet()); paths.addAll(right.keySet()); JsonArray changes = new JsonArray();
        for (String path : paths) {
            JsonObject a = AgentJson.child(left, path), b = AgentJson.child(right, path);
            boolean same = a.has("hash") && b.has("hash") ? a.get("hash").equals(b.get("hash")) : a.equals(b);
            if (same) continue;
            boolean unknownBefore = !left.has(path) && !AgentJson.string(before, "coverage").equals("complete");
            boolean unknownAfter = !right.has(path) && !AgentJson.string(after, "coverage").equals("complete");
            if (unknownBefore) a = AgentJson.object("omitted", "outside_capture");
            if (unknownAfter) b = AgentJson.object("omitted", "outside_capture");
            changes.add(AgentJson.object("path", path, "kind", unknownBefore || unknownAfter ? "unobserved" : !left.has(path) ? "added" : !right.has(path) ? "deleted" : "modified", "before", a, "after", b));
        }
        return AgentJson.object("coverage", AgentJson.string(before, "coverage").equals("complete") && AgentJson.string(after, "coverage").equals("complete") ? "complete" : "partial",
                "attribution", "mixed_or_unknown", "capturedAt", after.get("capturedAt"), "scope", after.get("scope"),
                "baselineFingerprint", before.get("fingerprint"), "finalFingerprint", after.get("fingerprint"), "files", changes);
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
