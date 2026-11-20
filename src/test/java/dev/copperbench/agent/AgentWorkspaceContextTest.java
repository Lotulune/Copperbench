package dev.copperbench.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentWorkspaceContextTest {
    @TempDir Path root;

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
        for (String expected : new String[]{"get_workspace_environment(field_contracts='summary')", "ws.insert_user_code(",
                "registry='languageKeys'", "'translations': {'en_us'"})
            assertTrue(instructions.contains(expected), expected);
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
