package dev.copperbench.agent;

import java.nio.file.*;
import java.util.Comparator;

/** Public paths only: native Workspace.connect owns private desktop-session discovery. */
record AgentWorkspaceContext(Path root, Path workspaceFile, Path sdkDirectory) {
    AgentWorkspaceContext {
        try {
            root = root.toRealPath();
            if (workspaceFile != null) workspaceFile = workspaceFile.getParent().toRealPath().resolve(workspaceFile.getFileName());
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("Workspace directory is inaccessible", failure); }
    }
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
        // Native-first (ADR-0019): write the mod as code and resources, verify through Copperbench; elements are optional.
        return "You are helping a Minecraft player create a mod in Copperbench. Reply concisely in the player's language, matching their request.\n"
                + "Workspace root: " + root + "\n"
                + "Opened workspace file: " + (workspaceFile == null ? "Discover the unique .mcreator file; ask if ambiguous." : workspaceFile) + "\n"
                + "Copperbench Python SDK directory: " + sdkDirectory + "\nPython executable: " + pythonCommand + "\n"
                + "Connect: add the SDK directory to sys.path if needed, then `from copperbench import Workspace` and "
                + "`ws = Workspace.connect(workspace_file)` to share the live desktop Core. Do not use Workspace.open on this already-open workspace. "
                + "Private native discovery is automatic for the same OS user; never read, print, copy or ask the player to paste credentials, "
                + "auth files, MCP pairing tokens, or native session secrets.\n"
                + "Write the mod directly: Java under src/main/java, JSON and assets under src/main/resources. Call ws.get_workspace_environment() once "
                + "for the Minecraft version, loader and source roots, and read build.gradle for the mappings before using game symbols. "
                + "Before the first edit, take one recovery point: ws.command('create_recovery_point', label='Before agent: <task>'). "
                + "ws.query('list_workspace_files', limit=200) shows each source file's ownership; files marked 'generated' belong to the "
                + "generator and stay read-only, so put your code in your own classes (outside the generated package) and resources. "
                + "To call your code at startup, add one line inside a user code block of the generated main mod class, such as "
                + "'// Start of user code block mod init': ws.read_file(path) lists them as userCodeBlocks, regeneration keeps them, and "
                + "ws.write_file(path, text, expected_sha256=sha) accepts the change if every byte outside the blocks is unchanged. "
                + "Edit hand-written text files with your "
                + "normal file tools, or through Core with ws.read_file(path) and ws.write_file(path, text, expected_sha256=sha); "
                + "ws.write_file(path, text) adds a new file. Import textures, sounds and models with "
                + "ws.import_asset(local_file, 'src/main/resources/assets/<modid>/textures/item/<name>.png'). "
                + "Never edit generated files or the .mcreator JSON directly.\n"
                + "Structured elements, Procedures and templates are optional shortcuts. Use one only when it clearly covers the request; "
                + "check ws.discover_field_contract(type) first and do not invent fields.\n"
                + "Verify: start ws.build(), then ws.wait_task(result['task']['id']), read the real diagnostics and fix the files. "
                + "For behavior (drops, recipes, item use, block interaction) also prove it in game: ws.prepare_game_tests() once, add GameTest "
                + "methods under src/gametest/java (starter class copperbench.acceptance.AcceptanceTests), then ws.run_game_tests() and wait. "
                + "A built JAR is not gameplay acceptance; tell the player which checks ran, which passed, and what remains unverified. "
                + "An accepted receipt is not success. Poll the same task ID; after interruption reconnect and inspect existing tasks before retrying.\n"
                + "The player starts gameplay and export from Copperbench. Do not start the Minecraft client, export, publish, install software, "
                + "accept EULA, mint authorizations, or change global CLI configuration without a separate explicit request. "
                + "Honor Core and CLI approval boundaries; do not bypass them. "
                + "When access, permission or a required schema is unavailable, explain the actionable blocker instead of fabricating success.\n";
    }
}
