package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.automation.security.TaskAuthorizationStore;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.tracks.PreviewCapabilityCatalog;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreviewAccessTest {
    final Clock clock = Clock.systemUTC();
    final UUID id = UUID.randomUUID();
    final RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
    final UiCore.RequestContext writable = new UiCore.RequestContext(UiCore.Actor.HEADLESS, UiCore.PermissionProfile.WORKSPACE);

    PreviewAccessTest() { store.register(state(id, "fabric-26.3")); }

    WorkspaceState state(UUID id, String target) {
        JsonObject generator = new JsonObject(); generator.addProperty("id", target);
        generator.addProperty("loader", "fabric"); generator.addProperty("minecraftVersion", "26.3"); generator.addProperty("state", "ready");
        return new WorkspaceState(id, "Preview", "mod", 0, false, generator, new JsonObject(), List.of());
    }

    WorkspaceApplicationService service(PreviewCapabilityCatalog policy) {
        return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID),
                WorkspaceMutationGateway.noOp(), null, null, null, clock, UUID::randomUUID, false,
                TaskAuthorizationStore.productDefault(clock), policy);
    }

    PreviewCapabilityCatalog fixture() {
        return new PreviewCapabilityCatalog(Map.of("fabric-26.3", new PreviewCapabilityCatalog.Evidence(
                "fixture-environment", Map.of(UiCore.Operation.BUILD_WORKSPACE, "fixture-build",
                UiCore.Operation.RUN_SERVER, "fixture-server", UiCore.Operation.CREATE_WORKSPACE, "fixture-create"), Map.of(), "registered")));
    }

    JsonObject request(String operation) {
        JsonObject value = JsonParser.parseString("{\"supportedVersions\":[\"1\"],\"expectedRevision\":0,\"generatorId\":\"fabric-26.3\"}").getAsJsonObject();
        value.addProperty("operation", operation); return value;
    }

    JsonObject send(WorkspaceApplicationService service, JsonObject request) {
        return service.preview(id, UUID.randomUUID(), request, writable);
    }

    JsonObject command(WorkspaceApplicationService service, String operation) {
        JsonObject value = request("command"); value.addProperty("command", operation);
        return send(service, value);
    }

    void code(JsonObject response, String code) { assertTrue(response.toString().contains(code), response.toString()); }

    @Test void discoveryDistinguishesRegistrationEnvironmentAndUnreadyOperations() {
        var service = service(PreviewCapabilityCatalog.official());
        var result = send(service, JsonParser.parseString("{\"supportedVersions\":[\"1\"],\"operation\":\"catalog\"}").getAsJsonObject());
        assertEquals(10, result.getAsJsonArray("targets").size());
        assertEquals(2, result.getAsJsonArray("targets").asList().stream().filter(value -> value.getAsJsonObject().get("trackId").getAsString().equals("advanced")).count());
        assertTrue(result.toString().contains("PREVIEW_NATIVE_OPERATION_UNVERIFIED"));
        assertTrue(result.toString().contains("p263-ticket-03"));
        assertFalse(result.toString().contains("native_preview"));
        send(service, request("opt_in"));
        var unsupportedType = request("command"); unsupportedType.addProperty("command", "create_mod_element");
        unsupportedType.add("payload", JsonParser.parseString("{\"elementType\":\"loottable\",\"name\":\"unverified\"}"));
        code(send(service, unsupportedType), "PREVIEW_ELEMENT_UNVERIFIED");
        code(command(service, "run_client"), "PREVIEW_NATIVE_OPERATION_UNVERIFIED");
        var missing = service(new PreviewCapabilityCatalog(Map.of()));
        send(missing, request("opt_in"));
        code(command(missing, "build_workspace"), "PREVIEW_ENVIRONMENT_UNVERIFIED");
    }

    @Test void qualificationOpensOnlyTheProvenOperationsForEachLoader() {
        var catalog = PreviewCapabilityCatalog.official();
        for (String target : List.of("fabric-26.3", "neoforge-26.3")) {
            var capabilities = catalog.projection(target);
            assertEquals("registered", capabilities.get("supportStage").getAsString());
            var ready = capabilities.getAsJsonArray("operations").asList().stream()
                    .map(com.google.gson.JsonElement::getAsJsonObject).filter(item -> item.get("ready").getAsBoolean())
                    .map(item -> item.get("operation").getAsString()).collect(java.util.stream.Collectors.toSet());
            var expected = new java.util.HashSet<>(java.util.Set.of("create_workspace", "update_workspace_file", "validate_workspace",
                    "generate_workspace", "build_workspace", "run_server"));
            expected.addAll(java.util.Set.of("prepare_game_tests", "run_gametest", "export_workspace"));
            expected.addAll(java.util.Set.of("create_mod_element", "update_mod_element"));
            assertEquals(expected, ready);
            var supported = capabilities.getAsJsonArray("elementTypes").asList().stream()
                    .map(com.google.gson.JsonElement::getAsJsonObject).filter(item -> item.get("supported").getAsBoolean())
                    .map(item -> item.get("elementType").getAsString()).collect(java.util.stream.Collectors.toSet());
            assertEquals(java.util.Set.of("item", "block", "recipe", "function", "code"), supported);
        }
    }

    @Test void explicitChoiceAllowsOnlyEvidenceBackedOperationsAndRevocationStopsThem() {
        var service = service(fixture());
        code(command(service, "build_workspace"), "PREVIEW_OPT_IN_REQUIRED");
        assertEquals("succeeded", send(service, request("opt_in")).get("status").getAsString());
        assertEquals("accepted", command(service, "build_workspace").get("status").getAsString());
        code(command(service, "run_client"), "PREVIEW_NATIVE_OPERATION_UNVERIFIED");
        code(command(service, "create_mod_element"), "PREVIEW_OPERATION_UNSUPPORTED");
        assertEquals("succeeded", send(service, request("revoke")).get("status").getAsString());
        code(command(service, "build_workspace"), "PREVIEW_OPT_IN_REQUIRED");
    }

    @Test void negotiationLegacyCallsAndPermissionsCannotBeBypassed() {
        var service = service(fixture());
        send(service, request("opt_in"));
        JsonObject bad = request("command"); bad.addProperty("command", "build_workspace"); bad.remove("supportedVersions");
        code(send(service, bad), "PREVIEW_VERSION_UNSUPPORTED");
        var legacy = service.execute(UiCore.Command.of(UUID.randomUUID(), id, 0, UiCore.Operation.BUILD_WORKSPACE, new JsonObject()), writable);
        assertEquals("PREVIEW_NEGOTIATION_REQUIRED", legacy.result().diagnostics().getFirst().code());
        var readOnly = new UiCore.RequestContext(UiCore.Actor.HEADLESS, UiCore.PermissionProfile.READ_ONLY);
        JsonObject build = request("command"); build.addProperty("command", "build_workspace");
        code(service.preview(id, UUID.randomUUID(), build, readOnly), "PERMISSION_DENIED");
        code(service.preview(id, UUID.randomUUID(), request("opt_in"), readOnly), "PERMISSION_DENIED");
        code(command(service, "run_server"), "USER_APPROVAL_REQUIRED");
        build.add("payload", JsonParser.parseString("{\"taskAuthorizationId\":\"forged\"}"));
        code(send(service, build), "TASK_AUTHORIZATION_INVALID");
    }

    @Test void choiceIsBoundToWorkspaceExactTargetAndNewWorkspaceDirectory() {
        var service = service(fixture());
        send(service, request("opt_in"));
        UUID other = UUID.randomUUID(); store.register(state(other, "fabric-26.3"));
        JsonObject build = request("command"); build.addProperty("command", "build_workspace");
        code(service.preview(other, UUID.randomUUID(), build, writable), "PREVIEW_OPT_IN_REQUIRED");
        build.addProperty("generatorId", "neoforge-26.3"); code(send(service, build), "PREVIEW_TARGET_MISMATCH");
        JsonObject selection = request("opt_in"); selection.addProperty("workspaceFolderPath", java.nio.file.Path.of("output/p263-06/selected").toAbsolutePath().toString());
        send(service, selection);
        JsonObject create = request("command"); create.addProperty("command", "create_workspace");
        JsonObject payload = new JsonObject(); payload.addProperty("generatorId", "fabric-26.3");
        payload.addProperty("workspaceFolderPath", java.nio.file.Path.of("output/p263-06/different").toAbsolutePath().toString());
        create.add("payload", payload); code(send(service, create), "PREVIEW_OPT_IN_REQUIRED");
        payload.add("workspaceFolderPath", selection.get("workspaceFolderPath")); create.add("payload", payload);
        code(send(service, create), "USER_APPROVAL_REQUIRED");
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(selection.get("workspaceFolderPath").getAsString())));
    }

    @Test void staleRevisionAndTargetChangesAreReevaluatedAndReconnectRequiresNewChoice() {
        var service = service(fixture()); send(service, request("opt_in"));
        store.transact(id, 0, state -> RevisionedWorkspaceStore.Decision.commit(true, List.of("/name")));
        code(command(service, "build_workspace"), "REVISION_CONFLICT");
        JsonObject build = request("command"); build.addProperty("command", "build_workspace"); build.addProperty("expectedRevision", 1);
        assertEquals("accepted", send(service, build).get("status").getAsString());
        code(send(service(fixture()), build), "PREVIEW_OPT_IN_REQUIRED");
        store.replace(id, 1, state(id, "neoforge-26.3"), java.util.Set.of("/generator"));
        build.addProperty("expectedRevision", 2); build.addProperty("generatorId", "neoforge-26.3");
        code(send(service, build), "PREVIEW_OPT_IN_REQUIRED");
    }
}
