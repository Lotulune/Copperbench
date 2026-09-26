package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Actual host JVM termination and restart around the production task gateway and process runner. */
class Stage17TaskSessionRecoveryTest {
    private static final UUID WORKSPACE = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test void abruptHostLossRestoresUnconfirmedStateAndLastLogsWithoutCancellationAuthority() throws Exception {
        try (Host host = new Host()) {
            UUID task = host.awaitReady();
            host.process.destroyForcibly();
            assertTrue(host.process.waitFor(15, TimeUnit.SECONDS));
            Path record = host.root.resolve(".copperbench/task-records/" + task + ".json");
            String before = Files.readString(record);
            try (var reopened = gateway(host.root, (root, args, timeout, output) -> {
                throw new AssertionError("Restoring history must not invoke a process");
            })) {
                JsonObject restored = reopened.find(WORKSPACE, task).orElseThrow();
                assertEquals("failed", restored.get("state").getAsString());
                assertEquals("unconfirmed_after_session_loss", restored.get("resultObservation").getAsString());
                assertFalse(restored.get("cancellable").getAsBoolean());
                assertTrue(restored.get("completedAt").isJsonNull());
                assertTrue(reopened.active(WORKSPACE).isEmpty());
                assertTrue(reopened.cancel(WORKSPACE, task).isEmpty());
                assertEquals(1, reopened.recent(WORKSPACE).size());
                String logs = reopened.logs(WORKSPACE, task).toString();
                assertTrue(logs.contains("SESSION_CLIENT_READY"), logs);
                assertFalse(logs.contains("fixture-secret"), logs);
                assertFalse(logs.contains("two secret words"), logs);
                assertEquals(before, Files.readString(record), "history queries cannot overwrite the original observation");
                JsonObject fresh = reopened.start(WORKSPACE, Operation.VALIDATE_WORKSPACE, new JsonObject());
                UUID freshId = UUID.fromString(fresh.get("id").getAsString());
                assertNotEquals(task, freshId);
                await(() -> reopened.find(WORKSPACE, freshId).orElseThrow().get("state").getAsString().equals("succeeded"));
                assertEquals("unconfirmed_after_session_loss", reopened.find(WORKSPACE, task).orElseThrow().get("resultObservation").getAsString());
            }
            String persisted = Files.readString(host.root.resolve(".copperbench/task-records/" + task + ".logs.jsonl"));
            assertFalse(persisted.contains("fixture-secret"));
            assertFalse(persisted.contains("two secret words"));
        }
    }

    @Test void observedNormalExitRemainsSucceededAfterHostRestart() throws Exception {
        try (Host host = new Host()) {
            UUID task = host.awaitReady();
            Files.writeString(host.root.resolve("finish"), "0");
            assertTrue(host.process.waitFor(20, TimeUnit.SECONDS), host.logs());
            assertEquals(0, host.process.exitValue(), host.logs());
            try (var reopened = gateway(host.root, null)) {
                JsonObject restored = reopened.find(WORKSPACE, task).orElseThrow();
                assertEquals("succeeded", restored.get("state").getAsString());
                assertFalse(restored.has("resultObservation"));
                assertFalse(restored.get("completedAt").isJsonNull());
                assertTrue(reopened.logs(WORKSPACE, task).toString().contains("SESSION_CLIENT_NORMAL_EXIT"));
                assertTrue(reopened.cancel(WORKSPACE, task).isEmpty());
            }
        }
    }

    @Test void observedAbnormalExitRemainsFailedAfterHostRestart() throws Exception {
        try (Host host = new Host()) {
            UUID task = host.awaitReady();
            Files.writeString(host.root.resolve("finish"), "7");
            assertTrue(host.process.waitFor(20, TimeUnit.SECONDS), host.logs());
            try (var reopened = gateway(host.root, null)) {
                JsonObject restored = reopened.find(WORKSPACE, task).orElseThrow();
                assertEquals("failed", restored.get("state").getAsString());
                assertFalse(restored.has("resultObservation"));
                assertTrue(reopened.diagnostics(WORKSPACE, task).toString().contains("SESSION_RUN_CLIENT_EXITED"));
            }
        }
    }

    private static GradleWorkspaceTaskGateway gateway(Path root, GradleProcessRunner runner) {
        var store = new RevisionedWorkspaceStore(); store.register(Fabric1211GoldenWorkspace.create());
        GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
            public String displayName() { return "Stage17 session fixture"; }
            public String diagnosticPrefix() { return "SESSION"; }
            public List<ValidationIssue> validate(WorkspaceState workspace) { return List.of(); }
            public GenerationResult generate(Path target, WorkspaceState workspace) {
                return new GenerationResult("fabric-1.21.1", "session_fixture", List.of());
            }
        };
        return new GradleWorkspaceTaskGateway(store, ignored -> root, backend, Clock.systemUTC(), UUID::randomUUID, runner);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
        assertTrue(condition.getAsBoolean());
    }

    private static final class Host implements AutoCloseable {
        final Path root;
        final Process process;
        final Path log;
        Host() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("COPPERBENCH_STAGE5_GRADLE_EXECUTABLE") == null);
            root = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "stage17-session-");
            String classpath = System.getProperty("copperbench.test.runtimeClasspath");
            assertNotNull(classpath, "The test host needs the actual Gradle test runtime classpath");
            Path args = root.resolve("host.args");
            Files.writeString(args, "-cp\n\"" + classpath.replace('\\', '/') + "\"\n" + HostFixture.class.getName()
                    + "\n\"" + root.toString().replace('\\', '/') + "\"\n", StandardCharsets.UTF_8);
            log = root.resolve("host.log");
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "@" + args)
                    .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        }
        UUID awaitReady() throws Exception {
            await(() -> Files.exists(root.resolve("observed-ready")) || !process.isAlive());
            assertTrue(process.isAlive(), logs());
            return UUID.fromString(Files.readString(root.resolve("task-id")).trim());
        }
        String logs() throws Exception { return Files.readString(log); }
        public void close() throws Exception {
            var children = process.descendants().toList();
            if (process.isAlive()) process.destroyForcibly();
            for (ProcessHandle child : children) if (child.isAlive()) child.destroyForcibly();
            // After host loss the wrapper is reparented; only use the PID recorded by our own fixture.
            for (String file : List.of("client.pid", "wrapper.pid")) {
                Path pid = root.resolve(file);
                if (Files.exists(pid)) ProcessHandle.of(Long.parseLong(Files.readString(pid).trim()))
                        .filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
            }
            process.waitFor(15, TimeUnit.SECONDS);
        }
    }

    public static final class HostFixture {
        public static void main(String[] args) throws Exception {
            Path root = Path.of(args[0]);
            boolean windows = System.getProperty("os.name").startsWith("Windows");
            Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
            Path clientArgs = root.resolve("client.args");
            Files.writeString(clientArgs, "-cp\n\"" + System.getProperty("java.class.path").replace('\\', '/')
                    + "\"\n" + ClientFixture.class.getName() + "\n", StandardCharsets.UTF_8);
            String command = "\"" + java + "\" @client.args";
            Path wrapper = root.resolve(windows ? "gradlew.bat" : "gradlew");
            Files.writeString(wrapper, windows ? "@echo off\r\n" + command + "\r\nexit /b %errorlevel%\r\n"
                    : "#!/bin/sh\n" + command + "\nexit $?\n", StandardCharsets.UTF_8);
            if (!windows && !wrapper.toFile().setExecutable(true)) throw new java.io.IOException("Cannot enable fixture wrapper");
            var processes = Fabric1211ProcessRunner.system("SESSION_CLIENT_READY");
            try (var gateway = gateway(root, (directory, arguments, timeout, output) -> {
                var result = processes.run(directory, arguments, timeout, line -> {
                    output.accept(line);
                    if (line.equals("SESSION_CLIENT_READY")) {
                        try { Files.writeString(root.resolve("observed-ready"), "ready"); }
                        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    }
                });
                return new GradleProcessRunner.ProcessResult(result.exitCode(), result.readinessMarkerSeen(), result.runtimeFailureCode());
            })) {
                var task = gateway.start(WORKSPACE, Operation.RUN_CLIENT, new JsonObject());
                UUID id = UUID.fromString(task.get("id").getAsString());
                Files.writeString(root.resolve("task-id"), id.toString());
                while (gateway.find(WORKSPACE, id).orElseThrow().get("state").getAsString().equals("running")) Thread.sleep(25);
            }
        }
    }

    public static final class ClientFixture {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of("client.pid"), Long.toString(ProcessHandle.current().pid()));
            Files.writeString(Path.of("wrapper.pid"), Long.toString(ProcessHandle.current().parent().orElseThrow().pid()));
            System.out.println("Authorization: Bearer fixture-secret");
            System.out.println("password=\"two secret words\"");
            System.out.println("SESSION_CLIENT_READY"); System.out.flush();
            while (!Files.exists(Path.of("finish"))) Thread.sleep(25);
            int exit = Integer.parseInt(Files.readString(Path.of("finish")).trim());
            if (exit == 0) System.out.println("SESSION_CLIENT_NORMAL_EXIT");
            System.exit(exit);
        }
    }
}
