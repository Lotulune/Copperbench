package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Stage17NestedReferenceContractTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void nestedReferencesSurviveRealPersistenceAndQueriesDoNotWrite(String generator) throws Exception {
        Path document = root.resolve("nested_references.mcreator");
        WorkspaceSettings settings = new WorkspaceSettings("nested_references");
        settings.setModName("Nested References"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        UUID workspaceId = UUID.randomUUID(); String itemId, guiId;
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             MCreatorWorkspaceSession session = attach(workspace, workspaceId)) {
            itemId = create(session, 0, "item", "reference_item", """
                    {"fields":{"customProperties":{"state":"missing_property"},"glowCondition":false}}
                    """);
            guiId = create(session, 1, "gui", "reference_gui", """
                    {"components":[{"type":"button","data":{"name":"run","x":10,"y":10,
                    "width":60,"height":20,"text":"Run","onClick":"missing_click","displayCondition":null}},
                    {"type":"label","data":{"name":"fixed","x":10,"y":35,"color":-1,
                    "text":{"name":null,"fixedValue":"minecraft:stone"}}}]}
                    """);
            assertReferences(session, itemId, guiId, 2);
        }
        Map<Path, byte[]> before = new LinkedHashMap<>();
        for (String name : List.of("reference_item", "reference_gui")) {
            Path path = root.resolve("elements/" + name + ".mod.json");
            before.put(path, Files.readAllBytes(path));
        }
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null);
             MCreatorWorkspaceSession session = attach(workspace, workspaceId)) {
            assertReferences(session, itemId, guiId, 2);
            for (var entry : before.entrySet()) assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()));
            create(session, 2, "procedure", "missing_property", "{}");
            create(session, 3, "procedure", "missing_click", "{}");
            JsonObject graph = query(session, Operation.GET_WORKSPACE_REFERENCES);
            assertEquals(0, graph.getAsJsonArray("diagnostics").size());
            assertEquals(3, graph.getAsJsonArray("edges").size());
            for (var raw : graph.getAsJsonArray("edges")) {
                var edge = raw.getAsJsonObject();
                assertEquals(edge.get("kind").getAsString().equals("resource") ? "external" : "resolved", edge.get("resolution").getAsString());
            }
            for (var entry : before.entrySet()) assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()));
        }
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null);
             MCreatorWorkspaceSession session = attach(workspace, workspaceId)) {
            assertEquals(4, query(session, Operation.GET_WORKSPACE_REFERENCES).get("revision").getAsLong());
            assertEquals(0, query(session, Operation.GET_WORKSPACE_REFERENCES).getAsJsonArray("diagnostics").size());
        }
    }

    private static void assertReferences(MCreatorWorkspaceSession session, String item, String gui, long revision) {
        JsonObject graph = query(session, Operation.GET_WORKSPACE_REFERENCES);
        assertEquals(revision, graph.get("revision").getAsLong());
        assertEquals(3, graph.getAsJsonArray("edges").size(), graph.toString());
        assertEquals(1, graph.getAsJsonArray("edges").asList().stream().filter(e -> e.getAsJsonObject().get("kind").getAsString().equals("resource")
                && e.getAsJsonObject().get("target").getAsString().equals("minecraft:barrier")).count());
        assertEquals(Set.of("/elements/" + item + "/customProperties/state", "/elements/" + gui + "/components/0/data/onClick"),
                graph.getAsJsonArray("diagnostics").asList().stream().map(d -> d.getAsJsonObject().get("path").getAsString()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, query(session, Operation.GET_WORKSPACE_HEALTH).getAsJsonObject("references").get("danglingCount").getAsInt());
    }
    private static JsonObject query(MCreatorWorkspaceSession session, Operation operation) {
        var result = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), operation, new JsonObject()));
        assertEquals("succeeded", result.status(), result.diagnostics().toString());
        return result.data().getAsJsonObject();
    }
    private static String create(MCreatorWorkspaceSession session, long revision, String type, String name, String input) {
        JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", name);
        payload.add("initialValues", JsonParser.parseString(input));
        var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
        assertEquals("committed", result.status(), result.diagnostics().toString());
        return result.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
    }
    private static MCreatorWorkspaceSession attach(Workspace workspace, UUID id) throws Exception {
        return MCreatorWorkspaceSession.attach(workspace, id,
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }
}
