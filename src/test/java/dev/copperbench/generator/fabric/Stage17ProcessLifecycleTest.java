package dev.copperbench.generator.fabric;

import dev.copperbench.platform.RuntimePlatform;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real isolated OS processes exercise the cancellation boundary; no Gradle cache or Minecraft is used. */
class Stage17ProcessLifecycleTest {
    private static final String MARKER = "COPPERBENCH_STAGE17_PROCESS_READY";

    @Test void interruptDuringWaitKillsTheWrapperAndItsJavaDescendants() throws Exception {
        try (Probe probe = new Probe("wait", Duration.ZERO, false)) {
            probe.start();
            probe.awaitReady();
            await(() -> java.util.Arrays.stream(probe.worker.getStackTrace())
                    .anyMatch(frame -> frame.getMethodName().equals("waitFor")), "worker must be inside process.waitFor");
            probe.worker.interrupt();
            probe.awaitFinished();
            assertInstanceOf(InterruptedException.class, probe.failure.get());
            assertNull(probe.result.get());
            assertTrue(probe.interruptedAtExit);
            probe.assertProcessesStopped();
        }
    }

    @Test void timeoutDoesNotTurnAReadinessMarkerIntoSuccessfulClientExit() throws Exception {
        try (Probe probe = new Probe("wait", Duration.ofSeconds(8), false)) {
            probe.start();
            probe.awaitReady();
            probe.awaitFinished();
            assertNull(probe.failure.get());
            assertEquals(124, probe.result.get().exitCode());
            assertTrue(probe.result.get().readinessMarkerSeen());
            probe.assertProcessesStopped();
        }
    }

    @Test void normalClientExitIsObservedOnlyAfterTheClientActuallyExits() throws Exception {
        try (Probe probe = new Probe("wait", Duration.ZERO, false)) {
            probe.start();
            probe.awaitReady();
            assertTrue(probe.worker.isAlive());
            assertNull(probe.result.get(), "readiness alone must not finish an interactive task");
            Files.writeString(probe.root.resolve("finish"), "0", StandardCharsets.UTF_8);
            probe.awaitFinished();
            assertNull(probe.failure.get());
            assertEquals(0, probe.result.get().exitCode());
            probe.assertProcessesStopped();
        }
    }

    @Test void nonzeroExitRemainsFailureEvenAfterReadiness() throws Exception {
        try (Probe probe = new Probe("wait", Duration.ZERO, false)) {
            probe.start();
            probe.awaitReady();
            Files.writeString(probe.root.resolve("finish"), "7", StandardCharsets.UTF_8);
            probe.awaitFinished();
            assertNull(probe.failure.get());
            assertEquals(7, probe.result.get().exitCode());
            assertTrue(probe.result.get().readinessMarkerSeen());
            probe.assertProcessesStopped();
        }
    }

    @Test void lostOutputObservationFailsPromptlyAndCleansUpTheRunningClient() throws Exception {
        try (Probe probe = new Probe("wait", Duration.ZERO, true)) {
            probe.start();
            probe.awaitReady();
            probe.awaitFinished();
            assertInstanceOf(IllegalStateException.class, probe.failure.get());
            assertNull(probe.result.get());
            probe.assertProcessesStopped();
        }
    }

    @Test void cancellationBeforeLaunchDoesNotStartAnyExternalProcess() throws Exception {
        try (Probe probe = new Probe("wait", Duration.ZERO, false)) {
            probe.worker.interrupt();
            probe.start();
            probe.awaitFinished();
            assertInstanceOf(InterruptedException.class, probe.failure.get());
            assertFalse(Files.exists(probe.root.resolve("parent.pid")));
            assertFalse(Files.exists(probe.root.resolve("child.pid")));
        }
    }

    private static void await(BooleanSupplier condition, String explanation) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(condition.getAsBoolean(), explanation);
    }

    private static final class Probe implements AutoCloseable {
        final Path root;
        final Thread worker;
        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<Fabric1211ProcessRunner.ProcessResult> result = new AtomicReference<>();
        final List<String> output = new CopyOnWriteArrayList<>();
        volatile boolean interruptedAtExit;

        Probe(String mode, Duration timeout, boolean failOutput) throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("COPPERBENCH_STAGE5_GRADLE_EXECUTABLE") == null);
            root = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "stage17-lifecycle-");
            boolean windows = RuntimePlatform.current().operatingSystem() == RuntimePlatform.OperatingSystem.WINDOWS;
            Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
            Path classes = Path.of(ProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String command = "\"" + java + "\" -cp \"" + classes + "\" \"" + ProcessFixture.class.getName() + "\" " + mode;
            // The dollar in a nested Java class name is literal in cmd; escape it for the POSIX fixture wrapper.
            if (!windows) command = command.replace("$", "\\$");
            Path wrapper = root.resolve(windows ? "gradlew.bat" : "gradlew");
            Files.writeString(wrapper, windows ? "@echo off\r\n" + command + "\r\nexit /b %errorlevel%\r\n"
                    : "#!/bin/sh\n" + command + "\nexit $?\n", StandardCharsets.UTF_8);
            if (!windows) assertTrue(wrapper.toFile().setExecutable(true));
            worker = new Thread(() -> {
                try {
                    result.set(Fabric1211ProcessRunner.system(MARKER).run(root, List.of("runClient"), timeout, line -> {
                        output.add(line);
                        if (line.equals(MARKER)) {
                            ready.countDown();
                            if (failOutput) throw new IllegalStateException("Injected loss of output observation");
                        }
                    }));
                } catch (Throwable exception) {
                    failure.set(exception);
                } finally {
                    interruptedAtExit = Thread.currentThread().isInterrupted();
                }
            }, "stage17-process-observer");
        }

        void start() { worker.start(); }
        void awaitReady() throws Exception { assertTrue(ready.await(15, TimeUnit.SECONDS), () -> output + " failure=" + failure.get()); }
        void awaitFinished() throws Exception { worker.join(Duration.ofSeconds(20)); assertFalse(worker.isAlive(), output.toString()); }

        List<ProcessHandle> handles() throws Exception {
            var handles = new java.util.ArrayList<ProcessHandle>();
            for (String file : List.of("parent.pid", "child.pid", "wrapper.pid")) {
                Path pid = root.resolve(file);
                if (Files.exists(pid)) ProcessHandle.of(Long.parseLong(Files.readString(pid).trim())).ifPresent(handles::add);
            }
            return handles;
        }

        void assertProcessesStopped() throws Exception {
            assertTrue(Files.exists(root.resolve("parent.pid")));
            assertTrue(Files.exists(root.resolve("child.pid")));
            var handles = handles();
            await(() -> handles.stream().noneMatch(ProcessHandle::isAlive), "fixture process survived task termination: " + handles);
        }

        @Override public void close() throws Exception {
            // Cleanup is restricted to PIDs written by this test's own process fixture.
            if (worker.isAlive()) worker.interrupt();
            for (ProcessHandle handle : handles()) if (handle.isAlive()) handle.destroyForcibly();
            worker.join(Duration.ofSeconds(15));
        }
    }

    public static final class ProcessFixture {
        public static void main(String[] arguments) throws Exception {
            if (arguments[0].equals("child")) {
                Files.writeString(Path.of("child.pid"), Long.toString(ProcessHandle.current().pid()));
                while (!Files.exists(Path.of("finish"))) Thread.sleep(25);
                System.exit(Integer.parseInt(Files.readString(Path.of("finish")).trim()));
            }
            Files.writeString(Path.of("parent.pid"), Long.toString(ProcessHandle.current().pid()));
            ProcessHandle.current().parent().ifPresent(parent -> {
                try { Files.writeString(Path.of("wrapper.pid"), Long.toString(parent.pid())); }
                catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            });
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    ProcessFixture.class.getName(), "child").inheritIO().start();
            while (!Files.exists(Path.of("child.pid"))) Thread.sleep(25);
            System.out.println(MARKER);
            System.out.flush();
            System.exit(child.waitFor());
        }
    }
}
