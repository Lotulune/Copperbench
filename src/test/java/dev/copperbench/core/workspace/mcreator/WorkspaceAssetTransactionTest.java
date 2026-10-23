package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceAssetTransactionTest {
    @TempDir Path temp;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    Workspace create(Path root) throws Exception {
        Files.createDirectories(root);
        WorkspaceSettings settings = new WorkspaceSettings("review_probe");
        settings.setModName("Review Probe"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        return Workspace.createWorkspace(root.resolve("review_probe.mcreator").toFile(), settings);
    }

    @ParameterizedTest @ValueSource(strings = {"single", "batch", "move"})
    void assetMutationMustPersistRevisionAndPermitNextEdit(String kind) throws Exception {
        Path root = temp.resolve("workspace");
        Path source = temp.resolve("source.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", source.toFile());
        try (Workspace workspace = create(root);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var entry = session.uiEntry();
            JsonObject previewPayload = new JsonObject();
            previewPayload.addProperty("targetRelativePath", "assets/review_probe/textures/block/imported.png");
            Operation previewOperation = Operation.PREVIEW_ASSET_IMPORT, applyOperation = Operation.IMPORT_ASSET;
            if (kind.equals("move")) {
                Path original = root.resolve("assets/review_probe/textures/block/original.png");
                Files.createDirectories(original.getParent()); Files.copy(source, original);
                previewPayload.addProperty("sourceAssetId", new AssetWorkspaceService(root).list().getFirst().id());
                previewOperation = Operation.PREVIEW_ASSET_MOVE; applyOperation = Operation.MOVE_ASSET;
            } else {
                previewPayload.addProperty("sourceGrantId", session.service().grantAssetImportSource(source).id());
                if (kind.equals("batch")) {
                    JsonArray items = new JsonArray(); items.add(previewPayload); previewPayload = new JsonObject(); previewPayload.add("items", items);
                    previewOperation = Operation.PREVIEW_ASSET_IMPORT_BATCH; applyOperation = Operation.IMPORT_ASSET_BATCH;
                }
            }
            var preview = entry.query(Query.of(UUID.randomUUID(), session.workspaceId(), previewOperation, previewPayload));
            assertEquals("succeeded", preview.status(), preview.diagnostics().toString());
            JsonObject apply = new JsonObject(); apply.add("planToken", preview.data().getAsJsonObject().get("planToken"));
            var imported = entry.execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, applyOperation, apply)).result();
            assertEquals("committed", imported.status(), imported.diagnostics().toString());
            long durable = json(Files.readString(root.resolve("review_probe.mcreator"))).getAsJsonObject("dev.copperbench").get("revision").getAsLong();
            var next = entry.execute(Command.of(UUID.randomUUID(), session.workspaceId(), imported.newRevision(), Operation.CREATE_REGISTRY_ENTRY,
                    json("{\"registry\":\"variables\",\"entry\":{\"name\":\"counter\",\"dataType\":\"number\",\"scope\":\"global\",\"value\":0}}"))).result();
            assertAll(() -> assertEquals(imported.newRevision(), durable), () -> assertEquals("committed", next.status()));
        }
        try (Workspace reopened = Workspace.readFromFS(root.resolve("review_probe.mcreator").toFile(), null)) {
            assertEquals(2, reopened.getFileManager().loadOrCreateProductMetadata(UUID::randomUUID).revision());
        }
    }

    @Test void externalAssetEditsPersistTheirRevisionBeforeTheNextStructuredEdit() throws Exception {
        Path root = temp.resolve("workspace");
        try (Workspace workspace = create(root);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            Path file = root.resolve("models/lamp.bbmodel"); Files.createDirectories(file.getParent()); Files.writeString(file, "{}");
            var assets = new AssetWorkspaceService(root);
            var lifecycle = session.service().blockbenchEditLifecycle(session.workspaceId());
            var prepared = lifecycle.prepare(assets.findByRelativePath("models/lamp.bbmodel").orElseThrow());
            Files.writeString(file, "{\"edited\":true}");
            var completion = lifecycle.complete(prepared, assets.findByRelativePath("models/lamp.bbmodel").orElseThrow());
            assertEquals(1, completion.workspaceRevision());
            assertEquals(1, workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId()).revision());
            var next = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.CREATE_REGISTRY_ENTRY,
                    json("{\"registry\":\"variables\",\"entry\":{\"name\":\"counter\",\"dataType\":\"number\",\"scope\":\"global\",\"value\":0}}"))).result();
            assertEquals("committed", next.status(), next.diagnostics().toString());
            assertEquals("{\"edited\":true}", Files.readString(file));
        }
    }

    @Test void failedRegistryPersistenceMustRestoreChangedElementDefinitions() throws Exception {
        Path root = temp.resolve("workspace");
        try (Workspace workspace = create(root);
             var session = MCreatorWorkspaceSession.attach(workspace,
                     store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT,
                    json("{\"elementType\":\"function\",\"name\":\"probe_function\",\"initialValues\":{\"code\":\"say before\",\"namespace\":\"mod\"}}"))).result();
            assertEquals("committed", created.status(), created.diagnostics().toString());
            var metadata = workspace.getFileManager().loadOrCreateProductMetadata(session.workspaceId());
            WorkspaceState before = new MCreatorWorkspaceStateMapper().map(workspace, metadata);
            WorkspaceState after = before.copy();
            var old = before.elements().getFirst(); JsonObject values = old.values().deepCopy();
            values.remove("commands"); values.remove("fields"); values.addProperty("code", "say after");
            after.replaceElement(new WorkspaceState.Element(old.id(), old.type(), old.name(), old.displayName(), old.state(), old.ownership(), Instant.now(), values));
            Path definition = root.resolve("elements/probe_function.mod.json"); byte[] original = Files.readAllBytes(definition);
            // Inject a late durable revision conflict; all definition writes happen before this check.
            workspace.getFileManager().synchronizeProductRevision(session.workspaceId(), metadata.revision() + 1);
            var gateway = new MCreatorWorkspaceMutationGateway(workspace, session.workspaceId());
            Exception failure = assertThrows(Exception.class, () -> gateway.persistWorkspaceData(before, after, Operation.RENAME_REGISTRY_ENTRY));
            assertInstanceOf(ProductMetadataManager.RevisionConflictException.class, failure);
            assertArrayEquals(original, Files.readAllBytes(definition), "Rejected registry transaction must restore each changed definition");
        }
    }
}
