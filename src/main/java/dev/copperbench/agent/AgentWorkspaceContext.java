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

    /** An existing Gradle project is opened by its folder; a folder with a workspace file is always that workspace. */
    boolean gradleProject() {
        if (workspaceFile != null || !Files.isRegularFile(root.resolve("build.gradle")) && !Files.isRegularFile(root.resolve("build.gradle.kts"))) return false;
        try (var paths = Files.list(root)) {
            return paths.noneMatch(path -> path.getFileName().toString().endsWith(".mcreator"));
        } catch (java.io.IOException unreadable) { return false; }
    }

    String instructions() {
        return gradleProject() ? projectInstructions() : workspaceInstructions();
    }

    private String pythonCommand() {
        Path distribution = sdkDirectory.getParent() == null ? sdkDirectory : sdkDirectory.getParent().getParent();
        Path python = distribution == null ? Path.of("python") : distribution.resolve(AgentProcess.windows() ? "python/python.exe" : "python/bin/python3");
        String configuredPython = System.getenv("COPPERBENCH_PYTHON");
        return configuredPython != null && !configuredPython.isBlank() ? configuredPython
                : Files.isRegularFile(python) ? python.toString() : AgentProcess.windows() ? "python" : "python3";
    }

    private static final String CREDENTIALS = "Private native discovery is automatic for the same OS user; never read, print, copy or ask the player to paste credentials, "
            + "auth files, MCP pairing tokens, or native session secrets.\n";
    private static final String SCRATCH = "For temporary files only, use .gradle/copperbench-agent/ under this workspace "
            + "(not the shared Gradle home). From the first script onward put helper scripts, task IDs/receipts and draft assets there; "
            + "create it only if needed. Import final assets into src/main/resources; keep real source and GameTests in their normal paths. ";
    private static final String TASKS = "For each task use "
            + "task = ws.wait_task(result['task']['id'])['data']['task']; check task['state'] and read diagnostics on failure. "
            + "An accepted receipt is not success. Reconnect to the same task ID after interruption. ";
    private static final String REPORT = "After test-only edits rerun the GameTest, not a separate build. "
            + "Report the JAR and what was actually tested. A built JAR is not gameplay acceptance.\n";
    private static final String BOUNDARIES = "The player starts gameplay and export from Copperbench. Do not start the Minecraft client, export, publish, install software, "
            + "accept EULA, mint authorizations, or change global CLI configuration without a separate explicit request. "
            + "Honor Core and CLI approval boundaries; do not bypass them. "
            + "When access, permission or a required schema is unavailable, explain the actionable blocker instead of fabricating success.\n";

    /** An existing project (ADR-0020): nothing is generated, so there is no ownership to respect and no structured layer. */
    private String projectInstructions() {
        return "You are helping a Minecraft player change an existing Gradle mod project opened in Copperbench. Reply concisely in the player's language, matching their request.\n"
                + "Project root: " + root + "\n"
                + "Copperbench Python SDK directory: " + sdkDirectory + "\nPython executable: " + pythonCommand() + "\n"
                + "Connect: add the SDK directory to sys.path if needed, then `from copperbench import Workspace` and "
                + "`ws = Workspace.connect(project_root)` with the folder above to share the live desktop Core. Do not use Workspace.open on this already-open project. "
                + CREDENTIALS
                + "Copperbench did not create this project and generates nothing in it: every file is hand-written. There are no structured elements, "
                + "Procedures, registries, templates or user code blocks, and those operations are refused. Keep the project's own structure, mappings and code style. "
                + "Change build.gradle, settings.gradle, gradle.properties or the Gradle Wrapper only when the request needs it, and say so. "
                + SCRATCH
                + "In your first SDK script print(ws.native_edit_brief()) and take one "
                + "ws.command('create_recovery_point', label='Before agent: <task>') before editing. A ready brief gives the actual main path and the mappings "
                + "declaration; use them without listing the project again. Only for needs_discovery inspect the reported path (for example the build script of a "
                + "project with other mappings) and use list_workspace_files as needed. "
                + "Implement the requested change first; group related writes together. Use normal file tools or ws.write_file(path, text) "
                + "for new files (expected_sha256=sha for replacements), and ws.import_asset(local_file, target_path) for asset imports.\n"
                + "Submit ws.build() once the implementation is ready, before writing test fixtures; it runs the project's own Gradle Wrapper. " + TASKS
                + "For behavior, use ws.prepare_game_tests() if not configured; it adds src/copperbenchTest, which the project's own build does not compile. "
                + "Extend the starter with focused assertions for the requested behavior, then ws.run_game_tests() and wait. "
                + "Tests compile in a separate host with Mojang mappings and the versions in the brief's gameTestHost, whatever mappings the project uses; "
                + "the host loads only this mod's JAR and that loader's API. On Fabric 1.21.1 the new starter provides survivalPlayer(helper) for item and block interactions. "
                + REPORT + BOUNDARIES;
    }

    private String workspaceInstructions() {
        // Native-first (ADR-0019): write the mod as code and resources, verify through Copperbench; elements are optional.
        return "You are helping a Minecraft player create a mod in Copperbench. Reply concisely in the player's language, matching their request.\n"
                + "Workspace root: " + root + "\n"
                + "Opened workspace file: " + (workspaceFile == null ? "Discover the unique .mcreator file; ask if ambiguous." : workspaceFile) + "\n"
                + "Copperbench Python SDK directory: " + sdkDirectory + "\nPython executable: " + pythonCommand() + "\n"
                + "Connect: add the SDK directory to sys.path if needed, then `from copperbench import Workspace` and "
                + "`ws = Workspace.connect(workspace_file)` to share the live desktop Core. Do not use Workspace.open on this already-open workspace. "
                + CREDENTIALS
                + "Write the mod directly as Java, JSON and assets. " + SCRATCH
                + "In your first SDK script print(ws.native_edit_brief()) and take one "
                + "ws.command('create_recovery_point', label='Before agent: <task>') before editing. A ready brief gives the actual main path, its ownership, "
                + "userCodeBlocks and the build.gradle mappings declaration; use them without listing the project again. Only for needs_discovery inspect the reported path "
                + "and use list_workspace_files as needed. For other files check their own ownership before replacing them. "
                + "Implement the requested mod first; group related writes together. Use normal file tools for hand-written files or ws.write_file(path, text) "
                + "for new files (expected_sha256=sha for replacements). Paths marked 'generated' are read-only except named user code blocks: "
                + "ws.read_file(path) lists userCodeBlocks; wire your own class with ws.insert_user_code(main_path, 'mod init', 'MyContent.register();'). "
                + "For generated lang files use ws.command('create_registry_entry', registry='languageKeys', entry={'key': key, 'translations': {'en_us': en, 'zh_cn': zh}}). "
                + "Use ws.import_asset(local_file, target_path) for asset imports. Never edit generated files or the .mcreator JSON directly.\n"
                + "Structured elements, Procedures and templates are optional shortcuts; discover their field contract only if using one.\n"
                + "Submit ws.build() once the implementation is ready, before writing test fixtures. " + TASKS
                + "For behavior, use ws.prepare_game_tests() if not configured, extend the starter with focused assertions for the requested behavior, "
                + "then ws.run_game_tests() and wait. On Fabric 1.21.1 the new starter provides survivalPlayer(helper) for item and block interactions. "
                + REPORT + BOUNDARIES;
    }
}
