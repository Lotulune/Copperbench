package dev.copperbench.agent;

import java.nio.file.*;
import java.util.Comparator;

/** Public paths only: native Workspace.connect owns private desktop-session discovery. */
record AgentWorkspaceContext(Path root, Path workspaceFile, Path sdkDirectory) {
    static AgentWorkspaceContext discover(Path root) {
        Path file = null;
        try (var paths = Files.list(root)) {
            var candidates = paths.filter(path -> path.getFileName().toString().endsWith(".mcreator")).sorted(Comparator.naturalOrder()).toList();
            if (candidates.size() == 1) file = candidates.getFirst();
        } catch (java.io.IOException ignored) { }
        return new AgentWorkspaceContext(root, file, Path.of(System.getProperty("user.dir"), "sdk/python").toAbsolutePath().normalize());
    }

    String instructions() {
        Path distribution = sdkDirectory.getParent() == null ? sdkDirectory : sdkDirectory.getParent().getParent();
        Path python = distribution == null ? Path.of("python") : distribution.resolve(AgentProcess.windows() ? "python/python.exe" : "python/bin/python3");
        String configuredPython = System.getenv("COPPERBENCH_PYTHON");
        String pythonCommand = configuredPython != null && !configuredPython.isBlank() ? configuredPython
                : Files.isRegularFile(python) ? python.toString() : AgentProcess.windows() ? "python" : "python3";
        return "You are helping a Minecraft player create a mod in Copperbench. Reply concisely in the player's language, matching their request.\n"
                + "Workspace root: " + root + "\n"
                + "Opened workspace file: " + (workspaceFile == null ? "Discover the unique .mcreator file; ask if ambiguous." : workspaceFile) + "\n"
                + "Copperbench Python SDK directory: " + sdkDirectory + "\nPython executable: " + pythonCommand + "\n"
                + "Read the SDK README.md and ../agent-workflows.md before editing. Add the SDK directory to sys.path if needed. "
                + "Use `from copperbench import Workspace` and `Workspace.connect(workspace_file)` to share the live desktop Core. "
                + "Do not use Workspace.open on this already-open workspace. Private native discovery is automatic for the same OS user; "
                + "never read, print, copy or ask the player to paste credentials, auth files, MCP pairing tokens, or native session secrets.\n"
                + "Inspect require_schema() and get_workspace_environment() and use exposed field contracts, current revision/hash and public SDK "
                + "commands; do not invent element fields, overwrite generated sources, or modify .mcreator JSON behind Core. "
                + "Use normal workspace operations to create/edit, build, inspect actual task receipts and repair errors. "
                + "A task accepted receipt is not success. Poll the same task ID; after interruption reconnect and inspect existing tasks before retrying. "
                + "The player starts gameplay and export from Copperbench. Do not start the Minecraft client, export, publish, install software, "
                + "accept EULA, mint authorizations, or change global CLI configuration without a separate explicit request. "
                + "Honor Core and CLI approval boundaries; do not bypass them. A completed build is not gameplay acceptance. "
                + "When access or a required schema is unavailable, explain the actionable blocker instead of fabricating success.\n";
    }
}
