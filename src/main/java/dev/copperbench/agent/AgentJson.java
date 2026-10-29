package dev.copperbench.agent;

import com.google.gson.*;

import java.util.regex.Pattern;

/** Wire helpers and output filtering. Raw wire frames and process environments are never logged. */
final class AgentJson {
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|password|client[_-]?secret)\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\s\\\"',}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\\bauthorization\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)(?:Bearer|Basic|Token)\\s+[^\\s\\\"',}]+");
    private static final Pattern KEY = Pattern.compile("\\b(?:sk-[A-Za-z0-9_-]{12,}|xai-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)");
    private static final Pattern QUERY_SECRET = Pattern.compile("(?i)([?&](?:token|key|api_key|access_token|refresh_token)=)[^&#\\s]+");
    private static final Pattern ARG_SECRET = Pattern.compile("(?i)(--(?:api-key|access-token|token|password|secret)\\s+)[^\\s]+");
    private AgentJson() { }

    static JsonObject object(Object... pairs) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            JsonElement json = value == null ? JsonNull.INSTANCE : value instanceof JsonElement element ? element
                    : value instanceof Boolean bool ? new JsonPrimitive(bool)
                    : value instanceof Number number ? new JsonPrimitive(number) : new JsonPrimitive(value.toString());
            result.add(pairs[i].toString(), json);
        }
        return result;
    }

    static JsonArray array(JsonElement... elements) {
        JsonArray result = new JsonArray();
        for (JsonElement element : elements) result.add(element);
        return result;
    }

    static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    static String required(JsonObject object, String key) {
        String result = string(object, key);
        if (result.isBlank()) throw new IllegalArgumentException("缺少 " + key);
        return result;
    }

    static JsonObject child(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    static JsonArray list(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    static String redacted(String text) {
        if (text == null) return "";
        text = text.replaceAll("\\u001B\\[[;?0-9]*[ -/]*[@-~]", "");
        // Consume a complete authorization value before the generic field filter can hide only its scheme.
        text = AUTHORIZATION.matcher(text).replaceAll("$1[已隐藏]");
        text = BEARER.matcher(text).replaceAll("Bearer [已隐藏]");
        text = SECRET_FIELD.matcher(text).replaceAll("$1[已隐藏]");
        text = QUERY_SECRET.matcher(text).replaceAll("$1[已隐藏]");
        text = ARG_SECRET.matcher(text).replaceAll("$1[已隐藏]");
        return KEY.matcher(text).replaceAll("[已隐藏]");
    }

    static String bounded(String text, int limit) {
        String safe = redacted(text);
        return safe.length() <= limit ? safe : safe.substring(0, limit) + "\n[输出已截断]";
    }

    static JsonElement redactedTree(JsonElement value) {
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonObject()) {
            JsonObject safe = new JsonObject(); value.getAsJsonObject().entrySet().forEach(entry -> {
                String key = entry.getKey();
                safe.add(key, key.toLowerCase(java.util.Locale.ROOT).matches(".*(?:password|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|authorization).*")
                        ? new JsonPrimitive("[redacted]") : redactedTree(entry.getValue()));
            }); return safe;
        }
        if (value.isJsonArray()) { JsonArray safe = new JsonArray(); value.getAsJsonArray().forEach(item -> safe.add(redactedTree(item))); return safe; }
        return value.getAsJsonPrimitive().isString() ? new JsonPrimitive(redacted(value.getAsString())) : value.deepCopy();
    }

    static String detail(JsonElement value) { return bounded(value == null ? "" : value.toString(), 12_000); }

    static String remoteId(String value) {
        if (!value.matches("[A-Za-z0-9_.:-]{1,256}"))
            throw new IllegalArgumentException("CLI 返回了无效的会话标识");
        return value;
    }
}
