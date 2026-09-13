package dev.copperbench.generator;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real Gradle compilation of the product's host templates. This does not claim gameplay coverage. */
@EnabledIfEnvironmentVariable(named = "COPPERBENCH_GAMETEST_HOST_MATRIX", matches = "true")
class GameTestHostCompilationTest {
    @Test void generatedHostAndReporterCompileAgainstAllSupportedJavaTracks() throws Exception {
        Path repository = Path.of(".").toAbsolutePath().normalize();
        Path evidence = repository.resolve("build/agent-workflow-verification/host-compile/" + UUID.randomUUID());
        Files.createDirectories(evidence);
        List<JsonObject> environments = new ArrayList<>();
        for (var profile : List.of(Fabric1211Generator.Profile.FABRIC_1201, Fabric1211Generator.Profile.FABRIC_1211,
                Fabric1211Generator.Profile.FABRIC_261, Fabric1211Generator.Profile.FABRIC_262))
            environments.add(new Fabric1211Generator(repository, profile).gameTestEnvironment());
        for (var profile : List.of(NeoForge1211Generator.Profile.NEOFORGE_1201, NeoForge1211Generator.Profile.NEOFORGE_1211,
                NeoForge1211Generator.Profile.NEOFORGE_261, NeoForge1211Generator.Profile.NEOFORGE_262))
            environments.add(new NeoForge1211Generator(repository, profile).gameTestEnvironment());
        String selection = System.getenv("COPPERBENCH_GAMETEST_HOST_TRACKS");
        JsonArray results = new JsonArray();
        for (JsonObject environment : environments) {
            String generator = environment.get("generatorId").getAsString();
            if (selection != null && !List.of(selection.split(",")).contains(generator)) continue;
            JsonObject result = new JsonObject(); result.addProperty("generatorId", generator); result.addProperty("kind", "host_compile_only");
            Path fixture = evidence.resolve(generator).resolve("fixture"); Files.createDirectories(fixture);
            var generatorProjection = new JsonObject(); generatorProjection.addProperty("id", generator);
            var state = new WorkspaceState(UUID.randomUUID(), "host_compile_fixture", "mod", 0, false, generatorProjection, new JsonObject(), List.of());
            try {
                generate(repository, fixture, generator, state);
                GameTestSupport.prepare(fixture, environment, "host_compile_fixture");
                Path jar = fixture.resolve("build/libs/compile-fixture.jar"); Files.createDirectories(jar.getParent());
                try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
                    String metadata = generator.startsWith("fabric") ? "fabric.mod.json" : generator.endsWith("1.20.1") ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml";
                    zip.putNextEntry(new ZipEntry(metadata));
                    zip.write((generator.startsWith("fabric") ? "{\"schemaVersion\":1,\"id\":\"host_compile_fixture\",\"version\":\"1\"}" : "").getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
                var host = GameTestSupport.host(fixture, fixture.resolveSibling("host"), GameTestSupport.configuration(fixture), environment);
                Path log = fixture.resolveSibling("compile.log");
                var runner = Fabric1211ProcessRunner.system("HOST_COMPILATION_ONLY", BundledJdkLocator.locate(repository, environment.get("javaRelease").getAsInt()));
                try (var writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
                    var process = runner.run(host.root(), List.of("compileJava"), Duration.ofMinutes(20), line -> {
                        try { writer.write(line); writer.newLine(); writer.flush(); }
                        catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                    });
                    result.addProperty("exitCode", process.exitCode()); result.addProperty("passed", process.exitCode() == 0);
                }
                assertFalse(Files.exists(host.root().resolve("run/eula.txt")), "Compilation must not accept a server EULA");
                result.addProperty("log", log.toString());
            } catch (Exception exception) { result.addProperty("passed", false); result.addProperty("failure", exception.toString()); }
            results.add(result);
            Files.writeString(evidence.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n");
            System.out.println("Host compilation: " + result);
        }
        assertFalse(results.isEmpty());
        assertTrue(results.asList().stream().allMatch(row -> row.getAsJsonObject().get("passed").getAsBoolean()), results.toString());
    }

    private static void generate(Path repository, Path root, String id, WorkspaceState state) throws Exception {
        for (var profile : List.of(Fabric1211Generator.Profile.FABRIC_1201, Fabric1211Generator.Profile.FABRIC_1211,
                Fabric1211Generator.Profile.FABRIC_261, Fabric1211Generator.Profile.FABRIC_262))
            if (profile.generatorId().equals(id)) { new Fabric1211Generator(repository, profile).generate(root, state); return; }
        for (var profile : List.of(NeoForge1211Generator.Profile.NEOFORGE_1201, NeoForge1211Generator.Profile.NEOFORGE_1211,
                NeoForge1211Generator.Profile.NEOFORGE_261, NeoForge1211Generator.Profile.NEOFORGE_262))
            if (profile.generatorId().equals(id)) { new NeoForge1211Generator(repository, profile).generate(root, state); return; }
        throw new IllegalArgumentException(id);
    }
}
