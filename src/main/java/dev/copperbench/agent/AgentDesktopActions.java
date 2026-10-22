package dev.copperbench.agent;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Opens official setup/sign-in surfaces. A launched login window is not evidence of authentication. */
class AgentDesktopActions {
    void setup(AgentProvider provider) throws IOException {
        browse(provider.setupUrl());
    }

    void browse(String url) throws IOException {
        URI uri = URI.create(url);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Only HTTP and HTTPS links are supported");
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new IOException("无法打开浏览器，请访问 " + url);
        Desktop.getDesktop().browse(uri);
    }

    void login(AgentProvider provider, java.nio.file.Path workspace) throws IOException {
        List<String> arguments = switch (provider.id()) {
            case "codex" -> List.of("login");
            case "claude" -> List.of("auth", "login");
            case "grok" -> List.of("login");
            case "antigravity" -> List.of(); // Official first-run interactive sign-in; no undocumented auth command.
            default -> throw new IllegalArgumentException("未知的创作工具");
        };
        if (AgentProcess.windows()) {
            String command = "Set-Location -LiteralPath " + ps(workspace.toString()) + "\n& " + ps(provider.executable().toString());
            for (String argument : arguments) command += " " + ps(argument);
            String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
            String launch = "Start-Process -FilePath 'pwsh' -ArgumentList @('-NoProfile','-NoExit','-EncodedCommand'," + ps(encoded) + ")";
            Process started = new ProcessBuilder("pwsh", "-NoProfile", "-NonInteractive", "-Command", launch)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            // This short launcher only opens an explicitly requested visible terminal; the player owns the login window.
            try {
                if (!started.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { started.destroyForcibly(); throw new IOException("登录窗口启动超时"); }
                if (started.exitValue() != 0) throw new IOException("无法打开登录窗口，请在终端运行官方 CLI 登录命令");
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("打开登录窗口已取消", interrupted); }
        } else if (System.getProperty("os.name").startsWith("Mac")) {
            String command = "cd " + sh(workspace.toString()) + " && " + sh(provider.executable().toString());
            for (String argument : arguments) command += " " + sh(argument);
            String script = "tell application \"Terminal\" to do script \"" + command.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            new ProcessBuilder("osascript", "-e", script).start();
        } else {
            List<String> command = new ArrayList<>(List.of("x-terminal-emulator", "-e", provider.executable().toString()));
            command.addAll(arguments); new ProcessBuilder(command).directory(workspace.toFile()).start();
        }
    }
    private static String ps(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static String sh(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }
}
