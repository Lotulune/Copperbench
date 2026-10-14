package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.element.types.Block;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import net.mcreator.ui.workspace.resources.TextureType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuredCreatorPersistenceTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }

    @ParameterizedTest @ValueSource(strings = {"fabric-1.21.1", "neoforge-1.21.1"})
    void creatorsAndLightingGenerateAndSurviveReopen(String generator) throws Exception {
        Path document = root.resolve("creator_check.mcreator");
        WorkspaceSettings settings = new WorkspaceSettings("creator_check");
        settings.setModName("Creator Check"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            assertTrue(workspace.getGenerator().generateBase());
            writeTexture(workspace);
            long revision = 0;
            for (String type : List.of("tool", "livingentity", "specialentity")) {
                JsonObject contract = ElementFieldContract.discover(type, generator);
                assertTrue(contract.get("complete").getAsBoolean(), contract.toString());
                var payload = contract.getAsJsonObject("minimalExample").deepCopy();
                JsonObject values = payload.getAsJsonObject("initialValues");
                if (type.equals("livingentity")) values.addProperty("mobModelTexture", "creator_entity.png");
                if (type.equals("specialentity")) values.addProperty("entityTexture", "creator_entity.png");
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision,
                        Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertEquals("committed", result.status(), result.diagnostics().toString());
                revision = result.newRevision();
                var definition = workspace.getModElementByName("discovery_" + type).getGeneratableElement();
                assertTrue(workspace.getGenerator().generateElement(definition), type);
            }
            var payload = JsonParser.parseString("""
                    {"elementType":"block","name":"creator_lamp","initialValues":{"luminance":15,"lightOpacity":0}}
                    """).getAsJsonObject();
            var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision,
                    Operation.CREATE_MOD_ELEMENT, payload)).result();
            assertEquals("committed", created.status(), created.diagnostics().toString());
            var lamp = workspace.getModElementByName("creator_lamp");
            assertEquals(15, ((Block) lamp.getGeneratableElement()).luminance.getFixedValue());
            String source = Files.readString(lamp.getAssociatedFiles().stream().filter(f -> f.getName().endsWith("Block.java")).findFirst().orElseThrow().toPath());
            assertTrue(source.contains(".lightLevel("), source);
            assertTrue(source.contains("getLightBlock"), source);
            var update = new JsonObject();
            update.addProperty("elementId", created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString());
            update.add("changes", JsonParser.parseString("[{\"path\":\"/fields/luminance\",\"value\":{\"fixedValue\":7}},{\"path\":\"/lightOpacity\",\"value\":3}]"));
            var edited = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), created.newRevision(),
                    Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("committed", edited.status(), edited.diagnostics().toString());
        }
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null)) {
            assertEquals(4, reopened.getModElements().size());
            Block block = (Block) reopened.getModElementByName("creator_lamp").getGeneratableElement();
            assertEquals(7, block.luminance.getFixedValue()); assertEquals(3, block.lightOpacity);
            assertTrue(reopened.getGenerator().generateBase());
            for (var element : reopened.getModElements()) assertTrue(reopened.getGenerator().generateElement(element.getGeneratableElement()));
        }
    }

    @Test void missingAndUnknownTexturesHaveFieldDiagnosticsAndLeaveNoElements() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("missing_texture");
        settings.setModName("Missing Texture"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("missing_texture.mcreator").toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            assertTrue(workspace.getGenerator().generateBase());
            for (String type : List.of("livingentity", "specialentity", "tool")) {
                JsonObject payload = new JsonObject(); payload.addProperty("elementType", type); payload.addProperty("name", "missing_" + type);
                JsonObject values = new JsonObject();
                String field = type.equals("tool") ? "texture" : type.equals("livingentity") ? "mobModelTexture" : "entityTexture";
                values.addProperty(field, ""); payload.add("initialValues", values);
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertEquals("FIELD_REQUIRED", result.diagnostics().getFirst().code());
                assertTrue(result.diagnostics().getFirst().path().endsWith("/" + field));
                assertEquals(0, result.newRevision());
                values.addProperty(field, type.equals("livingentity") ? "missing.png" : "missing");
                result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertEquals("FIELD_REFERENCE_INVALID", result.diagnostics().getFirst().code(), result.diagnostics().toString());
                assertTrue(workspace.getModElements().isEmpty());
                assertEquals(0, result.newRevision());
            }
        }
    }

    static void writeTexture(Workspace workspace) throws Exception {
        Path texture = workspace.getFolderManager().getTextureFile("creator_entity", TextureType.ENTITY).toPath();
        Files.createDirectories(texture.getParent());
        var image = new java.awt.image.BufferedImage(128, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, 0xffc47c32);
        javax.imageio.ImageIO.write(image, "png", texture.toFile());
    }
}
