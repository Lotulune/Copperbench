package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real adapter/temporary-workspace regression; does not run Minecraft or Gradle. */
class GenerationConflictDiagnosticTest {
    @TempDir Path root;

    @BeforeAll static void initialize() throws Exception {
        McreatorTestRuntime.ensureInitialized();
    }

    @Test void taskPreparationReportsTheProtectedFileWithoutOverwritingOrStartingDependencies() throws Exception {
        var settings = new WorkspaceSettings("cold_fields");
        settings.setCurrentGenerator("fabric-1.21.1");
        settings.setModName("Cold Fields");
        settings.setVersion("1.0.0");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("cold_fields.mcreator").toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            Files.writeString(root.resolve("build.gradle"), "// Never executed by this guard test\n");
            Files.createDirectories(root.resolve("gradle/wrapper"));
            Files.write(root.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[0]);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            try (var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
                    Clock.systemUTC(), UUID::randomUUID)) {
                JsonObject payload = new JsonObject();
                payload.addProperty("elementType", "block");
                payload.addProperty("name", "cold_block");
                JsonObject values = new JsonObject();
                values.addProperty("hardness", 4);
                payload.add("initialValues", values);
                var response = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0,
                        Operation.CREATE_MOD_ELEMENT, payload));
                assertEquals("committed", response.result().status());
                String relative = "src/main/java/net/mcreator/cold_fields/block/cold_blockBlock.java";
                Path target = root.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.writeString(target, "// Externally authored source\n");
                var state = new MCreatorWorkspaceStateMapper().map(workspace,
                        workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()));
                var store = new RevisionedWorkspaceStore();
                store.register(state);
                var failure = assertThrows(WorkspaceTaskGateway.GenerationPreparationException.class,
                        () -> new MCreatorGenerationPreparation(workspace, store).prepare(state, root,
                                Operation.BUILD_WORKSPACE, ignored -> fail("Must reject before dependency setup")));
                assertEquals("GENERATION_SOURCE_CONFLICT", failure.code());
                assertTrue(failure.getMessage().contains(relative), failure.getMessage());
                assertTrue(failure.getMessage().contains("source no longer matches"), failure.getMessage());
                assertEquals(relative, failure.conflicts().getFirst().relativePath());
                assertEquals(WorkspaceTaskGateway.GenerationPreparationException.ConflictReason.SOURCE_CHANGED,
                        failure.conflicts().getFirst().reason());
                assertNull(failure.getCause());
                assertEquals("// Externally authored source\n", Files.readString(target));
                assertNotNull(workspace.getMetadata(MCreatorGenerationPreparation.PENDING));
                assertEquals(state.revision(), store.read(state.id()).orElseThrow().revision());
            }
        }
    }
}
