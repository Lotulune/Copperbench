package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.StructuredElementDefaults;
import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeElementReferenceValidationTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest
    @CsvSource({"fabric-1.21.1,false", "fabric-1.21.1,true", "neoforge-1.21.1,false", "neoforge-1.21.1,true"})
    void nativeReferencesRetainInternalAndRegistryIdentitiesAcrossReopen(String generator, boolean storedId) throws Exception {
        UUID workspaceId = UUID.randomUUID();
        Path document = root.resolve("references.mcreator");
        try (Workspace workspace = fixture(generator, storedId)) {
            assertReferences(workspace, workspaceId);
        }
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null)) {
            assertReferences(workspace, workspaceId);
        }
    }

    private void assertReferences(Workspace workspace, UUID workspaceId) throws Exception {
        var state = map(workspace, workspaceId);
        var target = state.elements().stream().filter(element -> element.name().equals("kept_registry")).findFirst().orElseThrow();
        assertNotNull(workspace.getModElementByName("MyGem"));
        assertEquals("kept_registry", workspace.getModElementByName("MyGem").getRegistryName());
        Map<String, String> before = inventory();
        assertTrue(issues(state).isEmpty(), () -> issues(state).toString());
        for (String invalid : List.of("CUSTOM:kept_registry", "CUSTOM:mygem", "CUSTOM:missing")) {
            var changed = state.copy();
            references(changed, invalid);
            assertEquals(2, issues(changed).size(), invalid);
            assertTrue(issues(changed).stream().allMatch(issue -> issue.code().equals("FIELD_REFERENCE_INVALID")), invalid);
        }
        assertEquals(before, inventory(), "Validation must not rewrite names, definitions or workspace metadata");
        assertEquals("kept_registry", state.element(target.id()).name());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aDeletedTargetCannotResolveThroughStaleUpstreamMetadata(boolean storedId) throws Exception {
        try (Workspace workspace = fixture("fabric-1.21.1", storedId)) {
            var state = map(workspace, UUID.randomUUID());
            var target = state.elements().stream().filter(element -> element.name().equals("kept_registry")).findFirst().orElseThrow();
            state.removeElement(target.id());
            state.addElement(new WorkspaceState.Element(UUID.randomUUID(), "item", "kept_registry", "Replacement",
                    "valid", "generated", Instant.now(), new JsonObject()));
            assertEquals(2, issues(state).size(), "A new element with the same projected name is not the old native target");
            assertTrue(issues(state).stream().allMatch(issue -> issue.code().equals("FIELD_REFERENCE_INVALID")));
        }
    }

    @Test void newlyCreatedTargetsResolveBeforeTheUpstreamSnapshotIsRefreshed() throws Exception {
        try (Workspace workspace = fixture("fabric-1.21.1", false)) {
            var state = map(workspace, UUID.randomUUID());
            state.addElement(new WorkspaceState.Element(UUID.randomUUID(), "item", "session_item", "Session Item",
                    "valid", "generated", Instant.now(), new JsonObject()));
            references(state, "CUSTOM:session_item");
            assertTrue(issues(state).isEmpty(), () -> issues(state).toString());
        }
    }

    private Workspace fixture(String generator, boolean storedId) throws Exception {
        var settings = new WorkspaceSettings("references");
        settings.setCurrentGenerator(generator); settings.setModName("References"); settings.setVersion("1.0.0");
        Workspace workspace = Workspace.createWorkspace(root.resolve("references.mcreator").toFile(), settings);
        var target = new ModElement(workspace, "MyGem", ModElementType.ITEM);
        target.setRegistryName("kept_registry");
        if (storedId) target.putMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA, UUID.randomUUID().toString());
        workspace.addModElement(target);
        workspace.getModElementManager().storeModElement(StructuredElementDefaults.item(target, workspace, "My Gem"));
        var holder = new ModElement(workspace, "filled_vessel", ModElementType.ITEM);
        workspace.addModElement(holder);
        var item = StructuredElementDefaults.item(holder, workspace, "Filled Vessel");
        item.recipeRemainder = new MItemBlock(workspace, "CUSTOM:MyGem");
        workspace.getModElementManager().storeModElement(item);
        var recipeElement = new ModElement(workspace, "gem_recipe", ModElementType.RECIPE);
        workspace.addModElement(recipeElement);
        var recipe = StructuredElementDefaults.recipe(recipeElement, workspace, "gem_recipe");
        recipe.recipeSlots[0] = new MItemBlock(workspace, "CUSTOM:MyGem");
        recipe.recipeReturnStack = new MItemBlock(workspace, "CUSTOM:MyGem");
        recipe.recipeShapeless = true;
        workspace.getModElementManager().storeModElement(recipe);
        workspace.getFileManager().saveWorkspaceDirectlyAndWait();
        return workspace;
    }

    private static WorkspaceState map(Workspace workspace, UUID id) throws Exception {
        return new MCreatorWorkspaceStateMapper().map(workspace, new ProductMetadataManager.Metadata(1, id, 0));
    }

    private static void references(WorkspaceState state, String reference) {
        for (var element : state.elements()) {
            JsonObject values = element.values();
            if (element.name().equals("filled_vessel")) values.addProperty("recipeRemainder", reference);
            else if (element.type().equals("recipe")) {
                values.addProperty("recipeReturnStack", reference);
                values.getAsJsonArray("recipeSlots").set(0, new com.google.gson.JsonPrimitive(reference));
            } else continue;
            state.replaceElement(new WorkspaceState.Element(element.id(), element.type(), element.name(), element.displayName(),
                    element.state(), element.ownership(), element.updatedAt(), values));
        }
    }

    private static List<Fabric1211Generator.ValidationIssue> issues(WorkspaceState state) {
        if (state.generator().get("id").getAsString().equals("fabric-1.21.1"))
            return new Fabric1211Generator(Path.of(".")).validate(state);
        return new NeoForge1211Generator(Path.of(".")).validate(state).stream()
                .map(issue -> new Fabric1211Generator.ValidationIssue(issue.code(), issue.message(), issue.path(), issue.elementId(), issue.repairValue())).toList();
    }

    private Map<String, String> inventory() throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                result.put(name, name.equals(".copperbench/workspace.write.lock") ? "lock:" + Files.size(path)
                        : WorkspaceExecutionSnapshot.sha256(path));
            }
        }
        return result;
    }
}
