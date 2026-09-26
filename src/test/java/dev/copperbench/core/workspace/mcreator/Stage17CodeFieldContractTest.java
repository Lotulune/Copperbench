package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage17CodeFieldContractTest {
    @TempDir Path root;
    @BeforeAll static void initialize() throws Exception { McreatorTestRuntime.ensureInitialized(); }
    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void compatibleBodyUpdatesWriteExactFilesAndPreserveExternalEdits(String generator) throws Exception {
        verifyCompatibleWrites(generator, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
            "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2"})
    void coldWorkspaceMustPersistCodeWithoutASeparateGenerationStep(String generator) throws Exception {
        verifyCompatibleWrites(generator, false);
    }

    private void verifyCompatibleWrites(String generator, boolean prepareBase) throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("code_contract");
        settings.setModName("Code Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(generator);
        Path document = root.resolve("code_contract.mcreator"); UUID workspaceId = UUID.randomUUID();
        String initial = "package net.mcreator.code_contract;\npublic class code_probe { public static final int VALUE = 1; }\n";
        String updated = initial.replace("= 1", "= 2"), external = initial.replace("= 1", "= 3");
        Path primary; String id;
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
            if (prepareBase) assertTrue(workspace.getGenerator().generateBase());
            else assertFalse(workspace.getGenerator().getSourceRoot().isDirectory());
            Files.writeString(root.resolve("build.gradle"), "// No Gradle invocation in this contract test\n");
            Files.createDirectories(root.resolve("gradle/wrapper"));
            Files.write(root.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[0]);
            assertTrue(MCreatorGenerationPreparation.deferred(workspace));
            try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                    new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                JsonObject fields = new JsonObject(); fields.addProperty("code", initial); fields.add("sourceFingerprints", new JsonObject());
                fields.add("codeFiles", JsonParser.parseString("[{\"path\":\"parts/Helper.java\",\"code\":\"package net.mcreator.code_contract.parts; public class Helper {}\\n\"}]"));
                JsonObject values = new JsonObject(); values.add("fields", fields);
                JsonObject create = new JsonObject(); create.addProperty("elementType", "code"); create.addProperty("name", "code_probe"); create.add("initialValues", values);
                var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, create)).result();
                assertEquals("committed", created.status(), created.diagnostics().toString());
                id = created.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
                var element = workspace.getModElementByName("code_probe"); assertTrue(element.isCodeLocked());
                primary = element.getAssociatedFiles().stream().map(java.io.File::toPath).filter(p -> p.getFileName().toString().equals("code_probe.java")).findFirst().orElseThrow();
                Path helper = primary.getParent().resolve("parts/Helper.java");
                assertEquals(initial, Files.readString(primary)); byte[] helperBefore = Files.readAllBytes(helper);
                JsonObject update = new JsonObject(); update.addProperty("elementId", id);
                update.add("changes", JsonParser.parseString("[{\"path\":\"/fields/codeFiles/0/code\",\"value\":true}]"));
                var rejected = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
                assertEquals("rejected", rejected.status()); assertEquals("FIELD_TYPE_INVALID", rejected.diagnostics().getFirst().code());
                assertEquals(1, rejected.newRevision()); assertEquals(initial, Files.readString(primary)); assertArrayEquals(helperBefore, Files.readAllBytes(helper));
                JsonObject operation = new JsonObject(); operation.addProperty("operation", "update_mod_element"); operation.add("payload", update);
                JsonArray operations = new JsonArray(); operations.add(operation); JsonObject plan = new JsonObject(); plan.add("operations", operations);
                plan.addProperty("expectedRevision", 1); plan.addProperty("idempotencyKey", "invalid-code-body");
                var planned = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.PLAN_WORKSPACE_CHANGES, plan));
                assertEquals("failed", planned.status()); assertTrue(planned.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_TYPE_INVALID")));
                assertArrayEquals(helperBefore, Files.readAllBytes(helper));
                JsonObject change = new JsonObject(); change.addProperty("path", "/fields/code"); change.addProperty("value", updated);
                JsonArray changes = new JsonArray(); changes.add(change); update.add("changes", changes);
                var accepted = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 1, Operation.UPDATE_MOD_ELEMENT, update)).result();
                assertEquals("committed", accepted.status(), accepted.diagnostics().toString()); assertEquals(updated, Files.readString(primary));
                assertArrayEquals(helperBefore, Files.readAllBytes(helper));
                JsonObject pending = net.mcreator.workspace.WorkspaceFileManager.gson.toJsonTree(
                        workspace.getMetadata(MCreatorGenerationPreparation.PENDING)).getAsJsonObject();
                String relative = root.relativize(primary).toString().replace('\\', '/');
                assertFalse(pending.getAsJsonObject("files").has(relative), "Manual source must not enter generation ownership");
                // Historical packages recorded Code files here; reopening must respect current manual ownership.
                pending.getAsJsonObject("files").addProperty(relative, "stale-generated-fingerprint");
                workspace.putMetadata(MCreatorGenerationPreparation.PENDING,
                        net.mcreator.workspace.WorkspaceFileManager.gson.fromJson(pending, Object.class));
                workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            }
        }
        Files.writeString(primary, external);
        try (Workspace workspace = Workspace.readFromFS(document.toFile(), null);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, workspaceId,
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            JsonObject update = new JsonObject(); update.addProperty("elementId", id);
            update.add("changes", JsonParser.parseString("[{\"path\":\"/description\",\"value\":\"After external edit\"}]"));
            var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 2, Operation.UPDATE_MOD_ELEMENT, update)).result();
            assertEquals("committed", result.status(), result.diagnostics().toString()); assertEquals(external, Files.readString(primary));
        }
    }
    @Test void codeTextCannotBeCoercedOrReplacedWithAnEmptyBody() throws Exception {
        WorkspaceSettings settings = new WorkspaceSettings("code_contract");
        settings.setModName("Code Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("code_contract.mcreator").toFile(), settings);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                     new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
            for (String[] input : new String[][] {
                    {"{\"code\":true}", "FIELD_TYPE_INVALID", "/code"},
                    {"{\"code\":null}", "FIELD_TYPE_INVALID", "/code"},
                    {"{\"codeFiles\":[{\"path\":\"Helper.java\",\"code\":42}]}", "FIELD_TYPE_INVALID", "/codeFiles/0/code"},
                    {"{\"codeFiles\":[{\"path\":\"Helper.java\",\"code\":\"\",\"enabled\":false}]}", "FIELD_UNSUPPORTED", "/codeFiles/0/enabled"},
                    {"{\"fields\":{\"codeFiles\":false}}", "CODE_BUNDLE_INVALID", "/fields/codeFiles"},
                    {"{\"sourceFingerprints\":true}", "FIELD_TYPE_INVALID", "/sourceFingerprints"},
                    {"{\"sourceFingerprints\":{\"$primary\":42}}", "FIELD_TYPE_INVALID", "/sourceFingerprints/$primary"}
            }) {
                JsonObject payload = new JsonObject(); payload.addProperty("elementType", "code"); payload.addProperty("name", "invalid_input");
                payload.add("initialValues", JsonParser.parseString(input[0]));
                var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, payload)).result();
                assertEquals("rejected", result.status(), input[0]);
                assertEquals(input[1], result.diagnostics().getFirst().code(), result.diagnostics().toString());
                assertTrue(result.diagnostics().getFirst().path().endsWith(input[2]), result.diagnostics().toString());
                assertEquals(0, result.newRevision()); assertNull(workspace.getModElementByName("invalid_input"));
            }
        }
    }
}
