package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.procedure.ProcedureIrCodec;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.blockly.BlocklyCompileNote;
import net.mcreator.element.types.Procedure;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Stage17ProcedureCallGenerationTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void stableUuidAndNameCallsGenerateTheSameTarget(String generator) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("call_contract"); settings.setModName("Call Contract");
        settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        Path document = root.resolve("call_contract.mcreator"); UUID workspaceId = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            String target = create(session, 0, "target_proc", new JsonObject());
            create(session, 1, "named_caller", body("procedure", "target_proc"));
            String namedCode = generated(workspace, "named_caller");
            assertTrue(namedCode.contains("target_procProcedure.execute("), namedCode);
            create(session, 2, "uuid_caller", body("procedureId", target));
            var graph = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_WORKSPACE_REFERENCES, new JsonObject()));
            assertEquals(0, graph.data().getAsJsonObject().getAsJsonArray("diagnostics").size(), graph.data().toString());
            assertEquals(namedCode, generated(workspace, "uuid_caller"));
            JsonObject staleHint = body("procedure", "stale_name_hint");
            staleHint.getAsJsonObject("procedureIr").getAsJsonArray("nodes").forEach(raw -> {
                var node = raw.getAsJsonObject();
                if (node.get("type").getAsString().equals("call_procedure")) node.getAsJsonObject("fields").addProperty("procedureId", target);
            });
            create(session, 3, "hinted_caller", staleHint);
            assertEquals(namedCode, generated(workspace, "hinted_caller"), "A stale name hint must not override the stable target, regardless of field order");
            JsonObject missingId = body("procedure", "target_proc");
            missingId.getAsJsonObject("procedureIr").getAsJsonArray("nodes").forEach(raw -> {
                var node = raw.getAsJsonObject();
                if (node.get("type").getAsString().equals("call_procedure")) node.getAsJsonObject("fields").addProperty("procedureId", UUID.randomUUID().toString());
            });
            create(session, 4, "missing_caller", missingId);
            var missing = ((Procedure) workspace.getModElementByName("missing_caller").getGeneratableElement()).getBlocklyToProcedure(new HashMap<>());
            assertTrue(missing.getCompileNotes().stream().anyMatch(note -> note.type() == BlocklyCompileNote.Type.ERROR));
            assertFalse(missing.getGeneratedCode().contains("target_procProcedure"), "A missing stable identity must not silently fall back to a name hint");
            byte[] before = Files.readAllBytes(root.resolve("elements/uuid_caller.mod.json"));
            assertEquals(namedCode, generated(workspace, "uuid_caller"));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("elements/uuid_caller.mod.json")));
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            assertEquals(generated(reopened, "named_caller"), generated(reopened, "uuid_caller"));
            assertEquals(generated(reopened, "named_caller"), generated(reopened, "hinted_caller"));
            var target = reopened.getModElementByName("target_proc");
            target.putMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA, null);
            String importedId = MCreatorWorkspaceStateMapper.elementId(workspaceId, target).toString();
            assertEquals("target_proc", dev.copperbench.procedure.WorkspaceProcedureTargets.resolveName(reopened, importedId));
            assertNull(dev.copperbench.procedure.WorkspaceProcedureTargets.resolveName(reopened, "Target Proc"), "Display labels are not bindings");
        }
    }

    private static JsonObject body(String field, String target) {
        String xml = "<xml><block type=\"event_trigger\"><field name=\"trigger\">no_ext_trigger</field><next>"
                + "<block type=\"call_procedure\"><field name=\"" + field + "\">" + target + "</field></block>"
                + "</next></block></xml>";
        var codec = new ProcedureIrCodec(); JsonObject values = new JsonObject();
        values.add("procedureIr", codec.toJson(codec.fromBlocklyXml(xml, UUID.randomUUID()))); return values;
    }
    private static String generated(Workspace workspace, String name) throws Exception {
        var compiler = ((Procedure) workspace.getModElementByName(name).getGeneratableElement()).getBlocklyToProcedure(new HashMap<>());
        assertTrue(compiler.getCompileNotes().stream().noneMatch(note -> note.type() == BlocklyCompileNote.Type.ERROR), compiler.getCompileNotes().toString());
        return compiler.getGeneratedCode();
    }
    private static String create(MCreatorWorkspaceSession session, long revision, String name, JsonObject values) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", "procedure"); payload.addProperty("name", name); payload.add("initialValues", values);
        var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
        assertEquals("committed", result.status(), result.diagnostics().toString());
        return result.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
    }
}
