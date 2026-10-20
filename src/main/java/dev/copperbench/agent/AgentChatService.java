package dev.copperbench.agent;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Workspace-owned CLI conversations. Opening history never resumes execution. */
public final class AgentChatService implements AutoCloseable {
    private final AgentWorkspaceContext context;
    private final AgentChatStore store;
    private final AgentDesktopActions desktop;
    private final Map<String, AgentProvider> providers = new LinkedHashMap<>();
    private final Map<String, Approval> approvals = new LinkedHashMap<>();
    private final Map<String, JsonObject> runMessages = new LinkedHashMap<>();
    private final Map<String, StringBuilder> runRawMessages = new HashMap<>();
    private final ScheduledExecutorService persistence = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Copperbench-agent-history"); thread.setDaemon(true); return thread;
    });
    private final ExecutorService actions = Executors.newVirtualThreadPerTaskExecutor();
    private String state = "idle";
    private String error;
    private String storageError;
    private long sequence;
    private long generation;
    private boolean dirty;
    private boolean closed;
    private boolean detecting;
    private AgentRun active;
    private Thread runThread;

    private record Approval(String id, String sourceId, String title, String detail, JsonArray choices,
                            Map<String, String> wireChoices, boolean question, boolean multiSelect, boolean allowCustom,
                            Consumer<List<String>> callback) {
        JsonObject json() {
            return AgentJson.object("id", id, "title", title, "detail", detail, "choices", choices,
                    "kind", question ? "question" : "permission", "multiSelect", multiSelect, "allowCustom", allowCustom);
        }
    }

    public AgentChatService(Path workspaceRoot) {
        this(AgentWorkspaceContext.discover(workspaceRoot.toAbsolutePath().normalize()), new AgentDesktopActions());
    }

    public AgentChatService(Path workspaceRoot, Path workspaceFile, Path sdkDirectory) {
        this(new AgentWorkspaceContext(workspaceRoot.toAbsolutePath().normalize(),
                workspaceFile == null ? null : workspaceFile.toAbsolutePath().normalize(), sdkDirectory.toAbsolutePath().normalize()), new AgentDesktopActions());
    }

    AgentChatService(AgentWorkspaceContext context, AgentDesktopActions desktop) {
        this.context = context; this.desktop = desktop; this.store = new AgentChatStore(context.root());
        if (!Files.isDirectory(context.root())) throw new IllegalArgumentException("创作工作区目录不存在");
        if (context.workspaceFile() != null && !context.workspaceFile().getParent().equals(context.root()))
            throw new IllegalArgumentException("工作区文件必须属于当前工作区");
        try {
            store.load();
            for (AgentChatStore.Conversation session : store.sessions.values()) if (session.interrupted) {
                append(session, "system", "上次创作未确认结束。已保留记录；发送新消息后才会继续，并应先核对已有任务。", null);
                session.interrupted = false;
            }
        } catch (IOException exception) { storageError = AgentJson.redacted(exception.getMessage()); error = storageError; state = "failed"; }
        for (String id : AgentProvider.IDS) providers.put(id, new AgentProvider(id, AgentProvider.label(id), AgentProvider.locate(id), null, AgentProvider.setup(id), null));
        persistence.scheduleWithFixedDelay(this::flushSafely, 500, 500, TimeUnit.MILLISECONDS);
    }

    /** Request discriminator is operation. All responses are full snapshots, including failures. */
    public synchronized JsonObject handle(JsonObject request) {
        if (request == null) request = new JsonObject();
        try {
            String operation = AgentJson.string(request, "operation");
            if (operation.isEmpty()) operation = "status";
            if (closed && !operation.equals("status")) throw new IllegalStateException("创作工作区已关闭");
            switch (operation) {
                case "status" -> { }
                case "detect" -> detect();
                case "new" -> {
                    requireIdle(); requireStore();
                    store.create(providerId(request)); error = null; state = "idle"; changed(); flush();
                }
                case "select" -> {
                    requireIdle(); requireStore(); String id = AgentJson.required(request, "conversationId");
                    if (!store.sessions.containsKey(id)) throw new IllegalArgumentException("创作会话不存在");
                    store.selected = id; state = "idle"; error = null; changed(); flush();
                }
                case "send" -> send(request);
                case "stop" -> stop();
                case "approve" -> approve(request);
                case "login" -> openLogin(providerId(request));
                case "openSetup" -> openSetup(providerId(request));
                default -> throw new IllegalArgumentException("未知的创作操作：" + operation);
            }
        } catch (IOException | RuntimeException exception) {
            error = AgentJson.bounded(exception.getMessage(), 3000);
            if (!isBusy()) state = "failed";
            sequence++;
        }
        return snapshot();
    }

    public synchronized boolean isBusy() { return active != null || Set.of("starting", "running", "waiting_approval", "stopping").contains(state); }

    private void requireIdle() { if (isBusy()) throw new IllegalStateException("请先停止或等待当前创作完成"); }
    private void requireStore() { if (storageError != null) throw new IllegalStateException(storageError); }
    private String providerId(JsonObject request) { String id = AgentJson.required(request, "provider"); AgentProvider.label(id); return id; }

    private void send(JsonObject request) throws IOException {
        requireIdle(); requireStore();
        String providerId = providerId(request), text = AgentJson.required(request, "text");
        if (text.length() > 65536) throw new IllegalArgumentException("消息不能超过 65,536 字符");
        AgentProvider provider = providers.get(providerId);
        if (provider.executable() == null || !Files.isRegularFile(provider.executable()))
            throw new IllegalStateException("未找到 " + provider.label() + "，请先安装并点击检查连接");
        String requested = AgentJson.string(request, "conversationId");
        AgentChatStore.Conversation session;
        if (!requested.isBlank()) {
            session = store.sessions.get(requested);
            if (session == null) throw new IllegalArgumentException("创作会话不存在");
        } else {
            session = store.selected == null ? null : store.sessions.get(store.selected);
            if (session == null || !session.provider.equals(providerId)) session = store.create(providerId);
        }
        if (!session.provider.equals(providerId)) throw new IllegalArgumentException("每个会话固定使用一个 CLI，请新建会话后切换工具");
        store.selected = session.id;
        if (session.messages.asList().stream().noneMatch(value -> AgentJson.string(value.getAsJsonObject(), "role").equals("user")))
            session.title = AgentJson.bounded(text.strip().replaceAll("\\s+", " "), 64);
        append(session, "user", text, null);
        session.interrupted = true;
        state = "starting"; error = null; approvals.clear(); runMessages.clear(); runRawMessages.clear();
        long turn = ++generation;
        AgentChatStore.Conversation target = session;
        AgentRun run = new AgentRun(provider, context, session.remoteId, listener(turn, target));
        active = run; changed();
        try { flush(); }
        catch (IOException exception) { active = null; state = "failed"; session.interrupted = false; throw exception; }
        runThread = Thread.ofVirtual().name("Copperbench-agent-" + providerId).start(() -> run.execute(text));
    }

    private AgentRun.Listener listener(long turn, AgentChatStore.Conversation session) {
        return new AgentRun.Listener() {
            private boolean current() { return !closed && generation == turn && active != null; }
            @Override public void text(String key, String role, String text, boolean append) {
                synchronized (AgentChatService.this) {
                    if (!current() || text == null || text.isEmpty()) return;
                    StringBuilder raw = runRawMessages.computeIfAbsent(key, ignored -> new StringBuilder());
                    if (!append) raw.setLength(0);
                    int room = 32768 - raw.length();
                    if (room > 0) raw.append(text, 0, Math.min(text.length(), room));
                    String display = AgentJson.bounded(raw.toString(), 32768);
                    if (text.length() > room) display += "\n[输出已截断]";
                    JsonObject message = runMessages.get(key);
                    if (message == null) {
                        message = AgentChatService.this.append(session, role, display, null); runMessages.put(key, message);
                        if (runMessages.size() > 1000) {
                            String oldest = runMessages.keySet().iterator().next(); runMessages.remove(oldest); runRawMessages.remove(oldest);
                        }
                    } else {
                        message.addProperty("text", display);
                        session.updatedAt = System.currentTimeMillis(); changed();
                    }
                }
            }
            @Override public void remoteId(String id) {
                synchronized (AgentChatService.this) { if (current()) { session.remoteId = id; changed(); } }
            }
            @Override public void approval(String id, String title, String detail, JsonArray choices, Consumer<String> answer) {
                addApproval(id, title, detail, choices, false, false, false, values -> answer.accept(values.getFirst()));
            }
            @Override public void question(String id, String title, String detail, JsonArray choices, boolean multiSelect, boolean allowCustom, Consumer<List<String>> answer) {
                addApproval(id, title, detail, choices, true, multiSelect, allowCustom, answer);
            }
            private void addApproval(String id, String title, String detail, JsonArray choices, boolean question, boolean multiSelect, boolean allowCustom, Consumer<List<String>> answer) {
                synchronized (AgentChatService.this) {
                    if (!current() || state.equals("stopping")) return;
                    if (approvals.size() >= 32) { error = "CLI 同时请求了过多输入，本轮已停止"; stop(); return; }
                    String localId = UUID.randomUUID().toString();
                    JsonArray safeChoices = new JsonArray();
                    Map<String, String> wireChoices = new HashMap<>();
                    for (JsonElement value : choices) {
                        JsonObject choice = value.getAsJsonObject();
                        String choiceId = "choice-" + safeChoices.size();
                        wireChoices.put(choiceId, AgentJson.string(choice, "id"));
                        JsonObject option = AgentJson.object("id", choiceId, "label", AgentJson.bounded(AgentJson.string(choice, "label"), 300));
                        String description = AgentJson.string(choice, "description");
                        if (!description.isBlank()) option.addProperty("description", AgentJson.bounded(description, 2000));
                        safeChoices.add(option);
                    }
                    approvals.put(localId, new Approval(localId, id, AgentJson.bounded(title, 300), AgentJson.bounded(detail, 12000), safeChoices,
                            Map.copyOf(wireChoices), question, multiSelect, allowCustom, answer));
                    state = "waiting_approval"; changed();
                }
            }
            @Override public void removeApproval(String id) {
                synchronized (AgentChatService.this) {
                    if (!current()) return;
                    approvals.values().removeIf(approval -> approval.sourceId().equals(id) || approval.sourceId().startsWith(id + ":"));
                    if (approvals.isEmpty() && state.equals("waiting_approval")) state = "running";
                    changed();
                }
            }
            @Override public void running() {
                synchronized (AgentChatService.this) { if (current() && state.equals("starting")) { state = "running"; changed(); } }
            }
            @Override public void finished(boolean success, boolean stopped, String failure) {
                synchronized (AgentChatService.this) {
                    if (!current()) return;
                    active = null; runThread = null; approvals.clear(); runMessages.clear(); runRawMessages.clear(); session.interrupted = false;
                    if (stopped) {
                        state = "idle"; error = null;
                        append(session, "system", "CLI 助手已停止，已有修改保留。已提交的 Copperbench 构建等任务可能仍在运行，可在任务面板查看或停止；再次发送消息才会继续创作。", null);
                    } else if (success) { state = "idle"; error = null; }
                    else {
                        state = "failed"; error = AgentJson.bounded(failure == null ? "CLI 未完成本轮创作" : failure, 4000);
                        append(session, "system", error, null);
                    }
                    changed();
                    try { flush(); } catch (IOException exception) { storageFailed(exception); }
                }
            }
        };
    }

    private void approve(JsonObject request) {
        String id = AgentJson.required(request, "id");
        Approval approval = approvals.get(id);
        if (approval == null || active == null || state.equals("stopping")) throw new IllegalArgumentException("批准请求已失效");
        List<String> answers = new ArrayList<>();
        if (approval.question()) {
            if (request.has("choices") && !request.get("choices").isJsonArray()) throw new IllegalArgumentException("问题选项必须为数组");
            JsonArray selected = AgentJson.list(request, "choices");
            if (selected.size() > approval.choices().size()) throw new IllegalArgumentException("选择了过多选项");
            Set<String> seen = new HashSet<>();
            for (JsonElement option : selected) {
                if (!option.isJsonPrimitive() || !option.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("无效的问题选项");
                String choice = option.getAsString();
                if (!seen.add(choice) || !approval.wireChoices().containsKey(choice)) throw new IllegalArgumentException("无效的问题选项");
                answers.add(approval.wireChoices().get(choice));
            }
            if (request.has("answer") && (!request.get("answer").isJsonPrimitive() || !request.get("answer").getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("自定义回答必须为文本");
            String custom = AgentJson.string(request, "answer").strip();
            if (custom.length() > 8192) throw new IllegalArgumentException("回答过长");
            if (!custom.isEmpty()) {
                if (!approval.allowCustom()) throw new IllegalArgumentException("此问题不支持自定义回答");
                answers.add(custom);
            }
            if (answers.isEmpty()) throw new IllegalArgumentException("请选择选项或填写回答");
            if (!approval.multiSelect() && answers.size() != 1) throw new IllegalArgumentException("此问题只能提交一个回答");
        } else {
            String choice = AgentJson.required(request, "choice");
            if (!approval.wireChoices().containsKey(choice)) throw new IllegalArgumentException("无效的批准选项");
            answers.add(approval.wireChoices().get(choice));
        }
        List<String> submitted = List.copyOf(answers);
        approvals.remove(id); state = approvals.isEmpty() ? "running" : "waiting_approval"; error = null; changed();
        long turn = generation;
        actions.submit(() -> {
            synchronized (AgentChatService.this) { if (closed || generation != turn || active == null || state.equals("stopping")) return; }
            approval.callback().accept(submitted);
        });
    }

    private void stop() {
        AgentRun run = active;
        if (run == null) return;
        state = "stopping"; approvals.clear(); changed();
        actions.submit(run::stop);
    }

    private void detect() {
        if (detecting) return;
        detecting = true; error = null; sequence++;
        actions.submit(() -> {
            Map<String, AgentProvider> found = new LinkedHashMap<>();
            try { for (String id : AgentProvider.IDS) {
                if (Thread.currentThread().isInterrupted()) break;
                found.put(id, AgentProvider.detect(id, context.root()));
            } } finally {
                synchronized (AgentChatService.this) {
                    if (!closed) { providers.putAll(found); detecting = false; sequence++; }
                }
            }
        });
    }

    private void openLogin(String id) {
        requireIdle(); AgentProvider provider = providers.get(id);
        if (provider.executable() == null) throw new IllegalStateException("请先安装 " + provider.label());
        if (id.equals("deepseek")) throw new IllegalStateException("请运行 dsh web，在 Settings → Models 中配置模型，然后返回发送消息。");
        actions.submit(() -> {
            try {
                desktop.login(provider, context.root());
                synchronized (AgentChatService.this) {
                    if (!closed) {
                        selectedSystem("已打开 " + provider.label() + " 的官方登录终端。完成登录后返回检查连接；打开窗口不代表已登录。");
                        sequence++;
                    }
                }
            } catch (IOException exception) { actionFailed(exception); }
        });
    }

    private void openSetup(String id) {
        AgentProvider provider = providers.get(id);
        actions.submit(() -> { try { desktop.setup(provider); } catch (IOException exception) { actionFailed(exception); } });
    }

    private synchronized void actionFailed(Exception failure) {
        if (!closed) { error = AgentJson.bounded(failure.getMessage(), 2000); sequence++; }
    }

    private void selectedSystem(String message) {
        AgentChatStore.Conversation session = store.sessions.get(store.selected);
        if (session != null) append(session, "system", message, null);
    }

    private JsonObject append(AgentChatStore.Conversation session, String role, String text, String id) {
        JsonObject message = AgentJson.object("id", id == null ? UUID.randomUUID().toString() : id, "role", role,
                "text", AgentJson.bounded(text, 32768), "at", System.currentTimeMillis());
        if (session.messages.size() >= 1000) {
            // Preserve a visible retention marker; native CLI history remains authoritative for resume.
            session.messages.remove(0);
            if (session.messages.size() >= 999) session.messages.remove(0);
            session.messages.asList().addFirst(AgentJson.object("id", "retention", "role", "system", "text", "仅显示最近 1,000 条记录；CLI 保留其原生会话历史。", "at", System.currentTimeMillis()));
        }
        session.messages.add(message); session.updatedAt = System.currentTimeMillis(); changed(); return message;
    }

    private void changed() { dirty = true; sequence++; }
    private void flush() throws IOException { if (dirty && storageError == null) { store.save(); dirty = false; } }
    private synchronized void flushSafely() {
        if (closed || !dirty || storageError != null) return;
        try { flush(); } catch (IOException exception) { storageFailed(exception); }
    }
    private void storageFailed(IOException exception) {
        storageError = AgentJson.bounded(exception.getMessage(), 3000); error = storageError;
        if (active != null) stop(); else state = "failed";
        sequence++;
    }

    private JsonObject snapshot() {
        AgentChatStore.Conversation selected = store.sessions.get(store.selected);
        JsonArray sessions = new JsonArray();
        store.sessions.values().stream().sorted(Comparator.comparingLong((AgentChatStore.Conversation session) -> session.updatedAt).reversed())
                .forEach(session -> sessions.add(session.summary()));
        JsonArray providerStates = new JsonArray(); providers.values().forEach(provider -> providerStates.add(provider.json()));
        JsonArray pending = new JsonArray(); approvals.values().forEach(approval -> pending.add(approval.json()));
        JsonObject result = AgentJson.object("state", state, "conversationId", selected == null ? null : selected.id,
                "provider", selected == null ? null : selected.provider, "sessions", sessions,
                "messages", selected == null ? new JsonArray() : selected.messages.deepCopy(), "approvals", pending,
                "providers", providerStates, "sequence", sequence, "detecting", detecting);
        if (storageError != null) result.addProperty("error", storageError);
        else if (error != null && !error.isBlank()) result.addProperty("error", error);
        return result.deepCopy();
    }

    @Override public void close() {
        AgentRun run; Thread worker;
        synchronized (this) {
            if (closed) return;
            closed = true; run = active; worker = runThread; approvals.clear();
            AgentChatStore.Conversation session = store.sessions.get(store.selected);
            if (session != null && run != null) { session.interrupted = true; changed(); }
            try { flush(); } catch (IOException exception) { error = AgentJson.bounded(exception.getMessage(), 2000); }
        }
        persistence.shutdownNow(); actions.shutdownNow();
        if (run != null) run.forceClose();
        if (worker != null) try { worker.join(4000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        synchronized (this) { active = null; runThread = null; state = "idle"; sequence++; }
    }
}
