package dev.copperbench.agent;

import com.google.gson.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** One explicit user turn; closes its CLI before reporting the run as settled. */
final class AgentRun implements AgentProtocol.Channel {
    interface Listener {
        void text(String key, String role, String text, boolean append);
        void remoteId(String id);
        void approval(String id, String title, String detail, JsonArray choices, Consumer<String> answer);
        void question(String id, String title, String detail, JsonArray choices, boolean multiSelect, boolean allowCustom, Consumer<List<String>> answer);
        void removeApproval(String id);
        void running();
        void finished(boolean success, boolean stopped, String error);
    }
    record Outcome(boolean success, boolean interrupted, String error) { }
    private final AgentProvider provider;
    private final AgentWorkspaceContext context;
    private final Listener listener;
    private final AgentProtocol protocol;
    private final int outputLimit;
    private final CompletableFuture<Outcome> outcome = new CompletableFuture<>();
    private final Map<String, CompletableFuture<JsonObject>> requests = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile AgentProcess process;
    private volatile boolean shuttingDown;
    private volatile boolean outputClosed;
    private final StringBuilder stderr = new StringBuilder();

    AgentRun(AgentProvider provider, AgentWorkspaceContext context, String remoteId, Listener listener) {
        this(provider, context, remoteId, null, listener);
    }
    AgentRun(AgentProvider provider, AgentWorkspaceContext context, String remoteId, String model, Listener listener) {
        this(provider, context, remoteId, model, AgentTurnOptions.DEFAULT, List.of(), listener);
    }
    AgentRun(AgentProvider provider, AgentWorkspaceContext context, String remoteId, String model,
             AgentTurnOptions options, List<AgentAttachments.Item> attachments, Listener listener) {
        this.provider = provider; this.context = context; this.listener = listener;
        protocol = AgentProtocol.create(provider.id(), this, context.root(), remoteId);
        protocol.useModel(model);
        protocol.configure(options, attachments);
        // Streaming CLIs can replay the submitted image blocks. Keep the bound tied to admitted input.
        outputLimit = AgentProcess.MAX_LINE + (int) attachments.stream().filter(AgentAttachments.Item::image).mapToLong(item -> (item.size() + 2) / 3 * 4).sum();
    }

    JsonObject models() throws Exception {
        Thread output = null, errors = null;
        try {
            if (stopped.get()) throw new CancellationException();
            AgentProcess launched = new AgentProcess(provider.executable(), protocol.arguments(), context.root()); process = launched;
            if (stopped.get()) throw new CancellationException();
            output = Thread.startVirtualThread(() -> readOutput(launched));
            errors = Thread.startVirtualThread(() -> readErrors(launched));
            JsonObject catalog = protocol.models(); protocol.finish(); return catalog;
        } finally {
            shuttingDown = true;
            if (process != null) { process.close(); joinReader(output); joinReader(errors); }
        }
    }

    void execute(String prompt) {
        Outcome result = null;
        Thread outputReader = null, errorReader = null;
        try {
            if (stopped.get()) throw new CancellationException();
            AgentProcess launched = new AgentProcess(provider.executable(), protocol.arguments(), context.root());
            process = launched;
            if (stopped.get()) throw new CancellationException();
            outputReader = Thread.startVirtualThread(() -> readOutput(launched));
            errorReader = Thread.startVirtualThread(() -> readErrors(launched));
            protocol.start(prompt, context.instructions());
            result = outcome.get();
            if (!stopped.get()) protocol.finish();
            shuttingDown = true;
            launched.endInput();
            if (!launched.await(Duration.ofSeconds(5)) && !result.interrupted() && !protocol.persistentAfterTurn())
                result = new Outcome(false, false, "CLI 已返回结果，但未正常结束；后台进程已停止，请检查工具任务状态");
            if (!launched.process.isAlive()) {
                outputReader.join(1000); errorReader.join(1000);
                int exit = launched.process.exitValue();
                if (exit != 0 && !result.interrupted()) result = new Outcome(false, false, "CLI 退出码 " + exit + diagnosticTail());
            }
        } catch (CancellationException exception) {
            result = new Outcome(false, true, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); result = new Outcome(false, true, null);
        } catch (Exception exception) {
            Throwable cause = exception instanceof ExecutionException && exception.getCause() != null ? exception.getCause() : exception;
            result = new Outcome(false, stopped.get(), AgentJson.bounded(cause.getMessage(), 2000) + diagnosticTail());
        } finally {
            shuttingDown = true;
            AgentProcess current = process;
            if (current != null) {
                current.close();
                // Drain diagnostics before settling. Antigravity may report policy denials on stderr
                // after its SUCCESS frame, and its installed streaming process can remain alive after EOF.
                joinReader(outputReader); joinReader(errorReader);
                Integer naturalExit = current.exitBeforeTeardown();
                if (naturalExit != null && naturalExit != 0 && result != null && !result.interrupted())
                    result = new Outcome(false, false, "CLI 退出码 " + naturalExit + diagnosticTail());
                String protocolError = protocol.completionError();
                if (protocolError != null && result != null && !result.interrupted())
                    result = new Outcome(false, false, protocolError);
                if (current.process.isAlive() || current.hasLiveChildren())
                    result = new Outcome(false, false, "CLI 子进程未能完全停止，请在系统进程管理器中检查 " + provider.label());
            }
            requests.values().forEach(request -> request.completeExceptionally(new IOException("CLI 连接已关闭")));
            requests.clear();
            if (result == null) result = new Outcome(false, stopped.get(), "CLI 未返回有效结果");
            listener.finished(result.success(), result.interrupted(), result.error());
        }
    }

    /** No session/start, prompt, resume, approval or tool request is sent by this path. */
    void check() {
        Thread output = null, errors = null;
        String failure = null;
        try {
            if (stopped.get()) throw new CancellationException();
            AgentProcess launched = new AgentProcess(provider.executable(), protocol.arguments(), context.root());
            process = launched;
            if (stopped.get()) throw new CancellationException();
            output = Thread.startVirtualThread(() -> readOutput(launched));
            errors = Thread.startVirtualThread(() -> readErrors(launched));
            protocol.check();
        } catch (Exception exception) {
            Throwable cause = exception instanceof ExecutionException && exception.getCause() != null ? exception.getCause() : exception;
            failure = AgentJson.bounded(cause.getMessage(), 2000);
        } finally {
            shuttingDown = true;
            AgentProcess launched = process;
            if (launched != null) { launched.close(); joinReader(output); joinReader(errors); }
            listener.finished(failure == null && !stopped.get(), stopped.get(), failure);
        }
    }

    private static void joinReader(Thread reader) {
        if (reader == null) return;
        try { reader.join(1000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    private void readOutput(AgentProcess target) {
        try {
            AgentProcess.lines(target.process.getInputStream(), line -> {
                if (line.isBlank()) return;
                try {
                    if (!protocol.stdoutDiagnostic(line)) receive(JsonParser.parseString(line).getAsJsonObject());
                }
                catch (RuntimeException malformed) { fail("CLI 返回无法识别的协议消息，请检查 CLI 版本或启动器"); }
            }, outputLimit);
        } catch (IOException exception) { if (!shuttingDown) fail(exception.getMessage()); }
        finally {
            outputClosed = true;
            requests.values().forEach(request -> request.completeExceptionally(new IOException("CLI 输出连接已关闭")));
            if (!outcome.isDone() && !shuttingDown) fail("CLI 连接在本轮完成前关闭" + diagnosticTail());
        }
    }

    private void readErrors(AgentProcess target) {
        try { AgentProcess.lines(target.process.getErrorStream(), line -> {
            String safe = AgentJson.bounded(line, 2048);
            synchronized (stderr) {
                stderr.append(safe).append('\n');
                if (stderr.length() > 8192) stderr.delete(0, stderr.length() - 8192);
            }
            protocol.stderr(safe);
            listener.text("diagnostic", "tool", safe + "\n", true);
        }); } catch (IOException ignored) { }
    }

    private String diagnosticTail() {
        synchronized (stderr) { return stderr.isEmpty() ? "" : "\n" + AgentJson.bounded(stderr.toString(), 2000); }
    }

    private void receive(JsonObject frame) {
        if (frame.has("id") && !frame.has("method")) {
            CompletableFuture<JsonObject> pending = requests.remove("rpc:" + frame.get("id"));
            if (pending != null) {
                if (frame.has("error")) pending.completeExceptionally(new IOException(AgentJson.detail(frame.get("error"))));
                else pending.complete(AgentJson.child(frame, "result"));
            }
            if (pending != null) return;
        }
        if (AgentJson.string(frame, "type").equals("control_response")) {
            JsonObject response = AgentJson.child(frame, "response");
            CompletableFuture<JsonObject> pending = requests.remove("control:" + AgentJson.string(response, "request_id"));
            if (pending != null) {
                if (AgentJson.string(response, "subtype").equals("error"))
                    pending.completeExceptionally(new IOException(AgentJson.string(response, "error")));
                else pending.complete(AgentJson.child(response, "response"));
            }
            return;
        }
        protocol.receive(frame);
    }

    @Override public JsonObject rpc(String method, JsonObject params) throws Exception {
        long id = nextId.incrementAndGet();
        return request("rpc:" + id, AgentJson.object("jsonrpc", "2.0", "id", id, "method", method, "params", params), method.equals("session/prompt"));
    }

    @Override public JsonObject control(JsonObject request) throws Exception {
        String id = "copperbench-" + nextId.incrementAndGet();
        return request("control:" + id, AgentJson.object("type", "control_request", "request_id", id, "request", request), false);
    }

    private JsonObject request(String key, JsonObject frame, boolean turn) throws Exception {
        if (stopped.get()) throw new CancellationException();
        CompletableFuture<JsonObject> response = new CompletableFuture<>(); requests.put(key, response);
        try {
            // EOF can arrive before or during registration, including after the turn result but before session/close.
            if (outputClosed) throw new IOException("CLI 输出连接已关闭");
            send(frame); return turn ? response.get() : response.get(60, TimeUnit.SECONDS);
        }
        catch (TimeoutException timeout) { throw new IOException(AgentJson.string(frame, "method").equals("session/close")
                ? "CLI 会话关闭确认超时" : "CLI 初始化或会话恢复超时，请检查登录与网络", timeout); }
        finally { requests.remove(key); }
    }

    @Override public void send(JsonObject frame) {
        if (stopped.get() && !Set.of("turn/interrupt", "session/cancel").contains(AgentJson.string(frame, "method"))
                && !AgentJson.string(AgentJson.child(frame, "request"), "subtype").equals("interrupt")) return;
        AgentProcess target = process;
        try {
            if (target == null) throw new IOException("CLI 尚未启动");
            target.write(frame.toString());
        } catch (IOException exception) { fail(exception.getMessage()); }
    }

    void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        requests.values().forEach(request -> request.completeExceptionally(new CancellationException()));
        Thread.startVirtualThread(() -> {
            try { outcome.get(3, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            catch (ExecutionException | TimeoutException ignored) { }
            outcome.complete(new Outcome(false, true, null));
            AgentProcess target = process;
            if (target != null) target.close();
        });
        protocol.stop();
    }

    void forceClose() { stop(); AgentProcess target = process; if (target != null) target.close(); }
    private void fail(String error) {
        outcome.complete(new Outcome(false, stopped.get(), AgentJson.bounded(error, 4000)));
        requests.values().forEach(request -> request.completeExceptionally(new IOException(AgentJson.bounded(error, 2000))));
    }
    @Override public void text(String key, String role, String text, boolean append) { listener.text(key, role, text, append); }
    @Override public void remoteId(String id) { listener.remoteId(AgentJson.remoteId(id)); }
    @Override public void approval(String id, String title, String detail, JsonArray choices, Consumer<String> answer) {
        if (!stopped.get()) listener.approval(id, title, detail, choices, answer);
    }
    @Override public void question(String id, String title, String detail, JsonArray choices, boolean multiSelect, boolean allowCustom, Consumer<List<String>> answer) {
        if (!stopped.get()) listener.question(id, title, detail, choices, multiSelect, allowCustom, answer);
    }
    @Override public void removeApproval(String id) { listener.removeApproval(id); }
    @Override public void diagnostic(String text) { listener.text("notice-" + nextId.incrementAndGet(), "system", text, false); }
    @Override public void complete(boolean success, boolean interrupted, String error) {
        outcome.complete(new Outcome(success, interrupted, error));
    }
    @Override public void ready() { listener.running(); }
}
