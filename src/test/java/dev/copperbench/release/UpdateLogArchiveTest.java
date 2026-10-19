package dev.copperbench.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class UpdateLogArchiveTest {
    @TempDir Path cache;

    private Path prepared(String id) throws IOException {
        Path path = Files.createDirectory(cache.resolve("update-" + id));
        Files.writeString(path.resolve("request.properties"), "version=1.0.0\nscope=system\ntarget=app\nmcpToken=do-not-copy-this\n");
        Files.writeString(path.resolve("finished.properties"), "pid=123\nstatus=success\n");
        Files.writeString(path.resolve("update-steps.log"), "helper-started mode=/ELEVATE childPid=456\n");
        Files.writeString(path.resolve("update.log"), "elevated-worker registration-write uninstall.exe\n");
        Files.createDirectories(path.resolve("payload"));
        Files.writeString(path.resolve("payload/owned-marker"), "prepared payload");
        return path;
    }

    private Properties result() {
        Properties result = new Properties();
        result.setProperty("status", "success"); result.setProperty("pid", "123");
        result.setProperty("unexpectedCredential", "do-not-copy-result");
        return result;
    }

    @Test void preservesDiagnosticsBeforeRemovingPreparedPayloadWithoutCopyingUnknownProperties() throws Exception {
        Path prepared = prepared("123");
        ProductUpdates.archiveAndRemoveCompleted(cache, prepared, result());
        assertFalse(Files.exists(prepared));
        String report = Files.readString(cache.resolve("history/update-123.log"));
        assertTrue(report.startsWith(UpdateLogArchive.HEADER));
        assertTrue(report.contains("request.version=1.0.0"));
        assertTrue(report.contains("result.status=success"));
        assertTrue(report.contains("mode=/ELEVATE childPid=456"));
        assertTrue(report.contains("registration-write uninstall.exe"));
        assertFalse(report.contains("do-not-copy"));
        assertTrue(Files.readString(cache.resolve("last-result.properties")).contains("status=success"));
    }

    @Test void archiveFailureKeepsTheOriginalLogsAndPreparedPayload() throws Exception {
        Path prepared = prepared("123");
        Files.writeString(cache.resolve("history"), "not a directory");
        Files.writeString(cache.resolve("last-result.properties"), "previous result");
        assertThrows(IOException.class, () -> ProductUpdates.archiveAndRemoveCompleted(cache, prepared, result()));
        assertTrue(Files.exists(prepared.resolve("payload/owned-marker")));
        assertTrue(Files.exists(prepared.resolve("update-steps.log")));
        assertEquals("previous result", Files.readString(cache.resolve("last-result.properties")));
    }

    @Test void largeLogsRetainTheirTailWithAnExplicitTruncationNotice() throws Exception {
        Path prepared = prepared("123");
        Files.writeString(prepared.resolve("update.log"), "x".repeat(UpdateLogArchive.MAX_LOG_BYTES * 2) + "\nlast failure\n");
        Path report = UpdateLogArchive.write(cache, prepared, result());
        assertTrue(Files.size(report) < UpdateLogArchive.MAX_LOG_BYTES + 4096L);
        String text = Files.readString(report);
        assertTrue(text.contains("earlier output omitted"));
        assertTrue(text.contains("last failure"));
    }

    @Test void retainsFiveRecentReportsAndDoesNotPruneUnrelatedFiles() throws Exception {
        Path history = Files.createDirectory(cache.resolve("history"));
        Path notes = history.resolve("update-personal.log");
        Files.writeString(notes, "keep my notes");
        for (int i = 0; i < 7; i++) {
            Path prepared = prepared(Integer.toString(i));
            Files.setLastModifiedTime(prepared.resolve("finished.properties"), FileTime.fromMillis(1_700_000_000_000L + i));
            ProductUpdates.archiveAndRemoveCompleted(cache, prepared, result());
        }
        assertFalse(Files.exists(history.resolve("update-0.log")));
        assertFalse(Files.exists(history.resolve("update-1.log")));
        for (int i = 2; i < 7; i++) assertTrue(Files.exists(history.resolve("update-" + i + ".log")));
        assertEquals("keep my notes", Files.readString(notes));
    }

    @Test void refusesAnArchiveOutsideTheUpdateCache() throws Exception {
        Path outside = cache.resolveSibling("update-outside");
        assertThrows(IOException.class, () -> UpdateLogArchive.write(cache, outside, result()));
        assertFalse(Files.exists(cache.resolve("history")));
    }

    @Test void refusesHistoryLinksWithoutTouchingTheirTarget() throws Exception {
        Path prepared = prepared("123");
        Path unrelated = Files.createDirectory(cache.resolve("unrelated"));
        Path keep = unrelated.resolve("keep.txt");
        Files.writeString(keep, "user data");
        try { Files.createSymbolicLink(cache.resolve("history"), unrelated); }
        catch (UnsupportedOperationException | FileSystemException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable on this filesystem");
        }
        assertThrows(IOException.class, () -> ProductUpdates.archiveAndRemoveCompleted(cache, prepared, result()));
        assertEquals("user data", Files.readString(keep));
        assertTrue(Files.exists(prepared.resolve("payload/owned-marker")));
    }
}
