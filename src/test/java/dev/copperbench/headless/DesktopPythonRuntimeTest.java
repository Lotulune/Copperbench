package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DesktopPythonRuntimeTest {
    @TempDir Path root;

    private WorkspaceApplicationService service(UUID workspaceId) {
        System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
        var store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(workspaceId, "Python desktop", "mod", 0, false,
                JsonParser.parseString("{\"id\":\"fabric-1.21.1\",\"loader\":\"fabric\",\"minecraftVersion\":\"1.21.1\",\"state\":\"ready\"}").getAsJsonObject(),
                new JsonObject(), List.of()));
        return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
                Clock.systemUTC(), UUID::randomUUID);
    }

    private static JsonObject connection(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(DesktopPythonRuntime.connectionFile(file))).getAsJsonObject();
    }

    private static final class Client implements AutoCloseable {
        final Socket socket;
        final BufferedReader input;
        final PrintWriter output;
        Client(JsonObject connection, String token) throws IOException {
            socket = new Socket("127.0.0.1", connection.get("port").getAsInt());
            socket.setSoTimeout(3000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            JsonObject hello = new JsonObject();
            hello.addProperty("token", token);
            output.println(hello);
        }
        JsonObject read() throws IOException { return JsonParser.parseString(input.readLine()).getAsJsonObject(); }
        JsonObject call(String kind, String operation, long revision, String payload) throws IOException {
            output.println("{\"id\":\"" + UUID.randomUUID() + "\",\"kind\":\"" + kind + "\",\"operation\":\""
                    + operation + "\",\"expectedRevision\":" + revision + ",\"payload\":" + payload + "}");
            return read().getAsJsonObject("result");
        }
        @Override public void close() throws IOException { socket.close(); }
    }

    @Test void authenticatedClientsShareStateNotifyDesktopAndCancelWithRealCoreStatus() throws Exception {
        Path file = Files.writeString(root.resolve("desktop.mcreator"), "{}");
        UUID id = UUID.randomUUID();
        var core = service(id);
        List<Event> events = new CopyOnWriteArrayList<>();
        try (var subscription = core.subscribeEvents(id, 0, events::add);
             var runtime = DesktopPythonRuntime.start(file, id, new HeadlessWorkspaceEntryAdapter(core, PermissionProfile.WORKSPACE))) {
            var metadata = connection(file);
            assertFalse(Files.exists(root.resolve(".copperbench/mcp-connection.json")));
            try (var first = new Client(metadata, metadata.get("token").getAsString());
                 var second = new Client(metadata, metadata.get("token").getAsString())) {
                assertEquals("ready", first.read().get("status").getAsString());
                assertEquals("ready", second.read().get("status").getAsString());
                var created = first.call("command", "create_registry_entry", 0,
                        "{\"registry\":\"variables\",\"entry\":{\"name\":\"python_score\",\"dataType\":\"number\",\"scope\":\"global\"}}");
                assertEquals("committed", created.get("status").getAsString(), created.toString());
                assertEquals(1, events.size(), "Desktop receives one mutation event");
                assertTrue(second.call("query", "list_workspace_registries", 0, "{}").toString().contains("python_score"));
                assertEquals("rejected", second.call("command", "validate_workspace", 0, "{}").get("status").getAsString());
                var task = first.call("command", "validate_workspace", 1, "{}").getAsJsonObject("task");
                var cancelled = second.call("command", "cancel_task", 1, "{\"taskId\":\"" + task.get("id").getAsString() + "\"}");
                assertEquals("cancelled", cancelled.get("status").getAsString(), cancelled.toString());
                var forged = first.call("command", "create_task_authorization", 1, "{\"userApproved\":true}");
                assertTrue(forged.toString().contains("TASK_AUTHORIZATION_USER_ONLY"));
            }
            try (var reconnect = new Client(metadata, metadata.get("token").getAsString())) {
                assertEquals("ready", reconnect.read().get("status").getAsString());
                assertEquals(1, reconnect.call("query", "get_workbench", 0, "{}").get("revision").getAsLong());
            }
        }
        assertFalse(Files.exists(DesktopPythonRuntime.connectionFile(file)));
    }

    @Test void badCredentialsCannotDispatchAndClosingHostDisconnectsAttachedClients() throws Exception {
        Path file = Files.writeString(root.resolve("desktop.mcreator"), "{}");
        UUID id = UUID.randomUUID();
        try (var runtime = DesktopPythonRuntime.start(file, id,
                new HeadlessWorkspaceEntryAdapter(service(id), PermissionProfile.WORKSPACE))) {
            var metadata = connection(file);
            try (var invalid = new Client(metadata, "wrong")) {
                assertEquals("rejected", invalid.read().get("status").getAsString());
                assertNull(invalid.input.readLine());
            }
            try (var attached = new Client(metadata, metadata.get("token").getAsString())) {
                assertEquals("ready", attached.read().get("status").getAsString());
                runtime.close();
                assertNull(attached.input.readLine());
            }
        }
        assertFalse(Files.exists(DesktopPythonRuntime.connectionFile(file)));
    }
}
