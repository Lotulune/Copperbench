package dev.copperbench.evaluation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.generator.BundledJdkLocator;
import dev.copperbench.generator.GameTestReport;
import dev.copperbench.generator.GameTestSupport;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the Emberkit comparison assertions against finished mod JARs. Each JAR is loaded unchanged into the product's
 * own packaged-JAR GameTest host (Fabric 1.21.1), so every group is judged by the same host and the same tests.
 * Usage: runAgentEvaluation -PagentEvalManifest=manifest.json, where the manifest lists {label, task, jar} runs.
 */
public final class AgentEvaluationMain {
    private static final List<String> WRAPPER = List.of("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties");

    private AgentEvaluationMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0].isBlank()) throw new IllegalArgumentException("Pass -PagentEvalManifest=<manifest.json>");
        Path repository = Path.of("").toAbsolutePath().normalize();
        Path fixture = repository.resolve("examples/agent-eval/emberkit");
        JsonObject catalogue = JsonParser.parseString(Files.readString(fixture.resolve("assertions.json"))).getAsJsonObject();
        JsonObject manifest = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path work = repository.resolve("build/agent-eval").resolve(runId);
        Path reports = repository.resolve("build/reports/agent-eval").resolve(runId);
        Files.createDirectories(reports);
        Files.copy(Path.of(args[0]), reports.resolve("manifest.json"));

        Fabric1211Generator generator = new Fabric1211Generator(repository, Fabric1211Generator.Profile.FABRIC_1211);
        JsonObject environment = generator.gameTestEnvironment();
        Path javaHome = BundledJdkLocator.locate(repository, environment.get("javaRelease").getAsInt());
        Path wrapperSource = repository.resolve("plugins/generator-1.21.1/fabric-1.21.1/workspacebase");

        List<AgentEvaluation.RunResult> results = new ArrayList<>();
        for (JsonElement raw : manifest.getAsJsonArray("runs")) {
            JsonObject run = raw.getAsJsonObject();
            String label = run.get("label").getAsString(), task = run.get("task").getAsString();
            if (!label.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid label: " + label);
            Path jar = Path.of(run.get("jar").getAsString()).toAbsolutePath().normalize();
            System.out.println("Agent evaluation " + label + " (" + task + "): " + jar);
            results.add(evaluate(catalogue, fixture, task, label, jar, work.resolve(label), reports, environment, javaHome, wrapperSource));
        }

        JsonObject summary = new JsonObject();
        summary.addProperty("schemaVersion", "1");
        summary.addProperty("runId", runId);
        summary.add("environment", environment);
        summary.addProperty("host", "Copperbench packaged-JAR GameTest host; each JAR loaded unchanged from run/mods");
        JsonArray runs = new JsonArray();
        results.forEach(result -> runs.add(AgentEvaluation.toJson(result)));
        summary.add("runs", runs);
        Files.writeString(reports.resolve("results.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(summary));
        Files.writeString(reports.resolve("results.md"), AgentEvaluation.markdown(catalogue, results), StandardCharsets.UTF_8);
        for (AgentEvaluation.RunResult result : results)
            System.out.println("Agent evaluation " + result.label() + ": " + result.passed() + "/" + result.outcomes().size() + " passed");
        System.out.println("Agent evaluation report: " + reports.resolve("results.md"));
        // Process runners and plugin executors are application-owned; this is a standalone harness.
        System.exit(0);
    }

    private static AgentEvaluation.RunResult evaluate(JsonObject catalogue, Path fixture, String task, String label, Path jar, Path work,
            Path reports, JsonObject environment, Path javaHome, Path wrapperSource) throws Exception {
        List<AgentEvaluation.Assertion> assertions = AgentEvaluation.assertions(catalogue, task);
        String entrypoint = AgentEvaluation.task(catalogue, task).get("entrypoint").getAsString();
        String jarSha256 = WorkspaceExecutionSnapshot.sha256(jar);

        // A snapshot shaped like a built workspace: the JAR in build/libs, the wrapper, and this task's tests.
        Path snapshot = work.resolve("snapshot");
        Path libs = Files.createDirectories(snapshot.resolve("build/libs"));
        Files.copy(jar, libs.resolve(jar.getFileName()));
        for (String path : WRAPPER) {
            Path target = snapshot.resolve(path);
            Files.createDirectories(target.getParent());
            Files.copy(wrapperSource.resolve(path), target);
        }
        Path tests = Files.createDirectories(snapshot.resolve("src/gametest/java/copperbench/eval"));
        try (var sources = Files.list(fixture.resolve(task))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList())
                Files.copy(source, tests.resolve(source.getFileName()));
        }
        Files.createDirectories(snapshot.resolve("src/gametest/resources"));

        var configuration = new GameTestSupport.Configuration("packaged_jar", "runGameTest", "run/gametest-results.xml",
                "src/gametest/java", "src/gametest/resources", List.of(entrypoint), (int) AgentEvaluation.gameTestCount(assertions));
        GameTestSupport.Host host = GameTestSupport.host(snapshot, work.resolve("host"), configuration, environment);
        if (!host.jarSha256().equals(jarSha256)) throw new IOException("The host received different JAR bytes for " + label);

        StringBuilder log = new StringBuilder();
        Instant startedAt = Instant.now();
        var process = Fabric1211ProcessRunner.system("unused-evaluation-marker", javaHome).run(host.root(), List.of("runGameTest"),
                Duration.ofMinutes(20), line -> { log.append(line).append('\n'); System.out.println("[" + label + "] " + line); });
        Files.writeString(reports.resolve(label + "-host.log"), log, StandardCharsets.UTF_8);
        var assessment = GameTestReport.assess(host.report(), startedAt, configuration.minimumTests());
        JsonObject verification = assessment.verification();
        verification.addProperty("processExitCode", process.exitCode());
        Files.writeString(reports.resolve(label + "-gametest.json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(verification));
        if (Files.isRegularFile(host.report())) Files.copy(host.report(), reports.resolve(label + "-gametest-results.xml"));

        List<AgentEvaluation.Outcome> outcomes = new ArrayList<>(AgentEvaluation.gameTests(assertions, verification));
        outcomes.addAll(AgentEvaluation.staticChecks(task, assertions, jar));
        // Keep catalogue order so every report lists assertions the same way.
        outcomes.sort(java.util.Comparator.comparingInt(outcome -> assertions.stream().map(AgentEvaluation.Assertion::id).toList().indexOf(outcome.id())));
        return new AgentEvaluation.RunResult(label, task, jar.toString(), jarSha256, verification.get("reasonCode").getAsString(), List.copyOf(outcomes));
    }
}
