package dev.copperbench.generator;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Freezes real workspace inputs without copying caches, credentials or player worlds. */
public final class WorkspaceExecutionSnapshot {
    private static final Set<String> EXCLUDED_ROOTS = Set.of(
            ".git", ".gradle", ".copperbench", ".idea", ".vscode", "build", "out", "run", "runs",
            "logs", "node_modules", ".env");
    private static final Set<String> EXCLUDED_ANYWHERE = Set.of(".git", ".gradle", "node_modules");
    private static final Set<String> LEGACY_RUNTIME_ENTRIES = Set.of("localHistory", "workspaceBackups", "userSettings");
    private static final int MAX_FILES = 100_000;
    private static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;

    private WorkspaceExecutionSnapshot() {}

    public record FileEntry(String path, long bytes, String sha256) {}
    public record Snapshot(Path root, Path manifestPath, String sha256, List<FileEntry> files) {
        public JsonObject projection() {
            JsonObject result = new JsonObject();
            result.addProperty("sha256", sha256);
            result.addProperty("fileCount", files.size());
            result.addProperty("bytes", files.stream().mapToLong(FileEntry::bytes).sum());
            result.addProperty("manifestPath", manifestPath.toString());
            return result;
        }
    }

    public static final class SnapshotException extends IOException {
        private final String code;
        SnapshotException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    public static Snapshot capture(Path source, Path target, UUID workspaceId, long revision, Clock clock,
            BooleanSupplier cancelled) throws IOException {
        try {
            return captureInputs(source, target, workspaceId, revision, clock, cancelled);
        } catch (java.nio.file.NoSuchFileException disappeared) {
            throw changed();
        }
    }

    private static Snapshot captureInputs(Path source, Path target, UUID workspaceId, long revision, Clock clock,
            BooleanSupplier cancelled) throws IOException {
        Path sourceRoot = source.toAbsolutePath().normalize();
        Path targetRoot = target.toAbsolutePath().normalize();
        if (sourceRoot.equals(targetRoot)) throw unsafe("Snapshot destination must differ from the workspace");
        rejectLinks(sourceRoot);
        rejectLinks(targetRoot);
        if (Files.exists(targetRoot, LinkOption.NOFOLLOW_LINKS))
            throw unsafe("Snapshot destination already exists");
        List<FileEntry> before = inventory(sourceRoot, cancelled);
        Files.createDirectories(targetRoot);
        for (FileEntry entry : before) {
            checkCancelled(cancelled);
            Path from = sourceRoot.resolve(entry.path());
            Path to = targetRoot.resolve(entry.path());
            rejectLinks(from);
            rejectLinks(to);
            Files.createDirectories(to.getParent());
            try (InputStream input = Files.newInputStream(from, LinkOption.NOFOLLOW_LINKS);
                 OutputStream output = Files.newOutputStream(to, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[64 * 1024];
                long copied = 0;
                for (int count; (count = input.read(buffer)) >= 0;) {
                    checkCancelled(cancelled);
                    copied += count;
                    if (copied > entry.bytes()) throw changed();
                    output.write(buffer, 0, count);
                }
                if (copied != entry.bytes()) throw changed();
            }
            if (!entry.sha256().equals(sha256(to))) throw changed();
            if (entry.path().equals("gradlew"))
                dev.copperbench.platform.ExecutableFilePermissions.ensureOwnerExecutable(to);
        }
        if (!before.equals(inventory(sourceRoot, cancelled))) throw changed();
        String fingerprint = manifestHash(before);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schemaVersion", "1.0");
        manifest.addProperty("workspaceId", workspaceId.toString());
        manifest.addProperty("revision", revision);
        manifest.addProperty("capturedAt", clock.instant().toString());
        manifest.addProperty("sha256", fingerprint);
        JsonArray files = new JsonArray();
        before.forEach(entry -> {
            JsonObject file = new JsonObject();
            file.addProperty("path", entry.path()); file.addProperty("bytes", entry.bytes());
            file.addProperty("sha256", entry.sha256()); files.add(file);
        });
        manifest.add("files", files);
        Path manifestPath = targetRoot.resolveSibling("source-manifest.json");
        rejectLinks(manifestPath);
        Files.writeString(manifestPath, new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return new Snapshot(targetRoot, manifestPath, fingerprint, List.copyOf(before));
    }

    static List<FileEntry> inventory(Path root, BooleanSupplier cancelled) throws IOException {
        List<FileEntry> result = new ArrayList<>();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        rejectLinks(root);
        long[] total = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                checkCancelled(cancelled);
                if (!directory.equals(root) && excluded(root.relativize(directory))) return FileVisitResult.SKIP_SUBTREE;
                if (attributes.isSymbolicLink()) throw unsafe("Snapshot input contains a symbolic link: " + directory);
                rejectLinks(directory);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                checkCancelled(cancelled);
                Path relative = root.relativize(file);
                if (excluded(relative)) return FileVisitResult.CONTINUE;
                if (!attributes.isRegularFile() || attributes.isSymbolicLink())
                    throw unsafe("Snapshot input is not a regular file: " + relative);
                rejectLinks(file);
                total[0] += attributes.size();
                if (result.size() >= MAX_FILES || total[0] > MAX_BYTES)
                    throw new SnapshotException("WORKSPACE_SNAPSHOT_TOO_LARGE", "Snapshot exceeds 100,000 files or 2 GiB");
                result.add(new FileEntry(relative.toString().replace('\\', '/'), attributes.size(), sha256(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort(Comparator.comparing(FileEntry::path));
        return List.copyOf(result);
    }

    public static String fingerprint(Path root, BooleanSupplier cancelled) throws IOException {
        return manifestHash(inventory(root.toAbsolutePath().normalize(), cancelled));
    }

    private static boolean excluded(Path relative) {
        if (relative.getNameCount() == 0) return false;
        String first = relative.getName(0).toString();
        if (EXCLUDED_ROOTS.contains(first) || first.startsWith(".env.")) return true;
        if (first.equals(".mcreator") && relative.getNameCount() > 1
                && LEGACY_RUNTIME_ENTRIES.contains(relative.getName(1).toString())) return true;
        for (Path component : relative) if (EXCLUDED_ANYWHERE.contains(component.toString())) return true;
        return false;
    }

    public static void rejectLinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path current = absolute; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw unsafe("Symbolic links are not allowed: " + current);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && (!current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS))
                    || Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()))
                throw unsafe("A path redirects outside its declared location: " + current);
        }
    }

    /** Resolves ordinary Windows short-name aliases while rejecting symbolic links and junctions. */
    public static Path canonicalPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        rejectLinks(absolute);
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        return existing == null ? absolute : existing.toRealPath(LinkOption.NOFOLLOW_LINKS).resolve(existing.relativize(absolute)).normalize();
    }

    public static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            long bytes = 0;
            for (int count; (count = input.read(buffer)) >= 0;) {
                if (Thread.currentThread().isInterrupted()) throw new SnapshotException("WORKSPACE_SNAPSHOT_CANCELLED", "Snapshot hashing was cancelled");
                bytes += count;
                if (bytes > MAX_BYTES) throw new SnapshotException("WORKSPACE_SNAPSHOT_TOO_LARGE", "A hashed input exceeds 2 GiB");
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String manifestHash(List<FileEntry> files) {
        MessageDigest digest = digest();
        for (FileEntry file : files) digest.update((file.path() + "\0" + file.bytes() + "\0" + file.sha256() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void checkCancelled(BooleanSupplier cancelled) throws SnapshotException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
            throw new SnapshotException("WORKSPACE_SNAPSHOT_CANCELLED", "Snapshot capture was cancelled");
    }
    private static SnapshotException unsafe(String message) {
        return new SnapshotException("WORKSPACE_SNAPSHOT_UNSAFE_PATH", message);
    }
    private static SnapshotException changed() {
        return new SnapshotException("WORKSPACE_SNAPSHOT_CHANGED", "Workspace files changed during snapshot capture; retry from current files");
    }
}
