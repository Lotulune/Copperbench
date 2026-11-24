package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.release.ElementCoverageCatalog;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The element types Core offers and accepts follow the workspace generator, not the whole first-party catalog. */
class GeneratorElementTypesTest {
    @TempDir Path root;

    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @Test void dataPackWorkspaceOffersAndCreatesOnlyItsGeneratorsTypes() throws Exception {
        try (Workspace workspace = workspace("cbdp", "datapack-1.21.1"); var session = session(workspace)) {
            assertEquals(new TreeSet<>(Set.of("achievement", "biome", "damagetype", "dimension", "enchantment", "feature",
                    "function", "loottable", "recipe", "structure")), new TreeSet<>(availableTypes(session)));

            var block = session.uiEntry().execute(create(session.workspaceId(), "block", "stray_block"));
            assertNotEquals("committed", block.result().status());
            assertTrue(block.result().diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.code().equals("GENERATOR_ELEMENT_TYPE_UNSUPPORTED")), block.result().toString());
            assertNull(workspace.getModElementByName("stray_block"), "an element the generator cannot write is not stored");

            var biome = session.uiEntry().execute(create(session.workspaceId(), "biome", "probe_grove"));
            assertEquals("committed", biome.result().status(), biome.result().toString());
        }
    }

    @Test void modWorkspaceKeepsEveryTypeItsGeneratorDeclares() throws Exception {
        try (Workspace workspace = workspace("cold_mod", "fabric-1.21.1"); var session = session(workspace)) {
            List<String> offered = availableTypes(session);
            List<String> expected = ElementCoverageCatalog.FIRST_PARTY_SLICE.stream().filter(type ->
                    dev.copperbench.release.GeneratorElementCapabilityCatalog.decision("fabric-1.21.1", type).generatable()).toList();
            assertEquals(expected, offered);
            // The first-party Fabric generator declares every catalog type, so mod workspaces lose nothing.
            assertEquals(ElementCoverageCatalog.FIRST_PARTY_SLICE, offered);
        }
    }

    private Workspace workspace(String modId, String generator) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings(modId);
        settings.setModName("Element Types"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        return Workspace.createWorkspace(root.resolve(modId + ".mcreator").toFile(), settings);
    }

    private static MCreatorWorkspaceSession session(Workspace workspace) throws java.io.IOException {
        return MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
    }

    private static List<String> availableTypes(MCreatorWorkspaceSession session) {
        JsonObject payload = new JsonObject();
        payload.addProperty("search", ""); payload.add("types", new JsonArray()); payload.add("states", new JsonArray());
        payload.addProperty("limit", 50);
        var result = session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), Operation.LIST_MOD_ELEMENTS, payload));
        assertEquals("succeeded", result.status(), result.toString());
        List<String> types = new ArrayList<>();
        result.data().getAsJsonObject().getAsJsonArray("availableTypes").forEach(type -> types.add(type.getAsString()));
        return types;
    }

    private static Command create(UUID workspaceId, String type, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("clientMutationId", UUID.randomUUID().toString());
        payload.addProperty("elementType", type);
        payload.addProperty("name", name);
        payload.add("initialValues", new JsonObject());
        return Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, payload);
    }
}
