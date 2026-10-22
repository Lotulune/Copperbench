package dev.copperbench.agent;

import com.google.gson.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Reads only the host's configured model endpoint. Chat still runs through the installed Claude CLI. */
final class ClaudeModelSource {
    private final URI endpoint;
    private final String credential;
    private final String authHeader;
    private final String preferred;
    private final boolean custom;

    private ClaudeModelSource(URI endpoint, String credential, String authHeader, String preferred, boolean custom) {
        this.endpoint = endpoint; this.credential = credential; this.authHeader = authHeader; this.preferred = preferred; this.custom = custom;
    }

    static ClaudeModelSource configured(Path workspace) throws IOException {
        return configured(System.getenv(), Path.of(System.getProperty("user.home")), workspace);
    }

    static ClaudeModelSource configured(Map<String, String> environment, Path home, Path workspace) throws IOException {
        String directory = environment.get("CLAUDE_CONFIG_DIR");
        Path settingsFile = directory == null || directory.isBlank() ? home.resolve(".claude/settings.json") : Path.of(directory).resolve("settings.json");
        JsonObject settings = readSettings(settingsFile);
        JsonObject configuredEnv = AgentJson.child(settings, "env");
        String base = setting(environment, configuredEnv, "ANTHROPIC_BASE_URL");
        boolean custom = !base.isBlank() && !base.replaceAll("/+$", "").equals("https://api.anthropic.com");
        String token = setting(environment, configuredEnv, "ANTHROPIC_AUTH_TOKEN");
        String key = setting(environment, configuredEnv, "ANTHROPIC_API_KEY");
        if ((token + key).chars().anyMatch(c -> c < 0x20 || c > 0x7e)) throw new IOException("模型凭据格式无效，请检查主机 CLI 配置");
        if (!custom && token.isBlank() && key.isBlank()) return null; // OAuth and account-managed discovery stay in the native CLI.
        if (base.isBlank()) base = "https://api.anthropic.com";
        URI configured;
        try { configured = URI.create(base.replaceAll("/+$", "")); }
        catch (RuntimeException invalid) { throw new IOException("模型服务地址无效"); }
        if (!Set.of("https", "http").contains(configured.getScheme()) || configured.getHost() == null
                || configured.getUserInfo() != null || configured.getRawQuery() != null || configured.getRawFragment() != null)
            throw new IOException("模型服务地址无效");
        // Workspace content must not redirect a credential loaded from the host profile.
        for (String name : List.of("settings.json", "settings.local.json")) {
            JsonObject local = AgentJson.child(readSettings(workspace.resolve(".claude").resolve(name)), "env");
            String localBase = AgentJson.string(local, "ANTHROPIC_BASE_URL").replaceAll("/+$", "");
            if (!localBase.isBlank() && !localBase.equals(configured.toString()))
                throw new IOException("项目覆盖了模型服务地址，请先在主机 CLI 配置中确认该源");
            for (String nameKey : List.of("ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_API_KEY")) {
                String localCredential = AgentJson.string(local, nameKey);
                if (!localCredential.isBlank() && !localCredential.equals(setting(environment, configuredEnv, nameKey)))
                    throw new IOException("项目覆盖了模型服务配置，请先在主机 CLI 配置中确认该源");
            }
        }
        String path = configured.getPath();
        URI endpoint = URI.create(configured + (path.endsWith("/v1") ? "/models" : "/v1/models"));
        String model = setting(environment, configuredEnv, "ANTHROPIC_MODEL");
        if (model.isBlank()) model = AgentJson.string(settings, "model");
        return new ClaudeModelSource(endpoint, !token.isBlank() ? "Bearer " + token : key,
                !token.isBlank() ? "Authorization" : "x-api-key", model.isBlank() ? null : model, custom);
    }

    private static JsonObject readSettings(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        if (Files.size(file) > 1024 * 1024) throw new IOException("CLI 配置文件过大，无法读取模型服务设置");
        try { return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("无法读取 CLI 的模型服务设置"); }
    }

    private static String setting(Map<String, String> environment, JsonObject settings, String name) {
        String value = environment.get(name);
        return value != null && !value.isBlank() ? value : AgentJson.string(settings, name);
    }

    JsonObject fetch() throws IOException, InterruptedException {
        JsonArray models = new JsonArray(); Set<String> cursors = new HashSet<>(); String after = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            for (int page = 0; page < 10; page++) {
                URI pageUrl = after == null ? endpoint : URI.create(endpoint + "?limit=100&after_id=" + URLEncoder.encode(after, StandardCharsets.UTF_8));
                HttpRequest.Builder request = HttpRequest.newBuilder(pageUrl).GET().timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json").header("anthropic-version", "2023-06-01");
                if (!credential.isBlank()) request.header(authHeader, credential);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException("获取模型列表超时");
                CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request.build(), ignored -> new LimitedBody());
                HttpResponse<byte[]> response;
                try { response = pending.get(remaining, TimeUnit.NANOSECONDS); }
                catch (TimeoutException timeout) { pending.cancel(true); throw new IOException("获取模型列表超时"); }
                catch (InterruptedException interrupted) { pending.cancel(true); throw interrupted; }
                catch (ExecutionException error) { throw new IOException("无法连接模型列表服务，请检查当前源与网络"); }
                if (response.statusCode() != 200) throw new IOException("模型列表请求失败（HTTP " + response.statusCode() + "），请检查当前源与权限");
                JsonObject body;
                try { body = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject(); }
                catch (RuntimeException invalid) { throw new IOException("模型服务返回了无法读取的列表"); }
                for (JsonElement entry : AgentJson.list(body, "data")) {
                    if (!entry.isJsonObject()) throw new IOException("模型服务返回了无法读取的列表");
                    JsonObject row = entry.getAsJsonObject();
                    String label = AgentJson.string(row, "display_name");
                    if (label.isBlank()) label = AgentJson.string(row, "name");
                    AgentModels.add(models, AgentJson.required(row, "id"), label);
                }
                if (!AgentJson.bool(body, "has_more")) return AgentModels.catalog(models, preferred, "service", custom);
                after = AgentJson.required(body, "last_id");
                if (after.length() > 512 || !cursors.add(after)) throw new IOException("模型列表分页无效");
            }
        }
        throw new IOException("模型列表超过读取上限");
    }

    /** Bound the body while receiving it; fetch also caps the full response and all pages at 25 seconds. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 1024 * 1024) {
                    subscription.cancel(); result.completeExceptionally(new IOException("模型列表超过读取上限")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
