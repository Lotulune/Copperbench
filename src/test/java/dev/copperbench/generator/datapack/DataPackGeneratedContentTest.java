package dev.copperbench.generator.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.generator.GradleWorkspaceBackend.ValidationIssue;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The content check blocks builds, so everything a bundled data pack generator writes from defaults must pass it. */
class DataPackGeneratedContentTest {
    @TempDir Path root;

    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest @ValueSource(strings = {"datapack-1.20.1", "datapack-1.21.1", "datapack-26.1.x"})
    void everyOfferedElementTypeGeneratesContentThatPassesTheCheck(String generator) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("cbdp");
        settings.setModName("Generated Content"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("cbdp.mcreator").toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            assertTrue(workspace.getGenerator().generateBase());
            // A feature needs its Blockly graph and a structure its .nbt file; defaults provide neither, so upstream
            // generation rejects both before any content exists to check.
            List<String> types = availableTypes(session).stream().filter(type -> !Set.of("feature", "structure").contains(type)).toList();
            assertTrue(types.containsAll(List.of("achievement", "biome", "function", "loottable", "recipe")), types.toString());
            long revision = 0;
            List<String> rejected = new ArrayList<>();
            for (String type : types) {
                var created = session.uiEntry().execute(create(session.workspaceId(), revision, type, "probe_" + type));
                if (!"committed".equals(created.result().status())) rejected.add(type + ": " + created.result().diagnostics());
                else revision = created.result().newRevision();
            }
            assertEquals(List.of(), rejected);
            for (ModElement element : workspace.getModElements())
                workspace.getGenerator().generateElement(element.getGeneratableElement(), true);
            // Hand-written content that loaded in the Fabric 1.21.1 GameTest host (see examples/datapack-load) uses 1.21 folders.
            if (!generator.equals("datapack-1.20.1")) {
                Path fixture = Path.of("examples/datapack-load/pack");
                try (var files = Files.walk(fixture)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Path target = root.resolve("src/main").resolve(fixture.relativize(file).toString());
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target);
                    }
                }
            }

            List<String> log = new ArrayList<>();
            List<ValidationIssue> issues = DataPackContentValidator.validate(root.resolve("src/main"), generator, "cbdp", log::add);
            assertEquals(List.of(), issues, () -> generator + " (" + String.join(", ", types) + "):\n" + issues.stream()
                    .map(issue -> issue.code() + " " + issue.message()).collect(Collectors.joining("\n")));
            try (var files = Files.walk(root.resolve("src/main/data"))) {
                assertTrue(files.filter(Files::isRegularFile).count() >= types.size(), log.toString());
            }
            // From 1.21 the recipe's unlocking advancement rewards the recipe by ID, so references are resolved too.
            assertTrue(log.getLast().matches("Data pack content check: \\d+ files, "
                    + (generator.equals("datapack-1.20.1") ? "\\d+" : "[1-9]\\d*") + " references into namespace cbdp"), log.toString());
        }
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

    private static Command create(UUID workspaceId, long revision, String type, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("clientMutationId", UUID.randomUUID().toString());
        payload.addProperty("elementType", type);
        payload.addProperty("name", name);
        JsonObject values = new JsonObject();
        if (type.equals("recipe")) {
            // Defaults leave the crafting grid empty, which upstream generation cannot write.
            values.addProperty("recipeType", "Crafting"); values.addProperty("recipeShapeless", true);
            JsonArray slots = new JsonArray(); slots.add("Items.STICK");
            for (int index = 1; index < 9; index++) slots.add("");
            values.add("recipeSlots", slots); values.addProperty("recipeReturnStack", "Items.DIAMOND");
            JsonArray unlocking = new JsonArray(); unlocking.add("Items.STICK");
            values.add("unlockingItems", unlocking);
        }
        payload.add("initialValues", values);
        return Command.of(UUID.randomUUID(), workspaceId, revision, Operation.CREATE_MOD_ELEMENT, payload);
    }
}
