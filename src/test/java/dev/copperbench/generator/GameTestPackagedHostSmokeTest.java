package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211WorkspaceTaskGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real product task pipeline on both NeoForge GameTest API generations; no player worlds or EULA acceptance. */
@EnabledIfEnvironmentVariable(named = "COPPERBENCH_GAMETEST_NEO_SMOKE", matches = "true")
class GameTestPackagedHostSmokeTest {
    @Test void packagedNeoForgeModsLoadAndProduceCountedReports(@org.junit.jupiter.api.io.TempDir Path temporary) throws Exception {
        Path repository = Path.of(".").toAbsolutePath().normalize();
        Path evidence = repository.resolve("build/agent-workflow-verification/neo-smoke/" + UUID.randomUUID());
        Files.createDirectories(evidence);
        JsonArray results = new JsonArray();
        for (var profile : List.of(NeoForge1211Generator.Profile.NEOFORGE_1211, NeoForge1211Generator.Profile.NEOFORGE_262)) {
            String selection = System.getenv("COPPERBENCH_GAMETEST_NEO_TRACKS");
            if (selection != null && !List.of(selection.split(",")).contains(profile.generatorId())) continue;
            // Keep Windows wrapper paths below its launcher limit despite the nested task directory.
            Path root = temporary.resolve(profile.generatorId()); Files.createDirectories(root);
            UUID workspaceId = UUID.randomUUID();
            JsonObject generator = new JsonObject(); generator.addProperty("id", profile.generatorId());
            var state = new WorkspaceState(workspaceId, "host_smoke", "mod", 0, false, generator, new JsonObject(), List.of());
            new NeoForge1211Generator(repository, profile).generate(root, state);
            Files.writeString(root.resolve("host_smoke.mcreator"), "{}");
            GameTestSupport.prepare(root, new NeoForge1211Generator(repository, profile).gameTestEnvironment(), "host_smoke");
            RevisionedWorkspaceStore store = new RevisionedWorkspaceStore(); store.register(state);
            try (var gateway = new NeoForge1211WorkspaceTaskGateway(store, ignored -> root, repository, Clock.systemUTC(), UUID::randomUUID, profile);
                 var logs = Files.newBufferedWriter(evidence.resolve(profile.generatorId() + ".log"), StandardCharsets.UTF_8)) {
                UUID taskId = UUID.fromString(gateway.start(workspaceId, Operation.RUN_GAMETEST, new JsonObject()).get("id").getAsString());
                Instant deadline = Instant.now().plusSeconds(1200);
                long sequence = 0; JsonObject task;
                while (true) {
                    task = gateway.find(workspaceId, taskId).orElseThrow();
                    for (JsonObject entry : gateway.logs(workspaceId, taskId)) {
                        long next = entry.get("sequence").getAsLong();
                        if (next > sequence) { logs.write(entry.get("text").getAsString()); logs.newLine(); sequence = next; }
                    }
                    logs.flush();
                    if (!List.of("running", "queued").contains(task.get("state").getAsString())) break;
                    if (!Instant.now().isBefore(deadline)) { gateway.cancel(workspaceId, taskId); fail("NeoForge smoke timed out: " + profile.generatorId()); }
                    Thread.sleep(500);
                }
                results.add(task);
                Files.writeString(evidence.resolve("results.json"), results.toString() + "\n");
                assertEquals("succeeded", task.get("state").getAsString(), task.toString());
                JsonObject verification = task.getAsJsonObject("verification");
                assertEquals("passed", verification.get("status").getAsString());
                assertEquals(1, verification.get("acceptanceExecuted").getAsInt());
                assertTrue(verification.get("passed").getAsInt() >= 1);
                assertTrue(verification.has("artifactSha256"));
                assertFalse(Files.exists(Path.of(verification.get("artifactPath").getAsString()).getParent().getParent().resolve("eula.txt")));
                Files.copy(Path.of(verification.get("artifactPath").getAsString()), evidence.resolve(profile.generatorId() + ".jar"));
                Files.copy(Path.of(verification.get("reportPath").getAsString()), evidence.resolve(profile.generatorId() + ".xml"));
                Files.copy(Path.of(verification.get("sourceSnapshot").getAsJsonObject().get("manifestPath").getAsString()), evidence.resolve(profile.generatorId() + "-source-manifest.json"));
            }
        }
        assertFalse(results.isEmpty());
    }
}
