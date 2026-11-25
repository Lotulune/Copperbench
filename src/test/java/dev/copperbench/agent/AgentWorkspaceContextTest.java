package dev.copperbench.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentWorkspaceContextTest {
    @TempDir Path root;

    @Test void anExistingGradleProjectIsConnectedByItsFolderAndHasNoStructuredLayer() throws Exception {
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'fabric-loom' version '1.7.4' }\n");
        AgentWorkspaceContext context = AgentWorkspaceContext.discover(root);
        assertTrue(context.gradleProject());
        String instructions = context.instructions();
        for (String expected : new String[]{"Workspace.connect(project_root)", "Project root: " + context.root(), "create_recovery_point", "every file is hand-written",
                "ws.native_edit_brief()", "only when the request needs it", "ws.build()", "src/copperbenchTest", "gameTestHost", "ws.run_game_tests()",
                "A built JAR is not gameplay acceptance", "An accepted receipt is not success",
                "never read, print, copy or ask the player to paste credentials", "Do not start the Minecraft client", "accept EULA, mint authorizations"})
            assertTrue(instructions.contains(expected), expected);
        for (String absent : new String[]{".mcreator", "insert_user_code", "languageKeys", "Structured elements, Procedures and templates are optional"})
            assertFalse(instructions.contains(absent), absent);

        Files.writeString(root.resolve("ember.mcreator"), "{}");
        assertFalse(AgentWorkspaceContext.discover(root).gradleProject(), "a folder with a workspace file is that workspace");
        Files.writeString(root.resolve("second.mcreator"), "{}");
        assertFalse(AgentWorkspaceContext.discover(root).gradleProject());
        assertTrue(AgentWorkspaceContext.discover(root).instructions().contains("ask if ambiguous"));
    }

    @Test void defaultRouteWritesCodeThroughCoreAndVerifiesInGame() throws Exception {
        Path workspaceFile = Files.writeString(root.resolve("ember.mcreator"), "{}");
        String instructions = new AgentWorkspaceContext(root, workspaceFile, root.resolve("sdk/python")).instructions();

        // Native code and resources are the default; Core keeps a recovery point and generator ownership.
        for (String expected : new String[]{"Workspace.connect(workspace_file)", "create_recovery_point", "list_workspace_files",
                "'generated'", "normal file tools", "ws.write_file(path, text)", "expected_sha256=sha", "ws.import_asset(", "build.gradle",
                "user code block", "userCodeBlocks"})
            assertTrue(instructions.contains(expected), expected);
        assertTrue(instructions.contains("Structured elements, Procedures and templates are optional shortcuts"));
        // Measured detours: a 190,000-character environment, CRLF user-code markers and an undocumented language entry.
        for (String expected : new String[]{"ws.native_edit_brief()", "Only for needs_discovery", "ws.insert_user_code(",
                "registry='languageKeys'", "'translations': {'en_us'"})
            assertTrue(instructions.contains(expected), expected);
        assertTrue(instructions.indexOf(".gradle/copperbench-agent/") >= 0
                && instructions.indexOf(".gradle/copperbench-agent/") < instructions.indexOf("ws.native_edit_brief()"),
                "The location must be known before writing the first helper script");
        assertFalse(instructions.contains("Read the SDK README.md"), "reading SDK documents is no longer a prerequisite");

        // Verification goes beyond a successful build.
        for (String expected : new String[]{"ws.build()", "ws.prepare_game_tests()", "ws.run_game_tests()",
                "A built JAR is not gameplay acceptance", "An accepted receipt is not success"})
            assertTrue(instructions.contains(expected), expected);

        // Safety boundaries are unchanged.
        for (String expected : new String[]{"never read, print, copy or ask the player to paste credentials",
                "Do not use Workspace.open on this already-open workspace", "Do not start the Minecraft client",
                "accept EULA, mint authorizations", "Never edit generated files or the .mcreator JSON directly"})
            assertTrue(instructions.contains(expected), expected);
    }
}
