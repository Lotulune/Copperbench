package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class UpdateDownloadTest {
    private static final URI RELEASE = URI.create("https://updates.example.test/release.zip");
    @TempDir Path directory;

    @Test void resumesAfterAReadTimeoutAndRefreshesTheOriginalRedirect() throws Exception {
        var firstRedirect = response(302, "").header("Location", "https://cdn.example.test/first");
        var interrupted = new Response(200, interrupted("abc")).header("Content-Length", "6");
        var refreshedRedirect = response(302, "").header("Location", "https://cdn.example.test/refreshed");
        var resumed = response(206, "def").header("Content-Range", "bytes 3-5/6").header("Content-Length", "3");
        var source = new Source(firstRedirect, interrupted, refreshedRedirect, resumed);
        var progress = new AtomicLong();
        Path file = directory.resolve("resume.zip");
        UpdatePreparation.downloadWithConnections(RELEASE, file, 6, () -> false, progress::addAndGet, source);
        assertEquals("abcdef", Files.readString(file));
        assertEquals(6, progress.get());
        assertEquals(List.of(RELEASE, URI.create("https://cdn.example.test/first"), RELEASE,
                URI.create("https://cdn.example.test/refreshed")), source.requested);
        assertEquals("bytes=3-", refreshedRedirect.getRequestProperty("Range"));
        assertEquals("bytes=3-", resumed.getRequestProperty("Range"));
        assertTrue(interrupted.disconnected);
        assertTrue(resumed.disconnected);
    }

    @Test void restartsCleanlyWhenTheServerIgnoresRangeWithoutDoubleCountingProgress() throws Exception {
        var interrupted = new Response(200, interrupted("abc"));
        var restarted = response(200, "abcdef");
        Path file = directory.resolve("restart.zip");
        var progress = new AtomicLong();
        UpdatePreparation.downloadWithConnections(RELEASE, file, 6, () -> false, progress::addAndGet, new Source(interrupted, restarted));
        assertEquals("bytes=3-", restarted.getRequestProperty("Range"));
        assertEquals("abcdef", Files.readString(file));
        assertEquals(6, progress.get());
    }

    @Test void retriesAnEarlyEofAndAnInitialConnectionFailure() throws Exception {
        var partial = response(200, "abc").header("Content-Length", "6");
        var remainder = response(206, "def").header("Content-Range", "bytes 3-5/6");
        var source = new Source(partial, remainder);
        AtomicInteger attempts = new AtomicInteger();
        Path file = directory.resolve("connection.zip");
        UpdatePreparation.downloadWithConnections(RELEASE, file, 6, () -> false, ignored -> {}, uri -> {
            if (attempts.getAndIncrement() == 0) throw new ConnectException("connection interrupted");
            return source.open(uri);
        });
        assertEquals(3, attempts.get());
        assertEquals("abcdef", Files.readString(file));
    }

    @Test void rejectsInconsistentRangesWithoutWritingAnInvalidArchive() throws Exception {
        for (String range : List.of("bytes 2-5/6", "bytes 3-5/7", "bytes 3-6/6", "invalid")) {
            var partial = new Response(200, interrupted("abc"));
            var wrong = response(206, "def").header("Content-Range", range);
            var source = new Source(partial, wrong);
            Path file = directory.resolve("range-" + source.hashCode());
            IOException error = assertThrows(IOException.class, () ->
                    UpdatePreparation.downloadWithConnections(RELEASE, file, 6, () -> false, ignored -> {}, source));
            assertTrue(error.getMessage().contains("invalid download range"));
            assertEquals(2, source.requested.size());
            assertFalse(Files.exists(file));
        }
    }

    @Test void boundsRetriesAndRemovesItsPartialFile() {
        var source = new Source(response(503, ""), response(503, ""), response(503, ""), response(503, ""));
        Path file = directory.resolve("unavailable.zip");
        IOException error = assertThrows(IOException.class, () ->
                UpdatePreparation.downloadWithConnections(RELEASE, file, 6, () -> false, ignored -> {}, source));
        assertTrue(error.getMessage().contains("503"));
        assertEquals(4, source.requested.size());
        assertFalse(Files.exists(file));
    }

    @Test void doesNotRetryPermanentHttpErrorsOrOverwriteAnExistingFile() throws Exception {
        var source = new Source(response(404, ""));
        Path missing = directory.resolve("missing.zip");
        assertThrows(IOException.class, () ->
                UpdatePreparation.downloadWithConnections(RELEASE, missing, 6, () -> false, ignored -> {}, source));
        assertEquals(1, source.requested.size());
        assertFalse(Files.exists(missing));
        Path existing = directory.resolve("existing.zip");
        Files.writeString(existing, "user data");
        assertThrows(FileAlreadyExistsException.class, () ->
                UpdatePreparation.downloadWithConnections(RELEASE, existing, 6, () -> false, ignored -> {}, source));
        assertEquals("user data", Files.readString(existing));
        assertEquals(1, source.requested.size());
    }

    @Test void cancellationStopsRetryingAndRemovesItsPartialFile() {
        var cancelled = new AtomicBoolean();
        AtomicInteger attempts = new AtomicInteger();
        Path file = directory.resolve("cancelled.zip");
        assertThrows(CancellationException.class, () -> UpdatePreparation.downloadWithConnections(RELEASE, file, 6,
                cancelled::get, ignored -> {}, uri -> {
                    attempts.incrementAndGet(); cancelled.set(true); return response(503, "");
                }));
        assertEquals(1, attempts.get());
        assertFalse(Files.exists(file));
    }

    @Test void metadataConnectionsRetryTransientFailuresAndRejectHttpsDowngrades() throws Exception {
        var first = response(502, "");
        var success = response(200, "manifest");
        var source = new Source(first, success);
        try (InputStream input = UpdatePreparation.open(RELEASE, source)) {
            assertEquals("manifest", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertTrue(first.disconnected); assertTrue(success.disconnected);
        var downgrade = new Source(response(302, "").header("Location", "http://updates.example.test/unsafe"));
        assertThrows(IOException.class, () -> UpdatePreparation.open(RELEASE, downgrade));
        assertEquals(1, downgrade.requested.size());
    }

    @Test void rejectsOversizedDownloadsAndPreservesManifestSizeValidation() {
        Path file = directory.resolve("oversized.zip");
        assertThrows(IOException.class, () -> UpdatePreparation.downloadWithConnections(RELEASE, file, 3, () -> false,
                ignored -> {}, new Source(response(200, "abcdef"))));
        assertFalse(Files.exists(file));
    }

    @Test void installedDialogStreamSourceRetainsNetworkResumeSupport() throws Exception {
        var interrupted = new Response(200, interrupted("abc")).header("Content-Length", "6");
        var resumed = response(206, "def").header("Content-Range", "bytes 3-5/6");
        var connections = new Source(interrupted, resumed);
        UpdatePreparation.ContentSource source = uri -> UpdatePreparation.open(uri, connections);
        Path file = directory.resolve("installed-dialog.zip");
        UpdatePreparation.download(RELEASE, file, 6, () -> false, ignored -> {}, source);
        assertEquals("abcdef", Files.readString(file));
        assertEquals("bytes=3-", resumed.getRequestProperty("Range"));
        assertTrue(interrupted.disconnected); assertTrue(resumed.disconnected);
        assertDoesNotThrow(() -> Class.forName("dev.copperbench.shell.ProductUpdateDialog").getDeclaredMethods());
    }

    @Test void injectedStreamsRestartWithoutDoubleCountingAfterAnInterruption() throws Exception {
        var streams = new ArrayDeque<InputStream>(List.of(interrupted("abc"), new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8))));
        UpdatePreparation.ContentSource source = uri -> streams.removeFirst();
        var progress = new AtomicLong();
        Path file = directory.resolve("injected-source.zip");
        UpdatePreparation.download(RELEASE, file, 6, () -> false, progress::addAndGet, source);
        assertEquals("abcdef", Files.readString(file));
        assertEquals(6, progress.get());
        assertTrue(streams.isEmpty());
    }

    private static InputStream interrupted(String prefix) {
        return new InputStream() {
            private final ByteArrayInputStream input = new ByteArrayInputStream(prefix.getBytes(StandardCharsets.UTF_8));
            @Override public int read() throws IOException {
                int value = input.read();
                if (value < 0) throw new SocketTimeoutException("interrupted transfer");
                return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int count = input.read(bytes, offset, length);
                if (count < 0) throw new SocketTimeoutException("interrupted transfer");
                return count;
            }
        };
    }
    private static Response response(int status, String body) {
        return new Response(status, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
    private static URL testUrl() {
        try { return RELEASE.toURL(); }
        catch (MalformedURLException error) { throw new AssertionError(error); }
    }
    private static final class Source implements UpdatePreparation.ConnectionSource {
        final List<URI> requested = new ArrayList<>();
        final Deque<Response> responses;
        Source(Response... responses) { this.responses = new ArrayDeque<>(List.of(responses)); }
        @Override public HttpURLConnection open(URI uri) { requested.add(uri); return responses.removeFirst(); }
    }
    private static final class Response extends HttpURLConnection {
        final int status;
        final InputStream body;
        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        boolean disconnected;
        Response(int status, InputStream body) {
            super(testUrl()); this.status = status; this.body = body;
        }
        Response header(String name, String value) { headers.put(name, value); return this; }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { return body; }
        @Override public String getHeaderField(String name) { return headers.get(name); }
        @Override public long getContentLengthLong() { return headers.containsKey("Content-Length") ? Long.parseLong(headers.get("Content-Length")) : -1; }
        @Override public void connect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { disconnected = true; }
    }
}
