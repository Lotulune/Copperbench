package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeApiSessionTest {
    private NativeApiSession session(PermissionProfile permission) {
        System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
        UUID workspaceId = UUID.randomUUID();
        var store = new RevisionedWorkspaceStore();
        var generator = JsonParser.parseString("""
                {"id":"fabric-1.21.1","loader":"fabric","minecraftVersion":"1.21.1","state":"ready"}
                """).getAsJsonObject();
        store.register(new WorkspaceState(workspaceId, "Python 测试", "mod", 0, false, generator,
                new JsonObject(), List.of()));
        var service = new WorkspaceApplicationService(store,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
        return new NativeApiSession(new HeadlessWorkspaceEntryAdapter(service, permission), workspaceId);
    }

    private static String request(String kind, String operation, long revision, String payload) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"kind\":\"" + kind + "\",\"operation\":\""
                + operation + "\",\"expectedRevision\":" + revision + ",\"payload\":" + payload + "}";
    }

    @Test void persistentCoreSessionMutatesQueriesAndRejectsStaleRevision() {
        var api = session(PermissionProfile.WORKSPACE);
        var created = api.dispatch(request("command", "create_registry_entry", 0,
                "{\"registry\":\"variables\",\"entry\":{\"name\":\"score\",\"dataType\":\"number\",\"scope\":\"global\"}}"));
        assertEquals("committed", created.getAsJsonObject("result").get("status").getAsString(), created.toString());
        assertEquals(1, created.getAsJsonObject("result").get("newRevision").getAsLong());
        var queried = api.dispatch(request("query", "get_workbench", 0, "{}"));
        assertEquals(1, queried.getAsJsonObject("result").get("revision").getAsLong());
        var stale = api.dispatch(request("command", "build_workspace", 0, "{}"));
        assertEquals("rejected", stale.getAsJsonObject("result").get("status").getAsString());
        assertEquals(1, stale.getAsJsonObject("result").getAsJsonObject("conflict").get("actualRevision").getAsLong());
    }

    @Test void approvalCannotBeForgedOrPermissionElevated() {
        var api = session(PermissionProfile.WORKSPACE);
        var denied = api.dispatch(request("command", "create_task_authorization", 0,
                "{\"userApproved\":true,\"actor\":\"ui\",\"permission\":\"full_access\"}"));
        assertTrue(denied.toString().contains("TASK_AUTHORIZATION_USER_ONLY"), denied.toString());
        var readOnly = session(PermissionProfile.READ_ONLY).dispatch(request("command", "build_workspace", 0, "{}"));
        assertTrue(readOnly.toString().contains("PERMISSION_DENIED"), readOnly.toString());
        var expired = api.dispatch(request("command", "build_workspace", 0, "{\"taskAuthorizationId\":\"missing\"}"));
        assertEquals("rejected", expired.getAsJsonObject("result").get("status").getAsString());
    }

    @Test void invalidRequestsDoNotBreakFollowingRequestsAndEofClosesSession() throws Exception {
        var api = session(PermissionProfile.WORKSPACE);
        StringWriter output = new StringWriter();
        int exit = api.serve(new StringReader("{broken\n" + request("query", "get_workbench", 0, "{}") + "\n"),
                new PrintWriter(output));
        var lines = output.toString().lines().map(s -> JsonParser.parseString(s).getAsJsonObject()).toList();
        assertEquals(0, exit);
        assertEquals(3, lines.size());
        assertEquals("1", lines.getFirst().get("nativeApiVersion").getAsString());
        assertEquals("NATIVE_INVALID_REQUEST", lines.get(1).getAsJsonObject("error").get("code").getAsString());
        assertEquals("succeeded", lines.get(2).getAsJsonObject("result").get("status").getAsString());
    }

    @Test void oversizedRequestStopsSessionBeforeDispatch() throws Exception {
        var output = new StringWriter();
        assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), session(PermissionProfile.WORKSPACE).serve(
                new StringReader("x".repeat(4 * 1024 * 1024 + 1)), new PrintWriter(output)));
        assertTrue(output.toString().contains("NATIVE_REQUEST_TOO_LARGE"));
    }

    @Test void scriptingContextUsesRealElementIdsWithoutChangingWorkspaceRevision() {
        var api = session(PermissionProfile.WORKSPACE);
        var created = api.dispatch(request("command", "create_mod_element", 0,
                "{\"elementType\":\"item\",\"name\":\"context_item\",\"initialValues\":{\"displayName\":\"Context item\"}}"));
        String id = created.getAsJsonObject("result").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
        var selected = api.dispatch(request("context", "select", 0, "{\"elementId\":\"" + id + "\"}"));
        assertEquals(id, selected.getAsJsonObject("result").getAsJsonObject("data").get("activeElementId").getAsString());
        assertEquals(1, selected.getAsJsonObject("result").get("revision").getAsLong());
        assertEquals(id, api.dispatch(request("context", "get", 0, "{}"))
                .getAsJsonObject("result").getAsJsonObject("data").get("activeElementId").getAsString());
        assertTrue(api.dispatch(request("context", "select", 0, "{\"elementId\":\"" + UUID.randomUUID() + "\"}"))
                .has("error"));
    }
}
