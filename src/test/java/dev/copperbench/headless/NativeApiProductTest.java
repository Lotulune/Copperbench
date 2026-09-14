package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeApiProductTest {
    @TempDir Path root;

    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    private Path createWorkspace() {
        WorkspaceSettings settings = new WorkspaceSettings("python_api");
        settings.setModName("Python API");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator("fabric-1.21.1");
        Path file = root.resolve("python_api.mcreator");
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings)) {
            assertNotNull(workspace);
        }
        return file;
    }

    private static String request(String kind, String operation, long revision, String payload) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"kind\":\"" + kind + "\",\"operation\":\""
                + operation + "\",\"expectedRevision\":" + revision + ",\"payload\":" + payload + "}\n";
    }

    private static List<JsonObject> run(Path file, String requests, int expectedExit) {
        StringWriter output = new StringWriter();
        int result = HeadlessProductLauncher.run(new String[]{"--workspace", file.toString(), "api"},
                new PrintWriter(output), new StringReader(requests));
        assertEquals(expectedExit, result, output.toString());
        return output.toString().lines().map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
    }

    @Test void nativeProductPersistsEditsAndReleasesLeaseWithoutMcp() {
        Path file = createWorkspace();
        var result = run(file,
                request("command", "create_registry_entry", 0,
                        "{\"registry\":\"variables\",\"entry\":{\"name\":\"python_score\",\"dataType\":\"number\",\"scope\":\"global\"}}")
                        + request("query", "list_workspace_registries", 0, "{}"), 0);
        assertEquals("ready", result.getFirst().get("status").getAsString());
        assertEquals("committed", result.get(1).getAsJsonObject("result").get("status").getAsString(), result.toString());
        assertTrue(result.get(2).toString().contains("python_score"));
        var reopened = run(file, request("query", "list_workspace_registries", 0, "{}"), 0);
        assertTrue(reopened.get(1).toString().contains("python_score"));
        assertEquals(1, reopened.get(1).getAsJsonObject("result").get("revision").getAsLong());
        assertFalse(java.nio.file.Files.exists(root.resolve(".copperbench/mcp-connection.json")));
    }

    @Test void nativeProductCannotOpenAWorkspaceAlreadyOwnedByDesktop() throws Exception {
        Path file = createWorkspace();
        try (Workspace owner = Workspace.readFromFS(file.toFile(), null)) {
            assertNotNull(owner);
            var result = run(file, "", HeadlessExitCode.PERMISSION_DENIED.code());
            assertEquals("WORKSPACE_WRITE_LOCKED", result.getFirst().get("code").getAsString());
        }
        assertEquals("ready", run(file, "", 0).getFirst().get("status").getAsString());
    }
}
