package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.bridge.JcefBridgeEndpoint;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.headless.NativeApiSession;
import dev.copperbench.migration.*;
import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.tracks.VersionTrackCatalog;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionCopyUpgradePreviewTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static JsonObject request(String target) { var r = json("{\"operation\":\"preview\",\"supportedVersions\":[\"1\"]}"); r.addProperty("targetGeneratorId", target); return r; }
    private static JsonObject nativeRequest(MCreatorWorkspaceSession session, JsonObject request) throws Exception {
        var input = new JsonObject(); input.addProperty("id", UUID.randomUUID().toString()); input.addProperty("kind", "version_copy_upgrade");
        var payload = request.deepCopy(); input.add("operation", payload.remove("operation")); input.add("payload", payload);
        var output = new StringWriter();
        var api = new NativeApiSession(session.headlessEntry(UiCore.PermissionProfile.READ_ONLY), session.workspaceId());
        assertEquals(0, api.serve(new StringReader(input + "\n"), new PrintWriter(output)));
        return json(output.toString().lines().skip(1).findFirst().orElseThrow()).getAsJsonObject("result");
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void nativeMcpAndUiShareTheReadOnlyPlanAndDurableUnknownValues(String loader) throws Exception {
        var settings = new WorkspaceSettings("upgrade_source"); settings.setModName("Upgrade Source"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(loader + "-26.2");
        try (var workspace = Workspace.createWorkspace(root.resolve("source.mcreator").toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            UUID id = session.workspaceId();
            var created = session.uiEntry().execute(UiCore.Command.of(UUID.randomUUID(), id, 0, UiCore.Operation.CREATE_MOD_ELEMENT,
                    json("{\"elementType\":\"item\",\"name\":\"trail_kit\",\"initialValues\":{\"stackSize\":16}}"))).result();
            assertEquals("committed", created.status(), created.toString());
            var code = session.uiEntry().execute(UiCore.Command.of(UUID.randomUUID(), id, 1, UiCore.Operation.CREATE_MOD_ELEMENT,
                    json("{\"elementType\":\"code\",\"name\":\"manual_probe\",\"initialValues\":{\"code\":\"// manual 中文\\r\\n\"}}"))).result();
            assertEquals("committed", code.status(), code.toString());
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            var manualPaths = workspace.getModElementByName("manual_probe").getAssociatedFiles().stream()
                    .filter(f -> f.getName().endsWith(".java")).toList();
            assertEquals(1, manualPaths.size());
            Files.writeString(manualPaths.getFirst().toPath(), "// External manual source 中文\r\n");
            Path definition = root.resolve("elements/trail_kit.mod.json");
            var raw = json(Files.readString(definition)); raw.add("plugin.example", json("{\"future\":[1,\"中文\"]}"));
            raw.getAsJsonObject("definition").addProperty("unknownUpgradeField", "retain-exactly"); Files.writeString(definition, raw.toString());
            Files.createDirectories(root.resolve("run/saves/Source World")); Files.write(root.resolve("run/saves/Source World/level.dat"), new byte[]{1,2,3,4});
            String before = WorkspaceTreeHasher.hash(root);
            var query = request(loader + "-26.3"); var nativeResult = nativeRequest(session, query);
            assertEquals("succeeded", nativeResult.get("status").getAsString(), nativeResult.toString());
            var plan = nativeResult.getAsJsonObject("plan");
            assertEquals(before, plan.get("sourceFingerprint").getAsString()); assertFalse(plan.get("executionAvailable").getAsBoolean());
            assertTrue(plan.toString().contains("retain-exactly")); assertTrue(plan.toString().contains("plugin.example"));
            assertTrue(plan.toString().contains("manual_probe")); assertTrue(plan.toString().contains("manual"));
            assertTrue(plan.toString().contains("External manual source"));
            assertTrue(plan.toString().contains("FIELD_RULE_UNPROVEN")); assertTrue(plan.toString().contains("FILE_SEMANTICS_UNPROVEN"));
            assertEquals(plan, nativeRequest(session, query).get("plan"));
            assertEquals(plan, session.mcpEntry(UiCore.PermissionProfile.READ_ONLY).versionCopyUpgrade(UUID.randomUUID(), id, query).get("plan"));
            JsonObject envelope = new JsonObject(); envelope.addProperty("messageType", "version_copy_upgrade"); envelope.addProperty("requestId", UUID.randomUUID().toString());
            envelope.addProperty("workspaceId", id.toString()); envelope.add("request", query);
            assertEquals(plan, json(new JcefBridgeEndpoint(session.uiEntry(), ignored -> fail("Preview published an event")).handle(envelope.toString())).get("plan"));
            assertEquals(2, nativeResult.get("revision").getAsInt());
            assertEquals(before, WorkspaceTreeHasher.hash(root));
            assertArrayEquals(new byte[]{1,2,3,4}, Files.readAllBytes(root.resolve("run/saves/Source World/level.dat")));
            assertFalse(Files.exists(root.resolveSibling("upgraded-copy")));
            Path evidence = Path.of("output/p263-24/core-ui"); Files.createDirectories(evidence);
            Files.writeString(evidence.resolve(loader + "-preview.json"), UiCore.wireGson().toJson(nativeResult));
            // An external definition change is shown from its actual bytes, even before reopening the model.
            raw.getAsJsonObject("definition").addProperty("stackSize", 11); Files.writeString(definition, raw.toString());
            String edited = WorkspaceTreeHasher.hash(root); var next = nativeRequest(session, query).getAsJsonObject("plan");
            assertNotEquals(plan.get("planId"), next.get("planId")); assertEquals(edited, next.get("sourceFingerprint").getAsString());
            assertTrue(next.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject)
                    .anyMatch(e -> e.get("path").getAsString().endsWith("/stackSize") && e.get("sourceValue").getAsInt() == 11));
            assertEquals(edited, WorkspaceTreeHasher.hash(root));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void crossLoaderMalformedAndWriteRequestsFailWithoutCreatingATarget(String loader) throws Exception {
        var settings = new WorkspaceSettings("upgrade_boundary"); settings.setModName("Upgrade Boundary"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(loader + "-26.2");
        try (var workspace = Workspace.createWorkspace(root.resolve("source.mcreator").toFile(), settings);
             var session = MCreatorWorkspaceSession.attach(workspace, store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            workspace.getFileManager().saveWorkspaceDirectlyAndWait(); String before = WorkspaceTreeHasher.hash(root);
            for (var bad : List.of(request((loader.equals("fabric") ? "neoforge" : "fabric") + "-26.3"),
                    request(loader + "-1.21.1"), json("{\"operation\":\"preview\",\"supportedVersions\":[\"99\"]}"),
                    json("{\"operation\":\"execute\",\"supportedVersions\":[\"1\"]}"),
                    json("{\"operation\":\"preview\",\"supportedVersions\":[\"1\"],\"targetGeneratorId\":\"" + loader + "-26.3\",\"targetDirectory\":\"../copy\"}"))) {
                var rejected = nativeRequest(session, bad); assertEquals("rejected", rejected.get("status").getAsString(), rejected.toString());
                assertFalse(rejected.has("plan")); assertEquals(before, WorkspaceTreeHasher.hash(root));
            }
            assertFalse(VersionTrackCatalog.official().migratable(loader + "-26.2", loader + "-26.3"));
        }
    }
}
