package dev.copperbench.headless;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.copperbench.automation.security.TaskAuthorizationStore;
import dev.copperbench.core.contract.UiCore.Actor;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A user-facing CLI pairing step; subsequent bootstrap/headless calls use the issued authorization ID. */
final class TaskAuthorizationLauncher {
    private TaskAuthorizationLauncher() {}

    static int run(String[] arguments, PrintWriter output) {
        var json = new GsonBuilder().disableHtmlEscaping().create();
        JsonObject result = new JsonObject(); result.addProperty("schemaVersion", "1.0");
        result.addProperty("operation", arguments[0]);
        try {
            Map<String, String> options = new LinkedHashMap<>();
            Set<String> allowed = Set.of("--root", "--label", "--capabilities", "--ttl-seconds", "--authorization-id");
            for (int i = 1; i < arguments.length; i += 2) {
                if (i + 1 >= arguments.length || !allowed.contains(arguments[i]) || options.put(arguments[i], arguments[i + 1]) != null)
                    throw new IllegalArgumentException("Use unique --name value options for root, label, capabilities, ttl-seconds or authorization-id");
            }
            TaskAuthorizationStore store = TaskAuthorizationStore.productDefault(Clock.systemUTC());
            switch (arguments[0]) {
                case "list-authorizations" -> result.add("data", json.toJsonTree(store.list(options.containsKey("--root") ? Path.of(options.get("--root")) : null)));
                case "revoke-authorization" -> result.add("data", store.revoke(required(options, "--authorization-id"), null));
                case "authorize-task" -> {
                    Path root = Path.of(required(options, "--root"));
                    if (!root.isAbsolute()) throw new IllegalArgumentException("--root must be an absolute directory");
                    root = dev.copperbench.generator.WorkspaceExecutionSnapshot.canonicalPath(root);
                    if (root.getParent() == null) throw new IllegalArgumentException("--root cannot be a filesystem root");
                    String label = required(options, "--label");
                    List<String> capabilities = options.containsKey("--capabilities")
                            ? List.of(options.get("--capabilities").split(",")) : TaskAuthorizationStore.DEFAULT_CAPABILITIES;
                    long ttl = Long.parseLong(options.getOrDefault("--ttl-seconds", "7200"));
                    if (GraphicsEnvironment.isHeadless()) throw new TaskAuthorizationStore.AuthorizationException(
                            "TASK_AUTHORIZATION_USER_ONLY", "Approve a task in the desktop AI and MCP page before running headless commands");
                    if (!TaskAuthorizationStore.CAPABILITIES.containsAll(capabilities) || ttl < 60 || ttl > 86400)
                        throw new IllegalArgumentException("Unknown capabilities or invalid lifetime");
                    new PrintWriter(new FileOutputStream(FileDescriptor.err), true).println(
                            "Copperbench is waiting for task approval. Root: " + root + "; capabilities: " + capabilities + "; lifetime: " + ttl + " seconds.");
                    String text = "Task: " + label + "\nRoot: " + root + "\nCapabilities: " + String.join(", ", capabilities)
                            + "\nLifetime: " + ttl + " seconds\n\nAllow this task to continue through CLI and MCP until expiry or revocation?";
                    boolean approved = JOptionPane.showConfirmDialog(null, text, "Copperbench task authorization",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
                    boolean eula = false;
                    if (approved && capabilities.contains("run_server")) eula = JOptionPane.showConfirmDialog(null,
                            "Dedicated server execution requires the Minecraft EULA: https://aka.ms/MinecraftEULA\nDo you accept it for servers created by this task?",
                            "Copperbench server authorization", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
                    result.add("data", store.issue(Actor.UI, approved, label, root, capabilities, ttl, eula));
                }
                default -> throw new IllegalArgumentException("Unknown authorization command");
            }
            result.addProperty("status", "completed"); result.addProperty("exitCode", 0);
        } catch (Exception exception) {
            boolean denied = exception instanceof TaskAuthorizationStore.AuthorizationException;
            result.addProperty("status", "rejected");
            result.addProperty("code", denied ? ((TaskAuthorizationStore.AuthorizationException) exception).code() : "TASK_AUTHORIZATION_INVALID");
            result.addProperty("message", exception.getMessage()); result.addProperty("exitCode", denied ? 3 : 4);
        }
        output.println(json.toJson(result)); output.flush(); return result.get("exitCode").getAsInt();
    }
    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
