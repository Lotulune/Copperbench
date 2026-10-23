package dev.copperbench.agent;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

record AgentProvider(String id, String label, Path executable, String version, String setupUrl, String discoveryError) {
    static final List<String> IDS = List.of("codex", "claude", "grok", "antigravity", "deepseek");
    static String label(String id) { return switch (id) {
        case "codex" -> "Codex CLI"; case "claude" -> "Claude Code";
        case "grok" -> "Grok Build"; case "antigravity" -> "Antigravity CLI";
        case "deepseek" -> "DeepSeek Harness";
        default -> throw new IllegalArgumentException("未知的创作工具");
    }; }
    static String setup(String id) { return switch (id) {
        case "codex" -> "https://developers.openai.com/codex/cli/";
        case "claude" -> "https://code.claude.com/docs/en/setup";
        case "grok" -> "https://docs.x.ai/build/overview";
        case "antigravity" -> "https://antigravity.google/docs/cli/overview/";
        case "deepseek" -> "https://github.com/deepseek-ai/deepseek-harness";
        default -> throw new IllegalArgumentException("未知的创作工具");
    }; }

    JsonObject json() {
        JsonObject result = AgentJson.object("id", id, "label", label, "installed", executable != null,
                "executable", executable == null ? null : executable.toString(), "setupUrl", setupUrl,
                "loginAvailable", executable != null && !id.equals("deepseek"));
        result.addProperty("checkAvailable", !id.equals("antigravity"));
        result.add("capabilities", AgentJson.object("structuredEvents", true, "interactivePermission", !id.equals("antigravity"),
                "sessionResume", true, "cancellation", "both", "usageReporting", false,
                "runSettings", id.equals("codex"), "permissionProfile", id.equals("codex") ? "workspace-write / on-request" : "provider configuration"));
        if (version != null && !version.isBlank()) result.addProperty("version", version);
        if (discoveryError != null) AgentCopy.put(result, "error", AgentJson.bounded(discoveryError, 512));
        result.addProperty("detection", executable == null ? "missing" : discoveryError != null ? "version_failed" : version == null ? "found" : "ready");
        return result;
    }

    static AgentProvider detect(String id, Path cwd) {
        label(id);
        Path path = locate(id);
        String version = null, failure = null;
        if (path != null) try { version = AgentProcess.version(path, cwd); }
        catch (IOException | RuntimeException exception) { failure = exception.getMessage(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); failure = "连接检查已取消"; }
        return new AgentProvider(id, label(id), path, version, setup(id), failure);
    }

    static Path locate(String id) {
        String configured = System.getProperty("copperbench.agent." + id);
        if (configured == null || configured.isBlank()) configured = System.getenv("COPPERBENCH_AGENT_" + id.toUpperCase(Locale.ROOT));
        if (configured != null && !configured.isBlank()) {
            Path explicit = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(explicit) ? explicit : null;
        }
        String name = switch (id) { case "antigravity" -> "agy"; case "deepseek" -> "dsh"; default -> id; };
        List<Path> directories = new ArrayList<>();
        String search = System.getenv("PATH");
        if (search != null) for (String item : search.split(java.io.File.pathSeparator)) {
            if (!item.isBlank()) try { directories.add(Path.of(item.replaceAll("^\"|\"$", ""))); }
            catch (InvalidPathException ignored) { }
        }
        Path user = Path.of(System.getProperty("user.home"));
        directories.addAll(List.of(user.resolve("bin"), user.resolve(".local/bin"), user.resolve(".grok/bin")));
        String appData = System.getenv("APPDATA"), local = System.getenv("LOCALAPPDATA");
        if (appData != null) directories.add(Path.of(appData, "npm"));
        if (local != null) directories.addAll(List.of(Path.of(local, "agy/bin"), Path.of(local, "Microsoft/WinGet/Links")));
        List<String> endings = AgentProcess.windows() ? List.of(".exe", ".cmd", ".bat", ".ps1", "") : List.of("");
        for (Path directory : directories) for (String ending : endings) {
            Path candidate = directory.resolve(name + ending).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate) && (AgentProcess.windows() || Files.isExecutable(candidate))) return candidate;
        }
        return null;
    }
}
