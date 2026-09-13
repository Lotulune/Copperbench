package dev.copperbench.gradle;

import dev.copperbench.platform.RuntimePlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Process-local recovery for Windows JDKs whose Unix-domain selector wakeup pipe is unusable. */
public final class GradleRuntimeCompatibility {
    private static final Map<String, Boolean> TCP_FALLBACK = new HashMap<>();

    private GradleRuntimeCompatibility() {}

    public static String daemonStartupOption() throws IOException {
        Path location;
        try { location = Path.of(GradleLoopbackProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()); }
        catch (Exception exception) { throw new IOException("Could not locate the daemon startup helper", exception); }
        Path agent = daemonAgentPath(location, Path.of(System.getProperty("user.dir")));
        if (!Files.isRegularFile(agent)) throw new IOException("Bundled daemon startup helper is missing: " + agent);
        return "-javaagent:" + agent.toAbsolutePath().normalize();
    }

    static Path daemonAgentPath(Path codeSource, Path workingDirectory) {
        if (!Files.isRegularFile(codeSource)) return workingDirectory.resolve("build/libs/copperbench-local-ipc-agent.jar");
        Path directory = codeSource.getParent();
        // Launch4j loads the bridge from its wrapped EXE, whereas Linux/direct Java uses lib/copperbench.jar.
        if (codeSource.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) directory = directory.resolve("lib");
        return directory.resolve("copperbench-local-ipc-agent.jar");
    }

    /** Must run before the application creates a selector (Desktop MCP and the Tooling API do). */
    public static void configureApplicationRuntime(Consumer<String> output) throws IOException, InterruptedException {
        if (RuntimePlatform.current().operatingSystem() != RuntimePlatform.OperatingSystem.WINDOWS) return;
        Path executable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        Map<String, String> environment = new HashMap<>(System.getenv());
        configure(executable, environment, _ -> {
            System.setProperty("jdk.net.unixdomain.tmpdir", executable.toAbsolutePath().normalize().toString());
            output.accept("APPLICATION_IPC_TCP_FALLBACK: Windows local socket probe failed; using the verified JDK TCP wakeup pipe for this application process.");
        }, GradleRuntimeCompatibility::probe, TCP_FALLBACK);
    }

    public static void configure(Path javaHome, Map<String, String> environment, Consumer<String> output)
            throws IOException, InterruptedException {
        if (RuntimePlatform.current().operatingSystem() != RuntimePlatform.OperatingSystem.WINDOWS) return;
        configure(javaHome.resolve("bin/java.exe"), environment, output, GradleRuntimeCompatibility::probe, TCP_FALLBACK);
    }

    @FunctionalInterface interface Probe {
        boolean ready(Path executable, Map<String, String> environment) throws IOException, InterruptedException;
    }

    static void configure(Path executable, Map<String, String> environment, Consumer<String> output,
            Probe probe, Map<String, Boolean> cache) throws IOException, InterruptedException {
        if (!Files.isRegularFile(executable)) throw new IOException("Workspace Java executable is missing: " + executable);
        String key = executable.toAbsolutePath().normalize() + "\n" + environment.toString();
        boolean fallback;
        synchronized (cache) {
            Boolean cached = cache.get(key);
            if (cached != null) fallback = cached;
            else {
                fallback = !probe.ready(executable, environment);
                if (fallback) {
                    Map<String, String> recovered = new HashMap<>(environment);
                    forceTcp(executable, recovered);
                    if (!probe.ready(executable, recovered)) throw new LoopbackUnavailableException();
                }
                cache.put(key, fallback);
            }
        }
        if (fallback) {
            forceTcp(executable, environment);
            output.accept("GRADLE_IPC_TCP_FALLBACK: Windows local socket probe failed; using the JDK TCP wakeup pipe for this task only.");
        }
    }

    private static void forceTcp(Path executable, Map<String, String> environment) {
        // PipeImpl.createListener falls back to TCP if the Unix-domain listener cannot bind.
        // An existing executable is a file, so it cannot contain a socket. This is deterministic
        // and requires neither a global environment change nor a shared temporary marker file.
        String option = "-Djdk.net.unixdomain.tmpdir=\"" + executable.toAbsolutePath().normalize() + "\"";
        String previous = environment.getOrDefault("JAVA_TOOL_OPTIONS", "");
        environment.put("JAVA_TOOL_OPTIONS", previous.isBlank() ? option : previous + " " + option);
    }

    private static boolean probe(Path executable, Map<String, String> environment) throws IOException, InterruptedException {
        Path probeClasses;
        try {
            probeClasses = Path.of(GradleLoopbackProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception exception) {
            throw new IOException("Could not locate the bundled Gradle loopback probe", exception);
        }
        ProcessBuilder builder = new ProcessBuilder(executable.toString(), "-cp", probeClasses.toString(),
                GradleLoopbackProbe.class.getName()).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Workspace JDK loopback probe timed out");
            String result = new String(process.getInputStream().readNBytes(1024), java.nio.charset.StandardCharsets.UTF_8).strip();
            if (process.exitValue() == 0 && result.equals("GRADLE_LOOPBACK_READY")) return true;
            if (process.exitValue() == 2 && result.equals("GRADLE_LOOPBACK_UNAVAILABLE")) return false;
            throw new IOException("Workspace JDK loopback probe could not start (exit " + process.exitValue() + ")");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    public static final class LoopbackUnavailableException extends IOException {
        public LoopbackUnavailableException() {
            super("Workspace Java cannot establish local IPC with either Unix-domain sockets or TCP. Check local loopback access and the bundled JDK, then retry; no build task has started.");
        }
    }
}
