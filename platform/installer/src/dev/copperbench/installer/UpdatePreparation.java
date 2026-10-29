/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Downloads changed components and constructs a verified payload outside the running installation. */
public final class UpdatePreparation {
    private static final int ATTEMPTS = 4;
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)");
    @FunctionalInterface interface ConnectionSource { HttpURLConnection open(URI uri) throws IOException; }
    private static final ConnectionSource CONNECTIONS = uri -> (HttpURLConnection) uri.toURL().openConnection();
    private UpdatePreparation() {}
    @FunctionalInterface public interface Download { void fetch(String name, Path destination, long bytes) throws IOException; }
    @FunctionalInterface public interface ContentSource { InputStream open(URI uri) throws IOException; }
    @FunctionalInterface private interface TransferSource { Transfer open(long offset) throws IOException; }
    private record Transfer(InputStream input, boolean resumed) {}
    public static Properties installationOptions(Path target) throws IOException {
        PlatformInstall.Existing existing = PlatformInstall.detect();
        Properties options;
        if (Files.isRegularFile(target.resolve(InstallEngine.RECEIPT))) options = InstallEngine.readReceipt(target);
        else if (existing != null && existing.path().equals(target)) {
            options = new Properties(); options.setProperty("scope", existing.scope());
        } else throw new IOException("Install Copperbench with the graphical installer before using in-app upgrades.");
        options.setProperty("legacy", Boolean.toString(!Files.exists(target.resolve(InstallEngine.RECEIPT))));
        options.setProperty("deb", Boolean.toString(existing != null && existing.path().equals(target) && existing.deb()));
        options.setProperty("language", PlatformInstall.preference(target, "ui-language.txt", "install-language.txt", Set.of("zh", "en"), "zh"));
        options.setProperty("theme", PlatformInstall.preference(target, "ui-theme.txt", "install-theme.txt", Set.of("light", "dark"), "dark"));
        return options;
    }
    public static List<UpdateManifest.Part> changedParts(UpdateManifest manifest, Path installed, BooleanSupplier cancelled) throws IOException {
        List<UpdateManifest.Part> changed = new ArrayList<>();
        for (var part : manifest.parts()) {
            for (var entry : part.entries()) {
                check(cancelled);
                if (!UpdateManifest.matches(installed, entry)) { changed.add(part); break; }
            }
        }
        return List.copyOf(changed);
    }
    public static void prepare(UpdateManifest manifest, List<UpdateManifest.Part> changed, Path installed, Path scratch,
                               Download download, BooleanSupplier cancelled, Consumer<String> progress) throws IOException {
        InstallEngine.ensureNoLinks(scratch);
        Path payload = scratch.resolve("payload"); Files.createDirectory(payload);
        for (var part : manifest.parts()) {
            check(cancelled); boolean fetch = changed.contains(part);
            Path archive = scratch.resolve(part.name());
            if (fetch) {
                progress.accept(part.name()); download.fetch(part.name(), archive, part.bytes());
                if (Files.size(archive) != part.bytes() || !UpdateManifest.sha256(archive).equals(part.sha256())) throw new IOException("Update checksum mismatch: " + part.name());
            }
            try (ZipFile zip = fetch ? new ZipFile(archive.toFile()) : null) {
                Set<String> expected = new HashSet<>(); part.entries().stream().filter(e -> e.type().equals("F")).forEach(e -> expected.add(e.name()));
                if (zip != null) {
                    var entries = zip.entries(); Set<String> found = new HashSet<>();
                    while (entries.hasMoreElements()) { String name = entries.nextElement().getName(); if (!expected.contains(name) || !found.add(name)) throw new IOException("Unexpected update archive entry: " + name); }
                    if (!found.equals(expected)) throw new IOException("Incomplete update component: " + part.name());
                }
                for (var entry : part.entries()) {
                    check(cancelled);
                    Path output = payload.resolve(entry.name()); InstallEngine.ensureNoLinks(output.getParent()); Files.createDirectories(output.getParent());
                    switch (entry.type()) {
                        case "D" -> Files.createDirectories(output);
                        case "L" -> {
                            Path link = Path.of(new String(Base64.getDecoder().decode(entry.digest()), StandardCharsets.UTF_8));
                            if (link.isAbsolute() || !output.getParent().resolve(link).normalize().startsWith(payload)) throw new IOException("Unsafe update link");
                            Files.createSymbolicLink(output, link);
                        }
                        case "F" -> {
                            if (!fetch && !UpdateManifest.matches(installed, entry)) throw new IOException("Installed files changed while preparing the update. Check for updates again.");
                            try (InputStream in = fetch ? zip.getInputStream(zip.getEntry(entry.name())) : Files.newInputStream(installed.resolve(entry.name()));
                                 OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                                byte[] buffer = new byte[128 * 1024]; long copied = 0;
                                for (int n; (n = in.read(buffer)) >= 0;) {
                                    check(cancelled); copied += n; if (copied > entry.size()) throw new IOException("Update file exceeds declared size"); out.write(buffer, 0, n);
                                }
                                if (copied != entry.size()) throw new IOException("Incomplete update file: " + entry.name());
                            }
                            if (!UpdateManifest.sha256(output).equals(entry.digest())) throw new IOException("Update file checksum mismatch: " + entry.name());
                        }
                    }
                    if (!entry.type().equals("L")) InstallEngine.setPermissions(output, entry.permissions());
                }
            }
            Files.deleteIfExists(archive);
        }
        Files.writeString(scratch.resolve("payload.tsv"), manifest.inventory(), StandardCharsets.UTF_8);
    }
    public static InputStream open(URI uri) throws IOException {
        return open(uri, CONNECTIONS);
    }
    static InputStream open(URI uri, ConnectionSource source) throws IOException {
        for (int attempt = 1;; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = connect(uri, 0, source);
                return new ConnectionInputStream(connection, uri, source);
            } catch (IOException error) {
                if (connection != null) connection.disconnect();
                if (!retryable(error)) throw error;
                if (attempt >= ATTEMPTS) throw new IOException("Update connection attempts exhausted", error);
                pause(() -> false);
            }
        }
    }
    private static HttpURLConnection connect(URI uri, long offset, ConnectionSource source) throws IOException {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null) throw new IOException("Updates require HTTPS");
            HttpURLConnection connection = source.open(uri);
            connection.setConnectTimeout(20_000); connection.setReadTimeout(30_000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Copperbench-Updater");
            connection.setRequestProperty("Accept", uri.getHost().equals("api.github.com") ? "application/vnd.github+json" : "application/octet-stream");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location"); connection.disconnect();
                    if (location == null) throw new IOException("Update redirect has no location"); uri = uri.resolve(location); continue;
                }
                if (status != 200 && !(offset > 0 && status == 206)) throw new ResponseException(status);
                return connection;
            } catch (IOException | RuntimeException error) {
                connection.disconnect(); throw error;
            }
        }
        throw new IOException("Too many update redirects");
    }
    private static final class ConnectionInputStream extends FilterInputStream {
        private final HttpURLConnection connection;
        private final URI origin;
        private final ConnectionSource source;
        ConnectionInputStream(HttpURLConnection connection, URI origin, ConnectionSource source) throws IOException {
            super(connection.getInputStream()); this.connection = connection; this.origin = origin; this.source = source;
        }
        @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
    }
    public static void download(URI uri, Path file, long limit, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        downloadWithConnections(uri, file, limit, cancelled, progress, CONNECTIONS);
    }
    /** Retains the stream-source boundary used by installed update dialogs. */
    public static void download(URI uri, Path file, long limit, BooleanSupplier cancelled, LongConsumer progress, ContentSource source) throws IOException {
        ConnectionInputStream[] network = {null};
        transfer(file, limit, cancelled, progress, offset -> {
            if (network[0] != null) return networkTransfer(network[0].origin, offset, limit, network[0].source);
            InputStream input = source.open(uri);
            if (input instanceof ConnectionInputStream connected) network[0] = connected;
            return new Transfer(input, false);
        });
    }
    static void downloadWithConnections(URI uri, Path file, long limit, BooleanSupplier cancelled, LongConsumer progress, ConnectionSource source) throws IOException {
        transfer(file, limit, cancelled, progress, offset -> networkTransfer(uri, offset, limit, source));
    }
    private static Transfer networkTransfer(URI uri, long offset, long limit, ConnectionSource source) throws IOException {
        HttpURLConnection connection = connect(uri, offset, source);
        try {
            boolean resumed = connection.getResponseCode() == 206;
            if (resumed) validateRange(connection, offset, limit);
            else if (connection.getContentLengthLong() >= 0 && connection.getContentLengthLong() != limit)
                throw new IOException("Update download size does not match the manifest");
            return new Transfer(new ConnectionInputStream(connection, uri, source), resumed);
        } catch (IOException | RuntimeException error) { connection.disconnect(); throw error; }
    }
    private static void transfer(Path file, long limit, BooleanSupplier cancelled, LongConsumer progress, TransferSource source) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("Invalid update download size");
        check(cancelled);
        boolean created = false;
        try (SeekableByteChannel output = Files.newByteChannel(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            long reported = 0;
            byte[] buffer = new byte[128 * 1024];
            for (int attempt = 1;; attempt++) {
                check(cancelled);
                try {
                    Transfer transfer = source.open(output.position());
                    try (InputStream input = transfer.input()) {
                        if (!transfer.resumed()) { output.truncate(0); output.position(0); }
                        for (int n; (n = input.read(buffer)) >= 0;) {
                            check(cancelled);
                            if (n > limit - output.position()) throw new IOException("Update download exceeds declared size");
                            ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, n);
                            while (bytes.hasRemaining()) output.write(bytes);
                            if (output.position() > reported) {
                                progress.accept(output.position() - reported); reported = output.position();
                            }
                        }
                    }
                    if (output.position() != limit) throw new EOFException("Incomplete update download");
                    return;
                } catch (IOException error) {
                    if (attempt >= ATTEMPTS || !retryable(error)) throw error;
                    System.getLogger(UpdatePreparation.class.getName()).log(System.Logger.Level.INFO,
                            "Retrying update download at byte {0} after {1} (attempt {2}/{3})", output.position(), error.getClass().getSimpleName(), attempt + 1, ATTEMPTS);
                }
                pause(cancelled);
            }
        } catch (IOException | RuntimeException error) {
            if (created) try { Files.deleteIfExists(file); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }
    private static void validateRange(HttpURLConnection connection, long offset, long limit) throws IOException {
        var match = CONTENT_RANGE.matcher(Objects.toString(connection.getHeaderField("Content-Range"), ""));
        try {
            if (match.matches() && Long.parseLong(match.group(1)) == offset && Long.parseLong(match.group(3)) == limit) {
                long end = Long.parseLong(match.group(2));
                if (end >= offset && end < limit && (connection.getContentLengthLong() < 0 || connection.getContentLengthLong() == end - offset + 1)) return;
            }
        } catch (NumberFormatException ignored) { /* Invalid response, reported below. */ }
        throw new IOException("Update server returned an invalid download range");
    }
    private static boolean retryable(IOException error) {
        return error instanceof SocketException || error instanceof SocketTimeoutException || error instanceof EOFException
                || error instanceof ResponseException response && (response.status == 429 || response.status >= 500 && response.status <= 599);
    }
    private static void pause(BooleanSupplier cancelled) throws IOException {
        for (int i = 0; i < 10; i++) {
            check(cancelled);
            try { Thread.sleep(100); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Update interrupted"); }
        }
        check(cancelled);
    }
    private static final class ResponseException extends IOException {
        private final int status;
        ResponseException(int status) { super("Update server returned HTTP " + status); this.status = status; }
    }
    private static void check(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) throw new CancellationException(); }
}
