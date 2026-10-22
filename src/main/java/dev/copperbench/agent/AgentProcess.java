package dev.copperbench.agent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Owns exactly one CLI process tree. Prompts travel over stdin, never through a shell command. */
final class AgentProcess implements AutoCloseable {
    static final int MAX_LINE = 2 * 1024 * 1024;
    final Process process;
    private final Writer input;
    private final Map<ProcessHandle, Optional<Instant>> descendants = new ConcurrentHashMap<>();
    private final Thread collector;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final CountDownLatch closeFinished = new CountDownLatch(1);
    private volatile boolean closed;
    private volatile Integer exitBeforeTeardown;

    AgentProcess(Path executable, List<String> arguments, Path cwd) throws IOException {
        this(new ProcessBuilder(command(executable, arguments)).directory(cwd.toFile()));
    }

    AgentProcess(ProcessBuilder builder) throws IOException {
        process = builder.start();
        input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        collector = Thread.startVirtualThread(() -> {
            while (!closed) {
                collectChildren();
                try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
            }
        });
    }

    static boolean windows() { return System.getProperty("os.name").startsWith("Windows"); }

    static List<String> command(Path executable, List<String> arguments) {
        String path = executable.toAbsolutePath().normalize().toString();
        String lower = path.toLowerCase(Locale.ROOT);
        List<String> command = new ArrayList<>();
        if (windows() && lower.endsWith(".ps1")) {
            command.addAll(List.of("pwsh", "-NoProfile", "-NonInteractive", "-File", path));
            command.addAll(arguments);
        } else if (windows() && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            List<String> words = new ArrayList<>(); words.add(path); words.addAll(arguments);
            // cmd expands these even in quotes. Reject instead of executing an altered wrapper command.
            for (String word : words) if (word.matches(".*[\\\"%!^\\r\\n].*"))
                throw new IllegalArgumentException("CLI 启动路径或参数含不支持的 cmd 字符；请选择 .exe 或 .ps1 启动器");
            command.addAll(List.of("cmd.exe", "/d", "/s", "/c",
                    "\"" + String.join(" ", words.stream().map(word -> "\"" + word + "\"").toList()) + "\""));
        } else { command.add(path); command.addAll(arguments); }
        return command;
    }

    synchronized void write(String line) throws IOException {
        if (closed || !process.isAlive()) throw new IOException("CLI 进程已退出");
        input.write(line); input.write('\n'); input.flush();
    }

    synchronized void endInput() {
        try { input.close(); } catch (IOException ignored) { }
    }

    static void lines(InputStream stream, Consumer<String> consumer) throws IOException {
        lines(stream, consumer, MAX_LINE);
    }

    static void lines(InputStream stream, Consumer<String> consumer, int maxLine) throws IOException {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder line = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                for (int i = 0; i < count; i++) {
                    char next = buffer[i];
                    if (next == '\n') { consumer.accept(line.toString()); line.setLength(0); }
                    else if (next != '\r') {
                        if (line.length() >= maxLine) throw new IOException("CLI 消息超过读取上限");
                        line.append(next);
                    }
                }
            }
            if (!line.isEmpty()) consumer.accept(line.toString());
        }
    }

    boolean await(Duration duration) throws InterruptedException {
        return process.waitFor(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void collectChildren() {
        if (process.isAlive()) process.descendants().forEach(child -> descendants.putIfAbsent(child, child.info().startInstant()));
    }

    private boolean sameLiveProcess(ProcessHandle child, Optional<Instant> started) {
        return child.isAlive() && (started.isEmpty() || started.equals(child.info().startInstant()));
    }

    boolean hasLiveChildren() {
        collectChildren();
        return descendants.entrySet().stream().anyMatch(entry -> sameLiveProcess(entry.getKey(), entry.getValue()));
    }

    /** Captured before any owned process is terminated; distinguishes genuine failure from our teardown. */
    Integer exitBeforeTeardown() { return exitBeforeTeardown; }

    @Override public void close() {
        if (!closing.compareAndSet(false, true)) {
            try { closeFinished.await(4, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return;
        }
        try {
        if (!process.isAlive()) exitBeforeTeardown = process.exitValue();
        collectChildren(); closed = true; collector.interrupt();
        AtomicBoolean terminatedOwnedProcess = new AtomicBoolean();
        // Retain descendants observed before an intermediary wrapper exited or reparented them.
        descendants.forEach((child, started) -> {
            if (sameLiveProcess(child, started) && child.destroy()) terminatedOwnedProcess.set(true);
        });
        if (process.isAlive() && process.toHandle().destroy()) terminatedOwnedProcess.set(true);
        endInput();
        try { process.waitFor(750, TimeUnit.MILLISECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        descendants.forEach((child, started) -> {
            if (sameLiveProcess(child, started) && child.destroyForcibly()) terminatedOwnedProcess.set(true);
        });
        if (process.isAlive() && process.toHandle().destroyForcibly()) terminatedOwnedProcess.set(true);
        try { process.waitFor(1500, TimeUnit.MILLISECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
        for (var entry : descendants.entrySet()) {
            if (sameLiveProcess(entry.getKey(), entry.getValue())) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try { entry.getKey().onExit().get(remaining, TimeUnit.NANOSECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                catch (ExecutionException | TimeoutException ignored) { }
            }
        }
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        try { process.getErrorStream().close(); } catch (IOException ignored) { }
        if (exitBeforeTeardown == null && !terminatedOwnedProcess.get() && !process.isAlive())
            exitBeforeTeardown = process.exitValue(); // It raced our initial check, but we never terminated anything.
        } finally { closeFinished.countDown(); }
    }

    static String version(Path executable, Path cwd) throws IOException, InterruptedException {
        try (AgentProcess probe = new AgentProcess(executable, List.of("--version"), cwd)) {
            StringBuilder output = new StringBuilder();
            Thread reader = Thread.startVirtualThread(() -> {
                try { lines(probe.process.getInputStream(), line -> { synchronized (output) {
                    if (output.length() < 2048) output.append(AgentJson.bounded(line, 1024)).append('\n');
                } }); } catch (IOException ignored) { }
            });
            Thread errors = Thread.startVirtualThread(() -> {
                try { lines(probe.process.getErrorStream(), ignored -> { }); } catch (IOException ignored) { }
            });
            probe.endInput();
            if (!probe.await(Duration.ofSeconds(6))) throw new IOException("版本检查超时");
            reader.join(1000); errors.join(1000);
            if (probe.process.exitValue() != 0) throw new IOException("版本检查退出码 " + probe.process.exitValue());
            synchronized (output) { return AgentJson.bounded(output.toString().strip(), 512); }
        }
    }
}
