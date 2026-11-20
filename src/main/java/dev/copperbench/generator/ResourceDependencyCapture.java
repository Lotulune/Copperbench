package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Captures resolved runtime dependencies during explicit tasks, never from asset/health reads. */
public final class ResourceDependencyCapture {
    public static final String INDEX = ".copperbench/resource-index/runtime-dependencies.json";
    private static final String SCRIPT = ".copperbench/resource-index/capture.gradle";
    private ResourceDependencyCapture() {}

    public static Capture prepare(Path root, List<String> command, Consumer<String> log) {
        try {
            String generator = generator(root);
            if (generator == null) return null;
            Path directory = root.resolve(".copperbench/resource-index");
            WorkspaceExecutionSnapshot.rejectLinks(directory);
            Files.createDirectories(directory);
            Path script = root.resolve(SCRIPT);
            WorkspaceExecutionSnapshot.rejectLinks(script);
            try (var source = ResourceDependencyCapture.class.getResourceAsStream("/dev/copperbench/assets/capture-runtime-resources.gradle")) {
                if (source == null) throw new IOException("Resource capture script is unavailable");
                Files.write(script, source.readAllBytes());
            }
            String captureId = UUID.randomUUID().toString().replace("-", "");
            String capturePath = ".copperbench/resource-index/capture-" + captureId + ".json";
            String fingerprint = WorkspaceExecutionSnapshot.fingerprint(root, () -> Thread.currentThread().isInterrupted());
            // Read before the pending state below replaces the earlier index.
            var previous = previousHashes(root.resolve(INDEX));
            JsonObject pending = new JsonObject();
            pending.addProperty("state", "collecting"); pending.addProperty("generator", generator);
            pending.addProperty("inputSha256", fingerprint);
            write(root.resolve(INDEX), pending);
            command.add("--init-script"); command.add(SCRIPT);
            command.add("-PcopperbenchResourceSnapshotOutput=" + capturePath);
            command.add("-PcopperbenchResourceCaptureTask=copperbenchCaptureResourceContext" + captureId);
            command.add("copperbenchCaptureResourceContext" + captureId);
            return new Capture(root, capturePath, generator, fingerprint, log, java.util.Map.copyOf(previous));
        } catch (IOException | RuntimeException exception) {
            log.accept("RESOURCE_INDEX_UNAVAILABLE: runtime dependency capture could not be prepared (" + exception.getClass().getSimpleName() + ")");
            return null;
        }
    }

    public static String generator(Path root) throws IOException {
        try (var files = Files.list(root)) {
            var workspaces = files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".mcreator")).toList();
            if (workspaces.size() != 1 || Files.size(workspaces.getFirst()) > 16 * 1024 * 1024) return null;
            WorkspaceExecutionSnapshot.rejectLinks(workspaces.getFirst());
            var settings = JsonParser.parseString(Files.readString(workspaces.getFirst())).getAsJsonObject().getAsJsonObject("workspaceSettings");
            if (settings == null || !settings.has("currentGenerator")) return null;
            String generator = settings.get("currentGenerator").getAsString();
            return generator.matches("(?:fabric|neoforge)-(?:1\\.20\\.1|1\\.21\\.1|26\\.1\\.2|26\\.2)") ? generator : null;
        }
    }

    /** Hashes recorded by an earlier capture, keyed by real path, size and modification time. */
    static java.util.Map<String, String> previousHashes(Path index) {
        var result = new java.util.HashMap<String, String>();
        try {
            if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS) || Files.size(index) > 8 * 1024 * 1024) return result;
            JsonObject value = JsonParser.parseString(Files.readString(index)).getAsJsonObject();
            if (!value.has("artifacts") || !value.get("artifacts").isJsonArray()) return result;
            for (var raw : value.getAsJsonArray("artifacts")) {
                if (!raw.isJsonObject()) continue;
                JsonObject item = raw.getAsJsonObject();
                if (!item.has("path") || !item.has("sha256") || !item.has("size") || !item.has("lastModifiedMillis")) continue;
                String hash = item.get("sha256").getAsString();
                if (hash.matches("[0-9a-f]{64}"))
                    result.put(item.get("path").getAsString() + "|" + item.get("size").getAsLong() + "|"
                            + item.get("lastModifiedMillis").getAsLong(), hash);
            }
        } catch (IOException | RuntimeException ignored) {
            // An unreadable earlier index only means every archive is hashed again.
        }
        return result;
    }

    private static void write(Path file, JsonObject value) throws IOException {
        WorkspaceExecutionSnapshot.rejectLinks(file);
        Path pending = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".pending");
        Files.writeString(pending, value.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        try { Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    public record Capture(Path root, String capturePath, String generator, String fingerprint, Consumer<String> log,
            java.util.Map<String, String> previousHashes) {
        /** A failed, cancelled or unobserved task cannot establish a fresh dependency context. */
        public void finish(boolean succeeded) {
            JsonObject result = new JsonObject();
            result.addProperty("state", "unavailable"); result.addProperty("schemaVersion", "1.0");
            result.addProperty("generator", generator); result.addProperty("inputSha256", fingerprint);
            try {
                Path captured = root.resolve(capturePath);
                WorkspaceExecutionSnapshot.rejectLinks(captured);
                if (succeeded && Files.isRegularFile(captured) && Files.size(captured) < 8 * 1024 * 1024
                        && fingerprint.equals(WorkspaceExecutionSnapshot.fingerprint(root, () -> Thread.currentThread().isInterrupted()))) {
                    JsonObject data = JsonParser.parseString(Files.readString(captured)).getAsJsonObject();
                    JsonArray artifacts = data.getAsJsonArray("artifacts");
                    if (data.get("complete").getAsBoolean() && artifacts != null && artifacts.size() <= 4096) {
                        JsonArray verified = new JsonArray();
                        var seen = new java.util.HashSet<Path>();
                        var previous = previousHashes;
                        for (var raw : artifacts) {
                            JsonObject artifact = raw.getAsJsonObject();
                            Path path = Path.of(artifact.get("path").getAsString()).toRealPath();
                            // A dependency directory may contain another project's resources. Do not claim a complete JAR catalog for it.
                            if (Files.isDirectory(path)) throw new IOException("Runtime directory artifacts require an explicit resource context");
                            if (!path.getFileName().toString().endsWith(".jar") || !seen.add(path)) continue;
                            JsonObject item = artifact.deepCopy();
                            var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
                            long size = attributes.size(), modified = attributes.lastModifiedTime().toMillis();
                            // Dependency JARs rarely change between builds; rehashing about a hundred of them cost seconds on every
                            // build. Readers re-verify this hash before using an archive, so a stale entry fails safely.
                            String hash = previous.get(path.toString() + "|" + size + "|" + modified);
                            item.addProperty("path", path.toString());
                            item.addProperty("sha256", hash != null ? hash : WorkspaceExecutionSnapshot.sha256(path));
                            item.addProperty("size", size);
                            item.addProperty("lastModifiedMillis", modified);
                            verified.add(item);
                        }
                        if (fingerprint.equals(WorkspaceExecutionSnapshot.fingerprint(root, () -> Thread.currentThread().isInterrupted()))) {
                            result.add("artifacts", verified); result.add("configurations", data.get("configurations"));
                            result.addProperty("state", "available");
                        }
                    }
                }
                write(root.resolve(INDEX), result);
                log.accept("RESOURCE_INDEX_" + result.get("state").getAsString().toUpperCase(java.util.Locale.ROOT));
            } catch (IOException | RuntimeException exception) {
                // A resource-index failure never turns a successful mod build into a fabricated compiler failure.
				result.addProperty("state", "unavailable");
				result.remove("artifacts");
				result.remove("configurations");
                try { write(root.resolve(INDEX), result); } catch (IOException ignored) { /* Previous pending state remains unverified. */ }
                log.accept("RESOURCE_INDEX_UNAVAILABLE: " + exception.getClass().getSimpleName());
            }
        }
    }
}
