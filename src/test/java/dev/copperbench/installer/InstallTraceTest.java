package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InstallTraceTest {
    @TempDir Path directory;

    @Test void recordsActualProcessIdentityAndExitWithoutCopyingArguments() throws Exception {
        Path log = directory.resolve("steps.log");
        Path java = Path.of(System.getProperty("java.home"), "bin", PlatformInstall.WINDOWS ? "java.exe" : "java");
        var result = PlatformInstall.command(List.of(java.toString(), "-Dprivate.test.value=do-not-log-this", "-version"), new InstallTrace(log), "probe");
        assertEquals(0, result.code());
        String text = Files.readString(log);
        assertTrue(text.contains("process-request\t"));
        assertTrue(text.contains("operation=probe"));
        assertTrue(text.matches("(?s).*childPid=[0-9]+.*"));
        assertTrue(text.contains("exitCode=0"));
        assertFalse(text.contains("do-not-log-this"));
        assertFalse(text.contains("-Dprivate"));
    }

    @Test void boundsFieldTextAndRecordsPhaseChangesInsteadOfEveryProgressTick() throws Exception {
        Path log = directory.resolve("steps.log");
        InstallTrace trace = new InstallTrace(log);
        trace.phase(new InstallEngine.Progress("copy", 0, 100));
        trace.phase(new InstallEngine.Progress("copy", 50, 100));
        trace.phase(new InstallEngine.Progress("activate", 100, 100));
        trace.failure("copy", new java.io.IOException("line1\nline2\t" + "x".repeat(5000)));
        List<String> lines = Files.readAllLines(log);
        assertEquals(3, lines.size());
        assertTrue(lines.get(2).contains("line1\\nline2\\t"));
        assertTrue(lines.get(2).contains("[truncated]"));
    }

    @Test void unavailableDiagnosticsDoNotAbortInstallation() {
        InstallTrace trace = new InstallTrace(directory);
        assertDoesNotThrow(() -> trace.event("start"));
        assertDoesNotThrow(() -> trace.event("finished"));
    }

    @Test @org.junit.jupiter.api.parallel.ResourceLock(org.junit.jupiter.api.parallel.Resources.SYSTEM_OUT)
    void relaysElevatedWorkerEventsBeforeItsScratchIsRemoved() throws Exception {
        Path workerLog = directory.resolve("worker-steps.log");
        new InstallTrace(workerLog).event("registration-write", "uninstaller", "测试/uninstall.exe");
        var bytes = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        try (var capture = new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(capture);
            new InstallTrace(directory.resolve("update-steps.log")).relayWorkerLog(workerLog);
        } finally { System.setOut(original); }
        String result = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(result.contains("Elevated update worker diagnostics"));
        assertTrue(result.contains("registration-write"));
        assertTrue(result.contains("测试/uninstall.exe"));
    }
}
