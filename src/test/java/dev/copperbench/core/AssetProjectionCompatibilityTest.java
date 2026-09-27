package dev.copperbench.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Public-query contracts run against the original facade before extracting asset projections. */
class AssetProjectionCompatibilityTest {
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"), ZoneOffset.UTC);
    private static final Gson JSON = new Gson();
    @TempDir Path root;

    private WorkspaceApplicationService service(Path workspaceRoot) {
        var generator = new JsonObject();
        generator.addProperty("id", "fabric-1.21.1");
        var store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(ID, "Asset compatibility", "mod", 7, false, generator,
                new JsonObject(), List.of()));
        return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(CLOCK, UUID::randomUUID),
                WorkspaceMutationGateway.noOp(), null, null, ignored -> workspaceRoot, CLOCK, UUID::randomUUID);
    }

    private QueryResult query(WorkspaceApplicationService service, Operation operation, Actor actor) {
        return service.query(Query.of(ID, ID, operation, new JsonObject()), new RequestContext(actor, PermissionProfile.READ_ONLY));
    }

    @Test void assetsAndHealthAreIdenticalAcrossUiMcpAndHeadlessAndKeepSnapshotSemantics() throws Exception {
        Path model = root.resolve("assets/test/models/block/lamp.json");
        Path texture = root.resolve("assets/test/textures/block/lamp.png");
        Files.createDirectories(model.getParent());
        Files.createDirectories(texture.getParent());
        Files.writeString(model, "{\"textures\":{\"all\":\"test:block/lamp\",\"missing\":\"test:block/missing\"}}");
        Files.write(texture, new byte[]{1, 2, 3});
        Files.write(texture.resolveSibling("lamp_copy.png"), new byte[]{1, 2, 3});
        var service = service(root);
        var assets = query(service, Operation.LIST_ASSETS, Actor.UI);
        var health = query(service, Operation.GET_WORKSPACE_HEALTH, Actor.UI);
        assertEquals("succeeded", assets.status());
        assertEquals(7, assets.revision());
        for (Actor actor : List.of(Actor.MCP, Actor.HEADLESS)) {
            assertEquals(JSON.toJson(assets), JSON.toJson(query(service, Operation.LIST_ASSETS, actor)));
            assertEquals(JSON.toJson(health), JSON.toJson(query(service, Operation.GET_WORKSPACE_HEALTH, actor)));
        }
        var projection = assets.data().getAsJsonObject();
        assertEquals(List.of("schemaVersion", "assets", "references", "diagnostics", "health"),
                List.copyOf(projection.keySet()));
        assertEquals(3, projection.getAsJsonArray("assets").size());
        assertEquals("assets/test/models/block/lamp.json",
                projection.getAsJsonArray("assets").get(0).getAsJsonObject().get("relativePath").getAsString());
        assertEquals(2, projection.getAsJsonObject("health").get("duplicateAssets").getAsInt());
        assertEquals(1, projection.getAsJsonObject("health").get("missingReferences").getAsInt());
        assertEquals(JSON.toJsonTree(assets.diagnostics()), projection.get("diagnostics"));
        assertEquals(projection.get("health"), health.data().getAsJsonObject().getAsJsonObject("assets").get("summary"));

        // Freeze the existing UUID input, including JSON ordering, rather than defining a new fingerprint.
        var graph = new AssetWorkspaceService(root).referenceGraph();
        var summary = health.data().getAsJsonObject().getAsJsonObject("diagnostics").deepCopy();
        var snapshotId = summary.remove("snapshotId").getAsString();
        String expected = UUID.nameUUIDFromBytes(("7\n" + JSON.toJson(graph.assets()) + JSON.toJson(graph.references())
                + "\n" + summary).getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(expected, snapshotId);
        Files.write(texture, new byte[]{4, 5, 6});
        var changed = query(service, Operation.GET_WORKSPACE_HEALTH, Actor.UI);
        assertEquals(7, changed.revision());
        assertNotEquals(snapshotId, changed.data().getAsJsonObject().getAsJsonObject("diagnostics").get("snapshotId").getAsString());
    }

    @Test void rootAndIndexFailuresRemainDifferentAndHealthStaysPartial() {
        for (Path workspaceRoot : new Path[]{null, root.resolve("not-created")}) {
            var service = service(workspaceRoot);
            String code = workspaceRoot == null ? "ASSET_WORKSPACE_ROOT_UNAVAILABLE" : "ASSET_QUERY_FAILED";
            var assets = query(service, Operation.LIST_ASSETS, Actor.UI);
            assertEquals("rejected", assets.status());
            assertEquals(7, assets.revision());
            assertTrue(assets.data().isJsonNull());
            assertEquals(code, assets.diagnostics().getFirst().code());
            var health = query(service, Operation.GET_WORKSPACE_HEALTH, Actor.UI);
            assertEquals("succeeded", health.status());
            var data = health.data().getAsJsonObject();
            assertFalse(data.getAsJsonObject("assets").get("indexed").getAsBoolean());
            assertEquals(code, data.getAsJsonObject("assets").get("reasonCode").getAsString());
            assertEquals("partial", data.getAsJsonObject("diagnostics").get("collectionState").getAsString());
        }
    }
}
