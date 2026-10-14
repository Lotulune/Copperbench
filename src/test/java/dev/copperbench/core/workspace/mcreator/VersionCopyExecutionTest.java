package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.workspace.*;
import dev.copperbench.migration.VersionCopyUpgradeExecution;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionCopyExecutionTest {
    @TempDir Path root;
    static final String CODE = "# preserved 中文\nscoreboard objectives add camp_count dummy\nscoreboard players set camp_probe camp_count 7\nsetblock 2 65 2 minecraft:gold_block\n";
    @BeforeAll static void init() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    JsonObject preview(String output, String target) { var r = json("{\"operation\":\"preview\",\"supportedVersions\":[\"2\"],\"targetGeneratorId\":\"fabric-26.3\"}"); r.addProperty("targetGeneratorId", target); r.addProperty("outputName", output); return r; }
    JsonObject execute(JsonObject plan) {
        var r = preview(plan.get("outputName").getAsString(), plan.get("targetGeneratorId").getAsString()); r.addProperty("operation", "execute");
        r.add("planId", plan.get("planId")); r.add("expectedRevision", plan.get("sourceRevision")); r.addProperty("userApproved", true); return r;
    }
    void rejected(JsonObject result, String code) {
        assertEquals("rejected", result.get("status").getAsString(), result.toString());
        assertEquals(code, result.getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString(), result.toString());
    }
    final class Fixture implements AutoCloseable {
        final Workspace workspace; final MCreatorWorkspaceSession session; final Path source = root.resolve("source");
        final MCreatorWorkspaceMutationGateway gateway; final String target;
        Fixture(String loader) throws Exception {
            target = loader + "-26.3";
            var settings = new WorkspaceSettings("upgrade_source"); settings.setModName("Upgrade Source"); settings.setVersion("1.0.0");
            settings.setCurrentGenerator(loader + "-26.2"); settings.setModElementsPackage("dev.copperbench.upgradesource");
            workspace = Workspace.createWorkspace(source.resolve("source.mcreator").toFile(), settings);
            session = MCreatorWorkspaceSession.attach(workspace, store -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID);
            var request = json("{\"elementType\":\"function\",\"name\":\"camp_signal\",\"initialValues\":{\"namespace\":\"mod\"}}");
            request.getAsJsonObject("initialValues").addProperty("code", CODE);
            var created = session.uiEntry().execute(UiCore.Command.of(UUID.randomUUID(), session.workspaceId(), 0, UiCore.Operation.CREATE_MOD_ELEMENT, request));
            assertEquals("committed", created.result().status(), created.toString());
            assertTrue(workspace.getGenerator().generateBase());
            assertTrue(workspace.getGenerator().generateElement(workspace.getModElementByName("camp_signal").getGeneratableElement()));
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            Path definition = source.resolve("elements/camp_signal.mod.json"); var raw = json(Files.readString(definition));
            raw.addProperty("unknownEnvelope", "retain"); raw.getAsJsonObject("definition").add("futureFunctionField", json("{\"keep\":[1,2,3]}"));
            Files.writeString(definition, raw.toString());
            Files.createDirectories(source.resolve("run/saves/Original World")); Files.write(source.resolve("run/saves/Original World/level.dat"), new byte[]{0,1,2,3});
            gateway = new MCreatorWorkspaceMutationGateway(workspace, session.workspaceId());
        }
        JsonObject previewResult(String name) { return session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), session.workspaceId(), preview(name, target)); }
        JsonObject plan(String name) { var r = previewResult(name); assertEquals("succeeded", r.get("status").getAsString(), r.toString()); return r.getAsJsonObject("plan"); }
        void optIn(String name) {
            var r = json("{\"operation\":\"opt_in\",\"supportedVersions\":[\"1\"],\"generatorId\":\"fabric-26.3\",\"expectedRevision\":1}");
            r.addProperty("generatorId", target); r.addProperty("workspaceFolderPath", root.resolve(name).toString());
            var response = session.uiEntry().preview(UUID.randomUUID(), session.workspaceId(), r);
            assertEquals("succeeded", response.get("status").getAsString(), response.toString());
        }
        @Override public void close() { session.close(); workspace.close(); }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void baselinePreviewIsRepeatableAndReadOnly(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            String before = VersionCopyUpgradeExecution.fingerprint(f.source); var plan = f.plan("copy");
            assertTrue(plan.get("executionAvailable").getAsBoolean(), plan.toString());
            assertEquals(loader + "-" + VersionCopyUpgradeExecution.RULE_VERSION, plan.get("ruleVersion").getAsString());
            assertEquals(plan, f.plan("copy")); assertEquals(0, plan.getAsJsonObject("counts").get("manual").getAsInt());
            assertTrue(plan.toString().contains("futureFunctionField")); assertTrue(plan.toString().contains("RUNTIME_EXCLUDED"));
            assertEquals(before, VersionCopyUpgradeExecution.fingerprint(f.source)); assertFalse(Files.exists(root.resolve("copy")));
            Path evidence = Path.of("output/p263-26/core-ui"); Files.createDirectories(evidence);
            Files.writeString(evidence.resolve(loader + "-executable-preview.json"), UiCore.wireGson().toJson(f.previewResult("copy")));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void targetInvalidFunctionNamesRemainManualAndNeverCreateACopy(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            Path definition = f.source.resolve("elements/camp_signal.mod.json");
            var raw = json(Files.readString(definition));
            f.optIn("copy");
            for (String name : List.of("folder/signal", "_signal", "1signal", "a".repeat(65), "", "<missing>")) {
                if (name.equals("<missing>")) raw.getAsJsonObject("definition").remove("name");
                else raw.getAsJsonObject("definition").addProperty("name", name);
                Files.writeString(definition, raw.toString());
                String before = VersionCopyUpgradeExecution.fingerprint(f.source);
                var plan = f.plan("copy");
                assertFalse(plan.get("executionAvailable").getAsBoolean(), name);
                assertTrue(plan.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject)
                        .anyMatch(entry -> entry.get("reasonCode").getAsString().equals("FUNCTION_RULE_UNPROVEN")
                                && entry.get("disposition").getAsString().equals("manual")), plan.toString());
                rejected(f.session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), execute(plan)), "UPGRADE_MANUAL_TASKS_REQUIRED");
                assertEquals(before, VersionCopyUpgradeExecution.fingerprint(f.source));
                assertFalse(Files.exists(root.resolve("copy")));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void permissionChoiceAuthorityAndRevisionCannotBeForged(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            var request = execute(f.plan("copy")); String before = VersionCopyUpgradeExecution.fingerprint(f.source);
            rejected(f.session.headlessEntry(UiCore.PermissionProfile.READ_ONLY).versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "PERMISSION_DENIED");
            rejected(f.session.mcpEntry(UiCore.PermissionProfile.READ_ONLY).versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "PERMISSION_DENIED");
            rejected(f.session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "PREVIEW_OPT_IN_REQUIRED");
            f.optIn("copy");
            rejected(f.session.headlessEntry(UiCore.PermissionProfile.FULL_ACCESS).versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "TASK_AUTHORIZATION_REQUIRED");
            rejected(f.session.mcpEntry(UiCore.PermissionProfile.WORKSPACE).versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "TASK_AUTHORIZATION_REQUIRED");
            request.addProperty("userApproved", false);
            rejected(f.session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "USER_APPROVAL_REQUIRED");
            request.addProperty("userApproved", true); request.addProperty("expectedRevision", 0);
            rejected(f.session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "WORKSPACE_REVISION_CONFLICT");
            assertEquals(before, VersionCopyUpgradeExecution.fingerprint(f.source)); assertFalse(Files.exists(root.resolve("copy")));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void staleSourceExistingDestinationAndTraversalAreRejectedBeforeWriting(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            var request = execute(f.plan("copy")); f.optIn("copy");
            Files.writeString(f.source.resolve("run/saves/Original World/new.dat"), "external world change");
            rejected(f.session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), request), "UPGRADE_PLAN_STALE");
            assertFalse(Files.exists(root.resolve("copy")));
            Files.createDirectory(root.resolve("copy")); rejected(f.previewResult("copy"), "UPGRADE_TARGET_EXISTS");
            for (String name : List.of("../escaped", "source", "C:\\escape", "CON", "trailing."))
                rejected(f.previewResult(name), "UPGRADE_TARGET_INVALID");
        }
    }
    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void alteredGeneratedCodeAndUnprovenCommandsRemainManualTasks(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            var baseline = f.plan("copy"); assertTrue(baseline.get("executionAvailable").getAsBoolean(), baseline.toString());
            Path javaFile;
            try (var paths = Files.walk(f.source.resolve("src/main/java"))) { javaFile = paths.filter(p -> p.toString().endsWith(".java")).findFirst().orElseThrow(); }
            Files.writeString(javaFile, Files.readString(javaFile) + "\n// manual addition\n");
            var changed = f.plan("copy"); assertFalse(changed.get("executionAvailable").getAsBoolean()); assertTrue(changed.toString().contains("FILE_RULE_UNPROVEN"));
            f.optIn("copy"); rejected(f.session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), execute(changed)), "UPGRADE_MANUAL_TASKS_REQUIRED");
            Path definition = f.source.resolve("elements/camp_signal.mod.json"); var raw = json(Files.readString(definition));
            raw.getAsJsonObject("definition").addProperty("code", "execute as @e run kill @s\n"); Files.writeString(definition, raw.toString());
            assertTrue(f.plan("copy").toString().contains("FUNCTION_RULE_UNPROVEN")); assertFalse(Files.exists(root.resolve("copy")));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void failedTargetHasNoSuccessMarkerAndLeavesSourceAndWorldBytesUntouched(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            var metadata = new ProductMetadataManager.Metadata(1, f.session.workspaceId(), 1);
            var state = new MCreatorWorkspaceStateMapper().map(f.workspace, metadata);
            WorkspaceMutationGateway failureBackend = new WorkspaceMutationGateway() {
                public void persist(WorkspaceState a, WorkspaceState b, UiCore.Operation o, WorkspaceState.Element e) {}
                public JsonObject elementIdentity(WorkspaceState.Element e) { return f.gateway.elementIdentity(e); }
                public Map<String, byte[]> versionCopyBaseline(JsonObject d, Map<String, JsonObject> defs) throws Exception { return f.gateway.versionCopyBaseline(d, defs); }
                public void generateVersionCopy(Path path) throws Exception { Files.writeString(path.getParent().resolve("partial-build.txt"), "interrupted"); throw new java.io.IOException("Injected target generation failure"); }
            };
            var service = new VersionCopyUpgradeExecution(failureBackend); String before = VersionCopyUpgradeExecution.fingerprint(f.source);
            var inspection = service.inspect(state, f.source, f.target, "failed-copy");
            assertTrue(inspection.plan().get("executionAvailable").getAsBoolean(), inspection.plan().toString());
            var receipt = service.execute(f.source, inspection);
            assertEquals("failed", receipt.get("status").getAsString()); assertFalse(receipt.get("conversionComplete").getAsBoolean());
            assertEquals(receipt, json(Files.readString(root.resolve("failed-copy/version-copy-upgrade-result.json"))));
            assertFalse(Files.exists(root.resolve("failed-copy/run"))); assertEquals(before, VersionCopyUpgradeExecution.fingerprint(f.source));
            assertArrayEquals(new byte[]{0,1,2,3}, Files.readAllBytes(f.source.resolve("run/saves/Original World/level.dat")));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void activeSourceTasksPreventCopyPlanning(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            var started = f.session.uiEntry().execute(UiCore.Command.of(UUID.randomUUID(), f.session.workspaceId(), 1,
                    UiCore.Operation.BUILD_WORKSPACE, new JsonObject()));
            assertEquals("accepted", started.result().status(), started.toString());
            rejected(f.previewResult("copy"), "UPGRADE_SOURCE_BUSY"); assertFalse(Files.exists(root.resolve("copy")));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void silentTargetMetadataDamageIsAFailure(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            var state = new MCreatorWorkspaceStateMapper().map(f.workspace, new ProductMetadataManager.Metadata(1, f.session.workspaceId(), 1));
            WorkspaceMutationGateway corruptBackend = new WorkspaceMutationGateway() {
                public void persist(WorkspaceState a, WorkspaceState b, UiCore.Operation o, WorkspaceState.Element e) {}
                public JsonObject elementIdentity(WorkspaceState.Element e) { return f.gateway.elementIdentity(e); }
                public Map<String, byte[]> versionCopyBaseline(JsonObject d, Map<String, JsonObject> defs) throws Exception { return f.gateway.versionCopyBaseline(d, defs); }
                public void generateVersionCopy(Path path) throws Exception {
                    var doc = json(Files.readString(path)); doc.getAsJsonArray("mod_elements").get(0).getAsJsonObject().addProperty("registry_name", "wrong_name");
                    Files.writeString(path, doc.toString());
                }
            };
            var service = new VersionCopyUpgradeExecution(corruptBackend); String before = VersionCopyUpgradeExecution.fingerprint(f.source);
            var receipt = service.execute(f.source, service.inspect(state, f.source, f.target, "damaged-copy"));
            assertEquals("failed", receipt.get("status").getAsString()); assertFalse(receipt.get("conversionComplete").getAsBoolean());
            assertTrue(receipt.get("detail").getAsString().contains("preserved identity"));
            assertEquals(before, VersionCopyUpgradeExecution.fingerprint(f.source));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void missingOwnershipAndDivergedEditorFieldsCannotBeCertified(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            Path workspaceFile = f.source.resolve("source.mcreator"); var doc = json(Files.readString(workspaceFile));
            doc.getAsJsonObject("metadata").remove("files"); Files.writeString(workspaceFile, doc.toString());
            var missing = f.plan("copy"); assertFalse(missing.get("executionAvailable").getAsBoolean());
            assertTrue(missing.toString().contains("FILE_RULE_UNPROVEN"));
            Path file = f.source.resolve("elements/camp_signal.mod.json"); var raw = json(Files.readString(file));
            raw.getAsJsonObject("definition").addProperty("code", CODE.replace("camp_count 7", "camp_count 8")); Files.writeString(file, raw.toString());
            var diverged = f.plan("copy"); assertFalse(diverged.get("executionAvailable").getAsBoolean());
            assertTrue(diverged.toString().contains("SOURCE_FIELDS_DIVERGED")); assertFalse(Files.exists(root.resolve("copy")));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "neoforge"})
    void versionTwoStillRejectsCrossLoaderAndOtherVersions(String loader) throws Exception {
        try (var f = new Fixture(loader)) {
            for (String wrong : List.of((loader.equals("fabric") ? "neoforge" : "fabric") + "-26.3", loader + "-1.21.1")) {
                rejected(f.session.uiEntry().versionCopyUpgrade(UUID.randomUUID(), f.session.workspaceId(), preview("copy", wrong)), "UPGRADE_RULE_UNAVAILABLE");
                assertFalse(Files.exists(root.resolve("copy")));
            }
        }
    }

    @Test void sourceIdeProfilesStayWithSourceAndCustomProfilesRequireReview() throws Exception {
        try (var f = new Fixture("neoforge")) {
            Path ide = f.source.resolve(".eclipse/configurations"); Files.createDirectories(ide);
            Files.writeString(ide.resolve("Client.launch"), "<launchConfiguration source='original/run'/>");
            Files.writeString(ide.resolve("Server.launch"), "<launchConfiguration source='original/run'/>");
            String before = VersionCopyUpgradeExecution.fingerprint(f.source);
            var supported = f.plan("copy"); assertTrue(supported.get("executionAvailable").getAsBoolean(), supported.toString());
            assertEquals(2, supported.getAsJsonArray("entries").asList().stream().filter(e -> "SOURCE_LAUNCH_PROFILE_EXCLUDED".equals(e.getAsJsonObject().get("reasonCode").getAsString())).count());
            assertEquals(before, VersionCopyUpgradeExecution.fingerprint(f.source));
            Files.writeString(ide.resolve("Personal.launch"), "<launchConfiguration custom='true'/>");
            var manual = f.plan("copy"); assertFalse(manual.get("executionAvailable").getAsBoolean());
            assertTrue(manual.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject).anyMatch(e -> e.get("path").getAsString().endsWith("Personal.launch") && e.get("disposition").getAsString().equals("manual")));
            assertFalse(Files.exists(root.resolve("copy")));
        }
    }
}
