package dev.copperbench.agent;

import com.google.gson.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded model identifiers and provider catalog projections; no credentials are projected. */
final class AgentModels {
    static final int LIMIT = 1000;
    private AgentModels() { }

    static String id(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:/@+\\-]{0,199}"))
            throw new IllegalArgumentException("模型 ID 格式无效");
        return value;
    }

    static void add(JsonArray models, String id, String label) {
        if (models.size() >= LIMIT) throw new IllegalArgumentException("模型列表超过读取上限");
        String valid = id(id);
        if (valid == null || models.asList().stream().anyMatch(item -> AgentJson.string(item.getAsJsonObject(), "id").equals(valid))) return;
        models.add(AgentJson.object("id", valid, "label", label == null || label.isBlank() ? valid : AgentJson.bounded(label, 180)));
    }

    static JsonObject catalog(JsonArray models, String preferred, String source, boolean requiresSelection) {
        if (models.isEmpty()) throw new IllegalArgumentException("当前服务未返回可用模型，请刷新或输入模型 ID");
        JsonObject result = AgentJson.object("state", "ready", "models", models, "source", source,
                "requiresSelection", requiresSelection, "at", System.currentTimeMillis());
        if (preferred != null && models.asList().stream().anyMatch(item -> AgentJson.string(item.getAsJsonObject(), "id").equals(preferred)))
            result.addProperty("defaultModel", preferred);
        return result;
    }

    static JsonObject modelOption(JsonObject session) {
        return option(session, "model");
    }

    static JsonObject option(JsonObject session, String category) {
        for (JsonElement entry : AgentJson.list(session, "configOptions")) {
            JsonObject option = entry.getAsJsonObject();
            if (AgentJson.string(option, "category").equals(category) && AgentJson.string(option, "type").equals("select")) return option;
        }
        return new JsonObject();
    }

    /** DSH uses a JSON tuple as its opaque ACP value, not a command-line model ID. */
    static String acpId(String value) {
        String[] route = acpRoute(value);
        return route == null ? id(value) : id(route[0] + "/" + route[1]);
    }

    private static String[] acpRoute(String value) {
        if (!value.startsWith("[")) return null;
        try {
            JsonArray route = JsonParser.parseString(value).getAsJsonArray();
            if (route.size() != 2 || !route.asList().stream().allMatch(item -> item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("模型 ID 格式无效");
            return new String[] { Objects.requireNonNull(id(route.get(0).getAsString())), Objects.requireNonNull(id(route.get(1).getAsString())) };
        } catch (RuntimeException error) { throw new IllegalArgumentException("模型 ID 格式无效", error); }
    }

    static List<JsonObject> choices(JsonObject option) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement item : AgentJson.list(option, "options")) {
            JsonObject row = item.getAsJsonObject();
            if (row.has("group")) for (JsonElement nested : AgentJson.list(row, "options")) result.add(nested.getAsJsonObject());
            else result.add(row);
        }
        return result;
    }

    static String acpValue(JsonObject option, String model) {
        List<String> matches = new ArrayList<>();
        for (JsonObject row : choices(option)) {
            String value = AgentJson.required(row, "value");
            if (model.equals(acpId(value))) return value;
            String[] route = acpRoute(value);
            if (route != null && model.equals(route[1])) matches.add(value);
        }
        if (matches.size() > 1) throw new IllegalArgumentException("多个提供方包含此模型，请从列表选择完整模型 ID");
        return matches.isEmpty() ? model : matches.getFirst();
    }

    static JsonObject acpCatalog(JsonObject session) {
        JsonArray models = new JsonArray(); JsonObject option = modelOption(session);
        if (!option.isEmpty()) {
            for (JsonElement entry : AgentJson.list(option, "options")) {
                JsonObject row = entry.getAsJsonObject();
                if (row.has("group")) {
                    for (JsonElement nested : AgentJson.list(row, "options")) {
                        JsonObject value = nested.getAsJsonObject(); add(models, acpId(AgentJson.string(value, "value")), AgentJson.string(value, "name"));
                    }
                } else add(models, acpId(AgentJson.string(row, "value")), AgentJson.string(row, "name"));
            }
            return catalog(models, acpId(AgentJson.string(option, "currentValue")), "cli", false);
        }
        JsonObject legacy = AgentJson.child(session, "models");
        for (JsonElement entry : AgentJson.list(legacy, "availableModels")) {
            JsonObject row = entry.getAsJsonObject(); add(models, AgentJson.string(row, "modelId"), AgentJson.string(row, "name"));
        }
        return catalog(models, AgentJson.string(legacy, "currentModelId"), "cli", false);
    }

    static JsonObject antigravity(AgentProvider provider, java.nio.file.Path scratch) throws Exception {
        StringBuilder output = new StringBuilder();
        AtomicReference<Exception> failed = new AtomicReference<>();
        try (AgentProcess process = new AgentProcess(provider.executable(), List.of("models"), scratch)) {
            Thread reader = Thread.startVirtualThread(() -> {
                try { AgentProcess.lines(process.process.getInputStream(), line -> {
                    synchronized (output) {
                        if (output.length() + line.length() > 256 * 1024) throw new IllegalArgumentException("模型列表超过读取上限");
                        output.append(line).append('\n');
                    }
                }); } catch (Exception error) { failed.set(error); }
            });
            Thread errors = Thread.startVirtualThread(() -> {
                try { AgentProcess.lines(process.process.getErrorStream(), ignored -> {}); }
                catch (IOException ignored) { }
            });
            process.endInput();
            if (!process.await(Duration.ofSeconds(25))) throw new IOException("获取模型列表超时");
            reader.join(1000); errors.join(1000);
            if (failed.get() != null) throw failed.get();
            if (process.process.exitValue() != 0) throw new IOException("模型列表命令失败，请检查 CLI 登录与服务配置");
            JsonArray models = new JsonArray();
            synchronized (output) {
                for (String line : output.toString().split("\\R")) {
                    String[] columns = AgentJson.redacted(line).split("\\t", 2);
                    // agy models 1.4.2 documents a tab-separated ID/display-name list; notices are not models.
                    if (columns.length == 2) add(models, columns[0].strip(), columns[1].strip());
                }
            }
            return catalog(models, null, "cli", false);
        }
    }
}
