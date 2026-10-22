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
    private final AgentAttachments attachments;
    private final AgentDesktopActions desktop;
    private final Map<String, AgentProvider> providers = new LinkedHashMap<>();
    private final Map<String, JsonObject> connections = new LinkedHashMap<>();
    private final Map<String, JsonObject> modelCatalogs = new LinkedHashMap<>();
    private final Map<String, AgentRun> modelRuns = new LinkedHashMap<>();
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
    private boolean checking;
    private AgentRun checkRun;
    private JsonObject historyResult;
    private AgentRun active;
    private Thread runThread;
    private JsonObject runUserMessage;

    private record Approval(String id, String sourceId, String title, String detail, JsonArray choices,
                            Map<String, String> wireChoices, boolean question, boolean multiSelect, boolean allowCustom,
                            Consumer<List<String>> callback) {
        JsonObject json() {
            JsonObject result = AgentJson.object("id", id, "title", title, "detail", detail, "choices", choices,
                    "kind", question ? "question" : "permission", "multiSelect", multiSelect, "allowCustom", allowCustom);
            if (!question) AgentCopy.put(result, "title", title);
            return result;
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
        this.attachments = new AgentAttachments(context.root());
        if (!Files.isDirectory(context.root())) throw new IllegalArgumentException("创作工作区目录不存在");
        if (context.workspaceFile() != null && !context.workspaceFile().getParent().equals(context.root()))
            throw new IllegalArgumentException("工作区文件必须属于当前工作区");
        try {
            store.load();
            for (AgentChatStore.Conversation session : store.sessions.values()) if (session.interrupted) {
                append(session, "system", "上次创作未确认结束。已保留记录；发送新消息后才会继续，并应先核对已有任务。", null);
                session.interrupted = false;
            }
        } catch (IOException exception) { store.unreadable = true; storageError = AgentJson.redacted(exception.getMessage()); error = storageError; state = "failed"; }
        for (String id : AgentProvider.IDS) providers.put(id, new AgentProvider(id, AgentProvider.label(id), AgentProvider.locate(id), null, AgentProvider.setup(id), null));
        persistence.scheduleWithFixedDelay(this::flushSafely, 500, 500, TimeUnit.MILLISECONDS);
    }

    /** Request discriminator is operation. All responses are full snapshots, including failures. */
    public synchronized JsonObject handle(JsonObject request) {
        if (request == null) request = new JsonObject();
        boolean linkOpened = false;
        try {
            String operation = AgentJson.string(request, "operation");
            if (Set.of("exportConversation", "deleteConversation", "deleteConversations", "recoverHistory", "retryHistory").contains(operation)) historyResult = null;
            if (operation.isEmpty()) operation = "status";
            if (closed && !operation.equals("status")) throw new IllegalStateException("创作工作区已关闭");
            switch (operation) {
                case "status" -> { }
                case "detect" -> detect();
                case "checkConnection" -> checkConnection(providerId(request));
                case "models" -> discoverModels(providerId(request));
                case "exportConversation" -> {
                    String id = AgentJson.required(request, "conversationId");
                    historyResult = AgentJson.object("operation", operation, "conversationId", id, "path", store.export(id).toString());
                    sequence++;
                }
                case "deleteConversation", "deleteConversations" -> {
                    List<String> ids = operation.equals("deleteConversation") ? List.of(AgentJson.required(request, "conversationId"))
                            : AgentJson.list(request, "conversationIds").asList().stream().map(JsonElement::getAsString).distinct().toList();
                    if (ids.size() > 100) throw new IllegalArgumentException("选择了过多选项");
                    if (isBusy() && ids.contains(store.selected)) throw new IllegalStateException("请先停止当前会话并等待收尾后再删除");
                    if (!AgentJson.bool(request, "confirmed")) throw new IllegalArgumentException("请确认此历史操作");
                    Set<String> previousAttachments = referencedAttachments();
                    store.delete(ids); storageError = null; error = null; dirty = false;
                    historyResult = AgentJson.object("operation", operation);
                    previousAttachments.removeAll(referencedAttachments());
                    for (String id : previousAttachments) if (!attachments.isDraft(id)) {
                        try { attachments.remove(id); }
                        catch (IOException failure) { error = "会话已删除，但部分附件副本未能清理：" + AgentJson.bounded(failure.getMessage(), 2000); }
                    }
                    if (!isBusy()) state = "idle";
                    sequence++;
                }
                case "recoverHistory" -> {
                    requireIdle();
                    if (!AgentJson.bool(request, "confirmed")) throw new IllegalArgumentException("请确认此历史操作");
                    historyResult = AgentJson.object("operation", operation, "path", store.recover().toString());
                    storageError = null; error = null; dirty = false; state = "idle"; sequence++;
                }
                case "retryHistory" -> {
                    requireIdle();
                    if (store.unreadable) throw new IllegalStateException("请先备份并恢复无法读取的历史记录");
                    store.save(); storageError = null; error = null; dirty = false; state = "idle"; sequence++;
                    historyResult = AgentJson.object("operation", operation);
                }
                case "new" -> {
                    requireIdle(); requireStore();
                    AgentTurnOptions options = AgentTurnOptions.from(request, AgentTurnOptions.DEFAULT);
                    store.create(providerId(request), requestedModel(request)).options = options;
                    error = null; state = "idle"; changed(); flush();
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
                case "openLink" -> {
                    String url = AgentJson.required(request, "url");
                    desktop.browse(url);
                    linkOpened = true;
                }
                default -> throw new IllegalArgumentException("未知的创作操作：" + operation);
            }
        } catch (IOException | RuntimeException exception) {
            error = AgentJson.bounded(exception.getMessage(), 3000);
            if (!isBusy() && Set.of("send", "new", "select").contains(AgentJson.string(request, "operation"))) state = "failed";
            sequence++;
        }
        JsonObject result = snapshot();
        if (AgentJson.string(request, "operation").equals("openLink")) result.addProperty("linkOpened", linkOpened);
        return result;
    }

    public synchronized boolean isBusy() { return checking || active != null || Set.of("starting", "running", "waiting_approval", "stopping").contains(state); }

    /** Bounded attachment transfer endpoints. Responses contain no conversation snapshot or host paths. */
    public synchronized JsonObject handleAttachment(JsonObject request) {
        try {
            if (closed) throw new IllegalStateException("创作工作区已关闭");
            return attachments.handle(request, referencedAttachments());
        } catch (IOException | RuntimeException failure) { return attachmentFailure(failure); }
    }

    /** Called only with paths supplied by a native file selection or drop, never by browser JSON. */
    public synchronized JsonObject importDroppedAttachments(List<Path> paths) {
        try {
            if (closed) throw new IllegalStateException("创作工作区已关闭");
            return AgentJson.object("ok", true, "attachments", attachments.importFiles(paths));
        } catch (IOException | RuntimeException failure) { return attachmentFailure(failure); }
    }

    private static JsonObject attachmentFailure(Exception failure) {
        JsonObject result = AgentJson.object("ok", false);
        AgentCopy.put(result, "error", AgentJson.bounded(failure.getMessage(), 1000)); return result;
    }

    private Set<String> referencedAttachments() {
        Set<String> ids = new HashSet<>();
        for (AgentChatStore.Conversation session : store.sessions.values()) ids.addAll(session.attachmentIds());
        return ids;
    }

    private void requireIdle() { if (isBusy()) throw new IllegalStateException("请先停止或等待当前创作完成"); }
    private void requireStore() { if (storageError != null) throw new IllegalStateException(storageError); }
    private String providerId(JsonObject request) { String id = AgentJson.required(request, "provider"); AgentProvider.label(id); return id; }
    private String requestedModel(JsonObject request) {
        JsonElement value = request.get("model");
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("模型 ID 格式无效");
        return AgentModels.id(value.getAsString());
    }

    private void send(JsonObject request) throws IOException {
        requireIdle(); requireStore();
        String providerId = providerId(request), text = AgentJson.string(request, "text");
        List<AgentAttachments.Item> selectedAttachments = attachments.resolve(request);
        if (text.isBlank() && selectedAttachments.isEmpty()) throw new IllegalArgumentException("请填写消息或添加附件");
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
            if (session == null || !session.provider.equals(providerId)) session = store.create(providerId, requestedModel(request));
        }
        if (!session.provider.equals(providerId)) throw new IllegalArgumentException("每个会话固定使用一个 CLI，请新建会话后切换工具");
        if (request.has("model") && !Objects.equals(session.model, requestedModel(request)))
            throw new IllegalArgumentException("每个会话固定使用一个模型，请新建对话后切换模型");
        AgentTurnOptions options = AgentTurnOptions.from(request, session.options);
        long turn = generation + 1;
        AgentChatStore.Conversation target = session;
        // Provider-specific validation happens before the user's draft is admitted to history.
        AgentRun run = new AgentRun(provider, context, session.remoteId, session.model, options, selectedAttachments, listener(turn, target));
        session.options = options;
        store.selected = session.id;
        if (session.messages.asList().stream().noneMatch(value -> AgentJson.string(value.getAsJsonObject(), "role").equals("user"))) {
            session.title = AgentJson.bounded(text.isBlank() ? selectedAttachments.getFirst().name() : text.strip().replaceAll("\\s+", " "), 64);
            session.defaultTitle = false;
        }
        runUserMessage = append(session, "user", text, null);
        runUserMessage.addProperty("delivery", "pending");
        if (!selectedAttachments.isEmpty()) {
            JsonArray files = new JsonArray(); selectedAttachments.forEach(item -> files.add(item.json()));
            runUserMessage.add("attachments", files);
        }
        session.interrupted = true;
        state = "starting"; error = null; approvals.clear(); runMessages.clear(); runRawMessages.clear();
        generation = turn;
        active = run; changed();
        try { flush(); }
        catch (IOException exception) { active = null; state = "failed"; session.interrupted = false; storageFailed(exception); throw exception; }
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
                        if (role.equals("system") || role.equals("tool")) AgentCopy.put(message, "text", display);
                        else message.addProperty("text", display);
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
                        if (!question) AgentCopy.put(option, "label", AgentJson.bounded(AgentJson.string(choice, "label"), 300));
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
                synchronized (AgentChatService.this) { if (current() && !state.equals("stopping")) {
                    if (runUserMessage != null) {
                        runUserMessage.addProperty("delivery", "sent");
                        attachments.sent(AgentJson.list(runUserMessage, "attachments"));
                    }
                    if (state.equals("starting")) state = "running";
                    changed();
                } }
            }
            @Override public void finished(boolean success, boolean stopped, String failure) {
                synchronized (AgentChatService.this) {
                    if (!current()) return;
                    if (runUserMessage != null && "pending".equals(AgentJson.string(runUserMessage, "delivery"))) runUserMessage.addProperty("delivery", "failed");
                    runUserMessage = null;
                    active = null; runThread = null; approvals.clear(); runMessages.clear(); runRawMessages.clear(); session.interrupted = false;
                    if (stopped) {
                        state = "idle"; error = null;
                        append(session, "system", "CLI 助手已停止，已有修改保留。已提交的 Copperbench 构建等任务可能仍在运行，可在任务面板查看或停止；再次发送消息才会继续创作。", null);
                    } else if (success) {
                        state = "idle"; error = null;
                        connections.put(session.provider, AgentJson.object("state", "verified", "scope", "response", "at", System.currentTimeMillis()));
                    }
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
        if (checkRun != null) { actions.submit(checkRun::stop); return; }
        AgentRun run = active;
        if (run == null) return;
        state = "stopping"; approvals.clear(); changed();
        actions.submit(run::stop);
    }

    private void detect() {
        if (detecting) return;
        detecting = true; sequence++;
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

    private void discoverModels(String id) {
        if ("loading".equals(AgentJson.string(modelCatalogs.get(id), "state"))) return;
        AgentProvider provider = providers.get(id);
        modelCatalogs.put(id, AgentJson.object("state", "loading", "models", new JsonArray(), "requiresSelection", id.equals("claude")));
        sequence++;
        actions.submit(() -> {
            Path scratch = null; AgentRun run = null; JsonObject result; String catalogSource = "cli";
            try {
                if (provider.executable() == null) throw new IllegalStateException("请先安装 " + provider.label());
                ClaudeModelSource source = id.equals("claude") ? ClaudeModelSource.configured(context.root()) : null;
                if (source != null) { catalogSource = "service"; result = source.fetch(); }
                else {
                    scratch = Files.createTempDirectory("copperbench-models-");
                    if (id.equals("antigravity")) result = AgentModels.antigravity(provider, scratch);
                    else {
                        run = new AgentRun(provider, new AgentWorkspaceContext(scratch, null, context.sdkDirectory()), null, silentListener());
                        synchronized (AgentChatService.this) { if (closed) return; modelRuns.put(id, run); }
                        AgentRun pending = run;
                        ScheduledFuture<?> timeout = persistence.schedule(pending::stop, 25, TimeUnit.SECONDS);
                        try { result = run.models(); } finally { timeout.cancel(false); }
                    }
                }
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                Throwable cause = failure instanceof ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
                result = AgentJson.object("state", "failed", "models", new JsonArray(), "source", catalogSource, "requiresSelection", id.equals("claude"));
                String message = cause instanceof CancellationException ? "获取模型列表超时" : AgentJson.bounded(cause.getMessage(), 1500);
                AgentCopy.put(result, "error", message.isBlank() ? "当前服务未返回可用模型，请刷新或输入模型 ID" : message);
            } finally {
                synchronized (AgentChatService.this) { modelRuns.remove(id); }
                if (scratch != null) try { Files.deleteIfExists(scratch); } catch (IOException ignored) { }
            }
            synchronized (AgentChatService.this) { if (!closed) { modelCatalogs.put(id, result); sequence++; } }
        });
    }

    private static AgentRun.Listener silentListener() {
        return new AgentRun.Listener() {
            @Override public void text(String key, String role, String text, boolean append) { }
            @Override public void remoteId(String id) { }
            @Override public void approval(String id, String title, String detail, JsonArray choices, Consumer<String> answer) { }
            @Override public void question(String id, String title, String detail, JsonArray choices, boolean multi, boolean custom, Consumer<List<String>> answer) { }
            @Override public void removeApproval(String id) { }
            @Override public void running() { }
            @Override public void finished(boolean success, boolean stopped, String error) { }
        };
    }

    private void checkConnection(String id) throws IOException {
        requireIdle();
        AgentProvider provider = providers.get(id);
        if (provider.executable() == null) throw new IllegalStateException("请先安装 " + provider.label());
        if (id.equals("antigravity")) throw new IllegalArgumentException("Antigravity has no standalone protocol handshake. Send a message to verify a model request.");
        Path scratch = Files.createTempDirectory("copperbench-cli-check-");
        AgentWorkspaceContext isolated = new AgentWorkspaceContext(scratch, null, context.sdkDirectory());
        checking = true;
        connections.put(id, AgentJson.object("state", "checking", "scope", "protocol")); sequence++;
        checkRun = new AgentRun(provider, isolated, null, new AgentRun.Listener() {
            @Override public void text(String key, String role, String text, boolean append) { }
            @Override public void remoteId(String remote) { }
            @Override public void approval(String key, String title, String detail, JsonArray choices, Consumer<String> answer) { }
            @Override public void question(String key, String title, String detail, JsonArray choices, boolean multi, boolean custom, Consumer<List<String>> answer) { }
            @Override public void removeApproval(String key) { }
            @Override public void running() { }
            @Override public void finished(boolean success, boolean stopped, String failure) {
                synchronized (AgentChatService.this) {
                    checking = false; checkRun = null;
                    JsonObject status = AgentJson.object("state", success ? "verified" : "failed", "scope", "protocol", "at", System.currentTimeMillis());
                    if (!success) AgentCopy.put(status, "error", stopped ? "连接检查已取消" : failure);
                    if (!closed) { connections.put(id, status); sequence++; }
                }
                // Do not recursively remove anything a CLI may have left behind.
                try { Files.deleteIfExists(scratch); } catch (IOException ignored) { }
            }
        });
        AgentRun probe = checkRun;
        actions.submit(probe::check);
        persistence.schedule(() -> { synchronized (AgentChatService.this) { if (checkRun == probe) actions.submit(probe::stop); } }, 25, TimeUnit.SECONDS);
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
        if (role.equals("system") || role.equals("tool")) AgentCopy.put(message, "text", AgentJson.bounded(text, 32768));
        if (session.messages.size() >= 1000) {
            // Preserve a visible retention marker; native CLI history remains authoritative for resume.
            session.retainAttachments(session.messages.remove(0).getAsJsonObject());
            if (session.messages.size() >= 999) session.retainAttachments(session.messages.remove(0).getAsJsonObject());
            JsonObject marker = AgentJson.object("id", "retention", "role", "system", "at", System.currentTimeMillis());
            AgentCopy.put(marker, "text", "仅显示最近 1,000 条记录；CLI 保留其原生会话历史。");
            session.messages.asList().addFirst(marker);
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
        JsonArray providerStates = new JsonArray(); providers.values().forEach(provider -> {
            JsonObject info = provider.json();
            if (connections.containsKey(provider.id())) info.add("connection", connections.get(provider.id()).deepCopy());
            if (modelCatalogs.containsKey(provider.id())) info.add("modelCatalog", modelCatalogs.get(provider.id()).deepCopy());
            providerStates.add(info);
        });
        JsonArray pending = new JsonArray(); approvals.values().forEach(approval -> pending.add(approval.json()));
        JsonObject result = AgentJson.object("state", state, "conversationId", selected == null ? null : selected.id,
                "provider", selected == null ? null : selected.provider, "sessions", sessions,
                "model", selected == null ? null : selected.model,
                "usesSessionModel", selected != null && selected.model == null && selected.remoteId != null,
                "messages", selected == null ? new JsonArray() : selected.messages.deepCopy(), "approvals", pending,
                "providers", providerStates, "sequence", sequence, "detecting", detecting, "checking", checking,
                "history", store.capacity(), "storageBlocked", storageError != null);
        if (storageError != null) AgentCopy.put(result, "error", storageError);
        else if (error != null && !error.isBlank()) AgentCopy.put(result, "error", error);
        if (historyResult != null) result.add("historyResult", historyResult.deepCopy());
        if (selected != null) selected.options.put(result);
        return result.deepCopy();
    }

    @Override public void close() {
        AgentRun run; Thread worker; List<AgentRun> catalogs;
        synchronized (this) {
            if (closed) return;
            closed = true; run = active != null ? active : checkRun; worker = runThread; approvals.clear();
            catalogs = List.copyOf(modelRuns.values());
            AgentChatStore.Conversation session = store.sessions.get(store.selected);
            if (session != null && run != null) { session.interrupted = true; changed(); }
            try { flush(); } catch (IOException exception) { error = AgentJson.bounded(exception.getMessage(), 2000); }
        }
        persistence.shutdownNow(); actions.shutdownNow();
        catalogs.forEach(AgentRun::forceClose);
        if (run != null) run.forceClose();
        if (worker != null) try { worker.join(4000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        synchronized (this) { attachments.discardUnusedDrafts(referencedAttachments()); attachments.close(); }
        synchronized (this) { active = null; runThread = null; state = "idle"; sequence++; }
    }
}
