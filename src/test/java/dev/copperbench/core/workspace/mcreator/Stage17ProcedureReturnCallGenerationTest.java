package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.blockly.BlocklyCompileNote;
import net.mcreator.element.types.Procedure;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Stage17ProcedureReturnCallGenerationTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void returnCallsResolveStableIdentityWithoutRelyingOnFieldOrder(String generator) throws Exception {
        var settings = new WorkspaceSettings("return_calls"); settings.setModName("Return Calls");
        settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        Path document = root.resolve("return_calls.mcreator"); UUID workspaceId = UUID.randomUUID();
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            String id = create(session, 0, "target", returning("<block type=\"math_number\"><field name=\"NUM\">17</field></block>"));
            create(session, 1, "named", call("<field name=\"procedure\">target</field>"));
            String named = generated(workspace, "named");
            assertTrue(named.contains("targetProcedure.execute("), named);
            create(session, 2, "identified", call("<field name=\"procedureId\">" + id + "</field>"));
            assertEquals(named, generated(workspace, "identified"));
            create(session, 3, "hinted", call("<field name=\"procedure\">stale_hint</field><field name=\"procedureId\">" + id + "</field>"));
            assertEquals(named, generated(workspace, "hinted"));
            create(session, 4, "missing", call("<field name=\"procedure\">target</field><field name=\"procedureId\">" + UUID.randomUUID() + "</field>"));
            var missing = ((Procedure) workspace.getModElementByName("missing").getGeneratableElement()).getBlocklyToProcedure(new HashMap<>());
            assertTrue(missing.getCompileNotes().stream().anyMatch(note -> note.type() == BlocklyCompileNote.Type.ERROR));
            assertFalse(missing.getGeneratedCode().contains("targetProcedure"));
            byte[] bytes = Files.readAllBytes(root.resolve("elements/identified.mod.json"));
            assertEquals(named, generated(workspace, "identified"));
            assertArrayEquals(bytes, Files.readAllBytes(root.resolve("elements/identified.mod.json")));
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            assertEquals(generated(reopened, "named"), generated(reopened, "identified"));
            assertEquals(generated(reopened, "named"), generated(reopened, "hinted"));
        }
    }

    private static JsonObject returning(String value) {
        var values = new JsonObject();
        values.addProperty("procedurexml", "<xml><block type=\"event_trigger\"><field name=\"trigger\">no_ext_trigger</field><next>"
                + "<block type=\"return_number\"><value name=\"VALUE\">" + value + "</value></block></next></block></xml>");
        return values;
    }
    private static JsonObject call(String fields) { return returning("<block type=\"procedure_retval_number\">" + fields + "</block>"); }
    private static String generated(Workspace workspace, String name) throws Exception {
        var compiler = ((Procedure) workspace.getModElementByName(name).getGeneratableElement()).getBlocklyToProcedure(new HashMap<>());
        assertTrue(compiler.getCompileNotes().stream().noneMatch(note -> note.type() == BlocklyCompileNote.Type.ERROR), compiler.getCompileNotes().toString());
        assertEquals("number", compiler.getReturnType().getName());
        return compiler.getGeneratedCode();
    }
    private static String create(MCreatorWorkspaceSession session, long revision, String name, JsonObject values) {
        var payload = new JsonObject(); payload.addProperty("elementType", "procedure"); payload.addProperty("name", name); payload.add("initialValues", values);
        var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
        assertEquals("committed", result.status(), result.diagnostics().toString());
        return result.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
    }
}
