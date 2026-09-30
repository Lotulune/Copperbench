package dev.copperbench.generator.datapack;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.GradleProcessRunner;
import dev.copperbench.generator.LoaderRoutingWorkspaceTaskGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DataPackWorkspaceTaskGatewayTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {"datapack-1.20.1", "datapack-1.21.1", "datapack-26.1.x"})
    void routerPreparesDataPackAndExposesTaskEventsLogsAndHistory(String generatorId) throws Exception {
        UUID workspaceId = UUID.randomUUID();
        var store = store(workspaceId, generatorId);
        var events = new CopyOnWriteArrayList<WorkspaceTaskGateway.TaskEvent>();
        try (var tasks = new LoaderRoutingWorkspaceTaskGateway(store, ignored -> root, Path.of("."),
                Clock.systemUTC(), UUID::randomUUID);
             var subscription = tasks.subscribeTaskEvents(events::add)) {
            tasks.setGenerationPreparation((state, target, operation, output) -> {
                assertEquals(generatorId, state.generator().get("id").getAsString());
                Files.createDirectories(target.resolve("src/main"));
                Files.writeString(target.resolve("src/main/pack.mcmeta"), "{}");
                output.accept("data-pack preparation ran");
            });
            UUID taskId = UUID.fromString(tasks.start(workspaceId, Operation.GENERATE_WORKSPACE, new JsonObject()).get("id").getAsString());
            await(tasks, workspaceId, taskId, "succeeded");
            assertTrue(Files.isRegularFile(root.resolve("src/main/pack.mcmeta")));
            assertTrue(tasks.logs(workspaceId, taskId).toString().contains("data-pack preparation ran"));
            assertFalse(tasks.recent(workspaceId).isEmpty());
            assertTrue(tasks.diagnostics(workspaceId, taskId).isEmpty());
            assertFalse(events.isEmpty());
        }
    }

    @Test void buildAndExportUseZipArtifactAndForwardPreparation() throws Exception {
        UUID id = UUID.randomUUID();
        var calls = new CopyOnWriteArrayList<List<String>>();
        GradleProcessRunner process = (target, arguments, timeout, output) -> {
            calls.add(arguments);
            assertTrue(Files.isRegularFile(target.resolve("src/main/pack.mcmeta")));
            Path artifact = target.resolve("build/export/export.zip");
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact, "data-pack-artifact");
            return new GradleProcessRunner.ProcessResult(0, false);
        };
        try (var tasks = new DataPackWorkspaceTaskGateway(store(id, "datapack-1.21.1"), ignored -> root,
                root, Clock.systemUTC(), UUID::randomUUID, process)) {
            tasks.setGenerationPreparation((state, target, operation, output) -> {
                Files.createDirectories(target.resolve("src/main"));
                Files.writeString(target.resolve("src/main/pack.mcmeta"), "{}");
            });
            UUID build = UUID.fromString(tasks.start(id, Operation.BUILD_WORKSPACE, new JsonObject()).get("id").getAsString());
            await(tasks, id, build, "succeeded");
            JsonObject payload = new JsonObject(); payload.addProperty("output", "dist/example.zip");
            UUID export = UUID.fromString(tasks.start(id, Operation.EXPORT_WORKSPACE, payload).get("id").getAsString());
            await(tasks, id, export, "succeeded");
            assertEquals("data-pack-artifact", Files.readString(root.resolve("dist/example.zip")));
            assertEquals(List.of(List.of("build"), List.of("build")), calls);
            payload.addProperty("output", "../escape.zip");
            UUID rejected = UUID.fromString(tasks.start(id, Operation.EXPORT_WORKSPACE, payload).get("id").getAsString());
            await(tasks, id, rejected, "failed");
            assertFalse(tasks.diagnostics(id, rejected).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> tasks.start(id, Operation.RUN_GAMETEST, new JsonObject()));
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "copperbench.stage8.workspaceGeneratorBuild", matches = "true")
    void uiBuildCommandProducesARealDataPackZip() throws Exception {
        UUID id = UUID.randomUUID();
        var store = store(id, "datapack-1.20.1");
        Files.createDirectories(root.resolve("src/main/data/probe/functions"));
        Files.writeString(root.resolve("src/main/pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Data pack regression\"}}");
        Files.writeString(root.resolve("src/main/data/probe/functions/hello.mcfunction"), "say routed build\n");
        Files.copy(Path.of("plugins/generator-1.20.1/datapack-1.20.1/workspacebase/build.gradle"), root.resolve("build.gradle"));
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'datapack_regression'\n");
        Files.createDirectories(root.resolve("gradle/wrapper"));
        for (String file : List.of("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"))
            Files.copy(Path.of(file), root.resolve(file));
        root.resolve("gradlew").toFile().setExecutable(true);
        try (var tasks = new LoaderRoutingWorkspaceTaskGateway(store, ignored -> root, Path.of("."), Clock.systemUTC(), UUID::randomUUID)) {
            var service = new dev.copperbench.core.application.WorkspaceApplicationService(store, tasks, Clock.systemUTC(), UUID::randomUUID);
            var started = service.execute(dev.copperbench.core.contract.UiCore.Command.of(UUID.randomUUID(), id, 0,
                    Operation.BUILD_WORKSPACE, new JsonObject()), new dev.copperbench.core.contract.UiCore.RequestContext(
                    dev.copperbench.core.contract.UiCore.Actor.UI, dev.copperbench.core.contract.UiCore.PermissionProfile.WORKSPACE));
            assertEquals("accepted", started.result().status(), started.result().diagnostics().toString());
            UUID task = UUID.fromString(started.result().task().getAsJsonObject().get("id").getAsString());
            Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
            while (Instant.now().isBefore(deadline) && tasks.find(id, task).orElseThrow().get("state").getAsString().equals("running"))
                Thread.sleep(100);
            assertEquals("succeeded", tasks.find(id, task).orElseThrow().get("state").getAsString(), tasks.logs(id, task).toString());
            try (var zip = new java.util.zip.ZipFile(root.resolve("build/export/export.zip").toFile())) {
                assertNotNull(zip.getEntry("pack.mcmeta"));
                var entry = zip.getEntry("data/probe/functions/hello.mcfunction");
                assertNotNull(entry);
                assertEquals("say routed build\n", new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    @Test void routerCancelsTheDataPackWorker() throws Exception {
        UUID id = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1), block = new CountDownLatch(1);
        try (var tasks = new LoaderRoutingWorkspaceTaskGateway(store(id, "datapack-1.21.1"), ignored -> root,
                root, Clock.systemUTC(), UUID::randomUUID)) {
            tasks.setGenerationPreparation((state, target, operation, output) -> { started.countDown(); block.await(); });
            UUID task = UUID.fromString(tasks.start(id, Operation.GENERATE_WORKSPACE, new JsonObject()).get("id").getAsString());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(1, tasks.active(id).size());
            assertTrue(tasks.cancel(id, task).isPresent());
            await(tasks, id, task, "cancelled");
            assertTrue(tasks.active(id).isEmpty());
        } finally { block.countDown(); }
    }

    private static RevisionedWorkspaceStore store(UUID id, String generatorId) {
        JsonObject generator = new JsonObject(); generator.addProperty("id", generatorId);
        generator.addProperty("loader", "datapack");
        var store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(id, "Data Pack", "mod", 0, false, generator, new JsonObject(), List.of()));
        return store;
    }

    private static void await(WorkspaceTaskGateway tasks, UUID id, UUID taskId, String expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            String state = tasks.find(id, taskId).orElseThrow().get("state").getAsString();
            if (!state.equals("running") && !state.equals("queued")) {
                assertEquals(expected, state, tasks.diagnostics(id, taskId).toString()); return;
            }
            Thread.sleep(10);
        }
        fail("Data-pack task did not complete");
    }
}
