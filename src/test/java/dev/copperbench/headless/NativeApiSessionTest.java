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
    @Test void editorWireVersionIsExplicitAndDoesNotChangeOtherOperations() throws Exception {
        var api = session(PermissionProfile.WORKSPACE);
        var created = api.dispatch(request("command", "create_mod_element", 0,
                "{\"elementType\":\"item\",\"name\":\"versioned_item\",\"initialValues\":{\"displayName\":\"Versioned item\"}}"));
        String id = created.getAsJsonObject("result").getAsJsonObject("data").getAsJsonObject("element").get("id").getAsString();
        var query = JsonParser.parseString(request("query", "get_mod_element_editor", 1, "{\"elementId\":\"" + id + "\"}")).getAsJsonObject();
        var legacy = api.dispatch(query.toString()).getAsJsonObject("result");
        assertEquals("1.0", legacy.get("schemaVersion").getAsString());
        assertFalse(legacy.getAsJsonObject("data").has("configuration"));
        query.addProperty("schemaVersion", "1.1");
        var rich = api.dispatch(query.toString()).getAsJsonObject("result");
        assertEquals("1.1", rich.get("schemaVersion").getAsString());
        assertTrue(rich.getAsJsonObject("data").has("configuration"));
        var evidence = new com.google.gson.JsonArray(); evidence.add(legacy); evidence.add(rich);
        query.addProperty("operation", "get_workbench");
        assertEquals("NATIVE_INVALID_REQUEST", api.dispatch(query.toString()).getAsJsonObject("error").get("code").getAsString());
        query.addProperty("kind", "command"); query.addProperty("operation", "build_workspace");
        assertEquals("NATIVE_INVALID_REQUEST", api.dispatch(query.toString()).getAsJsonObject("error").get("code").getAsString());
        var folder = java.nio.file.Path.of("output/p263-27"); java.nio.file.Files.createDirectories(folder);
        java.nio.file.Files.writeString(folder.resolve("native-editor-wire.json"), evidence.toString());
    }

    private NativeApiSession session(PermissionProfile permission) { return session(permission, "fabric-1.21.1"); }
    private NativeApiSession session(PermissionProfile permission, String target) {
        System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
        UUID workspaceId = UUID.randomUUID();
        var store = new RevisionedWorkspaceStore();
        var generator = JsonParser.parseString("""
                {"id":"fabric-1.21.1","loader":"fabric","minecraftVersion":"1.21.1","state":"ready"}
                """).getAsJsonObject();
        generator.addProperty("id", target);
        store.register(new WorkspaceState(workspaceId, "Python 测试", "mod", 0, false, generator,
                new JsonObject(), List.of()));
        var service = new WorkspaceApplicationService(store,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
        return new NativeApiSession(new HeadlessWorkspaceEntryAdapter(service, permission), workspaceId);
    }

    @Test void previewProtocolNegotiatesAndRefusesUnverifiedOrLegacyWrites() throws Exception {
        var api = session(PermissionProfile.WORKSPACE, "fabric-26.3");
        var receipts = new com.google.gson.JsonArray();
        var catalog = api.dispatch(request("preview", "catalog", 0, "{\"supportedVersions\":[\"1\"]}")).getAsJsonObject("result");
        receipts.add(catalog);
        assertEquals(10, catalog.getAsJsonArray("targets").size());
        var selected = api.dispatch(request("preview", "opt_in", 0,
                "{\"supportedVersions\":[\"1\"],\"generatorId\":\"fabric-26.3\",\"expectedRevision\":0}")).getAsJsonObject("result");
        receipts.add(selected);
        assertEquals("succeeded", selected.get("status").getAsString());
        var denied = api.dispatch(request("preview", "command", 0,
                "{\"supportedVersions\":[\"1\"],\"generatorId\":\"fabric-26.3\",\"expectedRevision\":0,\"command\":\"run_datagen\"}")).getAsJsonObject("result");
        receipts.add(denied);
        assertTrue(denied.toString().contains("PREVIEW_NATIVE_OPERATION_UNVERIFIED"));
        var legacy = api.dispatch(request("command", "build_workspace", 0, "{}")).getAsJsonObject("result");
        assertTrue(legacy.toString().contains("PREVIEW_NEGOTIATION_REQUIRED"));
        var unknown = api.dispatch(request("preview", "catalog", 0, "{\"supportedVersions\":[\"99\"]}")).getAsJsonObject("result");
        receipts.add(unknown);
        assertTrue(unknown.toString().contains("PREVIEW_VERSION_UNSUPPORTED"));
        var path = java.nio.file.Path.of("output/p263-06/native-wire.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, UiCoreJson(receipts));
    }

    private static String UiCoreJson(com.google.gson.JsonElement value) {
        return dev.copperbench.core.contract.UiCore.wireGson().toJson(value);
    }

    @Test void bothPreviewTargetsKeepReadOnlyAuthorityAndUnverifiedOperationsClosed() {
        for (String target : List.of("fabric-26.3", "neoforge-26.3")) {
            var readOnly = session(PermissionProfile.READ_ONLY, target);
            String payload = "{\"supportedVersions\":[\"1\"],\"generatorId\":\"" + target + "\",\"expectedRevision\":0}";
            assertTrue(readOnly.dispatch(request("preview", "opt_in", 0, payload)).toString().contains("PERMISSION_DENIED"));
            var api = session(PermissionProfile.WORKSPACE, target);
            assertEquals("succeeded", api.dispatch(request("preview", "opt_in", 0, payload))
                    .getAsJsonObject("result").get("status").getAsString());
            var command = JsonParser.parseString(payload).getAsJsonObject();
            for (String operation : List.of("run_datagen")) {
                command.addProperty("command", operation);
                assertTrue(api.dispatch(request("preview", "command", 0, command.toString())).toString()
                        .contains("PREVIEW_NATIVE_OPERATION_UNVERIFIED"), target + ": " + operation);
            }
        }
    }

    @Test void qualifiedClientsRequireOptInAndWorkspacePermissionAndValidateSuppliedAuthority() {
        for (String target : List.of("fabric-26.3", "neoforge-26.3")) {
            var api = session(PermissionProfile.WORKSPACE, target);
            JsonObject payload = new JsonObject();
            payload.add("supportedVersions", JsonParser.parseString("[\"1\"]"));
            payload.addProperty("generatorId", target);
            payload.addProperty("expectedRevision", 0);
            JsonObject selection = payload.deepCopy();
            payload.addProperty("command", "run_client");
            assertTrue(api.dispatch(request("preview", "command", 0, payload.toString())).toString()
                    .contains("PREVIEW_OPT_IN_REQUIRED"));
            assertEquals("succeeded", api.dispatch(request("preview", "opt_in", 0, selection.toString()))
                    .getAsJsonObject("result").get("status").getAsString());
            var accepted = api.dispatch(request("preview", "command", 0, payload.toString()));
            assertEquals("accepted", accepted.getAsJsonObject("result").get("status").getAsString(), accepted.toString());
            var readOnly = session(PermissionProfile.READ_ONLY, target);
            assertTrue(readOnly.dispatch(request("preview", "command", 0, payload.toString())).toString()
                    .contains("PERMISSION_DENIED"));
            payload.add("payload", JsonParser.parseString("{\"taskAuthorizationId\":\"forged\"}"));
            assertTrue(api.dispatch(request("preview", "command", 0, payload.toString())).toString()
                    .contains("TASK_AUTHORIZATION_INVALID"));
            assertEquals("succeeded", api.dispatch(request("preview", "revoke", 0, selection.toString()))
                    .getAsJsonObject("result").get("status").getAsString());
            assertTrue(api.dispatch(request("preview", "command", 0, payload.toString())).toString()
                    .contains("PREVIEW_OPT_IN_REQUIRED"));
        }
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

    @Test void schemaNegotiationUsesCoreVersionsWithoutMutatingWorkspace() {
        var api = session(PermissionProfile.READ_ONLY);
        var compatible = api.dispatch(request("handshake", "handshake", 0,
                "{\"supportedSchemaVersions\":[\"1.0\"],\"client\":{\"name\":\"preview\",\"version\":\"1\"}}"));
        assertEquals("compatible", compatible.getAsJsonObject("result").get("status").getAsString());
        assertEquals(compatible.get("id"), compatible.getAsJsonObject("result").get("requestId"));
        var incompatible = api.dispatch(request("handshake", "handshake", 0,
                "{\"supportedSchemaVersions\":[\"99.0\"],\"client\":{\"name\":\"preview\",\"version\":\"1\"}}"));
        assertEquals("incompatible", incompatible.getAsJsonObject("result").get("status").getAsString());
        assertEquals(0, api.dispatch(request("query", "get_workbench", 0, "{}"))
                .getAsJsonObject("result").get("revision").getAsLong());
    }

    @Test void requiredCaseContractNegotiatesWithoutChangingLegacySchemaOrAuthority() {
        var api = session(PermissionProfile.READ_ONLY);
        var catalog = api.dispatch(request("acceptance", "catalog", 0, "{\"supportedVersions\":[\"1\"]}"))
                .getAsJsonObject("result");
        assertEquals("succeeded", catalog.get("status").getAsString());
        assertEquals("1", catalog.get("selectedVersion").getAsString());
        assertEquals(2, catalog.getAsJsonArray("manifests").size());
        var manifest = catalog.getAsJsonArray("manifests").get(0);
        JsonObject payload = new JsonObject(); payload.add("requiredCases", manifest);
        JsonObject extension = new JsonObject();
        extension.add("supportedVersions", com.google.gson.JsonParser.parseString("[\"1\"]"));
        extension.addProperty("expectedRevision", 0); extension.add("payload", payload);
        var denied = api.dispatch(request("acceptance", "run", 0, extension.toString()));
        assertTrue(denied.toString().contains("PERMISSION_DENIED"), denied.toString());
        var invalidVersion = api.dispatch(request("acceptance", "run", 0, "{\"supportedVersions\":[\"99\"]}"));
        assertTrue(invalidVersion.toString().contains("ACCEPTANCE_VERSION_UNSUPPORTED"));
        var unnegotiated = session(PermissionProfile.WORKSPACE).dispatch(request("command", "run_gametest", 0, payload.toString()));
        assertEquals("rejected", unnegotiated.getAsJsonObject("result").get("status").getAsString());
        assertEquals("ACCEPTANCE_NEGOTIATION_REQUIRED", unnegotiated.getAsJsonObject("result")
                .getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString());
        var legacy = api.dispatch(request("query", "get_workbench", 0, "{}")).getAsJsonObject("result");
        assertEquals("1.0", legacy.get("schemaVersion").getAsString());
        assertEquals(0, legacy.get("revision").getAsInt());
    }

    @Test void oversizedRequestStopsSessionBeforeDispatch() throws Exception {
        var output = new StringWriter();
        assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), session(PermissionProfile.WORKSPACE).serve(
                new StringReader("x".repeat(4 * 1024 * 1024 + 1)), new PrintWriter(output)));
        assertTrue(output.toString().contains("NATIVE_REQUEST_TOO_LARGE"));
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void nativeHostGrantsALocalFileForTheGuardedAssetImport(boolean fromAgentScratch, @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp)
            throws Exception {
        var workspace = java.nio.file.Files.createDirectories(temp.resolve("workspace"));
        var source = fromAgentScratch ? java.nio.file.Files.createDirectories(workspace.resolve(".gradle/copperbench-agent")) : temp;
        var texture = java.nio.file.Files.write(source.resolve("ember_wand.png"), new byte[] { 7, 7, 7 });
        UUID workspaceId = UUID.randomUUID();
        var store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(workspaceId, "Import", "mod", 0, false, JsonParser.parseString(
                "{\"id\":\"fabric-1.21.1\",\"loader\":\"fabric\",\"minecraftVersion\":\"1.21.1\",\"state\":\"ready\"}").getAsJsonObject(),
                new JsonObject(), List.of()));
        try (var history = dev.copperbench.history.JGitLocalHistoryService.open(workspace, Clock.systemUTC())) {
            var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
                    dev.copperbench.core.application.WorkspaceMutationGateway.noOp(), history, null, ignored -> workspace,
                    Clock.systemUTC(), UUID::randomUUID);
            var api = new NativeApiSession(new HeadlessWorkspaceEntryAdapter(service, PermissionProfile.WORKSPACE), workspaceId);
            StringWriter ready = new StringWriter();
            api.serve(new StringReader(""), new PrintWriter(ready));
            assertTrue(ready.toString().contains("\"hostOperations\":[\"grant_asset_import_source\"]"), ready.toString());

            JsonObject path = new JsonObject(); path.addProperty("path", texture.toString());
            var granted = api.dispatch(request("host", "grant_asset_import_source", 0, path.toString())).getAsJsonObject("result");
            assertEquals("succeeded", granted.get("status").getAsString());
            assertEquals("ember_wand.png", granted.getAsJsonObject("data").get("fileName").getAsString());
            JsonObject preview = new JsonObject();
            preview.addProperty("sourceGrantId", granted.getAsJsonObject("data").get("sourceGrantId").getAsString());
            preview.addProperty("targetRelativePath", "assets/example/textures/item/ember_wand.png");
            var plan = api.dispatch(request("query", "preview_asset_import", 0, preview.toString())).getAsJsonObject("result");
            assertEquals("succeeded", plan.get("status").getAsString(), plan.toString());
            JsonObject apply = new JsonObject(); apply.addProperty("planToken", plan.getAsJsonObject("data").get("planToken").getAsString());
            var imported = api.dispatch(request("command", "import_asset", 0, apply.toString())).getAsJsonObject("result");
            assertEquals("committed", imported.get("status").getAsString(), imported.toString());
            assertArrayEquals(new byte[] { 7, 7, 7 }, java.nio.file.Files.readAllBytes(
                    workspace.resolve("assets/example/textures/item/ember_wand.png")));

            path.addProperty("path", "relative/ember_wand.png");
            assertEquals("ASSET_IMPORT_SOURCE_UNAVAILABLE", api.dispatch(request("host", "grant_asset_import_source", 0, path.toString()))
                    .getAsJsonObject("error").get("code").getAsString());
            path.addProperty("path", temp.resolve("missing.png").toString());
            assertEquals("ASSET_IMPORT_SOURCE_UNAVAILABLE", api.dispatch(request("host", "grant_asset_import_source", 0, path.toString()))
                    .getAsJsonObject("error").get("code").getAsString());
            assertEquals("NATIVE_INVALID_REQUEST", api.dispatch(request("host", "read_any_file", 0, path.toString()))
                    .getAsJsonObject("error").get("code").getAsString());
            var session = java.nio.file.Files.createDirectories(temp.resolve(".copperbench/python-sessions"))
                    .resolve("session.json");
            java.nio.file.Files.writeString(session, "{\"token\":\"not-for-workspaces\"}");
            path.addProperty("path", session.toString());
            assertEquals("ASSET_IMPORT_SOURCE_PRIVATE", api.dispatch(request("host", "grant_asset_import_source", 0, path.toString()))
                    .getAsJsonObject("error").get("code").getAsString());
        }
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
