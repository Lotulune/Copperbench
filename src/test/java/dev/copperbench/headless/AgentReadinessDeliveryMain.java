package dev.copperbench.headless;

import com.google.gson.*;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Opt-in real product delivery gate. No simulated task gateway, authority issuance or EULA acceptance. */
public final class AgentReadinessDeliveryMain {
    public static void main(String[] args) throws Exception {
        McreatorTestRuntime.ensureInitialized();
        Path repository = Path.of("").toAbsolutePath().normalize();
        Path workspaceParent = repository.resolve("build/m1-workspaces"); Files.createDirectories(workspaceParent);
        Path root = Files.createTempDirectory(workspaceParent, "delivery-");
        Path evidence = repository.resolve("build/reports/m1-delivery").resolve(root.getFileName());
        Files.createDirectories(evidence);
        Path file = root.resolve("m1_delivery.mcreator");
        var settings = new WorkspaceSettings("m1_delivery");
        settings.setModName("M1 Delivery"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
        JsonObject config = new JsonObject();
        try (Workspace workspace = Workspace.createWorkspace(file.toFile(), settings)) {
            WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
            if (!workspace.getGenerator().generateBase()) throw new IllegalStateException("Fixture base generation failed");
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            config.addProperty("generatorVersion", workspace.getGenerator().getFullGeneratorVersion());
        }
        Path fixture = repository.resolve("examples/agent-readiness/m1-delivery");
        Files.copy(fixture.resolve("copperbench-tests.json"), root.resolve("copperbench-tests.json"));
        Path tests = root.resolve("src/gametest/java/copperbench/acceptance/ContractGameTests.java");
        Files.createDirectories(tests.getParent()); Files.copy(fixture.resolve("ContractGameTests.java"), tests);
        JsonObject identity = new JsonObject();
        for (String name : java.util.List.of("recovery_probe.java", "ContractGameTests.java", "copperbench-tests.json"))
            identity.addProperty(name, WorkspaceExecutionSnapshot.sha256(fixture.resolve(name)));
        config.add("fixtureHashes", identity);
        config.addProperty("fixture", fixture.toString()); config.addProperty("workspace", file.toString());
        config.addProperty("evidence", evidence.toString()); config.addProperty("cwd", repository.toString());
        config.addProperty("javaVersion", System.getProperty("java.version")); config.addProperty("javaHome", System.getProperty("java.home"));
        config.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        config.addProperty("startedAt", Instant.now().toString());
        config.addProperty("cachePolicy", "Existing local Gradle/Minecraft caches retained; new workspace and isolated host; not a cold-cache claim.");
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        Path configFile = evidence.resolve("config.json"); Files.writeString(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        System.out.println("M1 delivery evidence: " + evidence);
        Process process = new ProcessBuilder(args[0], "sdk/python/agent_readiness_delivery.py", configFile.toString()).inheritIO().start();
        if (!process.waitFor(70, TimeUnit.MINUTES)) {
            process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly();
            throw new IllegalStateException("M1 delivery timed out; evidence retained at " + evidence);
        }
        if (process.exitValue() != 0) throw new IllegalStateException("M1 delivery failed; evidence retained at " + evidence);
        System.out.println("M1 real delivery gate passed: " + evidence);
        // Runtime plugin executors are application-owned; this command is a standalone development harness.
        System.exit(0);
    }
}
