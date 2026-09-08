package dev.copperbench.generator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Stage 14C packaged-JAR/server-ready matrix for the four Fabric tracks.
 *
 * <p>The test is opt-in because it launches eight real Minecraft GameTest servers. Each host contains
 * only a tiny no-op GameTest harness plus the packaged Survey Pulse JAR under run/mods. It never writes
 * eula=true; Fabric's headless GameTest server is used specifically so the runtime layer can be verified
 * without accepting the Minecraft EULA on the user's behalf.</p>
 */
class Stage14CFabricPackagedMatrixTest {
    private static final String ENABLE_PROPERTY = "copperbench.stage14c.fabricPackagedMatrix";
    private static final String RESUME_PROPERTY = "copperbench.stage14c.fabricPackagedMatrixResume";
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration SERVER_TIMEOUT = Duration.ofMinutes(5);

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "true")
    void packagedJarLoadsAndReachesServerReadyAcrossFabricMatrix() throws Exception {
        Path repository = Path.of(".").toAbsolutePath().normalize();
        Path fixtureRoot = repository.resolve(".tmp/stage14-track-audit/projects");
        Path evidenceRoot = repository.resolve("evidence/stage14/2026-09-08/fabric-packaged-matrix");
        Path hostRoot = repository.resolve("build/stage14c-fabric-packaged-matrix");
        Path temp = repository.resolve("t/stage14c-fabric-packaged-matrix");
        Files.createDirectories(evidenceRoot);
        Files.createDirectories(temp);

        boolean resume = Boolean.parseBoolean(System.getProperty(RESUME_PROPERTY, "false"));
        List<CellResult> results = new ArrayList<>();
        for (FabricTrack track : tracks(repository)) {
            for (String variant : List.of("native", "copperbench")) {
                CellResult existing = resume ? readPassedCell(evidenceRoot, track.id(), variant) : null;
                results.add(existing != null ? existing
                        : runCell(repository, fixtureRoot, evidenceRoot, hostRoot, temp, track, variant));
            }
        }

        JsonArray cells = new JsonArray();
        List<String> failures = new ArrayList<>();
        for (CellResult result : results) {
            cells.add(result.evidence());
            if (!result.passed()) failures.add(result.track() + "/" + result.variant() + ": " + result.failure());
        }
        JsonObject matrix = new JsonObject();
        matrix.addProperty("schemaVersion", "1.0");
        matrix.addProperty("kind", "stage14c-fabric-packaged-server-ready-matrix");
        matrix.addProperty("eulaAcceptedByHarness", false);
        matrix.addProperty("cellCount", results.size());
        matrix.addProperty("passedCount", results.stream().filter(CellResult::passed).count());
        matrix.add("cells", cells);
        matrix.addProperty("passed", failures.isEmpty());
        matrix.addProperty("completedAt", Instant.now().toString());
        Files.writeString(evidenceRoot.resolve("matrix.json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(matrix),
                StandardCharsets.UTF_8);

        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    private static CellResult readPassedCell(Path evidenceRoot, String track, String variant) {
        Path evidenceJson = evidenceRoot.resolve(track).resolve(variant + ".json");
        if (!Files.isRegularFile(evidenceJson)) return null;
        try {
            JsonObject evidence = com.google.gson.JsonParser.parseString(Files.readString(evidenceJson)).getAsJsonObject();
            if (!evidence.has("passed") || !evidence.get("passed").getAsBoolean()) return null;
            return new CellResult(track, variant, true, "", evidence);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CellResult runCell(Path repository, Path fixtureRoot, Path evidenceRoot, Path hostRoot,
            Path temp, FabricTrack track, String variant) throws Exception {
        Instant started = Instant.now();
        Path source = fixtureRoot.resolve(track.id()).resolve(variant);
        Path cellEvidenceDir = evidenceRoot.resolve(track.id());
        Path evidenceJson = cellEvidenceDir.resolve(variant + ".json");
        Path evidenceLog = cellEvidenceDir.resolve(variant + ".log");
        Path host = hostRoot.resolve(track.id()).resolve(variant);
        Files.createDirectories(cellEvidenceDir);

        ProcessRun build = null;
        ProcessRun runtime = null;
        Path deployedJar = null;
        String failure = "";
        String output = "";
        boolean passed = false;
        try {
            require(Files.isDirectory(source), "missing fixture source: " + source);
            build = runGradle(source, track.gradleHome(), track.javaHome(), temp, BUILD_TIMEOUT,
                    List.of(track.packageTask()), false);
            require(!build.timedOut(), track.packageTask() + " timed out");
            require(build.exitCode() == 0, diagnosticTail(build.output()));

            Path packagedJar = findPackagedJar(source.resolve("build/libs"));
            require(packagedJar != null, "packaged Survey Pulse JAR missing");
            prepareHost(source, host, track);
            Path run = host.resolve("run");
            Files.createDirectories(run.resolve("mods"));
            deployedJar = run.resolve("mods/survey_pulse-1.0.jar");
            Files.copy(packagedJar, deployedJar);
            Files.writeString(run.resolve("server.properties"), """
                    server-ip=127.0.0.1
                    server-port=0
                    online-mode=false
                    level-name=stage14c-fabric-packaged-%s-%s
                    view-distance=3
                    simulation-distance=3
                    """.formatted(track.id(), variant), StandardCharsets.UTF_8);
            require(!eulaAccepted(run), "harness must not pre-accept the Minecraft EULA");
            require(!Files.exists(host.resolve("src/main/java/dev/example/surveypulse")),
                    "packaged host contains Survey Pulse source");

            runtime = runGradle(host, track.gradleHome(), track.javaHome(), temp, SERVER_TIMEOUT,
                    List.of("runServer"), true);
            output = runtime.output();
            require(!runtime.timedOut(), diagnosticTail(output));
            require(runtime.exitCode() == 0, diagnosticTail(output));
            require(output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id()), diagnosticTail(output));
            require(output.contains("Started game test server"), diagnosticTail(output));
            require(output.contains("STAGE14C_HOST_GAMETEST_READY track=" + track.id()), diagnosticTail(output));
            require(requiredTestsPassed(output), diagnosticTail(output));
            require(!fatalSeen(output), diagnosticTail(output));
            require(!eulaAccepted(run), "GameTest run accepted the Minecraft EULA");
            passed = true;
        } catch (Throwable problem) {
            failure = problem.getClass().getSimpleName() + ": " + String.valueOf(problem.getMessage());
        } finally {
            if (runtime != null) output = runtime.output();
            Files.writeString(evidenceLog, output, StandardCharsets.UTF_8);
            JsonObject evidence = evidence(repository, started, track, variant, source, host, deployedJar,
                    build, runtime, output, failure, passed, evidenceLog);
            Files.writeString(evidenceJson,
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(evidence),
                    StandardCharsets.UTF_8);
        }
        return new CellResult(track.id(), variant, passed, failure,
                new GsonBuilder().create().fromJson(Files.readString(evidenceJson), JsonObject.class));
    }

    private static JsonObject evidence(Path repository, Instant started, FabricTrack track, String variant,
            Path source, Path host, Path jar, ProcessRun build, ProcessRun runtime, String output,
            String failure, boolean passed, Path evidenceLog) {
        boolean jarPresent = jar != null && Files.isRegularFile(jar);
        JsonObject root = new JsonObject();
        root.addProperty("track", track.id());
        root.addProperty("variant", variant);
        root.addProperty("minecraftVersion", track.minecraftVersion());
        root.addProperty("loaderVersion", track.loaderVersion());
        root.addProperty("fabricApiVersion", track.fabricApiVersion());
        root.addProperty("javaHome", track.javaHome().toString());
        root.addProperty("gradleHome", track.gradleHome().toString());
        root.addProperty("prepared", Files.isDirectory(source));
        root.addProperty("compiled", build != null && build.exitCode() == 0 && !build.timedOut());
        root.addProperty("initializerExecuted", output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id()));
        root.addProperty("eulaBoundaryStatus", "not_applicable_game_test_bypass");
        root.addProperty("eulaAcceptedByHarness", false);
        root.addProperty("serverReady", output.contains("Started game test server"));
        root.addProperty("serverReadyMode", "fabric_headless_gametest_dedicated");
        root.addProperty("packagedJarLoaded", jarPresent
                && output.contains("SURVEY_PULSE_INITIALIZED track=" + track.id()));
        root.addProperty("behaviorVerified", false);
        root.addProperty("behaviorStatus", "not_run_in_server_ready_matrix");
        root.addProperty("hostContainsSurveySources", Files.exists(host.resolve("src/main/java/dev/example/surveypulse")));
        root.addProperty("hostHarnessTestPassed", output.contains("STAGE14C_HOST_GAMETEST_READY track=" + track.id())
                && requiredTestsPassed(output));
        root.addProperty("buildExitCode", build == null ? -1 : build.exitCode());
        root.addProperty("buildTimedOut", build == null || build.timedOut());
        root.addProperty("runtimeExitCode", runtime == null ? -1 : runtime.exitCode());
        root.addProperty("runtimeTimedOut", runtime == null || runtime.timedOut());
        root.addProperty("fatalSeen", fatalSeen(output));
        if (jarPresent) {
            try {
                root.addProperty("packagedJarSha256", sha256(jar));
            } catch (Exception ignored) {
            }
        }
        root.addProperty("logFile", repository.relativize(evidenceLog).toString().replace('\\', '/'));
        root.addProperty("durationSeconds", Duration.between(started, Instant.now()).toMillis() / 1000.0);
        root.addProperty("failure", failure);
        root.addProperty("passed", passed);
        root.addProperty("completedAt", Instant.now().toString());
        return root;
    }

    private static void prepareHost(Path source, Path host, FabricTrack track) throws IOException {
        deleteRecursively(host);
        Files.createDirectories(host.resolve("gradle/wrapper"));
        Files.createDirectories(host.resolve("src/main/java/dev/copperbench/stage14c"));
        Files.createDirectories(host.resolve("src/main/resources"));
        Files.copy(source.resolve("gradlew.bat"), host.resolve("gradlew.bat"));
        Files.copy(source.resolve("gradlew"), host.resolve("gradlew"));
        Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.jar"), host.resolve("gradle/wrapper/gradle-wrapper.jar"));
        Files.copy(source.resolve("gradle/wrapper/gradle-wrapper.properties"),
                host.resolve("gradle/wrapper/gradle-wrapper.properties"));

        Files.writeString(host.resolve("settings.gradle"), """
                pluginManagement {
                    repositories {
                        maven { url = 'https://maven.fabricmc.net/' }
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                rootProject.name = 'stage14c-fabric-packaged-host'
                """, StandardCharsets.UTF_8);
        Files.writeString(host.resolve("gradle.properties"), """
                org.gradle.jvmargs=-Xmx1G -Dfile.encoding=UTF-8 -Duser.language=en
                org.gradle.parallel=false
                org.gradle.configuration-cache=false
                """, StandardCharsets.UTF_8);

        String mappings = track.officialMappings() ? "    mappings loom.officialMojangMappings()\n" : "";
        Files.writeString(host.resolve("build.gradle"), """
                plugins { id '%s' version '%s' }
                dependencies {
                    minecraft 'com.mojang:minecraft:%s'
                %s    %s 'net.fabricmc:fabric-loader:%s'
                    %s 'net.fabricmc.fabric-api:fabric-api:%s'
                }
                tasks.withType(JavaCompile).configureEach { options.release = %d }
                """.formatted(track.pluginId(), track.pluginVersion(), track.minecraftVersion(), mappings,
                        track.dependencyConfiguration(), track.loaderVersion(), track.dependencyConfiguration(),
                        track.fabricApiVersion(), track.javaRelease()), StandardCharsets.UTF_8);

        String hostGameTestSource;
        if (track.minecraftVersion().startsWith("26.")) {
            hostGameTestSource = """
                    package dev.copperbench.stage14c;

                    import net.fabricmc.fabric.api.gametest.v1.GameTest;
                    import net.minecraft.gametest.framework.GameTestHelper;

                    public final class HostGameTest {
                        @GameTest
                        public void packagedServerReady(GameTestHelper helper) {
                            System.out.println("STAGE14C_HOST_GAMETEST_READY track=%s");
                            helper.succeed();
                        }
                    }
                    """.formatted(track.id());
        } else {
            hostGameTestSource = """
                    package dev.copperbench.stage14c;

                    import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
                    import net.minecraft.gametest.framework.GameTest;
                    import net.minecraft.gametest.framework.GameTestHelper;

                    public final class HostGameTest implements FabricGameTest {
                        @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
                        public void packagedServerReady(GameTestHelper helper) {
                            System.out.println("STAGE14C_HOST_GAMETEST_READY track=%s");
                            helper.succeed();
                        }
                    }
                    """.formatted(track.id());
        }
        Files.writeString(host.resolve("src/main/java/dev/copperbench/stage14c/HostGameTest.java"),
                hostGameTestSource, StandardCharsets.UTF_8);
        Files.writeString(host.resolve("src/main/resources/fabric.mod.json"), """
                {
                  "schemaVersion": 1,
                  "id": "stage14c_host",
                  "version": "1.0.0",
                  "name": "Stage 14C Packaged Host",
                  "environment": "server",
                  "entrypoints": {
                    "fabric-gametest": ["dev.copperbench.stage14c.HostGameTest"]
                  },
                  "depends": {
                    "fabricloader": ">=%s",
                    "minecraft": "%s",
                    "fabric-api": "*"
                  }
                }
                """.formatted(track.loaderVersion(), track.minecraftVersion()), StandardCharsets.UTF_8);
    }

    private static ProcessRun runGradle(Path project, Path gradleHome, Path javaHome, Path temp, Duration timeout,
            List<String> tasks, boolean gameTest) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add("gradlew.bat");
        command.add("--no-daemon");
        command.add("--console=plain");
        command.addAll(tasks);
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", javaHome.toString());
        builder.environment().put("GRADLE_USER_HOME", gradleHome.toString());
        builder.environment().put("TEMP", temp.toString());
        builder.environment().put("TMP", temp.toString());
        if (gameTest) {
            builder.environment().put("JAVA_TOOL_OPTIONS",
                    "-Dfabric-api.gametest=1 -Dfabric-api.gametest.report-file=run/gametest-results.xml");
        }
        Process process = builder.start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        return new ProcessRun(completed ? process.exitValue() : -1, !completed,
                output.get(30, TimeUnit.SECONDS));
    }

    private static List<FabricTrack> tracks(Path repository) {
        return List.of(
                new FabricTrack("fabric-1.20.1", "1.20.1", "0.15.11", "0.92.2+1.20.1",
                        "fabric-loom", "1.7.4", "modImplementation", "remapJar", true, 17,
                        repository.resolve("jdk/jdk21_win_64"),
                        repository.resolve("build/stage8-workspace-generator-gradle/fabric-1_20_1")),
                new FabricTrack("fabric-1.21.1", "1.21.1", "0.19.3", "0.116.15+1.21.1",
                        "net.fabricmc.fabric-loom-remap", "1.17.19", "modImplementation", "remapJar", true, 21,
                        repository.resolve("jdk/jdk21_win_64"),
                        repository.resolve("build/stage8-workspace-generator-gradle/fabric-1_21_1")),
                new FabricTrack("fabric-26.1.2", "26.1.2", "0.19.3", "0.155.2+26.1.2",
                        "net.fabricmc.fabric-loom", "1.17-SNAPSHOT", "implementation", "jar", false, 25,
                        repository.resolve("jdk/jbr25_win_64"),
                        repository.resolve("build/stage8-workspace-generator-gradle/fabric-26_1_2")),
                new FabricTrack("fabric-26.2", "26.2", "0.19.3", "0.158.0+26.2",
                        "net.fabricmc.fabric-loom", "1.17-SNAPSHOT", "implementation", "jar", false, 25,
                        repository.resolve("jdk/jbr25_win_64"),
                        repository.resolve("build/stage8-workspace-generator-gradle/fabric-26_2")));
    }

    private static Path findPackagedJar(Path libs) throws IOException {
        if (!Files.isDirectory(libs)) return null;
        try (Stream<Path> entries = Files.list(libs)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().contains("sources"))
                    .filter(path -> !path.getFileName().toString().contains("dev-shadow"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified())).orElse(null);
        }
    }

    private static boolean eulaAccepted(Path run) throws IOException {
        Path eula = run.resolve("eula.txt");
        if (!Files.isRegularFile(eula)) return false;
        return Files.readString(eula, StandardCharsets.UTF_8).lines()
                .map(String::trim).anyMatch(line -> line.equalsIgnoreCase("eula=true"));
    }

    private static boolean requiredTestsPassed(String output) {
        return output != null && output.matches("(?s).*All [1-9][0-9]* required tests passed :\\).*?");
    }

    private static boolean fatalSeen(String output) {
        String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return lower.contains("crash report") || lower.contains("failed to start the minecraft server")
                || lower.contains("exception in server tick loop");
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static String diagnosticTail(String output) {
        if (output == null) return "";
        return output.length() <= 8000 ? output : output.substring(output.length() - 8000);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private record FabricTrack(String id, String minecraftVersion, String loaderVersion, String fabricApiVersion,
            String pluginId, String pluginVersion, String dependencyConfiguration, String packageTask, boolean officialMappings,
            int javaRelease, Path javaHome, Path gradleHome) {
    }

    private record ProcessRun(int exitCode, boolean timedOut, String output) {
    }

    private record CellResult(String track, String variant, boolean passed, String failure, JsonObject evidence) {
    }
}
