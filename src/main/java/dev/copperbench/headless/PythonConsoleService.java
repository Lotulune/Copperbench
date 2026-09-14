package dev.copperbench.headless;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns one persistent CPython worker per desktop workspace; never owns Core. */
public final class PythonConsoleService implements AutoCloseable {
    private final Path workspaceFile;
    private final Path workerFile;
    private final PythonContext context;
    private final ExecutorService writes = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final Deque<JsonObject> output = new ArrayDeque<>();
    private Process process;
    private Writer input;
    private JsonObject pending;
    private long sequence;
    private int outputChars;
    private String state = "stopped";
    private String version = "";
    private String lastRequestId = "";
    private String lastOperation = "";
    private String lastResult = "idle";
    private String lastError = "";
    private JsonArray completions = new JsonArray();
    private JsonArray operators = new JsonArray();
    private Integer errorLine;
    private boolean closed;

    public PythonConsoleService(Path workspaceFile, Path sdkDirectory, PythonContext context) {
        this.workspaceFile = workspaceFile.toAbsolutePath().normalize();
        this.workerFile = sdkDirectory.resolve("copperbench_console.py").toAbsolutePath().normalize();
        this.context = context;
    }

    public PythonContext context() { return context; }

    public synchronized JsonObject execute(JsonObject request) throws IOException {
        if (closed) throw new IllegalStateException("Python workbench is closed");
        if (state.equals("starting") || state.equals("running")) throw new IllegalStateException("Python is busy");
        String operation = request.has("operation") ? request.get("operation").getAsString() : "execute";
        if (!Set.of("execute", "complete", "start").contains(operation)) throw new IllegalArgumentException("Unknown Python operation");
        if (!operation.equals("start")) {
            String source = request.get(operation.equals("complete") ? "text" : "source").getAsString();
            if (source.length() > 262144) throw new IllegalArgumentException("Python source exceeds 256 Ki characters");
            if (request.has("mode") && !Set.of("script", "console").contains(request.get("mode").getAsString()))
                throw new IllegalArgumentException("Invalid execution mode");
        }
        if (process == null || !process.isAlive()) start(request.has("python") ? request.get("python").getAsString() : "");
        if (!operation.equals("start")) {
            pending = request.deepCopy();
            pending.remove("python");
            lastRequestId = UUID.randomUUID().toString();
            lastOperation = operation;
            pending.addProperty("id", lastRequestId);
            completions = new JsonArray();
            if (operation.equals("execute")) {
                errorLine = null;
                lastResult = "running";
                lastError = "";
                append("input", request.get("source").getAsString());
            }
            if (!state.equals("starting")) sendPending();
        }
        return status(0);
    }

    private void start(String executable) throws IOException {
        if (!Files.isRegularFile(workerFile)) throw new IOException("Python SDK worker is missing: " + workerFile);
        if (executable.isBlank()) {
            String configured = System.getenv("COPPERBENCH_PYTHON");
            Path bundled = workerFile.getParent().getParent().getParent().resolve("python")
                    .resolve(System.getProperty("os.name").startsWith("Windows") ? "python.exe" : "bin/python3");
            executable = configured != null && !configured.isBlank() ? configured
                    : Files.isRegularFile(bundled) ? bundled.toString()
                    : System.getProperty("os.name").startsWith("Windows") ? "python" : "python3";
        }
        ProcessBuilder builder = new ProcessBuilder(executable, "-u", workerFile.toString(), workspaceFile.toString())
                .directory(workspaceFile.getParent().toFile());
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        Process started = builder.start();
        process = started;
        input = new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8);
        state = "starting";
        version = "";
        lastResult = "idle";
        lastError = "";
        operators = new JsonArray();
        Thread.startVirtualThread(() -> read(started));
        Thread.startVirtualThread(() -> {
            try (var reader = new InputStreamReader(started.getErrorStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    synchronized (this) { if (process == started) append("stderr", new String(buffer, 0, count)); }
                }
            } catch (IOException ignored) { }
        });
    }

    private synchronized void sendPending() {
        if (pending == null) { state = "ready"; return; }
        String message = pending.toString();
        pending = null;
        state = "running";
        Process target = process;
        Writer targetInput = input;
        writes.submit(() -> {
            try {
                targetInput.write(message + "\n");
                targetInput.flush();
            } catch (IOException exception) {
                synchronized (this) {
                    if (process == target) { state = "failed"; lastResult = "failed"; lastError = "Python 连接已断开"; append("stderr", lastError + "\n"); }
                }
            }
        });
    }

    private void read(Process target) {
        try (var reader = new BufferedReader(new InputStreamReader(target.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder line = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                if (ch == '\n') {
                    synchronized (this) { if (process == target) receive(line.toString()); }
                    line.setLength(0);
                } else if (line.length() < 262144) line.append((char) ch);
            }
        } catch (IOException ignored) {
        } finally {
            synchronized (this) {
                if (process == target) {
                    state = "failed";
                    lastResult = "failed";
                    lastError = "Python 解释器已退出";
                    pending = null;
                    append("stderr", "Python 解释器已退出；可重新运行脚本启动新会话。\n");
                }
            }
        }
    }

    private void receive(String line) {
        try {
            JsonObject message = JsonParser.parseString(line).getAsJsonObject();
            String event = message.get("event").getAsString();
            if (event.equals("output")) {
                append(message.get("channel").getAsString(), message.get("text").getAsString());
            } else if (event.equals("ready")) {
                version = message.get("pythonVersion").getAsString();
                sendPending();
            } else if (Set.of("completed", "failed", "incomplete").contains(event)) {
                if (!message.has("executionId") || !lastRequestId.equals(message.get("executionId").getAsString())) return;
                state = "ready";
                if (lastOperation.equals("execute")) {
                    lastResult = event.equals("completed") ? "succeeded" : event;
                    lastError = message.has("message") ? message.get("message").getAsString() : "";
                }
                if (event.equals("incomplete")) append("system", "… 等待后续输入\n");
                if (message.has("operators")) operators = message.getAsJsonArray("operators").deepCopy();
                if (message.has("completions")) completions = message.getAsJsonArray("completions").deepCopy();
                if (message.has("line") && !message.get("line").isJsonNull()) errorLine = message.get("line").getAsInt();
            }
        } catch (RuntimeException exception) {
            append("stdout", line + "\n");
        }
    }

    private void append(String channel, String text) {
        if (text.length() > 16384) text = text.substring(0, 16384) + "\n[输出已截断]\n";
        JsonObject entry = new JsonObject();
        entry.addProperty("sequence", ++sequence);
        entry.addProperty("channel", channel);
        entry.addProperty("text", text);
        output.addLast(entry);
        outputChars += text.length();
        while (output.size() > 500 || outputChars > 200000) {
            outputChars -= output.removeFirst().get("text").getAsString().length();
        }
    }

    public synchronized JsonObject status(long afterSequence) {
        JsonObject result = new JsonObject();
        result.addProperty("state", state);
        result.addProperty("pythonVersion", version);
        result.addProperty("requestId", lastRequestId);
        result.addProperty("lastResult", lastResult);
        result.addProperty("lastError", lastError);
        result.addProperty("lastSequence", sequence);
        result.addProperty("truncated", !output.isEmpty() && afterSequence < output.getFirst().get("sequence").getAsLong() - 1);
        result.add("errorLine", errorLine == null ? JsonNull.INSTANCE : new JsonPrimitive(errorLine));
        result.add("context", context.snapshot());
        result.add("completions", completions.deepCopy());
        result.add("operators", operators.deepCopy());
        JsonArray logs = new JsonArray();
        output.stream().filter(entry -> entry.get("sequence").getAsLong() > afterSequence)
                .forEach(entry -> logs.add(entry.deepCopy()));
        result.add("output", logs);
        return result;
    }

    public void stop() {
        Process target;
        synchronized (this) {
            target = process;
            process = null;
            pending = null;
            lastResult = state.equals("running") || state.equals("starting") ? "cancelled" : "idle";
            lastError = "";
            state = "stopped";
            operators = new JsonArray();
            completions = new JsonArray();
            errorLine = null;
            if (target != null) append("system", "解释器已停止，变量已清空；已提交的工作区修改仍保留。\n");
        }
        if (target != null) {
            List<ProcessHandle> children = target.descendants().toList();
            children.forEach(ProcessHandle::destroy);
            target.destroy();
            try {
                if (!target.waitFor(500, TimeUnit.MILLISECONDS)) target.destroyForcibly();
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                target.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                target.destroyForcibly();
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override public void close() {
        synchronized (this) { closed = true; }
        stop();
        writes.shutdownNow();
    }
}
