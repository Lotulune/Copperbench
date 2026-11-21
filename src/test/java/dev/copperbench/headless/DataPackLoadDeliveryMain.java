package dev.copperbench.headless;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.generator.BundledJdkLocator;
import dev.copperbench.generator.GameTestReport;
import dev.copperbench.generator.GameTestSupport;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.testing.McreatorTestRuntime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a {@code datapack-1.21.1} workspace through the product, writes a function, a recipe and a registry tag
 * through Core with the Python SDK, builds the ZIP, then loads that ZIP unchanged from the world's datapacks folder
 * of the Fabric 1.21.1 GameTest host. The host needs no EULA acceptance; a probe mod only satisfies its JAR slot.
 * Evidence goes to {@code output/minecraft-validation/datapack-load-<run>}.
 */
public final class DataPackLoadDeliveryMain {
    private static final String GENERATOR = "datapack-1.21.1", PACK_FILE = "copperbench_dp.zip";
    private static final List<String> WRAPPER = List.of("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties");
    private static final int TESTS = 4;

    private DataPackLoadDeliveryMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Provide the Python executable.");
        McreatorTestRuntime.ensureInitialized();
        Path repository = Path.of("").toAbsolutePath().normalize();
        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path evidence = Files.createDirectories(repository.resolve("output/minecraft-validation/datapack-load-" + runId));
        JsonObject summary = new JsonObject();
        summary.addProperty("generatorId", GENERATOR);
        summary.addProperty("sourceCommit", git(repository, "rev-parse", "HEAD"));
        summary.addProperty("sourceTreeClean", git(repository, "status", "--porcelain").isEmpty());
        System.out.println("Data pack load evidence: " + evidence);

        // 1. Product creation path, then Core edits and build through the Python SDK.
        Path workspace = evidence.resolve("workspace");
        var created = new WorkspaceCreationService().create(GENERATOR, "Copperbench Data Probe", "cbdp", null,
                workspace.toString(), "1.0.0", true);
        Files.writeString(evidence.resolve("creation.json"), UiCore.wireGson().toJson(created));
        if (!created.complete()) throw new IllegalStateException("Data pack creation failed: " + created);
        JsonObject config = new JsonObject();
        config.addProperty("workspace", workspace.resolve("cbdp.mcreator").toString());
        config.addProperty("evidence", evidence.toString());
        config.addProperty("cwd", repository.toString());
        JsonArray launcher = new JsonArray();
        launcher.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED"); launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
        launcher.add("-cp"); launcher.add(System.getProperty("java.class.path")); launcher.add("net.mcreator.Launcher");
        config.add("launcher", launcher);
        Path configFile = evidence.resolve("config.json");
        Files.writeString(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(config));
        Process python = new ProcessBuilder(args[0], "sdk/python/datapack_load_delivery.py", configFile.toString()).inheritIO().start();
        if (!python.waitFor(25, TimeUnit.MINUTES)) {
            python.descendants().forEach(ProcessHandle::destroy); python.destroyForcibly();
            throw new IllegalStateException("Data pack workspace step timed out: " + evidence);
        }
        if (python.exitValue() != 0) throw new IllegalStateException("Data pack workspace step failed: " + evidence);
        Path archive = workspace.resolve("build/export/export.zip");
        String archiveSha256 = WorkspaceExecutionSnapshot.sha256(archive);
        summary.addProperty("dataPackSha256", archiveSha256);

        // 2. Probe host: Copperbench's packaged-JAR GameTest host for Fabric 1.21.1, with the pack in run/world/datapacks.
        Fabric1211Generator generator = new Fabric1211Generator(repository, Fabric1211Generator.Profile.FABRIC_1211);
        JsonObject environment = generator.gameTestEnvironment();
        summary.add("hostEnvironment", environment);
        Path snapshot = evidence.resolve("host-input");
        Files.createDirectories(snapshot.resolve("build/libs"));
        probeJar(snapshot.resolve("build/libs/cbdp-probe-1.0.0.jar"));
        Path wrapperSource = repository.resolve("plugins/generator-1.21.1/fabric-1.21.1/workspacebase");
        for (String path : WRAPPER) {
            Path target = snapshot.resolve(path);
            Files.createDirectories(target.getParent());
            Files.copy(wrapperSource.resolve(path), target);
        }
        Path tests = Files.createDirectories(snapshot.resolve("src/gametest/java/copperbench/datapack"));
        Files.copy(repository.resolve("examples/datapack-load/DataPackLoadTests.java"), tests.resolve("DataPackLoadTests.java"));
        Files.createDirectories(snapshot.resolve("src/gametest/resources"));
        var configuration = new GameTestSupport.Configuration("packaged_jar", "runGameTest", "run/gametest-results.xml",
                "src/gametest/java", "src/gametest/resources", List.of("copperbench.datapack.DataPackLoadTests"), TESTS);
        GameTestSupport.Host host = GameTestSupport.host(snapshot, evidence.resolve("host"), configuration, environment);
        Path deployed = Files.createDirectories(host.root().resolve("run/world/datapacks")).resolve(PACK_FILE);
        Files.copy(archive, deployed);
        if (!WorkspaceExecutionSnapshot.sha256(deployed).equals(archiveSha256)) throw new IOException("Deployed pack differs from the build");
        summary.addProperty("deployedPack", deployed.toString());

        StringBuilder log = new StringBuilder();
        Instant startedAt = Instant.now();
        Path javaHome = BundledJdkLocator.locate(repository, environment.get("javaRelease").getAsInt());
        var process = Fabric1211ProcessRunner.system("unused-datapack-marker", javaHome).run(host.root(), List.of("runGameTest"),
                Duration.ofMinutes(20), line -> { log.append(line).append('\n'); System.out.println("[host] " + line); });
        Files.writeString(evidence.resolve("host.log"), log, StandardCharsets.UTF_8);
        JsonObject verification = GameTestReport.assess(host.report(), startedAt, TESTS).verification();
        verification.addProperty("processExitCode", process.exitCode());
        if (Files.isRegularFile(host.report())) Files.copy(host.report(), evidence.resolve("gametest-results.xml"));
        summary.add("gameTest", verification);
        boolean passed = process.exitCode() == 0 && verification.has("status") && verification.get("status").getAsString().equals("passed");
        summary.addProperty("result", passed ? "passed" : "failed");
        Files.writeString(evidence.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(summary));
        System.out.println("Data pack load " + (passed ? "passed" : "failed") + ": " + evidence.resolve("summary.json"));
        // Process runners and plugin executors are application-owned; this is a standalone harness.
        System.exit(passed ? 0 : 1);
    }

    /** A metadata-only Fabric mod: the host loads one packaged JAR, and this one adds nothing to the game. */
    private static void probeJar(Path jar) throws IOException {
        try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write("""
                    {"schemaVersion": 1, "id": "cbdp_probe", "version": "1.0.0", "name": "Copperbench data pack probe"}
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static String git(Path repository, String... command) throws IOException, InterruptedException {
        var arguments = new java.util.ArrayList<>(List.of("git", "-C", repository.toString()));
        arguments.addAll(List.of(command));
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor();
        return output;
    }
}
