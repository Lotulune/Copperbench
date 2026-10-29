package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ElementInputPreflightTest {
    private final UUID workspaceId = UUID.randomUUID();
    private final RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
    private final WorkspaceApplicationService service;

    ElementInputPreflightTest() {
        store.register(new WorkspaceState(workspaceId, "Input diagnostics", "mod", 0, false,
                json("{\"id\":\"fabric-1.21.1\",\"state\":\"ready\"}"), new JsonObject(), List.of()));
        service = new WorkspaceApplicationService(store,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }

    @ParameterizedTest @EnumSource(value = Actor.class, names = {"UI", "MCP", "HEADLESS"})
    void allEntriesReportEveryMissingCreateFieldBeforeRecoveryOrMutation(Actor actor) {
        var outcome = service.execute(Command.of(UUID.randomUUID(), workspaceId, 0,
                Operation.CREATE_MOD_ELEMENT, new JsonObject()), new RequestContext(actor, PermissionProfile.WORKSPACE));
        assertEquals("rejected", outcome.result().status());
        assertEquals(List.of("/elementType", "/name", "/initialValues"),
                outcome.result().diagnostics().stream().map(Diagnostic::path).toList());
        for (var diagnostic : outcome.result().diagnostics()) {
            assertEquals("FIELD_REQUIRED", diagnostic.code());
            assertEquals("missing", diagnostic.message().args().get("actualType").getAsString());
            assertEquals(3, diagnostic.message().args().getAsJsonArray("missingFields").size());
            assertFalse(diagnostic.message().args().get("repairHint").getAsString().isBlank());
        }
        assertUnchanged();
        assertTrue(outcome.events().isEmpty());
        assertNull(outcome.result().recoveryPointId());
    }

    @ParameterizedTest @ValueSource(strings = {"null", "[]", "42", "\"text\""})
    void rejectsInvalidInitialValuesWithAnObjectExpectation(String raw) {
        var result = create("{\"elementType\":\"block\",\"name\":\"first_block\",\"initialValues\":" + raw + "}");
        assertEquals("rejected", result.status());
        assertEquals("/initialValues", result.diagnostics().getFirst().path());
        assertEquals("object", result.diagnostics().getFirst().message().args().get("expectedType").getAsString());
        assertUnchanged();
    }

    @Test void missingTexturesAreListedAndCorrectingTheSameInputCanCommit() {
        var result = create("{\"elementType\":\"specialentity\",\"name\":\"first_boat\",\"initialValues\":{}}");
        assertEquals(List.of("/initialValues/entityTexture", "/initialValues/itemTexture"),
                result.diagnostics().stream().map(Diagnostic::path).toList());
        assertUnchanged();
        var fixed = create("""
                {"elementType":"specialentity","name":"first_boat",
                 "initialValues":{"fields":{"entityTexture":"boat_skin","itemTexture":"boat_item"}}}
                """);
        assertEquals("committed", fixed.status(), fixed.diagnostics().toString());
        assertEquals(1, fixed.newRevision());
    }

    @Test void nestedFieldTypeHasTheActualInputPathAndIsNotCoerced() {
        var result = create("""
                {"elementType":"tool","name":"first_tool","initialValues":{"fields":{"texture":12}}}
                """);
        var issue = result.diagnostics().getFirst();
        assertEquals("FIELD_TYPE_INVALID", issue.code());
        assertEquals("/initialValues/fields/texture", issue.path());
        assertEquals("string", issue.message().args().get("expectedType").getAsString());
        assertEquals("number", issue.message().args().get("actualType").getAsString());
        assertUnchanged();
    }

    @Test void planRejectsBeforeApplyAndKeepsAllMissingFieldsInItsDiagnostic() {
        var result = service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.PLAN_WORKSPACE_CHANGES, json("""
                {"expectedRevision":0,"idempotencyKey":"missing-input","operations":[
                  {"operation":"create_mod_element","payload":{"elementType":"specialentity","name":"first_boat","initialValues":{}}}
                ]}
                """)), new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
        assertEquals("failed", result.status());
        assertEquals("FIELD_REQUIRED", result.diagnostics().getFirst().code());
        assertEquals(2, result.diagnostics().getFirst().message().args().getAsJsonArray("missingFields").size());
        assertUnchanged();
    }

    @Test void supportedDefaultsAndUnknownPluginValuesAreStillPreserved() {
        var result = create("""
                {"elementType":"item","name":"first_item","initialValues":{"pluginExtra":{"keep":true}}}
                """);
        assertEquals("committed", result.status());
        assertTrue(store.read(workspaceId).orElseThrow().elements().getFirst().values().getAsJsonObject("pluginExtra").get("keep").getAsBoolean());
    }

    @Test void toolGuiTextureAlternativeIsNotRejectedAsMissingPrimaryTexture() {
        assertTrue(ElementInputPreflight.requiredValues("tool", json("{\"guiTexture\":\"custom_icon\"}"), null).isEmpty());
        assertEquals("FIELD_REQUIRED", ElementInputPreflight.requiredValues("tool", json("{\"guiTexture\":\"\"}"), null).getFirst().code());
    }

    @Test void environmentExposesActualGeneratorMatrixAndPreflightScope() {
        var result = service.query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_WORKSPACE_ENVIRONMENT,
                new JsonObject()), new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
        assertEquals("succeeded", result.status());
        var data = result.data().getAsJsonObject();
        assertEquals("fabric-1.21.1", data.getAsJsonObject("versionTracks").getAsJsonObject("currentWorkspace")
                .getAsJsonObject("generator").get("id").getAsString());
        assertTrue(data.getAsJsonObject("fieldContracts").getAsJsonObject("inputPreflight")
                .getAsJsonObject("requiredGenerationFields").has("tool"));
    }

    private CommandResult create(String payload) {
        return service.execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, json(payload)),
                new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)).result();
    }
    private void assertUnchanged() {
        var state = store.read(workspaceId).orElseThrow();
        assertEquals(0, state.revision());
        assertTrue(state.elements().isEmpty());
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
