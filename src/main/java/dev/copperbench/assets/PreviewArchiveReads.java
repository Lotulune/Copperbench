package dev.copperbench.assets;

import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.zip.ZipFile;

/** One preview's archive reads. No result may escape the scope until close verifies every archive again. */
final class PreviewArchiveReads implements AutoCloseable {
    @FunctionalInterface interface Hasher { String hash(Path path) throws IOException; }
    private record Identity(Path realPath, Object fileKey, long size, FileTime modified) {
        static Identity read(Path path) throws IOException {
            Path real = path.toRealPath();
            BasicFileAttributes attributes = Files.readAttributes(real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) throw new IOException("Resource archive is not a regular file");
            return new Identity(real, attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
        }
    }
    private record Opened(Path path, String sha256, Identity identity, ZipFile zip) {}
    private final Hasher hasher;
    private final Map<Path, Opened> opened = new LinkedHashMap<>();
    private boolean closed;

    PreviewArchiveReads() { this(WorkspaceExecutionSnapshot::sha256); }
    PreviewArchiveReads(Hasher hasher) { this.hasher = Objects.requireNonNull(hasher); }

    byte[] read(Path archive, String expectedSha256, String resource, int limit) throws IOException {
        if (closed) throw new IOException("Preview archive scope is closed");
        if (limit < 0 || limit == Integer.MAX_VALUE) throw new IOException("Invalid resource size limit");
        if (resource == null || !resource.startsWith("assets/") || resource.length() > 4096
                || resource.indexOf('\\') >= 0 || resource.indexOf(':') >= 0
                || Arrays.stream(resource.split("/", -1)).anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals("..")))
            throw new IOException("Invalid archive resource path");
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) throw new IOException("Invalid resource archive identity");
        Path path = archive.toAbsolutePath().normalize();
        Opened entry = opened.get(path);
        if (entry == null) {
            Identity identity = Identity.read(path);
            if (!expectedSha256.equals(hasher.hash(path)) || !identity.equals(Identity.read(path)))
                throw new IOException("Resource archive changed before preview reads");
            ZipFile zip = new ZipFile(identity.realPath().toFile());
            try {
                if (!identity.equals(Identity.read(path))) throw new IOException("Resource archive changed while opening it");
                entry = new Opened(path, expectedSha256, identity, zip);
                opened.put(path, entry);
            } catch (IOException | RuntimeException error) {
                try { zip.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
        } else if (!entry.sha256().equals(expectedSha256) || !entry.identity().equals(Identity.read(path))) {
            throw new IOException("Resource archive identity changed during preview reads");
        }
        var file = entry.zip().getEntry(resource);
        if (file == null || file.isDirectory()) throw new IOException("Resource disappeared from its archive");
        if (file.getSize() > limit) throw new IOException("Texture exceeds the preview limit");
        byte[] bytes;
        try (var input = entry.zip().getInputStream(file)) { bytes = input.readNBytes(limit + 1); }
        if (bytes.length > limit) throw new IOException("Texture exceeds the preview limit");
        return bytes;
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        for (Opened entry : opened.values()) {
            try {
                entry.zip().close();
                if (!entry.identity().equals(Identity.read(entry.path()))) throw new IOException("Resource archive identity changed after preview reads");
                // The full digest is always recomputed, even when size, timestamps and file key are unchanged.
                if (!entry.sha256().equals(hasher.hash(entry.path())) || !entry.identity().equals(Identity.read(entry.path())))
                    throw new IOException("Resource archive changed after preview reads");
            } catch (IOException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        opened.clear();
        if (failure != null) throw failure;
    }
}
