package dev.copperbench.generator.workspace;

import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.generator.*;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.testing.McreatorTestRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NeoForge263AcceptanceTest {
    @Test @EnabledIfSystemProperty(named = "copperbench.p263.neoforgeNativeAcceptance", matches = "true")
    void realPackagedHostExecutesTheFrozenSixBusinessCases() throws Exception {
        McreatorTestRuntime.ensureInitialized();
        Path evidence = Path.of("output/p263-10/backend-" + UUID.randomUUID()).toAbsolutePath();
        Path workspace = evidence.resolve("workspace"); Files.createDirectories(evidence);
        var created = new WorkspaceCreationService().create("neoforge-26.3", "Copperbench Camp Kit", "copperbench_camp_kit",
                "dev.copperbench.campkit", workspace.toString(), "1.0.0", true);
        Files.writeString(evidence.resolve("creation.json"), UiCore.wireGson().toJson(created));
        assertTrue(created.complete(), created.toString());
        Path fixture = Path.of("examples/camp-kit-native/neoforge-26.3");
        try (var paths = Files.walk(fixture)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path target = workspace.resolve(fixture.relativize(path)); Files.createDirectories(target.getParent());
                Files.copy(path, target);
            }
        }
        Path entry = workspace.resolve("src/main/java/dev/copperbench/campkit/CopperbenchCampKitMod.java");
        String source = Files.readString(entry);
        String marker = "// Start of user code block mod init";
        assertTrue(source.contains(marker));
        Files.writeString(entry, source.replace(marker, marker + "\n        CampKitContent.initialize(modBus);"));
        var environment = new dev.copperbench.generator.neoforge.NeoForge1211Generator(Path.of("."), dev.copperbench.generator.neoforge.NeoForge1211Generator.Profile.NEOFORGE_263).gameTestEnvironment();
        var runner = Fabric1211ProcessRunner.system("COPPERBENCH_P263_NEOFORGE_INITIALIZED", BundledJdkLocator.locate(Path.of("."), 25));
        StringBuilder buildLog = new StringBuilder();
        var build = runner.run(workspace, List.of("build", "--console=plain"), Duration.ofMinutes(15), line -> buildLog.append(line).append('\n'));
        Files.writeString(evidence.resolve("build.log"), buildLog);
        assertEquals(0, build.exitCode(), buildLog.toString());
        var host = GameTestSupport.host(workspace, evidence.resolve("host"), GameTestSupport.configuration(workspace), environment);
        Instant start = Instant.now();
        StringBuilder hostLog = new StringBuilder();
        var run = runner.run(host.root(), List.of("runGameTest", "--console=plain"), Duration.ofMinutes(15), line -> hostLog.append(line).append('\n'));
        Files.writeString(evidence.resolve("host.log"), hostLog);
        Files.writeString(evidence.resolve("host-process.json"), UiCore.wireGson().toJson(run));
        var assessment = GameTestReport.assess(host.report(), start, RequiredCaseManifest.catalog().getFirst());
        Files.writeString(evidence.resolve("assessment.json"), UiCore.wireGson().toJson(assessment));
        assertEquals(0, run.exitCode(), hostLog.toString());
        assertEquals("passed", assessment.verification().get("status").getAsString(), assessment.toString());
        assertEquals(6, assessment.verification().get("acceptanceExecuted").getAsInt());
        assertEquals(host.jarSha256(), WorkspaceExecutionSnapshot.sha256(host.jar()));
    }
}
